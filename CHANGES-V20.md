# Admin v20: new panel design + staff roles

## What is new
- New layout: side panel on tablet/desktop, menu drawer on phone. Screens grouped (Overview, Review & safety, Catalog & content, People, Growth & messaging, App design, Platform). "Go to..." search (button or Ctrl/Cmd+K). Screens load only when opened and keep their state. Back returns to the Dashboard.
- Dashboard: "Needs your attention" cards (pending submissions, security review/failures, fraud, reported apps) that open the screen when tapped, plus store-at-a-glance numbers. Shows only what your role may see.
- Staff roles (built in, editable): Moderator, Reviewer, Support, Content, Viewer. Each area is None / View / Edit.
- Team & roles screen (administrators only): add people by e-mail, change/remove a role, create/edit/delete roles with a permission matrix.
- View-only screens show a banner; save/delete actions say "View only" instead of writing. The database also refuses the write.
- Polished theme (rounded cards, inputs, sheets), nicer login and "No access yet" pages.

## Rules
- Owner e-mail and users with role `admin` = full access.
- Admin-only always: Team & roles, Send notification, Push app update, Server, Tools & backup.
- A person must have opened the DarkStore app and signed in once (needs a `users` row).
- Database part is already applied (`supabase/13_staff_roles.sql`). Backend v22 honours the same roles on `/api/v1/admin/*`.

## Not compiled here
Flutter code was only checked for bracket balance. Run `flutter analyze` / build and report any error.

## v20.1: edit submissions
- Submission detail now has **Edit submission details** (name, category, version, size, description, what's new, APK link, logo, screenshots, video, release date, ads, upcoming) with the live store preview. Saved changes are written to the audit log (`EDIT_SUBMISSION`, lists changed fields). Package name stays fixed. Approving publishes the edited values; editing an already approved submission does not change the live app (use Apps > Edit).
- **Copy APK link / Copy package** buttons.
- **Reject** now offers quick reasons plus your own message, and requires a reason.
- Needs `submissions` Edit permission for staff roles.

## v20.2: login fix for other staff/admins
- The admin app now asks the server to add the missing Supabase `role` claim (same self-heal the store app uses) when a Firebase account does not have it. Before, such accounts silently ran as "anonymous" and landed on "No access yet" even when they were admins.

## v20.3: Premium card designer
- New screen **Premium card** (App design). Live preview as Premium member / Free member, quick styles, card look, texts, benefit list editor, extra colours, free-form blocks. Saves `premiumUiConfig` (store app v20 reads it). Needs `design` Edit for staff roles (database rule already updated).
