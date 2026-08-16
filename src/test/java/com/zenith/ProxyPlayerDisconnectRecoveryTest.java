package com.zenith;

import com.zenith.event.player.PlayerDisconnectedEvent;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.zenith.Globals.EVENT_BUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProxyPlayerDisconnectRecoveryTest {
    @Test
    void controllingPlayerDisconnectFlowsThroughEventBusToUpstreamEventLoopRecovery() {
        var eventLoopTasks = new ArrayDeque<Runnable>();
        var resyncBatches = new AtomicInteger();
        var botOnline = new AtomicBoolean(false);
        var proxy = new RecoveryTestProxy(
            eventLoopTasks,
            () -> {
                assertFalse(botOnline.get(), "recovery must not require bot ticks or online state");
                resyncBatches.incrementAndGet();
                return false;
            });
        proxy.initEventHandlers();
        try {
            EVENT_BUS.post(new PlayerDisconnectedEvent("test", disconnectedSession(true, false)));

            assertEquals(0, resyncBatches.get(), "the event handler must not resync synchronously");
            assertEquals(1, eventLoopTasks.size(), "the handler must hand recovery to the upstream event loop");

            eventLoopTasks.removeFirst().run();

            assertEquals(1, resyncBatches.get());
            assertFalse(botOnline.get());
        } finally {
            EVENT_BUS.unsubscribe(proxy);
        }
    }

    @Test
    void spectatorDisconnectDoesNotReachRecoveryScheduler() {
        var eventLoopTasks = new ArrayDeque<Runnable>();
        var resyncBatches = new AtomicInteger();
        var proxy = new RecoveryTestProxy(eventLoopTasks, () -> {
            resyncBatches.incrementAndGet();
            return false;
        });
        proxy.initEventHandlers();
        try {
            EVENT_BUS.post(new PlayerDisconnectedEvent("test", disconnectedSession(true, true)));

            assertEquals(0, eventLoopTasks.size());
            assertEquals(0, resyncBatches.get());
        } finally {
            EVENT_BUS.unsubscribe(proxy);
        }
    }

    @Test
    void capturedUpstreamReplacementPreventsQueuedRecoveryFromResyncingOrRescheduling() {
        var eventLoopTasks = new ArrayDeque<Runnable>();
        var resyncBatches = new AtomicInteger();
        var proxy = new RecoveryTestProxy(eventLoopTasks, () -> {
            resyncBatches.incrementAndGet();
            return true;
        });
        proxy.initEventHandlers();
        try {
            EVENT_BUS.post(new PlayerDisconnectedEvent("test", disconnectedSession(true, false)));
            assertEquals(1, eventLoopTasks.size());

            proxy.markCapturedUpstreamInactive();
            eventLoopTasks.removeFirst().run();

            assertEquals(0, resyncBatches.get(), "a replaced captured upstream must not resync");
            assertEquals(0, eventLoopTasks.size(), "a replaced captured upstream must not schedule another batch");
        } finally {
            EVENT_BUS.unsubscribe(proxy);
        }
    }

    private static ServerSession disconnectedSession(final boolean player, final boolean spectator) {
        var session = new ServerSession("localhost", 0, new MinecraftProtocol(), null);
        session.setPlayer(player);
        session.setSpectator(spectator);
        return session;
    }

    private static final class RecoveryTestProxy extends Proxy {
        private final ArrayDeque<Runnable> eventLoopTasks;
        private final AtomicBoolean capturedUpstreamActive = new AtomicBoolean(true);
        private final AtomicBoolean upstreamEventLoopShuttingDown = new AtomicBoolean(false);
        private final java.util.function.BooleanSupplier resyncBatch;

        private RecoveryTestProxy(
            final ArrayDeque<Runnable> eventLoopTasks,
            final java.util.function.BooleanSupplier resyncBatch
        ) {
            this.eventLoopTasks = eventLoopTasks;
            this.resyncBatch = resyncBatch;
        }

        @Override
        protected TeleportQueueRecoveryContext createTeleportQueueRecoveryContext() {
            return new TeleportQueueRecoveryContext(
                capturedUpstreamActive::get,
                upstreamEventLoopShuttingDown::get,
                resyncBatch,
                eventLoopTasks::add);
        }

        private void markCapturedUpstreamInactive() {
            capturedUpstreamActive.set(false);
        }
    }
}
