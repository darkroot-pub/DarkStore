import 'dart:convert';
import 'package:flutter/material.dart';
import 'core.dart';

// ───────────────────────────────────────────────────────────────────────────────
// Bottom navigation bar designer (festival / event looks).
// Saves one JSON row, `app_config` key `navUiConfig`. The store app reads it live and,
// like the event popup, only shows it while "ON" and inside the start/end time window;
// after the window it goes back to the built-in bar by itself. Every default equals the
// built-in bar.
// ───────────────────────────────────────────────────────────────────────────────

const Map<String, dynamic> kNavUiDefaults = {
  'enabled': true,
  'startAt': 0,
  'endAt': 0,
  'style': 'floating',
  'height': 64,
  'radius': 32,
  'sideMargin': 12,
  'shadow': true,
  'borderWidth': 0,
  'borderColor': '',
  'barColor': '',
  'barColor2': '',
  'pillColor': '',
  'selIconColor': '',
  'iconColor': '',
  'selLabelColor': '',
  'labelColor': '',
  'indicator': 'pill',
  'iconSize': 22,
  'labelMode': 'always',
  'label0': '',
  'label1': '',
  'label2': '',
  'label3': '',
  'label4': '',
  'emoji': '',
  'emojiPosition': 'right',
};

/// Festival / event looks. They change the design only, never the schedule or the labels.
const _presets = <String, Map<String, dynamic>>{
  'Built-in': {},
  '🪁 Dashain': {'barColor': '#7F1D1D', 'barColor2': '#B91C1C', 'pillColor': '#FBBF24', 'selIconColor': '#7F1D1D', 'iconColor': '#FDE68A', 'labelColor': '#FDE68A', 'selLabelColor': '#FBBF24', 'emoji': '🪁'},
  '🪔 Tihar': {'barColor': '#1E1B4B', 'barColor2': '#4C1D95', 'pillColor': '#FBBF24', 'selIconColor': '#1E1B4B', 'iconColor': '#C4B5FD', 'labelColor': '#C4B5FD', 'selLabelColor': '#FDE68A', 'emoji': '🪔'},
  '🎄 Christmas': {'barColor': '#14532D', 'barColor2': '#B91C1C', 'pillColor': '#FFFFFF', 'selIconColor': '#B91C1C', 'iconColor': '#D1FAE5', 'labelColor': '#D1FAE5', 'selLabelColor': '#FFFFFF', 'emoji': '🎄'},
  '🌙 Eid': {'barColor': '#064E3B', 'barColor2': '#0F766E', 'pillColor': '#FDE68A', 'selIconColor': '#064E3B', 'iconColor': '#A7F3D0', 'labelColor': '#A7F3D0', 'selLabelColor': '#FDE68A', 'emoji': '🌙'},
  '🎨 Holi': {'barColor': '#EC4899', 'barColor2': '#8B5CF6', 'pillColor': '#FFFFFF', 'selIconColor': '#8B5CF6', 'iconColor': '#FCE7F3', 'labelColor': '#FCE7F3', 'selLabelColor': '#FFFFFF', 'emoji': '🎨'},
  '🎆 New Year': {'barColor': '#0F172A', 'barColor2': '#1E3A8A', 'pillColor': '#FBBF24', 'selIconColor': '#0F172A', 'iconColor': '#BFDBFE', 'labelColor': '#BFDBFE', 'selLabelColor': '#FBBF24', 'emoji': '🎆'},
  '🎃 Halloween': {'barColor': '#111827', 'barColor2': '#7C2D12', 'pillColor': '#F97316', 'selIconColor': '#111827', 'iconColor': '#FDBA74', 'labelColor': '#FDBA74', 'selLabelColor': '#F97316', 'emoji': '🎃'},
  '💝 Valentine': {'barColor': '#9D174D', 'barColor2': '#F472B6', 'pillColor': '#FFFFFF', 'selIconColor': '#9D174D', 'iconColor': '#FCE7F3', 'labelColor': '#FCE7F3', 'selLabelColor': '#FFFFFF', 'emoji': '💝'},
  'Slim line': {'style': 'docked', 'radius': 20, 'height': 56, 'indicator': 'line', 'labelMode': 'selected', 'shadow': false, 'borderWidth': 1, 'borderColor': '#E2E8F0'},
};

