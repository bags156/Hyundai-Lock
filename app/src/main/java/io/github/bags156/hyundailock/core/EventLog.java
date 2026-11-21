package io.github.bags156.hyundailock.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A short, newest-first activity log stored as JSON text. Kept on the phone only. */
public final class EventLog {

    private EventLog() {
    }

    public static final int MAX_ENTRIES = 40;
    static final int MAX_TEXT = 300;

    public static final class Entry {
        public final long time;
        public final String text;

        public Entry(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    public static List<Entry> parse(String json) {
        List<Entry> entries = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return entries;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length() && entries.size() < MAX_ENTRIES; i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String text = Json.str(item, "m", null);
                if (text != null) {
                    entries.add(new Entry(item.optLong("t", 0L), text));
                }
            }
        } catch (JSONException e) {
            // A damaged log isn't worth failing over; start a new one.
            return new ArrayList<>();
        }
        return entries;
    }

    /** Returns the log text with {@code text} added at the top and old entries trimmed. */
    public static String append(String json, long time, String text) {
        List<Entry> entries = parse(json);
        String clean = text == null ? "" : text.replace('\n', ' ').trim();
        if (clean.length() > MAX_TEXT) {
            clean = clean.substring(0, MAX_TEXT - 1) + "…";
        }
        entries.add(0, new Entry(time, clean));
        JSONArray array = new JSONArray();
        for (int i = 0; i < entries.size() && i < MAX_ENTRIES; i++) {
            Entry entry = entries.get(i);
            try {
                array.put(new JSONObject().put("t", entry.time).put("m", entry.text));
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        }
        return array.toString();
    }

    public static List<Entry> newest(String json, int count) {
        List<Entry> entries = parse(json);
        return Collections.unmodifiableList(entries.subList(0, Math.min(count, entries.size())));
    }
}
