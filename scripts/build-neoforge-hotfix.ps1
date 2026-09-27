# Build only the two changed, unmapped classes against a released 0.14.1 NeoForge jar.
# This avoids resolving the unrelated Forge development toolchain for a local hotfix.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BaseJar,
    [Parameter(Mandatory)][string]$JeiJar,
    [Parameter(Mandatory)][string]$Libraries,
    [Parameter(Mandatory)][string]$MixinExtrasJar,
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$OutputJar
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$stage = Join-Path $repo ('build/hotfix-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $stage 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$dependencies = @(Get-ChildItem -LiteralPath $Libraries -Recurse -Filter '*.jar' |
    Where-Object { $_.Name -match 'sponge-mixin|log4j-(api|core)|asm' -and $_.FullName -notmatch '\\9\.3\\' } |
    Select-Object -ExpandProperty FullName)
$cp = (@($BaseJar, $MixinExtrasJar) + $dependencies) -join ';'
$sources = @(
    'src/main/java/com/tonywww/jeioptimize/JeiOptMixinPlugin.java',
    'src/main/java/com/tonywww/jeioptimize/mixin/JeiNativeSearchBuilderMixin.java',
    'src/test/java/com/tonywww/jeioptimize/TooltipAbiTest.java'
) | ForEach-Object { Join-Path $repo $_ }
& (Join-Path $JavaHome 'bin/javac.exe') --release 21 -encoding UTF-8 -proc:none -cp $cp -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Hotfix compilation failed' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$OutputJar = [IO.Path]::GetFullPath($OutputJar)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputJar) | Out-Null
if (Test-Path -LiteralPath $OutputJar) { throw "Output already exists: $OutputJar" }
$inputArchive = [IO.Compression.ZipFile]::OpenRead($BaseJar)
$outputArchive = [IO.Compression.ZipFile]::Open($OutputJar, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($entry in $inputArchive.Entries) {
        if ($entry.FullName -match '^META-INF/.*\.(SF|RSA|DSA)$') { throw 'Signed base jars are unsupported' }
        $replacement = Join-Path $classes $entry.FullName
        if ($entry.FullName -like '*TooltipAbiTest*') { throw 'Test class unexpectedly present in base jar' }
        if ((Test-Path -LiteralPath $replacement -PathType Leaf) -and $entry.FullName.EndsWith('.class')) { continue }
        $newEntry = $outputArchive.CreateEntry($entry.FullName)
        $destination = $newEntry.Open()
        $source = $entry.Open()
        try {
            if ($entry.FullName -eq 'META-INF/neoforge.mods.toml') {
                $reader = [IO.StreamReader]::new($source)
                $metadata = $reader.ReadToEnd()
                if ($metadata -notmatch 'version\s*=\s*"0\.14\.1"') { throw 'Expected release 0.14.1 metadata' }
                $metadata = $metadata -replace '(?m)^(\s*version\s*=\s*)"0\.14\.1"', '$1"0.14.2-mq.1"'
                $bytes = [Text.Encoding]::UTF8.GetBytes($metadata)
                $destination.Write($bytes, 0, $bytes.Length)
            } else { $source.CopyTo($destination) }
        } finally { $source.Dispose(); $destination.Dispose() }
    }
    Get-ChildItem -LiteralPath $classes -Recurse -Filter '*.class' |
        Where-Object Name -NotLike 'TooltipAbiTest*' | ForEach-Object {
            $relative = [IO.Path]::GetRelativePath($classes, $_.FullName).Replace('\', '/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($outputArchive, $_.FullName, $relative) | Out-Null
        }
} finally { $inputArchive.Dispose(); $outputArchive.Dispose() }
# The delivered jar comes FIRST: verify packaged annotations and the packaged ABI gate, not just javac output.
& (Join-Path $JavaHome 'bin/java.exe') -cp "$OutputJar;$classes;$cp" com.tonywww.jeioptimize.TooltipAbiTest $JeiJar
if ($LASTEXITCODE -ne 0) { throw 'Packaged hotfix regression tests failed; do not install output' }
Get-FileHash -LiteralPath $OutputJar -Algorithm SHA256
