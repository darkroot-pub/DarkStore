import 'dart:convert';
import 'package:flutter/material.dart';
import 'core.dart';
import 'uploader.dart';

// ───────────────────────────────────────────────────────────────────────────────
// Profile card designer.
// Saves one JSON row, `app_config` key `profileUiConfig`. The store app reads it live
// (and keeps the last copy on the phone), so a saved design shows up for everyone
// without a new app build. Every key here has the same default as the built-in design.
// ───────────────────────────────────────────────────────────────────────────────

const Map<String, dynamic> kProfileUiDefaults = {
  'enabled': true,
  'bannerStyle': 'gradient',
  'bannerColorA': '',
  'bannerColorB': '',
  'premiumBanner': true,
  'bannerImage': '',
  'bannerHeight': 116,
  'bannerBlobs': true,
  'avatarShape': 'circle',
  'avatarSize': 88,
  'ringStyle': 'auto',
  'ringColor': '',
  'cardColor': '',
  'cardRadius': 28,
  'cardShadow': true,
  'align': 'center',
  'nameSize': 21,
  'nameColor': '',
  'tagline': '',
  'showEmail': true,
  'showBadges': true,
  'showStats': true,
  'showBio': true,
  'showLocation': true,
  'showLinks': true,
  'showVerified': true,
  'statsStyle': 'tiles',
  'badgeStyle': 'soft',
};

const _presets = <String, Map<String, dynamic>>{
  'Default': {},
  'Ocean': {'bannerColorA': '#0EA5E9', 'bannerColorB': '#2563EB', 'premiumBanner': false},
  'Sunset': {'bannerColorA': '#F97316', 'bannerColorB': '#DB2777', 'premiumBanner': false},
  'Midnight': {'bannerColorA': '#0F172A', 'bannerColorB': '#4338CA', 'premiumBanner': false, 'ringStyle': 'accent'},
  'Mint': {'bannerColorA': '#10B981', 'bannerColorB': '#0D9488', 'premiumBanner': false},
  'Minimal': {'bannerStyle': 'none', 'statsStyle': 'outlined', 'badgeStyle': 'outlined', 'cardShadow': false, 'ringStyle': 'accent'},
  'Left aligned': {'align': 'start', 'avatarShape': 'rounded', 'bannerHeight': 90, 'statsStyle': 'plain', 'badgeStyle': 'solid'},
};

const _swatches = ['#00A8C5', '#2563EB', '#7C3AED', '#DB2777', '#EF4444', '#F59E0B', '#10B981', '#0F172A', '#FFFFFF', '#64748B'];

// ───────────── tiny readers (same rules as the store app) ─────────────
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

/// Who the preview pretends to be.
class _Who {
  final String label;
  final bool admin, dev, premium, guest;
  const _Who(this.label, {this.admin = false, this.dev = false, this.premium = false, this.guest = false});
}

const _whos = [
  _Who('Owner', admin: true, dev: true, premium: true),
  _Who('Developer', dev: true),
  _Who('Member'),
  _Who('Guest', guest: true),
];

