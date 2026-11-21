package io.github.bags156.hyundailock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.quicksettings.TileService;

import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.github.bags156.hyundailock.core.ActionState;
import io.github.bags156.hyundailock.core.AutoLockGate;
import io.github.bags156.hyundailock.core.Delays;
import io.github.bags156.hyundailock.core.Vehicle;
import io.github.bags156.hyundailock.core.VehicleStatus;

/**
 * Runs every car command, one at a time, on a background thread, and owns the auto-lock
 * schedule. Shared by the screen, the notification listener, notification buttons and the
 * Quick Settings tile.
 */
final class CarControl {

    enum Command {
        LOCK("Lock", "Locked"),
        UNLOCK("Unlock", "Unlocked"),
        REFRESH("Refresh", "Status updated"),
        WAKE_REFRESH("Wake car", "Status updated from the car"),
        CLIMATE_START("Start climate", "Climate started"),
        CLIMATE_STOP("Stop climate", "Climate stopped");

        final String label;
        final String done;

        Command(String label, String done) {
            this.label = label;
            this.done = done;
        }
    }

    /** Where a command came from. Anything but APP reports its result as a notification. */
    enum Source {
        APP,
        TILE,
        NOTIFICATION,
        AUTO
    }

    interface Listener {
        void onCarChanged();
    }

    private static final long CONFIRM_TIMEOUT_MS = 90_000;
    private static final long CONFIRM_POLL_MS = 3_000;
    private static final long AUTO_RETRY_DELAY_MS = 30_000;
    private static final long BACKUP_ALARM_SLACK_MS = 60_000;
    private static final long COMMAND_WAKE_MS = 3 * 60_000;

    private static CarControl instance;

    static synchronized CarControl get(Context context) {
        if (instance == null) {
            instance = new CarControl(context.getApplicationContext());
        }
        return instance;
    }

    private final Context app;
    private final AppSettings settings;
    private final BluelinkClient client = new BluelinkClient();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final PowerManager.WakeLock pendingWake;
    private final PowerManager.WakeLock commandWake;
    /** Held from a failed automatic lock until its retry finishes. */
    private final PowerManager.WakeLock retryWake;

    private ScheduledFuture<?> pendingLock; // guarded by this
    private volatile String busy;
    private volatile String message;

    private CarControl(Context app) {
        this.app = app;
        this.settings = new AppSettings(app);
        PowerManager power = app.getSystemService(PowerManager.class);
        pendingWake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HyundaiLock:pendingLock");
        pendingWake.setReferenceCounted(false);
        commandWake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HyundaiLock:command");
        commandWake.setReferenceCounted(false);
        retryWake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HyundaiLock:retry");
        retryWake.setReferenceCounted(false);
    }

    // ---- state for the screen ----

    void addListener(Listener listener) {
        listeners.add(listener);
    }

    void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** The command in progress, e.g. "Locking…", or null. */
    String busy() {
        return busy;
    }

    /** The latest result or error from a command started on the screen. */
    String message() {
        return message;
    }

    private void changed() {
        main.post(() -> {
            for (Listener listener : listeners) {
                listener.onCarChanged();
            }
        });
        try {
            TileService.requestListeningState(app, new ComponentName(app, LockTileService.class));
        } catch (RuntimeException ignored) {
            // The tile refreshes itself the next time the panel opens.
        }
    }

    // ---- account ----

    void signIn(String email, String password, String pin, Consumer<List<Vehicle>> onVehicles) {
        worker.execute(() -> {
            busy = "Signing in…";
            message = null;
            changed();
            try {
                BluelinkClient.Credentials credentials =
                        new BluelinkClient.Credentials(email.trim(), password, pin.trim());
                List<Vehicle> vehicles = client.signIn(credentials);
                settings.saveAccount(credentials.email, Secrets.encrypt(password),
                        Secrets.encrypt(credentials.pin));
                if (vehicles.size() == 1) {
                    settings.setVehicle(vehicles.get(0));
                }
                settings.addLog("Signed in as " + credentials.email);
                message = vehicles.size() == 1
                        ? "Signed in. Using " + vehicles.get(0).displayName() + "."
                        : "Signed in. Choose which car to control.";
                main.post(() -> onVehicles.accept(vehicles));
            } catch (GeneralSecurityException e) {
                message = "Couldn't store your login securely on this phone: " + e.getMessage();
            } catch (Exception e) {
                message = describe(e);
            } finally {
                busy = null;
                changed();
            }
        });
    }

