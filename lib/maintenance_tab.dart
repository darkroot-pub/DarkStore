import 'package:flutter/material.dart';
import 'core.dart';
import 'sb.dart';

// ─────────────────────────────────────────────────────────────────────────────
//  Maintenance: close the whole store for everyone (admins included) now, or on a schedule.
//  One row in `app_config`, key `maintenanceConfig`. The store app reads it live and opens the
//  maintenance page / the store again BY ITSELF at the start / end time (no one has to come back
//  to flip a switch). This admin app is not affected.
// ─────────────────────────────────────────────────────────────────────────────

const _mtScheduleFields = [
  F('startAt', 'Starts at (the store keeps working until then)', 'date'),
  F('endAt', 'Ends at (the store opens again by itself)', 'date'),
  F('title', 'Page title'),
  F('message', 'Message everyone sees', 'area'),
  F('showCountdown', 'Show "expected back" countdown (needs an end time)', 'bool'),
  F('warnMinutes', 'Heads-up strip on Home, minutes before the start (0 = none)', 'num'),
];

const _mtTextFields = [
  F('title', 'Page title'),
  F('message', 'Message everyone sees', 'area'),
  F('showCountdown', 'Show "expected back" countdown (needs an end time)', 'bool'),
  F('warnMinutes', 'Heads-up strip on Home, minutes before the start (0 = none)', 'num'),
];

const _mtDefaults = <String, dynamic>{
  'title': 'Under maintenance',
  'message': 'Dark Store is temporarily offline for maintenance. Please check back soon.',
  'showCountdown': true,
  'warnMinutes': 60,
  'startAt': 0,
  'endAt': 0,
};

Map<String, Object?> _only(Map<String, dynamic> r, List<F> f) => {for (final x in f) x.k: r[x.k]};

enum _Mt { off, scheduled, active, ended }

_Mt _state(Map<String, dynamic> m, int now) {
  if (!bo(m['isEnabled'] ?? m['enabled'])) return _Mt.off;
  final st = ni(m['startAt']), en = ni(m['endAt']);
  if (st > 0 && now < st) return _Mt.scheduled;
  if (en > 0 && now >= en) return _Mt.ended;
  return _Mt.active;
}

String _span(int ms) {
  final d = Duration(milliseconds: ms);
  if (d.inDays > 0) return '${d.inDays}d ${d.inHours % 24}h';
  if (d.inHours > 0) return '${d.inHours}h ${d.inMinutes % 60}m';
  return '${d.inMinutes}m';
}

Widget _mtStatus(Map<String, dynamic> m) {
  final now = ms();
  final st = ni(m['startAt']), en = ni(m['endAt']);
  final s0 = _state(m, now);
  final (label, color, detail) = switch (s0) {
    _Mt.off => ('OFF', Colors.green, 'The store is open.'),
    _Mt.scheduled => ('SCHEDULED', Colors.orange, 'Starts ${when(st)} (in ${_span(st - now)})${en > 0 ? ', ends ${when(en)}' : ', no end time'}.'),
    _Mt.active => ('ON NOW', Colors.red, en > 0 ? 'Everyone sees the maintenance page. Ends ${when(en)} (in ${_span(en - now)}).' : 'Everyone sees the maintenance page until you end it.'),
    _Mt.ended => ('ENDED', Colors.blueGrey, 'The end time has passed, so the store is open again. The switch is still on: press "End now" to clear it.'),
  };
  return Card(
    color: color.withOpacity(.10),
    child: Padding(
      padding: const EdgeInsets.all(14),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Icon(Icons.construction_rounded, color: color),
          const SizedBox(width: 8),
          Text('MAINTENANCE $label', style: TextStyle(fontWeight: FontWeight.w900, color: color, fontSize: 15)),
        ]),
        const SizedBox(height: 6),
        Text(detail, style: const TextStyle(height: 1.4)),
      ]),
    ),
  );
}

Widget _mtPreview(Map<String, dynamic> m) {
  final v = {..._mtDefaults, ...m};
  final en = ni(v['endAt']);
  return Container(
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(color: const Color(0xFFE8F4FC), borderRadius: BorderRadius.circular(14)),
    child: Column(children: [
      const Icon(Icons.construction_rounded, size: 38, color: Color(0xFF1FA9A0)),
      const SizedBox(height: 6),
      Text(s(v['title']).trim().isEmpty ? s(_mtDefaults['title']) : s(v['title']),
          textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16, color: Colors.black87)),
      const SizedBox(height: 4),
      Text(s(v['message']).trim().isEmpty ? s(_mtDefaults['message']) : s(v['message']),
          textAlign: TextAlign.center, style: const TextStyle(fontSize: 12.5, color: Colors.black54)),
      if (en > 0) ...[
        const SizedBox(height: 8),
        Text('Expected back ${when(en)}', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12.5, color: Colors.black87)),
        if (v['showCountdown'] != false) const Text('in 1h 23m', style: TextStyle(fontWeight: FontWeight.w800, color: Color(0xFF1FA9A0))),
      ],
    ]),
  );
}

/// A message to show instead of saving, or null when the times make sense.
String? _mtProblem(Map<String, dynamic> x, {required bool needStart}) {
  final now = ms();
  final st = ni(x['startAt']), en = ni(x['endAt']);
  if (needStart && st <= 0) return 'Pick a start time (to close the store right now use "Start now")';
  if (needStart && st <= now) return 'The start time is in the past. Pick a later time, or use "Start now"';
  if (en > 0 && en <= now) return 'The end time is in the past';
  if (en > 0 && st > 0 && en <= st) return 'The end time must be after the start time';
  if (ni(x['warnMinutes']) < 0 || ni(x['warnMinutes']) > 1440) return 'Heads-up minutes must be between 0 and 1440';
  return null;
}