/// Draws the profile card like the store app does (light theme), from a config map.
Widget profileCardPreview(Map<String, dynamic> c, _Who w) {
  const teal = Color(0xFF00A8C5);
  const ink = Color(0xFF1A2332), sub = Color(0xFF5A6577), mute = Color(0xFF8B95A5);
  const field = Color(0xFFF0F4F8), line = Color(0xFFE2E8F0);
  const goldA = Color(0xFFF59E0B), goldB = Color(0xFFEF6C00);

  final center = _pick(c['align'], const ['center', 'start'], 'center') != 'start';
  final bStyle = _pick(c['bannerStyle'], const ['gradient', 'solid', 'image', 'none'], 'gradient');
  final bh = _num(c['bannerHeight'], 116, 0, 220);
  final hasBanner = bStyle != 'none' && bh > 0;
  final gold = w.premium && _flag(c['premiumBanner'], true);
  final a = gold ? goldA : (_col(c['bannerColorA']) ?? teal);
  final b = gold ? goldB : (_col(c['bannerColorB']) ?? Color.lerp(a, const Color(0xFF6C5CE7), .65)!);
  final shape = _pick(c['avatarShape'], const ['circle', 'rounded', 'square'], 'circle');
  final size = _num(c['avatarSize'], 88, 56, 128);
  final ringStyle = _pick(c['ringStyle'], const ['auto', 'accent', 'color', 'none'], 'auto');
  final outer = ringStyle == 'none' ? size + 8 : size + 16;
  final mid = size + 8;
  final circle = shape == 'circle';
  double rad(double x) => shape == 'square' ? x * .12 : x * .3;
  BoxDecoration deco(double x, {Color? color, Gradient? gradient, List<BoxShadow>? shadow}) => BoxDecoration(
        shape: circle ? BoxShape.circle : BoxShape.rectangle,
        borderRadius: circle ? null : BorderRadius.circular(rad(x)),
        color: color,
        gradient: gradient,
        boxShadow: shadow,
      );
  final top = hasBanner ? (bh - outer * .52).clamp(18.0, 400.0).toDouble() : 18.0;
  final cardRadius = _num(c['cardRadius'], 28, 0, 44);
  final statsStyle = _pick(c['statsStyle'], const ['tiles', 'plain', 'outlined'], 'tiles');
  final badgeStyle = _pick(c['badgeStyle'], const ['soft', 'solid', 'outlined'], 'soft');
  final cross = center ? CrossAxisAlignment.center : CrossAxisAlignment.start;
  final rowAl = center ? MainAxisAlignment.center : MainAxisAlignment.start;
  final image = s(c['bannerImage']).startsWith('http') && bStyle == 'image';

  Widget chip(String t, Color col) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: badgeStyle == 'solid' ? col : (badgeStyle == 'outlined' ? null : col.withOpacity(.12)),
          border: badgeStyle == 'outlined' ? Border.all(color: col, width: 1.2) : null,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Text(t, style: TextStyle(fontWeight: FontWeight.w700, fontSize: 11, color: badgeStyle == 'solid' ? Colors.white : col)),
      );

  Widget stat(String l, String n) => Expanded(
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 12),
          decoration: statsStyle == 'plain'
              ? null
              : BoxDecoration(
                  color: statsStyle == 'tiles' ? field.withOpacity(.6) : null,
                  border: statsStyle == 'outlined' ? Border.all(color: line) : null,
                  borderRadius: BorderRadius.circular(18)),
          child: Column(children: [
            Text(n, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: ink)),
            Text(l, style: const TextStyle(fontSize: 12, color: sub)),
          ]),
        ),
      );

  Widget link(IconData i, String t, String h) => Expanded(
        child: Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(border: Border.all(color: line), borderRadius: BorderRadius.circular(99)),
          child: Row(children: [
            CircleAvatar(radius: 14, backgroundColor: teal.withOpacity(.12), child: Icon(i, size: 15, color: teal)),
            const SizedBox(width: 6),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(h, style: const TextStyle(fontSize: 10, color: mute)),
                Text(t, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: ink)),
              ]),
            ),
          ]),
        ),
      );

  final goldColors = const [Color(0xFFFFD54F), goldA, goldB, Color(0xFFFFD54F)];
  Widget? ring;
  if (ringStyle == 'accent') {
    ring = Container(width: outer, height: outer, decoration: deco(outer, color: teal));
  } else if (ringStyle == 'color') {
    ring = Container(width: outer, height: outer, decoration: deco(outer, color: _col(c['ringColor']) ?? teal));
  } else if (ringStyle == 'auto') {
    ring = w.premium
        ? Container(
            width: outer,
            height: outer,
            decoration: circle
                ? BoxDecoration(shape: BoxShape.circle, gradient: SweepGradient(colors: goldColors))
                : deco(outer, gradient: LinearGradient(colors: goldColors)))
        : Container(
            width: outer,
            height: outer,
            decoration: deco(outer, color: Colors.white, shadow: [BoxShadow(color: Colors.black.withOpacity(.12), blurRadius: 14)]));
  }

  final tag = s(c['tagline']).trim();
  return Container(
    clipBehavior: Clip.antiAlias,
    decoration: BoxDecoration(
      color: _col(c['cardColor']) ?? Colors.white,
      borderRadius: BorderRadius.circular(cardRadius),
      boxShadow: _flag(c['cardShadow'], true) ? [BoxShadow(color: Colors.black.withOpacity(.08), blurRadius: 18, offset: const Offset(0, 6))] : null,
    ),
    child: Stack(children: [
      if (hasBanner)
        SizedBox(
          width: double.infinity,
          height: bh,
          child: DecoratedBox(
            decoration: bStyle == 'solid' ? BoxDecoration(color: a) : BoxDecoration(gradient: LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: [a, b])),
            child: Stack(fit: StackFit.expand, children: [
              if (image) Image.network(s(c['bannerImage']), fit: BoxFit.cover, errorBuilder: (_, __, ___) => const SizedBox.shrink()),
              if (_flag(c['bannerBlobs'], true) && !image) ...[
                Positioned(right: -30, top: -40, width: 150, height: 150, child: DecoratedBox(decoration: BoxDecoration(shape: BoxShape.circle, color: Colors.white.withOpacity(.12)))),
                Positioned(left: -24, bottom: -50, width: 110, height: 110, child: DecoratedBox(decoration: BoxDecoration(shape: BoxShape.circle, color: Colors.white.withOpacity(.08)))),
              ],
            ]),
          ),
        ),
      Padding(
        padding: EdgeInsets.fromLTRB(18, top, 18, 18),
        child: Column(crossAxisAlignment: cross, children: [
          SizedBox(
            width: outer,
            height: outer,
            child: Stack(alignment: Alignment.center, children: [
              if (ring != null) ring,
              Container(
                width: mid,
                height: mid,
                padding: const EdgeInsets.all(4),
                decoration: deco(mid, color: Colors.white, shadow: ring == null ? [BoxShadow(color: Colors.black.withOpacity(.12), blurRadius: 14)] : null),
                child: Container(
                  decoration: deco(size, color: const Color(0xFFD6EEF8)),
                  child: Icon(Icons.person, size: size * .5, color: teal),
                ),
              ),
            ]),
          ),
          const SizedBox(height: 12),
          Row(mainAxisAlignment: rowAl, children: [
            Flexible(
              child: Text(w.guest ? 'Guest' : 'DarkRoot',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: _num(c['nameSize'], 21, 14, 34), fontWeight: FontWeight.w800, color: _col(c['nameColor']) ?? ink)),
            ),
            if (_flag(c['showVerified'], true) && w.admin) ...[const SizedBox(width: 5), const Icon(Icons.verified, size: 20, color: Color(0xFF3B82F6))],
          ]),
          if (tag.isNotEmpty)
            Padding(padding: const EdgeInsets.only(top: 2), child: Text(tag, textAlign: center ? TextAlign.center : TextAlign.start, style: const TextStyle(color: teal, fontSize: 13, fontWeight: FontWeight.w600))),
          if (_flag(c['showEmail'], true) && !w.guest) const Padding(padding: EdgeInsets.only(top: 2), child: Text('you@example.com', style: TextStyle(color: sub, fontSize: 13))),
          if (_flag(c['showBadges'], true)) ...[
            const SizedBox(height: 10),
            Wrap(spacing: 6, runSpacing: 6, alignment: center ? WrapAlignment.center : WrapAlignment.start, children: [
              if (w.guest) chip('GUEST', sub),
              if (w.dev) chip('DEVELOPER', teal),
              if (w.premium) chip('PREMIUM', goldA),
            ]),
          ],
          if (!w.guest) ...[
            if (_flag(c['showStats'], true)) ...[
              const SizedBox(height: 16),
              Row(children: [
                if (w.dev) ...[stat('Apps', '24'), const SizedBox(width: 8)],
                stat('Followers', '10'),
                const SizedBox(width: 8),
                stat('Following', '10'),
              ]),
            ],
            if (_flag(c['showBio'], true)) ...[
              const SizedBox(height: 12),
              SizedBox(width: double.infinity, child: Text('Owner of Dark Store', textAlign: center ? TextAlign.center : TextAlign.start, style: const TextStyle(color: ink, fontSize: 13))),
            ],
            if (_flag(c['showLocation'], true)) ...[
              const SizedBox(height: 6),
              Row(mainAxisAlignment: rowAl, children: const [Icon(Icons.place_outlined, size: 14, color: mute), SizedBox(width: 4), Text('Kathmandu, Nepal', style: TextStyle(fontSize: 12, color: mute))]),
            ],
            if (_flag(c['showLinks'], true)) ...[
              const SizedBox(height: 8),
              Row(children: [link(Icons.language_rounded, 'about-david.vercel.app', 'Website'), const SizedBox(width: 8), link(Icons.code_rounded, 'github.com/darkroot', 'GitHub')]),
            ],
          ],
        ]),
      ),
    ]),
  );
}

