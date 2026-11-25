import 'dart:convert';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'core.dart';
import 'sb.dart';
import 'fraud_tab.dart';
import 'previews.dart';
import 'tools_tab.dart';
import 'security_api.dart';
import 'security_tab.dart';
import 'uploader.dart';

Map<String, Object?> only(Map<String, dynamic> r, List<F> f) => {for (final x in f) x.k: r[x.k]};

class Searchable extends StatefulWidget {
  final List<Map<String, dynamic>> items;
  final String Function(Map<String, dynamic>) hay;
  final Widget Function(Map<String, dynamic>) tile;
  final List<Widget> header;
  const Searchable(this.items, this.hay, this.tile, {this.header = const [], super.key});
  @override
  State<Searchable> createState() => _SearchableState();
}

class _SearchableState extends State<Searchable> {
  String q = '';
  @override
  Widget build(BuildContext c) {
    final L = widget.items.where((x) => widget.hay(x).toLowerCase().contains(q.toLowerCase())).toList();
    return listPage([
      TextField(decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Search…', border: OutlineInputBorder()), onChanged: (x) => setState(() => q = x)),
      ...widget.header,
      Padding(padding: const EdgeInsets.symmetric(vertical: 6), child: Text('${L.length} shown', style: const TextStyle(color: Colors.grey))),
      ...L.map(widget.tile),
    ]);
  }
}

// ───────────── Home ─────────────
Future<Widget> homeTab(BuildContext c, VoidCallback rl) async {
  final r = await Future.wait([Db.list('apps'), Db.list('users'), Db.list('submissions'), Db.list('darkstoreReports'), Db.list('fraudAlerts')]);
  final a = r[0], u = r[1], sb = r[2];
  final st = {
    'Apps': a.length, 'Live': a.where((x) => bo(x['isApproved']) && !bo(x['isSuspended']) && !bo(x['isOnHold'])).length,
    'Suspended apps': a.where((x) => bo(x['isSuspended'])).length, 'Upcoming': a.where((x) => bo(x['isUpcoming'])).length,
    'Users': u.length, 'Developers': u.where((x) => bo(x['isDeveloper'])).length, 'Premium': u.where((x) => bo(x['isPremiumMember'])).length,
    'Suspended users': u.where((x) => bo(x['isSuspended'])).length,
    'Open fraud alerts': r[4].where((x) => s(x['status']) == 'open').length,
    'Pending review': sb.where((x) => s(x['status']).isEmpty || s(x['status']).toLowerCase() == 'pending').length,
    'App reports': a.where((x) => s(x['reportsJson']).isNotEmpty).length, 'Feedback': r[3].length,
  };
  // security backend (optional): what needs a human and what failed
  try {
    if (await SecApi.configured()) {
      final sec = await SecApi.overview();
      st['Needs security review'] = ni(sec['needs_review']);
      st['Security failures'] = ni(sec['failed']);
    }
  } catch (_) {}
  return listPage([
    Wrap(spacing: 8, runSpacing: 8, children: [
      for (final e in st.entries) SizedBox(width: 105, child: Card(child: Padding(padding: const EdgeInsets.all(12), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('${e.value}', style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800)), Text(e.key, style: const TextStyle(fontSize: 11, color: Colors.grey))])))),
    ]),
    ...homeInsights(a, u, sb),
  ]);
}

// ───────────── Submissions ─────────────
String _subState(Map<String, dynamic> x) {
  final v = s(x['status']).toLowerCase();
  return v.isEmpty ? 'pending' : v;
}

Future<Widget> subsTab(BuildContext c, VoidCallback rl) async {
  final L = await Db.list('submissions');
  L.sort((a, b) {
    final pa = _subState(a) == 'pending', pb = _subState(b) == 'pending';
    return pa != pb ? (pa ? -1 : 1) : ni(b['createdAt']).compareTo(ni(a['createdAt']));
  });
  // latest security status of the newest 100 checked submissions (one call)
  var sec = <String, Map<String, dynamic>>{};
  try {
    if (await SecApi.configured()) {
      final r = await SecApi.list(limit: 100);
      sec = {for (final it in asMaps(r['items'])) s(it['submission_id']): it};
    }
  } catch (_) {}
  return SubsView(L, sec, c, rl);
}

/// Submission list with All / Pending / Rejected / Approved filters and search.
class SubsView extends StatefulWidget {
  final List<Map<String, dynamic>> items;
  final Map<String, Map<String, dynamic>> sec;
  final BuildContext ctx;
  final VoidCallback rl;
  const SubsView(this.items, this.sec, this.ctx, this.rl, {super.key});
  @override
  State<SubsView> createState() => _SubsViewState();
}

class _SubsViewState extends State<SubsView> {
  String q = '';
  String f = 'all';

  @override
  Widget build(BuildContext context) {
    final all = widget.items;
    int count(String k) => k == 'all' ? all.length : all.where((x) => _subState(x) == k).length;
    final low = q.toLowerCase();
    final L = all
        .where((x) => (f == 'all' || _subState(x) == f) &&
            '${x['name']} ${x['packageName']} ${x['submittedBy']}'.toLowerCase().contains(low))
        .toList();
    return listPage([
      TextField(
        decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Search…', border: OutlineInputBorder()),
        onChanged: (v) => setState(() => q = v),
      ),
      const SizedBox(height: 8),
      Wrap(spacing: 8, children: [
        for (final k in const ['all', 'pending', 'rejected', 'approved'])
          ChoiceChip(
            label: Text('${k[0].toUpperCase()}${k.substring(1)} (${count(k)})'),
            selected: f == k,
            onSelected: (_) => setState(() => f = k),
          ),
      ]),
      Padding(padding: const EdgeInsets.symmetric(vertical: 6), child: Text('${L.length} shown', style: const TextStyle(color: Colors.grey))),
      ...L.map(_tile),
    ]);
  }

  Widget _tile(Map<String, dynamic> x) {
    final k = _subState(x);
    final st = k == 'pending' ? 'Pending' : s(x['status']);
    final secRow = widget.sec[s(x['id'])];
    final col = k == 'approved' ? Colors.green : k == 'rejected' ? Colors.red : Colors.orange;
    return Card(
      child: ListTile(
        leading: netImg(s(x['logo'])),
        title: Text(s(x['name'])),
        subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Wrap(children: [
            Text('${x['packageName']} · v${x['version']}  ', style: const TextStyle(fontSize: 12)),
            if (bo(x['isUpdateSubmission'])) tagChip('UPDATE', Colors.orange),
            if (bo(x['isUpcoming'])) tagChip('UPCOMING', Colors.purple),
            if (secRow != null) tagChip('SECURITY: ${statusV(s(secRow['status'])).label.toUpperCase()}', statusV(s(secRow['status'])).color),
          ]),
          if (k == 'rejected') ...[
            if (s(x['feedback']).isNotEmpty)
              Text('Reason: ${x['feedback']}', maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, color: Colors.red)),
            const Text('Tap to approve anyway', style: TextStyle(fontSize: 11, color: Colors.green, fontWeight: FontWeight.w600)),
          ],
        ]),
        isThreeLine: k == 'rejected',
        trailing: tagChip(st, col),
        onTap: () => subDetail(widget.ctx, x, widget.rl),
      ),
    );
  }
}

