# DarkStore Admin – Security backend integration

Copy these files over `Admin Panel/` (same paths). Not compiled here (no Flutter SDK in my environment):
run `flutter pub get` (new package: url_launcher) and your CI, send me the first `Error:` line if any.

## Setup (one value)
- GitHub secret `SECURITY_API` = your backend address, e.g. `https://security.yourdomain.com` (no `/api/v1`).
  `.github/workflows/build-apk.yml` already passes it as `--dart-define=SECURITY_API=...`.
- No rebuild needed alternative: open the new **Security** tab > gear icon > enter the address. It is stored at
  `notifierConfig/securityApiUrl` in the Realtime Database (admin only). The build-time value wins if both exist.
- The admin account must have `role = 'admin'` in `public.users` (the server checks it).

## New: Security tab (`lib/security_tab.dart`, `lib/security_api.dart`)
- Service card: online/offline, version, uptime, all values from `GET /api/v1/status` (queue + config for admins).
- Status tiles (tap to filter): Needs review, Scan failed, Publish failed, In progress, Published, Rejected. Counts roll up.
- Filter chips for every status with its count, search (app / package / developer), paging ("Load more").
- Refreshes by itself every 12 s while anything is being scanned.
- Each card: icon, name, package, version, status pill, summary, risk, updated time. Tap = full report.

## New: full report screen (7 tabs)
1. **Overview** – status banner with reasons and last error, facts (risk, permissions, signature, VirusTotal x/y, size, scans),
   SHA-256 + signer fingerprint (tap to copy), review info, the store submission (Approve / Reject in store), GitHub release.
2. **Checks** – every check, failed first with severity, passed ones in a collapsible list.
3. **App** – manifest: package, SDK levels, flags (debuggable, testOnly, cleartext…), native ABIs/libs, all permissions,
   activities/services/receivers/providers with exported flags.
4. **Signature** – verified, v1/v2/v3 schemes, each signer certificate (subject, issuer, validity, key, SHA-256/1), errors/warnings.
5. **Permissions** – risk level + score, sensitive permissions with explanations, risky combinations, manifest indicators.
6. **Scan** – VirusTotal: animated bar (malicious/suspicious/harmless/undetected), counts, threat names, every engine that
   flagged the file, link to the full VirusTotal report.
7. **Timeline** – events, jobs (attempts, errors), publishing details (load on demand).

Controls (bottom bar, depend on the status; each is confirmed and written to the Audit log):
- Needs review: **Approve** (optional note, publishes + trusts the certificate) · **Reject** (reason ≥ 3 chars) · **Rescan**
- Scan failed: **Retry** · **Rescan** · **Reject**      - Publish failed: **Retry publishing** · **Rescan**
- Rejected / Published: **Rescan**     - Being processed: live progress, buttons disabled
- No report yet: **Start security check**
- Server messages (409 conflicts, 429 limits, 403) are shown as they are.

## Changed existing screens
- Submissions tab: each card shows `SECURITY: <status>`; the detail sheet has a "Security report" button.
- Home tab: two new tiles, "Needs security review" and "Security failures".
- `main.dart`: new "Security" tab after Submissions. `pubspec.yaml`: `url_launcher`.

## Backend notes (new version you uploaded)
- `AUTO_INTAKE=true` (default) queues Pending submissions by itself, oldest first, 3 per minute, max 5 active, so the app does
  not have to call /process. Links that are not downloadable become SCAN_FAILED (not a rejection) and stay Pending.
- Security approval does NOT approve the app in the store unless `AUTO_APPROVE_IN_STORE=true`; use "Approve in store".
