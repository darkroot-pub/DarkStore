# Admin: Push update build

## Unsuspend now sticks (the bug)
The store app checks the phone at every start. A second account on a phone was suspended
again each time, even right after you unsuspended it (one account collected 7 strikes).
Now, whenever an account goes from suspended to active (Users tab, Fraud tab, SQL, anywhere):
- it is **protected** from the automatic rules (for good, or N days: your choice),
- its strikes, warnings and open fraud alerts are cleared.
Already applied to the live database (migration `guard_settings_unsuspend_sticks`); the same SQL is in `supabase/08_guard_settings.sql` if you ever rebuild the project.

## New tab: Guard
Every automatic-suspension setting, stored in `device_guard_config` (database), live in the app:
auto-suspend on/off, accounts per phone, **warnings before suspending** (the user gets a dialog first),
minutes between warnings, warning text, block emulators, protection after unsuspend (on/off, days),
clear counters on unsuspend. Lists protected accounts (with Remove) and warned accounts (with Clear);
"Protect an account" by e-mail.

## Update tab -> "Push update"
New fields, read by the store app: **What is new** (bullet list in the dialog), **Minimum version code**
(older apps cannot skip the update), **Ask again after N hours** after "Later".

## New tab: Announce
A message strip at the top of Home: style or custom colours, emoji, link (app page or https), audience
(all / signed in / guests / premium), schedule, closable. "Show again to everyone" re-shows it.

## New tab: Features
Switch Submit apps, Chat and Reviews off/on for everyone without a new build; custom "switched off" message.

## Also
Settings backup (Tools) now includes the announcement and feature switches.

Files in this zip: `lib/control_tabs.dart` (new), `lib/sb.dart`, `lib/main.dart`, `lib/tabs.dart`,
`lib/previews.dart`, `lib/tools_tab.dart`, `lib/fraud_tab.dart`, `supabase/08_guard_settings.sql`.
Copy them over the same names in your admin project. Not compiled here: send the first `Error:` line if CI complains.

---
# Added later: Support, Help / FAQ, rollout
- **Support** tab: Telegram (default t.me/davidstha01, already saved in the database), e-mail, website, privacy/terms links,
  one extra button, response note, suspension page title/message, show/hide on the suspension page. Live preview.
- **Help / FAQ** tab: add, edit, reorder, hide, delete questions; shown in the store app under Settings > About > Help & support.
- **Features** tab: each switch now has a **rollout %** (give a feature to 10%, then 50%, then everyone).
- Settings backup (Tools) includes support and FAQ.
Extra files in this zip: `lib/help_tabs.dart` (new).