// ───────────────────────────────────────────────────────────────────────────────
class ProfileUiTab extends StatefulWidget {
  const ProfileUiTab({super.key});
  @override
  State<ProfileUiTab> createState() => _ProfileUiTabState();
}

class _ProfileUiTabState extends State<ProfileUiTab> {
  Map<String, dynamic> cfg = {...kProfileUiDefaults};
  String saved = '';
  String? loadError;
  bool loading = true, busy = false, uploading = false;
  _Who who = _whos.first;
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

  void _syncControllers() {
    for (final e in _ctl.entries) {
      final t = s(cfg[e.key]);
      if (e.value.text != t) e.value.text = t;
    }
  }

  bool get dirty => jsonEncode(_sorted(cfg)) != saved;

  Map<String, dynamic> _sorted(Map<String, dynamic> m) => {for (final k in (m.keys.toList()..sort())) k: m[k]};

  Future<void> _load() async {
    setState(() {
      loading = true;
      loadError = null;
    });
    try {
      final m = await Db.map('profileUiConfig');
      cfg = {...kProfileUiDefaults, for (final e in m.entries) if (kProfileUiDefaults.containsKey(e.key)) e.key: e.value};
      saved = jsonEncode(_sorted(cfg));
      _syncControllers();
    } catch (e) {
      loadError = '$e';
    }
    if (mounted) setState(() => loading = false);
  }

