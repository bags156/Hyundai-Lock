package io.github.bags156.hyundailock;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Re-arms schedules after a reboot, an app update, or a clock or time-zone change. Exported only
 * because Android delivers these system broadcasts that way; other apps can't send them.
 */
public final class SystemEventsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
            case AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED:
                Scheduler.rescheduleAll(context);
                CarControl.get(context).resumeOverdue();
                break;
            default:
        }
    }
}
