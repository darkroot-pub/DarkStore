# Admin panel: Nav bar designer (festival / event looks)

Add on top of CHANGES-PROFILE-UI.md. New: `lib/nav_ui_tab.dart`. Changed: `lib/main.dart`, `lib/sb.dart`, `lib/tools_tab.dart`.
No SQL and no new packages: the design is one more row in `app_config` (key `navUiConfig`). Needs the store app v12 or later.
Not compiled here (no Flutter SDK): run your CI and send me the first `Error:` line if there is one.

## Nav bar tab
- **Switch and schedule** (same idea as the Events tab): ON/OFF, starts at, ends at. Outside the window the store shows the
  original bar again by itself. Status chip: LIVE NOW / ON, outside schedule / OFF.
- **Festival and event looks**: Dashain, Tihar, Christmas, Eid, Holi, New Year, Halloween, Valentine, Slim line, Built-in.
  A look sets colours and the festive emoji; your schedule and tab names are kept. Fine-tune everything afterwards.
- **Shape**: floating or full width, height, corner radius, side margin, shadow, border (and its colour).
- **Colours**: bar (one colour or a gradient), selected highlight, selected icon, other icons, selected name, other names.
- **Tabs**: selected tab as a filled pill / a line / icon colour only, icon size, names always / selected only / hidden,
  rename the five tabs (blank = normal name, 14 letters max).
- **Festive touch**: an emoji that sits on the edge of the bar (left / centre / right).
- Live preview with tappable tabs. Save writes the whole design and an Audit log entry. "Built-in bar" removes the row.
- A fresh install starts OFF, so opening the tab never changes the store by itself.
- The Tools tab backup now includes the nav bar design too.
