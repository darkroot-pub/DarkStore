import 'dart:convert';
import 'package:flutter/material.dart';
import 'core.dart';

// ───────────────────────────────────────────────────────────────────────────────
// App box designer: the look of the app cards in shelves, "see all" grids and search rows.
// Saves one JSON row, `app_config` key `appBoxUiConfig`. The store app reads it live. Every default
// equals the built-in card, so with no row (or OFF) people see the original card.
// ───────────────────────────────────────────────────────────────────────────────

const Map<String, dynamic> kAppBoxDefaults = {
  'enabled': true,
  'width': 112,
  'gridColumns': 3,
  'logoSize': 62,
  'logoShape': 'rounded',
  'logoRadius': 16,
  'cardRadius': 20,
  'cardColor': '',
  'cardColor2': '',
  'borderWidth': 0,
  'borderColor': '',
  'shadow': 'soft',
  'nameSize': 12,
  'nameWeight': 'semibold',
  'nameColor': '',
  'nameLines': 2,
  'align': 'center',
  'showSubtitle': true,
  'showRating': true,
  'ratingStyle': 'text',
  'showTags': false,
  'tagColor': '',
};

const _presets = <String, Map<String, dynamic>>{
  'Built-in': {},
  'Clean outline': {'borderWidth': 1, 'borderColor': '#E2E8F0', 'shadow': 'none', 'cardRadius': 16},
  'Bold gradient': {'cardColor': '#2563EB', 'cardColor2': '#7C3AED', 'nameColor': '#FFFFFF', 'shadow': 'strong', 'ratingStyle': 'pill', 'tagColor': '#F59E0B'},
  'Dark card': {'cardColor': '#0F172A', 'nameColor': '#F8FAFC', 'shadow': 'strong', 'borderWidth': 1, 'borderColor': '#334155'},
  'Round icons': {'logoShape': 'circle', 'cardRadius': 28},
  'Sharp': {'logoShape': 'square', 'cardRadius': 6, 'shadow': 'none', 'borderWidth': 1, 'borderColor': '#CBD5E1'},
  'Compact 4-up': {'gridColumns': 4, 'logoSize': 52, 'nameSize': 11, 'nameLines': 1, 'showSubtitle': false, 'width': 96},
  'Big 2-up': {'gridColumns': 2, 'logoSize': 80, 'nameSize': 14, 'width': 150, 'ratingStyle': 'pill'},
  'With labels': {'showTags': true, 'tagColor': '#EF4444'},
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

const _sample = <(String, String, double, String?, Color, IconData)>[
  ('Space Racing 3D', 'Games', 4.6, 'NEW', Color(0xFF6366F1), Icons.rocket_launch),
  ('Quick Notes', 'Tools', 4.2, 'HOT', Color(0xFF10B981), Icons.edit_note),
  ('Photo Editor Pro', 'Photography', 4.8, 'TOP', Color(0xFFF59E0B), Icons.photo_camera),
  ('Weather Live', 'Weather', 3.9, null, Color(0xFF0EA5E9), Icons.wb_sunny),
];

/// One app card drawn like the store app draws it (light theme), for the preview.
Widget appBoxCard(Map<String, dynamic> c, int i, {double? width}) {
  const ink = Color(0xFF1B2230), mute = Color(0xFF8B95A5), sub = Color(0xFF5B6678), surface = Colors.white, accent = Color(0xFF00A8C5);
  final sm = _sample[i % _sample.length];
  final w = width ?? _num(c['width'], 112, 96, 170);
  final logo = _num(c['logoSize'], 62, 44, 96);
  final shape = _pick(c['logoShape'], const ['rounded', 'circle', 'square'], 'rounded');
  final logoR = shape == 'circle' ? logo / 2 : (shape == 'square' ? 0.0 : _num(c['logoRadius'], 16, 0, 48).clamp(0.0, logo / 2).toDouble());
  final radius = _num(c['cardRadius'], 20, 0, 36);
  final c1 = _col(c['cardColor']);
  final c2 = _col(c['cardColor2']);
  final bw = _num(c['borderWidth'], 0, 0, 3);
  final shadow = _pick(c['shadow'], const ['none', 'soft', 'strong'], 'soft');
  final nameSize = _num(c['nameSize'], 12, 10, 16);
  final lines = _num(c['nameLines'], 2, 1, 2).round();
  final centered = _pick(c['align'], const ['center', 'start'], 'center') != 'start';
  final weight = const {'normal': FontWeight.w500, 'semibold': FontWeight.w600, 'bold': FontWeight.w800}[_pick(c['nameWeight'], const ['normal', 'semibold', 'bold'], 'semibold')]!;
  final showSub = _flag(c['showSubtitle'], true), showRating = _flag(c['showRating'], true);
  final pill = _pick(c['ratingStyle'], const ['text', 'pill'], 'text') == 'pill';
  final showTag = _flag(c['showTags'], false) && sm.$4 != null;
  final nameColor = _col(c['nameColor']) ?? ink;
  final br = BorderRadius.circular(radius);

  Widget bottom() {
    if (showRating && sm.$3 > 0) {
      final r = Row(mainAxisSize: MainAxisSize.min, children: [
        Text(sm.$3.toStringAsFixed(1), style: const TextStyle(fontSize: 11, color: sub)),
        const SizedBox(width: 2),
        const Icon(Icons.star_rounded, size: 13, color: Color(0xFFFFB800)),
      ]);
      return pill
          ? DecoratedBox(
              decoration: BoxDecoration(color: const Color(0xFFFFB800).withOpacity(.16), borderRadius: BorderRadius.circular(10)),
              child: Padding(padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 1), child: r))
          : r;
    }
    if (!showSub) return const SizedBox.shrink();
    return Text(sm.$2, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 11, color: mute));
  }

  return DecoratedBox(
    decoration: BoxDecoration(borderRadius: br, boxShadow: shadow == 'none' ? const [] : [BoxShadow(color: Colors.black.withOpacity(shadow == 'strong' ? .16 : .05), blurRadius: shadow == 'strong' ? 18 : 12, offset: Offset(0, shadow == 'strong' ? 6 : 4))]),
    child: Material(
      color: c2 != null ? Colors.transparent : (c1 ?? surface),
      shape: RoundedRectangleBorder(borderRadius: br, side: bw > 0 ? BorderSide(color: _col(c['borderColor']) ?? const Color(0xFFE2E8F0), width: bw) : BorderSide.none),
      clipBehavior: Clip.antiAlias,
      child: Ink(
        width: w,
        padding: const EdgeInsets.fromLTRB(10, 12, 10, 10),
        decoration: BoxDecoration(gradient: c2 == null ? null : LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: [c1 ?? surface, c2])),
        child: Stack(children: [
          Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: centered ? CrossAxisAlignment.center : CrossAxisAlignment.start, children: [
            Container(
              width: logo,
              height: logo,
              decoration: BoxDecoration(color: sm.$5, borderRadius: BorderRadius.circular(logoR)),
              child: Icon(sm.$6, color: Colors.white, size: logo * .5),
            ),
            const SizedBox(height: 8),
            SizedBox(
              height: lines * nameSize * 1.2 + .4,
              width: double.infinity,
              child: Align(
                alignment: centered ? Alignment.center : Alignment.centerLeft,
                child: Text(sm.$1, maxLines: lines, overflow: TextOverflow.ellipsis, textAlign: centered ? TextAlign.center : TextAlign.start, style: TextStyle(fontSize: nameSize, fontWeight: weight, color: nameColor, height: 1.2)),
              ),
            ),
            if (showSub || showRating) ...[
              const SizedBox(height: 6),
              SizedBox(height: 16, width: double.infinity, child: Align(alignment: centered ? Alignment.center : Alignment.centerLeft, child: bottom())),
            ],
          ]),
          if (showTag)
            Positioned(
              top: -12,
              left: -10,
              child: DecoratedBox(
                decoration: BoxDecoration(color: _col(c['tagColor']) ?? accent, borderRadius: const BorderRadius.only(topLeft: Radius.circular(4), bottomRight: Radius.circular(10))),
                child: Padding(padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2), child: Text(sm.$4!, style: const TextStyle(fontSize: 9, fontWeight: FontWeight.w800, color: Colors.white, letterSpacing: .4))),
              ),
            ),
        ]),
      ),
    ),
  );
}

