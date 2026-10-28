import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'core.dart';

// ─────────────────────────────────────────────────────────────────────────────
// Security: fraud alerts + device / account map.
//
// Firebase nodes read here (all written by the user app / notifier already):
//   fraudAlerts/<id>            type, severity, status, autoAction, uid, relatedUid, details…
//   fraudStrikes/<uid>          number of strikes per account
//   deviceRegistry/<hash>/<uid> first-seen time of that account on that phone
//   deviceOwner/<hash>          uid that owns the phone (first account)
//   userDevices/<uid>/<hash>    same link, seen from the account side
//   users/<uid>                 isSuspended, suspensionReason, role, email…
// ─────────────────────────────────────────────────────────────────────────────

/// Everything the Security tabs need, loaded once per refresh.
class _Sec {
  final Map<String, Map<String, dynamic>> users;
  final List<Map<String, dynamic>> alerts;
  final Map<String, int> strikes;
  final Map<String, Map<String, int>> devices; // device hash -> (uid -> first seen)
  final Map<String, String> owner; // device hash -> owner uid
  _Sec(this.users, this.alerts, this.strikes, this.devices, this.owner);

  Map<String, dynamic>? user(String uid) => users[uid];
  bool suspended(String uid) => bo(users[uid]?['isSuspended']);
  bool isAdminUid(String uid) => s(users[uid]?['role']) == 'admin';
  int strikeOf(String uid) => strikes[uid] ?? 0;

  String name(String uid) {
    final u = users[uid];
    if (u == null) return uid.isEmpty ? '-' : 'Deleted user';
    final n = s(u['displayName']).isNotEmpty ? s(u['displayName']) : s(u['userName']);
    return n.isNotEmpty ? n : (s(u['email']).isNotEmpty ? s(u['email']) : uid);
  }

  String email(String uid) => s(users[uid]?['email']);

  /// Device hashes this account has logged in on.
  List<String> devicesOf(String uid) => [for (final e in devices.entries) if (e.value.containsKey(uid)) e.key];

  /// Accounts on a device, owner first, then oldest first.
  List<String> accountsOn(String hash) {
    final m = devices[hash] ?? const <String, int>{};
    final l = m.keys.toList()..sort((a, b) => m[a]!.compareTo(m[b]!));
    final o = owner[hash];
    if (o != null && l.remove(o)) l.insert(0, o);
    return l;
  }
}

Future<_Sec> _load() async {
  final r = await Future.wait<Object>([
    Db.list('users'),
    Db.list('fraudAlerts'),
    Db.map('fraudStrikes'),
    Db.map('deviceRegistry'),
    Db.map('deviceOwner'),
    Db.map('userDevices'),
  ]);
  final users = {for (final u in r[0] as List<Map<String, dynamic>>) s(u['id']): u};
  final alerts = r[1] as List<Map<String, dynamic>>;
  final strikes = {for (final e in (r[2] as Map<String, dynamic>).entries) e.key: ni(e.value)};
  final dev = <String, Map<String, int>>{};
  (r[3] as Map<String, dynamic>).forEach((h, v) {
    if (v is Map) v.forEach((u, t) => (dev[h] ??= {})['$u'] = ni(t));
  });
  // The account-side copy fills any gap in the registry.
  (r[5] as Map<String, dynamic>).forEach((u, v) {
    if (v is Map) v.forEach((h, t) => (dev['$h'] ??= {}).putIfAbsent(u, () => ni(t)));
  });
  final owner = {for (final e in (r[4] as Map<String, dynamic>).entries) e.key: s(e.value)};
  return _Sec(users, alerts, strikes, dev, owner);
}

String _short(String h) => h.length > 14 ? '${h.substring(0, 7)}…${h.substring(h.length - 5)}' : h;

Color _sevColor(String sv) => switch (sv.toUpperCase()) {
      'HIGH' || 'CRITICAL' => Colors.red,
      'MEDIUM' => Colors.orange,
      _ => Colors.blueGrey,
    };

