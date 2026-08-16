package com.zenith.feature.queue;

import java.util.Objects;
import java.util.function.Consumer;

/** Freezes the player-activity suppression decision before handing an event to an asynchronous sink. */
public final class PlayerActivityEventGate {
    private PlayerActivityEventGate() { }

    public static <T> boolean emitIfAllowed(final boolean suppressed,
                                            final T event,
                                            final Consumer<? super T> asynchronousSink) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(asynchronousSink, "asynchronousSink");
        if (suppressed) return false;
        asynchronousSink.accept(event);
        return true;
    }
}
