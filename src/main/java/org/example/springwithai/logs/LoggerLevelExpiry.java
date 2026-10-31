package org.example.springwithai.logs;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggerGroup;
import org.springframework.boot.logging.LoggerGroups;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Reverts a log level changed at runtime after {@code app.logs.level-ttl} (30 minutes by default), so a forgotten
 * {@code DEBUG} cannot fill the disk. The logger goes back to the level it had before its first change; a newer change
 * to the same logger restarts the timer.
 * <p>
 * Names are resolved like Actuator's loggers endpoint does: a logger group with members (Boot's {@code web} and
 * {@code sql}, or any {@code logging.group.*}) is read and reverted as a group, setting the level of every member;
 * any other name is a single logger.
 * <p>
 * A change runs inside {@link #begin(String)}: it holds one lock from reading the old level until the new one is
 * recorded, and reverts take the same lock, so a revert or another change can never slip in between and leave a
 * wrong "original" behind. Level changes are rare and made by people, so a single lock costs nothing.
 * <p>
 * Timers live in memory. A restart drops them, but it also drops every runtime level, so nothing is left behind.
 */
@Component
class LoggerLevelExpiry {

    private static final Logger log = LoggerFactory.getLogger(LoggerLevelExpiry.class);

    private final LoggingSystem logging;
    private final LoggerGroups groups;
    private final TaskScheduler scheduler;
    private final Duration ttl;

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Pending> pending = new HashMap<>();  // guarded by lock
    private long generations;                                      // guarded by lock

    /**
     * A scheduled revert. {@code generation} identifies it, so a revert that already started running when a newer
     * change replaced it cannot undo that newer change.
     */
    private record Pending(@Nullable LogLevel original, long generation, ScheduledFuture<?> revert) {
    }

    /** What a change did: the level before and after it, and when it reverts (empty: nothing to revert). */
    record Outcome(@Nullable LogLevel before, @Nullable LogLevel after, Optional<Instant> revertsAt) {
    }

    LoggerLevelExpiry(LoggingSystem logging, LoggerGroups groups, TaskScheduler scheduler,
            @Value("${app.logs.level-ttl:30m}") Duration ttl) {
        this.logging = logging;
        this.groups = groups;
        this.scheduler = scheduler;
        this.ttl = ttl;
    }

    /**
     * Starts a change of one logger or group: locks, and reads its level before the change. Apply the change, call
     * {@link Change#applied()} if it succeeded, and close the change in every case.
     */
    Change begin(String name) {
        lock.lock();
        try {
            return new Change(name, configuredLevel(name));
        } catch (RuntimeException e) {
            lock.unlock();
            throw e;
        }
    }

    /** One change in progress; holds the lock until closed. */
    final class Change implements AutoCloseable {

        private final String name;
        private final @Nullable LogLevel before;

        private Change(String name, @Nullable LogLevel before) {
            this.name = name;
            this.before = before;
        }

        /** Records that the change succeeded and (re)starts the revert timer, unless the level is back at its original. */
        Outcome applied() {
            LogLevel after = configuredLevel(name);
            Pending previous = pending.remove(name);
            if (previous != null) {
                previous.revert().cancel(false);
            }
            LogLevel original = (previous != null) ? previous.original() : before;  // keep the very first level
            if (Objects.equals(after, original)) {
                return new Outcome(before, after, Optional.empty());
            }
            long generation = ++generations;
            Instant at = scheduler.getClock().instant().plus(ttl);
            pending.put(name, new Pending(original, generation, scheduler.schedule(() -> revert(name, generation), at)));
            return new Outcome(before, after, Optional.of(at));
        }

        @Override
        public void close() {
            lock.unlock();
        }
    }

    /** The configured level of the group or logger; {@code null} if it inherits its level or does not exist. */
    @Nullable LogLevel configuredLevel(String name) {
        LoggerGroup group = groupWithMembers(name);
        if (group != null) {
            return group.getConfiguredLevel();
        }
        LoggerConfiguration configuration = logging.getLoggerConfiguration(name);
        return (configuration != null) ? configuration.getConfiguredLevel() : null;
    }

    private void revert(String name, long generation) {
        lock.lock();
        try {
            Pending current = pending.get(name);
            if (current == null || current.generation() != generation) {
                return;  // superseded by a newer change, which has its own timer
            }
            pending.remove(name);
            setLevel(name, current.original());
            log.warn("Log level of {} reverted to {} after {} (expired)", LogPaths.printable(name),
                    describe(current.original()), ttl);
        } finally {
            lock.unlock();
        }
    }

    private void setLevel(String name, @Nullable LogLevel level) {
        LoggerGroup group = groupWithMembers(name);
        if (group != null) {
            group.configureLogLevel(level, logging::setLogLevel);
        } else {
            logging.setLogLevel(name, level);
        }
    }

    /** The group Actuator would change for this name: one that exists and has members. */
    private @Nullable LoggerGroup groupWithMembers(String name) {
        LoggerGroup group = groups.get(name);
        return (group != null && group.hasMembers()) ? group : null;
    }

    /** A level for log messages: {@code null} means the logger inherits its parent's level. */
    static String describe(@Nullable LogLevel level) {
        return (level != null) ? level.name() : "inherited";
    }
}
