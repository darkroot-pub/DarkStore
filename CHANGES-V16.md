# Admin v16 – new tabs

- **Rules**: submission limits (pending at once, per 24 hours) and chat rules (longest message, slow mode, block links,
  banned words) with a "test a message" button. Enforced by the database, so they hold for every app version.
- **Suggestions**: controls "Suggested for you", "Because you…", "Similar apps" and "New & rising" in the store app:
  switches, apps per row, category cap, daily shuffle, importance weights with presets (Balanced, Personal first,
  Quality first, Discover new), pinned apps and never-suggest apps (picked from the catalogue), row titles.
- **Home layout**: show/hide Home shelves and rename them.
- **Share text**: the message people send when sharing an app or their invite code, with a live preview.

Files: new `lib/v16_tabs.dart`; changed `lib/main.dart` (4 tabs), `lib/sb.dart` (5 new config rows), `lib/tools_tab.dart`
(backup list). Database: `supabase/09_limits_chat_rules.sql` (already applied). Not compiled here (no Flutter SDK).
