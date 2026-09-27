package com.tonywww.jeioptimize.index;

import java.util.*;

/** The shared tree contains strings only; all game objects belong to the current storage. */
public final class SharedSearchDictionary<V> {
    public interface Backend<T> {
        void put(String text, T value);
        Collection<T> search(String query);
    }

    public static final class Pool {
        private final Backend<String> index;
        private final Set<String> words = new HashSet<>();
        private final int maxWords;
        private final long maxCharacters;
        private long characters;
        private long added, reused, overflowed;

        public Pool(Backend<String> index, int maxWords, long maxCharacters) {
            this.index = Objects.requireNonNull(index);
            this.maxWords = maxWords;
            this.maxCharacters = maxCharacters;
        }

        synchronized boolean add(String text) {
            if (words.contains(text)) { reused++; return true; }
            if (words.size() >= maxWords || text.length() > maxCharacters - characters) {
                overflowed++;
                return false;
            }
            index.put(text, text);
            words.add(text);
            characters += text.length();
            added++;
            return true;
        }

        synchronized Collection<String> search(String query) { return index.search(query); }
        public synchronized String statistics() {
            return "words=" + words.size() + ", characters=" + characters
                + ", newWords=" + added + ", reusedPuts=" + reused + ", overflowPuts=" + overflowed;
        }
        public synchronized void resetCounters() { added = reused = overflowed = 0; }
    }

    private final Pool pool;
    private final Backend<V> overflow;
    private final Map<String, Set<V>> currentValues = new HashMap<>();
    private final Set<V> allValues = new HashSet<>();
    private final IntSummaryStatistics keyLengths = new IntSummaryStatistics();

    public SharedSearchDictionary(Pool pool, Backend<V> overflow) {
        this.pool = pool;
        this.overflow = overflow;
    }

    public void put(String text, V value) {
        if (pool.add(text)) currentValues.computeIfAbsent(text, ignored -> new HashSet<>()).add(value);
        else overflow.put(text, value);
        allValues.add(value);
        keyLengths.accept(text.length());
    }

    public Collection<V> search(String query) {
        Set<V> result = new HashSet<>();
        for (String word : pool.search(query)) {
            Set<V> values = currentValues.get(word);
            if (values != null) result.addAll(values);
        }
        result.addAll(overflow.search(query));
        return result;
    }

    public Collection<V> allValues() { return Collections.unmodifiableSet(allValues); }
    public String statistics() { return keyLengths.toString(); }
}
