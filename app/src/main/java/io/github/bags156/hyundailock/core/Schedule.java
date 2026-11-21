package io.github.bags156.hyundailock.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/**
 * A timed remote start: at a time of day on chosen weekdays, start climate control, optionally
 * only when the outside temperature is below or above a threshold.
 */
public final class Schedule {

    public enum Condition {
        ALWAYS,
        BELOW,
        ABOVE
    }

    // Day bits, Monday first.
    public static final int MONDAY = 1;
    public static final int TUESDAY = 1 << 1;
    public static final int WEDNESDAY = 1 << 2;
    public static final int THURSDAY = 1 << 3;
    public static final int FRIDAY = 1 << 4;
    public static final int SATURDAY = 1 << 5;
    public static final int SUNDAY = 1 << 6;
    public static final int WEEKDAYS = MONDAY | TUESDAY | WEDNESDAY | THURSDAY | FRIDAY;
    public static final int WEEKENDS = SATURDAY | SUNDAY;
    public static final int EVERY_DAY = WEEKDAYS | WEEKENDS;
    public static final String[] DAY_SHORT = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    public static final String[] DAY_LETTER = {"M", "T", "W", "T", "F", "S", "S"};

    public static final int MIN_THRESHOLD_F = -20;
    public static final int MAX_THRESHOLD_F = 110;

    public final long id;
    public final boolean enabled;
    public final int hour;
    public final int minute;
    public final int days;
    public final Condition condition;
    public final int thresholdF;
    public final ClimateOptions climate;

    public Schedule(long id, boolean enabled, int hour, int minute, int days, Condition condition,
            int thresholdF, ClimateOptions climate) {
        this.id = id;
        this.enabled = enabled;
        this.hour = ClimateOptions.clamp(hour, 0, 23);
        this.minute = ClimateOptions.clamp(minute, 0, 59);
        this.days = days & EVERY_DAY;
        this.condition = condition == null ? Condition.ALWAYS : condition;
        this.thresholdF = ClimateOptions.clamp(thresholdF, MIN_THRESHOLD_F, MAX_THRESHOLD_F);
        this.climate = climate;
    }

    /** Weekdays 7:30 AM, below 35°F: warm the cabin with defrost and heated features. */
    public static Schedule coldMorning(long id) {
        return new Schedule(id, true, 7, 30, WEEKDAYS, Condition.BELOW, 35,
                new ClimateOptions(74, 10, true, true));
    }

    /** Weekdays 4:45 PM, above 85°F: cool the cabin. */
    public static Schedule hotAfternoon(long id) {
        return new Schedule(id, true, 16, 45, WEEKDAYS, Condition.ABOVE, 85,
                new ClimateOptions(64, 10, false, false));
    }

    public Schedule withEnabled(boolean value) {
        return new Schedule(id, value, hour, minute, days, condition, thresholdF, climate);
    }

    public boolean needsWeather() {
        return condition != Condition.ALWAYS;
    }

    /** The day bit for a {@link Calendar#DAY_OF_WEEK} value (Sunday = 1 ... Saturday = 7). */
    public static int dayBit(int calendarDayOfWeek) {
        return 1 << ((calendarDayOfWeek + 5) % 7);
    }

