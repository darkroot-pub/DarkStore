import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'core.dart';
import 'sb.dart';
import 'tabs.dart' hide adsTab;
import 'security_api.dart';
import 'security_tab.dart';
import 'ads_tab.dart';
import 'fraud_tab.dart';
import 'profile_ui_tab.dart';
import 'nav_ui_tab.dart';
import 'tools_tab.dart';
import 'control_tabs.dart';
import 'help_tabs.dart';
import 'publish_guide_tab.dart';
import 'maintenance_tab.dart';
import 'v16_tabs.dart';
import 'v17_tabs.dart';
import 'api_tab.dart';
import 'app_box_tab.dart';
import 'team_tab.dart';
import 'premium_ui_tab.dart';

// ───────────── Who is signed in, and what may they do ─────────────

/// Areas a staff role can be given. "area" = view, "area:w" = view + change.
const kAreas = <(String, String, String, IconData)>[
  ('submissions', 'Submissions', 'Review, approve and reject submitted apps', Icons.inbox_rounded),
  ('security', 'Security', 'Scan reports, rescans, device guard', Icons.shield_moon_rounded),
  ('catalog', 'Catalog', 'Apps, collections, featured lists', Icons.store_rounded),
  ('reviews', 'Reviews', 'Ratings and written reviews', Icons.rate_review_rounded),
  ('users', 'Users', 'People, developers, devices', Icons.people_rounded),
  ('fraud', 'Fraud', 'Fraud alerts and suspicious activity', Icons.gpp_maybe_rounded),
  ('support', 'Support', 'Support chat, feedback, chat rules', Icons.support_agent_rounded),
  ('content', 'Content', 'Banners, events, announcements, FAQ, texts', Icons.campaign_rounded),
  ('design', 'App design', 'Navigation, profile, home layout, app box', Icons.palette_rounded),
  ('ads', 'Ads', 'Ad placement and ad settings', Icons.ads_click_rounded),
  ('settings', 'Settings', 'Feature switches and app settings', Icons.tune_rounded),
  ('api', 'Public API', 'API keys, limits and which apps are shared', Icons.api_rounded),
  ('audit', 'Audit log', 'History of what staff changed', Icons.history_rounded),
  ('insights', 'Insights', 'Statistics and search insights', Icons.insights_rounded),
];

Color hexColor(String h, [Color fallback = accent]) {
  final x = h.replaceAll('#', '').trim();
  if (x.length != 6) return fallback;
  final v = int.tryParse(x, radix: 16);
  return v == null ? fallback : Color(0xFF000000 | v);
}

class Access {
  final bool admin;
  final String role, label;
  final Color color;
  final Set<String> perms;
  const Access(this.admin, this.role, this.label, this.color, this.perms);
  static const none = Access(false, '', '', Colors.grey, {});

  bool get isStaff => admin || role.isNotEmpty;
  bool can(String? area, [bool write = false]) {
    if (admin || area == null) return true;
    return perms.contains('$area:w') || (!write && perms.contains(area));
  }

  /// Signed-in person → access. The owner e-mail is always a full administrator.
  static Future<Access> load(User u) async {
    const adminAccess = Access(true, 'admin', 'Administrator', Color(0xFFEF4444), {});
    if (u.email == ownerEmail && u.emailVerified) return adminAccess;
    try {
      final r = await Sb.c.rpc('my_access');
      if (r is Map) {
        if (r['admin'] == true) return adminAccess;
        final role = s(r['role']);
        if (role.isEmpty) return none;
        return Access(false, role, s(r['label']).isEmpty ? role : s(r['label']), hexColor(s(r['color']), Colors.teal),
            {for (final x in (r['perms'] is List ? r['perms'] as List : const [])) '$x'});
      }
    } catch (e) {
      debugPrint('my_access: $e');
    }
    return (await SbDb.isAdminUid(u.uid)) ? adminAccess : none;
  }
}

/// The person using the panel right now (set once by the gate).
Access myAccess = Access.none;

/// Screens ask the shell to open another screen by id (e.g. a dashboard tile).
final ValueNotifier<String?> navTo = ValueNotifier<String?>(null);

// ───────────── Screens ─────────────

