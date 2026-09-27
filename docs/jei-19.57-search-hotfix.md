# JEI 19.57 missing sidebar after asynchronous startup

The affected client runs Minecraft 1.21.1, NeoForge 21.1.251, JEI 19.57.0.449
and Just Enough Threads 0.14.1. Joining and switching servers reaches the world,
but JEI never publishes a usable runtime. The loading panel disappearing does not
mean initialization succeeded.

The client log reports `InvalidInjectionException` for
`JeiNativeSearchBuilderMixin.jeiopt$retainBulkBuilder` and then
`JEI failed to start on the background thread`.

The source used the bare selector `<init>`. The published jar contains only
`<init>(Lmezz/jei/gui/search/ElementPrefixParser;)V`, matching the compile-time
JEI 19.27 constructor. The installed JEI instead exposes
`<init>(Lmezz/jei/gui/search/ElementPrefixParser;Ljava/util/Collection;Lmezz/jei/api/runtime/IIngredientManager;)V`.
The old ABI gate accepted any constructor containing `ISearchStorageBuilder.build`,
so it enabled a hook whose packaged selector could not match.

The fix explicitly lists both descriptors and opts out of hook remapping. The ABI
gate accepts only those signatures with the expected builder call. This preserves
the existing asynchronous startup and native bulk-index behavior.

## Reproducible local hotfix

Run `scripts/build-neoforge-hotfix.ps1` in PowerShell 7 with these arguments:

- `-BaseJar`: released `justenoughthreads-0.14.1+1.21.1.jar`
- `-JeiJar`: the installed JEI 19.57.0.449 jar
- `-Libraries`: the launcher's `.minecraft/libraries` directory
- `-MixinExtrasJar`: MixinExtras NeoForge 0.5.3 jar
- `-JavaHome`: a Java 21 JDK
- `-OutputJar`: a new output path

The script uses `javac --release 21 -proc:none`, preserves the released jar except
for the changed Mixin/plugin classes and the mod version, excludes the regression
test class from the artifact, and runs `TooltipAbiTest` against the packaged jar.
These changes contain no mapped Minecraft member references.

For a complete NeoForge source build on a Java 21-only machine, use
`./gradlew :1.21.1-neoforge:build --configure-on-demand`. This command and all nine
tooltip/ABI regression runners passed for this change. The default multi-project
configuration also requires Java 17 for the Forge node. The fully remapped output
was separately tested against the installed JEI 19.57 jar; both explicit constructor
selectors survived packaging.

## Client verification

Restart is required after replacing the mod. Check both initial join and a server
transfer, then open the inventory and verify that the complete JEI sidebar and
search appear. Check for `Starting JEI took` and absence of native search Mixin or
background-startup failures. ABI tests alone do not establish full modpack stability.