  void _set(String k, Object? v) => setState(() => cfg[k] = v);

  void _preset(String name) {
    setState(() {
      cfg = {...kProfileUiDefaults, 'enabled': cfg['enabled'], 'tagline': cfg['tagline'], 'bannerImage': cfg['bannerImage'], ..._presets[name]!};
      _syncControllers();
    });
  }

  Future<void> _save() async {
    setState(() => busy = true);
    try {
      await Db.upd('profileUiConfig', {...cfg});
      await Db.audit('PROFILE_UI', 'config', 'profileUiConfig', 'Profile card', 'saved');
      saved = jsonEncode(_sorted(cfg));
      if (mounted) toast(context, 'Saved. Users see the new design within moments.');
    } catch (e) {
      if (mounted) toast(context, 'Failed: $e');
    }
    if (mounted) setState(() => busy = false);
  }

  Future<void> _reset() async {
    if (!await confirm(context, 'Back to the built-in design?', 'Removes your custom design. Everyone sees the original profile card again.') || !mounted) return;
    setState(() => busy = true);
    try {
      await Db.del('profileUiConfig');
      await Db.audit('PROFILE_UI', 'config', 'profileUiConfig', 'Profile card', 'reset to default');
      cfg = {...kProfileUiDefaults};
      saved = jsonEncode(_sorted(cfg));
      _syncControllers();
      if (mounted) toast(context, 'Reset to the built-in design');
    } catch (e) {
      if (mounted) toast(context, 'Failed: $e');
    }
    if (mounted) setState(() => busy = false);
  }

