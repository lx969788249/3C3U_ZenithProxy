package com.zenith.feature.queue;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ThreeCThreeUQueueEventDispatcherTest {
    @Test
    void dropsEventQueuedBeforeDisconnectButExecutedAfterGenerationChanged() {
        var tracker = new ThreeCThreeUQueueTracker(Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC));
        var queuedWorkers = new ArrayDeque<Runnable>();
        var consumed = new ArrayList<Integer>();
        var dispatcher = new ThreeCThreeUQueueEventDispatcher(queuedWorkers::add, tracker, event -> consumed.add((Integer) event));

        var oldGeneration = tracker.beginConnect();
        dispatcher.post(oldGeneration, 1);
        assertEquals(1, queuedWorkers.size());

        assertTrue(tracker.resetDisconnected(oldGeneration));
        var currentGeneration = tracker.beginConnect();
        queuedWorkers.removeFirst().run();
        assertTrue(consumed.isEmpty());

        dispatcher.post(currentGeneration, 2);
        queuedWorkers.removeFirst().run();
        assertEquals(List.of(2), consumed);
    }

    @Test
    void preservesSubmissionOrderAndDropsEventsFromInvalidatedGeneration() throws Exception {
        var tracker = new ThreeCThreeUQueueTracker(Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC));
        var baseExecutor = Executors.newFixedThreadPool(4);
        try {
            var consumed = new CopyOnWriteArrayList<Integer>();
            var firstBatch = new CountDownLatch(100);
            var currentGenerationEvent = new CountDownLatch(1);
            var dispatcher = new ThreeCThreeUQueueEventDispatcher(baseExecutor, tracker, event -> {
                var value = (Integer) event;
                consumed.add(value);
                if (value < 100) firstBatch.countDown();
                if (value == 1000) currentGenerationEvent.countDown();
            });

            var firstGeneration = tracker.beginConnect();
            IntStream.range(0, 100).forEach(value -> dispatcher.post(firstGeneration, value));
            assertTrue(firstBatch.await(5, TimeUnit.SECONDS));
            assertEquals(IntStream.range(0, 100).boxed().toList(), new ArrayList<>(consumed));

            assertTrue(tracker.resetDisconnected(firstGeneration));
            var secondGeneration = tracker.beginConnect();
            dispatcher.post(firstGeneration, 999);
            dispatcher.post(secondGeneration, 1000);
            assertTrue(currentGenerationEvent.await(5, TimeUnit.SECONDS));
            assertFalse(consumed.contains(999));
            assertEquals(1000, consumed.getLast());
        } finally {
            baseExecutor.shutdownNow();
            assertTrue(baseExecutor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
