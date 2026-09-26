# DarkStore — Realtime Database Schema

This is the authoritative reference for every node in the Realtime Database.
Nothing else touches this database — no Firestore, no other RTDB nodes exist
beyond what's documented here (verified by a full grep of the codebase).

Base URL: `https://dark-store-6836d-default-rtdb.asia-southeast1.firebasedatabase.app/`

## Top-level nodes

### `apps/{appId}`
The published catalog — every app/game visible in the store. One `AppEntity`
per key.

| Field | Type | Notes |
|---|---|---|
| id | string | matches the node key |
| name, developer, description, category | string | |
| version, versionCode | string / int | current published version |
| apkUrl, logo, screenshots | string | screenshots is comma-separated URLs |
| packageName | string | Android package id |
| rating | string | average, recomputed client-side on every new review |
| isApproved | bool | |
| submittedBy | string | the developer's email — who owns this app |
| hasAds | bool | |
| isSuspended, suspensionReason | bool / string | admin can pull a published app without deleting it |
| isPremium, price | bool / string | legacy — paid-app purchasing was removed; new apps can't set this, existing ones are grandfathered |
| **versionHistoryJson** | string (JSON) | see "Version History" below |
| changelog | string | changelog for the *current* version |
| reportsJson | string | user-submitted reports, semicolon/comma separated |

**Version History.** Every time a published app's version changes — whether
through a submission getting approved, or an admin editing the app directly —
the version being replaced is appended (never overwritten) into
`versionHistoryJson` as a JSON array of:
```json
{ "versionName": "1.2.0", "versionCode": 12, "apkUrl": "...", "changelog": "...", "publishedAt": 1737490000000 }
```
Shown in-app under a collapsible "VERSION HISTORY" section on the app details
screen (newest first).

**Access:** public read (guests browse without logging in). Write requires
any real logged-in account — see *Rules rationale* below for why this can't
be tightened to admin-only without breaking the review/rating flow.

### `submissions/{submissionId}`
The review queue — every app or update a developer has submitted, whatever
its current status. One `SubmissionEntity` per key.

| Field | Type | Notes |
|---|---|---|
| status | string | `"Pending"` / `"Approved"` / `"Rejected"` |
| feedback | string | admin's note — shown to the developer, and is the "reason" in status-change emails |
| submittedBy | string | developer's email — who gets notified of status changes |
| isUpdateSubmission | bool | distinguishes a new app from an update to an existing one |
| versionCode, changelog | int / string | only meaningful for update submissions |

**Access:** requires being logged in to read (the app fetches the whole node
and filters client-side to the current user's own submissions, or everything
if admin). Write requires being logged in — a developer creates/updates their
own submission; the admin updates `status`/`feedback` on approval/rejection.

### `users/{uid}`
One `UserEntity` per account — every user, not just developers (this used to
also have a separate `developers/{uid}` mirror node; that's been removed
entirely, see the changelog below).

| Field | Type | Notes |
|---|---|---|
| email, displayName, role | string | role is `"user"` or `"admin"` (display-only — real admin authority is the hardcoded UID/email in the rules, not this field) |
| isDeveloper, devWebsite, devGithub, devName, devBio | | developer profile |
| isEmailVerified | bool | defaults true so pre-existing accounts aren't retroactively blocked |
| profilePhotoUrl | string | |
| isPremiumMember | bool | drives the gold badge on this user's name/reviews and the highlighted-review styling; see `premiumConfig` below |

**Access:** requires being logged in to read (any account — the admin Users
tab and developer name-uniqueness checks both need the full list). Write is
restricted to your own uid, or the admin.

### `notices/{noticeId}`
Announcements/notices shown in-app and pushed via FCM.

**Access:** public read. Write is admin-only — nothing in the app lets a
regular user create a notice, so this is one of the few nodes that could be
locked down cleanly.

### `reviews/{appId}/{reviewId}`
User reviews, nested under the app they're for. One `ReviewEntity` per key.

**Access:** public read (reviews are shown to everyone, including guests).
Write requires being logged in.

### `terms_agreements/{id}`
Records of which users have accepted which policy version.

**Access:** requires login for both read and write.

### `app_policy` (singleton)
The current Terms/Ecosystem Policy text and version number.

**Access:** public read, admin-only write.

### `premiumConfig/isFree` (singleton)
The one switch controlling whether DarkStore Premium membership can
currently be turned on for free. There's no real payment processor behind
Premium yet — this defaults to `true` so nobody is ever blocked while the
value loads, and requires the admin to deliberately flip it to `false` once
real payment support exists. Flipping it off only blocks *new* activations
(shown a Coming Soon message) — it doesn't retroactively revoke Premium from
anyone who already has it via `users/{uid}.isPremiumMember`.

**Access:** public read (every app launch checks this), admin-only write —
toggleable from the admin console's Update Config tab.

