package io.github.bags156.hyundailock.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether a notification from a watched app says the car was unlocked.
 *
 * A notification matches when its text contains any trigger phrase and none of the ignore
 * phrases. Matching ignores case. Lists are separated by commas, semicolons or new lines.
 */
public final class KeywordRules {

    /** MyHyundai with Bluelink (US). */
    public static final String DEFAULT_PACKAGES = "com.stationdm.bluelink";
    public static final String DEFAULT_TRIGGERS = "unlock";
    /** Failed unlock attempts leave the car locked, so they shouldn't trigger a lock. */
    public static final String DEFAULT_IGNORES = "fail, error, unable, could not, couldn't, couldn\u2019t";

    private final List<String> packages;
    private final List<String> triggers;
    private final List<String> ignores;

    public KeywordRules(String packages, String triggers, String ignores) {
        this.packages = parseList(packages);
        this.triggers = parseList(triggers);
        this.ignores = parseList(ignores);
    }

    public static List<String> parseList(String raw) {
        List<String> items = new ArrayList<>();
        if (raw == null) {
            return items;
        }
        for (String part : raw.split("[,;\\n]")) {
            String item = part.trim().toLowerCase(Locale.ROOT);
            if (!item.isEmpty() && !items.contains(item)) {
                items.add(item);
            }
        }
        return Collections.unmodifiableList(items);
    }

    public boolean watches(String packageName) {
        return packageName != null && packages.contains(packageName.trim().toLowerCase(Locale.ROOT));
    }

    public boolean matches(String text) {
        return matchedTrigger(text) != null;
    }

    /** The trigger phrase that matched, or null when the text doesn't count as "unlocked". */
    public String matchedTrigger(String text) {
        if (text == null || triggers.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String hit = null;
        for (String trigger : triggers) {
            if (lower.contains(trigger)) {
                hit = trigger;
                break;
            }
        }
        if (hit == null) {
            return null;
        }
        for (String ignore : ignores) {
            if (lower.contains(ignore)) {
                return null;
            }
        }
        return hit;
    }

    public List<String> packages() {
        return packages;
    }

    public List<String> triggers() {
        return triggers;
    }

    public List<String> ignores() {
        return ignores;
    }
}