class NavItem {
  final String id, label, group, desc;
  final IconData icon;
  final String? area; // null = every staff member
  final bool adminOnly;
  final Widget Function() build;
  const NavItem(this.id, this.label, this.group, this.icon, this.desc, this.build, {this.area, this.adminOnly = false});
}

const _groups = ['Overview', 'Review & safety', 'Catalog & content', 'People', 'Growth & messaging', 'App design', 'Platform'];
const _groupIcons = {
  'Overview': Icons.dashboard_rounded,
  'Review & safety': Icons.verified_user_rounded,
  'Catalog & content': Icons.store_rounded,
  'People': Icons.groups_rounded,
  'Growth & messaging': Icons.campaign_rounded,
  'App design': Icons.palette_rounded,
  'Platform': Icons.settings_suggest_rounded,
};

List<NavItem> _allItems() => [
      NavItem('home', 'Dashboard', 'Overview', Icons.space_dashboard_rounded, 'What needs your attention today', () => FutureTab(dashTab)),
      NavItem('subs', 'Submissions', 'Review & safety', Icons.inbox_rounded, 'Apps waiting for a decision', () => FutureTab(subsTab), area: 'submissions'),
      NavItem('security', 'Security scans', 'Review & safety', Icons.shield_moon_rounded, 'Scan results, quarantine and rescans', () => const SecurityTab(), area: 'security'),
      NavItem('guard', 'Device guard', 'Review & safety', Icons.security_rounded, 'Root, emulator and tamper checks', () => FutureTab(guardTab), area: 'security'),
      NavItem('fraud', 'Fraud alerts', 'Review & safety', Icons.gpp_maybe_rounded, 'Suspicious behaviour', () => FutureTab(fraudTab), area: 'fraud'),
      NavItem('feedback', 'Feedback & reports', 'Review & safety', Icons.flag_rounded, 'Reports sent by users', () => FutureTab(feedbackTab), area: 'support'),
      NavItem('catalog', 'Apps', 'Catalog & content', Icons.store_rounded, 'Every app in the store', () => FutureTab(catalogTab), area: 'catalog'),
      NavItem('collections', 'Collections', 'Catalog & content', Icons.collections_rounded, 'Hand-picked app lists', () => FutureTab(collectionsTab), area: 'catalog'),
      NavItem('reviews', 'Reviews', 'Catalog & content', Icons.rate_review_rounded, 'Ratings and comments', () => FutureTab(reviewsAdminTab), area: 'reviews'),
      NavItem('banners', 'Banners', 'Catalog & content', Icons.view_carousel_rounded, 'Home banners', () => FutureTab(bannersTab), area: 'content'),
      NavItem('events', 'Events', 'Catalog & content', Icons.celebration_rounded, 'Festival and event themes', () => FutureTab(eventsTab), area: 'content'),
      NavItem('publishguide', 'Publish guide', 'Catalog & content', Icons.rocket_launch_rounded, 'Home notice + how to publish page with video', () => FutureTab(publishGuideTab), area: 'content'),
      NavItem('faq', 'Help / FAQ', 'Catalog & content', Icons.quiz_rounded, 'Questions and answers', () => FutureTab(faqTab), area: 'content'),
      NavItem('search', 'Search', 'Catalog & content', Icons.manage_search_rounded, 'Search settings and insights', () => FutureTab(searchAdminTab), area: 'content'),
      NavItem('share', 'Share text', 'Catalog & content', Icons.share_rounded, 'Text used when sharing', () => FutureTab(shareTab), area: 'content'),
      NavItem('users', 'Users', 'People', Icons.people_rounded, 'Accounts, developers, premium', () => FutureTab(usersTab), area: 'users'),
      NavItem('devices', 'Devices', 'People', Icons.devices_rounded, 'Registered devices', () => FutureTab(devicesTab), area: 'users'),
      NavItem('support', 'Support chat', 'People', Icons.support_agent_rounded, 'Conversations with users', () => FutureTab(supportTab), area: 'support'),
      NavItem('rules', 'Chat rules', 'People', Icons.gavel_rounded, 'Blocked words and chat limits', () => FutureTab(rulesTab), area: 'support'),
      NavItem('team', 'Team & roles', 'People', Icons.admin_panel_settings_rounded, 'Who can use this panel, and what they may do', () => FutureTab(teamTab), adminOnly: true),
      NavItem('notify', 'Send notification', 'Growth & messaging', Icons.notifications_active_rounded, 'Push messages to users', () => const NotifyTab(), adminOnly: true),
      NavItem('announce', 'Announcements', 'Growth & messaging', Icons.announcement_rounded, 'In-app notices', () => FutureTab(announceTab), area: 'content'),
      NavItem('ads', 'Ads', 'Growth & messaging', Icons.ads_click_rounded, 'Ad placement', () => FutureTab(adsTab), area: 'ads'),
      NavItem('suggest', 'Suggestions', 'Growth & messaging', Icons.auto_awesome_rounded, 'Recommendation settings', () => FutureTab(suggestTab), area: 'design'),
      NavItem('profileui', 'Profile screen', 'App design', Icons.badge_rounded, 'Profile page layout', () => const ProfileUiTab(), area: 'design'),
      NavItem('premiumui', 'Premium card', 'App design', Icons.workspace_premium_rounded, 'Design the Premium card members see', () => const PremiumUiTab(), area: 'design'),
      NavItem('navui', 'Navigation bar', 'App design', Icons.view_day_rounded, 'Bottom bar items', () => const NavUiTab(), area: 'design'),
      NavItem('homelayout', 'Home layout', 'App design', Icons.view_agenda_rounded, 'Order of home sections', () => FutureTab(homeLayoutTab), area: 'design'),
      NavItem('appbox', 'App box', 'App design', Icons.dashboard_customize_rounded, 'App card design', () => const AppBoxTab(), area: 'design'),
      NavItem('features', 'Feature switches', 'Platform', Icons.toggle_on_rounded, 'Turn app features on or off', () => FutureTab(featuresTab), area: 'settings'),
      NavItem('api', 'Public API', 'Platform', Icons.api_rounded, 'Keys, limits and shared apps', () => FutureTab(apiAdminTab), area: 'api'),
      NavItem('update', 'Push app update', 'Platform', Icons.system_update_rounded, 'Tell users a new version exists', () => FutureTab(updateTab), adminOnly: true),
      NavItem('audit', 'Audit log', 'Platform', Icons.history_rounded, 'Who changed what', () => FutureTab(auditTab), area: 'audit'),
      NavItem('server', 'Server', 'Platform', Icons.cloud_rounded, 'Notifier server address and status', () => FutureTab(serverTab), adminOnly: true),
      NavItem('maintenance', 'Maintenance', 'Platform', Icons.construction_rounded, 'Close the store now or on a schedule', () => FutureTab(maintenanceTab), adminOnly: true),
      NavItem('tools', 'Tools & backup', 'Platform', Icons.build_circle_rounded, 'Backups, imports, maintenance', () => FutureTab(toolsTab), adminOnly: true),
    ];