Widget _statCard(String label, Object value, [Color? col]) => SizedBox(
      width: 105,
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('$value', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800, color: col)),
            Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
          ]),
        ),
      ),
    );

Widget _chips(List<(String, String)> opts, String cur, void Function(String) on) => SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(children: [
        for (final o in opts)
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: ChoiceChip(label: Text(o.$2), selected: cur == o.$1, onSelected: (_) => on(o.$1)),
          ),
      ]),
    );

Widget _search(void Function(String) on) => TextField(
      decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Search name, email, device…', border: OutlineInputBorder(), isDense: true),
      onChanged: on,
    );

/// Status tags for one account (suspended / admin / strikes / device owner).
Widget _userTags(_Sec d, String uid, {bool owner = false}) => Wrap(runSpacing: 2, children: [
      if (owner) tagChip('OWNER', Colors.blue),
      if (d.suspended(uid)) tagChip('SUSPENDED', Colors.red),
      if (d.isAdminUid(uid)) tagChip('ADMIN', Colors.orange),
      if (d.strikeOf(uid) > 0) tagChip('${d.strikeOf(uid)} STRIKE${d.strikeOf(uid) == 1 ? '' : 'S'}', Colors.deepOrange),
      if (d.user(uid) == null) tagChip('DELETED', Colors.grey),
    ]);

// ───────────── Actions ─────────────

Future<void> _setSuspended(BuildContext c, _Sec d, String uid, bool on, VoidCallback rl, {String reason = ''}) async {
  final label = d.email(uid).isNotEmpty ? d.email(uid) : uid;
  if (on && d.isAdminUid(uid)) return toast(c, 'This account is an admin. Remove the admin role in Users first.');
  var why = reason;
  if (on) {
    final r = await editSheet(c, 'Suspend $label', const [F('reason', 'Reason shown to the user', 'area')], {'reason': reason});
    if (r == null || !c.mounted) return;
    why = s(r['reason']);
  } else if (!await confirm(c, 'Unsuspend $label?', 'They can sign in and use the app again.') || !c.mounted) {
    return;
  }
  await act(c, () async {
    await Db.upd('users/$uid', {'isSuspended': on, 'suspensionReason': on ? why : ''});
    await Db.audit(on ? 'SUSPEND_USER' : 'UNSUSPEND_USER', 'user', uid, label, on ? 'security: $why' : 'security');
  }, rl, on ? 'Suspended' : 'Unsuspended');
}

Future<void> _suspendOthers(BuildContext c, _Sec d, String hash, VoidCallback rl) async {
  final o = d.owner[hash];
  final targets = [for (final u in d.accountsOn(hash)) if (u != o && !d.suspended(u) && !d.isAdminUid(u)) u];
  if (targets.isEmpty) return toast(c, 'Nothing to suspend on this device');
  if (!await confirm(c, 'Suspend ${targets.length} account(s)?', 'Every account on this phone except the owner (${d.email(o ?? '').isEmpty ? 'unknown' : d.email(o ?? '')}) will be suspended.') || !c.mounted) return;
  await act(c, () async {
    for (final u in targets) {
      await Db.upd('users/$u', {'isSuspended': true, 'suspensionReason': 'Multiple accounts on one device'});
    }
    await Db.audit('SUSPEND_DEVICE_ACCOUNTS', 'device', hash, _short(hash), '${targets.length} accounts');
  }, rl, 'Suspended ${targets.length}');
}

Future<void> _setAlertStatus(BuildContext c, Map<String, dynamic> a, String status, VoidCallback rl) => act(c, () async {
      await Db.upd('fraudAlerts/${a['id']}', {'status': status, 'reviewedBy': FirebaseAuth.instance.currentUser?.email ?? '', 'reviewedAt': ms()});
      await Db.audit('FRAUD_ALERT_${status.toUpperCase()}', 'fraudAlert', s(a['id']), s(a['email']), s(a['type']));
    }, rl, status == 'open' ? 'Re-opened' : 'Dismissed');