/// What an admin may correct on a submission before (or after) a decision. The package name stays fixed:
/// the per-package lock and the version history depend on it.
const subFields = [
  F('name', 'App name'), F('category', 'Category'), F('version', 'Version'), F('versionCode', 'Version code', 'num'),
  F('size', 'Size'), F('description', 'Description', 'area'), F('changelog', 'What\'s new', 'area'),
  F('apkUrl', 'APK URL'), F('logo', 'Logo URL', 'image'), F('screenshots', 'Screenshots (links, or add from device)', 'images'),
  F('videoUrl', 'Video URL'), F('expectedRelease', 'Expected release'),
  F('hasAds', 'Contains ads', 'bool'), F('isUpcoming', 'Upcoming', 'bool'),
];

Future<void> editSub(BuildContext c, Map<String, dynamic> x, VoidCallback rl) async {
  final init = {...x, 'screenshots': shots(x['screenshots']).join(', ')};
  final r = await editSheet(c, 'Edit submission: ${x['name']}', subFields, init, preview: appPrev);
  if (r == null || !c.mounted) return;
  final patch = only(r, subFields);
  final changed = <String>[
    for (final f in subFields)
      if ('${patch[f.k] ?? ''}' != '${init[f.k] ?? ''}') f.k,
  ];
  if (changed.isEmpty) {
    toast(c, 'Nothing changed');
    return;
  }
  await act(c, () async {
    await Db.upd('submissions/${x['id']}', patch);
    await Db.audit('EDIT_SUBMISSION', 'submission', s(x['id']), s(patch['name'] ?? x['name']), 'changed: ${changed.join(', ')}');
  }, rl, 'Saved: ${changed.length} field(s) updated');
}

void _copy(BuildContext c, String label, String v) {
  Clipboard.setData(ClipboardData(text: v));
  toast(c, '$label copied');
}

void subDetail(BuildContext c, Map<String, dynamic> x, VoidCallback rl) {
  final k = _subState(x);
  final pending = k == 'pending';
  final rejected = k == 'rejected';
  showModalBottomSheet(
    context: c, isScrollControlled: true, useSafeArea: true,
    builder: (d) => ListView(padding: const EdgeInsets.all(16), children: [
      const Text('PREVIEW — how it will look in the store', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
      const SizedBox(height: 6),
      appPrev(x),
      const SizedBox(height: 8),
      Text('By ${x['submittedBy']} · versionCode ${x['versionCode']} · ads: ${bo(x['hasAds'])}'),
      Text('APK: ${x['apkUrl']}', style: const TextStyle(fontSize: 12)),
      if (s(x['videoUrl']).isNotEmpty) Text('Video: ${x['videoUrl']}', style: const TextStyle(fontSize: 12)),
      if (rejected)
        Container(
          margin: const EdgeInsets.only(top: 10),
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(color: Colors.red.withOpacity(0.08), borderRadius: BorderRadius.circular(10), border: Border.all(color: Colors.red.withOpacity(0.4))),
          child: Text(s(x['feedback']).isEmpty ? 'Rejected (no reason was written).' : 'Rejected: ${x['feedback']}', style: const TextStyle(color: Colors.red)),
        )
      else if (s(x['feedback']).isNotEmpty)
        Text('Feedback: ${x['feedback']}'),
      const SizedBox(height: 12),
      Wrap(spacing: 8, children: [
        OutlinedButton.icon(onPressed: () => _copy(c, 'APK link', s(x['apkUrl'])), icon: const Icon(Icons.link_rounded, size: 18), label: const Text('Copy APK link')),
        OutlinedButton.icon(onPressed: () => _copy(c, 'Package name', s(x['packageName'])), icon: const Icon(Icons.copy_rounded, size: 18), label: const Text('Copy package')),
      ]),
      const SizedBox(height: 8),
      FilledButton.tonalIcon(
        onPressed: () { Navigator.pop(d); editSub(c, x, rl); },
        icon: const Icon(Icons.edit_rounded),
        label: Text(k == 'approved' ? 'Edit submission details (live app is not changed)' : 'Edit submission details'),
      ),
      const SizedBox(height: 8),
      OutlinedButton.icon(
        onPressed: () { Navigator.pop(d); openSecurityReport(c, x); },
        icon: const Icon(Icons.shield_moon_rounded),
        label: const Text('Security report (scan, signature, permissions)'),
      ),
      const SizedBox(height: 10),
      if (pending) Row(children: [
        Expanded(child: FilledButton(style: FilledButton.styleFrom(backgroundColor: Colors.green), onPressed: () { Navigator.pop(d); approveSub(c, x, rl); }, child: const Text('Approve'))),
        const SizedBox(width: 10),
        Expanded(child: FilledButton(style: FilledButton.styleFrom(backgroundColor: Colors.red), onPressed: () { Navigator.pop(d); rejectSub(c, x, rl); }, child: const Text('Reject'))),
      ]),
      if (rejected) ...[
        FilledButton.icon(
          style: FilledButton.styleFrom(backgroundColor: Colors.green),
          onPressed: () { Navigator.pop(d); approveSub(c, x, rl); },
          icon: const Icon(Icons.check_circle_outline),
          label: const Text('Approve anyway'),
        ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: () { Navigator.pop(d); reopenSub(c, x, rl); },
          icon: const Icon(Icons.undo_rounded),
          label: const Text('Move back to Pending'),
        ),
      ],
    ]),
  );
}

/// Frees the per-package lock so the developer can send the next update (same key rule as the apps).
Future<void> _releaseLock(String pkg) async {
  final k = pkg.trim().toLowerCase().replaceAll(RegExp(r'[^a-z0-9_-]'), '_');
  if (k.isEmpty) return;
  try {
    await Db.del('submissionLocks/${k.length > 150 ? k.substring(0, 150) : k}');
  } catch (_) {}
}

/// Things worth knowing before a REJECTED submission goes live (empty = nothing unusual).
Future<List<String>> _approveWarnings(Map<String, dynamic> x) async {
  final out = <String>[];
  try {
    final pkg = s(x['packageName']).toLowerCase();
    final code = ni(x['versionCode']);
    final subs = await Db.list('submissions');
    final newer = subs.where((y) =>
        s(y['id']) != s(x['id']) &&
        s(y['packageName']).toLowerCase() == pkg &&
        ni(y['createdAt']) > ni(x['createdAt']) &&
        _subState(y) != 'rejected').toList();
    if (newer.isNotEmpty) {
      out.add('The developer has sent ${newer.length} newer submission(s) for this package (${newer.map((y) => 'v${y['version']} ${_subState(y)}').join(', ')}). Approving this older one could replace them.');
    }
    final apps = await Db.list('apps');
    final live = apps.where((y) => s(y['packageName']).toLowerCase() == pkg).toList();
    if (live.isNotEmpty && code > 0 && ni(live.first['versionCode']) >= code) {
      out.add('The live app is already v${live.first['version']} (code ${live.first['versionCode']}). This would roll it back to v${x['version']} (code $code).');
    }
  } catch (_) {}
  return out;
}

Future<void> approveSub(BuildContext c, Map<String, dynamic> x, VoidCallback rl) async {
  final wasRejected = _subState(x) == 'rejected';
  var note = '';
  if (wasRejected) {
    final warns = await _approveWarnings(x);
    if (!c.mounted) return;
    final r = await editSheet(
      c,
      'Approve ${x['name']} after rejection?',
      const [F('feedback', 'Message to the developer (optional)', 'area')],
      {'feedback': ''},
      preview: (_) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('It was rejected${s(x['feedback']).isEmpty ? '' : ': ${x['feedback']}'}.\nApproving publishes it now and replaces the rejection with an approval.',
            style: const TextStyle(fontSize: 13)),
        for (final w in warns)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Icon(Icons.warning_amber_rounded, size: 18, color: Colors.orange),
              const SizedBox(width: 6),
              Expanded(child: Text(w, style: const TextStyle(fontSize: 12, color: Colors.orange))),
            ]),
          ),
      ]),
    );
    if (r == null || !c.mounted) return;
    note = s(r['feedback']).trim();
  } else {
    if (!await confirm(c, 'Approve ${x['name']}?', 'It goes live in the store now.')) return;
    if (!c.mounted) return;
  }
  await act(c, () async {
    // One server-side step: publishes / updates the app, appends the version
    // history, takes the developer name from users.dev_name and releases the package.
    await Sb.c.rpc('approve_submission', params: {'p_id': s(x['id']), 'p_feedback': note});
    await Db.audit(wasRejected ? 'APPROVE_AFTER_REJECT' : 'APPROVE_SUBMISSION', 'submission', s(x['id']), s(x['name']),
        wasRejected ? 'v${x['version']} (was rejected: ${s(x['feedback'])})' : 'v${x['version']}');
  }, rl, wasRejected ? 'Approved — it is live now' : 'Approved');
}

