# DarkStore Security Audit

## Fixed in code
| # | Severity | Issue | Fix |
|---|---|---|---|
| 1 | CRITICAL | Release keystore + password (`darkstore123`) committed in repo: anyone can sign an APK that installs as an "update" over your app | Passwords now come from env/GitHub Secrets (fallback remains ONLY so your build keeps working until you migrate - see TODO) |
| 2 | CRITICAL | Hard-coded FCM server key in MainActivity + stored in plain prefs | Removed; never persisted |
| 3 | CRITICAL | Command injection / path traversal: `app.id`/`app.version` (developer-controlled) went into the APK file name and into `su -c` / `sh -c` strings | File names sanitized; shell fallbacks removed (`pm install` arg-array only) |
| 4 | HIGH | Offline "sandbox" login: plaintext passwords in SharedPreferences, could create a local admin session | Removed entirely |
| 5 | HIGH | Client granted `admin` role by matching an email | Client always sends `user`; admin is enforced by rules (verified email token) |
| 6 | HIGH | Logout kept the refresh token, silently re-logging the user in | `SecureStore.clearAuth` on logout/guest |
| 7 | HIGH | ID/refresh tokens in plain SharedPreferences | AES-256-GCM in Android Keystore (`SecureStore`), auto-migrates old plain tokens |
| 8 | HIGH | APK downloads allowed http://, any `file://` path, and any package name | HTTPS only, `file://` limited to app storage, package name must match catalog entry |
| 9 | HIGH | Notifier URL read from DB could be `http://` / any host and receives the admin token | HTTPS + allow-listed host only |
| 10 | MED | RTDB URL editable to any host (token leak) | Must be https + `*.firebasedatabase.app` / `*.firebaseio.com` |
| 11 | MED | Banner/profile links opened with any scheme (`intent:`, `file:`) | `safeWebUri` http(s) only |
| 12 | MED | WebView could navigate anywhere / file access | Locked to YouTube, file/content access off |
| 13 | MED | FileProvider exposed all internal + external storage | Only `apks/` and `updates/` cache folders |
| 14 | MED | No network security config | HTTPS only, system CAs only |
| 15 | MED | Build downloaded images from i.ibb.co at build time (supply chain) | Task removed (files already in repo) |
| 16 | LOW | Release not minified, debug logs in release | R8 + shrink on, `Log.v/d/i` stripped |
| 17 | LOW | CI had default token permissions | `contents: read`, no persisted credentials |

## You must do (I cannot do these for you)
1. **Deploy `database.rules.json`** (Firebase Console > Realtime Database > Rules). It is your own rules file plus the new `submissionLocks` node and submission create/withdraw rules. Test each screen in the Rules Playground. This is the real security boundary; everything in the app can be bypassed by a raw API call.
2. **Rotate the signing key**: generate a new keystore, store it as GitHub Secrets `DS_KEYSTORE_B64`, `DS_STORE_PASSWORD`, `DS_KEY_ALIAS`, `DS_KEY_PASSWORD`, delete the keystore from the repo AND git history. Existing installs must be reinstalled (signature changes), and add the new SHA-1 to Firebase.
3. **Restrict the Firebase API key** (Google Cloud Console > Credentials) to package `com.darkstore.darkroot` + your SHA-1, and enable **Firebase App Check**.
4. Rotate the Gemini key from the old `.env` and scrub it from git history.
5. Make sure the admin account has a verified email and 2-step verification (rules trust `email_verified`).

## Known remaining risks
- **No APK hash pinning**: package-name check stops swapping apps, but a malicious *developer* can still upload a malicious APK under their own app. Next step: admin review computes/stores SHA-256 and the client verifies it (needs new field in 3 read/write spots + rules).
- `users` is readable by any logged-in user, exposing emails/FCM tokens. Split into `users` (private) and `publicProfiles`.
- `?auth=` token in query string; `QUERY_ALL_PACKAGES`, device-admin and silent `pm install` are broad privileges - drop what you don't need.
- Debug builds are signed with the release key; give debug its own key once Google Sign-In SHA-1s allow.
- R8 is now on: **test the release APK** (Moshi/Compose paths) before shipping. I could not compile here.


