package com.zenith.feature.queue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerActivityEventGateTest {
    @Test
    void mainEventAlreadySubmittedStillDeliversAfterImmediateRequeue() {
        List<Runnable> asynchronousTasks = new ArrayList<>();
        List<String> delivered = new ArrayList<>();

        var submitted = PlayerActivityEventGate.emitIfAllowed(
            false,
            "main-event",
            event -> asynchronousTasks.add(() -> delivered.add(event)));

        var nowSuppressedByRequeue = true;
        assertTrue(submitted);
        assertTrue(nowSuppressedByRequeue);
        asynchronousTasks.forEach(Runnable::run);
        assertEquals(List.of("main-event"), delivered);
    }

    @Test
    void queueEventIsNeverSubmitted() {
        List<String> submitted = new ArrayList<>();

        var emitted = PlayerActivityEventGate.emitIfAllowed(true, "queue-event", submitted::add);

        assertFalse(emitted);
        assertTrue(submitted.isEmpty());
    }
}