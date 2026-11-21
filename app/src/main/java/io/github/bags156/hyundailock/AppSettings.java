package io.github.bags156.hyundailock;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import io.github.bags156.hyundailock.core.ClimateOptions;
import io.github.bags156.hyundailock.core.Delays;
import io.github.bags156.hyundailock.core.EventLog;
import io.github.bags156.hyundailock.core.KeywordRules;
import io.github.bags156.hyundailock.core.Schedule;
import io.github.bags156.hyundailock.core.Weather;
import io.github.bags156.hyundailock.core.Vehicle;
import io.github.bags156.hyundailock.core.VehicleStatus;

/** Everything the app remembers. Stored in this app's private storage; backups are disabled. */
final class AppSettings {

    private static final String FILE = "settings";

    private static final String EMAIL = "email";
    private static final String PASSWORD_ENC = "password_enc";
    private static final String PIN_ENC = "pin_enc";
    private static final String VEHICLE = "vehicle";

    private static final String STATUS = "status";
    private static final String STATUS_FETCHED_AT = "status_fetched_at";
    private static final String LAST_RESULT = "last_result";

    private static final String CLIMATE_TEMP = "climate_temp";
    private static final String CLIMATE_MINUTES = "climate_minutes";
    private static final String CLIMATE_DEFROST = "climate_defrost";
    private static final String CLIMATE_HEAT = "climate_heat";

    private static final String AUTO_ENABLED = "auto_enabled";
    private static final String AUTO_DELAY = "auto_delay_ms";
    private static final String AUTO_PACKAGES = "auto_packages";
    private static final String AUTO_TRIGGERS = "auto_triggers";
    private static final String AUTO_IGNORES = "auto_ignores";
    private static final String AUTO_PAUSED_UNTIL = "auto_paused_until";
    private static final String AUTO_LAST_LOCK = "auto_last_lock";
    private static final String MANUAL_UNLOCK_AT = "manual_unlock_at";
    private static final String PENDING_DUE_AT = "pending_due_at";
    private static final String PENDING_ALERT = "pending_alert";

    private static final String LOG = "log";

    private static final String SCHEDULES = "schedules";
    private static final String NEXT_SCHEDULE_ID = "next_schedule_id";
    private static final String WEATHER_PLACE = "weather_place";
    private static final String WEATHER_TEMP = "weather_temp";
    private static final String WEATHER_AT = "weather_at";

    private final SharedPreferences prefs;

    AppSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- account ----

    String email() {
        return prefs.getString(EMAIL, null);
    }

    String passwordEnc() {
        return prefs.getString(PASSWORD_ENC, null);
    }

    String pinEnc() {
        return prefs.getString(PIN_ENC, null);
    }

    void saveAccount(String email, String passwordEnc, String pinEnc) {
        prefs.edit()
                .putString(EMAIL, email)
                .putString(PASSWORD_ENC, passwordEnc)
                .putString(PIN_ENC, pinEnc)
                .apply();
    }

    boolean hasAccount() {
        return email() != null && passwordEnc() != null && pinEnc() != null;
    }

    Vehicle vehicle() {
        return Vehicle.fromJson(prefs.getString(VEHICLE, null));
    }

    void setVehicle(Vehicle vehicle) {
        prefs.edit()
                .putString(VEHICLE, vehicle == null ? null : vehicle.toJson())
                .remove(STATUS)
                .remove(STATUS_FETCHED_AT)
                .apply();
    }

    boolean ready() {
        return hasAccount() && vehicle() != null;
    }

    /** Signs out: forgets the account, the car, its status and any scheduled lock. */
    void clearAccount() {
        prefs.edit()
                .remove(EMAIL)
                .remove(PASSWORD_ENC)
                .remove(PIN_ENC)
                .remove(VEHICLE)
                .remove(STATUS)
                .remove(STATUS_FETCHED_AT)
                .remove(LAST_RESULT)
                .remove(PENDING_DUE_AT)
                .remove(PENDING_ALERT)
                .apply();
    }

    // ---- status ----

    VehicleStatus status() {
        return VehicleStatus.fromJson(prefs.getString(STATUS, null));
    }

    long statusFetchedAt() {
        return prefs.getLong(STATUS_FETCHED_AT, 0L);
    }

    void saveStatus(VehicleStatus status, long fetchedAt) {
        prefs.edit()
                .putString(STATUS, status.toJson())
                .putLong(STATUS_FETCHED_AT, fetchedAt)
                .apply();
    }

    String lastResult() {
        return prefs.getString(LAST_RESULT, null);
    }

    void setLastResult(String text) {
        prefs.edit().putString(LAST_RESULT, text).apply();
    }

    // ---- climate ----

    ClimateOptions climate() {
        return new ClimateOptions(
                prefs.getInt(CLIMATE_TEMP, 72),
                prefs.getInt(CLIMATE_MINUTES, 10),
                prefs.getBoolean(CLIMATE_DEFROST, false),
                prefs.getBoolean(CLIMATE_HEAT, false));
    }

    void setClimate(ClimateOptions options) {
        prefs.edit()
                .putInt(CLIMATE_TEMP, options.tempF)
                .putInt(CLIMATE_MINUTES, options.minutes)
                .putBoolean(CLIMATE_DEFROST, options.defrost)
                .putBoolean(CLIMATE_HEAT, options.heat)
                .apply();
    }

