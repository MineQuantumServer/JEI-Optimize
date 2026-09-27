package com.tonywww.jeioptimize.runtime;

//? if neoforge {
/*import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.mixin.accessor.JeiRuntimeAccessor;
import mezz.jei.common.Internal;
import mezz.jei.common.util.RegistryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import java.util.function.Consumer;
*///?}

/** Retains the live runtime only across verified recipe resyncs on one proxy connection. */
public final class JeiSessionCache {
    private JeiSessionCache() {}
    // Kept separate from Minecraft-dependent state so render/input guards stay cheap.
    private static volatile boolean validating;
    public static boolean isValidating() { return validating; }

    //? if forge {
    public static void tick() {}
    public static void clear() {}
    public static void captureIfNeeded() {}
    public static void onRecipeRestart(Runnable restart) { restart.run(); }
    //?} else {
    /*private static SessionCacheStamp fingerprint;
    private static Connection connection;
    private static long generation;
    private static JeiSessionFingerprint pending;
    private static Consumer<byte[]> completion;
    private static long started;

    public static void clear() {
        if (pending != null) pending.close();
        pending = null;
        completion = null;
        fingerprint = null;
        connection = null;
        validating = false;
    }

    private static Connection currentConnection() {
        var listener = Minecraft.getInstance().getConnection();
        return listener == null ? null : listener.getConnection();
    }

    public static void captureIfNeeded() {
        if (!JeiOptFeatureFlags.sessionRuntimeCache() || pending != null || fingerprint != null) return;
        begin(false, result -> {
            if (result != null) {
                fingerprint = new SessionCacheStamp(connection, generation, result);
                JeiOptimize.LOGGER.info("JEI session cache baseline captured: generation={}, elapsedMs={}",
                    generation, (System.nanoTime() - started) / 1_000_000);
            }
        });
    }

    public static void onRecipeRestart(Runnable restart) {
        boolean ready = !JeiOptStartupProgressState.blocksJeiInput()
            && JeiRuntimeAccessor.jeiopt$getNullableRuntime() != null;
        if (!JeiOptFeatureFlags.sessionRuntimeCache() || !ready || fingerprint == null
                || !fingerprint.eligible(currentConnection(), JeiOptRuntimeState.currentGeneration(), ready)) {
            clear();
            restart.run();
            return;
        }
        SessionCacheStamp expected = fingerprint;
        begin(true, actual -> {
            if (expected.matches(actual)) {
                RegistryUtil.setRegistryAccess(Minecraft.getInstance().level.registryAccess());
                JeiOptimize.LOGGER.info("JEI session cache HIT: retained recipes and search index, validationMs={}",
                    (System.nanoTime() - started) / 1_000_000);
                // Screen init may have been suppressed while validation was pending.
                var mc = Minecraft.getInstance();
                if (mc.screen != null) mc.screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            } else {
                JeiOptimize.LOGGER.info("JEI session cache MISS: synchronized data changed or could not be verified");
                restart.run();
                // stop() clears the old baseline. The new digest belongs to the newly started runtime.
                connection = currentConnection();
                generation = JeiOptRuntimeState.currentGeneration();
                fingerprint = actual == null ? null : new SessionCacheStamp(connection, generation, actual);
            }
        });
    }

    private static void begin(boolean reuse, Consumer<byte[]> done) {
        if (pending != null) pending.close();
        pending = null;
        validating = reuse;
        connection = currentConnection();
        generation = JeiOptRuntimeState.currentGeneration();
        started = System.nanoTime();
        completion = done;
        try {
            pending = new JeiSessionFingerprint();
        } catch (Exception | LinkageError e) {
            fail(e);
        }
    }

    public static void tick() {
        if (pending == null) return;
        if (currentConnection() != connection || Minecraft.getInstance().level == null
                || !JeiOptRuntimeState.isCurrent(generation)) {
            clear();
            return;
        }
        try {
            if (!JeiOptFeatureFlags.sessionRuntimeCache()) { finish(null); return; }
            if (pending.step(System.nanoTime() + 4_000_000L)) {
                byte[] result = pending.result();
                finish(result);
            }
        } catch (Exception | LinkageError e) {
            fail(e);
        }
    }

    private static void fail(Throwable error) {
        JeiOptimize.LOGGER.warn("JEI session cache verification unavailable; using normal startup", error);
        finish(null);
    }

    private static void finish(byte[] result) {
        if (pending != null) pending.close();
        pending = null;
        Consumer<byte[]> done = completion;
        completion = null;
        validating = false;
        if (done != null) done.accept(result);
    }
    *///?}
}