    /** Lists the cars again, for switching between them. */
    void listVehicles(Consumer<List<Vehicle>> onVehicles) {
        worker.execute(() -> {
            busy = "Loading cars…";
            changed();
            try {
                List<Vehicle> vehicles = client.signIn(credentials());
                main.post(() -> onVehicles.accept(vehicles));
            } catch (Exception e) {
                message = describe(e);
            } finally {
                busy = null;
                changed();
            }
        });
    }

    void chooseVehicle(Vehicle vehicle) {
        settings.setVehicle(vehicle);
        message = "Using " + vehicle.displayName() + ".";
        changed();
    }

    void signOut() {
        cancelPendingLock(null);
        client.forget();
        settings.clearAccount();
        Secrets.destroy();
        settings.addLog("Signed out");
        message = "Signed out. Your login was removed from this phone.";
        changed();
    }

    private BluelinkClient.Credentials credentials() throws BluelinkException {
        String email = settings.email();
        String passwordEnc = settings.passwordEnc();
        String pinEnc = settings.pinEnc();
        if (email == null || passwordEnc == null || pinEnc == null) {
            throw new BluelinkException("Sign in first.");
        }
        try {
            return new BluelinkClient.Credentials(email, Secrets.decrypt(passwordEnc),
                    Secrets.decrypt(pinEnc));
        } catch (GeneralSecurityException e) {
            throw new BluelinkException("Your saved login can't be read on this phone any more. "
                    + "Sign out and sign in again.");
        }
    }

    private Vehicle vehicle() throws BluelinkException {
        Vehicle vehicle = settings.vehicle();
        if (vehicle == null) {
            throw new BluelinkException("Choose a car first.");
        }
        return vehicle;
    }

    // ---- commands ----

    void run(Command command, Source source) {
        if (source != Source.APP) {
            commandWake.acquire(COMMAND_WAKE_MS);
        }
        worker.execute(() -> execute(command, source, 0));
    }

    private void execute(Command command, Source source, int attempt) {
        busy = progressLabel(command);
        if (source == Source.APP) {
            message = null;
        }
        changed();
        try {
            BluelinkClient.Credentials credentials = credentials();
            Vehicle vehicle = vehicle();
            String transactionId;
            switch (command) {
                case REFRESH:
                case WAKE_REFRESH:
                    VehicleStatus status = client.status(credentials, vehicle,
                            command == Command.WAKE_REFRESH);
                    settings.saveStatus(status, System.currentTimeMillis());
                    succeed(command, source, command.done);
                    return;
                case LOCK:
                    transactionId = client.lock(credentials, vehicle, true);
                    break;
                case UNLOCK:
                    // Set first: the official app's own "unlocked" alert can arrive within seconds.
                    settings.setLastManualUnlockAt(System.currentTimeMillis());
                    cancelPendingLock("you unlocked from this app");
                    transactionId = client.lock(credentials, vehicle, false);
                    break;
                case CLIMATE_START:
                    transactionId = client.startClimate(credentials, vehicle, settings.climate());
                    break;
                case CLIMATE_STOP:
                    transactionId = client.stopClimate(credentials, vehicle);
                    break;
                default:
                    throw new IllegalStateException(command.name());
            }
            confirm(credentials, vehicle, command, source, attempt, transactionId);
        } catch (Exception e) {
            failed(command, source, attempt, describe(e));
        } finally {
            busy = null;
            changed();
            if (source != Source.APP && commandWake.isHeld()) {
                commandWake.release();
            }
            if (attempt > 0 && retryWake.isHeld()) {
                retryWake.release();
            }
        }
    }

