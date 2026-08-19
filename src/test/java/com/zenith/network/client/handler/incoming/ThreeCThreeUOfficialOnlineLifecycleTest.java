package com.zenith.network.client.handler.incoming;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ThreeCThreeUOfficialOnlineLifecycleTest {
    private static final Path SOURCE_ROOT = findSourceRoot();

    @Test
    void queuePacketHandlersDoNotMutateOfficialOnlineState() throws IOException {
        assertFalse(source("com/zenith/network/client/handler/incoming/SetActionBarTextHandler.java")
            .contains("session.setOnline("));
        assertFalse(source("com/zenith/network/client/handler/incoming/SetSubtitleTextHandler.java")
            .contains("session.setOnline("));

        var tabList = source("com/zenith/network/client/handler/incoming/TabListDataHandler.java");
        var threeCThreeUMethod = tabList.substring(
            tabList.indexOf("private void parse3c3uQueueState"),
            tabList.indexOf("private synchronized void parse2bQueueState"));
        assertFalse(threeCThreeUMethod.contains("session.setOnline("));
    }

    @Test
    void backendReconfigurationDoesNotMutateOfficialOnlineState() throws IOException {
        assertFalse(source("com/zenith/network/client/handler/postoutgoing/PostOutgoingFinishConfigurationHandler.java")
            .contains("session.setOnline("));
    }

    private static Path findSourceRoot() {
        for (Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath(); current != null; current = current.getParent()) {
            var candidate = current.resolve("src/main/java");
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate src/main/java");
    }

    private static String source(final String relativePath) throws IOException {
        return Files.readString(SOURCE_ROOT.resolve(relativePath));
    }
}
