package com.zenith.feature.player;

import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Schedules existing bot teleport-resync batches after the controlling player disconnects.
 * This intentionally has authority only to invoke the supplied existing resync callback; packet
 * handling, bot ticks, online state, movement, and anti-AFK remain outside this collaborator.
 */
public final class TeleportQueueRecovery {
    /** PlayerPositionHandler retains at most 100 teleports and Bot processes 25 per batch. */
    static final int MAX_DISCONNECT_RECOVERY_BATCHES = 4;

    private TeleportQueueRecovery() { }

    public static void scheduleAfterControllingPlayerDisconnect(
        final boolean wasControllingPlayer,
        final boolean wasSpectator,
        final BooleanSupplier upstreamConnected,
        final BooleanSupplier upstreamEventLoopShuttingDown,
        final BooleanSupplier runResyncBatchAndHasMore,
        final Consumer<Runnable> eventLoopScheduler
    ) {
        if (!wasControllingPlayer || wasSpectator || !isUpstreamReady(upstreamConnected, upstreamEventLoopShuttingDown)) return;

        final Runnable recovery = new Runnable() {
            private int batchesRun;

            @Override
            public void run() {
                if (!isUpstreamReady(upstreamConnected, upstreamEventLoopShuttingDown)) return;

                final boolean hasMore = runResyncBatchAndHasMore.getAsBoolean();
                batchesRun++;
                // Schedule at the event-loop tail so unrelated work can run between batches.
                if (hasMore
                    && batchesRun < MAX_DISCONNECT_RECOVERY_BATCHES
                    && isUpstreamReady(upstreamConnected, upstreamEventLoopShuttingDown)) {
                    schedule(eventLoopScheduler, this);
                }
            }
        };
        schedule(eventLoopScheduler, recovery);
    }

    /**
     * Runs bounded recovery work while its owner remains active. Returning false means recovery
     * must stop rather than handing any remaining work to a replacement session.
     */
    static boolean runBoundedWork(
        final int maxItems,
        final BooleanSupplier active,
        final BooleanSupplier hasWork,
        final BooleanSupplier processOne
    ) {
        int processed = 0;
        while (processed < maxItems && hasWork.getAsBoolean()) {
            if (!active.getAsBoolean() || !processOne.getAsBoolean()) return false;
            processed++;
        }
        return active.getAsBoolean() && hasWork.getAsBoolean();
    }

    private static boolean isUpstreamReady(
        final BooleanSupplier upstreamConnected,
        final BooleanSupplier upstreamEventLoopShuttingDown
    ) {
        return upstreamConnected.getAsBoolean() && !upstreamEventLoopShuttingDown.getAsBoolean();
    }

    private static void schedule(final Consumer<Runnable> eventLoopScheduler, final Runnable recovery) {
        try {
            eventLoopScheduler.accept(recovery);
        } catch (final RejectedExecutionException ignored) {
            // The upstream event loop began shutting down after the connection check.
        }
    }
}