  Future<void> _upload() async {
    if (uploading) return;
    setState(() => uploading = true);
    try {
      final f = await Uploader.one();
      if (f != null) {
        final url = await Uploader.upload(f, folder: 'profile-ui');
        _tc('bannerImage').text = url;
        cfg['bannerImage'] = url;
        cfg['bannerStyle'] = 'image';
      }
    } catch (e) {
      if (mounted) toast(context, '$e');
    }
    if (mounted) setState(() => uploading = false);
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
            for (final e in opts.entries)
              ChoiceChip(label: Text(e.value), selected: s(cfg[key]) == e.key, onSelected: (_) => _set(key, e.key)),
          ]),
        ]),
      );

  Widget _slider(String label, String key, double lo, double hi, {String unit = ''}) {
    final v = _num(cfg[key], lo, lo, hi);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(children: [
        SizedBox(width: 108, child: Text(label, style: const TextStyle(fontSize: 13))),
        Expanded(child: Slider(value: v, min: lo, max: hi, divisions: (hi - lo).round(), label: '${v.round()}$unit', onChanged: (x) => _set(key, x.round()))),
        SizedBox(width: 44, child: Text('${v.round()}$unit', textAlign: TextAlign.end, style: const TextStyle(fontSize: 12, color: Colors.grey))),
      ]),
    );
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

  Widget _text(String label, String key, {String? hint, Widget? trailing}) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: TextField(
          controller: _tc(key),
          decoration: InputDecoration(labelText: label, hintText: hint, isDense: true, border: const OutlineInputBorder(), suffixIcon: trailing),
          onChanged: (x) => setState(() => cfg[key] = x),
        ),
      );

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
    final h = (MediaQuery.of(c).size.height * .30).clamp(210.0, 300.0).toDouble();
    return Column(children: [
      // live preview, always visible
      Container(
        width: double.infinity,
        color: Theme.of(c).colorScheme.surfaceContainerHighest.withOpacity(.5),
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
        child: Column(children: [
          Row(children: [
            const Text('LIVE PREVIEW', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
            const SizedBox(width: 8),
            Expanded(
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                reverse: true,
                child: Row(children: [
                  for (final x in _whos)
                    Padding(
                      padding: const EdgeInsets.only(left: 6),
                      child: ChoiceChip(visualDensity: VisualDensity.compact, label: Text(x.label, style: const TextStyle(fontSize: 11)), selected: who == x, onSelected: (_) => setState(() => who = x)),
                    ),
                ]),
              ),
            ),
          ]),
          SizedBox(
            height: h,
            child: FittedBox(
              fit: BoxFit.scaleDown,
              child: SizedBox(width: 360, child: profileCardPreview(cfg, who)),
            ),
          ),
          TextButton.icon(
            icon: const Icon(Icons.fullscreen, size: 18),
            label: const Text('Full size'),
            onPressed: () => showDialog<void>(
              context: c,
              builder: (d) => Dialog(
                insetPadding: const EdgeInsets.all(12),
                child: SingleChildScrollView(padding: const EdgeInsets.all(12), child: profileCardPreview(cfg, who)),
              ),
            ),
          ),
        ]),
      ),
      Expanded(
        child: ListView(padding: const EdgeInsets.all(12), children: [
          _section('Use a custom design', [
            _switch('Custom design is ON', 'enabled'),
            const Text('Off = every user sees the original built-in profile card.', style: TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 6),
            const Text('Quick styles (keeps your tagline and banner photo)', style: TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 4),
            Wrap(spacing: 8, runSpacing: 4, children: [for (final n in _presets.keys) ActionChip(label: Text(n), onPressed: () => _preset(n))]),
            const SizedBox(height: 6),
          ]),
          _section('Banner', [
            _choice('Style', 'bannerStyle', const {'gradient': 'Gradient', 'solid': 'Solid', 'image': 'Photo', 'none': 'No banner'}),
            _colorField('Colour 1', 'bannerColorA'),
            _colorField('Colour 2', 'bannerColorB'),
            _switch('Premium members keep the gold banner', 'premiumBanner'),
            _switch('Soft shapes on the banner', 'bannerBlobs'),
            _slider('Height', 'bannerHeight', 0, 220, unit: ' px'),
            _text('Banner photo link (for the Photo style)', 'bannerImage',
                trailing: uploading
                    ? const Padding(padding: EdgeInsets.all(12), child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)))
                    : IconButton(tooltip: 'Pick photo from device', icon: const Icon(Icons.add_photo_alternate_outlined), onPressed: _upload)),
          ]),
          _section('Profile picture', [
            _choice('Shape', 'avatarShape', const {'circle': 'Circle', 'rounded': 'Rounded', 'square': 'Square'}),
            _slider('Size', 'avatarSize', 56, 128, unit: ' px'),
            _choice('Ring', 'ringStyle', const {'auto': 'Automatic', 'accent': 'Accent', 'color': 'Own colour', 'none': 'None'}),
            if (s(cfg['ringStyle']) == 'color') _colorField('Ring colour', 'ringColor'),
          ]),
          _section('Card', [
            _colorField('Card colour', 'cardColor'),
            _slider('Corner radius', 'cardRadius', 0, 44, unit: ' px'),
            _switch('Soft shadow', 'cardShadow'),
          ]),
          _section('Text', [
            _choice('Alignment', 'align', const {'center': 'Centered', 'start': 'Left'}),
            _slider('Name size', 'nameSize', 14, 34),
            _colorField('Name colour', 'nameColor'),
            _text('Tagline under the name (everyone sees it)', 'tagline', hint: 'e.g. Welcome to DarkStore'),
          ]),
          _section('What to show', [
            _switch('Verified tick (admins)', 'showVerified'),
            _switch('E-mail', 'showEmail'),
            _switch('Role badges (Developer, Premium)', 'showBadges'),
            _switch('Apps / Followers / Following', 'showStats'),
            _switch('Bio', 'showBio'),
            _switch('Location', 'showLocation'),
            _switch('Website and GitHub links', 'showLinks'),
          ]),
          _section('Styles', [
            _choice('Counters', 'statsStyle', const {'tiles': 'Tiles', 'outlined': 'Outlined', 'plain': 'Plain'}),
            _choice('Badges', 'badgeStyle', const {'soft': 'Soft', 'solid': 'Solid', 'outlined': 'Outlined'}),
          ]),
          const SizedBox(height: 8),
          const Text('Changes reach the store app live (it also remembers the last design on the phone). Colours use hex codes such as #2563EB.', style: TextStyle(fontSize: 12, color: Colors.grey)),
          const SizedBox(height: 70),
        ]),
      ),
      SafeArea(
        top: false,
        child: Container(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
          decoration: BoxDecoration(color: Theme.of(c).colorScheme.surface, border: Border(top: BorderSide(color: Colors.grey.withOpacity(.3)))),
          child: Row(children: [
            OutlinedButton(onPressed: busy ? null : _reset, child: const Text('Built-in design')),
            const SizedBox(width: 8),
            OutlinedButton(onPressed: busy || !dirty ? null : _load, child: const Text('Undo')),
            const SizedBox(width: 8),
            Expanded(
              child: FilledButton.icon(
                onPressed: busy || !dirty ? null : _save,
                icon: busy ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.check),
                label: Text(dirty ? 'Save design' : 'Saved'),
              ),
            ),
          ]),
        ),
      ),
    ]);
  }
}
