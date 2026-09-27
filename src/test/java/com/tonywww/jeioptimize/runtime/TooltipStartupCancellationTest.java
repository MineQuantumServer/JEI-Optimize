package com.tonywww.jeioptimize.runtime;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TooltipStartupCancellationTest {
    public static void main(String[] args) throws Exception {
        CancellationException cancelled = new CancellationException("world changed while sealing index");
        check(JeiOptExecutors.isJeiStartCancellation(cancelled), "world-change cancellation is not a crash");
        check(JeiOptExecutors.isJeiStartCancellation(new CompletionException(new ExecutionException(cancelled))),
            "nested future wrappers preserve cancellation");
        check(!JeiOptExecutors.isJeiStartCancellation(new CompletionException(new NullPointerException())),
            "real plugin failures are not hidden as cancellations");

        CompletableFuture<Void> seal = CompletableFuture.completedFuture(null).thenRun(() -> { throw cancelled; });
        CompletableFuture<Boolean> classified = new CompletableFuture<>();
        Future<?> first = JeiOptExecutors.runJeiStartAsync(JeiOptRuntimeState.currentGeneration(), () -> {
            try { JeiOptExecutors.awaitJeiStartTask(seal); classified.complete(false); }
            catch (Throwable failure) { classified.complete(JeiOptExecutors.isJeiStartCancellation(failure)); }
        });
        check(classified.get(3, TimeUnit.SECONDS), "actual async index cancellation reaches the cancellation path");
        first.get(3, TimeUnit.SECONDS);

        JeiOptExecutors.configureWorkerThreads(1);
        CountDownLatch workerBusy = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicBoolean queuedRan = new AtomicBoolean();
        AtomicBoolean stopped = new AtomicBoolean();
        CountDownLatch waiterStarted = new CountDownLatch(1);
        CountDownLatch waiterStopped = new CountDownLatch(1);
        try {
            JeiOptExecutors.workerExecutor().submit(() -> {
                workerBusy.countDown();
                try { releaseWorker.await(); }
                catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
            });
            check(workerBusy.await(3, TimeUnit.SECONDS), "worker blocked for queued-task reproduction");
            CompletableFuture<Void> queued = JeiOptExecutors.runAsync(() -> queuedRan.set(true));
            JeiOptRuntimeState.track(queued);
            JeiOptExecutors.runJeiStartAsync(JeiOptRuntimeState.currentGeneration(), () -> {
                waiterStarted.countDown();
                try { JeiOptExecutors.awaitJeiStartTask(CompletableFuture.allOf(queued)); }
                catch (Throwable failure) { stopped.set(JeiOptExecutors.isJeiStartCancellation(failure)); }
                finally { waiterStopped.countDown(); }
            });
            check(waiterStarted.await(3, TimeUnit.SECONDS), "startup worker waiting for recipe work");
            JeiOptExecutors.cancelJeiStart();
            JeiOptRuntimeState.invalidate();
            JeiOptExecutors.shutdownWorkerExecutor();
            check(waiterStopped.await(3, TimeUnit.SECONDS) && stopped.get(), "disconnect releases startup waiter");
            check(queued.isCancelled() && !queuedRan.get(), "discarded queued work cannot strand its future");
            AtomicBoolean restarted = new AtomicBoolean();
            JeiOptExecutors.runJeiStartAsync(JeiOptRuntimeState.currentGeneration(), () -> restarted.set(true))
                .get(3, TimeUnit.SECONDS);
            check(restarted.get(), "next startup runs after cancellation");
        } finally {
            releaseWorker.countDown();
            JeiOptExecutors.cancelJeiStart();
            JeiOptExecutors.shutdownWorkerExecutor();
        }
        System.out.println("TooltipStartupCancellationTest passed: wrapped cancellation, fatal-error distinction, queued warmup, disconnect/restart");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
