package io.github.bags156.hyundailock.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** The parts of Bluelink's vehicle status this app shows or acts on. Null means "not reported". */
public final class VehicleStatus {

    public final Boolean locked;
    public final Boolean engineOn;
    public final Boolean climateOn;
    public final Boolean anyDoorOpen;
    public final Boolean trunkOpen;
    public final Boolean hoodOpen;
    /** When the car last reported, as Hyundai sent it. */
    public final String reportedAt;

    public VehicleStatus(Boolean locked, Boolean engineOn, Boolean climateOn, Boolean anyDoorOpen,
            Boolean trunkOpen, Boolean hoodOpen, String reportedAt) {
        this.locked = locked;
        this.engineOn = engineOn;
        this.climateOn = climateOn;
        this.anyDoorOpen = anyDoorOpen;
        this.trunkOpen = trunkOpen;
        this.hoodOpen = hoodOpen;
        this.reportedAt = reportedAt;
    }

    /** Reads GET rcs/rvs/vehicleStatus. */
    public static VehicleStatus parse(JSONObject response) throws JSONException {
        JSONObject status = response == null ? null : response.optJSONObject("vehicleStatus");
        if (status == null) {
            throw new JSONException("Hyundai's reply had no vehicle status");
        }
        JSONObject doors = status.optJSONObject("doorOpen");
        Boolean anyDoor = null;
        if (doors != null) {
            for (String key : new String[] {"frontLeft", "frontRight", "backLeft", "backRight"}) {
                Boolean open = Json.bool(doors, key);
                if (open != null) {
                    anyDoor = (anyDoor != null && anyDoor) || open;
                }
            }
        }
        return new VehicleStatus(
                Json.bool(status, "doorLock"),
                Json.bool(status, "engine"),
                Json.bool(status, "airCtrlOn"),
                anyDoor,
                Json.bool(status, "trunkOpen"),
                Json.bool(status, "hoodOpen"),
                Json.str(status, "dateTime", null));
    }

    /** The same status with the lock state replaced, after a confirmed lock or unlock. */
    public VehicleStatus withLocked(boolean nowLocked) {
        return new VehicleStatus(nowLocked, engineOn, climateOn, anyDoorOpen, trunkOpen, hoodOpen,
                reportedAt);
    }

    public String lockLabel() {
        if (locked == null) {
            return "Unknown";
        }
        return locked ? "Locked" : "Unlocked";
    }

    /** "Open: a door, the trunk" style summary; "Nothing reported open" otherwise. */
    public String openLabel() {
        List<String> open = new ArrayList<>();
        if (Boolean.TRUE.equals(anyDoorOpen)) {
            open.add("a door");
        }
        if (Boolean.TRUE.equals(trunkOpen)) {
            open.add("the trunk");
        }
        if (Boolean.TRUE.equals(hoodOpen)) {
            open.add("the hood");
        }
        if (open.isEmpty()) {
            return "Nothing reported open";
        }
        String joined = String.join(", ", open);
        return "Open: " + joined;
    }

    /** One line for under the lock state: "Engine off · Climate off · All closed". */
    public String summaryLine() {
        List<String> parts = new ArrayList<>();
        if (engineOn != null) {
            parts.add(engineOn ? "Engine running" : "Engine off");
        }
        if (climateOn != null) {
            parts.add(climateOn ? "Climate on" : "Climate off");
        }
        List<String> open = new ArrayList<>();
        if (Boolean.TRUE.equals(anyDoorOpen)) {
            open.add("Door");
        }
        if (Boolean.TRUE.equals(trunkOpen)) {
            open.add(open.isEmpty() ? "Trunk" : "trunk");
        }
        if (Boolean.TRUE.equals(hoodOpen)) {
            open.add(open.isEmpty() ? "Hood" : "hood");
        }
        if (!open.isEmpty()) {
            parts.add(String.join(", ", open) + " open");
        } else if (anyDoorOpen != null || trunkOpen != null || hoodOpen != null) {
            parts.add("All closed");
        }
        return String.join(" · ", parts);
    }

    public static String onOff(Boolean value) {
        if (value == null) {
            return "unknown";
        }
        return value ? "on" : "off";
    }

    /** Epoch millis of {@link #reportedAt}, or null if Hyundai used a format this doesn't know. */
    public Long reportedAtMillis() {
        return parseTime(reportedAt);
    }

    static Long parseTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String text = raw.trim();
        String[] patterns = {
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyyMMddHHmmss",
        };
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            format.setLenient(false);
            try {
                ParsePosition position = new ParsePosition(0);
                Date date = format.parse(text, position);
                if (date != null && position.getIndex() == text.length()) {
                    return date.getTime();
                }
            } catch (RuntimeException ignored) {
                // Try the next pattern.
            }
        }
        return null;
    }

    public String toJson() {
        try {
            JSONObject object = new JSONObject();
            putIfKnown(object, "locked", locked);
            putIfKnown(object, "engineOn", engineOn);
            putIfKnown(object, "climateOn", climateOn);
            putIfKnown(object, "anyDoorOpen", anyDoorOpen);
            putIfKnown(object, "trunkOpen", trunkOpen);
            putIfKnown(object, "hoodOpen", hoodOpen);
            if (reportedAt != null) {
                object.put("reportedAt", reportedAt);
            }
            return object.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void putIfKnown(JSONObject object, String key, Boolean value) throws JSONException {
        if (value != null) {
            object.put(key, value.booleanValue());
        }
    }

    /** Reads {@link #toJson()}; null when the text is missing or damaged. */
    public static VehicleStatus fromJson(String text) {
        JSONObject object = Json.objectOrNull(text);
        if (object == null) {
            return null;
        }
        return new VehicleStatus(
                Json.bool(object, "locked"),
                Json.bool(object, "engineOn"),
                Json.bool(object, "climateOn"),
                Json.bool(object, "anyDoorOpen"),
                Json.bool(object, "trunkOpen"),
                Json.bool(object, "hoodOpen"),
                Json.str(object, "reportedAt", null));
    }
}