## Submission anti-spam (added)
- One review at a time per app: a second submission/update for a package that is Pending is refused (client check + server-side lock `submissionLocks/{package}` enforced by the rules).
- Lock is released automatically when the admin approves or rejects (and when the developer withdraws).
- Developers can withdraw their own pending review (app menu > "Withdraw pending review").
- Package ownership: nobody can submit/update a package another developer owns; a live package can't be re-submitted as "new app".
- Version code must be higher when the APK link or version name changes.
- Quotas: max 3 apps in review, 5 submissions / 24 h (client-side only - the rules cannot count; the lock is the real server-side guard).
- Package ID format and `createdAt` are validated by the rules on creation.
- Known: `submissions` is still readable by every logged-in user (needed by the current client); ImgBB upload key is hard-coded in the client.

## Round 3 additions
- Admin catalog: 5 distinct card styles - NEW APP REQUEST (blue), UPDATE REQUEST (amber), APPROVED APP (green), APPROVED UPDATE (cyan), REJECTED (red).
- Admin can message ANY user ("All users" tab); blue verified tick next to admin names in inbox, chat header, global chat and user list.
- Settings > "Report a problem about DarkStore" (between account card and Premium). Reports go to `darkstoreReports` (create: logged-in reporter only, 10-1000 chars; read/delete: admin only) and show in Admin > Feedback tab. 1 report / minute client-side.
- Startup: splash 2200 ms -> 900 ms, widget refresh moved off the main thread, submissions list no longer downloaded for normal users.
- Deploy the updated `database.rules.json` again.

## Round 5: App Links (open shared links directly)
- Android only verifies `assetlinks.json` when its **SHA-256** equals the signing certificate of the INSTALLED build. The 44:CB:63:... value you sent is a SHA-1 and is registered in Firebase, but the committed keystore is F7:8D:C3:... - builds signed by another key will not verify.
- Get the right SHA-256: Admin > Feedback > "Share link check" (copy button), or the GitHub Actions run summary ("Show APK signing fingerprints"). Add it to `website/.well-known/assetlinks.json` (several fingerprints are allowed), redeploy, reinstall the app.
- Requirements: HTTPS, exact path `/.well-known/assetlinks.json`, `Content-Type: application/json`, no redirects.

## Round 6: suspended apps in the developer profile
- Developer app cards now read the live catalog entry: a suspended app shows **Suspended** (red card) instead of "Published".
- The admin's suspension reason is shown on the card and in the detail screen, with the next step ("Fix the problem, then resubmit") and a one-tap "Resubmit for review".
- Rejected apps show the admin's feedback and the same resubmit prompt.
- Approving a resubmission clears the suspension, so the app comes back live.
- Removed the fake "5.0 / 23M" numbers from the card; it shows the app's real rating and size.

## Round 7: referral rewards
- Admin (Overview, under the Premium card): Referrals ON/OFF, friends needed per reward, Premium length (lifetime / 7 / 30 / 90 / 365 days), how new a friend's account must be (1-30 days), top referrers. Stored in `premiumConfig/referral`.
- Users (Settings, above the Premium card; shown only when Premium is paid-mode and referrals are ON): own 8-character code, progress bar, share/copy, "enter a friend's code", claim reward.
- Anti-abuse enforced by the database rules: one code use per account, no self-referral, only accounts created within the admin's window, count can only go +1 together with the referral record, a reward needs count >= (claimed+1) x required, Premium cannot be self-enabled when Premium is paid unless earned.
- Known limit: a time-limited reward is switched off by the app when it expires (the rules block re-enabling it, but a modified app could keep an already-true flag). Fake accounts can still farm referrals - raise "friends needed", shorten the window, or review "Top referrers".
- Deploy the updated `database.rules.json`.

## Round 8: referral anti-cheat
- **Same-device detection:** each phone gets a one-way fingerprint (hash of Android ID + salt) registered in `deviceRegistry`. A referral between two accounts used on the same phone is blocked by the rules and logged.
- **Fake accounts:** temporary-email domains are blocked at sign-up and for referrals (client + rules); more than N accounts on one phone (admin setting, default 3) makes every extra account suspend itself.
- **Automatic detection (admin app):** scans on every admin app start (max every 10 min) or "Scan now": same-phone referrals, phone farms, referral bursts, temp emails, referral farming. HIGH-severity cheaters are suspended automatically (can be switched off), their referral credit is taken back and a reward already claimed from fake referrals is revoked.
- **Notifications:** admin gets a system notification + alert list (Admin > Overview > Anti-cheat) + audit-log entry. The suspected user sees a "Your account is suspended" dialog with the reason and is signed out; login is blocked with the same reason.
- **Rules:** a user may only suspend ITSELF; only the admin can lift a suspension. Strikes can only go +1 on yourself.
- Limits (no backend server): the fingerprint comes from the client, so a modified app could lie; automatic suspension of other accounts happens when the admin app runs. Deploy the updated `database.rules.json`.

