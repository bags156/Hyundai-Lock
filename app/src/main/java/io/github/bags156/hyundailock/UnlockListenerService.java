package io.github.bags156.hyundailock;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import io.github.bags156.hyundailock.core.KeywordRules;
import io.github.bags156.hyundailock.core.NotificationText;

/**
 * Watches notifications from the apps listed in settings (MyHyundai by default) for an
 * "unlocked" alert. Notifications from every other app are ignored without being read or stored.
 */
public final class UnlockListenerService extends NotificationListenerService {

    static ComponentName component(Context context) {
        return new ComponentName(context, UnlockListenerService.class);
    }

    /** Whether the user has granted notification access to this app. */
    static boolean hasAccess(Context context) {
        String flat = component(context).flattenToString();
        android.app.NotificationManager nm = context.getSystemService(android.app.NotificationManager.class);
        if (nm != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            return nm.isNotificationListenerAccessGranted(component(context));
        }
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        return enabled != null && enabled.contains(flat);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        CarControl.get(this).resumeOverdue();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) {
            return;
        }
        AppSettings settings = new AppSettings(this);
        KeywordRules rules = settings.rules();
        if (!rules.watches(sbn.getPackageName())) {
            return;
        }
        Notification notification = sbn.getNotification();
        if (notification == null || (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return;
        }
        String text = textOf(notification);
        if (text.isEmpty()) {
            return;
        }
        CarControl.get(this).onWatchedNotification(text, rules.matchedTrigger(text));
    }

    static String textOf(Notification notification) {
        Bundle extras = notification.extras;
        CharSequence title = null;
        CharSequence body = null;
        CharSequence big = null;
        CharSequence sub = null;
        StringBuilder lines = new StringBuilder();
        if (extras != null) {
            title = extras.getCharSequence(Notification.EXTRA_TITLE);
            body = extras.getCharSequence(Notification.EXTRA_TEXT);
            big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
            CharSequence[] textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (textLines != null) {
                for (CharSequence line : textLines) {
                    if (line != null) {
                        lines.append(line).append(' ');
                    }
                }
            }
        }
        return NotificationText.combine(title, body, big, lines, sub, notification.tickerText);
    }
}
