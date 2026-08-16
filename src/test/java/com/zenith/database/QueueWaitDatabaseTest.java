package com.zenith.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueWaitDatabaseTest {
    @Test
    void processesQueueSamplesOnlyFor2b2t() {
        assertTrue(QueueWaitDatabase.shouldProcessQueueEvent(true));
        assertFalse(QueueWaitDatabase.shouldProcessQueueEvent(false));
    }
}