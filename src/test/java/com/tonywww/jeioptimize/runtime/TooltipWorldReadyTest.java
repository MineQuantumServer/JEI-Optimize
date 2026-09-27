package com.tonywww.jeioptimize.runtime;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class TooltipWorldReadyTest {
    public static void main(String[] args) {
        AtomicBoolean current = new AtomicBoolean(true);
        AtomicBoolean ready = new AtomicBoolean(false);
        AtomicInteger calls = new AtomicInteger();
        JeiOptWorldReadyTask task = new JeiOptWorldReadyTask(current::get, ready::get, calls::incrementAndGet);
        for (int tick = 0; tick < 600; tick++) {
            check(!task.getAsBoolean(), "slow login remains queued without blocking a tick");
        }
        check(calls.get() == 0 && !task.completion().isDone(), "GUI cannot run before a world exists");
        ready.set(true);
        check(task.getAsBoolean(), "world attachment releases startup");
        task.completion().join();
        task.getAsBoolean();
        check(calls.get() == 1, "GUI callback runs once");

        ready.set(false);
        JeiOptWorldReadyTask disconnected = new JeiOptWorldReadyTask(current::get, ready::get, calls::incrementAndGet);
        check(!disconnected.getAsBoolean(), "transfer detaches the world");
        current.set(false);
        ready.set(true);
        check(disconnected.getAsBoolean() && disconnected.completion().isCancelled(), "disconnect/restart cancels old work");
        check(calls.get() == 1, "new world's readiness cannot revive old callbacks");

        current.set(true);
        JeiOptWorldReadyTask cancelled = new JeiOptWorldReadyTask(current::get, ready::get, calls::incrementAndGet);
        cancelled.completion().cancel(false);
        check(cancelled.getAsBoolean() && calls.get() == 1, "tracked cancellation drains without callbacks");

        RuntimeException failure = new IllegalStateException("GUI registration failed");
        JeiOptWorldReadyTask failed = new JeiOptWorldReadyTask(current::get, ready::get, () -> { throw failure; });
        check(failed.getAsBoolean(), "failed work leaves tick queue");
        try { failed.completion().join(); throw new AssertionError("failure lost"); }
        catch (CompletionException expected) { check(expected.getCause() == failure, "failure reaches startup worker"); }

        JeiOptWorldReadyTask invalidated = new JeiOptWorldReadyTask(current::get, ready::get, () -> current.set(false));
        check(invalidated.getAsBoolean() && invalidated.completion().isCancelled(), "in-callback invalidation cannot publish success");
        System.out.println("TooltipWorldReadyTest passed: delayed world, once-only GUI, disconnect, restart, cancellation, failure");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