/// Puts a rejected submission back in the review queue without publishing it.
Future<void> reopenSub(BuildContext c, Map<String, dynamic> x, VoidCallback rl) async {
  if (!await confirm(c, 'Move ${x['name']} back to Pending?', 'It returns to the review queue. Nothing is published yet.')) return;
  if (!c.mounted) return;
  await act(c, () async {
    await Db.upd('submissions/${x['id']}', {'status': 'Pending'});
    await Db.audit('REOPEN_SUBMISSION', 'submission', s(x['id']), s(x['name']), 'was rejected: ${s(x['feedback'])}');
  }, rl, 'Moved to Pending');
}

Future<void> rejectSub(BuildContext c, Map<String, dynamic> x, VoidCallback rl) async {
  const none = '— write my own reason —';
  const templates = [
    none,
    'The app could not be installed or crashes on start.',
    'The app description or screenshots do not match the app.',
    'The app asks for permissions it does not need.',
    'The app looks like a copy of another app. Please submit your own work.',
    'The APK link does not work or is not a direct download.',
    'The app contains harmful or deceptive behaviour.',
  ];
  final r = await editSheet(c, 'Reject ${x['name']}',
      const [F('template', 'Quick reason', 'select', templates), F('feedback', 'Add your own message (optional)', 'area')], {'template': none});
  if (r == null || !c.mounted) return;
  final tpl = s(r['template']);
  final reason = [if (tpl.isNotEmpty && tpl != none) tpl, s(r['feedback']).trim()].where((e) => e.isNotEmpty).join('\n');
  if (reason.isEmpty) {
    toast(c, 'Please choose or write a reason so the developer knows what to fix.');
    return;
  }
  await act(c, () async {
    await Db.upd('submissions/${x['id']}', {'status': 'Rejected', 'feedback': reason});
    await _releaseLock(s(x['packageName']));
    await Db.audit('REJECT_SUBMISSION', 'submission', s(x['id']), s(x['name']), reason);
  }, rl, 'Rejected');
}

// ───────────── Live catalog ─────────────
const appFields = [
  F('name', 'Name'), F('category', 'Category'), F('version', 'Version'), F('versionCode', 'Version code', 'num'),
  F('size', 'Size'), F('description', 'Description', 'area'), F('changelog', 'Changelog', 'area'), F('apkUrl', 'APK URL'), F('logo', 'Logo URL', 'image'),
  F('screenshots', 'Screenshots (links, or add from device)', 'images'), F('videoUrl', 'Video URL'), F('expectedRelease', 'Expected release'),
  F('isFeatured', 'Featured', 'bool'), F('isPopular', 'Popular', 'bool'), F('isRecent', 'Recent', 'bool'), F('isPremium', 'Premium', 'bool'),
  F('isUpcoming', 'Upcoming', 'bool'), F('isOnHold', 'On hold (hidden, data kept)', 'bool'), F('hasAds', 'Contains ads', 'bool'),
];

Future<Widget> catalogTab(BuildContext c, VoidCallback rl) async {
  final L = await Db.list('apps');
  L.sort((a, b) => s(a['name']).toLowerCase().compareTo(s(b['name']).toLowerCase()));
  return Searchable(L, (x) => '${x['name']} ${x['packageName']} ${x['developer']}', (x) => Card(
        child: ListTile(
          leading: netImg(s(x['logo'])),
          title: Text(s(x['name'])),
          subtitle: Wrap(children: [
            Text('v${x['version']}  ', style: const TextStyle(fontSize: 12)),
            if (bo(x['isSuspended'])) tagChip('SUSPENDED', Colors.red),
            if (bo(x['isOnHold'])) tagChip('HOLD', Colors.orange),
            if (bo(x['isUpcoming'])) tagChip('UPCOMING', Colors.purple),
            if (!bo(x['isApproved'])) tagChip('NOT LIVE', Colors.grey),
            if (s(x['reportsJson']).isNotEmpty) tagChip('REPORTED', Colors.red),
          ]),
          trailing: PopupMenuButton<String>(
            onSelected: (m) => appMenu(c, m, x, rl),
            itemBuilder: (_) => [
              const PopupMenuItem(value: 'edit', child: Text('Edit + preview')),
              const PopupMenuItem(value: 'history', child: Text('Version history / rollback')),
              PopupMenuItem(value: 'susp', child: Text(bo(x['isSuspended']) ? 'Unsuspend' : 'Suspend')),
              const PopupMenuItem(value: 'del', child: Text('Delete')),
            ],
          ),
        ),
      ));
}

