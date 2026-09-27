package com.tonywww.jeioptimize.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Polled on client ticks. Waiting never blocks the client thread or runs stale startup work. */
public final class JeiOptWorldReadyTask implements BooleanSupplier {
    private final BooleanSupplier current;
    private final BooleanSupplier ready;
    private final Runnable action;
    private final CompletableFuture<Void> completion = new CompletableFuture<>();

    public JeiOptWorldReadyTask(BooleanSupplier current, BooleanSupplier ready, Runnable action) {
        this.current = current;
        this.ready = ready;
        this.action = action;
    }

    public CompletableFuture<Void> completion() { return completion; }

    @Override
    public boolean getAsBoolean() {
        if (completion.isDone()) return true;
        try {
            if (!current.getAsBoolean()) {
                completion.cancel(false);
                return true;
            }
            if (!ready.getAsBoolean()) return false;
            action.run();
            if (current.getAsBoolean()) completion.complete(null);
            else completion.cancel(false);
        } catch (Throwable failure) {
            completion.completeExceptionally(failure);
        }
        return true;
    }
}