Future<Widget> maintenanceTab(BuildContext c, VoidCallback rl) async {
  final m = await Db.map('maintenanceConfig');
  final shown = {..._mtDefaults, ...m};
  final now = ms();
  final state = _state(m, now);
  final on = state == _Mt.active || state == _Mt.scheduled;

  Future<void> save(Map<String, Object?> patch, String what) => act(c, () async {
        // `isEnabled` is kept in step with `enabled`: older store versions read that name
        await Db.upd('maintenanceConfig', {...patch, 'isEnabled': patch['enabled']});
        await Db.audit('MAINTENANCE', 'config', 'maintenanceConfig', 'Maintenance', what);
      }, rl);

  Future<void> startNow({int minutes = 0}) async {
    final label = minutes > 0 ? 'for $minutes minutes' : 'until you end it';
    if (!await confirm(c, 'Close the store now?', 'Everyone, admins too, will see the maintenance page $label.') || !c.mounted) return;
    await save({'enabled': true, 'startAt': 0, 'endAt': minutes > 0 ? now + minutes * 60000 : 0}, 'started $label');
  }

  return listPage([
    _mtStatus(m),
    const SizedBox(height: 10),
    const Text('WHAT PEOPLE SEE', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
    const SizedBox(height: 6),
    _mtPreview(shown),
    const SizedBox(height: 14),
    const Text('QUICK ACTIONS', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
    const SizedBox(height: 6),
    Wrap(spacing: 8, runSpacing: 8, children: [
      if (state == _Mt.off || state == _Mt.ended || state == _Mt.scheduled) ...[
        FilledButton.icon(icon: const Icon(Icons.power_settings_new), label: const Text('Start now'), onPressed: () => startNow()),
        OutlinedButton(onPressed: () => startNow(minutes: 30), child: const Text('Start for 30 min')),
        OutlinedButton(onPressed: () => startNow(minutes: 60), child: const Text('Start for 1 hour')),
      ],
      if (state == _Mt.active)
        OutlinedButton.icon(
          icon: const Icon(Icons.more_time),
          label: const Text('Extend 30 min'),
          onPressed: () => save({'enabled': true, 'endAt': (ni(m['endAt']) > now ? ni(m['endAt']) : now) + 30 * 60000}, 'extended 30 min'),
        ),
      if (on || state == _Mt.ended)
        FilledButton.icon(
          style: FilledButton.styleFrom(backgroundColor: Colors.green),
          icon: const Icon(Icons.check_circle_outline),
          label: Text(state == _Mt.scheduled ? 'Cancel schedule' : 'End now'),
          onPressed: () async {
            if (!await confirm(c, state == _Mt.scheduled ? 'Cancel the scheduled maintenance?' : 'Open the store again?', 'The store opens for everyone right away.') || !c.mounted) return;
            await save({'enabled': false, 'startAt': 0, 'endAt': 0}, state == _Mt.scheduled ? 'schedule cancelled' : 'ended');
          },
        ),
    ]),
    const SizedBox(height: 14),
    Wrap(spacing: 8, runSpacing: 8, children: [
      FilledButton.tonalIcon(
        icon: const Icon(Icons.event_available),
        label: Text(state == _Mt.scheduled ? 'Change schedule' : 'Schedule maintenance'),
        onPressed: () async {
          final init = {...shown, if (ni(shown['startAt']) <= now) 'startAt': 0, if (ni(shown['endAt']) <= now) 'endAt': 0};
          final x = await editSheet(c, 'Schedule maintenance', _mtScheduleFields, init, preview: _mtPreview);
          if (x == null || !c.mounted) return;
          final bad = _mtProblem(x, needStart: true);
          if (bad != null) return toast(c, bad);
          final st = ni(x['startAt']), en = ni(x['endAt']);
          if (!await confirm(c, 'Schedule maintenance?', 'From ${when(st)}${en > 0 ? ' to ${when(en)}' : ' until you end it'}, everyone (admins too) will see the maintenance page. The store opens by itself at the end time.') || !c.mounted) return;
          await save({..._only(x, _mtScheduleFields), 'enabled': true}, 'scheduled ${when(st)}${en > 0 ? ' - ${when(en)}' : ''}');
        },
      ),
      OutlinedButton.icon(
        icon: const Icon(Icons.edit),
        label: const Text('Edit page text'),
        onPressed: () async {
          final x = await editSheet(c, 'Maintenance page', _mtTextFields, shown, preview: (v) => _mtPreview({...shown, ...v}));
          if (x == null || !c.mounted) return;
          final bad = _mtProblem({...shown, ...x}, needStart: false);
          if (bad != null) return toast(c, bad);
          await act(c, () async {
            await Db.upd('maintenanceConfig', _only(x, _mtTextFields));
            await Db.audit('MAINTENANCE', 'config', 'maintenanceConfig', 'Maintenance', 'text edited');
          }, rl);
        },
      ),
    ]),
    const SizedBox(height: 14),
    const Text(
      'While maintenance is ON, the store app shows the maintenance page to EVERYONE, admins included. '
      'This admin app is not affected, so you can end it from here. With a start time the store keeps working until then '
      'and Home shows a heads-up strip before it begins; with an end time the store opens again by itself. '
      'Times are exact moments: each phone shows them in its own time zone. Phones with a wrong clock may open or close a few minutes off.',
      style: TextStyle(fontSize: 12, color: Colors.grey, height: 1.4),
    ),
  ]);
}
