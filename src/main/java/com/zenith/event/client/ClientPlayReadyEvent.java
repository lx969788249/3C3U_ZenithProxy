package com.zenith.event.client;

/**
 * Fired after the initial game login packet has been processed and chat packets
 * can be sent, independently of whether queue detection defers online state.
 */
public record ClientPlayReadyEvent() {
    public static final ClientPlayReadyEvent INSTANCE = new ClientPlayReadyEvent();
}