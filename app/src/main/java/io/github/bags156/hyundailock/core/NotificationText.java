package io.github.bags156.hyundailock.core;

import java.util.ArrayList;
import java.util.List;

/** Joins the visible parts of a notification (title, text, big text...) into one line. */
public final class NotificationText {

    private NotificationText() {
    }

    static final int MAX_LENGTH = 400;

    /** Skips empty parts and parts already contained in an earlier one. */
    public static String combine(CharSequence... parts) {
        List<String> kept = new ArrayList<>();
        for (CharSequence part : parts) {
            if (part == null) {
                continue;
            }
            String text = part.toString().replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) {
                continue;
            }
            boolean duplicate = false;
            for (int i = 0; i < kept.size(); i++) {
                String earlier = kept.get(i);
                if (earlier.contains(text)) {
                    duplicate = true;
                    break;
                }
                if (text.contains(earlier)) {
                    // A longer version (e.g. big text) replaces the short one.
                    kept.set(i, text);
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                kept.add(text);
            }
        }
        String joined = String.join(" · ", kept);
        if (joined.length() > MAX_LENGTH) {
            joined = joined.substring(0, MAX_LENGTH - 1) + "…";
        }
        return joined;
    }
}
