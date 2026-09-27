package com.tonywww.jeioptimize.runtime;

public final class TooltipSessionCacheTest {
    public static void main(String[] args) {
        Object connection = new String("proxy");
        RuntimeCacheLease lease = new RuntimeCacheLease(connection, 7);
        check(lease.canReuse(true, connection, 7, true), "FULL same connection reuses completed runtime");
        check(!lease.canReuse(false, connection, 7, true), "ACCURATE always rebuilds recipe runtime");
        check(!lease.canReuse(true, new String("proxy"), 7, true), "FULL does not span reconnects");
        check(!lease.canReuse(true, connection, 8, true), "FULL does not span manual/resource rebuilds");
        check(!lease.canReuse(true, connection, 7, false), "FULL does not reuse incomplete startup");
        check(!new RuntimeCacheLease(null, 7).canReuse(true, null, 7, true), "no disconnected reuse");
        System.out.println("TooltipSessionCacheTest passed: ACCURATE/FULL selection, identity, generation, readiness");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