Future<void> appMenu(BuildContext c, String m, Map<String, dynamic> x, VoidCallback rl) async {
  final id = s(x['id']);
  if (m == 'edit') {
    final init = {...x, 'screenshots': shots(x['screenshots']).join(', ')};
    final r = await editSheet(c, 'Edit ${x['name']}', appFields, init, preview: appPrev);
    if (r == null || !c.mounted) return;
    await act(c, () async { await Db.upd('apps/$id', only(r, appFields)); await Db.audit('EDIT_APP', 'app', id, s(x['name'])); }, rl);
  } else if (m == 'susp') {
    if (bo(x['isSuspended'])) {
      await act(c, () async { await Db.upd('apps/$id', {'isSuspended': false, 'suspensionReason': ''}); await Db.audit('UNSUSPEND_APP', 'app', id, s(x['name'])); }, rl);
    } else {
      final r = await editSheet(c, 'Suspend ${x['name']}', const [F('reason', 'Reason (emailed to developer)', 'area')], {});
      if (r == null || !c.mounted) return;
      await act(c, () async { await Db.upd('apps/$id', {'isSuspended': true, 'suspensionReason': s(r['reason'])}); await Db.audit('SUSPEND_APP', 'app', id, s(x['name']), s(r['reason'])); }, rl);
    }
  } else if (m == 'del') {
    if (!await confirm(c, 'Delete ${x['name']}?', 'This removes it from the store permanently.') || !c.mounted) return;
    await act(c, () async { await Db.del('apps/$id'); await Db.audit('DELETE_APP', 'app', id, s(x['name'])); }, rl);
  } else if (m == 'history') {
    var h = <dynamic>[];
    try { h = jsonDecode(s(x['versionHistoryJson']).isEmpty ? '[]' : s(x['versionHistoryJson'])); } catch (_) {}
    if (!c.mounted) return;
    showModalBottomSheet(context: c, builder: (d) => ListView(padding: const EdgeInsets.all(12), children: [
      Text('Version history — ${x['name']}', style: Theme.of(c).textTheme.titleMedium),
      if (h.isEmpty) const Padding(padding: EdgeInsets.all(16), child: Text('No history recorded.')),
      for (final e in h.reversed)
        ListTile(
          title: Text('v${e['version']} (${e['versionCode']})'),
          subtitle: Text('${when(e['date'])}\n${s(e['changelog'])}'),
          trailing: TextButton(
            child: const Text('Restore'),
            onPressed: () async {
              Navigator.pop(d);
              if (!await confirm(c, 'Restore v${e['version']}?', 'Recorded as a normal update, so it can be undone.') || !c.mounted) return;
              h.add({...Map<String, dynamic>.from(e), 'date': ms()});
              await act(c, () async {
                await Db.upd('apps/$id', {'version': e['version'], 'versionCode': e['versionCode'], 'apkUrl': e['apkUrl'], 'changelog': s(e['changelog']), 'versionHistoryJson': jsonEncode(h)});
                await Db.audit('ROLLBACK_APP', 'app', id, s(x['name']), 'to v${e['version']}');
              }, rl);
            },
          ),
        ),
    ]));
  }
}

// ───────────── Push update / maintenance / premium ─────────────
const updFields = [F('latestVersionName', 'Latest version name'), F('latestVersionCode', 'Latest version code', 'num'), F('apkDownloadUrl', 'APK download URL'), F('updateTitle', 'Dialog title'), F('updateMessage', 'Dialog message', 'area'), F('forceUpdate', 'Force update (cannot be skipped)', 'bool'), F('minVersionCode', 'Minimum version code (older apps MUST update, 0 = off)', 'num'), F('releaseNotes', 'What is new (one point per line)', 'area'), F('remindHours', 'After "Later", ask again after (hours, 0 = every start)', 'num')];

Future<Widget> updateTab(BuildContext c, VoidCallback rl) async {
  final r = await Future.wait([Db.map('DarkStoreUpdate'), Db.map('maintenanceConfig'), Db.map('premiumConfig'), Db.map('app_policy')]);
  final u = r[0], m = r[1], p = r[2], pol = r[3];
  return listPage([
    Card(child: Padding(padding: const EdgeInsets.all(12), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Text('Push app update', style: TextStyle(fontWeight: FontWeight.bold)),
      Text('Latest: ${u['latestVersionName']} (${u['latestVersionCode']})  ${bo(u['forceUpdate']) ? '· FORCED' : ''}'),
      const SizedBox(height: 8), updatePrev(u),
      FilledButton(onPressed: () async {
        final x = await editSheet(c, 'Update dialog', updFields, u, preview: updatePrev);
        if (x == null || !c.mounted) return;
        await act(c, () async { await Db.upd('DarkStoreUpdate', only(x, updFields)); await Db.audit('PUSH_UPDATE', 'config', 'DarkStoreUpdate', 'Update config', 'v${x['latestVersionName']}'); }, rl);
      }, child: const Text('Edit & publish')),
    ]))),
    Card(child: Column(children: [
      ListTile(leading: const Icon(Icons.construction_rounded), title: const Text('Maintenance'), subtitle: Text(bo(m['isEnabled'] ?? m['enabled']) ? 'Switched ON. Open the Maintenance page to see times or end it.' : 'Off. Close the store now or on a schedule from the Maintenance page.')),
      SwitchListTile(title: const Text('Premium is free for everyone'), value: p['isFree'] != false,
          onChanged: (v) => act(c, () async { await Db.upd('premiumConfig', {'isFree': v}); await Db.audit('PREMIUM_FREE', 'config', 'premiumConfig', 'Premium', '$v'); }, rl)),
    ])),
    Card(child: ListTile(
      title: Text('Terms & policy · v${pol['version'] ?? '-'}'), subtitle: const Text('Changing text/version makes everyone accept again'), trailing: const Icon(Icons.edit),
      onTap: () async {
        const f = [F('title', 'Title'), F('version', 'Version'), F('content', 'Full text', 'area')];
        final x = await editSheet(c, 'Terms & Community Agreement', f, pol);
        if (x == null || !c.mounted) return;
        await act(c, () async { await Db.upd('app_policy', {...only(x, f), 'updatedAt': DateTime.now().toIso8601String().substring(0, 10)}); await Db.audit('EDIT_POLICY', 'config', 'app_policy', 'Policy', 'v${x['version']}'); }, rl);
      },
    )),
  ]);
}

