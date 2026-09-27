package com.tonywww.jeioptimize.runtime;

/** Lifecycle conditions shared by FULL mode and its tests; recipe equality is intentionally not a condition. */
public record RuntimeCacheLease(Object connection, long generation) {
    public boolean canReuse(boolean fullMode, Object currentConnection, long currentGeneration, boolean ready) {
        return fullMode && ready && connection != null && connection == currentConnection && generation == currentGeneration;
    }
}
