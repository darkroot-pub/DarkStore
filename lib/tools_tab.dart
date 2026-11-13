import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'core.dart';

// ───────────── Home insights (computed from data the Home tab already loads) ─────────────

Widget _bar(String label, int n, int max, Color col) => Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(children: [
        SizedBox(width: 110, child: Text(label.isEmpty ? '(none)' : label, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12))),
        Expanded(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(value: max == 0 ? 0 : n / max, minHeight: 10, color: col, backgroundColor: col.withOpacity(.12)),
          ),
        ),
        SizedBox(width: 34, child: Text('$n', textAlign: TextAlign.end, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold))),
      ]),
    );

Widget _box(String title, List<Widget> kids) => Card(
      margin: const EdgeInsets.only(top: 10),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          ...kids,
        ]),
      ),
    );

/// Growth numbers and breakdowns for the Home tab.
List<Widget> homeInsights(List<Map<String, dynamic>> apps, List<Map<String, dynamic>> users, List<Map<String, dynamic>> subs) {
  final now = DateTime.now().millisecondsSinceEpoch;
  int within(List<Map<String, dynamic>> l, int days) => l.where((x) {
        final t = ni(x['createdAt']);
        return t > 0 && now - t <= days * 86400000;
      }).length;

  Map<String, int> count(Iterable<Map<String, dynamic>> l, String Function(Map<String, dynamic>) key) {
    final m = <String, int>{};
    for (final x in l) {
      final k = key(x).trim();
      m[k] = (m[k] ?? 0) + 1;
    }
    return m;
  }

  List<MapEntry<String, int>> top(Map<String, int> m, int n) => (m.entries.toList()..sort((a, b) => b.value.compareTo(a.value))).take(n).toList();

  final live = apps.where((x) => bo(x['isApproved']) && !bo(x['isSuspended']));
  final cats = top(count(live, (x) => s(x['category'])), 8);
  final status = top(count(subs, (x) => s(x['status']).isEmpty ? 'Pending' : s(x['status'])), 6);
  final devs = top(count(live.where((x) => s(x['developer']).isNotEmpty), (x) => s(x['developer'])), 5);

  Widget tile(String l, int n) => SizedBox(
        width: 105,
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('+$n', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
              Text(l, style: const TextStyle(fontSize: 11, color: Colors.grey)),
            ]),
          ),
        ),
      );

  return [
    const SizedBox(height: 14),
    const Text('GROWTH', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
    const SizedBox(height: 6),
    Wrap(spacing: 8, runSpacing: 8, children: [
      tile('Users · 7 days', within(users, 7)),
      tile('Users · 30 days', within(users, 30)),
      tile('Apps · 30 days', within(apps, 30)),
      tile('Submissions · 30 d', within(subs, 30)),
    ]),
    if (cats.isNotEmpty) _box('Live apps by category', [for (final e in cats) _bar(e.key, e.value, cats.first.value, Colors.teal)]),
    if (status.isNotEmpty) _box('Submissions by status', [for (final e in status) _bar(e.key, e.value, status.first.value, Colors.indigo)]),
    if (devs.isNotEmpty) _box('Top developers (live apps)', [for (final e in devs) _bar(e.key, e.value, devs.first.value, Colors.orange)]),
  ];
}

// ───────────── Tools tab: CSV export + settings backup ─────────────

String _cell(Object? v) {
  var t = v?.toString() ?? '';
  // a cell that starts with = + - @ would run as a formula in a spreadsheet
  if (t.isNotEmpty && '=+-@'.contains(t[0])) t = "'$t";
  return t.contains(RegExp(r'[",\n\r]')) ? '"${t.replaceAll('"', '""')}"' : t;
}

String _iso(Object? ms) {
  final n = ni(ms);
  return n <= 0 ? '' : DateTime.fromMillisecondsSinceEpoch(n, isUtc: true).toIso8601String();
}

String toCsv(List<Map<String, dynamic>> rows, List<String> cols, {Set<String> dates = const {'createdAt'}}) {
  final b = StringBuffer()..writeln(cols.map(_cell).join(','));
  for (final r in rows) {
    b.writeln(cols.map((k) => _cell(dates.contains(k) ? _iso(r[k]) : r[k])).join(','));
  }
  return b.toString();
}

/// Settings rows that can be backed up and restored (one JSON row each in `app_config`).
const backupKeys = ['adsConfig', 'unityAdsConfig', 'maintenanceConfig', 'premiumConfig', 'eventConfig', 'profileUiConfig', 'navUiConfig'];