List<NavItem> visibleItems() => [
      for (final i in _allItems())
        if (i.adminOnly ? myAccess.admin : (i.area == null || myAccess.can(i.area))) i
    ];

// ───────────── The shell ─────────────

class Shell extends StatefulWidget {
  const Shell({super.key});
  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  late final List<NavItem> items = visibleItems();
  final Map<String, Widget> _built = {};
  String sel = 'home';
  final scaffoldKey = GlobalKey<ScaffoldState>();

  NavItem get cur => items.firstWhere((i) => i.id == sel, orElse: () => items.first);

  @override
  void initState() {
    super.initState();
    navTo.addListener(_onNav);
    _apply();
  }

  @override
  void dispose() {
    navTo.removeListener(_onNav);
    viewOnly.value = false;
    super.dispose();
  }

  void _onNav() {
    final id = navTo.value;
    if (id == null) return;
    navTo.value = null;
    go(id);
  }

  void _apply() {
    final it = cur;
    viewOnly.value = !myAccess.admin && it.area != null && !myAccess.can(it.area, true);
  }

  void go(String id) {
    if (!items.any((i) => i.id == id)) return;
    setState(() => sel = id);
    _apply();
  }

  Future<void> openPalette() async {
    final id = await showDialog<String>(context: context, builder: (_) => _Palette(items));
    if (id != null) go(id);
  }

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= 960;
    final it = cur;
    final page = _built.putIfAbsent(sel, () => KeyedSubtree(key: ValueKey(sel), child: it.build()));
    final body = Column(children: [
      _PageHeader(it, wide: wide, onMenu: () => scaffoldKey.currentState?.openDrawer(), onSearch: openPalette),
      ValueListenableBuilder<bool>(
        valueListenable: viewOnly,
        builder: (_, v, __) => v
            ? Container(
                width: double.infinity,
                color: Theme.of(context).colorScheme.tertiaryContainer,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: Row(children: [
                  Icon(Icons.visibility_rounded, size: 16, color: Theme.of(context).colorScheme.onTertiaryContainer),
                  const SizedBox(width: 8),
                  Expanded(child: Text('View only: your role can look at this screen but not change it.', style: TextStyle(fontSize: 12.5, color: Theme.of(context).colorScheme.onTertiaryContainer))),
                ]))
            : const SizedBox.shrink(),
      ),
      Expanded(
        child: IndexedStack(
          index: items.indexWhere((i) => i.id == sel),
          children: [for (final i in items) i.id == sel ? page : (_built[i.id] ?? const SizedBox.shrink())],
        ),
      ),
    ]);
    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.keyK, control: true): openPalette,
        const SingleActivator(LogicalKeyboardKey.keyK, meta: true): openPalette,
      },
      child: Focus(
        autofocus: true,
        child: PopScope(
          canPop: sel == 'home',
          onPopInvokedWithResult: (didPop, _) {
            if (!didPop) go('home');
          },
          child: Scaffold(
            key: scaffoldKey,
            drawer: wide ? null : Drawer(width: 300, child: _SidePanel(items, sel, (id) { Navigator.pop(context); go(id); })),
            body: SafeArea(
              child: wide
                  ? Row(children: [
                      SizedBox(width: 272, child: _SidePanel(items, sel, go)),
                      VerticalDivider(width: 1, color: Theme.of(context).colorScheme.outlineVariant),
                      Expanded(child: body),
                    ])
                  : body,
            ),
          ),
        ),
      ),
    );
  }
}

