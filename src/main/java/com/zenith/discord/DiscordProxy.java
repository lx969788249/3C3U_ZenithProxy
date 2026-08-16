package com.zenith.discord;

import com.neovisionaries.ws.client.WebSocketFactory;
import net.dv8tion.jda.api.JDABuilder;
import okhttp3.OkHttpClient;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Optional;

record DiscordProxy(String host, int port) {
    static Optional<DiscordProxy> fromSystemProperties() {
        var host = System.getProperty("https.proxyHost", "").trim();
        var portValue = System.getProperty("https.proxyPort", "").trim();
        if (host.isEmpty() && portValue.isEmpty()) return Optional.empty();
        if (host.isEmpty() || portValue.isEmpty()) {
            throw new IllegalArgumentException("Both https.proxyHost and https.proxyPort are required for the Discord proxy");
        }
        final int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Discord proxy port: " + portValue, e);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Discord proxy port is outside the valid range: " + port);
        }
        return Optional.of(new DiscordProxy(host, port));
    }

    OkHttpClient.Builder newHttpClientBuilder() {
        return new OkHttpClient.Builder()
            .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
    }

    WebSocketFactory newWebSocketFactory() {
        var factory = new WebSocketFactory();
        factory.getProxySettings().setHost(host).setPort(port);
        return factory;
    }

    void apply(final JDABuilder builder) {
        builder
            .setHttpClientBuilder(newHttpClientBuilder())
            .setWebsocketFactory(newWebSocketFactory());
    }
}
