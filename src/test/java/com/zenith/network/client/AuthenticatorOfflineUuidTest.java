package com.zenith.network.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AuthenticatorOfflineUuidTest {
    @Test
    public void derivesPclCompatibleUuidFromOfflineUsername() {
        assertEquals(
            UUID.fromString("71619e98-2ebc-39de-83b3-335f236cab41"),
            Authenticator.offlineUUID("offline_yongh")
        );
    }
}
