# Admin v17

## Approve a rejected app
- **Submissions** tab now has filters: All / Pending / Rejected / Approved (with counts) and search.
- A rejected submission shows its reason on the list and says "Tap to approve anyway".
- Opening it offers **Approve anyway** and **Move back to Pending**.
- Before approving a rejected one you see warnings if the developer has since sent a newer submission for the same
  package, or if the live app is already at the same/newer version (approving would roll it back). You can add a message
  for the developer; blank = the standard approval text. Logged as `APPROVE_AFTER_REJECT` / `REOPEN_SUBMISSION`.
- Database fix (`supabase/11_approve_after_reject.sql`, already applied): approving a rejected submission no longer keeps
  the old rejection reason as its "Approved" message. Hidden reviews also stop counting in the app rating.

## New tabs
- **Search**: switches (typo tolerance, search descriptions, suggestions, trending, automatic trending, record searches),
  max results, search-box text, your trending words, synonym groups, and a report of what people search and what
  finds nothing (one tap adds a synonym or pins a term as trending).
- **Reviews**: allow reviews, written text required, installed-only, "Installed" badge, helpful votes, developer replies,
  reports, auto-hide threshold, min/max length, default order. Below: the moderation queue (reported / hidden reviews)
  with Hide, Show again, Dismiss reports, Remove reply, Delete.
- **App box**: designer for the app cards (shelves, grids, search rows): size, columns, icon shape, corner radius, colours
  and gradient, border, shadow, name style, rating style, NEW/HOT/TOP corner label, presets, live preview.

Files: new `lib/v17_tabs.dart`, `lib/app_box_tab.dart`; changed `lib/tabs.dart` (submissions), `lib/main.dart`,
`lib/sb.dart`, `lib/tools_tab.dart` (backup list). Database: `supabase/10_reviews_search.sql`, `11_approve_after_reject.sql`
(both already applied to the DarkStore project). Not compiled here (no Flutter SDK).
