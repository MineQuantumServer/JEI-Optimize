package com.tonywww.jeioptimize.runtime;

import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IRecipesGui;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public final class TooltipSessionCacheTest {
    public static void main(String[] args) throws Exception {
        Object connection = new String("proxy");
        RuntimeCacheLease lease = new RuntimeCacheLease(connection, 7);
        check(lease.canReuse(true, connection, 7, true), "FULL same connection reuses completed runtime");
        check(!lease.canReuse(false, connection, 7, true), "ACCURATE always rebuilds recipe runtime");
        check(!lease.canReuse(true, new String("proxy"), 7, true), "FULL does not span reconnects");
        check(!lease.canReuse(true, connection, 8, true), "FULL does not span manual/resource rebuilds");
        check(!lease.canReuse(true, connection, 7, false), "FULL does not reuse incomplete startup");
        check(!new RuntimeCacheLease(null, 7).canReuse(true, null, 7, true), "no disconnected reuse");
        Map<String, Object> components = new HashMap<>();
        IJeiRuntime runtime = (IJeiRuntime) Proxy.newProxyInstance(IJeiRuntime.class.getClassLoader(),
            new Class<?>[] {IJeiRuntime.class}, (proxy, method, arguments) -> components.get(method.getName()));
        check(!JeiRuntimeReadiness.hasGui(null), "absent runtime is not ready");
        check(!JeiRuntimeReadiness.hasGui(runtime), "non-null runtime without GUI is not ready");
        components.put("getIngredientListOverlay", placeholder(IIngredientListOverlay.class));
        components.put("getIngredientFilter", placeholder(IIngredientFilter.class));
        components.put("getRecipesGui", placeholder(IRecipesGui.class));
        check(JeiRuntimeReadiness.hasGui(runtime), "initialized GUI is ready even when player hides overlay");
        for (var entry : Map.of(
            "getIngredientListOverlay", "mezz.jei.library.gui.IngredientListOverlayDummy",
            "getIngredientFilter", "mezz.jei.library.ingredients.IngredientFilterApiDummy",
            "getRecipesGui", "mezz.jei.library.gui.recipes.RecipesGuiDummy").entrySet()) {
            Object initialized = components.get(entry.getKey());
            Object dummy = Class.forName(entry.getValue()).getField("INSTANCE").get(null);
            components.put(entry.getKey(), dummy);
            check(!lease.canReuse(true, connection, 7, JeiRuntimeReadiness.hasGui(runtime)),
                "FULL must rebuild after GUI plugin failure: " + entry.getValue());
            components.put(entry.getKey(), initialized);
        }
        check(lease.canReuse(true, connection, 7, JeiRuntimeReadiness.hasGui(runtime)), "recovered GUI can be cached");
        System.out.println("TooltipSessionCacheTest passed: modes, lifecycle, real JEI dummy APIs, GUI failure and recovery");
    }
    private static Object placeholder(Class<?> api) {
        return Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, (proxy, method, args) -> {
            throw new AssertionError("readiness must not query visibility or enumerate ingredients");
        });
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
