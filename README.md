# Hyundai Lock

An unofficial Android remote for **Hyundai Bluelink (US)**. It can lock, unlock, show status and start climate control, and it can **lock the car automatically when the MyHyundai app posts an "unlocked" alert**. It uses no libraries, analytics or trackers. Android 8.0+ (API 26); targets Android 16 (API 36).

**Status: first version, not yet tested against a real car or account.** It sends the same requests as the open-source [hyundai_kia_connect_api](https://github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api) project, which works today. Hyundai can change that interface at any time. Not affiliated with Hyundai.

## What it does

- **Lock / Unlock** through Hyundai's servers, then wait up to 90 seconds for the car to confirm. Unlock asks you to confirm first.
- **Status** shows whether the car is locked, whether the engine and climate are on, and whether a door, the trunk or the hood is open. **Refresh** reads Hyundai's last saved copy. **Wake car** asks the car itself, which is slower and limited by Hyundai.
- **Remote start / climate**: 62–82 °F, 1–10 minutes, front defrost and heated features. Uses the right endpoint for gas and hybrid cars or EVs.
- **"Lock car" Quick Settings tile** for one-tap locking from the notification shade. There's deliberately no unlock tile.
- **Auto-lock** (below).

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
- The only network destination is `api.telematics.hyundaiusa.com`.

## Build

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Pure logic (alert matching, auto-lock decisions, request bodies and response parsing) lives in `app/src/main/java/.../core` and is covered by unit tests in `app/src/test`.
