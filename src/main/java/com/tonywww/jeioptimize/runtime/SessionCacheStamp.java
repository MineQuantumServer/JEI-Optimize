package com.tonywww.jeioptimize.runtime;

import java.util.Arrays;

/** A cache hit needs both the same live session and identical, successfully encoded data. */
public final class SessionCacheStamp {
    private final Object connection;
    private final long generation;
    private final byte[] digest;

    public SessionCacheStamp(Object connection, long generation, byte[] digest) {
        this.connection = connection;
        this.generation = generation;
        this.digest = digest == null ? null : digest.clone();
    }

    public boolean eligible(Object currentConnection, long currentGeneration, boolean runtimeReady) {
        return runtimeReady && connection != null && connection == currentConnection
            && generation == currentGeneration && digest != null && digest.length == 32;
    }

    public boolean matches(byte[] candidate) {
        return candidate != null && candidate.length == 32 && Arrays.equals(digest, candidate);
    }
}