class _PageHeader extends StatelessWidget {
  final NavItem it;
  final bool wide;
  final VoidCallback onMenu, onSearch;
  const _PageHeader(this.it, {required this.wide, required this.onMenu, required this.onSearch});
  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: EdgeInsets.fromLTRB(wide ? 24 : 4, 10, 12, 10),
      decoration: BoxDecoration(color: cs.surface, border: Border(bottom: BorderSide(color: cs.outlineVariant))),
      child: Row(children: [
        if (!wide) IconButton(tooltip: 'Menu', icon: const Icon(Icons.menu_rounded), onPressed: onMenu) else Icon(it.icon, color: cs.primary),
        if (wide) const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(it.label, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
            Text(it.desc, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)),
          ]),
        ),
        if (wide)
          OutlinedButton.icon(
            onPressed: onSearch,
            icon: const Icon(Icons.search_rounded, size: 18),
            label: const Text('Go to…   Ctrl K'),
          )
        else
          IconButton(tooltip: 'Go to…', icon: const Icon(Icons.search_rounded), onPressed: onSearch),
      ]),
    );
  }
}

class _SidePanel extends StatelessWidget {
  final List<NavItem> items;
  final String sel;
  final void Function(String) onSelect;
  const _SidePanel(this.items, this.sel, this.onSelect);

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final u = FirebaseAuth.instance.currentUser;
    final name = (u?.displayName ?? '').isNotEmpty ? u!.displayName! : (u?.email ?? 'Signed in');
    return Material(
      color: cs.surfaceContainerLow,
      child: SafeArea(
        child: Column(children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Row(children: [
              Container(
                width: 36, height: 36,
                decoration: BoxDecoration(color: cs.primary, borderRadius: BorderRadius.circular(10)),
                child: Icon(Icons.admin_panel_settings_rounded, color: cs.onPrimary, size: 20),
              ),
              const SizedBox(width: 10),
              const Expanded(child: Text('DarkStore Admin', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800))),
            ]),
          ),
          Expanded(
            child: ListView(padding: const EdgeInsets.fromLTRB(10, 4, 10, 10), children: [
              for (final g in _groups) ...[
                if (items.any((i) => i.group == g)) ...[
                  Padding(
                    padding: const EdgeInsets.fromLTRB(8, 14, 8, 4),
                    child: Text(g.toUpperCase(), style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w800, letterSpacing: .8, color: cs.onSurfaceVariant)),
                  ),
                  for (final i in items.where((x) => x.group == g))
                    _NavTile(i, selected: i.id == sel, viewOnly: !myAccess.admin && i.area != null && !myAccess.can(i.area, true), onTap: () => onSelect(i.id)),
                ],
              ],
            ]),
          ),
          Divider(height: 1, color: cs.outlineVariant),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Row(children: [
              CircleAvatar(
                radius: 18,
                backgroundColor: myAccess.color.withOpacity(.18),
                backgroundImage: (u?.photoURL ?? '').isNotEmpty ? NetworkImage(u!.photoURL!) : null,
                child: (u?.photoURL ?? '').isNotEmpty ? null : Text(name.isEmpty ? '?' : name[0].toUpperCase(), style: TextStyle(color: myAccess.color, fontWeight: FontWeight.w800)),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
                  const SizedBox(height: 2),
                  Align(alignment: Alignment.centerLeft, child: RoleBadge(myAccess.label.isEmpty ? 'Staff' : myAccess.label, myAccess.color)),
                ]),
              ),
              IconButton(tooltip: 'Sign out', icon: const Icon(Icons.logout_rounded, size: 20), onPressed: () => FirebaseAuth.instance.signOut()),
            ]),
          ),
        ]),
      ),
    );
  }
}

