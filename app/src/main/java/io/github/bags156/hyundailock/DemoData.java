package io.github.bags156.hyundailock;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import io.github.bags156.hyundailock.core.Schedule;
import io.github.bags156.hyundailock.core.Vehicle;
import io.github.bags156.hyundailock.core.Weather;
import io.github.bags156.hyundailock.core.VehicleStatus;

/**
 * Sample data for the CI screenshot job (.github/workflows/screenshots.yml). Only debuggable
 * builds accept it; release builds ignore the "demo" extra entirely.
 */
final class DemoData {

    private DemoData() {
    }

    static boolean allowed(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    /** {@code variant} is "normal" (locked) or "pending" (unlocked, auto-lock counting down). */
    static void seed(Context context, String variant) {
        AppSettings settings = new AppSettings(context);
        try {
            settings.saveAccount("demo@example.com", Secrets.encrypt("demo"), Secrets.encrypt("0000"));
        } catch (GeneralSecurityException e) {
            return;
        }
        settings.setVehicle(new Vehicle("DEMO", "KM8JBCAE5NU000123", "2022 TUCSON", "3", "N", "TUCSON"));
        long now = System.currentTimeMillis();
        boolean pending = "pending".equals(variant);
        settings.saveStatus(new VehicleStatus(!pending, false, false, false, false, false,
                iso(now - 14 * 60_000L)), now - 3 * 60_000L);
        settings.setAutoLockEnabled(true);
        for (Schedule existing : settings.schedules()) {
            settings.removeSchedule(existing.id);
        }
        settings.putSchedule(Schedule.coldMorning(1));
        settings.putSchedule(Schedule.hotAfternoon(2).withEnabled(false));
        settings.setWeatherPlace(new Weather.Place("Bentonville", "Arkansas", 36.37285, -94.20882));
        settings.saveWeather(31.4, now - 6 * 60_000L);
        settings.setPausedUntil(0);
        settings.clearLog();
        String alert = "Vehicle Alert · Your vehicle's doors are unlocked";
        settings.addLog("Signed in as demo@example.com");
        settings.addLog("Auto-lock turned on");
        settings.addLog("Alert ignored (no trigger phrase): \u201CVehicle Alert · Remote Start was successful\u201D");
        settings.addLog("Unlocked alert, locking in 2 minutes: \u201C" + alert + "\u201D");
        settings.addLog("Auto-lock: Locked");
        if (pending) {
            settings.addLog("Unlocked alert, locking in 2 minutes: \u201C" + alert + "\u201D");
            settings.setPending(now + 95_000L, alert);
        } else {
            settings.clearPending();
        }
    }

    private static String iso(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(millis));
    }
}
