package com.zenith.network.client.handler.incoming;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetSubtitleTextHandlerTest {
    @Test
    void legacySubtitleQueueParserRunsOnlyFor2b2tQueue() {
        assertTrue(SetSubtitleTextHandler.shouldParseQueuePosition(true, true));
        assertFalse(SetSubtitleTextHandler.shouldParseQueuePosition(false, true));
        assertFalse(SetSubtitleTextHandler.shouldParseQueuePosition(true, false));
    }
}