package io.github.bags156.hyundailock;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Notification buttons and the backup alarm. Not exported: only this app can send these. */
public final class ActionReceiver extends BroadcastReceiver {

    static final String ACTION_CANCEL = "io.github.bags156.hyundailock.CANCEL_LOCK";
    static final String ACTION_LOCK_NOW = "io.github.bags156.hyundailock.LOCK_NOW";
    static final String ACTION_LOCK_ANYWAY = "io.github.bags156.hyundailock.LOCK_ANYWAY";
    static final String ACTION_RETRY_LOCK = "io.github.bags156.hyundailock.RETRY_LOCK";
    static final String ACTION_OVERDUE_CHECK = "io.github.bags156.hyundailock.OVERDUE_CHECK";

    static PendingIntent pending(Context context, String action) {
        Intent intent = new Intent(context, ActionReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(context, action.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            return;
        }
        CarControl car = CarControl.get(context);
        switch (action) {
            case ACTION_CANCEL:
                car.cancelPendingLock("you tapped Don't lock");
                Notifications.result(context, "Auto-lock cancelled", "The car stays as it is.", false, false);
                break;
            case ACTION_LOCK_NOW:
                car.lockNow(true);
                break;
            case ACTION_LOCK_ANYWAY:
                Notifications.cancel(context, Notifications.ID_RESULT);
                car.lockNow(false);
                break;
            case ACTION_RETRY_LOCK:
                Notifications.cancel(context, Notifications.ID_RESULT);
                car.run(CarControl.Command.LOCK, CarControl.Source.NOTIFICATION);
                break;
            case ACTION_OVERDUE_CHECK:
                car.resumeOverdue();
                break;
            default:
        }
    }
}
