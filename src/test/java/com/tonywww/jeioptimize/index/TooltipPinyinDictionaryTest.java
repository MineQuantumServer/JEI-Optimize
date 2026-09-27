package com.tonywww.jeioptimize.index;

import java.util.*;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.zip.ZipFile;

public final class TooltipPinyinDictionaryTest {
    public static void main(String[] args) throws Exception {
        exercise(Literal::new, () -> {});
        for (String arg : args) {
            try (ZipFile jar = new ZipFile(arg)) {
                if (jar.getEntry("me/towdium/pinin/searchers/TreeSearcher.class") == null) continue;
            }
            realPinIn(Path.of(arg));
        }
        System.out.println("TooltipPinyinDictionaryTest passed: cold/warm results, stale-object isolation, duplicates, overflow, config refresh");
    }

    private static void exercise(Supplier<SharedSearchDictionary.Backend<Object>> factory, Runnable reconfigure) {
        // A tiny pool forces both dictionary hits and overflow; values from the first world must never escape.
        var wordBackend = factory.get();
        var pool = new SharedSearchDictionary.Pool(new SharedSearchDictionary.Backend<>() {
            public void put(String text, String value) { wordBackend.put(text, value); }
            public Collection<String> search(String query) {
                return wordBackend.search(query).stream().map(String.class::cast).toList();
            }
        }, 4, 50);
        var old = new SharedSearchDictionary<Object>(pool, factory.get());
        old.put("旧世界铁锭", "old-world-only");
        String[] texts = {"铁锭", "铜锭", "中文", "重型机器", "red concrete", "iron", "", "\ud83d\ude00", "long-".repeat(100)};
        for (int generation = 0; generation < 3; generation++) {
            var actual = new SharedSearchDictionary<Object>(pool, factory.get());
            var expected = factory.get();
            Set<Object> all = new HashSet<>();
            Random random = new Random(42 + generation);
            for (int i = 0; i < 1500; i++) {
                String word = texts[random.nextInt(texts.length)];
                Integer value = random.nextInt(500);
                expected.put(word, value);
                actual.put(word, value);
                all.add(value);
            }
            for (int pass = 0; pass < 2; pass++) {
                for (String query : List.of("", "i", "iron", "concrete", "铁", "中文", "tie", "tieding", "td", "zhong", "chong", "z", "long", "\ud83d", "absent", "旧世界")) {
                    check(new HashSet<>(expected.search(query)).equals(new HashSet<>(actual.search(query))), "query=" + query);
                    check(!actual.search(query).contains("old-world-only"), "no cached world objects");
                }
                reconfigure.run();
            }
            check(new HashSet<>(actual.allValues()).equals(all), "getAllElements uses current generation only");
            actual.put("runtime-new", 9000);
            expected.put("runtime-new", 9000);
            check(new HashSet<>(actual.search("runtime")).equals(new HashSet<>(expected.search("runtime"))), "runtime put");
        }
        System.out.println("Dictionary fixture: " + pool.statistics());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void realPinIn(Path jar) throws Exception {
        java.net.URL fastutil = Class.forName("it.unimi.dsi.fastutil.ints.IntSet").getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL(), fastutil}, ClassLoader.getPlatformClassLoader())) {
            Class<?> dict = loader.loadClass("me.towdium.pinin.DictLoader");
            Object dictionary = Proxy.newProxyInstance(loader, new Class<?>[]{dict}, (proxy, method, arguments) -> {
                BiConsumer<Character, String[]> consumer = (BiConsumer<Character, String[]>) arguments[0];
                consumer.accept('铁', new String[]{"tie3"}); consumer.accept('铜', new String[]{"tong2"});
                consumer.accept('锭', new String[]{"ding4"}); consumer.accept('中', new String[]{"zhong1"});
                consumer.accept('文', new String[]{"wen2"}); consumer.accept('重', new String[]{"zhong4", "chong2"});
                return null;
            });
            Class<?> contextType = loader.loadClass("me.towdium.pinin.PinIn");
            Object context = contextType.getConstructor(dict).newInstance(dictionary);
            Class<? extends Enum> logicType = (Class<? extends Enum>) loader.loadClass("me.towdium.pinin.searchers.Searcher$Logic");
            Object contain = Enum.valueOf(logicType, "CONTAIN");
            Class<?> treeType = loader.loadClass("me.towdium.pinin.searchers.TreeSearcher");
            Method put = treeType.getMethod("put", String.class, Object.class);
            Method search = treeType.getMethod("search", String.class);
            List<Object> trees = new ArrayList<>();
            Supplier<SharedSearchDictionary.Backend<Object>> factory = () -> {
                try {
                    Object tree = treeType.getConstructor(logicType, contextType).newInstance(contain, context);
                    trees.add(tree);
                    return new SharedSearchDictionary.Backend<>() {
                        public void put(String text, Object value) { call(put, tree, text, value); }
                        public Collection<Object> search(String query) { return (Collection<Object>) call(search, tree, query); }
                    };
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            };
            exercise(factory, () -> {
                try {
                    Object config = contextType.getMethod("config").invoke(context);
                    config.getClass().getMethod("fZh2Z", boolean.class).invoke(config, true);
                    config.getClass().getMethod("commit").invoke(config);
                    for (Object tree : trees) treeType.getMethod("refresh").invoke(tree);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            System.out.println("Real PinIn differential passed: " + jar.getFileName());
        }
    }

    private static Object call(Method m, Object receiver, Object... args) {
        try { return m.invoke(receiver, args); } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void check(boolean okay, String message) { if (!okay) throw new AssertionError(message); }
    private static final class Literal implements SharedSearchDictionary.Backend<Object> {
        private final Map<String, Set<Object>> values = new HashMap<>();
        public void put(String text, Object value) { values.computeIfAbsent(text, ignored -> new HashSet<>()).add(value); }
        public Collection<Object> search(String query) {
            Set<Object> result = new HashSet<>();
            values.forEach((key, value) -> { if (key.contains(query)) result.addAll(value); });
            return result;
        }
    }
}