// ───────────── Account rows + sheets ─────────────

Widget _acctTile(BuildContext sheet, BuildContext tab, _Sec d, String uid, VoidCallback rl, {int ts = 0, bool owner = false, bool showDevices = false}) {
  final sus = d.suspended(uid);
  return ListTile(
    dense: true,
    contentPadding: EdgeInsets.zero,
    leading: netImg(s(d.user(uid)?['profilePhotoUrl']), w: 36, h: 36, r: 18),
    title: Text(d.name(uid), maxLines: 1, overflow: TextOverflow.ellipsis),
    subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('${d.email(uid)}${ts > 0 ? '\nfirst seen ${when(ts)}' : ''}', style: const TextStyle(fontSize: 11)),
      _userTags(d, uid, owner: owner),
    ]),
    isThreeLine: true,
    trailing: PopupMenuButton<String>(
      onSelected: (m) {
        if (m == 'dev') {
          Navigator.pop(sheet);
          _openUserDevices(tab, d, uid, rl);
        } else {
          Navigator.pop(sheet);
          _setSuspended(tab, d, uid, !sus, rl, reason: 'Fraud / multiple accounts');
        }
      },
      itemBuilder: (_) => [
        PopupMenuItem(value: 'sus', child: Text(sus ? 'Unsuspend' : 'Suspend')),
        if (showDevices) const PopupMenuItem(value: 'dev', child: Text('All devices of this account')),
      ],
    ),
  );
}

void _openUserDevices(BuildContext c, _Sec d, String uid, VoidCallback rl) {
  final hashes = d.devicesOf(uid)..sort((a, b) => (d.devices[b]![uid]!).compareTo(d.devices[a]![uid]!));
  final alerts = d.alerts.where((a) => s(a['uid']) == uid || s(a['relatedUid']) == uid).toList()
    ..sort((a, b) => ni(b['createdAt']).compareTo(ni(a['createdAt'])));
  showModalBottomSheet(
    context: c,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (sheet) => ListView(padding: const EdgeInsets.all(16), children: [
      Text(d.name(uid), style: Theme.of(c).textTheme.titleLarge),
      Text(d.email(uid), style: const TextStyle(color: Colors.grey)),
      const SizedBox(height: 4),
      _userTags(d, uid),
      if (d.suspended(uid) && s(d.user(uid)?['suspensionReason']).isNotEmpty) Padding(padding: const EdgeInsets.only(top: 6), child: Text('Reason: ${d.user(uid)!['suspensionReason']}', style: const TextStyle(color: Colors.red))),
      const SizedBox(height: 8),
      Wrap(spacing: 8, children: [
        FilledButton.icon(
          style: FilledButton.styleFrom(backgroundColor: d.suspended(uid) ? Colors.green : Colors.red),
          icon: Icon(d.suspended(uid) ? Icons.lock_open : Icons.block),
          label: Text(d.suspended(uid) ? 'Unsuspend' : 'Suspend'),
          onPressed: () { Navigator.pop(sheet); _setSuspended(c, d, uid, !d.suspended(uid), rl, reason: 'Fraud / multiple accounts'); },
        ),
        if (d.strikeOf(uid) > 0)
          OutlinedButton.icon(
            icon: const Icon(Icons.restart_alt),
            label: Text('Reset ${d.strikeOf(uid)} strikes'),
            onPressed: () async {
              Navigator.pop(sheet);
              if (!await confirm(c, 'Reset strikes?', 'Fraud strikes for ${d.email(uid)} go back to 0.') || !c.mounted) return;
              await act(c, () async { await Db.del('fraudStrikes/$uid'); await Db.audit('RESET_FRAUD_STRIKES', 'user', uid, d.email(uid)); }, rl, 'Strikes reset');
            },
          ),
      ]),
      const Divider(height: 24),
      Text('Logged in on ${hashes.length} device${hashes.length == 1 ? '' : 's'}', style: const TextStyle(fontWeight: FontWeight.bold)),
      if (hashes.isEmpty) const Padding(padding: EdgeInsets.all(8), child: Text('No device recorded for this account.')),
      for (final h in hashes)
        Card(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 10, 12, 4),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                const Icon(Icons.smartphone, size: 18),
                const SizedBox(width: 6),
                Expanded(child: SelectableText(_short(h), style: const TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.bold))),
                if (d.accountsOn(h).length > 1) tagChip('${d.accountsOn(h).length} ACCOUNTS', Colors.red),
              ]),
              Text('this account first seen ${when(d.devices[h]![uid])}', style: const TextStyle(fontSize: 11, color: Colors.grey)),
              if (d.accountsOn(h).length > 1) ...[
                const SizedBox(height: 6),
                const Text('Other accounts on this phone', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                for (final u in d.accountsOn(h).where((x) => x != uid))
                  _acctTile(sheet, c, d, u, rl, ts: d.devices[h]![u] ?? 0, owner: d.owner[h] == u, showDevices: true),
              ],
            ]),
          ),
        ),
      const Divider(height: 24),
      Text('Fraud alerts (${alerts.length})', style: const TextStyle(fontWeight: FontWeight.bold)),
      if (alerts.isEmpty) const Padding(padding: EdgeInsets.all(8), child: Text('None')),
      for (final a in alerts.take(15))
        ListTile(
          dense: true,
          contentPadding: EdgeInsets.zero,
          title: Text('${s(a['type'])} · ${s(a['severity'])}'),
          subtitle: Text('${when(a['createdAt'])} · ${s(a['status'])}${s(a['autoAction']).isEmpty ? '' : ' · auto: ${a['autoAction']}'}\n${s(a['details'])}', style: const TextStyle(fontSize: 11)),
          isThreeLine: true,
        ),
    ]),
  );
}

