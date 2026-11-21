package io.github.bags156.hyundailock;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.bags156.hyundailock.core.ClimateOptions;
import io.github.bags156.hyundailock.core.Delays;
import io.github.bags156.hyundailock.core.EventLog;
import io.github.bags156.hyundailock.core.KeywordRules;
import io.github.bags156.hyundailock.core.Vehicle;
import io.github.bags156.hyundailock.core.VehicleStatus;

/** The only screen. Built in code with platform views; all car work happens in CarControl. */
// Text is built in code for a single-language personal app.
@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity implements CarControl.Listener {

    private static final int REQUEST_NOTIFICATIONS = 1;
    private static final int ACCENT = 0xFF2F6FDE;
    private static final long PAUSE_MS = 60 * 60_000L;

    private AppSettings settings;
    private CarControl car;
    private ColorStateList secondaryText;

    // Header
    private TextView subtitleText;
    private TextView messageText;

    // Account
    private LinearLayout signedOutBox;
    private LinearLayout signedInBox;
    private EditText emailField;
    private EditText passwordField;
    private EditText pinField;
    private Button signInButton;
    private TextView accountText;

    // Car
    private LinearLayout carCard;
    private TextView statusText;
    private Button lockButton;
    private Button unlockButton;
    private Button refreshButton;
    private Button wakeButton;

    // Climate
    private LinearLayout climateCard;
    private TextView tempLabel;
    private TextView minutesLabel;
    private SeekBar tempBar;
    private SeekBar minutesBar;
    private Switch defrostSwitch;
    private Switch heatSwitch;
    private Button climateStartButton;
    private Button climateStopButton;

    // Auto-lock
    private Switch autoSwitch;
    private TextView accessText;
    private Button accessButton;
    private TextView batteryText;
    private Button batteryButton;
    private TextView delayLabel;
    private SeekBar delayBar;
    private LinearLayout pendingBox;
    private TextView pendingText;
    private Button pauseButton;
    private EditText triggersField;
    private EditText ignoresField;
    private EditText packagesField;

    // Activity
    private TextView logText;

    private boolean rendering;

    // ---- lifecycle ----

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new AppSettings(this);
        car = CarControl.get(this);
        Notifications.ensureChannels(this);
        TypedArray attrs = obtainStyledAttributes(new int[] {android.R.attr.textColorSecondary});
        secondaryText = attrs.getColorStateList(0);
        attrs.recycle();
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
    }

    @Override
    protected void onStop() {
        car.removeListener(this);
        super.onStop();
    }

    @Override
    public void onCarChanged() {
        render();
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
                .setMessage("This removes your MyHyundai login from this phone and turns off "
                        + "auto-lock until you sign in again.")
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
                .setMessage("This asks the car itself for its status instead of Hyundai's last saved "
                        + "copy. It can take a minute, and Hyundai limits how often cars can be woken, "
                        + "because it uses the car's 12-volt battery.")
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
            openNotificationAccess();
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
                        + "\"Apps to watch\" and ignores everything else.\n\n"
                        + "If the switch is greyed out, Android is protecting a sideloaded app: open "
                        + "App info for Hyundai Lock, tap ⋮ (top right), choose \"Allow restricted "
                        + "settings\", then try again.")
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
        if (subtitleText == null) {
            return;
        }
        rendering = true;
        try {
            boolean ready = settings.ready();
            boolean hasAccount = settings.hasAccount();
            Vehicle vehicle = settings.vehicle();
            String busy = car.busy();
            boolean idle = busy == null;

            subtitleText.setText(vehicle != null ? vehicle.displayName()
                    : hasAccount ? "Choose a car" : "Not signed in");
            String message = busy != null ? busy : car.message();
            messageText.setText(message == null ? "" : message);
            messageText.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);

            signedOutBox.setVisibility(hasAccount ? View.GONE : View.VISIBLE);
            signedInBox.setVisibility(hasAccount ? View.VISIBLE : View.GONE);
            signInButton.setEnabled(idle);
            if (hasAccount) {
                accountText.setText("Signed in as " + settings.email()
                        + (vehicle != null ? "\nCar: " + vehicle.displayName()
                                + (vehicle.isEv() ? " (electric)" : "") : "\nNo car chosen yet"));
            }

            carCard.setVisibility(ready ? View.VISIBLE : View.GONE);
            climateCard.setVisibility(ready ? View.VISIBLE : View.GONE);
            statusText.setText(statusSummary());
            for (Button button : new Button[] {lockButton, unlockButton, refreshButton, wakeButton,
                    climateStartButton, climateStopButton}) {
                button.setEnabled(ready && idle);
            }

            ClimateOptions climate = settings.climate();
            tempBar.setProgress(climate.tempF - ClimateOptions.MIN_TEMP_F);
            minutesBar.setProgress(climate.minutes - ClimateOptions.MIN_MINUTES);
            tempLabel.setText("Temperature: " + climate.tempF + "°F");
            minutesLabel.setText("Run for: " + climate.minutes + (climate.minutes == 1 ? " minute" : " minutes"));
            defrostSwitch.setChecked(climate.defrost);
            heatSwitch.setChecked(climate.heat);

            renderAutoLock(ready);
            renderLog();
        } finally {
            rendering = false;
        }
    }

    private void renderAutoLock(boolean ready) {
        boolean enabled = settings.autoLockEnabled();
        autoSwitch.setChecked(enabled);
        autoSwitch.setEnabled(ready || enabled);

        boolean access = UnlockListenerService.hasAccess(this);
        accessText.setText(access ? "Notification access: allowed"
                : "Notification access: not allowed yet (required)");
        accessButton.setText(access ? "Change" : "Allow");

        PowerManager power = getSystemService(PowerManager.class);
        boolean unrestricted = power != null && power.isIgnoringBatteryOptimizations(getPackageName());
        batteryText.setText(unrestricted ? "Battery: unrestricted"
                : "Battery: optimized. OxygenOS and other Android versions may stop the app in the "
                        + "background, so set it to Unrestricted.");
        batteryButton.setVisibility(unrestricted ? View.GONE : View.VISIBLE);

        long delay = settings.delayMs();
        delayBar.setProgress(Delays.indexOf(delay));
        delayLabel.setText("Wait before locking: " + Delays.label(delay));

        long dueAt = settings.pendingDueAt();
        pendingBox.setVisibility(dueAt > 0 ? View.VISIBLE : View.GONE);
        if (dueAt > 0) {
            pendingText.setText("Locking at " + time(dueAt) + " after: " + nullToEmpty(settings.pendingAlert()));
        }

        long pausedUntil = settings.pausedUntil();
        boolean paused = pausedUntil > System.currentTimeMillis();
        pauseButton.setText(paused ? "Resume (paused until " + time(pausedUntil) + ")" : "Pause for 1 hour");
        pauseButton.setEnabled(enabled);
    }

    private void renderLog() {
        List<EventLog.Entry> entries = EventLog.newest(settings.log(), 15);
        if (entries.isEmpty()) {
            logText.setText("Nothing yet. Alerts from watched apps, automatic locks and Quick "
                    + "Settings commands show up here, so you can see exactly what MyHyundai's "
                    + "notifications say.");
            return;
        }
        StringBuilder text = new StringBuilder();
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (EventLog.Entry entry : entries) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(format.format(new Date(entry.time))).append("\n").append(entry.text);
        }
        logText.setText(text);
    }

    private String statusSummary() {
        VehicleStatus status = settings.status();
        if (status == null) {
            return "No status yet. Tap Refresh.";
        }
        StringBuilder text = new StringBuilder();
        text.append("Doors: ").append(status.lockLabel()).append('\n');
        text.append("Engine ").append(VehicleStatus.onOff(status.engineOn))
                .append(" · Climate ").append(VehicleStatus.onOff(status.climateOn)).append('\n');
        text.append(status.openLabel()).append('\n');
        Long reported = status.reportedAtMillis();
        if (reported != null) {
            text.append("Car last reported ").append(dateTime(reported));
        } else if (status.reportedAt != null) {
            text.append("Car last reported ").append(status.reportedAt);
        }
        long fetched = settings.statusFetchedAt();
        if (fetched > 0) {
            text.append("\nChecked ").append(dateTime(fetched));
        }
        return text.toString();
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private String time(long millis) {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(millis));
    }

    private String dateTime(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(millis));
    }

    // ---- building the screen ----

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(20), dp(16), dp(28));
        scroll.addView(column);

        TextView title = text(26, true);
        title.setText("Hyundai Lock");
        column.addView(title);
        subtitleText = secondary(text(15, false));
        column.addView(subtitleText);
        messageText = text(15, false);
        messageText.setTextColor(ACCENT);
        messageText.setPadding(0, dp(8), 0, 0);
        column.addView(messageText);

        buildAccountCard(column);
        buildCarCard(column);
        buildClimateCard(column);
        buildAutoLockCard(column);
        buildLogCard(column);

        TextView footer = secondary(text(12, false));
        footer.setText("Unofficial app, not made or supported by Hyundai. It uses the same "
                + "unofficial Bluelink interface as open-source projects, which Hyundai can change "
                + "at any time. Requires an active Bluelink Remote subscription (US). Your login is "
                + "stored only on this phone, encrypted with the Android keystore, and sent only to "
                + "Hyundai.");
        footer.setPadding(dp(4), dp(18), dp(4), 0);
        column.addView(footer);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            padForSystemBars(scroll);
        }
        return scroll;
    }

    private void buildAccountCard(LinearLayout column) {
        LinearLayout card = card(column, "MyHyundai account");

        signedOutBox = new LinearLayout(this);
        signedOutBox.setOrientation(LinearLayout.VERTICAL);
        emailField = field("Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        passwordField = field("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pinField = field("Bluelink PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        signedOutBox.addView(emailField);
        signedOutBox.addView(passwordField);
        signedOutBox.addView(pinField);
        signInButton = button("Sign in", v -> signIn());
        signedOutBox.addView(signInButton);
        TextView note = secondary(text(13, false));
        note.setText("Use the same login and 4-digit PIN as the MyHyundai with Bluelink app.");
        signedOutBox.addView(note);
        card.addView(signedOutBox);

        signedInBox = new LinearLayout(this);
        signedInBox.setOrientation(LinearLayout.VERTICAL);
        accountText = text(15, false);
        signedInBox.addView(accountText);
        signedInBox.addView(row(
                button("Change car", v -> car.listVehicles(this::chooseVehicle)),
                button("Sign out", v -> confirmSignOut())));
        card.addView(signedInBox);
    }

    private void buildCarCard(LinearLayout column) {
        carCard = card(column, "Car");
        statusText = text(15, false);
        statusText.setPadding(0, 0, 0, dp(6));
        carCard.addView(statusText);
        lockButton = button("Lock", v -> car.run(CarControl.Command.LOCK, CarControl.Source.APP));
        unlockButton = button("Unlock", v -> confirmUnlock());
        carCard.addView(row(lockButton, unlockButton));
        refreshButton = button("Refresh", v -> car.run(CarControl.Command.REFRESH, CarControl.Source.APP));
        wakeButton = button("Wake car", v -> confirmWake());
        carCard.addView(row(refreshButton, wakeButton));
        TextView hint = secondary(text(13, false));
        hint.setText("Refresh shows Hyundai's last saved status. Commands go through Hyundai's "
                + "servers over the car's cellular connection and usually take 10–30 seconds. "
                + "Add the \"Lock car\" Quick Settings tile for one-tap locking.");
        carCard.addView(hint);
    }

    private void buildClimateCard(LinearLayout column) {
        climateCard = card(column, "Remote start & climate");
        tempLabel = text(15, false);
        climateCard.addView(tempLabel);
        tempBar = new SeekBar(this);
        tempBar.setMax(ClimateOptions.MAX_TEMP_F - ClimateOptions.MIN_TEMP_F);
        tempBar.setOnSeekBarChangeListener(onChange(this::saveClimate));
        climateCard.addView(tempBar);
        minutesLabel = text(15, false);
        climateCard.addView(minutesLabel);
        minutesBar = new SeekBar(this);
        minutesBar.setMax(ClimateOptions.MAX_MINUTES - ClimateOptions.MIN_MINUTES);
        minutesBar.setOnSeekBarChangeListener(onChange(this::saveClimate));
        climateCard.addView(minutesBar);
        defrostSwitch = new Switch(this);
        defrostSwitch.setText("Front defrost");
        defrostSwitch.setOnCheckedChangeListener((b, checked) -> saveClimate());
        climateCard.addView(defrostSwitch);
        heatSwitch = new Switch(this);
        heatSwitch.setText("Heated features (steering wheel, rear defrost)");
        heatSwitch.setOnCheckedChangeListener((b, checked) -> saveClimate());
        climateCard.addView(heatSwitch);
        climateStartButton = button("Start", v -> car.run(CarControl.Command.CLIMATE_START, CarControl.Source.APP));
        climateStopButton = button("Stop", v -> car.run(CarControl.Command.CLIMATE_STOP, CarControl.Source.APP));
        climateCard.addView(row(climateStartButton, climateStopButton));
    }

    private void saveClimate() {
        if (rendering) {
            return;
        }
        settings.setClimate(new ClimateOptions(
                ClimateOptions.MIN_TEMP_F + tempBar.getProgress(),
                ClimateOptions.MIN_MINUTES + minutesBar.getProgress(),
                defrostSwitch.isChecked(),
                heatSwitch.isChecked()));
        render();
    }

    private void buildAutoLockCard(LinearLayout column) {
        LinearLayout card = card(column, "Auto-lock");
        TextView intro = secondary(text(13, false));
        intro.setText("When the MyHyundai app posts a notification saying the car is unlocked, "
                + "wait, then lock it. You get a countdown notification with a Don't lock button. "
                + "If Hyundai's last status says the engine is running, it asks before locking.");
        card.addView(intro);

        autoSwitch = new Switch(this);
        autoSwitch.setText("Lock automatically after an unlocked alert");
        autoSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        autoSwitch.setPadding(0, dp(8), 0, dp(8));
        autoSwitch.setOnCheckedChangeListener((b, checked) -> setAutoLock(checked));
        card.addView(autoSwitch);

        accessText = text(14, false);
        accessButton = button("Allow", v -> showAccessHelp());
        card.addView(labelledRow(accessText, accessButton));
        batteryText = text(14, false);
        batteryButton = button("Fix", v -> openBatterySettings());
        card.addView(labelledRow(batteryText, batteryButton));

        delayLabel = text(15, false);
        delayLabel.setPadding(0, dp(8), 0, 0);
        card.addView(delayLabel);
        delayBar = new SeekBar(this);
        delayBar.setMax(Delays.CHOICES_MS.length - 1);
        delayBar.setOnSeekBarChangeListener(onChange(() -> {
            if (!rendering) {
                settings.setDelayMs(Delays.at(delayBar.getProgress()));
                render();
            }
        }));
        card.addView(delayBar);

        pendingBox = new LinearLayout(this);
        pendingBox.setOrientation(LinearLayout.VERTICAL);
        pendingText = text(15, true);
        pendingText.setTextColor(ACCENT);
        pendingBox.addView(pendingText);
        pendingBox.addView(row(
                button("Don't lock", v -> car.cancelPendingLock("you tapped Don't lock")),
                button("Lock now", v -> car.lockNow(true))));
        card.addView(pendingBox);

        pauseButton = button("Pause for 1 hour", v -> togglePause());
        card.addView(pauseButton);

        TextView rulesTitle = text(14, true);
        rulesTitle.setText("Which alerts count as \"unlocked\"");
        rulesTitle.setPadding(0, dp(12), 0, 0);
        card.addView(rulesTitle);
        TextView rulesHint = secondary(text(13, false));
        rulesHint.setText("An alert counts when it contains any trigger phrase and none of the "
                + "ignore phrases (not case-sensitive, separate phrases with commas). Check Activity "
                + "below for the exact wording MyHyundai uses and adjust if needed.");
        card.addView(rulesHint);
        triggersField = field("Trigger phrases", InputType.TYPE_CLASS_TEXT);
        triggersField.setText(settings.triggers());
        card.addView(caption("Trigger phrases"));
        card.addView(triggersField);
        ignoresField = field("Ignore phrases", InputType.TYPE_CLASS_TEXT);
        ignoresField.setText(settings.ignores());
        card.addView(caption("Ignore phrases"));
        card.addView(ignoresField);
        packagesField = field("Apps to watch", InputType.TYPE_CLASS_TEXT);
        packagesField.setText(settings.packages());
        card.addView(caption("Apps to watch (package names)"));
        card.addView(packagesField);
        card.addView(row(button("Save", v -> saveRules()), button("Defaults", v -> resetRules())));
    }

    private void buildLogCard(LinearLayout column) {
        LinearLayout card = card(column, "Activity");
        logText = text(13, false);
        logText.setTextIsSelectable(true);
        card.addView(logText);
        card.addView(button("Clear activity", v -> {
            settings.clearLog();
            render();
        }));
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

    private LinearLayout card(LinearLayout parent, String heading) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(16));
        background.setColor(0x18808080);
        card.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        parent.addView(card, params);
        TextView title = text(13, true);
        title.setText(heading.toUpperCase(Locale.getDefault()));
        title.setTextColor(ACCENT);
        title.setLetterSpacing(0.08f);
        title.setPadding(0, 0, 0, dp(4));
        card.addView(title);
        return card;
    }

    private TextView text(int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private TextView caption(String label) {
        TextView view = secondary(text(12, false));
        view.setText(label);
        view.setPadding(0, dp(6), 0, 0);
        return view;
    }

    private TextView secondary(TextView view) {
        if (secondaryText != null) {
            view.setTextColor(secondaryText);
        }
        return view;
    }

    private EditText field(String hint, int inputType) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(inputType);
        field.setSingleLine(true);
        return field;
    }

    private Button button(String label, View.OnClickListener onClick) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(onClick);
        return button;
    }

    private LinearLayout row(View... children) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (View child : children) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            row.addView(child, params);
        }
        return row;
    }

    /** Text that takes the spare width, with a button at the end. */
    private LinearLayout labelledRow(TextView label, Button button) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static SeekBar.OnSeekBarChangeListener onChange(Runnable onUserChange) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    onUserChange.run();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
