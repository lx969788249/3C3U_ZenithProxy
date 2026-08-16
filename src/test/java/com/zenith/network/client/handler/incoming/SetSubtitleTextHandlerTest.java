package com.zenith.network.client.handler.incoming;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetSubtitleTextHandlerTest {
    @Test
    void subtitleQueueParserRoutes2b2tAnd3c3uIndependently() {
        assertTrue(SetSubtitleTextHandler.shouldParseQueuePosition(true, false, true));
        assertFalse(SetSubtitleTextHandler.shouldParseQueuePosition(true, false, false));
        assertTrue(SetSubtitleTextHandler.shouldParseQueuePosition(false, true, false));
        assertFalse(SetSubtitleTextHandler.shouldParseQueuePosition(false, false, true));
    }
}