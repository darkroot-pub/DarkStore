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