/// Used by the Users tab menu: opens the device / fraud sheet for one account.
Future<void> showUserSecurity(BuildContext c, String uid, VoidCallback rl) async {
  toast(c, 'Loading devices…');
  try {
    final d = await _load();
    if (!c.mounted) return;
    ScaffoldMessenger.of(c).hideCurrentSnackBar();
    _openUserDevices(c, d, uid, rl);
  } catch (e) {
    if (c.mounted) toast(c, 'Failed: $e');
  }
}

void _openDevice(BuildContext c, _Sec d, String hash, VoidCallback rl) {
  final accts = d.accountsOn(hash);
  final suspended = accts.where(d.suspended).length;
  showModalBottomSheet(
    context: c,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (sheet) => ListView(padding: const EdgeInsets.all(16), children: [
      Row(children: [
        const Icon(Icons.smartphone),
        const SizedBox(width: 8),
        Text('Device', style: Theme.of(c).textTheme.titleLarge),
      ]),
      const SizedBox(height: 4),
      SelectableText(hash, style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
      const SizedBox(height: 6),
      Wrap(children: [
        tagChip('${accts.length} account${accts.length == 1 ? '' : 's'}', accts.length > 1 ? Colors.red : Colors.green),
        if (suspended > 0) tagChip('$suspended suspended', Colors.red),
      ]),
      if (accts.length > 1)
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Align(
            alignment: Alignment.centerLeft,
            child: FilledButton.icon(
              style: FilledButton.styleFrom(backgroundColor: Colors.red),
              icon: const Icon(Icons.block),
              label: const Text('Suspend all except owner'),
              onPressed: () { Navigator.pop(sheet); _suspendOthers(c, d, hash, rl); },
            ),
          ),
        ),
      const Divider(height: 24),
      const Text('Accounts logged in on this phone', style: TextStyle(fontWeight: FontWeight.bold)),
      for (final u in accts) _acctTile(sheet, c, d, u, rl, ts: d.devices[hash]![u] ?? 0, owner: d.owner[hash] == u, showDevices: true),
    ]),
  );
}

