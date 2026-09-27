package com.tonywww.jeioptimize.runtime;

//? if neoforge {
/*import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.mixin.accessor.JeiRuntimeAccessor;
import mezz.jei.common.util.RegistryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
*///?}

/** Explicit FULL mode. ACCURATE never reuses a recipe runtime or performs a slow full digest. */
public final class JeiSessionCache {
    private JeiSessionCache() {}

    //? if forge {
    public static void clear() {}
    public static void captureIfNeeded() {}
    public static void onRecipeRestart(Runnable restart) { restart.run(); }
    //?} else {
    /*private static RuntimeCacheLease lease;

    public static void clear() {
        lease = null;
    }

    private static Connection currentConnection() {
        var listener = Minecraft.getInstance().getConnection();
        return listener == null ? null : listener.getConnection();
    }

    public static void captureIfNeeded() {
        if (!JeiOptFeatureFlags.enabled()) { clear(); return; }
        if (lease != null) return;
        lease = new RuntimeCacheLease(currentConnection(), JeiOptRuntimeState.currentGeneration());
        JeiOptimize.LOGGER.info("JEI runtime tracked: generation={}, cacheMode={}", lease.generation(),
            JeiOptFeatureFlags.sessionRuntimeCache() ? "FULL (recipe differences are not checked)" : "ACCURATE");
    }

    public static void onRecipeRestart(Runnable restart) {
        boolean ready = !JeiOptStartupProgressState.blocksJeiInput()
            && JeiRuntimeReadiness.hasGui(JeiRuntimeAccessor.jeiopt$getNullableRuntime());
        if (lease != null && lease.canReuse(JeiOptFeatureFlags.sessionRuntimeCache(), currentConnection(),
                JeiOptRuntimeState.currentGeneration(), ready)
                && Minecraft.getInstance().level != null) {
            RegistryUtil.setRegistryAccess(Minecraft.getInstance().level.registryAccess());
            JeiOptimize.LOGGER.info("JEI FULL cache HIT: retained current runtime; recipe validation and rebuild skipped");
            return;
        }
        if (lease != null && JeiOptFeatureFlags.sessionRuntimeCache() && !ready) {
            JeiOptimize.LOGGER.info("JEI FULL cache MISS: runtime or GUI is incomplete; rebuilding");
        }
        clear();
        restart.run();
    }
    *///?}
}
