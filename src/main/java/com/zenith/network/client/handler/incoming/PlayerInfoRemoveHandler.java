package com.zenith.network.client.handler.incoming;

import com.zenith.Proxy;
import com.zenith.event.module.ServerPlayerLogoutInVisualRangeEvent;
import com.zenith.event.server.ServerPlayerDisconnectedEvent;
import com.zenith.feature.queue.PlayerActivityEventGate;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoRemovePacket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.EVENT_BUS;

public class PlayerInfoRemoveHandler implements ClientEventLoopPacketHandler<ClientboundPlayerInfoRemovePacket, ClientSession> {
    @Override
    public boolean applyAsync(ClientboundPlayerInfoRemovePacket packet, ClientSession session) {
        List<UUID> profileIds = packet.getProfileIds();
        for (int i = 0; i < profileIds.size(); i++) {
            final UUID profileId = profileIds.get(i);
            Optional<PlayerListEntry> playerEntry = CACHE.getTabListCache().remove(profileId);
            playerEntry.ifPresent(e -> {
                // Cache removal always happens; only queue-phase activity side effects are suppressed.
                final boolean suppressed = Proxy.getInstance().shouldSuppressPlayerActivityAlerts();
                PlayerActivityEventGate.emitIfAllowed(
                    suppressed,
                    new ServerPlayerDisconnectedEvent(e),
                    EVENT_BUS::postAsync);
                CACHE.getEntityCache().getRecentlyRemovedPlayer(e.getProfileId())
                    .filter(entityPlayer -> !entityPlayer.isSelfPlayer())
                    .ifPresent(entityPlayer -> PlayerActivityEventGate.emitIfAllowed(
                        suppressed,
                        new ServerPlayerLogoutInVisualRangeEvent(e, entityPlayer),
                        EVENT_BUS::postAsync));
            });
        }
        return true;
    }
}