class _NavTile extends StatelessWidget {
  final NavItem item;
  final bool selected, viewOnly;
  final VoidCallback onTap;
  const _NavTile(this.item, {required this.selected, required this.viewOnly, required this.onTap});
  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 1),
      child: Material(
        color: selected ? cs.secondaryContainer : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Row(children: [
              Icon(item.icon, size: 20, color: selected ? cs.onSecondaryContainer : cs.onSurfaceVariant),
              const SizedBox(width: 12),
              Expanded(child: Text(item.label, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 13.5, fontWeight: selected ? FontWeight.w800 : FontWeight.w600, color: selected ? cs.onSecondaryContainer : cs.onSurface))),
              if (viewOnly) Icon(Icons.visibility_outlined, size: 15, color: cs.onSurfaceVariant),
            ]),
          ),
        ),
      ),
    );
  }
}

class RoleBadge extends StatelessWidget {
  final String text;
  final Color color;
  const RoleBadge(this.text, this.color, {super.key});
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
        decoration: BoxDecoration(color: color.withOpacity(.15), borderRadius: BorderRadius.circular(99)),
        child: Text(text, style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w800, color: color)),
      );
}

/// "Go to…" search: type a few letters of any screen you are allowed to open.
class _Palette extends StatefulWidget {
  final List<NavItem> items;
  const _Palette(this.items);
  @override
  State<_Palette> createState() => _PaletteState();
}

class _PaletteState extends State<_Palette> {
  String q = '';
  @override
  Widget build(BuildContext context) {
    final low = q.trim().toLowerCase();
    final list = widget.items.where((i) => low.isEmpty || i.label.toLowerCase().contains(low) || i.desc.toLowerCase().contains(low) || i.group.toLowerCase().contains(low)).toList();
    return Dialog(
      alignment: Alignment.topCenter,
      insetPadding: const EdgeInsets.fromLTRB(16, 56, 16, 16),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 520, maxHeight: 520),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Padding(
            padding: const EdgeInsets.all(12),
            child: TextField(
              autofocus: true,
              decoration: const InputDecoration(prefixIcon: Icon(Icons.search_rounded), hintText: 'Go to a screen…'),
              onChanged: (v) => setState(() => q = v),
              onSubmitted: (_) {
                if (list.isNotEmpty) Navigator.pop(context, list.first.id);
              },
            ),
          ),
          Flexible(
            child: list.isEmpty
                ? const Padding(padding: EdgeInsets.all(24), child: Text('Nothing matches. Try another word.'))
                : ListView(shrinkWrap: true, children: [
                    for (final i in list)
                      ListTile(
                        dense: true,
                        leading: Icon(i.icon),
                        title: Text(i.label, style: const TextStyle(fontWeight: FontWeight.w700)),
                        subtitle: Text('${i.group} · ${i.desc}', maxLines: 1, overflow: TextOverflow.ellipsis),
                        onTap: () => Navigator.pop(context, i.id),
                      ),
                  ]),
          ),
        ]),
      ),
    );
  }
}

