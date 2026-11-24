# Admin panel: Profile UI designer + more tools

Copy these files over your admin project (same paths): `lib/profile_ui_tab.dart` (new), `lib/tools_tab.dart` (new),
`lib/main.dart`, `lib/tabs.dart`, `lib/sb.dart`. No new packages. No SQL: the design is one more row in the
existing `app_config` table (key `profileUiConfig`), which every signed-in user can read and only admins can write.
Not compiled here (no Flutter SDK in my environment): run your CI and send me the first `Error:` line if there is one.

The store app (v11 or later) must be installed on phones for the design to show. It reads the row live, keeps the
last design on the phone, and uses the built-in look when nothing is saved.

## Profile UI tab (new)
- Live preview at the top (Owner / Developer / Member / Guest), "Full size" button.
- Quick styles: Default, Ocean, Sunset, Midnight, Mint, Minimal, Left aligned.
- Banner: gradient / solid / photo (pick from device) / none, two colours, height, soft shapes, gold banner for Premium on/off.
- Profile picture: circle / rounded / square, size, ring (automatic / accent / own colour / none).
- Card: colour, corner radius, shadow. Text: left or centred, name size and colour, a tagline under the name.
- Show or hide: verified tick, e-mail, role badges, Apps/Followers/Following, bio, location, links.
- Styles for the counters and badges. "Custom design is ON" switch. "Built-in design" removes the row.
- Save writes the whole design and an Audit log entry.

## Home tab
Growth (new users 7 / 30 days, new apps, submissions), live apps by category, submissions by status, top developers.

## Tools tab (new)
- Copy users / apps / submissions as a spreadsheet (CSV) to the clipboard (formula-safe).
- Copy all settings as JSON, and restore them from the clipboard (ads, maintenance, premium, event popup, profile design).
- Every export, backup and restore is written to the Audit log.
