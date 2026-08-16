package com.zenith.feature.player;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TeleportQueueRecoveryTest {
    @Test
    void queuedOfflineStyleControllingDisconnectSchedulesOneExistingResyncBatchPerEventLoopTask() {
        var eventLoopTasks = new ArrayDeque<Runnable>();
        var resyncBatches = new AtomicInteger();
        var botOnline = new AtomicBoolean(false);

        TeleportQueueRecovery.scheduleAfterControllingPlayerDisconnect(
            true,
            false,
            () -> true,
            () -> false,
            () -> {
                assertFalse(botOnline.get(), "recovery must not require the bot's online/tick state");
                return resyncBatches.incrementAndGet() < 3;
            },
            eventLoopTasks::add);

        assertEquals(1, eventLoopTasks.size(), "recovery must be enqueued on the upstream event loop");
        assertEquals(0, resyncBatches.get(), "resync must not run synchronously during disconnect handling");

        eventLoopTasks.removeFirst().run();
        assertEquals(1, resyncBatches.get());
        assertEquals(1, eventLoopTasks.size(), "each existing resync batch schedules exactly one next event-loop task");

        eventLoopTasks.removeFirst().run();
        assertEquals(2, resyncBatches.get());
        assertEquals(1, eventLoopTasks.size(), "each existing resync batch schedules exactly one next event-loop task");

        eventLoopTasks.removeFirst().run();

        assertEquals(3, resyncBatches.get());
        assertEquals(0, eventLoopTasks.size());
    }

    @Test
    void disconnectedUpstreamDoesNotRunAnExistingResyncBatch() {
        var eventLoopTasks = new ArrayDeque<Runnable>();
        var resyncBatches = new AtomicInteger();
        var upstreamConnected = new AtomicBoolean(true);

        TeleportQueueRecovery.scheduleAfterControllingPlayerDisconnect(
            true,
            false,
            upstreamConnected::get,
            () -> false,
            () -> {
                resyncBatches.incrementAndGet();
                return false;
            },
            eventLoopTasks::add);
        upstreamConnected.set(false);
        eventLoopTasks.removeFirst().run();

        assertEquals(0, resyncBatches.get(), "upstream cache reset owns cleanup after disconnect");
        assertEquals(0, eventLoopTasks.size());
    }

    @Test
    void nonPlayerOrSpectatorDisconnectDoesNotScheduleRecovery() {
        var eventLoopTasks = new ArrayDeque<Runnable>();
        var resyncBatches = new AtomicInteger();

        TeleportQueueRecovery.scheduleAfterControllingPlayerDisconnect(
            false,
            false,
            () -> true,
            () -> false,
            () -> {
                resyncBatches.incrementAndGet();
                return false;
            },
            eventLoopTasks::add);
        TeleportQueueRecovery.scheduleAfterControllingPlayerDisconnect(
            true,
            true,
            () -> true,
            () -> false,
            () -> {
                resyncBatches.incrementAndGet();
                return false;
            },
            eventLoopTasks::add);

        assertEquals(0, eventLoopTasks.size());
        assertEquals(0, resyncBatches.get());
    }

    @Test
    void shuttingDownUpstreamEventLoopDoesNotScheduleRecovery() {
        var eventLoopTasks = new ArrayDeque<Runnable>();
        var resyncBatches = new AtomicInteger();

        TeleportQueueRecovery.scheduleAfterControllingPlayerDisconnect(
            true,
            false,
            () -> true,
            () -> true,
            () -> {
                resyncBatches.incrementAndGet();
                return false;
            },
            eventLoopTasks::add);

        assertEquals(0, eventLoopTasks.size());
        assertEquals(0, resyncBatches.get());
    }

    @Test
    void recoveryRunsAtMostFourBatchesAndYieldsToPrequeuedEventLoopWork() {
        var eventLoopTasks = new ArrayDeque<Runnable>();
        var resyncBatches = new AtomicInteger();
        var unrelatedTaskRuns = new AtomicInteger();

        TeleportQueueRecovery.scheduleAfterControllingPlayerDisconnect(
            true,
            false,
            () -> true,
            () -> false,
            () -> {
                resyncBatches.incrementAndGet();
                return true;
            },
            eventLoopTasks::addLast);
        eventLoopTasks.addLast(unrelatedTaskRuns::incrementAndGet);

        eventLoopTasks.removeFirst().run();
        assertEquals(1, resyncBatches.get());
        assertEquals(2, eventLoopTasks.size());

        eventLoopTasks.removeFirst().run();
        assertEquals(1, unrelatedTaskRuns.get(), "the next recovery batch must yield to prequeued work");

        while (!eventLoopTasks.isEmpty()) {
            eventLoopTasks.removeFirst().run();
        }

        assertEquals(TeleportQueueRecovery.MAX_DISCONNECT_RECOVERY_BATCHES, resyncBatches.get());
        assertEquals(0, eventLoopTasks.size(), "the bounded recovery chain must terminate even if work always remains");
    }

    @Test
    void boundedWorkStopsBeforeLaterItemsWhenOwnerBecomesInactive() {
        var active = new AtomicBoolean(true);
        var remainingItems = new AtomicInteger(3);
        var processedItems = new AtomicInteger();

        final boolean hasMore = TeleportQueueRecovery.runBoundedWork(
            25,
            active::get,
            () -> remainingItems.get() > 0,
            () -> {
                remainingItems.decrementAndGet();
                processedItems.incrementAndGet();
                active.set(false);
                return true;
            });

        assertFalse(hasMore);
        assertEquals(1, processedItems.get());
        assertEquals(2, remainingItems.get(), "items after ownership loss must remain unprocessed");
    }
}
