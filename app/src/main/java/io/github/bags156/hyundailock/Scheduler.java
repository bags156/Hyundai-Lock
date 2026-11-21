package io.github.bags156.hyundailock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.util.List;
import java.util.TimeZone;

import io.github.bags156.hyundailock.core.Schedule;

/**
 * Sets one alarm per enabled schedule for its next start. Re-armed after each run, after a
 * reboot or app update, and when the clock or time zone changes.
 */
final class Scheduler {

    private Scheduler() {
    }

    private static final String SCHEME = "hyundailock";

    static void rescheduleAll(Context context) {
        AppSettings settings = new AppSettings(context);
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean ready = settings.ready();
        for (Schedule schedule : settings.schedules()) {
            PendingIntent intent = pending(context, schedule.id);
            alarms.cancel(intent);
            if (!ready || !schedule.enabled) {
                continue;
            }
            long next = schedule.nextRun(now, TimeZone.getDefault());
            if (next > 0) {
                set(alarms, next, intent);
            }
        }
    }

    static void cancel(Context context, long id) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms != null) {
            alarms.cancel(pending(context, id));
        }
    }

    /** The earliest upcoming start among enabled schedules, or -1. */
    static long nextRun(List<Schedule> schedules, long now) {
        long best = -1;
        for (Schedule schedule : schedules) {
            if (!schedule.enabled) {
                continue;
            }
            long next = schedule.nextRun(now, TimeZone.getDefault());
            if (next > 0 && (best < 0 || next < best)) {
                best = next;
            }
        }
        return best;
    }

    /** Whether alarms fire on the minute. If not, Android may run them a few minutes late. */
    static boolean exactAllowed(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        return alarms != null && alarms.canScheduleExactAlarms();
    }

    private static void set(AlarmManager alarms, long at, PendingIntent intent) {
        boolean exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms();
        try {
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
                return;
            }
        } catch (SecurityException e) {
            // Exact alarms were revoked between the check and the call; fall back below.
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
    }

    private static PendingIntent pending(Context context, long id) {
        // The data URI makes each schedule's alarm distinct.
        Intent intent = new Intent(context, ActionReceiver.class)
                .setAction(ActionReceiver.ACTION_RUN_SCHEDULE)
                .setData(Uri.parse(SCHEME + "://schedule/" + id));
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Reads the schedule id back out of an alarm intent; -1 if it isn't one. */
    static long idFrom(Intent intent) {
        Uri data = intent.getData();
        if (data == null || !SCHEME.equals(data.getScheme())) {
            return -1;
        }
        try {
            return Long.parseLong(data.getLastPathSegment());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
