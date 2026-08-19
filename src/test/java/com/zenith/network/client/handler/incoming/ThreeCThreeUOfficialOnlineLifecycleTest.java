package com.zenith.network.client.handler.incoming;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeCThreeUOfficialOnlineLifecycleTest {
    private static final Path SOURCE_ROOT = findSourceRoot();

    @Test
    void queuePacketHandlersDoNotMutateOfficialOnlineState() throws IOException {
        var actionBar = source("com/zenith/network/client/handler/incoming/SetActionBarTextHandler.java");
        assertFalse(actionBar.contains("session.setOnline("));
        assertTrue(actionBar.contains("tracker.observeActionbar("));
        assertTrue(actionBar.contains("session.setInQueue(true)"));
        assertTrue(actionBar.contains("new QueueStartEvent("));
        assertTrue(actionBar.contains("new QueuePositionUpdateEvent("));

        var subtitle = source("com/zenith/network/client/handler/incoming/SetSubtitleTextHandler.java");
        assertFalse(subtitle.contains("session.setOnline("));
        assertTrue(subtitle.contains("tracker.observeActionbar("));
        assertTrue(subtitle.contains("session.setInQueue(true)"));
        assertTrue(subtitle.contains("new QueueStartEvent("));
        assertTrue(subtitle.contains("new QueuePositionUpdateEvent("));

        var tabList = source("com/zenith/network/client/handler/incoming/TabListDataHandler.java");
        var threeCThreeUMethod = tabList.substring(
            tabList.indexOf("private void parse3c3uQueueState"),
            tabList.indexOf("private synchronized void parse2bQueueState"));
        assertFalse(threeCThreeUMethod.contains("session.setOnline("));
        assertTrue(threeCThreeUMethod.contains("tracker.observeFooter("));
        assertTrue(threeCThreeUMethod.contains("session.setInQueue(true)"));
        assertTrue(threeCThreeUMethod.contains("session.setInQueue(false)"));
        assertTrue(threeCThreeUMethod.contains("new QueueStartEvent("));
        assertTrue(threeCThreeUMethod.contains("new QueueCompleteEvent("));
    }

    @Test
    void backendReconfigurationDoesNotMutateOfficialOnlineState() throws IOException {
        var reconfiguration = source("com/zenith/network/client/handler/postoutgoing/PostOutgoingFinishConfigurationHandler.java");
        assertFalse(reconfiguration.contains("session.setOnline("));
        assertTrue(reconfiguration.contains("tracker.observeBackendReconfiguration("));
        assertTrue(reconfiguration.contains("session.setInQueue(false)"));
        assertTrue(reconfiguration.contains("new QueueCompleteEvent("));
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
