package com.zenith.network.client.handler.incoming;

import com.zenith.Proxy;
import com.zenith.event.server.ServerRestartingEvent;
import com.zenith.event.queue.QueuePositionUpdateEvent;
import com.zenith.event.queue.QueueStartEvent;
import com.zenith.feature.queue.ThreeCThreeUQueueTracker;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import com.zenith.util.ComponentSerializer;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;

import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static com.zenith.Globals.CLIENT_LOG;
import static com.zenith.Globals.EVENT_BUS;

public class SetActionBarTextHandler implements ClientEventLoopPacketHandler<ClientboundSetActionBarTextPacket, ClientSession> {
    private Instant lastRestartEvent = Instant.EPOCH;

    @Override
    public boolean applyAsync(final ClientboundSetActionBarTextPacket packet, final ClientSession session) {
        if (Proxy.getInstance().isOn2b2t()) parse2bRestart(packet, session);
        else if (Proxy.getInstance().isOn3c3u()) parse3c3uQueue(packet, session);
        return true;
    }

    private void parse3c3uQueue(final ClientboundSetActionBarTextPacket packet, final ClientSession session) {
        final String plainText = ComponentSerializer.serializePlain(packet.getText());
        final var tracker = Proxy.getInstance().getThreeCThreeUQueueTracker();
        final ThreeCThreeUQueueTracker.Observation observation = tracker.observeActionbar(session.getThreeCThreeUQueueGeneration(), plainText);
        if (observation.queueStarted()) {
            session.setInQueue(true);
            session.setOnline(false);
            session.postThreeCThreeUQueueEvent(new QueueStartEvent(false, Duration.ZERO));
        }
        if (observation.positionChanged()) {
            tracker.snapshot().position().ifPresent(position -> session.postThreeCThreeUQueueEvent(new QueuePositionUpdateEvent(position)));
        }
    }

    private void parse2bRestart(ClientboundSetActionBarTextPacket serverTitlePacket, final ClientSession session) {
        try {
            Optional.of(serverTitlePacket)
                .map(title -> ComponentSerializer.serializePlain(title.getText()))
                .filter(text -> text.toLowerCase().contains("restart"))
                .ifPresent(text -> {
                    if (lastRestartEvent.isBefore(Instant.now().minus(1, ChronoUnit.MINUTES))) {
                        lastRestartEvent = Instant.now();
                        EVENT_BUS.postAsync(new ServerRestartingEvent(text));
                    }
                });
        } catch (final Exception e) {
            CLIENT_LOG.warn("Error parsing restart message from title packet", e);
        }
    }
}
