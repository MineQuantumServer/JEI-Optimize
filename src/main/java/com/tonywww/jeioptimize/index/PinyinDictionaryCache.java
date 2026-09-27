package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import java.lang.reflect.*;
import java.util.Collection;

/** Optional JECharacters integration, avoiding a compile-time or bundled dependency on that mod. */
public final class PinyinDictionaryCache {
    private static SharedSearchDictionary.Pool pool;
    private static ClassLoader ownerLoader;
    private PinyinDictionaryCache() {}

    public static synchronized SharedSearchDictionary<Object> create(Object originalStorage) {
        if (!JeiOptFeatureFlags.pinyinDictionaryCache()) return null;
        try {
            ClassLoader loader = originalStorage.getClass().getClassLoader();
            Field treeField = originalStorage.getClass().getDeclaredField("tree");
            treeField.setAccessible(true);
            Object overflowTree = treeField.get(originalStorage);
            if (pool == null || ownerLoader != loader) {
                Class<?> match = Class.forName("me.towdium.jecharacters.utils.Match", true, loader);
                Object tree = match.getMethod("searcher").invoke(null);
                // Match.searcher registers this tree for JECharacters' keyboard/fuzzy-pinyin config invalidation.
                pool = new SharedSearchDictionary.Pool(new ReflectiveBackend<>(tree), 100_000, 8_000_000L);
                ownerLoader = loader;
            }
            return new SharedSearchDictionary<>(pool, new ReflectiveBackend<>(overflowTree));
        } catch (ReflectiveOperationException | LinkageError e) {
            JeiOptimize.LOGGER.warn("JEI pinyin dictionary cache unavailable; using original search storage", e);
            return null;
        }
    }

    public static synchronized void begin() { if (pool != null) pool.resetCounters(); }
    public static synchronized void clear() { pool = null; ownerLoader = null; }
    public static synchronized void report() {
        if (pool != null) JeiOptimize.LOGGER.info("JEI pinyin dictionary cache: {}", pool.statistics());
    }

    private static final class ReflectiveBackend<T> implements SharedSearchDictionary.Backend<T> {
        private final Object tree;
        private final Method put, search;
        ReflectiveBackend(Object tree) throws ReflectiveOperationException {
            this.tree = tree;
            put = tree.getClass().getMethod("put", String.class, Object.class);
            search = tree.getClass().getMethod("search", String.class);
        }
        @Override public void put(String text, T value) { invoke(put, text, value); }
        @SuppressWarnings("unchecked")
        @Override public Collection<T> search(String query) { return (Collection<T>) invoke(search, query); }
        private Object invoke(Method method, Object... args) {
            try { return method.invoke(tree, args); }
            catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException r) throw r;
                if (e.getCause() instanceof Error error) throw error;
                throw new IllegalStateException(e.getCause());
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
    }
}
