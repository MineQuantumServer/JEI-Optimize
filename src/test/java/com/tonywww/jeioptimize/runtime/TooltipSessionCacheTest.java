package com.tonywww.jeioptimize.runtime;

public final class TooltipSessionCacheTest {
    public static void main(String[] args) {
        Object connection = new String("proxy");
        byte[] original = new byte[32];
        original[0] = 5;
        SessionCacheStamp stamp = new SessionCacheStamp(connection, 7, original);
        check(stamp.eligible(connection, 7, true), "same active runtime");
        check(!stamp.eligible(new String("proxy"), 7, true), "reconnect to same address invalidates");
        check(!stamp.eligible(connection, 8, true), "resource reload invalidates");
        check(!stamp.eligible(connection, 7, false), "incomplete runtime cannot be reused");
        check(stamp.matches(original.clone()), "identical encoded content");
        original[0] = 6;
        check(!stamp.matches(original), "changed contents invalidate even with identical recipe IDs/counts");
        original[0] = 5;
        check(stamp.matches(original), "stamp owns its digest");
        check(!stamp.matches(null), "failed encoding cannot hit");
        check(!stamp.matches(new byte[0]), "empty encoding cannot hit");
        check(!new SessionCacheStamp(connection, 7, null).eligible(connection, 7, true), "failed baseline");
        check(!new SessionCacheStamp(null, 7, original).eligible(null, 7, true), "no disconnected cache hit");
        System.out.println("TooltipSessionCacheTest passed: identity, generation, readiness, content changes, encoding failure");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