### `DarkStoreUpdate` (singleton)
Self-update config for the DarkStore app itself (latest version code, force
vs. dismissible update).

**Access:** public read (every launch checks this, including for logged-out
users), admin-only write.

### `followers/{developerUid}/{followerUid}` and `following/{uid}/{developerUid}`
A "follow a developer" graph — rules exist for it, but **nothing in the app
code reads or writes these nodes yet**. There's no follow button, no
followers count, no UI referencing either node anywhere in MainActivity.kt or
StoreViewModel.kt. The rules are safe to deploy as-is (an unused node with
rules but no writers is harmless), but the feature itself isn't built. Let me
know if you want it actually implemented — a follow button on developer
profiles, a followers count, and a "new release from someone you follow"
notice would be the natural scope.

## Rules rationale — what's tight, and what's deliberately not

`database.rules.json` at the repo root is the actual rules file — deploy it
via the Firebase Console (Realtime Database → Rules tab → paste and Publish)
or `firebase deploy --only database` if you have the CLI set up. I can't
deploy it for you from here; there's no live access to your Firebase project
from this environment.

**Field-level app protection.** `apps/{appId}` write is scoped to: the admin,
OR the app's own developer (matched by `submittedBy` email) — and even the
developer can't flip `isApproved` themselves, closing off self-approval
entirely. This matches a real, existing feature: developers *do* legitimately
push their own screenshot/version updates directly from the Developer
Console without going through admin review each time (confirmed in the code
— `addOrUpdateAppInCatalog` is reachable from the developer's own app list,
not just the admin console).

**Ratings are the one deliberate exception.** Any logged-in user can review
*any* app, not just their own — so rating updates can't be gated by the
developer-ownership rule above. Fixed by giving `apps/{appId}/rating` its own
write rule (open to any logged-in user) separate from the rest of the app
object, and changing `updateAppRating()` in the Kotlin code to send a scoped
PATCH touching only that one field instead of overwriting the whole
`AppEntity` (which is what it did before, and which would have silently
failed under these rules — a reviewer was never the app's developer, so a
full-object write would be rejected). A reviewer can now update the rating
and nothing else on someone else's app.

**Reviews** validate shape and required fields server-side (star rating
1–5, all expected fields present) and can only be written by the user who
owns them (`userId` must match `auth.uid`, and you can't overwrite someone
else's review).

**Submissions** follow the same self-approval protection as apps: a
developer can create their own submission or edit it while it's still
"Pending," but can't set `status` to Approved/Rejected themselves — only the
admin branch of the rule can change status.

## Changelog

- **Removed:** `developers/{uid}` — a redundant mirror of `users/{uid}` that
  only existed as a side effect of certain save calls, could silently miss
  accounts, and was the root cause of a "some users don't show in the Users
  tab" bug. The admin Users tab now reads `users/` directly.
- **Removed:** duplicate in-memory copies of the active auth token and RTDB
  URL that used to live separately in both `FirebaseService` and
  `FirebaseAuthService` — now a single source of truth.
- **Added:** this file, and `database.rules.json` — neither existed before,
  despite one error message in the app already referencing
  `database.rules.json` by name.
- **Tightened:** rewrote the rules with real field-level protection —
  developers can only write their own apps/submissions and can't
  self-approve; ratings are split into their own scoped write path so a
  reviewer never has broader access than that one field; reviews validate
  their shape and ownership server-side. Added the two nodes (`app_policy`,
  `terms_agreements`) that were missing from an earlier draft of this file
  entirely, which would have made both completely inaccessible (no rule at
  any level defaults to fully denied in RTDB).
- **Added (rules only, not yet built):** `followers/{developerUid}/{followerUid}`
  and `following/{uid}/{developerUid}` — see that section above.
- **Added:** `premiumConfig/isFree` and `users/{uid}.isPremiumMember`.
  Premium membership moved from a local-only, device-only SharedPrefs flag to
  a real per-account field, enabling a visible gold badge on reviews/profile
  and highlighted reviews for Premium members — neither was possible when
  the flag lived only on one device with no server record at all. The
  free/paid switch lets an admin turn on real payment requirements later
  without an app update.
- **Fixed (Sep 24):** the follow/follower rules only granted `.read: true` at
  the deepest level (e.g. `followers/{devUid}/{followerUid}`), never at
  `followers/{devUid}` itself — but the app fetches the whole list at that
  shallower level (`followers/{devUid}.json`, `following/{uid}.json`), and
  RTDB read permission does not cascade upward from a child rule. Writes to
  the exact leaf path worked fine (so following someone looked like it
  succeeded), but the very next read of the aggregate list was silently
  denied and came back empty — looking exactly like an automatic unfollow on
  refresh. Moved `.read: true` up to `followers/{devUid}` and
  `following/{uid}`. **This is a rules-only fix — it requires re-deploying
  database.rules.json to the Firebase Console; no app rebuild changes this
  behavior on its own.**