const _swatches = ['#00A8C5', '#2563EB', '#7C3AED', '#DB2777', '#EF4444', '#F59E0B', '#10B981', '#0F172A', '#FFFFFF', '#64748B'];

bool _flag(Object? v, bool d) {
  if (v == null) return d;
  if (v is bool) return v;
  final t = '$v'.toLowerCase();
  return t == 'true' || t == '1' ? true : (t == 'false' || t == '0' ? false : d);
}

double _num(Object? v, double d, double lo, double hi) {
  final x = v is num ? v.toDouble() : double.tryParse('$v');
  return x == null ? d : x.clamp(lo, hi).toDouble();
}

String _pick(Object? v, List<String> ok, String d) {
  final t = s(v).trim().toLowerCase();
  return ok.contains(t) ? t : d;
}

bool _hexOk(String h) => RegExp(r'^#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$').hasMatch(h.trim());

Color? _col(Object? v) {
  var t = s(v).trim().replaceAll('#', '');
  if (!_hexOk(t)) return null;
  if (t.length == 6) t = 'FF$t';
  final n = int.tryParse(t, radix: 16);
  return n == null ? null : Color(n);
}

const _tabs = <(String, IconData, IconData)>[
  ('Games', Icons.sports_esports_outlined, Icons.sports_esports),
  ('Apps', Icons.home_outlined, Icons.home_rounded),
  ('My Library', Icons.library_books_outlined, Icons.library_books),
  ('Chat', Icons.chat_bubble_outline, Icons.chat_bubble),
  ('Profile', Icons.person_outline, Icons.person),
];

