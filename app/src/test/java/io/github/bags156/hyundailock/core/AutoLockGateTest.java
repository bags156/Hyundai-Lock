package io.github.bags156.hyundailock.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AutoLockGateTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long MIN = 60_000L;

    private static AutoLockGate.Inputs ready() {
        AutoLockGate.Inputs in = new AutoLockGate.Inputs();
        in.enabled = true;
        in.ready = true;
        in.now = NOW;
        return in;
    }

    @Test
    public void schedulesWhenNothingBlocks() {
        assertEquals(AutoLockGate.Decision.SCHEDULE, AutoLockGate.decide(ready()));
    }

    @Test
    public void offOrSignedOut() {
        AutoLockGate.Inputs in = ready();
        in.enabled = false;
        assertEquals(AutoLockGate.Decision.DISABLED, AutoLockGate.decide(in));
        in = ready();
        in.ready = false;
        assertEquals(AutoLockGate.Decision.NOT_READY, AutoLockGate.decide(in));
    }

    @Test
    public void pausedUntilLater() {
        AutoLockGate.Inputs in = ready();
        in.pausedUntil = NOW + MIN;
        assertEquals(AutoLockGate.Decision.PAUSED, AutoLockGate.decide(in));
        in.pausedUntil = NOW - 1;
        assertEquals(AutoLockGate.Decision.SCHEDULE, AutoLockGate.decide(in));
    }

    @Test
    public void onlyOnePendingLock() {
        AutoLockGate.Inputs in = ready();
        in.pendingDueAt = NOW + 2 * MIN;
        assertEquals(AutoLockGate.Decision.ALREADY_PENDING, AutoLockGate.decide(in));
        // A stale saved lock (way overdue) doesn't block a new one.
        in.pendingDueAt = NOW - AutoLockGate.OVERDUE_LIMIT_MS - 1;
        assertEquals(AutoLockGate.Decision.SCHEDULE, AutoLockGate.decide(in));
    }

    @Test
    public void cooldownAfterAutomaticLock() {
        AutoLockGate.Inputs in = ready();
        in.lastAutoLockAt = NOW - 4 * MIN;
        assertEquals(AutoLockGate.Decision.COOLDOWN, AutoLockGate.decide(in));
        in.lastAutoLockAt = NOW - 5 * MIN;
        assertEquals(AutoLockGate.Decision.SCHEDULE, AutoLockGate.decide(in));
    }

    @Test
    public void graceAfterUnlockingFromTheApp() {
        AutoLockGate.Inputs in = ready();
        in.lastManualUnlockAt = NOW - 9 * MIN;
        assertEquals(AutoLockGate.Decision.AFTER_MANUAL_UNLOCK, AutoLockGate.decide(in));
        in.lastManualUnlockAt = NOW - 11 * MIN;
        assertEquals(AutoLockGate.Decision.SCHEDULE, AutoLockGate.decide(in));
    }

    @Test
    public void clockSetBackwardsDoesNotBlockForever() {
        AutoLockGate.Inputs in = ready();
        in.lastAutoLockAt = NOW + 60 * MIN;
        in.lastManualUnlockAt = NOW + 60 * MIN;
        assertEquals(AutoLockGate.Decision.SCHEDULE, AutoLockGate.decide(in));
    }

    @Test
    public void overdueHandling() {
        assertEquals(AutoLockGate.Overdue.NONE, AutoLockGate.overdue(NOW, 0));
        assertEquals(AutoLockGate.Overdue.WAIT, AutoLockGate.overdue(NOW, NOW + 1));
        assertEquals(AutoLockGate.Overdue.RUN, AutoLockGate.overdue(NOW, NOW));
        assertEquals(AutoLockGate.Overdue.RUN, AutoLockGate.overdue(NOW, NOW - AutoLockGate.OVERDUE_LIMIT_MS));
        assertEquals(AutoLockGate.Overdue.EXPIRED,
                AutoLockGate.overdue(NOW, NOW - AutoLockGate.OVERDUE_LIMIT_MS - 1));
    }
}
