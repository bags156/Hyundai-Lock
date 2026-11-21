# Hyundai Lock

An unofficial Android remote for **Hyundai Bluelink (US)**. It can lock, unlock, show status and start climate control. It can also **lock the car automatically when the MyHyundai app posts an "unlocked" alert**, and **start the climate on a schedule depending on the outside temperature**. It uses no libraries, analytics or trackers. Android 8.0+ (API 26); targets Android 16 (API 36).

**Status:** sign-in, status and the screen work against a real 2022 Tucson. Lock and unlock confirmations, climate, auto-lock and schedules still need a live test. It sends the same requests as the open-source [hyundai_kia_connect_api](https://github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api) project, which works today. Hyundai can change that interface at any time. Not affiliated with Hyundai.

## What it does

- **Lock / Unlock** through Hyundai's servers, then wait up to 90 seconds for the car to confirm. Unlock asks you to confirm first.
- **Status** shows whether the car is locked, whether the engine and climate are on, and whether a door, the trunk or the hood is open. **Refresh** reads Hyundai's last saved copy. **Wake car** asks the car itself, which is slower and limited by Hyundai.
- **Remote start / climate**: 62–82 °F, 1–10 minutes, front defrost and heated features. Uses the right endpoint for gas and hybrid cars or EVs.
- **"Lock car" Quick Settings tile** for one-tap locking from the notification shade. There's deliberately no unlock tile.
- **Schedules** and **Auto-lock** (below).

## Schedules

A schedule starts the car's climate at a time of day on chosen days. It can run at any temperature, or only when it's **colder than** or **warmer than** a threshold. For example:

- **Cold morning** preset: weekdays at 7:30 AM, if it's below 35°F, warm to 74°F with front defrost and heated features for 10 minutes.
- **Hot afternoon** preset: weekdays at 4:45 PM, if it's above 85°F, cool to 64°F for 10 minutes.

Both presets can be edited, and you can add custom schedules. The outside temperature comes from [Open-Meteo](https://open-meteo.com), which is free and needs no account or API key. You set a ZIP code or city once. The app sends Open-Meteo only that place's coordinates and never uses your phone's location.

Every run sends a notification. A start looks like "Warm-up at 7:30 AM: climate started, 28°F outside". A skip, on a quieter channel, looks like "52°F outside, not below 35°F". If the weather can't be fetched, a temperature-based schedule doesn't start the car.

Schedules use exact alarms, so they fire on the minute. They're re-armed after a reboot, an app update, or a clock or time-zone change. Hyundai only remote-starts a car that's off and locked with the hood closed, for at most 10 minutes.

## Auto-lock

When a watched app (by default **MyHyundai with Bluelink**, `com.stationdm.bluelink`) posts a notification whose text contains a trigger phrase (default `unlock`) and no ignore phrase (default `fail, error, unable, could not, couldn't`):

1. A countdown notification appears: **Car unlocked: locking in 2 minutes**, with **Don't lock** and **Lock now** buttons. You can set the wait from 15 seconds to 10 minutes.
2. When the countdown ends, it checks Hyundai's saved status. If the engine is reported running, it doesn't lock. Instead it notifies you with a **Lock anyway** button.
3. It sends the lock command and waits for the car to confirm. If the lock fails, it retries once after 30 seconds, then notifies you with **Try again**.

Built-in guards:

- It doesn't re-trigger within **5 minutes** of an automatic lock, so it can't loop.
- It ignores alerts for **10 minutes** after you unlock from this app, and unlocking here cancels any lock that's counting down.
- Only one lock can be scheduled at a time. **Pause for 1 hour** is available.
- If Android stops the app mid-countdown, the lock runs when the app is next started (by the notification listener reconnecting, a backup alarm, or opening the app), unless it's more than 15 minutes late. In that case it's dropped and logged.

The **Activity** card logs every notification from the watched apps, including ones that didn't match, so you can see MyHyundai's exact wording and adjust the phrases. Notifications from all other apps are ignored without being read or stored.

### Auto-lock only works if

- **MyHyundai actually sends an unlocked alert** for your car. Turn its notifications on, and check Activity after unlocking with the fob to see what arrives.
- This app has **notification access**. If Android greys out the switch, open App info → ⋮ → **Allow restricted settings**, then try again.
- **Battery is Unrestricted** (the app's Fix button). OxygenOS in particular stops background apps aggressively, and Android cuts network access for optimized apps while the phone sleeps.

## Limits

- This is **not a digital key**. Digital Key 2 lives in your phone's wallet and talks to the car over NFC/UWB, and no third-party app can do that. This app controls the car over the internet.
- Every command goes phone → Hyundai → the car's cellular modem → back, which usually takes 10–30 seconds. Poor signal at the car (garages) means slow or failed commands.
- It needs an active **Bluelink Remote** subscription and a **US** MyHyundai account.
- Hyundai limits how often a car can be woken or commanded remotely, because each command uses the 12-volt battery.

## Install

With **Obtainium**: add `https://github.com/bags156/Hyundai-Lock`. Releases are signed with the same key every time, so updates install in place.

Without it: download the APK from **Releases** and open it on the phone.

To publish a release, change `version.txt` (e.g. `0.1.1`) and push to `main`. `.github/workflows/release.yml` builds, signs and publishes it.

## Privacy

- Your MyHyundai email is stored in the app's private storage. Your password and PIN are encrypted there with an AES key held in the Android keystore. The access token is kept in memory only.
- Backups and device-to-device transfer are disabled for the app's data. Signing out deletes the login and the key.
- The only network destinations are `api.telematics.hyundaiusa.com` (Hyundai), and `api.open-meteo.com` / `geocoding-api.open-meteo.com` for schedule temperatures and the location search.

## Build

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

`.github/workflows/screenshots.yml` runs on every branch except `main`. It boots an emulator, opens the debug build with sample data (debug builds only), and pushes light and dark screenshots plus the lint report to the `ci-screenshots` branch.

Pure logic (alert matching, auto-lock decisions, schedules, request bodies and response parsing) lives in `app/src/main/java/.../core` and is covered by unit tests in `app/src/test`.