// ───────────── Tab 1: Fraud alerts ─────────────

String _fFilter = 'open'; // kept across refreshes
Future<Widget> fraudTab(BuildContext c, VoidCallback rl) async => _FraudView(await _load(), rl);

class _FraudView extends StatefulWidget {
  final _Sec d;
  final VoidCallback rl;
  const _FraudView(this.d, this.rl);
  @override
  State<_FraudView> createState() => _FraudViewState();
}

class _FraudViewState extends State<_FraudView> {
  String q = '';

  @override
  Widget build(BuildContext c) {
    final d = widget.d, rl = widget.rl;
    final open = d.alerts.where((a) => s(a['status']) == 'open').toList();
    final list = d.alerts.where((a) {
      final st = s(a['status']);
      if (_fFilter == 'open' && st != 'open') return false;
      if (_fFilter == 'closed' && st == 'open') return false;
      return q.isEmpty || '${a['name']} ${a['email']} ${a['type']} ${a['details']} ${a['uid']} ${d.email(s(a['relatedUid']))}'.toLowerCase().contains(q.toLowerCase());
    }).toList()
      ..sort((a, b) {
        final byOpen = (s(b['status']) == 'open' ? 1 : 0) - (s(a['status']) == 'open' ? 1 : 0);
        return byOpen != 0 ? byOpen : ni(b['createdAt']).compareTo(ni(a['createdAt']));
      });
    final offenders = d.strikes.entries.where((e) => e.value > 0).toList()..sort((a, b) => b.value.compareTo(a.value));
    final suspendedNow = d.users.keys.where(d.suspended).length;

    return listPage([
      Wrap(spacing: 8, runSpacing: 8, children: [
        _statCard('Open alerts', open.length, open.isEmpty ? null : Colors.orange),
        _statCard('High severity', open.where((a) => s(a['severity']).toUpperCase() == 'HIGH').length, Colors.red),
        _statCard('Auto-suspended', d.alerts.where((a) => s(a['autoAction']) == 'suspended').length),
        _statCard('Suspended now', suspendedNow),
        _statCard('With strikes', offenders.length),
        _statCard('Shared phones', d.devices.values.where((m) => m.length > 1).length),
      ]),
      const SizedBox(height: 12),
      _search((x) => setState(() => q = x)),
      const SizedBox(height: 8),
      _chips(const [('open', 'Open'), ('closed', 'Dismissed'), ('all', 'All')], _fFilter, (v) => setState(() => _fFilter = v)),
      Padding(padding: const EdgeInsets.symmetric(vertical: 6), child: Text('${list.length} alert${list.length == 1 ? '' : 's'}', style: const TextStyle(color: Colors.grey))),
      if (list.isEmpty) const Padding(padding: EdgeInsets.all(20), child: Center(child: Text('No fraud alerts here'))),
      for (final a in list) _alertCard(c, d, a, rl),
      if (offenders.isNotEmpty) ...[
        const SizedBox(height: 14),
        const Text('Repeat offenders (strikes)', style: TextStyle(fontWeight: FontWeight.bold)),
        for (final e in offenders.take(25))
          Card(
            child: ListTile(
              dense: true,
              leading: CircleAvatar(radius: 14, backgroundColor: Colors.deepOrange.withOpacity(.2), child: Text('${e.value}', style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.deepOrange))),
              title: Text(d.name(e.key)),
              subtitle: Wrap(children: [Text('${d.email(e.key)}  ', style: const TextStyle(fontSize: 11)), _userTags(d, e.key)]),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => _openUserDevices(c, d, e.key, rl),
            ),
          ),
      ],
    ]);
  }

  Widget _alertCard(BuildContext c, _Sec d, Map<String, dynamic> a, VoidCallback rl) {
    final uid = s(a['uid']), rel = s(a['relatedUid']);
    final open = s(a['status']) == 'open';
    final sev = s(a['severity']);
    final sus = d.suspended(uid);
    final name = s(a['name']).isNotEmpty ? s(a['name']) : d.name(uid);
    final mail = s(a['email']).isNotEmpty ? s(a['email']) : d.email(uid);
    return Card(
      color: open ? null : Colors.grey.withOpacity(.08),
      child: InkWell(
        onTap: () => _openUserDevices(c, d, uid, rl),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 10, 4, 10),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Icon(s(a['type']) == 'DEVICE_FARM' ? Icons.dns : Icons.people_alt, size: 18, color: _sevColor(sev)),
              const SizedBox(width: 6),
              Expanded(child: Text(s(a['type']).replaceAll('_', ' '), style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13))),
              PopupMenuButton<String>(
                onSelected: (m) async {
                  switch (m) {
                    case 'dev': _openUserDevices(c, d, uid, rl);
                    case 'sus': await _setSuspended(c, d, uid, !sus, rl, reason: 'Fraud: ${s(a['type']).replaceAll('_', ' ').toLowerCase()}');
                    case 'close': await _setAlertStatus(c, a, 'dismissed', rl);
                    case 'open': await _setAlertStatus(c, a, 'open', rl);
                    case 'del':
                      if (await confirm(c, 'Delete alert?', 'Removes this alert only. The account is not changed.') && c.mounted) {
                        await act(c, () async { await Db.del('fraudAlerts/${a['id']}'); await Db.audit('FRAUD_ALERT_DELETED', 'fraudAlert', s(a['id']), mail); }, rl, 'Deleted');
                      }
                  }
                },
                itemBuilder: (_) => [
                  const PopupMenuItem(value: 'dev', child: Text('Devices & accounts')),
                  PopupMenuItem(value: 'sus', child: Text(sus ? 'Unsuspend user' : 'Suspend user')),
                  PopupMenuItem(value: open ? 'close' : 'open', child: Text(open ? 'Dismiss alert' : 'Re-open alert')),
                  const PopupMenuItem(value: 'del', child: Text('Delete alert')),
                ],
              ),
            ]),
            Wrap(runSpacing: 2, children: [
              tagChip(sev.isEmpty ? 'NO SEVERITY' : sev.toUpperCase(), _sevColor(sev)),
              tagChip(open ? 'OPEN' : s(a['status']).toUpperCase(), open ? Colors.orange : Colors.grey),
              if (s(a['autoAction']).isNotEmpty) tagChip('AUTO: ${s(a['autoAction']).toUpperCase()}', s(a['autoAction']) == 'suspended' ? Colors.red : Colors.amber),
              if (sus) tagChip('SUSPENDED NOW', Colors.red),
              if (d.strikeOf(uid) > 0) tagChip('${d.strikeOf(uid)} STRIKES', Colors.deepOrange),
            ]),
            const SizedBox(height: 6),
            Text('$name · $mail', style: const TextStyle(fontWeight: FontWeight.w600)),
            if (rel.isNotEmpty) Text('Same phone as: ${d.name(rel)}${d.email(rel).isEmpty ? '' : ' · ${d.email(rel)}'}', style: const TextStyle(fontSize: 12)),
            const SizedBox(height: 2),
            Text(s(a['details']), style: const TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 2),
            Text(when(a['createdAt']), style: const TextStyle(fontSize: 11, color: Colors.grey)),
          ]),
        ),
      ),
    );
  }
}

