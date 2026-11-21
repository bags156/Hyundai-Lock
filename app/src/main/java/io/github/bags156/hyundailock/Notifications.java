package io.github.bags156.hyundailock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;

import java.text.DateFormat;
import java.util.Date;

/** The countdown before an automatic lock, and command results. */
final class Notifications {

    private Notifications() {
    }

    static final String CHANNEL_AUTOLOCK = "autolock";
    static final String CHANNEL_RESULTS = "results";
    static final String CHANNEL_SCHEDULES = "schedules";
    static final String CHANNEL_SCHEDULES_SKIPPED = "schedules_skipped";

    static final int ID_COUNTDOWN = 10;
    static final int ID_RESULT = 11;
    static final int ID_SCHEDULE = 12;

    private static final int ACCENT = 0xFF2F6FDE;

    static void ensureChannels(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        NotificationChannel countdown = new NotificationChannel(CHANNEL_AUTOLOCK, "Auto-lock countdown",
                NotificationManager.IMPORTANCE_HIGH);
        countdown.setDescription("Shown when an unlocked alert arrives, with a button to stop the lock.");
        NotificationChannel results = new NotificationChannel(CHANNEL_RESULTS, "Lock results",
                NotificationManager.IMPORTANCE_DEFAULT);
        results.setDescription("Whether automatic and Quick Settings commands worked.");
        NotificationChannel schedules = new NotificationChannel(CHANNEL_SCHEDULES, "Scheduled starts",
                NotificationManager.IMPORTANCE_DEFAULT);
        schedules.setDescription("When a schedule starts the car's climate, or couldn't.");
        NotificationChannel skipped = new NotificationChannel(CHANNEL_SCHEDULES_SKIPPED,
                "Skipped schedules", NotificationManager.IMPORTANCE_LOW);
        skipped.setDescription("When a schedule didn't start the car because of the temperature.");
        nm.createNotificationChannel(countdown);
        nm.createNotificationChannel(results);
        nm.createNotificationChannel(schedules);
        nm.createNotificationChannel(skipped);
    }

    static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == PackageManager.PERMISSION_GRANTED;
        }
        return context.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    /** "Car unlocked · locking at 4:27 PM" with a live countdown, Don't lock and Lock now. */
    static void countdown(Context context, long dueAt, String alert) {
        if (!canPost(context)) {
            return;
        }
        ensureChannels(context);
        long remaining = Math.max(0, dueAt - System.currentTimeMillis());
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_AUTOLOCK)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setColor(ACCENT)
                .setContentTitle("Car unlocked · locking at " + DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(new Date(dueAt)))
                .setContentText(alert == null || alert.isEmpty() ? "From an unlocked alert." : alert)
                .setStyle(new Notification.BigTextStyle().bigText(
                        (alert == null || alert.isEmpty() ? "" : alert + "\n")
                                + "Tap Don't lock if you're still using the car."))
                .setWhen(dueAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setTimeoutAfter(remaining + 5 * 60_000L)
                .setContentIntent(openApp(context))
                .addAction(action(context, "Don't lock", ActionReceiver.ACTION_CANCEL))
                .addAction(action(context, "Lock now", ActionReceiver.ACTION_LOCK_NOW));
        context.getSystemService(NotificationManager.class).notify(ID_COUNTDOWN, builder.build());
    }

    static void result(Context context, String title, String text, boolean offerRetry,
            boolean offerLockAnyway) {
        if (!canPost(context)) {
            return;
        }
        ensureChannels(context);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_RESULTS)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setColor(ACCENT)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(openApp(context));
        if (text != null && !text.isEmpty()) {
            builder.setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text));
        }
        if (offerRetry) {
            builder.addAction(action(context, "Try again", ActionReceiver.ACTION_RETRY_LOCK));
        }
        if (offerLockAnyway) {
            builder.addAction(action(context, "Lock anyway", ActionReceiver.ACTION_LOCK_ANYWAY));
        }
        context.getSystemService(NotificationManager.class).notify(ID_RESULT, builder.build());
    }

    /** A schedule's outcome. Skips go to a quiet channel. */
    static void schedule(Context context, String title, String text, boolean skipped) {
        if (!canPost(context)) {
            return;
        }
        ensureChannels(context);
        Notification.Builder builder = new Notification.Builder(context,
                skipped ? CHANNEL_SCHEDULES_SKIPPED : CHANNEL_SCHEDULES)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setColor(ACCENT)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(openApp(context));
        if (text != null && !text.isEmpty()) {
            builder.setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text));
        }
        context.getSystemService(NotificationManager.class).notify(ID_SCHEDULE, builder.build());
    }

    static void cancel(Context context, int id) {
        context.getSystemService(NotificationManager.class).cancel(id);
    }

    private static Notification.Action action(Context context, String label, String intentAction) {
        return new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_stat_lock), label,
                ActionReceiver.pending(context, intentAction)).build();
    }

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
