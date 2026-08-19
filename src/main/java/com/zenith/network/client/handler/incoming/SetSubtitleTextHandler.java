package com.zenith.network.client.handler.incoming;

import com.zenith.Proxy;
import com.zenith.event.queue.QueuePositionUpdateEvent;
import com.zenith.event.queue.QueueStartEvent;
import com.zenith.feature.queue.ThreeCThreeUQueueTracker;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import com.zenith.util.ComponentSerializer;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetSubtitleTextPacket;

import java.time.Duration;
import java.util.Optional;

import static com.zenith.Globals.CLIENT_LOG;
import static com.zenith.Globals.EVENT_BUS;

public class SetSubtitleTextHandler implements ClientEventLoopPacketHandler<ClientboundSetSubtitleTextPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundSetSubtitleTextPacket packet, final ClientSession session) {
        final var proxy = Proxy.getInstance();
        final boolean isOn2b2t = proxy.isOn2b2t();
        final boolean isOn3c3u = proxy.isOn3c3u();
        if (shouldParseQueuePosition(isOn2b2t, isOn3c3u, proxy.isInQueue())) {
            if (isOn3c3u) parse3c3uQueuePos(packet, session);
            else parse2bQueuePos(packet, session);
        }
        return true;
    }

    static boolean shouldParseQueuePosition(final boolean isOn2b2t, final boolean isOn3c3u, final boolean isInQueue) {
        return isOn3c3u || (isOn2b2t && isInQueue);
    }

    private void parse3c3uQueuePos(final ClientboundSetSubtitleTextPacket packet, final ClientSession session) {
        final String plainText = ComponentSerializer.serializePlain(packet.getText());
        final var tracker = Proxy.getInstance().getThreeCThreeUQueueTracker();
        final ThreeCThreeUQueueTracker.Observation observation = tracker.observeActionbar(session.getThreeCThreeUQueueGeneration(), plainText);
        if (observation.queueStarted()) {
            session.setInQueue(true);
            session.postThreeCThreeUQueueEvent(new QueueStartEvent(false, Duration.ZERO));
        }
        if (observation.positionChanged()) {
            tracker.snapshot().position().ifPresent(position -> session.postThreeCThreeUQueueEvent(new QueuePositionUpdateEvent(position)));
        }
    }

    private void parse2bQueuePos(ClientboundSetSubtitleTextPacket serverTitlePacket, final ClientSession session) {
        try {
            Optional<Integer> position = Optional.of(serverTitlePacket)
                .map(title -> ComponentSerializer.serializePlain(title.getText()))
                .map(text -> {
                    String[] split = text.split(":");
                    if (split.length > 1) {
                        return split[1].trim();
                    } else {
                        return ""+Integer.MAX_VALUE; // some arbitrarily non-zero value
                    }
                })
                .map(Integer::parseInt);
            if (position.isPresent()) {
                if (position.get() != session.getLastQueuePosition()) {
                    EVENT_BUS.postAsync(new QueuePositionUpdateEvent(position.get()));
                }
                session.setLastQueuePosition(position.get());
            }
        } catch (final Exception e) {
            CLIENT_LOG.warn("Error parsing queue position from title packet", e);
        }
    }
}
