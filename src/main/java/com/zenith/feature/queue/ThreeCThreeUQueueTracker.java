package com.zenith.feature.queue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Packet-driven 3c3u queue state. A connection generation owns all observations so queued
 * handler work from a disconnected or superseded client cannot alter the current state.
 */
public final class ThreeCThreeUQueueTracker {
    public static final Duration FRESHNESS_WINDOW = Duration.ofMinutes(2);
    private static final int MAX_QUEUE_VALUE = 1_000_000;
    private static final int MAX_SOURCE_UTF8_BYTES = 512;
    private static final Pattern PERSONAL_POSITION = Pattern.compile("(?:正在排队\\s{0,8})?位置\\s{0,8}[：:]\\s{0,8}([1-9][0-9]{0,6})(?![0-9A-Za-z])");
    private static final Pattern QUEUE_TOTAL = Pattern.compile("(?i)\\b([1-9][0-9]{0,6})\\s+in\\s+queue\\b");
    private static final Pattern QUEUE_MARKER = Pattern.compile("(?iU)\\bin\\s+queue\\b");
    private static final Pattern THREE_C_THREE_U_HOST = Pattern.compile("(?i)(?<![A-Za-z0-9.-])(?:[A-Za-z0-9-]+\\.)*3c3u\\.org(?![A-Za-z0-9.-])");
    private static final Observation NO_OBSERVATION = new Observation(false, false, false, false);

    public enum Phase { UNKNOWN, CONNECTING, QUEUE, MAIN_SERVER }

    public record Snapshot(Phase phase, Optional<Integer> position, Optional<Integer> queueTotal, Instant queueStartedAt,
                           Optional<Instant> positionUpdatedAt, Optional<Instant> totalUpdatedAt,
                           Duration queueDuration) { }
    public record Observation(boolean queueStarted, boolean positionChanged, boolean mainServerReached, boolean queueCompleted) { }

    private final Clock clock;
    private long generation;
    private volatile State state = State.unknown();

    public ThreeCThreeUQueueTracker() {
        this(Clock.systemUTC());
    }

    public ThreeCThreeUQueueTracker(final Clock clock) {
        this.clock = clock;
    }

    /** Begins and returns the only generation allowed to update this connection's queue state. */
    public synchronized long beginConnect() {
        generation++;
        state = State.connecting();
        return generation;
    }

    /** Invalidates a disconnected connection only when it still owns the current generation. */
    public synchronized boolean resetDisconnected(final long observationGeneration) {
        if (!owns(observationGeneration)) return false;
        generation++;
        state = State.unknown();
        return true;
    }

    /** Executes a queued lifecycle delivery only while its connection generation still owns this tracker. */
    public synchronized boolean runIfCurrentGeneration(final long observationGeneration, final Runnable action) {
        if (!owns(observationGeneration)) return false;
        action.run();
        return true;
    }

    public synchronized Observation observeActionbar(final long observationGeneration, final String text) {
        if (!owns(observationGeneration)) return NO_OBSERVATION;
        var position = parsePersonalPosition(text);
        if (position.isEmpty()) return NO_OBSERVATION;
        var now = clock.instant();
        var queueStarted = state.phase != Phase.QUEUE;
        var startedAt = queueStarted ? now : state.queueStartedAt;
        var total = queueStarted ? null : state.total;
        var totalUpdatedAt = queueStarted ? null : state.totalUpdatedAt;
        var positionChanged = !Objects.equals(state.position, position.get());
        state = new State(Phase.QUEUE, position.get(), now, total, totalUpdatedAt, startedAt, null);
        return new Observation(queueStarted, positionChanged, false, false);
    }

