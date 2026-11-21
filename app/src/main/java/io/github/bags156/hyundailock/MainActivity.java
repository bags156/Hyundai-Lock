package io.github.bags156.hyundailock;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import io.github.bags156.hyundailock.core.ClimateOptions;
import io.github.bags156.hyundailock.core.Delays;
import io.github.bags156.hyundailock.core.EventLog;
import io.github.bags156.hyundailock.core.KeywordRules;
import io.github.bags156.hyundailock.core.Schedule;
import io.github.bags156.hyundailock.core.Weather;
import io.github.bags156.hyundailock.core.TimeText;
import io.github.bags156.hyundailock.core.Vehicle;
import io.github.bags156.hyundailock.core.VehicleStatus;

/**
 * The only screen: the car's state and Lock/Unlock first, then climate, auto-lock, activity and
 * the account. Built in code with platform views; all car work happens in CarControl.
 */
// Text is built in code for a single-language personal app.
@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity implements CarControl.Listener {

    private static final int REQUEST_NOTIFICATIONS = 1;
    private static final long PAUSE_MS = 60 * 60_000L;
    /** Opening the app refreshes Hyundai's saved status when it's older than this. */
    private static final long AUTO_REFRESH_AFTER_MS = 2 * 60_000L;
    /** Success messages fade after this; errors stay until the next action. */
    private static final long MESSAGE_SHOWN_MS = 12_000L;
    private static final long TICK_MS = 15_000L;
    private static final String[] DELAY_LABELS = {"15s", "30s", "1m", "2m", "3m", "5m", "10m"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            main.postDelayed(this, TICK_MS);
        }
    };

    private AppSettings settings;
    private CarControl car;
    private Ui ui;
    private boolean demo;
    private boolean rendering;
    private boolean showAllActivity;

    // Signed out
    private LinearLayout welcome;
    private EditText emailField;
    private EditText passwordField;
    private EditText pinField;
    private Button signInButton;
    private TextView welcomeStatus;

    // Signed in
    private LinearLayout content;
    private TextView carName;
    private TextView reportedText;
    private ImageButton refreshButton;

    private LinearLayout pendingCard;
    private Chronometer pendingClock;
    private TextView pendingAlert;

    private View lockCircle;
    private ImageView lockIcon;
    private TextView lockState;
    private TextView summaryText;
    private LinearLayout busyRow;
    private TextView busyText;
    private TextView messageText;
    private Button lockButton;
    private Button unlockButton;
    private Button wakeButton;

    private TextView tempValue;
    private TextView minutesValue;
    private Button tempDown;
    private Button tempUp;
    private Button minutesDown;
    private Button minutesUp;
    private Button defrostChip;
    private Button heatChip;
    private Button climateStartButton;
    private Button climateStopButton;

    private TextView schedulesSubtitle;
    private TextView weatherText;
    private Button weatherButton;
    private LinearLayout exactRow;
    private LinearLayout scheduleRows;

    private Switch autoSwitch;
    private TextView autoSubtitle;
    private LinearLayout autoDetails;
    private TextView accessText;
    private Button accessButton;
    private TextView batteryText;
    private Button batteryButton;
    private final Button[] delayChips = new Button[Delays.CHOICES_MS.length];
    private Button pauseButton;
    private Button rulesToggle;
    private LinearLayout rulesBox;
    private EditText triggersField;
    private EditText ignoresField;
    private EditText packagesField;

    private LinearLayout activityRows;
    private Button activityMore;

    private TextView accountText;

    // ---- lifecycle ----

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new AppSettings(this);
        car = CarControl.get(this);
        ui = new Ui(this);
        String demoVariant = getIntent() == null ? null : getIntent().getStringExtra("demo");
        if (demoVariant != null && DemoData.allowed(this)) {
            DemoData.seed(this, demoVariant);
            demo = true;
        }
        Notifications.ensureChannels(this);
        styleWindow();
        setContentView(buildUi());
        askForNotificationPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        car.addListener(this);
        car.resumeOverdue();
        if (settings.autoLockEnabled() && UnlockListenerService.hasAccess(this)) {
            // If Android unbound the listener (for example after an update), ask for it back.
            NotificationListenerService.requestRebind(UnlockListenerService.component(this));
        }
        render();
        refreshIfStale();
        if (!demo) {
            car.refreshWeatherIfStale();
        }
        main.postDelayed(tick, TICK_MS);
    }

    @Override
    protected void onStop() {
        main.removeCallbacks(tick);
        car.removeListener(this);
        super.onStop();
    }

    @Override
    public void onCarChanged() {
        render();
    }

    private void refreshIfStale() {
        if (!demo && settings.ready() && car.busy() == null
                && System.currentTimeMillis() - settings.statusFetchedAt() > AUTO_REFRESH_AFTER_MS) {
            car.run(CarControl.Command.REFRESH, CarControl.Source.SILENT);
        }
    }

    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"},
                    REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        render();
    }

    @SuppressWarnings("deprecation") // Bar colors only apply before Android 15's edge-to-edge.
    private void styleWindow() {
        getWindow().getDecorView().setBackgroundColor(ui.background);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            getWindow().setStatusBarColor(ui.background);
            getWindow().setNavigationBarColor(ui.background);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setBarAppearance(!ui.night);
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private void setBarAppearance(boolean lightBars) {
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(lightBars ? mask : 0, mask);
        }
    }

    // ---- actions ----

    private void signIn() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();
        String pin = pinField.getText().toString().trim();
        if (email.isEmpty() || password.isEmpty() || pin.isEmpty()) {
            showInfo("Sign in", "Enter the email, password and PIN you use in the MyHyundai app.");
            return;
        }
        car.signIn(email, password, pin, vehicles -> {
            passwordField.setText("");
            pinField.setText("");
            if (vehicles.size() > 1) {
                chooseVehicle(vehicles);
            } else {
                car.run(CarControl.Command.REFRESH, CarControl.Source.SILENT);
            }
            render();
        });
    }

    private void chooseVehicle(List<Vehicle> vehicles) {
        String[] names = new String[vehicles.size()];
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle v = vehicles.get(i);
            names[i] = v.displayName() + "  ·  VIN …" + v.vin.substring(Math.max(0, v.vin.length() - 6));
        }
        new AlertDialog.Builder(this)
                .setTitle("Which car?")
                .setItems(names, (dialog, which) -> car.chooseVehicle(vehicles.get(which)))
                .show();
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
                .setTitle("Sign out?")
                .setMessage("This removes your MyHyundai login from this phone. Auto-lock stops "
                        + "until you sign in again.")
                .setPositiveButton("Sign out", (d, w) -> car.signOut())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmUnlock() {
        new AlertDialog.Builder(this)
                .setTitle("Unlock the car?")
                .setMessage("Auto-lock ignores unlocked alerts for 10 minutes after you unlock here.")
                .setPositiveButton("Unlock", (d, w) ->
                        car.run(CarControl.Command.UNLOCK, CarControl.Source.APP))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmWake() {
        new AlertDialog.Builder(this)
                .setTitle("Wake the car?")
                .setMessage("This asks the car itself instead of Hyundai's last saved copy. It can "
                        + "take a minute, and Hyundai limits how often cars can be woken because it "
                        + "uses the 12-volt battery.")
                .setPositiveButton("Wake car", (d, w) ->
                        car.run(CarControl.Command.WAKE_REFRESH, CarControl.Source.APP))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setAutoLock(boolean enabled) {
        if (rendering) {
            return;
        }
        if (enabled && !settings.ready()) {
            showInfo("Sign in first", "Auto-lock needs your MyHyundai login and a chosen car.");
            render();
            return;
        }
        settings.setAutoLockEnabled(enabled);
        settings.addLog(enabled ? "Auto-lock turned on" : "Auto-lock turned off");
        if (!enabled) {
            car.cancelPendingLock("auto-lock was turned off");
        } else if (!UnlockListenerService.hasAccess(this)) {
            showAccessHelp();
        }
        render();
    }

    private void togglePause() {
        long now = System.currentTimeMillis();
        if (settings.pausedUntil() > now) {
            settings.setPausedUntil(0);
            settings.addLog("Auto-lock resumed");
        } else {
            settings.setPausedUntil(now + PAUSE_MS);
            car.cancelPendingLock("auto-lock was paused");
            settings.addLog("Auto-lock paused for 1 hour");
        }
        render();
    }

    private void changeClimate(int tempDelta, int minutesDelta) {
        ClimateOptions current = settings.climate();
        settings.setClimate(new ClimateOptions(current.tempF + tempDelta,
                current.minutes + minutesDelta, current.defrost, current.heat));
        render();
    }

    private void toggleClimate(boolean defrost) {
        ClimateOptions c = settings.climate();
        settings.setClimate(new ClimateOptions(c.tempF, c.minutes,
                defrost ? !c.defrost : c.defrost, defrost ? c.heat : !c.heat));
        render();
    }

    private void saveRules() {
        String triggers = triggersField.getText().toString().trim();
        if (KeywordRules.parseList(triggers).isEmpty()) {
            showInfo("Add a trigger phrase", "Without a trigger phrase, no alert would ever count as "
                    + "\"unlocked\". The default is: " + KeywordRules.DEFAULT_TRIGGERS);
            return;
        }
        String packages = packagesField.getText().toString().trim();
        if (KeywordRules.parseList(packages).isEmpty()) {
            showInfo("Add an app", "List at least one app to watch. MyHyundai is "
                    + KeywordRules.DEFAULT_PACKAGES);
            return;
        }
        settings.setRules(packages, triggers, ignoresField.getText().toString().trim());
        settings.addLog("Alert matching rules saved");
        render();
    }

    private void resetRules() {
        settings.resetRules();
        triggersField.setText(settings.triggers());
        ignoresField.setText(settings.ignores());
        packagesField.setText(settings.packages());
        settings.addLog("Alert matching rules reset to defaults");
        render();
    }

    private void openNotificationAccess() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            UnlockListenerService.component(this).flattenToString());
        } else {
            intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    @SuppressLint("BatteryLife") // Sideloaded personal app; this is the point of the button.
    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
        }
    }

    private void showAccessHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Notification access")
                .setMessage("Auto-lock needs to see the MyHyundai app's notifications to notice an "
                        + "unlocked alert. It only reads notifications from the apps listed under "
                        + "Alert matching and ignores everything else.\n\n"
                        + "If the switch is greyed out, Android is protecting a sideloaded app: open "
                        + "App info, tap ⋮ (top right), choose \"Allow restricted settings\", then "
                        + "try again.")
                .setPositiveButton("Open settings", (d, w) -> openNotificationAccess())
                .setNeutralButton("App info", (d, w) -> startActivity(new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null))))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showInfo(String title, String text) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(text)
                .setPositiveButton("OK", null).show();
    }

    // ---- rendering ----

    private void render() {
        if (content == null) {
            return;
        }
        rendering = true;
        try {
            boolean hasAccount = settings.hasAccount();
            welcome.setVisibility(hasAccount ? View.GONE : View.VISIBLE);
            content.setVisibility(hasAccount ? View.VISIBLE : View.GONE);
            String busy = car.busy();
            if (!hasAccount) {
                renderWelcome(busy);
                return;
            }
            Vehicle vehicle = settings.vehicle();
            boolean ready = vehicle != null;
            boolean idle = busy == null;

            carName.setText(vehicle != null ? vehicle.displayName() : "Choose a car");
            reportedText.setText(reportedLine());
            Ui.enable(refreshButton, ready && idle);

            renderPending();
            renderLockState(settings.status());

            busyRow.setVisibility(busy != null ? View.VISIBLE : View.GONE);
            busyText.setText(busy == null ? "" : busy);
            renderMessage(messageText, busy == null);

            for (View v : new View[] {lockButton, unlockButton, wakeButton, climateStartButton,
                    climateStopButton}) {
                Ui.enable(v, ready && idle);
            }

            ClimateOptions climate = settings.climate();
            tempValue.setText(climate.tempF + "°");
            minutesValue.setText(String.valueOf(climate.minutes));
            Ui.enable(tempDown, climate.tempF > ClimateOptions.MIN_TEMP_F);
            Ui.enable(tempUp, climate.tempF < ClimateOptions.MAX_TEMP_F);
            Ui.enable(minutesDown, climate.minutes > ClimateOptions.MIN_MINUTES);
            Ui.enable(minutesUp, climate.minutes < ClimateOptions.MAX_MINUTES);
            ui.styleChip(defrostChip, climate.defrost);
            ui.styleChip(heatChip, climate.heat);

            renderSchedules(ready);
            renderAutoLock(ready);
            renderActivity();
            accountText.setText(settings.email() + (vehicle != null
                    ? "\n" + vehicle.displayName() + (vehicle.isEv() ? " · electric" : "")
                            + " · VIN …" + vehicle.vin.substring(Math.max(0, vehicle.vin.length() - 6))
                    : "\nNo car chosen yet"));
        } finally {
            rendering = false;
        }
    }

    private void renderWelcome(String busy) {
        Ui.enable(signInButton, busy == null);
        signInButton.setText(busy != null ? busy : "Sign in");
        renderMessage(welcomeStatus, busy == null);
    }

    private void renderMessage(TextView view, boolean allowed) {
        String message = car.message();
        boolean error = car.messageIsError();
        boolean fresh = System.currentTimeMillis() - car.messageAt() < MESSAGE_SHOWN_MS;
        boolean show = allowed && message != null && !message.isEmpty() && (error || fresh);
        view.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            view.setText(message);
            view.setTextColor(error ? ui.bad : ui.good);
        }
    }

    private String reportedLine() {
        VehicleStatus status = settings.status();
        if (status == null) {
            return "No status yet";
        }
        long now = System.currentTimeMillis();
        StringBuilder line = new StringBuilder();
        Long reported = status.reportedAtMillis();
        if (reported != null) {
            line.append("Car reported ").append(dayAndTime(reported));
        }
        long fetched = settings.statusFetchedAt();
        if (fetched > 0) {
            if (line.length() > 0) {
                line.append(" · ");
            }
            line.append("checked ").append(TimeText.ago(now, fetched));
        }
        return line.toString();
    }

    private void renderLockState(VehicleStatus status) {
        int color;
        int icon;
        String label;
        if (status == null || status.locked == null) {
            color = ui.textSecondary;
            icon = R.drawable.ic_lock_unknown;
            label = status == null ? "No status yet" : "Lock unknown";
        } else if (status.locked) {
            color = ui.good;
            icon = R.drawable.ic_lock_closed;
            label = "Locked";
        } else {
            color = ui.warn;
            icon = R.drawable.ic_lock_open;
            label = "Unlocked";
        }
        lockCircle.setBackground(ui.circle(Ui.alpha(color, 0.16f)));
        lockIcon.setImageResource(icon);
        lockIcon.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        lockState.setText(label);
        lockState.setTextColor(color);
        String summary = status == null ? "Tap refresh to load" : status.summaryLine();
        summaryText.setText(summary);
        summaryText.setVisibility(summary.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void renderPending() {
        long dueAt = settings.pendingDueAt();
        boolean pending = dueAt > 0;
        pendingCard.setVisibility(pending ? View.VISIBLE : View.GONE);
        if (pending) {
            long remaining = dueAt - System.currentTimeMillis();
            pendingClock.setBase(SystemClock.elapsedRealtime() + remaining);
            pendingClock.start();
            String alert = settings.pendingAlert();
            pendingAlert.setText(alert == null || alert.isEmpty() ? "After an unlocked alert" : alert);
        } else {
            pendingClock.stop();
        }
    }

    private void renderAutoLock(boolean ready) {
        boolean enabled = settings.autoLockEnabled();
        autoSwitch.setChecked(enabled);
        Ui.enable(autoSwitch, ready || enabled);
        long pausedUntil = settings.pausedUntil();
        boolean paused = pausedUntil > System.currentTimeMillis();
        long delay = settings.delayMs();
        if (!enabled) {
            autoSubtitle.setText("Off. Turn on to lock the car after MyHyundai says it's unlocked.");
        } else if (paused) {
            autoSubtitle.setText("Paused until " + time(pausedUntil));
        } else {
            autoSubtitle.setText("Locks " + Delays.label(delay)
                    + " after MyHyundai says the car is unlocked");
        }
        autoDetails.setVisibility(enabled ? View.VISIBLE : View.GONE);

        boolean access = UnlockListenerService.hasAccess(this);
        accessText.setText(access ? "✓  Notification access allowed" : "!  Notification access needed");
        accessText.setTextColor(access ? ui.good : ui.warn);
        accessButton.setVisibility(access ? View.GONE : View.VISIBLE);

        PowerManager power = getSystemService(PowerManager.class);
        boolean unrestricted = power != null && power.isIgnoringBatteryOptimizations(getPackageName());
        batteryText.setText(unrestricted ? "✓  Battery unrestricted"
                : "!  Battery optimized: Android may stop auto-lock");
        batteryText.setTextColor(unrestricted ? ui.good : ui.warn);
        batteryButton.setVisibility(unrestricted ? View.GONE : View.VISIBLE);

        int selected = Delays.indexOf(delay);
        for (int i = 0; i < delayChips.length; i++) {
            ui.styleChip(delayChips[i], i == selected);
        }
        pauseButton.setText(paused ? "Resume now" : "Pause for 1 hour");
        rulesToggle.setText(rulesBox.getVisibility() == View.VISIBLE
                ? "Alert matching  ▴" : "Alert matching  ▾");
    }

    private void renderActivity() {
        List<EventLog.Entry> entries = EventLog.newest(settings.log(), showAllActivity ? 25 : 4);
        activityRows.removeAllViews();
        if (entries.isEmpty()) {
            TextView empty = ui.text(14, ui.textSecondary);
            empty.setText("Nothing yet. MyHyundai alerts, automatic locks and tile commands show up "
                    + "here, so you can see exactly what MyHyundai's notifications say.");
            activityRows.addView(empty);
        }
        for (EventLog.Entry entry : entries) {
            LinearLayout row = ui.row();
            row.setGravity(Gravity.TOP);
            row.setPadding(0, ui.dp(6), 0, ui.dp(6));
            TextView when = ui.text(13, ui.textSecondary);
            when.setText(dayAndTime(entry.time));
            row.addView(when, new LinearLayout.LayoutParams(ui.dp(92), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView what = ui.text(14, ui.text);
            what.setText(entry.text);
            row.addView(what, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            activityRows.addView(row);
        }
        int total = EventLog.parse(settings.log()).size();
        activityMore.setVisibility(total > 4 ? View.VISIBLE : View.GONE);
        activityMore.setText(showAllActivity ? "Show less" : "Show more");
    }

    private String time(long millis) {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(millis));
    }

    /** "10:08 AM" today, "Nov 20, 10:08 AM" otherwise. */
    private String dayAndTime(long millis) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        Calendar today = Calendar.getInstance();
        boolean sameDay = then.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        if (sameDay) {
            return time(millis);
        }
        String day = android.text.format.DateFormat.format("MMM d", then).toString();
        return day + ", " + time(millis);
    }

    // ---- building the screen ----

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ui.background);
        LinearLayout page = ui.column();
        page.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(32));
        scroll.addView(page);

        welcome = buildWelcome();
        page.addView(welcome);

        content = ui.column();
        page.addView(content);
        buildHeader(content);
        buildPendingCard(content);
        buildStatusCard(content);
        buildClimateCard(content);
        buildSchedulesCard(content);
        buildAutoLockCard(content);
        buildActivityCard(content);
        buildAccountCard(content);

        TextView footer = ui.text(12, Ui.alpha(ui.textSecondary, 0.8f));
        footer.setText("Unofficial app, not made or supported by Hyundai. It uses the same "
                + "unofficial Bluelink interface as open-source projects, which Hyundai can change "
                + "at any time. Your login stays on this phone, encrypted with the Android keystore, "
                + "and is sent only to Hyundai.");
        footer.setPadding(ui.dp(8), ui.dp(20), ui.dp(8), 0);
        page.addView(footer);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            padForSystemBars(scroll);
        }
        return scroll;
    }

    private LinearLayout buildWelcome() {
        LinearLayout box = ui.column();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(ui.dp(8), ui.dp(48), ui.dp(8), 0);

        LinearLayout badge = ui.column();
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(ui.circle(ui.tonal));
        badge.addView(ui.icon(R.drawable.ic_lock_closed, ui.onTonal, 40));
        box.addView(badge, new LinearLayout.LayoutParams(ui.dp(88), ui.dp(88)));

        TextView title = ui.medium(30, ui.text);
        title.setText("Hyundai Lock");
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, ui.dp(20), 0, ui.dp(6));
        box.addView(title);
        TextView intro = ui.text(15, ui.textSecondary);
        intro.setText("Sign in with the email, password and 4-digit PIN you use in the "
                + "MyHyundai with Bluelink app.");
        intro.setGravity(Gravity.CENTER);
        box.addView(intro);

        emailField = ui.field("Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        passwordField = ui.field("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pinField = ui.field("Bluelink PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        box.addView(emailField, ui.fullWidth(28));
        box.addView(passwordField, ui.fullWidth(10));
        box.addView(pinField, ui.fullWidth(10));
        signInButton = ui.filled("Sign in", v -> signIn());
        box.addView(signInButton, ui.fullWidth(20));
        welcomeStatus = ui.text(14, ui.bad);
        welcomeStatus.setGravity(Gravity.CENTER);
        welcomeStatus.setPadding(0, ui.dp(12), 0, 0);
        box.addView(welcomeStatus);

        TextView privacy = ui.text(13, Ui.alpha(ui.textSecondary, 0.85f));
        privacy.setText("Stored only on this phone, encrypted with the Android keystore. Sent only "
                + "to Hyundai's Bluelink server.");
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(0, ui.dp(16), 0, 0);
        box.addView(privacy);
        return box;
    }

    private void buildHeader(LinearLayout parent) {
        LinearLayout header = ui.row();
        header.setPadding(ui.dp(8), ui.dp(16), 0, ui.dp(4));
        LinearLayout titles = ui.column();
        carName = ui.medium(30, ui.text);
        carName.setSingleLine(true);
        titles.addView(carName);
        reportedText = ui.text(14, ui.textSecondary);
        titles.addView(reportedText);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        refreshButton = new ImageButton(this);
        refreshButton.setImageResource(R.drawable.ic_refresh);
        refreshButton.setImageTintList(android.content.res.ColorStateList.valueOf(ui.text));
        refreshButton.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Ui.alpha(ui.text, 0.15f)),
                ui.circle(ui.card), ui.circle(Color.WHITE)));
        refreshButton.setContentDescription("Refresh status");
        refreshButton.setOnClickListener(v -> car.run(CarControl.Command.REFRESH, CarControl.Source.APP));
        header.addView(refreshButton, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        parent.addView(header);
    }

    private void buildPendingCard(LinearLayout parent) {
        pendingCard = ui.card(parent);
        pendingCard.setBackground(ui.rounded(ui.tonal, 28));
        TextView label = ui.medium(14, ui.onTonal);
        label.setText("Auto-lock in");
        pendingCard.addView(label);
        pendingClock = new Chronometer(this);
        pendingClock.setCountDown(true);
        pendingClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        pendingClock.setTypeface(Ui.MEDIUM);
        pendingClock.setTextColor(ui.onTonal);
        pendingCard.addView(pendingClock);
        pendingAlert = ui.text(14, Ui.alpha(ui.onTonal, 0.85f));
        pendingCard.addView(pendingAlert);
        LinearLayout buttons = ui.split(
                ui.filled("Don't lock", v -> car.cancelPendingLock("you tapped Don't lock")),
                ui.textButton("Lock now", v -> car.lockNow(true)));
        pendingCard.addView(buttons, ui.fullWidth(14));
    }

    private void buildStatusCard(LinearLayout parent) {
        LinearLayout card = ui.card(parent);
        card.setPadding(ui.dp(20), ui.dp(22), ui.dp(20), ui.dp(14));

        LinearLayout top = ui.row();
        LinearLayout circle = ui.column();
        circle.setGravity(Gravity.CENTER);
        lockCircle = circle;
        lockIcon = ui.icon(R.drawable.ic_lock_unknown, ui.textSecondary, 34);
        circle.addView(lockIcon);
        top.addView(circle, new LinearLayout.LayoutParams(ui.dp(68), ui.dp(68)));
        LinearLayout words = ui.column();
        words.setPadding(ui.dp(16), 0, 0, 0);
        lockState = ui.medium(30, ui.text);
        words.addView(lockState);
        summaryText = ui.text(14, ui.textSecondary);
        words.addView(summaryText);
        top.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);

        busyRow = ui.row();
        busyRow.setPadding(0, ui.dp(14), 0, 0);
        ProgressBar spinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ui.accent));
        busyRow.addView(spinner, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        busyText = ui.text(14, ui.textSecondary);
        busyText.setPadding(ui.dp(10), 0, 0, 0);
        busyRow.addView(busyText);
        card.addView(busyRow);

        messageText = ui.text(14, ui.good);
        messageText.setPadding(0, ui.dp(14), 0, 0);
        card.addView(messageText);

        lockButton = ui.filled("Lock", v -> car.run(CarControl.Command.LOCK, CarControl.Source.APP));
        unlockButton = ui.tonal("Unlock", v -> confirmUnlock());
        card.addView(ui.split(lockButton, unlockButton), ui.fullWidth(18));
        wakeButton = ui.textButton("Wake car for a live reading", v -> confirmWake());
        LinearLayout.LayoutParams wakeParams = Ui.wrap();
        wakeParams.topMargin = ui.dp(6);
        wakeParams.leftMargin = -ui.dp(10);
        card.addView(wakeButton, wakeParams);
    }

    private void buildClimateCard(LinearLayout parent) {
        LinearLayout card = ui.card(parent);
        card.addView(ui.cardTitle("Climate"));
        TextView hint = ui.text(14, ui.textSecondary);
        hint.setText("Remote start with the cabin set to:");
        card.addView(hint);

        tempDown = ui.round("−", v -> changeClimate(-1, 0));
        tempUp = ui.round("+", v -> changeClimate(1, 0));
        tempValue = ui.medium(28, ui.text);
        minutesDown = ui.round("−", v -> changeClimate(0, -1));
        minutesUp = ui.round("+", v -> changeClimate(0, 1));
        minutesValue = ui.medium(28, ui.text);
        card.addView(ui.split(
                stepper("Temperature (°F)", tempDown, tempValue, tempUp),
                stepper("Run time (minutes)", minutesDown, minutesValue, minutesUp)), ui.fullWidth(14));

        defrostChip = ui.chip("Front defrost", false, v -> toggleClimate(true));
        heatChip = ui.chip("Heated wheel & rear", false, v -> toggleClimate(false));
        card.addView(ui.split(defrostChip, heatChip), ui.fullWidth(16));

        climateStartButton = ui.filled("Start", v -> car.run(CarControl.Command.CLIMATE_START, CarControl.Source.APP));
        climateStopButton = ui.tonal("Stop", v -> car.run(CarControl.Command.CLIMATE_STOP, CarControl.Source.APP));
        card.addView(ui.split(climateStartButton, climateStopButton), ui.fullWidth(16));
    }

    private LinearLayout stepper(String caption, Button minus, TextView value, Button plus) {
        LinearLayout column = ui.column();
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(ui.dp(8), ui.dp(12), ui.dp(8), ui.dp(12));
        column.setBackground(ui.rounded(ui.field, 20));
        TextView label = ui.text(12, ui.textSecondary);
        label.setText(caption);
        label.setGravity(Gravity.CENTER);
        column.addView(label);
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, ui.dp(8), 0, 0);
        row.addView(minus, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(ui.dp(58));
        row.addView(value, Ui.wrap());
        row.addView(plus, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
        column.addView(row);
        return column;
    }

    // ---- schedules ----

    private void buildSchedulesCard(LinearLayout parent) {
        LinearLayout card = ui.card(parent);
        card.addView(ui.cardTitle("Schedules"));
        schedulesSubtitle = ui.text(14, ui.textSecondary);
        card.addView(schedulesSubtitle);

        weatherText = ui.text(14, ui.text);
        weatherButton = ui.textButton("Set", v -> askForWeatherPlace());
        LinearLayout weatherRow = statusRow(weatherText, weatherButton);
        weatherRow.setPadding(0, ui.dp(8), 0, 0);
        card.addView(weatherRow);

        TextView exactText = ui.text(14, ui.warn);
        exactText.setText("!  Exact alarms are off, so starts can run a few minutes late");
        exactRow = statusRow(exactText, ui.textButton("Allow", v -> openExactAlarmSettings()));
        card.addView(exactRow);

        scheduleRows = ui.column();
        card.addView(scheduleRows, ui.fullWidth(6));

        card.addView(ui.split(
                ui.tonal("+ Cold morning", v -> editSchedule(Schedule.coldMorning(-1), true)),
                ui.tonal("+ Hot afternoon", v -> editSchedule(Schedule.hotAfternoon(-1), true))),
                ui.fullWidth(12));
        Button custom = ui.textButton("+ Custom schedule", v -> editSchedule(new Schedule(-1, true, 7, 0,
                Schedule.WEEKDAYS, Schedule.Condition.ALWAYS, 50, settings.climate()), true));
        LinearLayout.LayoutParams customParams = Ui.wrap();
        customParams.topMargin = ui.dp(4);
        customParams.leftMargin = -ui.dp(10);
        card.addView(custom, customParams);
        TextView hint = ui.text(13, ui.textSecondary);
        hint.setText("Hyundai only remote-starts a locked car that's turned off, for up to 10 "
                + "minutes. The temperature comes from Open-Meteo for the location above.");
        hint.setPadding(0, ui.dp(4), 0, 0);
        card.addView(hint);
    }

    private void renderSchedules(boolean ready) {
        List<Schedule> schedules = settings.schedules();
        long now = System.currentTimeMillis();
        long next = Scheduler.nextRun(schedules, now);
        if (schedules.isEmpty()) {
            schedulesSubtitle.setText("Start the car at set times, depending on the weather");
        } else if (next < 0) {
            schedulesSubtitle.setText("All schedules are off");
        } else {
            schedulesSubtitle.setText("Next: " + upcoming(next));
        }

        Weather.Place place = settings.weatherPlace();
        Double temp = settings.weatherTemp();
        if (place == null) {
            weatherText.setText("Set a location for temperature checks");
            weatherText.setTextColor(schedulesNeedWeather(schedules) ? ui.warn : ui.textSecondary);
            weatherButton.setText("Set");
        } else {
            weatherText.setTextColor(ui.text);
            weatherText.setText((temp != null ? Math.round(temp) + "°F in " : "") + place.label()
                    + (temp != null && settings.weatherAt() > 0
                            ? " · " + TimeText.ago(now, settings.weatherAt()) : ""));
            weatherButton.setText("Change");
        }
        exactRow.setVisibility(!schedules.isEmpty() && !Scheduler.exactAllowed(this)
                ? View.VISIBLE : View.GONE);

        scheduleRows.removeAllViews();
        for (Schedule schedule : schedules) {
            scheduleRows.addView(scheduleRow(schedule, ready), ui.fullWidth(8));
        }
    }

    private static boolean schedulesNeedWeather(List<Schedule> schedules) {
        for (Schedule schedule : schedules) {
            if (schedule.enabled && schedule.needsWeather()) {
                return true;
            }
        }
        return false;
    }

    private View scheduleRow(Schedule schedule, boolean ready) {
        LinearLayout row = ui.row();
        row.setPadding(ui.dp(16), ui.dp(12), ui.dp(12), ui.dp(12));
        row.setBackground(ui.ripple(ui.rounded(ui.field, 20), ui.text, 20));
        row.setOnClickListener(v -> editSchedule(schedule, false));
        LinearLayout words = ui.column();
        TextView when = ui.medium(17, schedule.enabled ? ui.text : ui.textSecondary);
        when.setText(clock(schedule.hour, schedule.minute) + "  ·  " + schedule.daysLabel());
        words.addView(when);
        TextView what = ui.text(13, ui.textSecondary);
        what.setText(schedule.conditionLabel() + "  →  " + schedule.actionLabel());
        words.addView(what);
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch toggle = ui.toggle();
        toggle.setChecked(schedule.enabled);
        toggle.setContentDescription("Schedule on");
        toggle.setOnCheckedChangeListener((b, checked) -> {
            if (rendering) {
                return;
            }
            settings.putSchedule(schedule.withEnabled(checked));
            Scheduler.rescheduleAll(this);
            settings.addLog("Schedule " + clock(schedule.hour, schedule.minute) + " turned "
                    + (checked ? "on" : "off"));
            render();
        });
        Ui.enable(toggle, ready);
        LinearLayout.LayoutParams toggleParams = Ui.wrap();
        toggleParams.leftMargin = ui.dp(8);
        row.addView(toggle, toggleParams);
        return row;
    }

    /** Mutable copy of a schedule while it's being edited. */
    private static final class Draft {
        long id;
        boolean enabled;
        int hour;
        int minute;
        int days;
        Schedule.Condition condition;
        int threshold;
        int temp;
        int minutes;
        boolean defrost;
        boolean heat;

        Draft(Schedule s) {
            id = s.id;
            enabled = s.enabled;
            hour = s.hour;
            minute = s.minute;
            days = s.days;
            condition = s.condition;
            threshold = s.thresholdF;
            temp = s.climate.tempF;
            minutes = s.climate.minutes;
            defrost = s.climate.defrost;
            heat = s.climate.heat;
        }

        Schedule build(long newId) {
            return new Schedule(id >= 0 ? id : newId, enabled, hour, minute, days, condition, threshold,
                    new ClimateOptions(temp, minutes, defrost, heat));
        }
    }

    private void editSchedule(Schedule original, boolean isNew) {
        Draft draft = new Draft(original);
        if (isNew) {
            draft.enabled = true;
        }
        LinearLayout box = ui.column();
        box.setPadding(ui.dp(22), ui.dp(8), ui.dp(22), ui.dp(4));

        Button time = ui.tonal("", null);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        time.setMinHeight(ui.dp(64));
        box.addView(time, ui.fullWidth(4));

        box.addView(caption("Days"));
        LinearLayout dayRow = ui.row();
        Button[] dayChips = new Button[7];
        for (int i = 0; i < 7; i++) {
            dayChips[i] = ui.chip(Schedule.DAY_LETTER[i], false, null);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ui.dp(38), 1f);
            if (i > 0) {
                params.leftMargin = ui.dp(5);
            }
            dayRow.addView(dayChips[i], params);
        }
        box.addView(dayRow, ui.fullWidth(6));

        box.addView(caption("Start when it's"));
        Button anyChip = ui.chip("Any temp", false, null);
        Button belowChip = ui.chip("Colder than", false, null);
        Button aboveChip = ui.chip("Warmer than", false, null);
        LinearLayout conditionRow = ui.row();
        conditionRow.addView(anyChip, Ui.weighted(0));
        LinearLayout.LayoutParams gap = Ui.weighted(0);
        gap.leftMargin = ui.dp(6);
        conditionRow.addView(belowChip, gap);
        LinearLayout.LayoutParams gap2 = Ui.weighted(0);
        gap2.leftMargin = ui.dp(6);
        conditionRow.addView(aboveChip, gap2);
        box.addView(conditionRow, ui.fullWidth(6));

        TextView thresholdValue = ui.medium(28, ui.text);
        Button thresholdDown = ui.round("−", null);
        Button thresholdUp = ui.round("+", null);
        LinearLayout thresholdBox = stepper("Outside temperature (°F)", thresholdDown, thresholdValue, thresholdUp);
        box.addView(thresholdBox, ui.fullWidth(10));

        box.addView(caption("Then start climate at"));
        TextView tempValueView = ui.medium(28, ui.text);
        Button tDown = ui.round("−", null);
        Button tUp = ui.round("+", null);
        TextView minutesView = ui.medium(28, ui.text);
        Button mDown = ui.round("−", null);
        Button mUp = ui.round("+", null);
        box.addView(ui.split(stepper("Cabin (°F)", tDown, tempValueView, tUp),
                stepper("Minutes", mDown, minutesView, mUp)), ui.fullWidth(6));
        Button defrost = ui.chip("Front defrost", false, null);
        Button heat = ui.chip("Heated wheel & rear", false, null);
        box.addView(ui.split(defrost, heat), ui.fullWidth(10));

        Runnable refresh = () -> {
            time.setText(clock(draft.hour, draft.minute));
            for (int i = 0; i < 7; i++) {
                ui.styleChip(dayChips[i], (draft.days & (1 << i)) != 0);
            }
            ui.styleChip(anyChip, draft.condition == Schedule.Condition.ALWAYS);
            ui.styleChip(belowChip, draft.condition == Schedule.Condition.BELOW);
            ui.styleChip(aboveChip, draft.condition == Schedule.Condition.ABOVE);
            thresholdBox.setVisibility(draft.condition == Schedule.Condition.ALWAYS ? View.GONE : View.VISIBLE);
            thresholdValue.setText(draft.threshold + "°");
            tempValueView.setText(draft.temp + "°");
            minutesView.setText(String.valueOf(draft.minutes));
            ui.styleChip(defrost, draft.defrost);
            ui.styleChip(heat, draft.heat);
        };
        time.setOnClickListener(v -> new TimePickerDialog(this, (picker, h, m) -> {
            draft.hour = h;
            draft.minute = m;
            refresh.run();
        }, draft.hour, draft.minute, android.text.format.DateFormat.is24HourFormat(this)).show());
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            dayChips[i].setOnClickListener(v -> {
                int next = draft.days ^ bit;
                if (next != 0) { // Keep at least one day.
                    draft.days = next;
                }
                refresh.run();
            });
        }
        anyChip.setOnClickListener(v -> {
            draft.condition = Schedule.Condition.ALWAYS;
            refresh.run();
        });
        belowChip.setOnClickListener(v -> {
            draft.condition = Schedule.Condition.BELOW;
            refresh.run();
        });
        aboveChip.setOnClickListener(v -> {
            draft.condition = Schedule.Condition.ABOVE;
            refresh.run();
        });
        thresholdDown.setOnClickListener(v -> {
            draft.threshold = Math.max(Schedule.MIN_THRESHOLD_F, draft.threshold - 1);
            refresh.run();
        });
        thresholdUp.setOnClickListener(v -> {
            draft.threshold = Math.min(Schedule.MAX_THRESHOLD_F, draft.threshold + 1);
            refresh.run();
        });
        tDown.setOnClickListener(v -> {
            draft.temp = Math.max(ClimateOptions.MIN_TEMP_F, draft.temp - 1);
            refresh.run();
        });
        tUp.setOnClickListener(v -> {
            draft.temp = Math.min(ClimateOptions.MAX_TEMP_F, draft.temp + 1);
            refresh.run();
        });
        mDown.setOnClickListener(v -> {
            draft.minutes = Math.max(ClimateOptions.MIN_MINUTES, draft.minutes - 1);
            refresh.run();
        });
        mUp.setOnClickListener(v -> {
            draft.minutes = Math.min(ClimateOptions.MAX_MINUTES, draft.minutes + 1);
            refresh.run();
        });
        defrost.setOnClickListener(v -> {
            draft.defrost = !draft.defrost;
            refresh.run();
        });
        heat.setOnClickListener(v -> {
            draft.heat = !draft.heat;
            refresh.run();
        });
        refresh.run();

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(isNew ? "New schedule" : "Edit schedule")
                .setView(scroll)
                .setPositiveButton("Save", (d, w) -> saveSchedule(draft.build(settings.newScheduleId())))
                .setNegativeButton("Cancel", null);
        if (!isNew) {
            builder.setNeutralButton("Delete", (d, w) -> {
                Scheduler.cancel(this, original.id);
                settings.removeSchedule(original.id);
                settings.addLog("Schedule " + clock(original.hour, original.minute) + " deleted");
                render();
            });
        }
        builder.show();
    }

    private void saveSchedule(Schedule schedule) {
        settings.putSchedule(schedule);
        Scheduler.rescheduleAll(this);
        settings.addLog("Schedule saved: " + clock(schedule.hour, schedule.minute) + " "
                + schedule.daysLabel() + ", " + schedule.conditionLabel().toLowerCase(java.util.Locale.ROOT)
                + " → " + schedule.actionLabel());
        render();
        if (schedule.needsWeather() && settings.weatherPlace() == null) {
            askForWeatherPlace();
        }
    }

    private void askForWeatherPlace() {
        EditText query = ui.field("ZIP code or city", InputType.TYPE_CLASS_TEXT);
        LinearLayout box = ui.column();
        box.setPadding(ui.dp(22), ui.dp(8), ui.dp(22), 0);
        TextView hint = ui.text(14, ui.textSecondary);
        hint.setText("Where the car usually is in the morning and afternoon. Used only to look up "
                + "the outside temperature.");
        box.addView(hint);
        box.addView(query, ui.fullWidth(12));
        new AlertDialog.Builder(this)
                .setTitle("Weather location")
                .setView(box)
                .setPositiveButton("Search", (d, w) -> {
                    String text = query.getText().toString().trim();
                    if (text.length() < 2) {
                        return;
                    }
                    car.searchPlaces(text, this::choosePlace,
                            error -> showInfo("Couldn't search", error));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void choosePlace(List<Weather.Place> places) {
        if (places.isEmpty()) {
            showInfo("No match", "Try a 5-digit ZIP code, or a city name like \"Bentonville\".");
            return;
        }
        String[] labels = new String[places.size()];
        for (int i = 0; i < places.size(); i++) {
            labels[i] = places.get(i).label();
        }
        new AlertDialog.Builder(this)
                .setTitle("Which place?")
                .setItems(labels, (d, which) -> car.setWeatherPlace(places.get(which)))
                .show();
    }

    @TargetApi(Build.VERSION_CODES.S)
    private void openExactAlarmSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
        }
    }

    private String clock(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        return android.text.format.DateFormat.getTimeFormat(this).format(c.getTime());
    }

    /** "today 4:45 PM", "tomorrow 7:30 AM", "Mon 7:30 AM". */
    private String upcoming(long millis) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        Calendar day = Calendar.getInstance();
        String prefix;
        if (sameDay(then, day)) {
            prefix = "today";
        } else {
            day.add(Calendar.DAY_OF_YEAR, 1);
            prefix = sameDay(then, day) ? "tomorrow"
                    : android.text.format.DateFormat.format("EEE", then).toString();
        }
        return prefix + " " + time(millis);
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private void buildAutoLockCard(LinearLayout parent) {
        LinearLayout card = ui.card(parent);
        LinearLayout header = ui.row();
        LinearLayout titles = ui.column();
        titles.addView(ui.cardTitle("Auto-lock"));
        autoSubtitle = ui.text(14, ui.textSecondary);
        titles.addView(autoSubtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        autoSwitch = ui.toggle();
        autoSwitch.setContentDescription("Auto-lock");
        autoSwitch.setOnCheckedChangeListener((b, checked) -> setAutoLock(checked));
        LinearLayout.LayoutParams switchParams = Ui.wrap();
        switchParams.leftMargin = ui.dp(12);
        header.addView(autoSwitch, switchParams);
        card.addView(header);

        autoDetails = ui.column();
        card.addView(autoDetails, ui.fullWidth(10));

        accessText = ui.text(14, ui.good);
        accessButton = ui.textButton("Allow", v -> showAccessHelp());
        autoDetails.addView(statusRow(accessText, accessButton));
        batteryText = ui.text(14, ui.good);
        batteryButton = ui.textButton("Fix", v -> openBatterySettings());
        autoDetails.addView(statusRow(batteryText, batteryButton));

        TextView delayCaption = ui.text(13, ui.textSecondary);
        delayCaption.setText("Wait before locking");
        delayCaption.setPadding(0, ui.dp(12), 0, ui.dp(8));
        autoDetails.addView(delayCaption);
        LinearLayout chips = ui.row();
        for (int i = 0; i < delayChips.length; i++) {
            final long choice = Delays.CHOICES_MS[i];
            delayChips[i] = ui.chip(DELAY_LABELS[i], false, v -> {
                settings.setDelayMs(choice);
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ui.dp(36), 1f);
            if (i > 0) {
                params.leftMargin = ui.dp(6);
            }
            chips.addView(delayChips[i], params);
        }
        autoDetails.addView(chips);

        LinearLayout actions = ui.row();
        actions.setPadding(0, ui.dp(10), 0, 0);
        pauseButton = ui.textButton("Pause for 1 hour", v -> togglePause());
        rulesToggle = ui.textButton("Alert matching  ▾", v -> {
            rulesBox.setVisibility(rulesBox.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            render();
        });
        LinearLayout.LayoutParams first = Ui.wrap();
        first.leftMargin = -ui.dp(10);
        actions.addView(pauseButton, first);
        actions.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        actions.addView(rulesToggle, Ui.wrap());
        autoDetails.addView(actions);

        rulesBox = ui.column();
        rulesBox.setVisibility(View.GONE);
        TextView rulesHint = ui.text(13, ui.textSecondary);
        rulesHint.setText("An alert counts as \"unlocked\" when it contains any trigger phrase and "
                + "none of the ignore phrases. Not case-sensitive; separate phrases with commas. "
                + "Activity shows MyHyundai's exact wording.");
        rulesHint.setPadding(0, ui.dp(4), 0, 0);
        rulesBox.addView(rulesHint);
        triggersField = ui.field("Trigger phrases", InputType.TYPE_CLASS_TEXT);
        triggersField.setText(settings.triggers());
        rulesBox.addView(caption("Trigger phrases"));
        rulesBox.addView(triggersField, ui.fullWidth(4));
        ignoresField = ui.field("Ignore phrases", InputType.TYPE_CLASS_TEXT);
        ignoresField.setText(settings.ignores());
        rulesBox.addView(caption("Ignore phrases"));
        rulesBox.addView(ignoresField, ui.fullWidth(4));
        packagesField = ui.field("Apps to watch", InputType.TYPE_CLASS_TEXT);
        packagesField.setText(settings.packages());
        rulesBox.addView(caption("Apps to watch (package names)"));
        rulesBox.addView(packagesField, ui.fullWidth(4));
        rulesBox.addView(ui.split(ui.tonal("Save", v -> saveRules()),
                ui.textButton("Reset to defaults", v -> resetRules())), ui.fullWidth(14));
        autoDetails.addView(rulesBox);
    }

    private LinearLayout statusRow(TextView label, Button action) {
        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(36));
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(action, Ui.wrap());
        return row;
    }

    private TextView caption(String text) {
        TextView view = ui.text(13, ui.textSecondary);
        view.setText(text);
        view.setPadding(ui.dp(4), ui.dp(12), 0, 0);
        return view;
    }

    private void buildActivityCard(LinearLayout parent) {
        LinearLayout card = ui.card(parent);
        LinearLayout header = ui.row();
        TextView title = ui.cardTitle("Activity");
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams clearParams = Ui.wrap();
        clearParams.rightMargin = -ui.dp(10);
        header.addView(ui.textButton("Clear", v -> {
            settings.clearLog();
            render();
        }), clearParams);
        card.addView(header);
        activityRows = ui.column();
        card.addView(activityRows, ui.fullWidth(4));
        activityMore = ui.textButton("Show more", v -> {
            showAllActivity = !showAllActivity;
            render();
        });
        LinearLayout.LayoutParams moreParams = Ui.wrap();
        moreParams.leftMargin = -ui.dp(10);
        card.addView(activityMore, moreParams);
    }

    private void buildAccountCard(LinearLayout parent) {
        LinearLayout card = ui.card(parent);
        card.addView(ui.cardTitle("Account"));
        accountText = ui.text(14, ui.textSecondary);
        accountText.setPadding(0, ui.dp(2), 0, ui.dp(6));
        card.addView(accountText);
        LinearLayout actions = ui.row();
        Button change = ui.textButton("Change car", v -> car.listVehicles(this::chooseVehicle));
        Button signOut = ui.textButton("Sign out", v -> confirmSignOut());
        signOut.setTextColor(ui.bad);
        LinearLayout.LayoutParams first = Ui.wrap();
        first.leftMargin = -ui.dp(10);
        actions.addView(change, first);
        actions.addView(signOut, Ui.wrap());
        card.addView(actions);
    }

    /** Android 15+ draws apps edge to edge; keep content clear of the system bars. */
    @TargetApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private static void padForSystemBars(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
    }
}