// ───────────── Tab 2: Devices ─────────────

String _dFilter = 'all'; // kept across refreshes
Future<Widget> devicesTab(BuildContext c, VoidCallback rl) async => _DevicesView(await _load(), rl);

class _DevicesView extends StatefulWidget {
  final _Sec d;
  final VoidCallback rl;
  const _DevicesView(this.d, this.rl);
  @override
  State<_DevicesView> createState() => _DevicesViewState();
}

class _DevicesViewState extends State<_DevicesView> {
  String q = '';

  int _latest(_Sec d, String h) => (d.devices[h] ?? const <String, int>{}).values.fold(0, (a, b) => a > b ? a : b);

  @override
  Widget build(BuildContext c) {
    final d = widget.d, rl = widget.rl;
    final all = d.devices.keys.toList();
    final shared = all.where((h) => d.devices[h]!.length > 1).toList();
    final withSus = all.where((h) => d.devices[h]!.keys.any(d.suspended)).toList();
    final list = (_dFilter == 'shared' ? shared : _dFilter == 'sus' ? withSus : all).where((h) {
      if (q.isEmpty) return true;
      final hay = '$h ${d.devices[h]!.keys.map((u) => '${d.name(u)} ${d.email(u)} $u').join(' ')}'.toLowerCase();
      return hay.contains(q.toLowerCase());
    }).toList()
      ..sort((a, b) {
        final n = d.devices[b]!.length.compareTo(d.devices[a]!.length);
        return n != 0 ? n : _latest(d, b).compareTo(_latest(d, a));
      });

    return listPage([
      Wrap(spacing: 8, runSpacing: 8, children: [
        _statCard('Devices', all.length),
        _statCard('Shared phones', shared.length, shared.isEmpty ? null : Colors.orange),
        _statCard('With suspended', withSus.length, withSus.isEmpty ? null : Colors.red),
        _statCard('Suspended users', d.users.keys.where(d.suspended).length),
      ]),
      const SizedBox(height: 12),
      _search((x) => setState(() => q = x)),
      const SizedBox(height: 8),
      _chips([('all', 'All (${all.length})'), ('shared', 'Shared (${shared.length})'), ('sus', 'With suspended (${withSus.length})')], _dFilter, (v) => setState(() => _dFilter = v)),
      Padding(padding: const EdgeInsets.symmetric(vertical: 6), child: Text('${list.length} device${list.length == 1 ? '' : 's'}  ·  tap one to see every account logged in on it', style: const TextStyle(color: Colors.grey))),
      for (final h in list.take(300)) _deviceCard(c, d, h, rl),
      if (list.length > 300) const Padding(padding: EdgeInsets.all(12), child: Text('Showing the first 300. Use search to narrow down.')),
    ]);
  }

