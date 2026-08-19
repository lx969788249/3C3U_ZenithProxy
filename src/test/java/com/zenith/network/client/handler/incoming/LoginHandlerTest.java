package com.zenith.network.client.handler.incoming;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientPlayReadyEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.zenith.Globals.EVENT_BUS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginHandlerTest {
    @Test
    void publishesOnlineAtLoginForAllServersExcept2b2t() {
        assertTrue(LoginHandler.shouldPublishOnlineAtLogin(false, false));
        assertFalse(LoginHandler.shouldPublishOnlineAtLogin(true, false));
        assertTrue(LoginHandler.shouldPublishOnlineAtLogin(false, true));
        assertFalse(LoginHandler.shouldPublishOnlineAtLogin(true, true));
    }

    @Test
    void publishesPlayReadyIndependentlyOfDeferredOnlineState() {
        var received = new AtomicBoolean(false);
        var subscriber = new Object();
        EVENT_BUS.subscribe(subscriber, EventConsumer.of(ClientPlayReadyEvent.class, event -> received.set(true)));
        try {
            LoginHandler.publishPlayReady();
            assertTrue(received.get());
        } finally {
            EVENT_BUS.unsubscribe(subscriber);
        }
    }
}