/// Draws the bottom bar like the store app does (light theme) on a small mock screen.
Widget navBarPreview(Map<String, dynamic> c, int sel, ValueChanged<int> onTap) {
  const teal = Color(0xFF00A8C5), mute = Color(0xFF8B95A5);
  final docked = _pick(c['style'], const ['floating', 'docked'], 'floating') == 'docked';
  final h = _num(c['height'], 64, 52, 88);
  final radius = _num(c['radius'], 32, 0, 40);
  final side = docked ? 0.0 : _num(c['sideMargin'], 12, 0, 32);
  final bw = _num(c['borderWidth'], 0, 0, 4);
  final c1 = _col(c['barColor']) ?? Colors.white;
  final c2 = _col(c['barColor2']);
  final pill = _col(c['pillColor']) ?? teal;
  final indicator = _pick(c['indicator'], const ['pill', 'line', 'none'], 'pill');
  final withPill = indicator == 'pill';
  final iconSel = _col(c['selIconColor']) ?? (withPill ? Colors.white : pill);
  final iconSize = _num(c['iconSize'], 22, 18, 30);
  final mode = _pick(c['labelMode'], const ['always', 'selected', 'never'], 'always');
  final emoji = s(c['emoji']).trim();
  final pos = _pick(c['emojiPosition'], const ['left', 'center', 'right'], 'right');

  Widget item(int i) {
    final on = i == sel;
    final label = s(c['label$i']).trim().isEmpty ? _tabs[i].$1 : s(c['label$i']).trim();
    final showLabel = mode == 'always' || (mode == 'selected' && on);
    return Expanded(
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () => onTap(i),
        child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            decoration: BoxDecoration(color: on && withPill ? pill : Colors.transparent, borderRadius: BorderRadius.circular(16)),
            child: Icon(on ? _tabs[i].$3 : _tabs[i].$2, size: iconSize, color: on ? iconSel : (_col(c['iconColor']) ?? mute)),
          ),
          if (showLabel) ...[
            const SizedBox(height: 2),
            Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 10, fontWeight: on ? FontWeight.w700 : FontWeight.w500, color: on ? (_col(c['selLabelColor']) ?? pill) : (_col(c['labelColor']) ?? mute))),
          ],
          if (indicator == 'line')
            Container(margin: const EdgeInsets.only(top: 3), width: 18, height: 3, decoration: BoxDecoration(color: on ? pill : Colors.transparent, borderRadius: BorderRadius.circular(2))),
        ]),
      ),
    );
  }

  final bar = Container(
    height: h,
    decoration: BoxDecoration(
      color: c2 == null ? c1 : null,
      gradient: c2 == null ? null : LinearGradient(colors: [c1, c2]),
      borderRadius: docked ? BorderRadius.vertical(top: Radius.circular(radius)) : BorderRadius.circular(radius),
      border: bw > 0 ? Border.all(color: _col(c['borderColor']) ?? teal, width: bw) : null,
      boxShadow: _flag(c['shadow'], true) ? [BoxShadow(color: Colors.black.withOpacity(.10), blurRadius: 20, offset: const Offset(0, 6))] : null,
    ),
    child: Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [for (var i = 0; i < 5; i++) item(i)]),
  );

  return Container(
    height: h + 70,
    clipBehavior: Clip.antiAlias,
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(14),
      gradient: const LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Color(0xFFC5E8F5), Color(0xFFE8E4F5), Color(0xFFF3EEFA)]),
    ),
    child: Stack(clipBehavior: Clip.none, children: [
      Positioned(
        top: 14,
        left: 14,
        right: 14,
        child: Row(children: [
          Container(width: 44, height: 44, decoration: BoxDecoration(color: Colors.white.withOpacity(.7), borderRadius: BorderRadius.circular(12))),
          const SizedBox(width: 10),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Container(height: 10, width: 120, decoration: BoxDecoration(color: Colors.white.withOpacity(.8), borderRadius: BorderRadius.circular(5))),
            const SizedBox(height: 6),
            Container(height: 8, width: 80, decoration: BoxDecoration(color: Colors.white.withOpacity(.6), borderRadius: BorderRadius.circular(4))),
          ])),
        ]),
      ),
      Positioned(
        left: side,
        right: side,
        bottom: docked ? 0 : 10,
        child: Stack(clipBehavior: Clip.none, children: [
          bar,
          if (emoji.isNotEmpty)
            Positioned(
              top: -14,
              left: pos == 'left' ? 18 : (pos == 'center' ? 0 : null),
              right: pos == 'right' ? 18 : (pos == 'center' ? 0 : null),
              child: IgnorePointer(child: Text(emoji, textAlign: TextAlign.center, style: const TextStyle(fontSize: 22))),
            ),
        ]),
      ),
    ]),
  );
}

// ───────────────────────────────────────────────────────────────────────────────
class NavUiTab extends StatefulWidget {
  const NavUiTab({super.key});
  @override
  State<NavUiTab> createState() => _NavUiTabState();
}

