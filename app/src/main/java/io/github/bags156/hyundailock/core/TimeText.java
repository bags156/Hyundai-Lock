package io.github.bags156.hyundailock.core;

/** Short relative times for the status line. */
public final class TimeText {

    private TimeText() {
    }

    /** "just now", "4 min ago", "3 h ago", "2 d ago". Future times read as "just now". */
    public static String ago(long now, long then) {
        long seconds = Math.max(0, (now - then) / 1000);
        if (seconds < 60) {
            return "just now";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " h ago";
        }
        return (hours / 24) + " d ago";
    }
}