    public synchronized Observation observeFooter(final long observationGeneration, final String footer) {
        if (!owns(observationGeneration) || footer == null || footer.isBlank() || !sourceWithinLimit(footer)) return NO_OBSERVATION;
        var total = parseQueueTotal(footer);
        if (total.isPresent()) {
            var now = clock.instant();
            var queueStarted = state.phase != Phase.QUEUE;
            var startedAt = queueStarted ? now : state.queueStartedAt;
            var position = queueStarted ? null : state.position;
            var positionUpdatedAt = queueStarted ? null : state.positionUpdatedAt;
            state = new State(Phase.QUEUE, position, positionUpdatedAt, total.get(), now, startedAt, null);
            return new Observation(queueStarted, false, false, false);
        }
        // A malformed queue footer is not evidence that the player reached the main server.
        if (QUEUE_MARKER.matcher(footer).find()) return NO_OBSERVATION;
        var now = clock.instant();
        var queueCompleted = state.phase == Phase.QUEUE;
        var completedDuration = queueCompleted ? queueDurationAt(state.queueStartedAt, now) : state.completedQueueDuration;
        state = new State(Phase.MAIN_SERVER, null, null, null, null, state.queueStartedAt, completedDuration);
        return new Observation(false, false, true, queueCompleted);
    }

    public Snapshot snapshot() {
        return snapshot(clock.instant());
    }

    public Snapshot snapshot(final Instant now) {
        var snapshotState = state;
        return new Snapshot(
            snapshotState.phase,
            freshValue(snapshotState.position, snapshotState.positionUpdatedAt, now),
            freshValue(snapshotState.total, snapshotState.totalUpdatedAt, now),
            snapshotState.queueStartedAt,
            Optional.ofNullable(snapshotState.positionUpdatedAt),
            Optional.ofNullable(snapshotState.totalUpdatedAt),
            snapshotState.completedQueueDuration != null
                ? snapshotState.completedQueueDuration
                : queueDurationAt(snapshotState.queueStartedAt, now)
        );
    }

    public Duration queueDuration() {
        var snapshotState = state;
        return snapshotState.completedQueueDuration != null
            ? snapshotState.completedQueueDuration
            : queueDurationAt(snapshotState.queueStartedAt, clock.instant());
    }

    /** Shared 3c3u display contract for commands, presence, embeds, and tab injection. */
    public String formatStatus(final Instant now) {
        return formatStatus(snapshot(now), now);
    }

    public String formatStatus(final Snapshot snapshot, final Instant now) {
        return "3C3U queue: " + snapshot.phase()
            + " | Position: " + formatValue(snapshot.position(), snapshot.positionUpdatedAt(), now)
            + " | Total: " + formatValue(snapshot.queueTotal(), snapshot.totalUpdatedAt(), now)
            + " | Wait: " + Queue.getEtaStringFromSeconds(snapshot.queueDuration().toSeconds())
            + " | Latest update: " + formatLatestUpdate(snapshot.positionUpdatedAt(), snapshot.totalUpdatedAt(), now);
    }

    public String formatTabListSummary() {
        return formatStatus(clock.instant());
    }

    public String formatTabListSummary(final Instant now) {
        return formatTabListSummary(snapshot(now), now);
    }

    public String formatTabListSummary(final Snapshot snapshot, final Instant now) {
        return formatStatus(snapshot, now);
    }

    /** Compact Discord presence contract; intentionally stays well below the custom-status limit. */
    public String formatPresence(final Instant now) {
        return formatPresence(snapshot(now), now);
    }

    public String formatPresence(final Snapshot snapshot, final Instant now) {
        return "3C3U " + snapshot.phase()
            + " P:" + snapshot.position().map(String::valueOf).orElse("?")
            + " T:" + snapshot.queueTotal().map(String::valueOf).orElse("?")
            + " W:" + Queue.getEtaStringFromSeconds(snapshot.queueDuration().toSeconds())
            + " U:" + latestFreshness(snapshot.positionUpdatedAt(), snapshot.totalUpdatedAt(), now);
    }

    public static Optional<Integer> parsePersonalPosition(final String text) {
        return extractSingleBoundedValue(PERSONAL_POSITION, text);
    }

    public static Optional<Integer> parseQueueTotal(final String footer) {
        if (footer == null || !THREE_C_THREE_U_HOST.matcher(footer).find()) return Optional.empty();
        return extractSingleBoundedValue(QUEUE_TOTAL, footer);
    }