## Round 9: anti-cheat moved to the backend (notifier.py v2.2 + referral_guard.py)
- Referral codes, redeem, claim, device registration and the fraud watchdog now run on the DarkStore server with the Firebase Admin SDK. Clients can no longer write any of those nodes (rules), cannot switch Premium on for themselves while Premium is paid, and cannot lift their own suspension.
- The watchdog runs on the server every 2 minutes (no admin needs to be online); suspension also revokes the account's sessions and sends a push to the user and to every admin.
- Account age is checked against Firebase Auth's real creation time (not a client-written field).
- Deploy order: 1) upload `backend/notifier.py` + `referral_guard.py` and restart the worker, 2) paste the new `database.rules.json`, 3) install the new app. If the server is offline the referral screens say so (no cheating path opens up).
- Your `serviceAccountKey.json` was pasted into chat: delete that key in Google Cloud and generate a new one; it is NOT included in any zip here.

## Round 10: referral by link
- Share sends `https://darkrootstore.vercel.app/invite.html?ref=<CODE>` (code still works too). The invite page shows "<name> invited you", opens DarkStore when installed (verified App Link or `darkstore://invite/<CODE>`), or sends people to the download page.
- Not installed yet: the Download button leaves `DSREF:<CODE>` in the clipboard; the new app finds it once, clears it and applies it after sign-up. The code is kept for 14 days and applied automatically as soon as the user is signed in.
- Every link referral goes through the same server checks (same phone, temporary email, account age, strikes). The referral record stores `source: link|code`.
- Deploy: upload `website/invite.html` + new backend (`notifier.py`, `referral_guard.py`) and install the new app.

## Round 11: one referral identity per phone, no free Premium, tighter security
- **Per-phone referral identity:** the first account ever opened on a phone is its primary account (`deviceOwner/`). Only the primary can have a code, redeem a code or claim a reward. Extra accounts (and emulators) get no code; using one counts as cheating (strike -> auto-suspend) and extra accounts are flagged (MULTI_ACCOUNT_DEVICE). A phone can redeem only once (`deviceRedeemed/`).
- **Detection now works on every login/sign-up** (not only when Settings is opened) and retries until the token is ready.
- **Free Premium removed:** the free-mode switch, the self-toggle and the "Coming soon" flow are gone. Premium = admin role, referral reward, or admin grant (Users tab > Grant Premium). The server sweeps every 2 minutes and switches off any Premium that isn't earned (this also removes all old free Premium); admin button: "Remove old free Premium from everyone".
- **Admin Premium fixed:** admins always have Premium (the old free-mode check blocked them); admins can grant/remove Premium for any user through the server.
- **Security:** rules - Premium and suspension can't be set by clients, admin e-mail fallback now needs a verified address, new server-only nodes; backend - per-IP rate limit + brute-force lockout, security headers, optional "verified email required for referrals", emulator detection, server-set `_verified` flag.

## Round 12: suspended account popup on the login page
- A suspended account that tries to sign in (email or Google) is refused, its session is wiped immediately and the "remembered" Google account is forgotten (sign-out + revoke), so the account chooser shows again.
- The popup appears on the LOGIN page (not after login): "The account da***e@gmail.com was suspended. Reason: ... You can keep using DarkStore with your first account fi***t@gmail.com." with "Choose my first account" (opens the Google chooser) or "Use email & password" (clears the fields).
- Both addresses are masked by the server (`/account/suspension-info`); full addresses never leave the server. If there is no first account (admin suspension, or it is suspended too) the popup shows the reason and tells the person to contact the admin.
- Also used when a logged-in account gets suspended while using the app (forced sign-out lands on the same popup).