// ───────────────────────────────────────────────────────────────────────────────
class AppBoxTab extends StatefulWidget {
  const AppBoxTab({super.key});
  @override
  State<AppBoxTab> createState() => _AppBoxTabState();
}

class _AppBoxTabState extends State<AppBoxTab> {
  Map<String, dynamic> cfg = {...kAppBoxDefaults};
  String saved = '';
  String? loadError;
  bool loading = true, busy = false;
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
      final m = await Db.map('appBoxUiConfig');
      // no row yet = starts OFF, so opening this tab never changes the store by itself
      cfg = {...kAppBoxDefaults, if (m.isEmpty) 'enabled': false, for (final e in m.entries) if (kAppBoxDefaults.containsKey(e.key)) e.key: e.value};
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
      cfg = {...kAppBoxDefaults, 'enabled': cfg['enabled'], ..._presets[name]!};
      _sync();
    });
  }

  Future<void> _save() async {
    setState(() => busy = true);
    try {
      await Db.upd('appBoxUiConfig', {...cfg});
      await Db.audit('APP_BOX_UI', 'config', 'appBoxUiConfig', 'App box', _flag(cfg['enabled'], false) ? 'saved (ON)' : 'saved (OFF)');
      saved = jsonEncode(_sorted(cfg));
      if (mounted) toast(context, 'Saved. App cards change within moments while it is ON.');
    } catch (e) {
      if (mounted) toast(context, 'Failed: $e');
    }
    if (mounted) setState(() => busy = false);
  }

  Future<void> _reset() async {
    if (!await confirm(context, 'Back to the built-in cards?', 'Removes the custom design. Everyone sees the original app cards.') || !mounted) return;
    setState(() => busy = true);
    try {
      await Db.del('appBoxUiConfig');
      await Db.audit('APP_BOX_UI', 'config', 'appBoxUiConfig', 'App box', 'reset to default');
      cfg = {...kAppBoxDefaults, 'enabled': false};
      saved = jsonEncode(_sorted(cfg));
      _sync();
      if (mounted) toast(context, 'Reset to the built-in cards');
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
    final on = _flag(cfg['enabled'], false);
    final cols = _num(cfg['gridColumns'], 3, 2, 4).round();
    return Column(children: [
      Container(
        width: double.infinity,
        color: Theme.of(c).colorScheme.surfaceContainerHighest.withOpacity(.5),
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 10),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            const Text('LIVE PREVIEW', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
            const Spacer(),
            tagChip(on ? 'ON' : 'OFF (built-in cards)', on ? Colors.green : Colors.grey),
          ]),
          const SizedBox(height: 10),
          // shelf: the Home rows
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            clipBehavior: Clip.none,
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              for (var i = 0; i < 3; i++) Padding(padding: const EdgeInsets.only(right: 10), child: appBoxCard(cfg, i)),
            ]),
          ),
          const SizedBox(height: 4),
          Text('Shelf · grid shows $cols per row', style: const TextStyle(fontSize: 11, color: Colors.grey)),
        ]),
      ),
      Expanded(
        child: ListView(padding: const EdgeInsets.all(12), children: [
          _section('Switch', [
            _switch('Custom cards are ON', 'enabled', false),
            const Text('OFF (or no saved design) = every user sees the original card. Cards are used on Home shelves, "see all" grids, suggestions and similar-app rows; search rows borrow the corners, colour and border.', style: TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 6),
          ]),
          _section('Looks', [
            const Text('Tap one, then fine-tune below.', style: TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 6),
            Wrap(spacing: 8, runSpacing: 4, children: [for (final n in _presets.keys) ActionChip(label: Text(n), onPressed: () => _preset(n))]),
            const SizedBox(height: 6),
          ]),
          _section('Size', [
            _slider('Card width', 'width', 96, 170),
            _choice('Grid columns', 'gridColumns', const {'2': '2', '3': '3', '4': '4'}),
            _slider('Icon size', 'logoSize', 44, 96),
          ]),
          _section('Icon shape', [
            _choice('Shape', 'logoShape', const {'rounded': 'Rounded', 'circle': 'Circle', 'square': 'Square'}),
            if (s(cfg['logoShape']) == 'rounded') _slider('Icon corners', 'logoRadius', 0, 48),
          ]),
          _section('Card', [
            _slider('Corner radius', 'cardRadius', 0, 36),
            _choice('Shadow', 'shadow', const {'none': 'None', 'soft': 'Soft', 'strong': 'Strong'}),
            _slider('Border', 'borderWidth', 0, 3),
            if (_num(cfg['borderWidth'], 0, 0, 3) > 0) _colorField('Border colour', 'borderColor'),
            _colorField('Card colour', 'cardColor'),
            _colorField('Card colour 2 (makes a gradient)', 'cardColor2'),
          ]),
          _section('Name', [
            _slider('Text size', 'nameSize', 10, 16),
            _choice('Weight', 'nameWeight', const {'normal': 'Normal', 'semibold': 'Semi-bold', 'bold': 'Bold'}),
            _choice('Lines', 'nameLines', const {'1': '1 line', '2': '2 lines'}),
            _choice('Alignment', 'align', const {'center': 'Centre', 'start': 'Left'}),
            _colorField('Name colour', 'nameColor'),
          ]),
          _section('Under the name', [
            _switch('Show rating', 'showRating'),
            _choice('Rating style', 'ratingStyle', const {'text': 'Text', 'pill': 'Yellow pill'}),
            _switch('Show category when there is no rating', 'showSubtitle'),
          ]),
          _section('Corner label', [
            _switch('Show NEW / HOT / TOP', 'showTags', false),
            const Text('NEW = recently added, HOT = popular, TOP = featured.', style: TextStyle(fontSize: 12, color: Colors.grey)),
            if (_flag(cfg['showTags'], false)) _colorField('Label colour', 'tagColor'),
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
            OutlinedButton(onPressed: busy ? null : _reset, child: const Text('Built-in')),
            const SizedBox(width: 8),
            OutlinedButton(onPressed: busy || !dirty ? null : _load, child: const Text('Undo')),
            const SizedBox(width: 8),
            Expanded(
              child: FilledButton.icon(
                onPressed: busy || !dirty ? null : _save,
                icon: busy ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.check),
                label: Text(dirty ? 'Save cards' : 'Saved'),
              ),
            ),
          ]),
        ),
      ),
    ]);
  }
}
