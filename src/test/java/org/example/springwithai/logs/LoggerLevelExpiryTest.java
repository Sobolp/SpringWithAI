package org.example.springwithai.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerGroups;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
class LoggerLevelExpiryTest {

    private static final String NAME = "org.example.springwithai";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

    @Mock
    private TaskScheduler scheduler;

    private final FakeLoggingSystem logging = new FakeLoggingSystem();
    private final LoggerGroups groups = new LoggerGroups();

    /** What was handed to the scheduler, in order; tasks run only when a test runs them. */
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Instant> times = new ArrayList<>();
    private final List<ScheduledFuture<?>> futures = new ArrayList<>();

    private LoggerLevelExpiry expiry;

    @BeforeEach
    void setUp() {
        when(scheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            times.add(invocation.getArgument(1));
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            futures.add(future);
            return future;
        });
        expiry = new LoggerLevelExpiry(logging, groups, scheduler, TTL);
    }

    @Test
    void changedLevelRevertsToTheOriginalAfterTheTtl() {
        LoggerLevelExpiry.Outcome outcome = change(NAME, LogLevel.DEBUG);

        assertThat(outcome).isEqualTo(new LoggerLevelExpiry.Outcome(null, LogLevel.DEBUG, Optional.of(NOW.plus(TTL))));
        assertThat(times).containsExactly(NOW.plus(TTL));

        tasks.getFirst().run();

        assertThat(expiry.configuredLevel(NAME)).isNull();
    }

    @Test
    void newerChangeKeepsTheFirstOriginalAndRestartsTheTimer() {
        logging.setLogLevel(NAME, LogLevel.WARN);
        change(NAME, LogLevel.DEBUG);
        change(NAME, LogLevel.TRACE);

        verify(futures.getFirst()).cancel(false);

        tasks.getFirst().run();  // the cancelled timer fired anyway: it must not undo the newer change
        assertThat(expiry.configuredLevel(NAME)).isEqualTo(LogLevel.TRACE);

        tasks.getLast().run();
        assertThat(expiry.configuredLevel(NAME)).isEqualTo(LogLevel.WARN);
    }

    @Test
    void changeBackToTheOriginalCancelsTheTimer() {
        change(NAME, LogLevel.DEBUG);

        assertThat(change(NAME, null).revertsAt()).isEmpty();

        verify(futures.getFirst()).cancel(false);
        logging.setLogLevel(NAME, LogLevel.ERROR);  // set some other way, after the timer was cancelled
        tasks.getFirst().run();
        assertThat(expiry.configuredLevel(NAME)).isEqualTo(LogLevel.ERROR);
    }

    /** The endpoint rejected the request: applied() is not called, and the earlier timer stays as it was. */
    @Test
    void failedChangeLeavesTheTimerAlone() {
        change(NAME, LogLevel.DEBUG);

        try (LoggerLevelExpiry.Change change = expiry.begin(NAME)) {
            assertThat(change).isNotNull();
        }

        assertThat(tasks).hasSize(1);
        verifyNoInteractions(futures.getFirst());
        tasks.getFirst().run();
        assertThat(expiry.configuredLevel(NAME)).isNull();
    }

    @Test
    void timersOfDifferentLoggersAreIndependent() {
        change("a", LogLevel.DEBUG);
        logging.setLogLevel("b", LogLevel.INFO);
        change("b", LogLevel.TRACE);

        tasks.getLast().run();

        assertThat(expiry.configuredLevel("a")).isEqualTo(LogLevel.DEBUG);
        assertThat(expiry.configuredLevel("b")).isEqualTo(LogLevel.INFO);
    }

    /** As Actuator does for a group: the group's own level is set, and through it every member's. */
    @Test
    void groupIsReadAndRevertedAsAGroup() {
        groups.putAll(Map.of("web", List.of("org.springframework.web", "org.springframework.http")));

        LoggerLevelExpiry.Outcome outcome;
        try (LoggerLevelExpiry.Change change = expiry.begin("web")) {
            groups.get("web").configureLogLevel(LogLevel.TRACE, logging::setLogLevel);
            outcome = change.applied();
        }

        assertThat(outcome).isEqualTo(new LoggerLevelExpiry.Outcome(null, LogLevel.TRACE, Optional.of(NOW.plus(TTL))));
        assertThat(expiry.configuredLevel("org.springframework.http")).isEqualTo(LogLevel.TRACE);

        tasks.getFirst().run();

        assertThat(expiry.configuredLevel("web")).isNull();
        assertThat(expiry.configuredLevel("org.springframework.web")).isNull();
        assertThat(expiry.configuredLevel("org.springframework.http")).isNull();
    }

    /**
     * A revert that fires while a change is in progress waits for it. Without the lock it would run between reading
     * the old level and recording the change, and the expired level would be kept as the "original".
     */
    @Test
    void revertWaitsForAChangeInProgress() throws InterruptedException {
        change(NAME, LogLevel.DEBUG);
        Thread revertThread;

        try (LoggerLevelExpiry.Change change = expiry.begin(NAME)) {
            revertThread = Thread.ofPlatform().start(tasks.getFirst());
            await().atMost(Duration.ofSeconds(5)).until(() -> revertThread.getState() == Thread.State.WAITING);
            assertThat(expiry.configuredLevel(NAME)).isEqualTo(LogLevel.DEBUG);  // not reverted under our feet

            logging.setLogLevel(NAME, LogLevel.TRACE);
            change.applied();
        }
        assertThat(revertThread.join(Duration.ofSeconds(5))).isTrue();

        assertThat(expiry.configuredLevel(NAME)).isEqualTo(LogLevel.TRACE);  // the superseded revert did nothing
        tasks.getLast().run();
        assertThat(expiry.configuredLevel(NAME)).isNull();  // back to the real original, not DEBUG
    }

    /** Sets a level the way the loggers endpoint does, inside a change. */
    private LoggerLevelExpiry.Outcome change(String name, LogLevel level) {
        try (LoggerLevelExpiry.Change change = expiry.begin(name)) {
            logging.setLogLevel(name, level);
            return change.applied();
        }
    }
}
