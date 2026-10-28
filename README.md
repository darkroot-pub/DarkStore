# DarkStore Admin (Flutter)

Admin-only app, package `com.darkstore.darkroot.admin`. It never requests an FCM token (the user app stores them); it only sends pushes through your DarkStore server.

## Build on GitHub (no local setup)
1. Push this whole folder to a GitHub repo (branch `main`).
2. Repo > Settings > Secrets and variables > Actions > add `KEYSTORE_PASSWORD` (optional `KEY_ALIAS`, `KEY_PASSWORD`). It uses the same `android/app/darkstore-release.keystore` as the user app.
3. Push, or run Actions > "Build DarkStore Admin APK" > Run workflow. Download `darkstore-admin-apk` from the run.

## Build locally
`flutter pub get && flutter build apk --release` (needs the same env vars for signing, otherwise it signs with the debug key).

Sign in with an account whose `users/<uid>/role` is `admin` (or the owner email, verified).

## Rewarded ads (new)
Ads tab: global config, per-app Ads ON/OFF (+ available / unlockable / apps per ad), verified-only stats and reward history. Needs the updated notifier (`ads_rewards.py`).

## Photos from the device, Google login
- Every image field (app logo, screenshots, banner image, event image, notification image) has **Pick photo from device**; screenshots allow selecting many. Uploads go to ImgBB when the build has the `IMGBB_API_KEY` GitHub secret (same as the user app), otherwise to Firebase Storage (your Storage rules must allow signed-in uploads).
- **Continue with Google** uses the release keystore already registered for `com.darkstore.darkroot.admin`. The Google account must have `users/<uid>/role = admin` (or be the verified owner email), otherwise the app shows "not an admin".
- Submissions: tap a submission, check the screenshots (portrait and landscape keep their shape, tap for full screen) and press Approve or Reject.

## Fraud & Devices (new)
- **Fraud tab**: all `fraudAlerts` (open / dismissed / all, search), counts, and a repeat-offenders list from `fraudStrikes`. Each alert shows the account, the other account on the same phone, the auto action (flagged / suspended) and whether the user is suspended now. Menu: Devices & accounts, Suspend / Unsuspend, Dismiss / Re-open, Delete alert.
- **Devices tab**: every phone from `deviceRegistry` / `userDevices` with the accounts logged in on it (owner marked, suspended accounts struck through). Filters: All / Shared phones / With suspended. Tap a phone for the full account list and **Suspend all except owner**.
- **Users tab → Devices & fraud**: for one account, every phone it used, the other accounts on those phones, its alerts, and Reset strikes.
- Home shows **Open fraud alerts**. Every suspend / unsuspend / dismiss / reset is written to the Audit log.
- Database rules: admins need read access to `deviceRegistry`, `deviceOwner`, `userDevices`, `fraudStrikes`, `fraudAlerts` and write access to `fraudAlerts`, `fraudStrikes` and `users/<uid>/isSuspended`.