// ───────────── Users ─────────────
Future<Widget> usersTab(BuildContext c, VoidCallback rl) async {
  final L = await Db.list('users');
  L.sort((a, b) => ni(b['createdAt']).compareTo(ni(a['createdAt'])));
  return Searchable(L, (x) => '${x['displayName']} ${x['userName']} ${x['email']} ${x['devName']}', (x) => Card(
        child: ListTile(
          leading: netImg(s(x['profilePhotoUrl']), r: 22),
          title: Text(s(x['displayName']).isEmpty ? s(x['userName']) : s(x['displayName'])),
          subtitle: Wrap(children: [
            Text('${x['email']}  ', style: const TextStyle(fontSize: 12)),
            if (x['role'] == 'admin') tagChip('ADMIN', Colors.orange),
            if (bo(x['isDeveloper'])) tagChip('DEV', Colors.blue),
            if (bo(x['isPremiumMember'])) tagChip('PREMIUM', Colors.amber),
            if (bo(x['isSuspended'])) tagChip('SUSPENDED', Colors.red),
          ]),
          trailing: PopupMenuButton<String>(
            onSelected: (m) => userMenu(c, m, x, rl),
            itemBuilder: (_) => const [
              PopupMenuItem(value: 'edit', child: Text('Edit / suspend')),
              PopupMenuItem(value: 'devices', child: Text('Devices & fraud')),
              PopupMenuItem(value: 'notify', child: Text('Send notification')),
              PopupMenuItem(value: 'grant', child: Text('Grant Premium')),
              PopupMenuItem(value: 'revoke', child: Text('Remove Premium')),
              PopupMenuItem(value: 'delete', child: Text('Delete account', style: TextStyle(color: Colors.red))),
            ],
          ),
        ),
      ));
}

Future<void> userMenu(BuildContext c, String m, Map<String, dynamic> x, VoidCallback rl) async {
  final id = s(x['id']), nm = s(x['email']);
  if (m == 'edit') {
    const f = [F('role', 'Role', 'select', ['user', 'admin']), F('isDeveloper', 'Developer', 'bool'), F('isSuspended', 'Suspended (cannot sign in)', 'bool'), F('suspensionReason', 'Suspension reason shown to user', 'area')];
    final r = await editSheet(c, 'Edit $nm', f, {...x, 'role': x['role'] == 'admin' ? 'admin' : 'user'});
    if (r == null || !c.mounted) return;
    await act(c, () async { await Db.upd('users/$id', only(r, f)); await Db.audit('EDIT_USER', 'user', id, nm, 'role=${r['role']} suspended=${r['isSuspended']}'); }, rl);
  } else if (m == 'delete') {
    if (x['role'] == 'admin') return toast(c, 'This is an admin. Remove the admin role first (Edit / suspend).');
    final r = await editSheet(c, 'Delete $nm', const [F('deleteApps', "Also delete this user's apps", 'bool')], {'deleteApps': false});
    if (r == null || !c.mounted) return;
    if (!await confirm(c, 'Delete $nm permanently?', 'Profile, chats, reviews, followers, devices and the login account are removed. This cannot be undone.') || !c.mounted) return;
    try {
      final res = await Api.call('/admin/delete-user', body: {'uid': id, 'deleteApps': bo(r['deleteApps'])});
      if (res != null && res['ok'] == true) {
        await Db.audit('DELETE_USER', 'user', id, nm, 'apps=${bo(r['deleteApps'])}');
        if (c.mounted) toast(c, 'Account deleted${s(res['authError']).isEmpty ? '' : ' (login account: ${res['authError']})'}');
      } else if (res != null && s(res['error']).isNotEmpty && s(res['error']) != 'admin only') {
        throw s(res['error']);
      } else {
        // server offline or not accepting this admin: the database itself checks that you are an admin
        final out = await Sb.c.rpc('admin_delete_user', params: {'p_uid': id, 'p_delete_apps': bo(r['deleteApps'])});
        await Db.audit('DELETE_USER', 'user', id, nm, 'data only (server unavailable)');
        if (c.mounted) {
          toast(c, out is Map && out['ok'] == true
              ? 'Data deleted. The server is not reachable as admin, so also delete this login in Firebase Console > Authentication.'
              : 'Nothing was deleted (no profile row). Delete the login in Firebase Console > Authentication.');
        }
      }
    } catch (e) {
      if (c.mounted) toast(c, 'Failed: $e');
    }
    rl();
  } else if (m == 'devices') {
    await showUserSecurity(c, id, rl);
  } else if (m == 'notify') {
    const f = [F('title', 'Title'), F('message', 'Message', 'area')];
    final r = await editSheet(c, 'Notify $nm', f, {}, preview: noticePrev);
    if (r == null || !c.mounted) return;
    final res = await Api.call('/admin/send', body: {'audience': 'selected', 'title': s(r['title']), 'message': s(r['message']), 'imageUrl': '', 'uids': [id]});
    if (c.mounted) toast(c, res == null ? "Couldn't reach the server" : res['ok'] == true ? 'Delivered: ${res['delivered']}, no token: ${res['no_token']}' : 'Not sent: ${res['error']}');
  } else if (m == 'grant') {
    final r = await editSheet(c, 'Grant Premium', const [F('days', 'Days (0 = lifetime)', 'num')], {'days': 30});
    if (r == null || !c.mounted) return;
    try {
      await Sb.c.rpc('admin_set_premium', params: {'p_uid': id, 'p_enabled': true, 'p_days': ni(r['days'])});
      if (c.mounted) toast(c, 'Premium granted');
      await Db.audit('GRANT_PREMIUM', 'user', id, nm, '${r['days']} days');
    } catch (e) {
      if (c.mounted) toast(c, 'Failed: $e');
    }
    rl();
  } else if (m == 'revoke') {
    try {
      await Sb.c.rpc('admin_set_premium', params: {'p_uid': id, 'p_enabled': false, 'p_days': 0});
      if (c.mounted) toast(c, 'Premium removed');
      await Db.audit('REMOVE_PREMIUM', 'user', id, nm);
    } catch (e) {
      if (c.mounted) toast(c, 'Failed: $e');
    }
    rl();
  }
}

// ───────────── Notify ─────────────
class NotifyTab extends StatefulWidget {
  const NotifyTab({super.key});
  @override
  State<NotifyTab> createState() => _NotifyTabState();
}

class _NotifyTabState extends State<NotifyTab> {
  final v = <String, dynamic>{'audience': 'everyone', 'title': '', 'message': '', 'imageUrl': ''};
  final sel = <String>{};
  final _img = TextEditingController();
  bool _imgBusy = false;
  List<Map<String, dynamic>> users = [];
  bool busy = false;
  @override
  void initState() {
    super.initState();
    Db.list('users').then((l) => mounted ? setState(() => users = l) : null);
  }