    /** The next start strictly after {@code now}, in epoch millis; -1 when no day is chosen. */
    public long nextRun(long now, TimeZone zone) {
        if (days == 0) {
            return -1;
        }
        Calendar day = Calendar.getInstance(zone);
        day.setTimeInMillis(now);
        for (int i = 0; i <= 7; i++) {
            Calendar candidate = (Calendar) day.clone();
            candidate.add(Calendar.DAY_OF_MONTH, i);
            candidate.set(Calendar.HOUR_OF_DAY, hour);
            candidate.set(Calendar.MINUTE, minute);
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);
            long at = candidate.getTimeInMillis();
            if (at > now && (days & dayBit(candidate.get(Calendar.DAY_OF_WEEK))) != 0) {
                return at;
            }
        }
        return -1;
    }

    /** Whether to start now, and why, given the outside temperature (null if unknown). */
    public static final class Check {
        public final boolean run;
        public final String reason;

        Check(boolean run, String reason) {
            this.run = run;
            this.reason = reason;
        }
    }

    public Check check(Double outsideF) {
        String now = outsideF == null ? null : Math.round(outsideF) + "°F outside";
        switch (condition) {
            case BELOW:
                if (outsideF == null) {
                    return new Check(false, "couldn't get the outside temperature");
                }
                return outsideF < thresholdF
                        ? new Check(true, now)
                        : new Check(false, now + ", not below " + thresholdF + "°F");
            case ABOVE:
                if (outsideF == null) {
                    return new Check(false, "couldn't get the outside temperature");
                }
                return outsideF > thresholdF
                        ? new Check(true, now)
                        : new Check(false, now + ", not above " + thresholdF + "°F");
            case ALWAYS:
            default:
                return new Check(true, now == null ? "on schedule" : now);
        }
    }

    // ---- labels ----

    public String daysLabel() {
        if (days == EVERY_DAY) {
            return "Every day";
        }
        if (days == WEEKDAYS) {
            return "Weekdays";
        }
        if (days == WEEKENDS) {
            return "Weekends";
        }
        if (days == 0) {
            return "No days chosen";
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            if ((days & (1 << i)) != 0) {
                names.add(DAY_SHORT[i]);
            }
        }
        return String.join(", ", names);
    }

    public String conditionLabel() {
        switch (condition) {
            case BELOW:
                return "If below " + thresholdF + "°F";
            case ABOVE:
                return "If above " + thresholdF + "°F";
            case ALWAYS:
            default:
                return "Any temperature";
        }
    }

    public String actionLabel() {
        StringBuilder text = new StringBuilder();
        text.append(climate.tempF).append("°F");
        if (climate.defrost) {
            text.append(" · defrost");
        }
        if (climate.heat) {
            text.append(" · heated");
        }
        text.append(" · ").append(climate.minutes).append("\u00A0min");
        return text.toString();
    }

    /** "Warm-up", "Cool-down" or "Scheduled start", for notifications. */
    public String kindLabel() {
        switch (condition) {
            case BELOW:
                return "Warm-up";
            case ABOVE:
                return "Cool-down";
            case ALWAYS:
            default:
                return "Scheduled start";
        }
    }

    // ---- storage ----

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("enabled", enabled)
                .put("hour", hour)
                .put("minute", minute)
                .put("days", days)
                .put("condition", condition.name())
                .put("threshold", thresholdF)
                .put("temp", climate.tempF)
                .put("minutes", climate.minutes)
                .put("defrost", climate.defrost)
                .put("heat", climate.heat);
    }

    public static String toJson(List<Schedule> schedules) {
        JSONArray array = new JSONArray();
        try {
            for (Schedule schedule : schedules) {
                array.put(schedule.toJson());
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return array.toString();
    }

    /** Reads {@link #toJson(List)}, skipping damaged entries. */
    public static List<Schedule> parseList(String text) {
        List<Schedule> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null || !o.has("id")) {
                    continue;
                }
                Condition condition;
                try {
                    condition = Condition.valueOf(Json.str(o, "condition", "ALWAYS"));
                } catch (IllegalArgumentException e) {
                    condition = Condition.ALWAYS;
                }
                result.add(new Schedule(
                        o.optLong("id"),
                        !Boolean.FALSE.equals(Json.bool(o, "enabled")),
                        o.optInt("hour", 7),
                        o.optInt("minute", 30),
                        o.optInt("days", WEEKDAYS),
                        condition,
                        o.optInt("threshold", 35),
                        new ClimateOptions(o.optInt("temp", 72), o.optInt("minutes", 10),
                                Boolean.TRUE.equals(Json.bool(o, "defrost")),
                                Boolean.TRUE.equals(Json.bool(o, "heat")))));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return result;
    }

    /** A copy sorted by time of day. */
    public static List<Schedule> sorted(List<Schedule> schedules) {
        List<Schedule> copy = new ArrayList<>(schedules);
        Collections.sort(copy, (a, b) -> Integer.compare(a.hour * 60 + a.minute, b.hour * 60 + b.minute));
        return copy;
    }
}