## Round 13: Premium APK upload + stricter one-account-per-phone
- **APK upload:** Premium users pick an APK in the submit form; the app reads its package/version locally, streams it to the DarkStore server, and gets back a download link (`/apk/<id>`) that fills the form. The dgx-cloud API key is NEVER in the app: the server holds it (env or admin panel, write-only). Downloads are proxied so the `?api_key=` URL of the storage service never reaches a user. Admin: on/off, limits, API address/key, test, totals, per-file delete.
- **Rotate the dgx key:** it was pasted into chat. Create a new key on dgx-cloud and enter it in Admin > APK storage (or `DGX_API_KEY`). Nothing in these zips contains it.
- **One account per phone:** default limit 1. 2nd/3rd accounts are suspended immediately: at login (popup on the login page, no session saved), or - if already logged in - the server suspends them, a 45-second check signs them out and the same popup names both accounts (masked).
- Storage caveat: free Render services have an ephemeral disk; make sure dgx-cloud keeps files on a persistent disk/bucket, otherwise uploads can disappear on redeploy.

## Round 14: performance & stability pass
Causes found (static audit - no device profiler available, so confirm on a phone):
1. **Three 4096-px PNGs** (`img_app_icon`, `img_app_logo_new`, `img_ad_badge`) = 67 + 67 + 45 MB when decoded. The logo was also the Coil *placeholder* of every app icon, so each card dragged a 67 MB bitmap through RAM + GPU texture upload -> jank and out-of-memory crashes. Now 512/1024 px (about 1 MB each) and the APK is ~7 MB smaller.
2. **Constant background work:** every sync downloaded + parsed the whole catalog, ALL submissions and ALL users (for every user) and rewrote Room + the widget + the on-disk page cache, every 12-30 s, forever (even with the app in the background). Now: 60 s foreground-only loop, ETag/304 (unchanged catalog = no download, no parse, no Room write, no recomposition), submissions only for developers/admins, users list only for admins, banners/collections/policy every 5 min, installed-apps scan every 5 min, Unity config every 10 min.
3. **Full catalog JSON printed to logcat** on every fetch (several large log lines per sync): replaced by a byte count.
4. **Glass tiles** rebuilt 3 gradient brushes + lists on every recomposition of every tile, and clipped twice. Brushes are now created once; tiles inside scrolling rows no longer draw a shadow each.
5. **Disk cache** rewrote the (growing) page list to disk after every refresh even when identical; now skipped when unchanged.
6. Debug APKs are slow by nature (no R8, debuggable): the CI summary now says to judge speed on the RELEASE artifact.

## Round 15: predictive back + more performance
- Predictive back: manifest `enableOnBackInvokedCallback`, `view/PredictiveBack.kt` helper, applied to App details and the Submit form; back from any tab returns to Apps; back closes an open chat first.
- Removed the 650 ms fake loading delay when opening an app. See `DarkStore_PERFORMANCE_REPORT.md` for the full report (static analysis only - nothing was profiled).

## Round 16: two apps (User + Admin)
- One codebase, two Gradle product flavors: `user` (`com.darkstore.darkroot`) and `admin` (`com.darkstore.darkroot.admin`, "DarkStore Admin", red ADMIN icon).
- The admin console (`ConsoleTabContent`, `AdminSubmissionCard`, `SendNoticeFormDialog`, `AdminStepper`, `toSubmissionEntity`) and the admin-only panels (`AdminNotifyPanel`, `AdminApkStorageCard`, `ServerStatusPanel`) live in `app/src/admin/` and are NOT compiled into the user APK (the user flavor gets a do-nothing stub).
- User app: no admin tab even for admin accounts (they use the normal UI), keeps share/invite links, widget, ads, forced updates.
- Admin app: only Console / Chat / Account; no sign-up, no guest mode, no ads, no widget, no share/invite links, no forced "update to the user app". Login is refused (and the remembered Google account forgotten) for any account whose role is not `admin`.
- Real protection is unchanged and server-side (RTDB rules + backend admin-token checks); the split removes the admin UI from every public install and shrinks the user APK. The shared ViewModel still contains the admin API calls (they cannot succeed without an admin token).
- Firebase: register `com.darkstore.darkroot.admin` (+ SHA-1/256) and replace `app/src/admin/google-services.json` (temporary copy included) - needed for Google sign-in and push in the Admin app.
