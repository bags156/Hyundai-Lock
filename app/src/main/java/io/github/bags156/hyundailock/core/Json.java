package io.github.bags156.hyundailock.core;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Small helpers that behave the same on Android's org.json and the reference org.json used by
 * unit tests. (Android's optString returns the text "null" for JSON nulls; these don't.)
 */
public final class Json {

    private Json() {
    }

    /** The value as text, or {@code fallback} when it's missing or JSON null. */
    public static String str(JSONObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.isNull(key)) {
            return fallback;
        }
        Object value = object.opt(key);
        return value == null ? fallback : String.valueOf(value);
    }

    /** true/false, 1/0 or "true"/"false"; null when missing or unrecognised. */
    public static Boolean bool(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) {
            return null;
        }
        Object value = object.opt(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.equalsIgnoreCase("true") || text.equals("1")) {
                return Boolean.TRUE;
            }
            if (text.equalsIgnoreCase("false") || text.equals("0")) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    /** Parses a response body, or returns null if it isn't a JSON object (including empty). */
    public static JSONObject objectOrNull(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Hyundai's API reports failures as {"errorCode": ..., "errorMessage": ...}, sometimes with
     * HTTP 200. Returns a readable message, or null when the body isn't an error.
     */
    public static String errorMessage(JSONObject body) {
        if (body == null || !body.has("errorCode") || body.isNull("errorCode")) {
            return null;
        }
        String code = str(body, "errorCode", "?");
        String message = str(body, "errorMessage", null);
        if (message == null || message.trim().isEmpty()) {
            message = "no details given";
        }
        return message.trim() + " (code " + code + ")";
    }

    /** The errorCode text, or null. */
    public static String errorCode(JSONObject body) {
        return str(body, "errorCode", null);
    }
}
