package io.github.bags156.hyundailock.core;

/** The choices for how long to wait between an "unlocked" alert and the automatic lock. */
public final class Delays {

    private Delays() {
    }

    public static final long[] CHOICES_MS = {
        15_000L, 30_000L, 60_000L, 2 * 60_000L, 3 * 60_000L, 5 * 60_000L, 10 * 60_000L,
    };

    public static final long DEFAULT_MS = 2 * 60_000L;

    /** Index of the choice closest to {@code ms}. */
    public static int indexOf(long ms) {
        int best = 0;
        for (int i = 1; i < CHOICES_MS.length; i++) {
            if (Math.abs(CHOICES_MS[i] - ms) < Math.abs(CHOICES_MS[best] - ms)) {
                best = i;
            }
        }
        return best;
    }

    public static long at(int index) {
        return CHOICES_MS[Math.max(0, Math.min(CHOICES_MS.length - 1, index))];
    }

    /** "15 seconds", "1 minute", "5 minutes". */
    public static String label(long ms) {
        long seconds = Math.max(0, ms / 1000);
        if (seconds < 60) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        long minutes = seconds / 60;
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
