package com.zenith.feature.queue;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ThreeCThreeUQueueTrackerTest {
    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    @Test
    void parsesOnlyValidChineseActionbarPersonalPositions() {
        assertEquals(172, ThreeCThreeUQueueTracker.parsePersonalPosition("正在排队  位置：172").orElseThrow());
        assertEquals(9, ThreeCThreeUQueueTracker.parsePersonalPosition("正在排队 位置: 9").orElseThrow());
        assertEquals(70, ThreeCThreeUQueueTracker.parsePersonalPosition("位置：70").orElseThrow());
        assertTrue(ThreeCThreeUQueueTracker.parsePersonalPosition("142 in queue 3c3u.org").isEmpty());
    }

    @Test
    void parsesObserved3c3uSubtitlePersonalPosition() {
        assertEquals(165, ThreeCThreeUQueueTracker.parsePersonalPosition("555 Playing  |  Position in queue: 165").orElseThrow());
    }

    @Test
    void actionbarAndSubtitlePersonalPositionsUseTheSameTrackerStateWithoutInventingATotal() {
        var tracker = tracker();
        var generation = tracker.beginConnect();

        assertTrue(tracker.observeActionbar(generation, "正在排队 位置：172").queueStarted());
        assertEquals(172, tracker.snapshot().position().orElseThrow());
        assertTrue(tracker.snapshot().queueTotal().isEmpty());

        assertTrue(tracker.observeActionbar(generation, "555 Playing  |  Position in queue: 165").positionChanged());
        assertEquals(165, tracker.snapshot().position().orElseThrow());
        assertTrue(tracker.snapshot().queueTotal().isEmpty());

        tracker.observeFooter(generation, "142 in queue 3c3u.org");
        assertEquals(165, tracker.snapshot().position().orElseThrow());
        assertEquals(142, tracker.snapshot().queueTotal().orElseThrow());
    }

    @Test
    void rejectsMalformedConflictingAndOutOfRangePersonalPositions() {
        assertTrue(ThreeCThreeUQueueTracker.parsePersonalPosition("正在排队 位置：0").isEmpty());
        assertTrue(ThreeCThreeUQueueTracker.parsePersonalPosition("正在排队 位置：0172").isEmpty());
        assertTrue(ThreeCThreeUQueueTracker.parsePersonalPosition("正在排队 位置：1000001").isEmpty());
        assertTrue(ThreeCThreeUQueueTracker.parsePersonalPosition("正在排队 位置：12abc").isEmpty());
        assertTrue(ThreeCThreeUQueueTracker.parsePersonalPosition("正在排队 位置：12 正在排队 位置：13").isEmpty());
        assertTrue(ThreeCThreeUQueueTracker.parsePersonalPosition("x".repeat(1024) + "位置：172").isEmpty());
    }

    @Test
    void parsesOnlyQueueTotalFromFooterAndNeverUsesItAsPosition() {
        assertEquals(142, ThreeCThreeUQueueTracker.parseQueueTotal("> 594 online players | 142 in queue 3c3u.org 群号523983557").orElseThrow());
        assertTrue(ThreeCThreeUQueueTracker.parseQueueTotal("正在排队 位置：172").isEmpty());
        assertTrue(ThreeCThreeUQueueTracker.parseQueueTotal("142 in queue").isEmpty());
        assertTrue(ThreeCThreeUQueueTracker.parseQueueTotal("142 in queue not3c3u.org").isEmpty());
        assertTrue(ThreeCThreeUQueueTracker.parseQueueTotal("142 in queue 3c3u.org.example").isEmpty());
        assertTrue(ThreeCThreeUQueueTracker.parseQueueTotal("1000001 in queue 3c3u.org").isEmpty());
        var tracker = tracker();
        var generation = tracker.beginConnect();
        var observation = tracker.observeFooter(generation, "> 594 online players | 142 in queue 3c3u.org 群号523983557");
        assertFalse(observation.queueStarted());
        assertFalse(observation.mainServerReached());
        assertFalse(observation.queueCompleted());
        assertEquals(ThreeCThreeUQueueTracker.Phase.CONNECTING, tracker.snapshot().phase());
        assertTrue(tracker.snapshot().position().isEmpty());
        assertEquals(142, tracker.snapshot().queueTotal().orElseThrow());
    }

    @Test
    void personalPositionPreservesTotalAlreadyObservedWhileConnecting() {
        var tracker = tracker();
        var generation = tracker.beginConnect();
        tracker.observeFooter(generation, "142 in queue 3c3u.org");

        var observation = tracker.observeActionbar(generation, "正在排队 位置：172");

        assertTrue(observation.queueStarted());
        assertEquals(ThreeCThreeUQueueTracker.Phase.QUEUE, tracker.snapshot().phase());
        assertEquals(172, tracker.snapshot().position().orElseThrow());
        assertEquals(142, tracker.snapshot().queueTotal().orElseThrow());
    }

    @Test
    void freshGlobalTotalAfterPersonalPositionExpiresCompletesQueue() {
        var clock = new MutableClock(NOW);
        var tracker = new ThreeCThreeUQueueTracker(clock);
        var generation = tracker.beginConnect();
        tracker.observeActionbar(generation, "正在排队 位置：172");

        clock.advance(ThreeCThreeUQueueTracker.FRESHNESS_WINDOW);
        var atBoundary = tracker.observeFooter(generation, "> 594 online players | 91 in queue 3c3u.org 群号523983557");
        assertFalse(atBoundary.mainServerReached());
        assertFalse(atBoundary.queueCompleted());
        assertEquals(ThreeCThreeUQueueTracker.Phase.QUEUE, tracker.snapshot().phase());

        clock.advance(Duration.ofNanos(1));
        var observation = tracker.observeFooter(generation, "> 594 online players | 90 in queue 3c3u.org 群号523983557");

        assertTrue(observation.mainServerReached());
        assertTrue(observation.queueCompleted());
        assertFalse(observation.queueStarted());
        assertEquals(ThreeCThreeUQueueTracker.Phase.MAIN_SERVER, tracker.snapshot().phase());
        assertTrue(tracker.snapshot().position().isEmpty());
        assertEquals(90, tracker.snapshot().queueTotal().orElseThrow());
    }

    @Test
    void globalQueueTotalCannotMoveMainServerBackToQueue() {
        var tracker = tracker();
        var generation = tracker.beginConnect();
        tracker.observeActionbar(generation, "正在排队 位置：1");
        tracker.observeFooter(generation, "Welcome to 3c3u.org");

        var observation = tracker.observeFooter(generation, "> 594 online players | 90 in queue 3c3u.org 群号523983557");

        assertFalse(observation.queueStarted());
        assertFalse(observation.mainServerReached());
        assertFalse(observation.queueCompleted());
        assertEquals(ThreeCThreeUQueueTracker.Phase.MAIN_SERVER, tracker.snapshot().phase());
        assertEquals(90, tracker.snapshot().queueTotal().orElseThrow());
    }

    @Test
    void queueObservationsDeduplicateAndPositionOneIsStillQueue() {
        var tracker = tracker();
        var generation = tracker.beginConnect();
        var first = tracker.observeActionbar(generation, "正在排队 位置：1");
        var repeated = tracker.observeActionbar(generation, "正在排队 位置：1");
        assertTrue(first.queueStarted());
        assertTrue(first.positionChanged());
        assertFalse(repeated.queueStarted());
        assertFalse(repeated.positionChanged());
        assertEquals(ThreeCThreeUQueueTracker.Phase.QUEUE, tracker.snapshot().phase());
        assertEquals(1, tracker.snapshot().position().orElseThrow());
        var totalFirst = tracker.observeFooter(generation, "> 594 online players | 142 in queue 3c3u.org 群号523983557");
        var totalRepeated = tracker.observeFooter(generation, "> 594 online players | 142 in queue 3c3u.org 群号523983557");
        assertFalse(totalFirst.queueStarted());
        assertFalse(totalRepeated.queueStarted());
    }

    @Test
    void repeatedNonCachedIntegerPositionIsDeduplicatedByValue() {
        var tracker = tracker();
        var generation = tracker.beginConnect();
        assertTrue(tracker.observeActionbar(generation, "正在排队 位置：172").positionChanged());
        assertFalse(tracker.observeActionbar(generation, "正在排队 位置：172").positionChanged());
    }

    @Test
    void malformedQueueLikeFooterNeverTransitionsToMainServer() {
        var tracker = tracker();
        var generation = tracker.beginConnect();
        tracker.observeActionbar(generation, "正在排队 位置：172");

        assertFalse(tracker.observeFooter(generation, "0   in   queue 3c3u.org").mainServerReached());
        assertEquals(ThreeCThreeUQueueTracker.Phase.QUEUE, tracker.snapshot().phase());
        assertFalse(tracker.observeFooter(generation, "0\u00a0in\u00a0queue 3c3u.org").mainServerReached());
        assertEquals(ThreeCThreeUQueueTracker.Phase.QUEUE, tracker.snapshot().phase());
    }

    @Test
    void oversizedFooterCannotCompleteQueue() {
        var tracker = tracker();
        var generation = tracker.beginConnect();
        tracker.observeActionbar(generation, "正在排队 位置：172");

        var observation = tracker.observeFooter(generation, "Welcome to 3c3u.org" + "x".repeat(600));

        assertFalse(observation.mainServerReached());
        assertEquals(ThreeCThreeUQueueTracker.Phase.QUEUE, tracker.snapshot().phase());

        var connectingTracker = tracker();
        var connectingGeneration = connectingTracker.beginConnect();
        var oversizedUtf8QueueFooter = "队".repeat(170) + " 142 in queue 3c3u.org";
        var queueObservation = connectingTracker.observeFooter(connectingGeneration, oversizedUtf8QueueFooter);
        assertFalse(queueObservation.queueStarted());
        assertEquals(ThreeCThreeUQueueTracker.Phase.CONNECTING, connectingTracker.snapshot().phase());
    }

    @Test
    void blankFooterIsNotMainButNonQueueFooterIs() {
        var tracker = tracker();
        var generation = tracker.beginConnect();
        tracker.observeActionbar(generation, "正在排队 位置：1");
        assertFalse(tracker.observeFooter(generation, "   ").mainServerReached());
        assertEquals(ThreeCThreeUQueueTracker.Phase.QUEUE, tracker.snapshot().phase());
        assertTrue(tracker.observeFooter(generation, "Welcome to 3c3u.org").mainServerReached());
        assertEquals(ThreeCThreeUQueueTracker.Phase.MAIN_SERVER, tracker.snapshot().phase());
        assertTrue(tracker.snapshot().position().isEmpty());
        assertTrue(tracker.snapshot().queueTotal().isEmpty());
    }

    @Test
    void staleQueueValuesBecomeUnavailableAndResetClearsEverything() {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var tracker = new ThreeCThreeUQueueTracker(clock);
        var generation = tracker.beginConnect();
        tracker.observeActionbar(generation, "正在排队 位置：172");
        tracker.observeFooter(generation, "> 594 online players | 142 in queue 3c3u.org 群号523983557");
        var stale = tracker.snapshot(NOW.plus(ThreeCThreeUQueueTracker.FRESHNESS_WINDOW).plusSeconds(1));
        assertEquals(ThreeCThreeUQueueTracker.Phase.QUEUE, stale.phase());
        assertTrue(stale.position().isEmpty());
        assertTrue(stale.queueTotal().isEmpty());
        assertTrue(tracker.resetDisconnected(generation));
        assertEquals(ThreeCThreeUQueueTracker.Phase.UNKNOWN, tracker.snapshot().phase());
        assertTrue(tracker.snapshot().position().isEmpty());
    }

    @Test
    void suppressionAppliesBeforeMainOnlyForThreeCThreeU() {
        assertTrue(ThreeCThreeUQueueTracker.shouldSuppressPlayerActivityAlerts(true, ThreeCThreeUQueueTracker.Phase.CONNECTING));
        assertTrue(ThreeCThreeUQueueTracker.shouldSuppressPlayerActivityAlerts(true, ThreeCThreeUQueueTracker.Phase.QUEUE));
        assertFalse(ThreeCThreeUQueueTracker.shouldSuppressPlayerActivityAlerts(true, ThreeCThreeUQueueTracker.Phase.MAIN_SERVER));
        assertFalse(ThreeCThreeUQueueTracker.shouldSuppressPlayerActivityAlerts(false, ThreeCThreeUQueueTracker.Phase.QUEUE));
    }


    @Test
    void displayIncludesPhaseValuesWaitLatestUpdateAndFreshnessWithoutEta() {
        var clock = new MutableClock(NOW);
        var tracker = new ThreeCThreeUQueueTracker(clock);
        var generation = tracker.beginConnect();
        assertEquals("3C3U queue: CONNECTING | Position: unknown (never) | Total: unknown (never) | Wait: 00:00:00 | Latest update: never", tracker.formatStatus(NOW));
        clock.advance(Duration.ofSeconds(30));
        assertTrue(tracker.formatStatus(clock.instant()).contains("Wait: 00:00:00"));
        tracker.observeActionbar(generation, "正在排队 位置：172");
        assertEquals("3C3U queue: QUEUE | Position: 172 (fresh) | Total: unknown (never) | Wait: 00:00:00 | Latest update: 2026-08-16T00:00:30Z (fresh)", tracker.formatStatus(clock.instant()));
        var now = clock.instant();
        var snapshot = tracker.snapshot(now);
        assertEquals(tracker.formatStatus(snapshot, now), tracker.formatTabListSummary(snapshot, now));
        assertFalse(tracker.formatStatus(snapshot, now).contains("ETA"));
        var presence = tracker.formatPresence(snapshot, now);
        assertTrue(presence.contains("3C3U QUEUE"));
        assertTrue(presence.contains("P:172"));
        assertTrue(presence.contains("T:?"));
        assertTrue(presence.contains("W:00:00:00"));
        assertTrue(presence.contains("U:fresh"));
        assertTrue(presence.length() <= 128);
    }

    @Test
    void capturedSnapshotKeepsPresenceConsistentAfterTrackerChanges() {
        var tracker = tracker();
        var generation = tracker.beginConnect();
        tracker.observeActionbar(generation, "正在排队 位置：172");
        var captured = tracker.snapshot(NOW);

        tracker.observeFooter(generation, "Welcome to 3c3u.org");

        var presence = tracker.formatPresence(captured, NOW);
        assertTrue(presence.contains("3C3U QUEUE"));
        assertTrue(presence.contains("P:172"));
        assertFalse(presence.contains("MAIN_SERVER"));
    }

    @Test
    void presenceStaysWithinDiscordLimitAtMaximumValuesAndLongWait() {
        var snapshot = new ThreeCThreeUQueueTracker.Snapshot(
            ThreeCThreeUQueueTracker.Phase.QUEUE,
            Optional.of(1_000_000),
            Optional.of(1_000_000),
            NOW,
            Optional.of(NOW),
            Optional.of(NOW),
            Duration.ofDays(365_000));

        var presence = tracker().formatPresence(snapshot, NOW);
        assertFalse(presence.contains("ETA"));
        assertTrue(presence.length() <= 128, presence);
    }

    @Test
    void ignoresStaleGenerationObservationsAfterDisconnectAndNewConnect() {
        var tracker = tracker();
        var firstGeneration = tracker.beginConnect();
        tracker.observeActionbar(firstGeneration, "正在排队 位置：172");
        assertTrue(tracker.resetDisconnected(firstGeneration));
        assertFalse(tracker.observeActionbar(firstGeneration, "正在排队 位置：1").queueStarted());
        assertEquals(ThreeCThreeUQueueTracker.Phase.UNKNOWN, tracker.snapshot().phase());

        var secondGeneration = tracker.beginConnect();
        assertNotEquals(firstGeneration, secondGeneration);
        assertFalse(tracker.observeFooter(firstGeneration, "142 in queue 3c3u.org").queueStarted());
        assertEquals(ThreeCThreeUQueueTracker.Phase.CONNECTING, tracker.snapshot().phase());
        assertFalse(tracker.observeFooter(secondGeneration, "142 in queue 3c3u.org").queueStarted());
        assertEquals(ThreeCThreeUQueueTracker.Phase.CONNECTING, tracker.snapshot().phase());
        assertEquals(142, tracker.snapshot().queueTotal().orElseThrow());
    }

    @Test
    void backendReconfigurationWhileConnectingMarksMainWithoutCompletingQueue() {
        var tracker = tracker();
        var generation = tracker.beginConnect();

        var observation = tracker.observeBackendReconfiguration(generation);

        assertTrue(observation.mainServerReached());
        assertFalse(observation.queueStarted());
        assertFalse(observation.queueCompleted());
        assertEquals(ThreeCThreeUQueueTracker.Phase.MAIN_SERVER, tracker.snapshot().phase());
        assertEquals(Duration.ZERO, tracker.queueDuration());
    }

    @Test
    void backendReconfigurationAfterQueueMarksMainServerReached() {
        var clock = new MutableClock(NOW);
        var tracker = new ThreeCThreeUQueueTracker(clock);
        var generation = tracker.beginConnect();
        tracker.observeActionbar(generation, "正在排队 位置：172");
        clock.advance(Duration.ofSeconds(10));

        var observation = tracker.observeBackendReconfiguration(generation);

        assertTrue(observation.mainServerReached());
        assertTrue(observation.queueCompleted());
        assertFalse(observation.queueStarted());
        assertEquals(ThreeCThreeUQueueTracker.Phase.MAIN_SERVER, tracker.snapshot().phase());
        assertEquals(Duration.ofSeconds(10), tracker.queueDuration());
    }

    @Test
    void requeueFromMainStartsANewQueueAndFreezesCompletedWait() {
        var clock = new MutableClock(NOW);
        var tracker = new ThreeCThreeUQueueTracker(clock);
        var generation = tracker.beginConnect();
        tracker.observeActionbar(generation, "正在排队 位置：1");
        clock.advance(Duration.ofSeconds(10));
        var completed = tracker.observeFooter(generation, "Welcome to 3c3u.org");
        assertTrue(completed.mainServerReached());
        assertTrue(completed.queueCompleted());
        assertEquals(Duration.ofSeconds(10), tracker.queueDuration());
        clock.advance(Duration.ofSeconds(20));
        assertEquals(Duration.ofSeconds(10), tracker.queueDuration());

        var requeue = tracker.observeActionbar(generation, "正在排队 位置：1");
        assertTrue(requeue.queueStarted());
        assertEquals(ThreeCThreeUQueueTracker.Phase.QUEUE, tracker.snapshot().phase());
        clock.advance(Duration.ofSeconds(5));
        assertEquals(Duration.ofSeconds(5), tracker.queueDuration());
    }

    @Test
    void latestUpdateUsesTheNewerFieldAndMarksIndividualStaleValues() {
        var clock = new MutableClock(NOW);
        var tracker = new ThreeCThreeUQueueTracker(clock);
        var generation = tracker.beginConnect();
        tracker.observeActionbar(generation, "正在排队 位置：172");
        clock.advance(Duration.ofSeconds(30));
        tracker.observeFooter(generation, "142 in queue 3c3u.org");

        var staleAt = NOW.plus(ThreeCThreeUQueueTracker.FRESHNESS_WINDOW).plusSeconds(1);
        var display = tracker.formatStatus(staleAt);
        assertTrue(display.contains("Position: unknown (stale)"));
        assertTrue(display.contains("Total: 142 (fresh)"));
        assertTrue(display.contains("Latest update: 2026-08-16T00:00:30Z (fresh)"));
    }

    private static ThreeCThreeUQueueTracker tracker() {
        return new ThreeCThreeUQueueTracker(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(final Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(final Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
