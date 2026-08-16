package com.zenith.feature.queue;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Delivers 3c3u queue lifecycle events in submission order without blocking the client packet loop.
 * Events belonging to a disconnected or superseded connection generation are discarded atomically.
 */
public final class ThreeCThreeUQueueEventDispatcher {
    private final Executor serialExecutor;
    private final ThreeCThreeUQueueTracker tracker;
    private final Consumer<Object> eventSink;

    public ThreeCThreeUQueueEventDispatcher(final Executor baseExecutor,
                                            final ThreeCThreeUQueueTracker tracker,
                                            final Consumer<Object> eventSink) {
        this.serialExecutor = MoreExecutors.newSequentialExecutor(Objects.requireNonNull(baseExecutor));
        this.tracker = Objects.requireNonNull(tracker);
        this.eventSink = Objects.requireNonNull(eventSink);
    }

    public void post(final long generation, final Object event) {
        serialExecutor.execute(() -> tracker.runIfCurrentGeneration(generation, () -> eventSink.accept(event)));
    }
}
