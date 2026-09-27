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

## Reproducible build

For a complete NeoForge source build on a Java 21-only machine, use
`./gradlew :1.21.1-neoforge:build --configure-on-demand`. This command and all nine
tooltip/ABI regression runners passed for this change. The default multi-project
configuration also requires Java 17 for the Forge node. The fully remapped output
was separately tested against the installed JEI 19.57 jar; both explicit constructor
selectors survived packaging.

## Follow-up fixes in mq.2

The user confirmed the sidebar was restored with mq.1. A further transfer with
the inventory open crashed in `JeiTooltip.prepareForIngredientTooltip` because
the runtime was still absent. JEI 19.57 renamed the GUI drawing callbacks to
`drawForScreenBackground` and `drawForScreenForeground`; the old render guards
were therefore skipped by their compatibility gates. A separate complete-ABI
guard variant now covers those methods and initialization/render preparation.

The ingredient filter's defer/schedule injections had the same constructor
specialization problem, but failed silently because the default injection count
was zero. A 3.428-second synchronous filter build was observed. Both callbacks
are constructor-argument-independent and now retain `<init>*` with explicit
`remap=false` and `require=1`, restoring the existing client-tick-budgeted build.

Ars Nouveau, Create Encased and EnderIO's base plugin also logged main-thread-only
API violations while removing ingredients. Their callbacks are explicitly routed
to the client thread. Other plugin work retains the existing execution policy.

mq.2 is built entirely through Gradle. The initial two-class mq.1 packaging helper
is removed because it cannot package the new guard and updated Mixin manifest.

## Client verification

The user confirmed mq.2 restored the sidebar across joins/transfers without the
previous crash, and reduced but did not eliminate the hitch. Client logs show
the synchronous filter phase reduced from 3.428 seconds to 0.093–0.192 seconds.
The remaining budgeted indexing plus plugin registration still delayed runtime
availability: one transfer rebuilt JEI in 46.39 seconds.

## Session cache in mq.3

That transfer emitted LoggingIn and RecipesUpdated on the existing connection,
without LoggingOut. JEI unconditionally restarted from the recipe-update handler.
The new hook intercepts only that restart, leaving resource reload/manual restart
and disconnect teardown intact.

Enable `sessionRuntimeCache = true` under `[general]` in
`config/justenoughthreads-client.toml` only for a proxy network whose subservers
use the same mod/plugin scripts and settings. The default is false. After the
first complete startup, a matching resync retains the full runtime, including
recipe registration and the ingredient search index. No runtime is serialized to
disk. Restarting the client or disconnecting requires a fresh build.

The verifier hashes the complete ordered synchronized recipes using their network
codecs, registry identifiers/numeric mappings, registry tags, all synchronized
datapack registry values via their codecs (including NeoForge's extended list),
and language. It runs on the client thread with a 4 ms per-tick budget between
individual encoding tasks. One slow mod codec can exceed that soft budget.
SHA-256 values are framed by byte lengths. Failed encoding is always a cache miss.
JEI rendering/input is withheld while validation is in progress. A hit refreshes
the registry utility and screen initialization; a miss performs normal teardown
and rebuild. Config/plugin-specific custom network data is outside this digest,
so this mode is not a promise that arbitrary servers with similar recipes are interchangeable.

Look for `JEI session cache baseline captured`, then `JEI session cache HIT` and
the measured `validationMs`. A hit should have no subsequent `Stopping JEI` /
`Starting JEI` sequence. Test sidebar search, recipe/use lookup and recipe transfer,
not just whether the item list is visible. F3+T or disconnect forces reconstruction.

The mq.3 full build and ten regression runners passed, including packaged-jar
ABI checks against JEI 19.57.0.449. Runtime acceptance is recorded separately.

## Manual acceptance

Restart is required after replacing the mod. Check both initial join and a server
transfer, then open the inventory and verify that the complete JEI sidebar and
search appear. Check for `Starting JEI took` and absence of native search Mixin or
background-startup failures. ABI tests alone do not establish full modpack stability.
