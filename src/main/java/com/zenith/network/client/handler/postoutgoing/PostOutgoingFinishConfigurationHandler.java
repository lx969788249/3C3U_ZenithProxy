package com.zenith.network.client.handler.postoutgoing;

import com.zenith.Proxy;
import com.zenith.event.client.ClientConfigurationEvent;
import com.zenith.event.queue.QueueCompleteEvent;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.PostOutgoingPacketHandler;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundFinishConfigurationPacket;

import static com.zenith.Globals.EVENT_BUS;

public class PostOutgoingFinishConfigurationHandler implements PostOutgoingPacketHandler<ServerboundFinishConfigurationPacket, ClientSession> {
    @Override
    public void accept(final ServerboundFinishConfigurationPacket packet, final ClientSession session) {
        session.getPacketProtocol().setOutboundState(ProtocolState.GAME); // CONFIGURATION -> GAME
        if (Proxy.getInstance().isOn3c3u()) {
            final var tracker = Proxy.getInstance().getThreeCThreeUQueueTracker();
            final var observation = tracker.observeBackendReconfiguration(session.getThreeCThreeUQueueGeneration());
            if (observation.mainServerReached()) {
                session.setInQueue(false);
                if (observation.queueCompleted()) {
                    session.postThreeCThreeUQueueEvent(new QueueCompleteEvent(tracker.queueDuration()));
                }
            }
        }
        EVENT_BUS.post(ClientConfigurationEvent.Exited.INSTANCE);
    }
}