// ───────────── Dashboard ─────────────

class _Stat {
  final String label;
  final int value;
  final IconData icon;
  final Color color;
  final String? goto;
  final bool urgent;
  const _Stat(this.label, this.value, this.icon, this.color, {this.goto, this.urgent = false});
}

Future<Widget> dashTab(BuildContext c, VoidCallback rl) async {
  Future<List<Map<String, dynamic>>> safe(String t, String? area) async {
    if (area != null && !myAccess.can(area)) return <Map<String, dynamic>>[];
    try {
      return await Db.list(t);
    } catch (_) {
      return <Map<String, dynamic>>[];
    }
  }

  final r = await Future.wait([safe('apps', 'catalog'), safe('users', 'users'), safe('submissions', 'submissions'), safe('fraudAlerts', 'fraud'), safe('darkstoreReports', 'support')]);
  final a = r[0], u = r[1], sb = r[2];
  var needsReview = 0, failed = 0;
  var secOk = false;
  try {
    if (myAccess.can('security') && await SecApi.configured()) {
      final sec = await SecApi.overview();
      needsReview = ni(sec['needs_review']);
      failed = ni(sec['failed']);
      secOk = true;
    }
  } catch (_) {}
  final pending = sb.where((x) => s(x['status']).isEmpty || s(x['status']).toLowerCase() == 'pending').length;
  final fraud = r[3].where((x) => s(x['status']) == 'open').length;
  final reports = a.where((x) => s(x['reportsJson']).isNotEmpty).length;

  final attention = <_Stat>[
    if (myAccess.can('submissions')) _Stat('Submissions waiting', pending, Icons.inbox_rounded, Colors.orange, goto: 'subs', urgent: true),
    if (secOk) _Stat('Need security review', needsReview, Icons.shield_moon_rounded, Colors.deepOrange, goto: 'security', urgent: true),
    if (secOk) _Stat('Security failures', failed, Icons.error_outline_rounded, Colors.red, goto: 'security', urgent: true),
    if (myAccess.can('fraud')) _Stat('Open fraud alerts', fraud, Icons.gpp_maybe_rounded, Colors.red, goto: 'fraud', urgent: true),
    if (myAccess.can('catalog')) _Stat('Apps with user reports', reports, Icons.report_gmailerrorred_rounded, Colors.amber.shade800, goto: 'catalog', urgent: true),
  ];
  final todo = attention.where((x) => x.value > 0).toList();
  final overview = <_Stat>[
    if (myAccess.can('catalog')) _Stat('Apps', a.length, Icons.apps_rounded, Colors.blue, goto: 'catalog'),
    if (myAccess.can('catalog')) _Stat('Live', a.where((x) => bo(x['isApproved']) && !bo(x['isSuspended']) && !bo(x['isOnHold'])).length, Icons.check_circle_outline_rounded, Colors.green, goto: 'catalog'),
    if (myAccess.can('catalog')) _Stat('Suspended apps', a.where((x) => bo(x['isSuspended'])).length, Icons.block_rounded, Colors.grey, goto: 'catalog'),
    if (myAccess.can('users')) _Stat('Users', u.length, Icons.people_rounded, Colors.indigo, goto: 'users'),
    if (myAccess.can('users')) _Stat('Developers', u.where((x) => bo(x['isDeveloper'])).length, Icons.code_rounded, Colors.teal, goto: 'users'),
    if (myAccess.can('users')) _Stat('Premium', u.where((x) => bo(x['isPremiumMember'])).length, Icons.workspace_premium_rounded, Colors.purple, goto: 'users'),
    if (myAccess.can('users')) _Stat('Suspended users', u.where((x) => bo(x['isSuspended'])).length, Icons.person_off_rounded, Colors.grey, goto: 'users'),
    if (myAccess.can('support')) _Stat('Feedback', r[4].length, Icons.flag_rounded, Colors.blueGrey, goto: 'feedback'),
  ];
  final user = FirebaseAuth.instance.currentUser;
  final first = ((user?.displayName ?? '').isNotEmpty ? user!.displayName! : (user?.email ?? '')).split(RegExp(r'[ @]')).first;
  final cs = Theme.of(c).colorScheme;

  Widget tile(_Stat e, double w) => SizedBox(
        width: w,
        child: Card(
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: e.goto == null ? null : () => navTo.value = e.goto,
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Container(
                  padding: const EdgeInsets.all(7),
                  decoration: BoxDecoration(color: e.color.withOpacity(.14), borderRadius: BorderRadius.circular(10)),
                  child: Icon(e.icon, size: 18, color: e.color),
                ),
                const SizedBox(height: 10),
                Text('${e.value}', style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800, height: 1)),
                const SizedBox(height: 4),
                Text(e.label, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)),
              ]),
            ),
          ),
        ),
      );

  Widget grid(List<_Stat> list) => LayoutBuilder(builder: (_, k) {
        final cols = (k.maxWidth / 190).floor().clamp(2, 6);
        final w = (k.maxWidth - (cols - 1) * 10) / cols;
        return Wrap(spacing: 10, runSpacing: 10, children: [for (final e in list) tile(e, w)]);
      });

  Widget heading(String t, [String? sub]) => Padding(
        padding: const EdgeInsets.fromLTRB(2, 18, 2, 8),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(t, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800)),
          if (sub != null) Text(sub, style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)),
        ]),
      );

  return listPage([
    Text('Hello${first.isEmpty ? '' : ', $first'}', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
    const SizedBox(height: 4),
    Row(children: [
      RoleBadge(myAccess.label.isEmpty ? 'Staff' : myAccess.label, myAccess.color),
      const SizedBox(width: 8),
      Expanded(child: Text(myAccess.admin ? 'Full access' : 'Access limited to your role', style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant))),
    ]),
    heading('Needs your attention', todo.isEmpty ? null : 'Tap a card to open it'),
    if (todo.isEmpty)
      Card(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Row(children: [
            Icon(Icons.check_circle_rounded, color: Colors.green.shade600),
            const SizedBox(width: 12),
            const Expanded(child: Text('All clear. Nothing is waiting for you right now.')),
          ]),
        ),
      )
    else
      grid(todo),
    if (overview.isNotEmpty) ...[heading('Store at a glance'), grid(overview)],
    if (myAccess.can('insights') && a.isNotEmpty) ...[heading('Insights'), ...homeInsights(a, u, sb)],
    const SizedBox(height: 24),
  ]);
}