    // ---- auto-lock ----

    boolean autoLockEnabled() {
        return prefs.getBoolean(AUTO_ENABLED, false);
    }

    void setAutoLockEnabled(boolean enabled) {
        prefs.edit().putBoolean(AUTO_ENABLED, enabled).apply();
    }

    long delayMs() {
        return prefs.getLong(AUTO_DELAY, Delays.DEFAULT_MS);
    }

    void setDelayMs(long ms) {
        prefs.edit().putLong(AUTO_DELAY, ms).apply();
    }

    String packages() {
        return prefs.getString(AUTO_PACKAGES, KeywordRules.DEFAULT_PACKAGES);
    }

    String triggers() {
        return prefs.getString(AUTO_TRIGGERS, KeywordRules.DEFAULT_TRIGGERS);
    }

    String ignores() {
        return prefs.getString(AUTO_IGNORES, KeywordRules.DEFAULT_IGNORES);
    }

    void setRules(String packages, String triggers, String ignores) {
        prefs.edit()
                .putString(AUTO_PACKAGES, packages)
                .putString(AUTO_TRIGGERS, triggers)
                .putString(AUTO_IGNORES, ignores)
                .apply();
    }

    void resetRules() {
        prefs.edit().remove(AUTO_PACKAGES).remove(AUTO_TRIGGERS).remove(AUTO_IGNORES).apply();
    }

    KeywordRules rules() {
        return new KeywordRules(packages(), triggers(), ignores());
    }

    long pausedUntil() {
        return prefs.getLong(AUTO_PAUSED_UNTIL, 0L);
    }

    void setPausedUntil(long time) {
        prefs.edit().putLong(AUTO_PAUSED_UNTIL, time).apply();
    }

    long lastAutoLockAt() {
        return prefs.getLong(AUTO_LAST_LOCK, 0L);
    }

    void setLastAutoLockAt(long time) {
        prefs.edit().putLong(AUTO_LAST_LOCK, time).apply();
    }

    long lastManualUnlockAt() {
        return prefs.getLong(MANUAL_UNLOCK_AT, 0L);
    }

    void setLastManualUnlockAt(long time) {
        prefs.edit().putLong(MANUAL_UNLOCK_AT, time).apply();
    }

    long pendingDueAt() {
        return prefs.getLong(PENDING_DUE_AT, 0L);
    }

    String pendingAlert() {
        return prefs.getString(PENDING_ALERT, null);
    }

    void setPending(long dueAt, String alert) {
        // commit(): this must survive the process being stopped right after.
        prefs.edit().putLong(PENDING_DUE_AT, dueAt).putString(PENDING_ALERT, alert).commit();
    }

    void clearPending() {
        prefs.edit().remove(PENDING_DUE_AT).remove(PENDING_ALERT).commit();
    }

    // ---- schedules ----

    List<Schedule> schedules() {
        return Schedule.sorted(Schedule.parseList(prefs.getString(SCHEDULES, null)));
    }

    Schedule schedule(long id) {
        for (Schedule schedule : schedules()) {
            if (schedule.id == id) {
                return schedule;
            }
        }
        return null;
    }

    /** Adds the schedule, or replaces the one with the same id. */
    synchronized void putSchedule(Schedule schedule) {
        List<Schedule> list = new ArrayList<>();
        for (Schedule existing : schedules()) {
            if (existing.id != schedule.id) {
                list.add(existing);
            }
        }
        list.add(schedule);
        prefs.edit().putString(SCHEDULES, Schedule.toJson(list)).commit();
    }

    synchronized void removeSchedule(long id) {
        List<Schedule> list = new ArrayList<>();
        for (Schedule existing : schedules()) {
            if (existing.id != id) {
                list.add(existing);
            }
        }
        prefs.edit().putString(SCHEDULES, Schedule.toJson(list)).commit();
    }

    synchronized long newScheduleId() {
        long id = prefs.getLong(NEXT_SCHEDULE_ID, 1L);
        prefs.edit().putLong(NEXT_SCHEDULE_ID, id + 1).commit();
        return id;
    }

    Weather.Place weatherPlace() {
        return Weather.Place.fromJson(prefs.getString(WEATHER_PLACE, null));
    }

    void setWeatherPlace(Weather.Place place) {
        prefs.edit()
                .putString(WEATHER_PLACE, place == null ? null : place.toJson())
                .remove(WEATHER_TEMP)
                .remove(WEATHER_AT)
                .apply();
    }

    /** Last outside temperature fetched, or null. */
    Double weatherTemp() {
        if (!prefs.contains(WEATHER_TEMP)) {
            return null;
        }
        return (double) prefs.getFloat(WEATHER_TEMP, 0f);
    }

    long weatherAt() {
        return prefs.getLong(WEATHER_AT, 0L);
    }

    void saveWeather(double tempF, long at) {
        prefs.edit().putFloat(WEATHER_TEMP, (float) tempF).putLong(WEATHER_AT, at).apply();
    }

    // ---- activity log ----

    String log() {
        return prefs.getString(LOG, null);
    }

    synchronized void addLog(String text) {
        prefs.edit().putString(LOG, EventLog.append(log(), System.currentTimeMillis(), text)).apply();
    }

    void clearLog() {
        prefs.edit().remove(LOG).apply();
    }
}
