package io.github.bags156.hyundailock.core;

/**
 * Whether an "unlocked" alert should schedule an automatic lock. Pure logic: the caller supplies
 * the clock and the saved state.
 */
public final class AutoLockGate {

    private AutoLockGate() {
    }

    /** After an automatic lock, ignore further alerts for this long so the app can't loop. */
    public static final long COOLDOWN_MS = 5 * 60_000L;
    /** After you unlock from this app, its own "unlocked" alert shouldn't re-lock the car. */
    public static final long MANUAL_UNLOCK_GRACE_MS = 10 * 60_000L;
    /** A lock that was due while the app wasn't running is dropped if it's later than this. */
    public static final long OVERDUE_LIMIT_MS = 15 * 60_000L;

    public enum Decision {
        SCHEDULE("lock scheduled"),
        DISABLED("auto-lock is off"),
        NOT_READY("not signed in or no car chosen"),
        PAUSED("auto-lock is paused"),
        ALREADY_PENDING("a lock is already scheduled"),
        COOLDOWN("the app locked the car less than 5 minutes ago"),
        AFTER_MANUAL_UNLOCK("you unlocked from this app less than 10 minutes ago");

        public final String reason;

        Decision(String reason) {
            this.reason = reason;
        }
    }

    public static final class Inputs {
        public boolean enabled;
        public boolean ready;
        public long now;
        public long pausedUntil;
        public long lastAutoLockAt;
        public long lastManualUnlockAt;
        /** Due time of a scheduled lock, or 0 when none is scheduled. */
        public long pendingDueAt;
    }

    public static Decision decide(Inputs in) {
        if (!in.enabled) {
            return Decision.DISABLED;
        }
        if (!in.ready) {
            return Decision.NOT_READY;
        }
        if (in.pausedUntil > in.now) {
            return Decision.PAUSED;
        }
        if (in.pendingDueAt > 0 && in.now - in.pendingDueAt <= OVERDUE_LIMIT_MS) {
            return Decision.ALREADY_PENDING;
        }
        if (within(in.now, in.lastAutoLockAt, COOLDOWN_MS)) {
            return Decision.COOLDOWN;
        }
        if (within(in.now, in.lastManualUnlockAt, MANUAL_UNLOCK_GRACE_MS)) {
            return Decision.AFTER_MANUAL_UNLOCK;
        }
        return Decision.SCHEDULE;
    }

    /** True when {@code then} is set and no more than {@code window} before {@code now}. */
    private static boolean within(long now, long then, long window) {
        return then > 0 && now >= then && now - then < window;
    }

    /** What to do with a saved lock time, e.g. after the app's process was restarted. */
    public enum Overdue {
        NONE,
        WAIT,
        RUN,
        EXPIRED
    }

    public static Overdue overdue(long now, long dueAt) {
        if (dueAt <= 0) {
            return Overdue.NONE;
        }
        if (now < dueAt) {
            return Overdue.WAIT;
        }
        if (now - dueAt <= OVERDUE_LIMIT_MS) {
            return Overdue.RUN;
        }
        return Overdue.EXPIRED;
    }
}