  @override
  Widget build(BuildContext c) {
    Widget tf(String k, String l, {int lines = 1}) => Padding(padding: const EdgeInsets.only(top: 10), child: TextField(minLines: lines, maxLines: lines + 3, decoration: InputDecoration(labelText: l, border: const OutlineInputBorder()), onChanged: (x) => setState(() => v[k] = x)));
    return listPage([
      DropdownButtonFormField<String>(
        value: s(v['audience']),
        decoration: const InputDecoration(labelText: 'Send to', border: OutlineInputBorder()),
        items: [for (final a in ['everyone', 'developers', 'users', 'admins', 'selected']) DropdownMenuItem(value: a, child: Text(a))],
        onChanged: (x) => setState(() => v['audience'] = x),
      ),
      if (v['audience'] == 'selected')
        ListTile(title: Text('${sel.length} people selected'), trailing: const Icon(Icons.group_add), onTap: pick),
      tf('title', 'Title'), tf('message', 'Message', lines: 2),
      Padding(
        padding: const EdgeInsets.only(top: 10),
        child: TextField(
          controller: _img,
          decoration: InputDecoration(
            labelText: 'Image (optional): link or pick from device',
            border: const OutlineInputBorder(),
            suffixIcon: _imgBusy
                ? const Padding(padding: EdgeInsets.all(12), child: SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2)))
                : IconButton(icon: const Icon(Icons.add_photo_alternate_outlined), onPressed: _pickImg),
          ),
          onChanged: (x) => setState(() => v['imageUrl'] = x),
        ),
      ),
      const SizedBox(height: 12),
      const Text('PREVIEW', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
      noticePrev(v),
      const SizedBox(height: 12),
      FilledButton.icon(onPressed: busy ? null : send, icon: const Icon(Icons.send), label: Text(busy ? 'Sending…' : 'Send push')),
    ]);
  }

  Future<void> _pickImg() async {
    setState(() => _imgBusy = true);
    try {
      final f = await Uploader.one();
      if (f != null) {
        final u = await Uploader.upload(f, folder: 'notifications');
        _img.text = u;
        v['imageUrl'] = u;
      }
    } catch (e) {
      if (mounted) toast(context, '$e');
    }
    if (mounted) setState(() => _imgBusy = false);
  }

  void pick() {
    showModalBottomSheet(context: context, isScrollControlled: true, useSafeArea: true, builder: (d) => StatefulBuilder(builder: (d, ss) => ListView(children: [
      for (final u in users) CheckboxListTile(value: sel.contains(u['id']), title: Text(s(u['displayName']).isEmpty ? s(u['userName']) : s(u['displayName'])), subtitle: Text(s(u['email'])), onChanged: (x) { ss(() => x == true ? sel.add(s(u['id'])) : sel.remove(s(u['id']))); setState(() {}); }),
    ])));
  }

  Future<void> send() async {
    if (s(v['title']).trim().isEmpty || s(v['message']).trim().isEmpty) return toast(context, 'Title and message are required');
    if (v['audience'] == 'selected' && sel.isEmpty) return toast(context, 'Pick at least one person');
    if (!await confirm(context, 'Send to ${v['audience']}?', '“${v['title']}”\n\n${v['message']}') || !mounted) return;
    setState(() => busy = true);
    final res = await Api.call('/admin/send', body: {'audience': v['audience'], 'title': s(v['title']).trim(), 'message': s(v['message']).trim(), 'imageUrl': s(v['imageUrl']).trim(), 'uids': sel.toList()});
    if (!mounted) return;
    setState(() => busy = false);
    if (res == null) return toast(context, "Couldn't reach the server — check the Server tab");
    if (res['ok'] != true) return toast(context, 'Not sent: ${res['error']}');
    toast(context, 'Delivered ${res['delivered'] ?? 'to all'} · failed ${res['failed'] ?? 0} · no token ${res['no_token'] ?? 0}');
    await Db.audit('NOTIFICATION_SENT', 'notification', s(v['audience']), s(v['title']));
    _img.clear();
    setState(() { v['title'] = ''; v['message'] = ''; v['imageUrl'] = ''; });
  }
}

// ───────────── Events (new) ─────────────
const evFields = [
  F('enabled', 'Event is ON', 'bool'), F('id', 'Event id (change it to show again to everyone)'), F('title', 'Title'), F('message', 'Message', 'area'),
  F('mediaType', 'Media', 'select', ['none', 'image', 'video']), F('mediaUrl', 'Image / YouTube link / .mp4 (or pick a photo)', 'image'), F('buttonText', 'Button text'),
  F('targetType', 'Button opens', 'select', ['none', 'app', 'link']), F('targetValue', 'App id or https link'),
  F('startAt', 'Starts at', 'date'), F('endAt', 'Ends at', 'date'), F('showOnce', 'Show only once per device', 'bool'),
];