class _NavUiTabState extends State<NavUiTab> {
  Map<String, dynamic> cfg = {...kNavUiDefaults};
  String saved = '';
  String? loadError;
  bool loading = true, busy = false;
  int sel = 1;
  final _ctl = <String, TextEditingController>{};

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    for (final c in _ctl.values) {
      c.dispose();
    }
    super.dispose();
  }

  TextEditingController _tc(String k) => _ctl.putIfAbsent(k, () => TextEditingController(text: s(cfg[k])));

  void _sync() {
    for (final e in _ctl.entries) {
      final t = s(cfg[e.key]);
      if (e.value.text != t) e.value.text = t;
    }
  }

  Map<String, dynamic> _sorted(Map<String, dynamic> m) => {for (final k in (m.keys.toList()..sort())) k: m[k]};
  bool get dirty => jsonEncode(_sorted(cfg)) != saved;

  Future<void> _load() async {
    setState(() {
      loading = true;
      loadError = null;
    });
    try {
      final m = await Db.map('navUiConfig');
      // no row yet = starts OFF, so opening this tab never changes the store by itself
      cfg = {...kNavUiDefaults, if (m.isEmpty) 'enabled': false, for (final e in m.entries) if (kNavUiDefaults.containsKey(e.key)) e.key: e.value};
      saved = jsonEncode(_sorted(cfg));
      _sync();
    } catch (e) {
      loadError = '$e';
    }
    if (mounted) setState(() => loading = false);
  }

  void _set(String k, Object? v) => setState(() => cfg[k] = v);

  void _preset(String name) {
    setState(() {
      cfg = {
        ...kNavUiDefaults,
        'enabled': cfg['enabled'],
        'startAt': cfg['startAt'],
        'endAt': cfg['endAt'],
        for (var i = 0; i < 5; i++) 'label$i': cfg['label$i'],
        ..._presets[name]!,
      };
      _sync();
    });
  }

  Future<void> _save() async {
    setState(() => busy = true);
    try {
      final st = (cfg['startAt'] is num ? (cfg['startAt'] as num).toInt() : 0), en = (cfg['endAt'] is num ? (cfg['endAt'] as num).toInt() : 0);
      if (st > 0 && en > 0 && en < st) throw 'The end must be after the start.';
      await Db.upd('navUiConfig', {...cfg});
      await Db.audit('NAV_UI', 'config', 'navUiConfig', 'Navigation bar', _flag(cfg['enabled'], false) ? 'saved (ON)' : 'saved (OFF)');
      saved = jsonEncode(_sorted(cfg));
      if (mounted) toast(context, 'Saved. The bar changes within moments while it is ON and inside its window.');
    } catch (e) {
      if (mounted) toast(context, 'Failed: $e');
    }
    if (mounted) setState(() => busy = false);
  }

  Future<void> _reset() async {
    if (!await confirm(context, 'Back to the built-in bar?', 'Removes the custom design and schedule. Everyone sees the original bottom bar.') || !mounted) return;
    setState(() => busy = true);
    try {
      await Db.del('navUiConfig');
      await Db.audit('NAV_UI', 'config', 'navUiConfig', 'Navigation bar', 'reset to default');
      cfg = {...kNavUiDefaults, 'enabled': false};
      saved = jsonEncode(_sorted(cfg));
      _sync();
      if (mounted) toast(context, 'Reset to the built-in bar');
    } catch (e) {
      if (mounted) toast(context, 'Failed: $e');
    }
    if (mounted) setState(() => busy = false);
  }

  // ───────────── controls ─────────────
  Widget _section(String title, List<Widget> kids) => Card(
        margin: const EdgeInsets.only(bottom: 10),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 12, 14, 8),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 15)),
            const SizedBox(height: 6),
            ...kids,
          ]),
        ),
      );

  Widget _choice(String label, String key, Map<String, String> opts) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label, style: const TextStyle(fontSize: 12, color: Colors.grey)),
          const SizedBox(height: 4),
          Wrap(spacing: 8, runSpacing: 4, children: [
            for (final e in opts.entries) ChoiceChip(label: Text(e.value), selected: s(cfg[key]) == e.key, onSelected: (_) => _set(key, e.key)),
          ]),
        ]),
      );

  Widget _slider(String label, String key, double lo, double hi, {String unit = ' px'}) {
    final v = _num(cfg[key], lo, lo, hi);
    return Row(children: [
      SizedBox(width: 108, child: Text(label, style: const TextStyle(fontSize: 13))),
      Expanded(child: Slider(value: v, min: lo, max: hi, divisions: (hi - lo).round(), label: '${v.round()}$unit', onChanged: (x) => _set(key, x.round()))),
      SizedBox(width: 48, child: Text('${v.round()}$unit', textAlign: TextAlign.end, style: const TextStyle(fontSize: 12, color: Colors.grey))),
    ]);
  }

  Widget _switch(String label, String key, [bool d = true]) => SwitchListTile(
        dense: true,
        contentPadding: EdgeInsets.zero,
        title: Text(label, style: const TextStyle(fontSize: 14)),
        value: _flag(cfg[key], d),
        onChanged: (x) => _set(key, x),
      );

  Widget _colorField(String label, String key) {
    final t = s(cfg[key]);
    final bad = t.isNotEmpty && !_hexOk(t);
    final cur = _col(t);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(
            width: 30,
            height: 30,
            decoration: BoxDecoration(color: cur ?? Colors.transparent, shape: BoxShape.circle, border: Border.all(color: Colors.grey.withOpacity(.5))),
            child: cur == null ? const Icon(Icons.format_color_reset, size: 16, color: Colors.grey) : null,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: TextField(
              controller: _tc(key),
              autocorrect: false,
              decoration: InputDecoration(labelText: '$label (blank = default)', hintText: '#RRGGBB', isDense: true, border: const OutlineInputBorder(), errorText: bad ? 'Use a hex colour like #2563EB' : null),
              onChanged: (x) => setState(() => cfg[key] = x.trim()),
            ),
          ),
        ]),
        const SizedBox(height: 6),
        Wrap(spacing: 6, runSpacing: 6, children: [
          for (final h in _swatches)
            InkWell(
              customBorder: const CircleBorder(),
              onTap: () {
                _tc(key).text = h;
                _set(key, h);
              },
              child: Container(width: 26, height: 26, decoration: BoxDecoration(color: _col(h), shape: BoxShape.circle, border: Border.all(color: Colors.grey.withOpacity(.5)))),
            ),
          ActionChip(
            visualDensity: VisualDensity.compact,
            label: const Text('Default', style: TextStyle(fontSize: 11)),
            onPressed: () {
              _tc(key).text = '';
              _set(key, '');
            },
          ),
        ]),
      ]),
    );
  }

  Widget _text(String label, String key, {String? hint, int? maxLen}) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: TextField(
          controller: _tc(key),
          maxLength: maxLen,
          decoration: InputDecoration(labelText: label, hintText: hint, isDense: true, border: const OutlineInputBorder(), counterText: ''),
          onChanged: (x) => setState(() => cfg[key] = x),
        ),
      );

  Widget _date(String label, String key) {
    final n = ni(cfg[key]);
    return ListTile(
      dense: true,
      contentPadding: EdgeInsets.zero,
      title: Text(label),
      subtitle: Text(n == 0 ? 'No limit' : when(n)),
      trailing: n == 0 ? const Icon(Icons.event) : IconButton(icon: const Icon(Icons.clear), onPressed: () => _set(key, 0)),
      onTap: () async {
        final now = DateTime.now();
        final init = n == 0 ? now : DateTime.fromMillisecondsSinceEpoch(n);
        final d = await showDatePicker(context: context, initialDate: init, firstDate: DateTime(2024), lastDate: DateTime(2040));
        if (d == null || !mounted) return;
        final tm = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(init));
        if (tm == null) return;
        _set(key, DateTime(d.year, d.month, d.day, tm.hour, tm.minute).millisecondsSinceEpoch);
      },
    );
  }

  @override
  Widget build(BuildContext c) {
    if (loading) return const Center(child: CircularProgressIndicator());
    if (loadError != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Text('Could not load: $loadError', textAlign: TextAlign.center),
            const SizedBox(height: 10),
            FilledButton(onPressed: _load, child: const Text('Try again')),
          ]),
        ),
      );
    }
    final now = ms();
    final on = _flag(cfg['enabled'], false);
    final live = on && (ni(cfg['startAt']) == 0 || now >= ni(cfg['startAt'])) && (ni(cfg['endAt']) == 0 || now <= ni(cfg['endAt']));
    return Column(children: [
      Container(
        width: double.infinity,
        color: Theme.of(c).colorScheme.surfaceContainerHighest.withOpacity(.5),
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            const Text('LIVE PREVIEW (tap the tabs)', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
            const Spacer(),
            tagChip(on ? (live ? 'LIVE NOW' : 'ON · outside schedule') : 'OFF', live ? Colors.green : Colors.grey),
          ]),
          const SizedBox(height: 6),
          navBarPreview(cfg, sel, (i) => setState(() => sel = i)),
        ]),
      ),
      Expanded(
        child: ListView(padding: const EdgeInsets.all(12), children: [
          _section('Switch and schedule', [
            _switch('Custom bar is ON', 'enabled', false),
            _date('Starts at', 'startAt'),
            _date('Ends at', 'endAt'),
            const Text('Outside this window (or when OFF) every user sees the original bar. Leave both empty to keep it on until you turn it off.', style: TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 6),
          ]),
          _section('Festival and event looks', [
            const Text('Tap one, then fine-tune below. Your schedule and tab names are kept.', style: TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 6),
            Wrap(spacing: 8, runSpacing: 4, children: [for (final n in _presets.keys) ActionChip(label: Text(n), onPressed: () => _preset(n))]),
            const SizedBox(height: 6),
          ]),
          _section('Shape', [
            _choice('Style', 'style', const {'floating': 'Floating', 'docked': 'Full width'}),
            _slider('Height', 'height', 52, 88),
            _slider('Corner radius', 'radius', 0, 40),
            if (s(cfg['style']) != 'docked') _slider('Side margin', 'sideMargin', 0, 32),
            _switch('Soft shadow', 'shadow'),
            _slider('Border', 'borderWidth', 0, 4),
            if (_num(cfg['borderWidth'], 0, 0, 4) > 0) _colorField('Border colour', 'borderColor'),
          ]),
          _section('Colours', [
            _colorField('Bar colour', 'barColor'),
            _colorField('Bar colour 2 (makes a gradient)', 'barColor2'),
            _colorField('Selected highlight', 'pillColor'),
            _colorField('Selected icon', 'selIconColor'),
            _colorField('Other icons', 'iconColor'),
            _colorField('Selected name', 'selLabelColor'),
            _colorField('Other names', 'labelColor'),
          ]),
          _section('Tabs', [
            _choice('Selected tab is shown as', 'indicator', const {'pill': 'Filled pill', 'line': 'Line', 'none': 'Icon colour only'}),
            _slider('Icon size', 'iconSize', 18, 30),
            _choice('Names', 'labelMode', const {'always': 'Always', 'selected': 'Selected only', 'never': 'Hidden'}),
            const Text('Rename the tabs for a festival (blank = normal name, max 14 letters).', style: TextStyle(fontSize: 12, color: Colors.grey)),
            for (var i = 0; i < 5; i++) _text('${_tabs[i].$1} tab name', 'label$i', maxLen: 14),
          ]),
          _section('Festive touch', [
            _text('Emoji on the bar (1 or 2)', 'emoji', hint: 'e.g. 🎄', maxLen: 4),
            _choice('Position', 'emojiPosition', const {'left': 'Left', 'center': 'Centre', 'right': 'Right'}),
          ]),
          const SizedBox(height: 70),
        ]),
      ),
      SafeArea(
        top: false,
        child: Container(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
          decoration: BoxDecoration(color: Theme.of(c).colorScheme.surface, border: Border(top: BorderSide(color: Colors.grey.withOpacity(.3)))),
          child: Row(children: [
            OutlinedButton(onPressed: busy ? null : _reset, child: const Text('Built-in bar')),
            const SizedBox(width: 8),
            OutlinedButton(onPressed: busy || !dirty ? null : _load, child: const Text('Undo')),
            const SizedBox(width: 8),
            Expanded(
              child: FilledButton.icon(
                onPressed: busy || !dirty ? null : _save,
                icon: busy ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.check),
                label: Text(dirty ? 'Save bar' : 'Saved'),
              ),
            ),
          ]),
        ),
      ),
    ]);
  }
}
