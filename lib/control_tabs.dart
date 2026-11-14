import 'package:flutter/material.dart';
import 'core.dart';
import 'sb.dart';

// ─────────────────────────────────────────────────────────────────────────────
// Remote controls for the store app. Everything here is saved in the database
// (`app_config` rows, or `device_guard_config`) and the store app reads it live,
// so nothing needs a new app build:
//   Guard     automatic suspension rules, warnings, protection after unsuspend
//   Announce  the message strip at the top of Home
//   Features  switch Submit / Chat / Reviews off and on
// ─────────────────────────────────────────────────────────────────────────────

Map<String, Object?> _only(Map<String, dynamic> r, List<F> f) => {for (final x in f) x.k: r[x.k]};

const _foreverMs = 32000000000000; // the database stores "forever" as the year 2999

// ═══════════════════════════════ GUARD ═══════════════════════════════

Future<Widget> guardTab(BuildContext c, VoidCallback rl) async {
  final r = await Future.wait<Object>([
    Db.map('guardConfig'),
    Db.list('users'),
    Db.map('fraudWarnings'),
    Db.map('fraudStrikes'),
  ]);
  final g = r[0] as Map<String, dynamic>;
  final users = r[1] as List<Map<String, dynamic>>;
  final warned = r[2] as Map<String, dynamic>;
  final strikes = r[3] as Map<String, dynamic>;
  final byId = {for (final u in users) s(u['id']): u};
  String who(String uid) {
    final u = byId[uid];
    if (u == null) return uid;
    final n = s(u['displayName']).isNotEmpty ? s(u['displayName']) : s(u['userName']);
    return '${n.isEmpty ? uid : n}  ${s(u['email'])}'.trim();
  }

  final now = ms();
  final protected = users.where((u) => ni(u['guardExemptUntil']) > now).toList()
    ..sort((a, b) => ni(a['guardExemptUntil']).compareTo(ni(b['guardExemptUntil'])));

  Future<void> put(Map<String, Object?> patch, String what) => act(c, () async {
        await Db.upd('guardConfig', patch);
        await Db.audit('GUARD_SETTINGS', 'config', 'device_guard_config', 'Guard settings', what);
      }, rl, 'Saved');

  Future<void> editOne(String key, String label, String type, {List<String> opts = const []}) async {
    final x = await editSheet(c, label, [F(key, label, type, opts)], g);
    if (x != null && c.mounted) await put({key: x[key]}, '$key=${x[key]}');
  }

  final warnN = ni(g['warningsBeforeSuspend']);
  final exDays = ni(g['exemptDays']);
  final maxAcc = ni(g['maxAccounts']) < 1 ? 1 : ni(g['maxAccounts']);

  String rule() {
    final b = StringBuffer();
    if (!bo(g['autoSuspend'])) {
      b.write('Automatic suspension for extra accounts is OFF: they are only recorded. ');
    } else {
      b.write('A phone may have $maxAcc account${maxAcc == 1 ? '' : 's'}; the next one ');
      b.write(warnN > 0 ? 'gets $warnN warning${warnN == 1 ? '' : 's'} (at most one every ${ni(g['warnCooldownMinutes'])} min) and is then suspended. ' : 'is suspended at once. ');
    }
    if (bo(g['exemptAfterUnsuspend'])) {
      b.write('When you unsuspend someone they are protected ${exDays <= 0 ? 'for good' : 'for $exDays day${exDays == 1 ? '' : 's'}'}, so the rules do not suspend them again.');
    } else {
      b.write('Unsuspended accounts can be suspended again by the rules.');
    }
    return b.toString();
  }

  return listPage([
    Card(
      color: Colors.blue.withOpacity(.08),
      child: Padding(padding: const EdgeInsets.all(12), child: Text(rule(), style: const TextStyle(fontSize: 13, height: 1.4))),
    ),
    const SizedBox(height: 8),
    Card(
      child: Column(children: [
        SwitchListTile(
          title: const Text('Auto-suspend extra accounts on one phone'),
          subtitle: const Text('Off = only record them, never suspend'),
          value: bo(g['autoSuspend']),
          onChanged: (v) => put({'autoSuspend': v}, 'autoSuspend=$v'),
        ),
        ListTile(
          title: const Text('Accounts allowed per phone'),
          subtitle: Text('$maxAcc'),
          trailing: const Icon(Icons.edit),
          onTap: () => editOne('maxAccounts', 'Accounts allowed per phone', 'num'),
        ),
        ListTile(
          title: const Text('Warnings before suspending'),
          subtitle: Text(warnN == 0 ? '0 · suspend at once' : '$warnN · the user is notified first'),
          trailing: const Icon(Icons.edit),
          onTap: () => editOne('warningsBeforeSuspend', 'Warnings before suspending (0 = none)', 'num'),
        ),
        ListTile(
          title: const Text('Minutes between warnings'),
          subtitle: Text('${ni(g['warnCooldownMinutes'])}'),
          trailing: const Icon(Icons.edit),
          onTap: () => editOne('warnCooldownMinutes', 'Minutes between warnings', 'num'),
        ),
        ListTile(
          title: const Text('Warning message'),
          subtitle: Text(s(g['warningMessage']).isEmpty ? 'Default text (explains the rule)' : s(g['warningMessage'])),
          trailing: const Icon(Icons.edit),
          onTap: () => editOne('warningMessage', 'Warning message shown to the user', 'area'),
        ),
        SwitchListTile(
          title: const Text('Block emulators'),
          value: bo(g['blockEmulators']),
          onChanged: (v) => put({'blockEmulators': v}, 'blockEmulators=$v'),
        ),
      ]),
    ),
    const SizedBox(height: 8),
    Card(
      child: Column(children: [
        const ListTile(title: Text('When you unsuspend an account', style: TextStyle(fontWeight: FontWeight.bold))),
        SwitchListTile(
          title: const Text('Protect it from automatic suspension'),
          subtitle: const Text('Stops the "suspended again at next start" problem'),
          value: bo(g['exemptAfterUnsuspend']),
          onChanged: (v) => put({'exemptAfterUnsuspend': v}, 'exemptAfterUnsuspend=$v'),
        ),
        ListTile(
          title: const Text('Protection lasts (days)'),
          subtitle: Text(exDays <= 0 ? '0 · for good' : '$exDays days'),
          trailing: const Icon(Icons.edit),
          onTap: () => editOne('exemptDays', 'Protection lasts (days, 0 = for good)', 'num'),
        ),
        SwitchListTile(
          title: const Text('Clear strikes, warnings and open alerts'),
          value: bo(g['resetStrikesOnUnsuspend']),
          onChanged: (v) => put({'resetStrikesOnUnsuspend': v}, 'resetStrikesOnUnsuspend=$v'),
        ),
      ]),
    ),
    const SizedBox(height: 14),
    Row(children: [
      Expanded(child: Text('Protected accounts (${protected.length})', style: const TextStyle(fontWeight: FontWeight.bold))),
      TextButton.icon(
        icon: const Icon(Icons.add_moderator_outlined, size: 18),
        label: const Text('Protect an account'),
        onPressed: () async {
          final x = await editSheet(c, 'Protect an account', const [
            F('email', 'Account e-mail'),
            F('days', 'Days (0 = for good)', 'num'),
          ], {'days': 0});
          if (x == null || !c.mounted) return;
          final mail = s(x['email']).trim().toLowerCase();
          final hit = users.where((u) => s(u['email']).toLowerCase() == mail).toList();
          if (hit.isEmpty) return toast(c, 'No account with that e-mail');
          final uid = s(hit.first['id']);
          await act(c, () async {
            await Sb.c.rpc('admin_set_guard_exempt', params: {'p_uid': uid, 'p_days': ni(x['days'])});
            await Db.audit('GUARD_PROTECT', 'user', uid, mail, 'days=${ni(x['days'])}');
          }, rl, 'Protected');
        },
      ),
    ]),
    if (protected.isEmpty) const Padding(padding: EdgeInsets.all(12), child: Text('None yet. Unsuspended accounts appear here.', style: TextStyle(color: Colors.grey))),
    for (final u in protected.take(60))
      Card(
        child: ListTile(
          dense: true,
          leading: const Icon(Icons.verified_user_outlined, color: Colors.green),
          title: Text(who(s(u['id'])), maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: Text(ni(u['guardExemptUntil']) >= _foreverMs ? 'Protected for good' : 'Protected until ${when(u['guardExemptUntil'])}'),
          trailing: TextButton(
            child: const Text('Remove'),
            onPressed: () async {
              if (!await confirm(c, 'Remove protection?', 'The automatic rules can suspend this account again.') || !c.mounted) return;
              await act(c, () async {
                await Sb.c.rpc('admin_set_guard_exempt', params: {'p_uid': s(u['id']), 'p_days': -1});
                await Db.audit('GUARD_UNPROTECT', 'user', s(u['id']), s(u['email']));
              }, rl, 'Protection removed');
            },
          ),
        ),
      ),
    const SizedBox(height: 14),
    Text('Warned accounts (${warned.length})', style: const TextStyle(fontWeight: FontWeight.bold)),
    if (warned.isEmpty) const Padding(padding: EdgeInsets.all(12), child: Text('Nobody has been warned.', style: TextStyle(color: Colors.grey))),
    for (final e in warned.entries.take(60))
      Card(
        child: ListTile(
          dense: true,
          leading: CircleAvatar(radius: 14, backgroundColor: Colors.amber.withOpacity(.25), child: Text('${ni(e.value)}', style: const TextStyle(fontWeight: FontWeight.bold))),
          title: Text(who(e.key), maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: Text('${ni(e.value)} warning${ni(e.value) == 1 ? '' : 's'} · ${ni(strikes[e.key])} strike${ni(strikes[e.key]) == 1 ? '' : 's'}'),
          trailing: TextButton(
            child: const Text('Clear'),
            onPressed: () => act(c, () async {
              await Db.del('fraudStrikes/${e.key}');
              await Db.audit('RESET_FRAUD_STRIKES', 'user', e.key, s(byId[e.key]?['email']), 'warnings cleared');
            }, rl, 'Cleared'),
          ),
        ),
      ),
  ]);
}

// ═══════════════════════════════ ANNOUNCE ═══════════════════════════════

const _anFields = [
  F('enabled', 'Announcement is ON', 'bool'),
  F('id', 'Announcement id (change it to show again to everyone)'),
  F('text', 'Message (max 240 characters)', 'area'),
  F('emoji', 'Emoji (optional)'),
  F('style', 'Look', 'select', ['info', 'success', 'warning', 'promo', 'custom']),
  F('bg', 'Background #RRGGBB (only for "custom")'),
  F('fg', 'Text colour #RRGGBB (only for "custom")'),
  F('linkLabel', 'Link text (optional)'),
  F('link', 'Opens: an app page like /apps or an https link (optional)'),
  F('audience', 'Shown to', 'select', ['all', 'signedIn', 'guests', 'premium']),
  F('dismissible', 'People can close it', 'bool'),
  F('startAt', 'Starts at', 'date'),
  F('endAt', 'Ends at', 'date'),
];

Color? _hex(Object? v) {
  var t = s(v).trim().replaceAll('#', '');
  if (!RegExp(r'^([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$').hasMatch(t)) return null;
  if (t.length == 6) t = 'FF$t';
  final n = int.tryParse(t, radix: 16);
  return n == null ? null : Color(n);
}

/// What the strip looks like in the store app.
Widget announcePreview(Map<String, dynamic> v) {
  final style = s(v['style']);
  Color bg;
  Color fg = Colors.white;
  switch (style) {
    case 'success':
      bg = const Color(0xFF0F7B55);
    case 'warning':
      bg = const Color(0xFFB7791F);
    case 'promo':
      bg = const Color(0xFF6C3CE0);
    case 'custom':
      bg = _hex(v['bg']) ?? const Color(0xFF1F6FEB);
      fg = _hex(v['fg']) ?? Colors.white;
    default:
      bg = const Color(0xFF1F6FEB);
  }
  final text = s(v['text']).trim().isEmpty ? 'Your message appears here' : s(v['text']).trim();
  return Container(
    padding: const EdgeInsets.fromLTRB(14, 10, 8, 10),
    decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(16)),
    child: Row(children: [
      if (s(v['emoji']).trim().isNotEmpty) Padding(padding: const EdgeInsets.only(right: 10), child: Text(s(v['emoji']).trim(), style: const TextStyle(fontSize: 20))),
      Expanded(
        child: Text.rich(TextSpan(children: [
          TextSpan(text: text),
          if (s(v['link']).isNotEmpty && s(v['linkLabel']).isNotEmpty)
            TextSpan(text: '  ${s(v['linkLabel'])}', style: const TextStyle(fontWeight: FontWeight.w800, decoration: TextDecoration.underline)),
        ]), style: TextStyle(color: fg, fontSize: 13.5, height: 1.3, fontWeight: FontWeight.w600)),
      ),
      if (v['dismissible'] != false) Icon(Icons.close_rounded, size: 18, color: fg),
    ]),
  );
}

Future<Widget> announceTab(BuildContext c, VoidCallback rl) async {
  final e = await Db.map('announceConfig');
  final now = ms();
  final on = bo(e['enabled']);
  final hasText = s(e['text']).trim().isNotEmpty;
  final live = on && hasText && (ni(e['startAt']) == 0 || now >= ni(e['startAt'])) && (ni(e['endAt']) == 0 || now <= ni(e['endAt']));

  Future<void> save(Map<String, dynamic> r, String what) => act(c, () async {
        await Db.upd('announceConfig', _only(r, _anFields));
        await Db.audit('ANNOUNCE', 'config', 'announceConfig', s(r['text']).length > 40 ? '${s(r['text']).substring(0, 40)}…' : s(r['text']), what);
      }, rl);

  return listPage([
    Row(children: [
      tagChip(on ? (live ? 'LIVE NOW' : 'ON · outside schedule') : 'OFF', live ? Colors.green : Colors.grey),
      Text(' id: ${s(e['id']).isEmpty ? '-' : s(e['id'])}', style: const TextStyle(fontSize: 12)),
    ]),
    Text('Window: ${when(e['startAt'])} → ${when(e['endAt'])} · for ${s(e['audience']).isEmpty ? 'all' : s(e['audience'])}', style: const TextStyle(fontSize: 12, color: Colors.grey)),
    const SizedBox(height: 10),
    const Text('PREVIEW (top of Home in the store app)', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
    const SizedBox(height: 6),
    announcePreview(e),
    const SizedBox(height: 12),
    Wrap(spacing: 8, runSpacing: 8, children: [
      FilledButton.icon(
        icon: const Icon(Icons.edit),
        label: const Text('Edit announcement'),
        onPressed: () async {
          final r = await editSheet(c, 'Announcement', _anFields, {'enabled': true, 'style': 'info', 'audience': 'all', 'dismissible': true, ...e}, preview: announcePreview);
          if (r != null && c.mounted) save(r, 'edited');
        },
      ),
      OutlinedButton.icon(
        icon: const Icon(Icons.power_settings_new),
        label: Text(on ? 'Turn off' : 'Turn on'),
        onPressed: () => save({...e, 'enabled': !on}, 'toggle'),
      ),
      OutlinedButton.icon(
        icon: const Icon(Icons.replay),
        label: const Text('Show again to everyone'),
        onPressed: () => save({...e, 'id': 'an-${ms()}'}, 'new id'),
      ),
      OutlinedButton.icon(
        icon: const Icon(Icons.delete_outline),
        label: const Text('Clear'),
        onPressed: () async {
          if (!await confirm(c, 'Clear announcement?', 'Removes the message and its settings.') || !c.mounted) return;
          await act(c, () async {
            await Db.del('announceConfig');
            await Db.audit('ANNOUNCE', 'config', 'announceConfig', 'Announcement', 'cleared');
          }, rl);
        },
      ),
    ]),
    const SizedBox(height: 12),
    const Text(
      'People can close it; it stays closed until you change the id (or press "Show again to everyone"). '
      'Use a schedule for sales, events or maintenance notices: it appears and disappears by itself.',
      style: TextStyle(fontSize: 12, color: Colors.grey, height: 1.4),
    ),
  ]);
}

// ═══════════════════════════════ FEATURES ═══════════════════════════════

const _features = <(String, String, String, IconData)>[
  ('submit', 'Submit apps', 'Sending new apps and updates for review', Icons.upload_file_rounded),
  ('chat', 'Chat', 'The Chat tab (messages, global room, developers)', Icons.chat_bubble_outline),
  ('reviews', 'Reviews', 'Writing app reviews and ratings', Icons.rate_review_outlined),
];

Future<Widget> featuresTab(BuildContext c, VoidCallback rl) async {
  final e = await Db.map('featureFlags');
  bool isOn(String k) => e[k] != false && s(e[k]) != 'false';
  final off = _features.where((f) => !isOn(f.$1)).length;

  return listPage([
    Row(children: [
      tagChip(off == 0 ? 'ALL FEATURES ON' : '$off OFF', off == 0 ? Colors.green : Colors.orange),
    ]),
    const SizedBox(height: 8),
    Card(
      child: Column(children: [
        for (final f in _features)
          SwitchListTile(
            secondary: Icon(f.$4),
            title: Text(f.$2),
            subtitle: Text(f.$3),
            value: isOn(f.$1),
            onChanged: (v) => act(c, () async {
              await Db.upd('featureFlags', {f.$1: v});
              await Db.audit('FEATURE_FLAG', 'config', 'featureFlags', f.$2, v ? 'on' : 'off');
            }, rl, v ? '${f.$2} is ON' : '${f.$2} is OFF for everyone'),
          ),
        ListTile(
          leading: const Icon(Icons.message_outlined),
          title: const Text('Message shown when a feature is off'),
          subtitle: Text(s(e['offMessage']).isEmpty ? 'Default: "This feature is switched off for now."' : s(e['offMessage'])),
          trailing: const Icon(Icons.edit),
          onTap: () async {
            final x = await editSheet(c, 'Message when a feature is off', const [F('offMessage', 'Message', 'area')], e);
            if (x != null && c.mounted) await act(c, () => Db.upd('featureFlags', {'offMessage': s(x['offMessage'])}), rl);
          },
        ),
      ]),
    ),
    const SizedBox(height: 10),
    const Text(
      'Switching a feature off takes effect in the store app within a minute or two, without a new build. '
      'Admins are not exempt, so test with care. Turn it back on the same way.',
      style: TextStyle(fontSize: 12, color: Colors.grey, height: 1.4),
    ),
  ]);
}