Future<Widget> eventsTab(BuildContext c, VoidCallback rl) async {
  final e = await Db.map('eventConfig');
  final now = ms();
  final live = bo(e['enabled']) && (ni(e['startAt']) == 0 || now >= ni(e['startAt'])) && (ni(e['endAt']) == 0 || now <= ni(e['endAt']));
  Future<void> save(Map<String, dynamic> r, String what) => act(c, () async { await Db.upd('eventConfig', only(r, evFields)); await Db.audit('EVENT', 'config', 'eventConfig', s(r['title']), what); }, rl);
  return listPage([
    Row(children: [tagChip(bo(e['enabled']) ? (live ? 'LIVE NOW' : 'ON · outside schedule') : 'OFF', live ? Colors.green : Colors.grey), Text(' id: ${e['id'] ?? '-'}', style: const TextStyle(fontSize: 12))]),
    Text('Window: ${when(e['startAt'])} → ${when(e['endAt'])}', style: const TextStyle(fontSize: 12, color: Colors.grey)),
    const SizedBox(height: 8),
    const Text('PREVIEW (what users see at launch)', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
    eventPrev(e),
    const SizedBox(height: 10),
    Wrap(spacing: 8, children: [
      FilledButton.icon(icon: const Icon(Icons.edit), label: const Text('Edit event'), onPressed: () async { final r = await editSheet(c, 'Event popup', evFields, {'showOnce': true, 'mediaType': 'none', 'targetType': 'none', ...e}, preview: eventPrev); if (r != null && c.mounted) save(r, 'edited'); }),
      OutlinedButton.icon(icon: const Icon(Icons.power_settings_new), label: Text(bo(e['enabled']) ? 'Turn off' : 'Turn on'), onPressed: () => save({...e, 'enabled': !bo(e['enabled'])}, 'toggle')),
      OutlinedButton.icon(icon: const Icon(Icons.replay), label: const Text('Show again to all'), onPressed: () => save({...e, 'id': 'ev-${ms()}'}, 'new id')),
      OutlinedButton.icon(icon: const Icon(Icons.delete_outline), label: const Text('Clear'), onPressed: () async { if (await confirm(c, 'Clear event?', 'Removes the whole popup config.') && c.mounted) act(c, () async { await Db.del('eventConfig'); await Db.audit('EVENT', 'config', 'eventConfig', 'Event', 'cleared'); }, rl); }),
    ]),
  ]);
}

// ───────────── Banners / Collections (CRUD with preview) ─────────────
Future<Widget> Function(BuildContext, VoidCallback) crud(String path, List<F> f, Widget Function(Map<String, dynamic>) prev, String Function(Map<String, dynamic>) title,
    {Map<String, dynamic> Function(Map<String, dynamic>)? toForm, Map<String, Object?> Function(Map<String, dynamic>)? fromForm}) {
  return (c, rl) async {
    final L = await Db.list(path);
    L.sort((a, b) => ni(a['order']).compareTo(ni(b['order'])));
    Future<void> edit([Map<String, dynamic>? x]) async {
      final init = toForm != null ? toForm(x ?? {'isActive': true}) : (x ?? {'isActive': true});
      final r = await editSheet(c, x == null ? 'New' : 'Edit', f, init, preview: prev);
      if (r == null || !c.mounted) return;
      final data = fromForm != null ? fromForm(r) : only(r, f);
      await act(c, () async {
        if (x == null) { await Db.push(path, {...data, 'createdAt': ms()}); } else { await Db.upd('$path/${x['id']}', data); }
        await Db.audit(x == null ? 'CREATE_${path.toUpperCase()}' : 'EDIT_${path.toUpperCase()}', path, s(x?['id']), s(r['title']));
      }, rl);
    }
    return Stack(children: [
      listPage([
        for (final x in L)
          Card(child: ListTile(
            title: Text(title(x)), subtitle: Text('order ${x['order'] ?? 0}'),
            leading: Switch(value: bo(x['isActive']), onChanged: (v) => act(c, () => Db.upd('$path/${x['id']}', {'isActive': v}), rl)),
            trailing: IconButton(icon: const Icon(Icons.delete_outline), onPressed: () async { if (await confirm(c, 'Delete?', title(x)) && c.mounted) act(c, () async { await Db.del('$path/${x['id']}'); await Db.audit('DELETE_${path.toUpperCase()}', path, s(x['id']), title(x)); }, rl); }),
            onTap: () => edit(x),
          )),
        const SizedBox(height: 70),
      ]),
      Positioned(right: 16, bottom: 16, child: FloatingActionButton(onPressed: () => edit(), child: const Icon(Icons.add))),
    ]);
  };
}

final bannersTab = crud('banners', const [F('title', 'Title'), F('subtitle', 'Subtitle'), F('imageUrl', 'Image', 'image'), F('buttonText', 'Button text'), F('targetType', 'Button opens', 'select', ['none', 'app', 'link']), F('targetValue', 'App id or link'), F('order', 'Order', 'num'), F('isActive', 'Active', 'bool')], bannerPrev, (x) => s(x['title']));

final collectionsTab = crud(
  'collections',
  const [F('title', 'Title'), F('titleNe', 'Title (Nepali)'), F('titleHi', 'Title (Hindi)'), F('subtitle', 'Subtitle'), F('subtitleNe', 'Subtitle (Nepali)'), F('subtitleHi', 'Subtitle (Hindi)'), F('color', 'Colour hex e.g. #10B981'), F('appIdsText', 'App ids (comma separated)', 'area'), F('order', 'Order', 'num'), F('isActive', 'Active', 'bool')],
  collPrev, (x) => s(x['title']),
  toForm: (x) => {...x, 'appIdsText': x['appIds'] is List ? (x['appIds'] as List).join(', ') : s(x['appIds'])},
  fromForm: (r) => {
    for (final k in ['title', 'titleNe', 'titleHi', 'subtitle', 'subtitleNe', 'subtitleHi', 'color', 'order', 'isActive']) k: r[k],
    'appIds': s(r['appIdsText']).split(',').map((e) => e.trim()).where((e) => e.isNotEmpty).toList(),
  },
);

// ───────────── Ads ─────────────
Future<Widget> adsTab(BuildContext c, VoidCallback rl) async {
  final a = await Db.map('unityAdsConfig');
  return listPage([
    Card(child: Column(children: [
      SwitchListTile(title: const Text('Unity Ads banner ON'), subtitle: const Text('Shown in the home promo slot'), value: bo(a['enabled']), onChanged: (v) => act(c, () async { await Db.upd('unityAdsConfig', {'enabled': v}); await Db.audit('ADS', 'config', 'unityAdsConfig', 'Unity Ads', 'enabled=$v'); }, rl)),
      SwitchListTile(title: const Text('Test mode'), value: bo(a['testMode']), onChanged: (v) => act(c, () async { await Db.upd('unityAdsConfig', {'testMode': v}); await Db.audit('ADS', 'config', 'unityAdsConfig', 'Unity Ads', 'testMode=$v'); }, rl)),
    ])),
  ]);
}

// ───────────── Feedback & reports ─────────────
Future<Widget> feedbackTab(BuildContext c, VoidCallback rl) async {
  final r = await Future.wait([Db.list('darkstoreReports'), Db.list('apps')]);
  r[0].sort((a, b) => ni(b['createdAt']).compareTo(ni(a['createdAt'])));
  final rep = r[1].where((a) => s(a['reportsJson']).isNotEmpty).toList();
  return listPage([
    const Text('Reports on apps', style: TextStyle(fontWeight: FontWeight.bold)),
    if (rep.isEmpty) const Padding(padding: EdgeInsets.all(8), child: Text('None')),
    for (final a in rep)
      Card(child: ListTile(leading: netImg(s(a['logo'])), title: Text(s(a['name'])), subtitle: Text(s(a['reportsJson']).replaceAll('||', '\n')), isThreeLine: true,
          trailing: Wrap(children: [
            IconButton(icon: const Icon(Icons.check), tooltip: 'Dismiss', onPressed: () => act(c, () async { await Db.upd('apps/${a['id']}', {'reportsJson': ''}); await Db.audit('DISMISS_REPORTS', 'app', s(a['id']), s(a['name'])); }, rl)),
            IconButton(icon: const Icon(Icons.block), tooltip: 'Suspend', onPressed: () => appMenu(c, 'susp', a, rl)),
          ]))),
    const SizedBox(height: 12),
    const Text('Problem reports from users', style: TextStyle(fontWeight: FontWeight.bold)),
    if (r[0].isEmpty) const Padding(padding: EdgeInsets.all(8), child: Text('None')),
    for (final x in r[0])
      Card(child: ListTile(
        title: Text('${x['category']} · ${x['reporterName'] ?? x['reporterEmail'] ?? ''}'),
        subtitle: Text('${x['message']}\n${x['reporterEmail'] ?? ''} · v${x['appVersion'] ?? ''} · ${x['device'] ?? ''} · ${when(x['createdAt'])}'), isThreeLine: true,
        trailing: IconButton(icon: const Icon(Icons.delete_outline), onPressed: () => act(c, () => Db.del('darkstoreReports/${x['id']}'), rl)),
      )),
  ]);
}

// ───────────── Audit ─────────────
Future<Widget> auditTab(BuildContext c, VoidCallback rl) async {
  final L = await Db.list('auditLog');
  L.sort((a, b) => ni(b['timestamp'] ?? b['ts']).compareTo(ni(a['timestamp'] ?? a['ts'])));
  return Searchable(L.take(400).toList(), (x) => '${x['action']} ${x['targetName']} ${x['adminEmail']}', (x) => Card(
        child: ListTile(dense: true, title: Text('${x['action']} · ${x['targetName'] ?? x['target'] ?? ''}'), subtitle: Text('${x['adminEmail'] ?? x['admin'] ?? ''} · ${when(x['timestamp'] ?? x['ts'])}${s(x['details']).isEmpty ? '' : '\n${x['details']}'}')),
      ));
}

// ───────────── Server ─────────────
Future<Widget> serverTab(BuildContext c, VoidCallback rl) async {
  final r = await Future.wait([Api.call('/health', auth: false, method: 'GET'), Api.call('/status', method: 'GET'), Api.call('/admin/apk/status', body: {}), Api.base()]);
  final h = r[0] as Map<String, dynamic>?, st = r[1] as Map<String, dynamic>?, apk = r[2] as Map<String, dynamic>?;
  Future<void> run(String path, String name) async {
    toast(c, '$name…');
    final x = await Api.call(path);
    if (c.mounted) toast(c, x == null ? "Couldn't reach the server" : x['ok'] == true ? '$name OK  ${x.entries.where((e) => e.key != 'ok').map((e) => '${e.key}=${e.value}').join(' ')}' : '$name failed: ${x['error']}');
    await Db.audit('SERVER_ACTION', 'config', path, name);
    rl();
  }
  Widget row(String a, Object? b) => Padding(padding: const EdgeInsets.symmetric(vertical: 2), child: Row(children: [Expanded(child: Text(a, style: const TextStyle(color: Colors.grey))), Text('$b')]));
  return listPage([
    Card(child: Padding(padding: const EdgeInsets.all(12), child: Column(children: [
      Row(children: [Icon(Icons.circle, size: 12, color: h?['ok'] == true ? Colors.green : Colors.red), const SizedBox(width: 8), Text(h?['ok'] == true ? 'Server online' : 'Server unreachable', style: const TextStyle(fontWeight: FontWeight.bold))]),
      row('URL', r[3]), if (h != null) row('Version', h['version']), if (h != null) row('Uptime', '${(ni(h['uptime_s']) / 3600).toStringAsFixed(1)} h'),
      if (st != null) ...[row('Pushes today', (st['today']?['sent'] as Map?)?.values.fold<int>(0, (a, b) => a + ni(b)) ?? 0), row('Pushes total', st['total']?['sent']), row('Last error', s(st['last_error']).isEmpty ? 'none' : st['last_error']), row('Users', st['users']), row('Pending submissions', st['tracked']?['pending_submissions'])],
    ]))),
    Wrap(spacing: 8, children: [
      OutlinedButton(onPressed: () => run('/admin/test-push', 'Test push'), child: const Text('Test push to me')),
      OutlinedButton(onPressed: () => run('/admin/backfill', 'Backfill users'), child: const Text('Backfill users')),
      OutlinedButton(onPressed: () => run('/admin/fraud-scan', 'Fraud scan'), child: const Text('Fraud scan')),
      OutlinedButton(onPressed: () async {
        try {
          final n = await Sb.c.rpc('premium_sweep');
          if (c.mounted) toast(c, 'Premium sweep: $n expired');
          await Db.audit('SERVER_ACTION', 'config', 'premium_sweep', 'Premium sweep', '$n expired');
        } catch (e) {
          if (c.mounted) toast(c, 'Failed: $e');
        }
        rl();
      }, child: const Text('Premium sweep')),
      OutlinedButton(onPressed: () async { final x = await editSheet(c, 'Server URL', const [F('baseUrl', 'https://…bot-keep.xyz')], {'baseUrl': r[3]}); if (x != null && c.mounted) act(c, () => Db.put('notifierConfig/baseUrl', s(x['baseUrl']).trim()), rl); }, child: const Text('Change server URL')),
    ]),
    const SizedBox(height: 10),
    Card(child: Padding(padding: const EdgeInsets.all(12), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Text('APK storage', style: TextStyle(fontWeight: FontWeight.bold)),
      Text(apk == null ? 'No answer' : const JsonEncoder.withIndent(' ').convert(apk), style: const TextStyle(fontSize: 11, fontFamily: 'monospace')),
      OutlinedButton(onPressed: () async {
        const f = [F('enabled', 'Uploads enabled', 'bool'), F('maxFileMB', 'Max file MB', 'num'), F('perUserFiles', 'Files per user', 'num'), F('perUserMB', 'MB per user', 'num'), F('baseUrl', 'Storage base URL (blank = keep)'), F('apiKey', 'API key (blank = keep)')];
        final x = await editSheet(c, 'APK storage config', f, {'enabled': apk?['enabled'] ?? true, 'maxFileMB': apk?['maxFileMB'] ?? 100, 'perUserFiles': apk?['perUserFiles'] ?? 5, 'perUserMB': apk?['perUserMB'] ?? 300});
        if (x == null || !c.mounted) return;
        final body = <String, dynamic>{'enabled': bo(x['enabled']), 'maxFileMB': ni(x['maxFileMB']), 'perUserFiles': ni(x['perUserFiles']), 'perUserMB': ni(x['perUserMB'])};
        if (s(x['baseUrl']).trim().isNotEmpty) body['baseUrl'] = s(x['baseUrl']).trim();
        if (s(x['apiKey']).trim().isNotEmpty) body['apiKey'] = s(x['apiKey']).trim();
        final res = await Api.call('/admin/apk/config', body: body);
        if (c.mounted) toast(c, res?['ok'] == true ? 'Saved' : 'Failed: ${res?['error'] ?? 'no answer'}');
        await Db.audit('APK_STORAGE_CONFIG', 'config', 'apk', 'APK storage');
        rl();
      }, child: const Text('Edit config')),
    ]))),
  ]);
}

String me() => FirebaseAuth.instance.currentUser?.email ?? '';
