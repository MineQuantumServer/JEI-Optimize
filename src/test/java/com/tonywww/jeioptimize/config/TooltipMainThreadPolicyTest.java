package com.tonywww.jeioptimize.config;

import java.util.List;
import java.util.Set;

public final class TooltipMainThreadPolicyTest {
    public static void main(String[] args) {
        check(JeiMainThreadPluginPolicy.requiresBuiltInMainThread("thaumcraft:jei_plugin"),
            "existing configurations still force the unsafe Thaumcraft callback onto the client thread");
        check(JeiOptFeatureFlags.pluginRequiresMainThread("thaumcraft:jei_plugin"), "actual flag applies built-in policy");
        check(!JeiMainThreadPluginPolicy.requiresBuiltInMainThread("jei:minecraft"), "ordinary recipe registration remains asynchronous");
        check(!JeiMainThreadPluginPolicy.requiresBuiltInMainThread(null), "unknown plugin accepted by conservative caller policy");
        Set<String> entries = JeiMainThreadPluginPolicy.normalizeEntries(List.of(" thaumcraft ", "test:recipes", "bad value"));
        check(JeiMainThreadPluginPolicy.matches("thaumcraft:other", entries), "configured namespace routes every callback");
        check(JeiMainThreadPluginPolicy.matches("test:recipes", entries), "configured exact ID");
        check(!JeiMainThreadPluginPolicy.matches("test:other", entries), "exact ID does not affect unrelated callback");
        check(entries.size() == 2, "invalid entry rejected");
        System.out.println("TooltipMainThreadPolicyTest passed: mandatory compatibility routing and existing config entries");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