Future<Widget> toolsTab(BuildContext c, VoidCallback rl) async {
  Future<void> exportCsv(String what, String path, List<String> cols) async {
    try {
      final rows = await Db.list(path);
      rows.sort((a, b) => ni(b['createdAt']).compareTo(ni(a['createdAt'])));
      await Clipboard.setData(ClipboardData(text: toCsv(rows, cols)));
      await Db.audit('EXPORT', 'csv', path, what, '${rows.length} rows');
      if (c.mounted) toast(c, '$what: ${rows.length} rows copied. Paste into Google Sheets or Excel.');
    } catch (e) {
      if (c.mounted) toast(c, 'Failed: $e');
    }
  }

  Future<void> backup() async {
    try {
      final out = <String, dynamic>{};
      for (final k in backupKeys) {
        final m = await Db.map(k);
        if (m.isNotEmpty) out[k] = m;
      }
      await Clipboard.setData(ClipboardData(text: const JsonEncoder.withIndent('  ').convert({'darkstoreSettings': 1, 'savedAt': DateTime.now().toUtc().toIso8601String(), 'configs': out})));
      await Db.audit('BACKUP', 'config', 'settings', 'Settings backup', '${out.length} settings');
      if (c.mounted) toast(c, '${out.length} settings copied. Paste them into a safe note.');
    } catch (e) {
      if (c.mounted) toast(c, 'Failed: $e');
    }
  }

  Future<void> restore() async {
    try {
      final clip = (await Clipboard.getData(Clipboard.kTextPlain))?.text ?? '';
      final j = jsonDecode(clip);
      final cfgs = (j is Map && j['configs'] is Map) ? Map<String, dynamic>.from(j['configs'] as Map) : <String, dynamic>{};
      final ok = cfgs.entries.where((e) => backupKeys.contains(e.key) && e.value is Map).toList();
      if (ok.isEmpty) {
        if (c.mounted) toast(c, 'The clipboard has no DarkStore settings backup.');
        return;
      }
      if (!c.mounted) return;
      final yes = await confirm(c, 'Restore ${ok.length} settings?', 'Replaces: ${ok.map((e) => e.key).join(', ')}. Current values of these are overwritten.');
      if (!yes) return;
      for (final e in ok) {
        await Db.del(e.key);
        await Db.upd(e.key, Map<String, Object?>.from(e.value as Map));
      }
      await Db.audit('RESTORE', 'config', 'settings', 'Settings restore', ok.map((e) => e.key).join(', '));
      if (c.mounted) toast(c, 'Restored ${ok.length} settings.');
    } catch (e) {
      if (c.mounted) toast(c, 'Could not restore: $e');
    }
  }

  Widget head(String t) => Padding(padding: const EdgeInsets.fromLTRB(4, 14, 4, 6), child: Text(t, style: const TextStyle(fontWeight: FontWeight.w800)));
  Widget tool(IconData i, String t, String sub, VoidCallback f) => Card(child: ListTile(leading: Icon(i), title: Text(t), subtitle: Text(sub), trailing: const Icon(Icons.chevron_right), onTap: f));

  return listPage([
    head('Export (copies a spreadsheet to the clipboard)'),
    tool(Icons.people_alt_outlined, 'Users', 'id, e-mail, names, role, developer / premium / suspended, joined', () => exportCsv('Users', 'users', const ['id', 'email', 'userName', 'displayName', 'devName', 'role', 'isDeveloper', 'isPremiumMember', 'isSuspended', 'createdAt'])),
    tool(Icons.apps, 'Apps', 'name, package, version, category, developer, status flags', () => exportCsv('Apps', 'apps', const ['id', 'name', 'packageName', 'version', 'versionCode', 'category', 'developer', 'submittedBy', 'isApproved', 'isSuspended', 'isFeatured', 'isPremium', 'createdAt'])),
    tool(Icons.inbox_outlined, 'Submissions', 'every submission with its status and who sent it', () => exportCsv('Submissions', 'submissions', const ['id', 'name', 'packageName', 'version', 'versionCode', 'status', 'submittedBy', 'createdAt'])),
    head('Settings backup'),
    tool(Icons.save_alt, 'Copy all settings', 'Ads, maintenance, premium, event popup, profile card and bottom bar designs as JSON', backup),
    tool(Icons.restore, 'Restore settings from clipboard', 'Paste a backup made above, then confirm', restore),
    const Padding(
      padding: EdgeInsets.all(8),
      child: Text('Tip: save a backup before big changes. Every export, backup and restore is written to the Audit log.', style: TextStyle(fontSize: 12, color: Colors.grey)),
    ),
  ]);
}
