package com.zenith.discord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordProxyTest {
    @AfterEach
    void clearProxyProperties() {
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
    }

    @Test
    void configuresRestAndGatewayWebSocketFromHttpsProxyProperties() {
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", "18080");

        var proxy = DiscordProxy.fromSystemProperties().orElseThrow();
        var httpProxy = proxy.newHttpClientBuilder().build().proxy();
        assertEquals(Proxy.Type.HTTP, httpProxy.type());
        var address = (InetSocketAddress) httpProxy.address();
        assertEquals("127.0.0.1", address.getHostString());
        assertEquals(18080, address.getPort());

        var websocketProxy = proxy.newWebSocketFactory().getProxySettings();
        assertEquals("127.0.0.1", websocketProxy.getHost());
        assertEquals(18080, websocketProxy.getPort());
    }
}