    /** Waits for the car to report that it carried out the command. */
    private void confirm(BluelinkClient.Credentials credentials, Vehicle vehicle, Command command,
            Source source, int attempt, String transactionId) throws Exception {
        if (transactionId == null) {
            succeed(command, source, command.done + " (sent; Hyundai gave no way to confirm it)");
            return;
        }
        busy = "Waiting for the car…";
        changed();
        long deadline = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(CONFIRM_POLL_MS);
            ActionState state = client.actionState(credentials, vehicle, transactionId);
            if (state == ActionState.SUCCESS) {
                rememberLockState(command);
                succeed(command, source, command.done);
                return;
            }
            if (state == ActionState.FAILED) {
                failed(command, source, attempt, "The car reported that "
                        + command.label.toLowerCase(Locale.ROOT)
                        + " didn't work.");
                return;
            }
        }
        succeed(command, source, "Sent, but the car hasn't confirmed yet. Refresh in a minute to check.");
    }

    private void rememberLockState(Command command) {
        if (command != Command.LOCK && command != Command.UNLOCK) {
            return;
        }
        VehicleStatus status = settings.status();
        if (status != null) {
            settings.saveStatus(status.withLocked(command == Command.LOCK), settings.statusFetchedAt());
        }
    }

    private void succeed(Command command, Source source, String text) {
        String stamped = text + " · " + time(System.currentTimeMillis());
        settings.setLastResult(stamped);
        if (command != Command.REFRESH && command != Command.WAKE_REFRESH) {
            settings.addLog(sourceLabel(source) + text);
        }
        if (source == Source.APP) {
            message = text;
        } else if (command == Command.LOCK) {
            Notifications.result(app, source == Source.AUTO ? "Car locked automatically" : "Car locked",
                    text.equals(command.done) ? "Confirmed by the car." : text, false, false);
        } else {
            Notifications.result(app, text, null, false, false);
        }
    }

    private void failed(Command command, Source source, int attempt, String reason) {
        settings.setLastResult(command.label + " failed · " + time(System.currentTimeMillis()));
        settings.addLog(sourceLabel(source) + command.label + " failed: " + reason);
        if (source == Source.APP) {
            message = command.label + " failed: " + reason;
            return;
        }
        if (source == Source.AUTO && command == Command.LOCK && attempt == 0) {
            Notifications.result(app, "Couldn't lock yet, trying again in 30 seconds", reason, false, false);
            retryWake.acquire(COMMAND_WAKE_MS + AUTO_RETRY_DELAY_MS);
            worker.schedule(() -> execute(Command.LOCK, Source.AUTO, 1), AUTO_RETRY_DELAY_MS,
                    TimeUnit.MILLISECONDS);
            return;
        }
        Notifications.result(app, "Couldn't " + command.label.toLowerCase(Locale.ROOT) + " the car", reason,
                command == Command.LOCK, false);
    }

    private static String progressLabel(Command command) {
        switch (command) {
            case LOCK:
                return "Locking…";
            case UNLOCK:
                return "Unlocking…";
            case REFRESH:
                return "Getting status…";
            case WAKE_REFRESH:
                return "Waking the car (can take a minute)…";
            case CLIMATE_START:
                return "Starting climate…";
            case CLIMATE_STOP:
                return "Stopping climate…";
            default:
                return "Working…";
        }
    }

    private static String sourceLabel(Source source) {
        switch (source) {
            case AUTO:
                return "Auto-lock: ";
            case TILE:
                return "Tile: ";
            case NOTIFICATION:
                return "Notification: ";
            default:
                return "";
        }
    }

    static String describe(Exception e) {
        if (e instanceof BluelinkException) {
            return e.getMessage();
        }
        if (e instanceof javax.net.ssl.SSLException) {
            return "Secure connection to Hyundai failed: " + e.getMessage();
        }
        if (e instanceof java.io.IOException) {
            return "Network problem: " + e.getMessage();
        }
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return "Interrupted.";
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private String time(long millis) {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(millis));
    }

    // ---- auto-lock ----

    /** Called for every notification posted by a watched app. */
    void onWatchedNotification(String text, String matchedTrigger) {
        if (matchedTrigger == null) {
            settings.addLog("Alert (no match): " + text);
            changed();
            return;
        }
        AutoLockGate.Inputs in = new AutoLockGate.Inputs();
        synchronized (this) {
            in.enabled = settings.autoLockEnabled();
            in.ready = settings.ready();
            in.now = System.currentTimeMillis();
            in.pausedUntil = settings.pausedUntil();
            in.lastAutoLockAt = settings.lastAutoLockAt();
            in.lastManualUnlockAt = settings.lastManualUnlockAt();
            in.pendingDueAt = settings.pendingDueAt();
            AutoLockGate.Decision decision = AutoLockGate.decide(in);
            if (decision != AutoLockGate.Decision.SCHEDULE) {
                settings.addLog("Alert matched \"" + matchedTrigger + "\", not locking ("
                        + decision.reason + "): " + text);
                changed();
                return;
            }
            long delay = settings.delayMs();
            long dueAt = in.now + delay;
            settings.addLog("Alert matched \"" + matchedTrigger + "\": locking in "
                    + Delays.label(delay) + ": " + text);
            schedule(dueAt, text);
        }
    }

    /** Schedules the automatic lock. Caller holds the lock on this. */
    private void schedule(long dueAt, String alert) {
        settings.setPending(dueAt, alert);
        long delay = Math.max(0, dueAt - System.currentTimeMillis());
        if (pendingLock != null) {
            pendingLock.cancel(false);
        }
        // Keep the CPU awake through the countdown so the timer and network aren't held back by
        // the phone sleeping.
        pendingWake.acquire(delay + COMMAND_WAKE_MS);
        pendingLock = worker.schedule(() -> fireAutoLock(true), delay, TimeUnit.MILLISECONDS);
        setBackupAlarm(dueAt + BACKUP_ALARM_SLACK_MS);
        Notifications.countdown(app, dueAt, alert);
        changed();
    }

    /** "Lock now" or "Lock anyway" from a notification. */
    void lockNow(boolean checkEngine) {
        synchronized (this) {
            if (pendingLock != null) {
                pendingLock.cancel(false);
                pendingLock = null;
            }
        }
        commandWake.acquire(COMMAND_WAKE_MS);
        worker.execute(() -> fireAutoLock(checkEngine));
    }

    /** Runs on the worker when the countdown ends. */
    private void fireAutoLock(boolean checkEngine) {
        synchronized (this) {
            pendingLock = null;
            settings.clearPending();
            // Recorded before sending, so the car's own "locked" chatter can't start a loop.
            settings.setLastAutoLockAt(System.currentTimeMillis());
        }
        cancelBackupAlarm();
        Notifications.cancel(app, Notifications.ID_COUNTDOWN);
        try {
            if (checkEngine && engineReportedOn()) {
                settings.addLog("Auto-lock: skipped, Hyundai reports the engine is running");
                settings.setLastResult("Auto-lock skipped (engine on) · " + time(System.currentTimeMillis()));
                Notifications.result(app, "Didn't lock: the engine is running",
                        "Someone may be using the car. Tap Lock anyway if that's wrong.", false, true);
                changed();
                return;
            }
            commandWake.acquire(COMMAND_WAKE_MS);
            execute(Command.LOCK, Source.AUTO, 0);
        } finally {
            if (pendingWake.isHeld()) {
                pendingWake.release();
            }
            if (commandWake.isHeld()) {
                commandWake.release();
            }
        }
    }

    /** Uses Hyundai's saved status (not waking the car). Unknown counts as not running. */
    private boolean engineReportedOn() {
        try {
            VehicleStatus status = client.status(credentials(), vehicle(), false);
            settings.saveStatus(status, System.currentTimeMillis());
            return Boolean.TRUE.equals(status.engineOn);
        } catch (Exception e) {
            settings.addLog("Auto-lock: couldn't check engine first (" + describe(e) + "), locking anyway");
            return false;
        }
    }

    /** "Don't lock", sign-out, or turning auto-lock off. {@code why} goes in the log when given. */
    void cancelPendingLock(String why) {
        boolean hadPending;
        synchronized (this) {
            hadPending = pendingLock != null || settings.pendingDueAt() > 0;
            if (pendingLock != null) {
                pendingLock.cancel(false);
                pendingLock = null;
            }
            settings.clearPending();
        }
        cancelBackupAlarm();
        Notifications.cancel(app, Notifications.ID_COUNTDOWN);
        if (pendingWake.isHeld()) {
            pendingWake.release();
        }
        if (hadPending && why != null) {
            settings.addLog("Auto-lock cancelled: " + why);
        }
        changed();
    }

    boolean hasPendingLock() {
        return settings.pendingDueAt() > 0;
    }

    /**
     * Picks up a lock that was scheduled before the app's process stopped. Called when the
     * notification listener connects, when the screen opens and by the backup alarm.
     */
    void resumeOverdue() {
        synchronized (this) {
            long dueAt = settings.pendingDueAt();
            switch (AutoLockGate.overdue(System.currentTimeMillis(), dueAt)) {
                case WAIT:
                    if (pendingLock == null) {
                        schedule(dueAt, settings.pendingAlert());
                    }
                    return;
                case RUN:
                    if (pendingLock == null || pendingLock.isDone()) {
                        pendingLock = null;
                        settings.addLog("Auto-lock: running a lock that was due at " + time(dueAt));
                        commandWake.acquire(COMMAND_WAKE_MS);
                        worker.execute(() -> fireAutoLock(true));
                    }
                    return;
                case EXPIRED:
                    settings.clearPending();
                    settings.addLog("Auto-lock: dropped a lock that was due at " + time(dueAt)
                            + " (more than 15 minutes late)");
                    Notifications.cancel(app, Notifications.ID_COUNTDOWN);
                    changed();
                    return;
                case NONE:
                default:
            }
        }
    }

    private PendingIntent backupAlarmIntent() {
        return ActionReceiver.pending(app, ActionReceiver.ACTION_OVERDUE_CHECK);
    }

    /** In case Android stops the app during the countdown; inexact, so no special permission. */
    private void setBackupAlarm(long at) {
        AlarmManager alarms = app.getSystemService(AlarmManager.class);
        if (alarms != null) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, backupAlarmIntent());
        }
    }

    private void cancelBackupAlarm() {
        AlarmManager alarms = app.getSystemService(AlarmManager.class);
        if (alarms != null) {
            alarms.cancel(backupAlarmIntent());
        }
    }
}