    public static boolean shouldSuppressPlayerActivityAlerts(final boolean isThreeCThreeU, final Phase phase) {
        return isThreeCThreeU && phase != Phase.MAIN_SERVER;
    }

    private boolean owns(final long observationGeneration) {
        return observationGeneration == generation;
    }

    private static Optional<Integer> extractSingleBoundedValue(final Pattern pattern, final String text) {
        if (text == null || !sourceWithinLimit(text)) return Optional.empty();
        var matcher = pattern.matcher(text);
        Integer value = null;
        while (matcher.find()) {
            if (value != null) return Optional.empty();
            try {
                var candidate = Integer.parseInt(matcher.group(1));
                if (candidate < 1 || candidate > MAX_QUEUE_VALUE) return Optional.empty();
                value = candidate;
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(value);
    }

    private static boolean sourceWithinLimit(final String text) {
        return text.getBytes(StandardCharsets.UTF_8).length <= MAX_SOURCE_UTF8_BYTES;
    }

    private boolean isFresh(final Instant updatedAt, final Instant now) {
        return updatedAt != null && !updatedAt.plus(FRESHNESS_WINDOW).isBefore(now);
    }

    private Optional<Integer> freshValue(final Integer value, final Instant updatedAt, final Instant now) {
        return isFresh(updatedAt, now) ? Optional.ofNullable(value) : Optional.empty();
    }

    private String formatValue(final Optional<Integer> value, final Optional<Instant> updatedAt, final Instant now) {
        return value.map(String::valueOf).orElse("unknown") + " (" + freshness(updatedAt, now) + ")";
    }

    private String formatLatestUpdate(final Optional<Instant> positionUpdatedAt, final Optional<Instant> totalUpdatedAt, final Instant now) {
        Optional<Instant> latest;
        if (positionUpdatedAt.isEmpty()) latest = totalUpdatedAt;
        else if (totalUpdatedAt.isEmpty()) latest = positionUpdatedAt;
        else latest = positionUpdatedAt.get().isAfter(totalUpdatedAt.get()) ? positionUpdatedAt : totalUpdatedAt;
        return latest.map(updatedAt -> updatedAt + " (" + freshness(Optional.of(updatedAt), now) + ")").orElse("never");
    }

    private String latestFreshness(final Optional<Instant> positionUpdatedAt, final Optional<Instant> totalUpdatedAt, final Instant now) {
        Optional<Instant> latest;
        if (positionUpdatedAt.isEmpty()) latest = totalUpdatedAt;
        else if (totalUpdatedAt.isEmpty()) latest = positionUpdatedAt;
        else latest = positionUpdatedAt.get().isAfter(totalUpdatedAt.get()) ? positionUpdatedAt : totalUpdatedAt;
        return freshness(latest, now);
    }

    private String freshness(final Optional<Instant> updatedAt, final Instant now) {
        if (updatedAt.isEmpty()) return "never";
        return isFresh(updatedAt.get(), now) ? "fresh" : "stale";
    }

    private Duration queueDurationAt(final Instant now) {
        var snapshotState = state;
        return snapshotState.completedQueueDuration != null
            ? snapshotState.completedQueueDuration
            : queueDurationAt(snapshotState.queueStartedAt, now);
    }

    private static Duration queueDurationAt(final Instant startedAt, final Instant now) {
        if (startedAt == null || now.isBefore(startedAt)) return Duration.ZERO;
        return Duration.between(startedAt, now);
    }

    private record State(Phase phase, Integer position, Instant positionUpdatedAt, Integer total, Instant totalUpdatedAt,
                         Instant queueStartedAt, Duration completedQueueDuration) {
        private static State unknown() {
            return new State(Phase.UNKNOWN, null, null, null, null, null, null);
        }

        private static State connecting() {
            return new State(Phase.CONNECTING, null, null, null, null, null, null);
        }
    }
}