// ───────────── Theme ─────────────

ThemeData adminTheme(Brightness b) {
  final base = ThemeData(colorSchemeSeed: accent, brightness: b, useMaterial3: true);
  final cs = base.colorScheme;
  final shape12 = RoundedRectangleBorder(borderRadius: BorderRadius.circular(12));
  return base.copyWith(
    scaffoldBackgroundColor: cs.surface,
    cardTheme: CardTheme(
      elevation: 0,
      margin: const EdgeInsets.symmetric(vertical: 4),
      color: cs.surfaceContainerLow,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16), side: BorderSide(color: cs.outlineVariant.withOpacity(.7))),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: cs.surfaceContainerHighest.withOpacity(.45),
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide(color: cs.primary, width: 1.6)),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
    ),
    filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(shape: shape12, minimumSize: const Size(48, 46))),
    outlinedButtonTheme: OutlinedButtonThemeData(style: OutlinedButton.styleFrom(shape: shape12, minimumSize: const Size(48, 44))),
    textButtonTheme: TextButtonThemeData(style: TextButton.styleFrom(shape: shape12)),
    snackBarTheme: SnackBarThemeData(behavior: SnackBarBehavior.floating, shape: shape12),
    dialogTheme: DialogTheme(shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20))),
    bottomSheetTheme: const BottomSheetThemeData(showDragHandle: true, shape: RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24)))),
    listTileTheme: ListTileThemeData(shape: shape12),
    dividerTheme: DividerThemeData(color: cs.outlineVariant.withOpacity(.6), space: 1),
    chipTheme: ChipThemeData(shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(99)), side: BorderSide(color: cs.outlineVariant)),
  );
}