  Widget _deviceCard(BuildContext c, _Sec d, String h, VoidCallback rl) {
    final accts = d.accountsOn(h);
    final many = accts.length > 1;
    final sus = accts.where(d.suspended).length;
    return Card(
      child: ListTile(
        leading: Icon(Icons.smartphone, color: many ? Colors.red : Colors.green),
        title: Text(_short(h), style: const TextStyle(fontFamily: 'monospace', fontSize: 13, fontWeight: FontWeight.bold)),
        subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          for (final u in accts.take(4))
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Row(children: [
                Icon(d.suspended(u) ? Icons.block : Icons.circle, size: d.suspended(u) ? 13 : 8, color: d.suspended(u) ? Colors.red : Colors.green),
                const SizedBox(width: 6),
                Expanded(child: Text('${d.email(u).isEmpty ? d.name(u) : d.email(u)}${d.owner[h] == u ? '  (owner)' : ''}', style: TextStyle(fontSize: 12, decoration: d.suspended(u) ? TextDecoration.lineThrough : null), maxLines: 1, overflow: TextOverflow.ellipsis)),
              ]),
            ),
          if (accts.length > 4) Text('+${accts.length - 4} more', style: const TextStyle(fontSize: 11, color: Colors.grey)),
          Text('last login ${when(_latest(d, h))}', style: const TextStyle(fontSize: 11, color: Colors.grey)),
        ]),
        isThreeLine: true,
        trailing: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          tagChip('${accts.length} ACC', many ? Colors.red : Colors.green),
          if (sus > 0) tagChip('$sus SUSP', Colors.red),
        ]),
        onTap: () => _openDevice(c, d, h, rl),
      ),
    );
  }
}
