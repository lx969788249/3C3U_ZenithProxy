package com.zenith.network.client.handler.incoming;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginHandlerTest {
    @Test
    void publishesOnlineAtLoginOnlyForServersWithoutDeferredQueueDetection() {
        assertTrue(LoginHandler.shouldPublishOnlineAtLogin(false, false));
        assertFalse(LoginHandler.shouldPublishOnlineAtLogin(true, false));
        assertFalse(LoginHandler.shouldPublishOnlineAtLogin(false, true));
        assertFalse(LoginHandler.shouldPublishOnlineAtLogin(true, true));
    }
}