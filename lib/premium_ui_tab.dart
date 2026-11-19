import 'dart:convert';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'core.dart';
import 'uploader.dart';

// ───────────────────────────────────────────────────────────────────────────────
// Premium card designer.
// Saves one JSON row, `app_config` key `premiumUiConfig`. The store app reads it live, so a saved
// design reaches every Premium member (and the "locked" view of free users) without a new build.
// Every key has the same default as the built-in card; "enabled: false" = built-in card.
// ───────────────────────────────────────────────────────────────────────────────

const Map<String, dynamic> kPremiumUiDefaults = {
  'enabled': true,
  'cardStyle': 'tint',
  'colorA': '',
  'colorB': '',
  'borderColor': '',
  'textColor': '',
  'accent': '',
  'angle': 135,
  'radius': 18,
  'borderWidth': 2,
  'padding': 16,
  'bgOpacity': 25,
  'shadow': false,
  'glow': false,
  'bgImage': '',
  'title': 'DARKSTORE PREMIUM',
  'icon': 'star',
  'badgeActive': 'ACTIVE MEMBER',
  'badgeInactive': 'STANDARD TIER',
  'titleAlign': 'start',
  'titleSize': 14,
  'showBadge': true,
  'introActive': 'Thank you for being a Premium member! Your badge and highlighted reviews are visible to everyone.',
  'introFree': 'Premium is never free. Earn it by inviting friends, or get it from the DarkStore admin.',
  'showBenefits': true,
  'benefitsTitle': '',
  'benefitsLayout': 'list',
  'perks': <dynamic>[],
  'showStatus': true,
  'statusTitle': 'Premium Membership Status',
  'statusActive': 'Premium features active',
  'statusFree': 'Locked - invite friends to unlock Premium',
  'ctaShow': true,
  'ctaText': 'Invite friends to unlock',
  'ctaLink': '/referral',
  'ctaStyle': 'outlined',
  'engineShow': true,
  'engineTitle': 'PREMIUM CUSTOM STYLING ENGINE',
  'engineSubtitle': "Change the store's accent and highlight colour everywhere.",
  'allowCustomColor': true,
  'hideBuiltInSwatches': false,
  'swatches': <dynamic>[],
  'blocks': <dynamic>[],
};

const _presets = <String, Map<String, dynamic>>{
  'Built-in': {},
  'Royal gold': {'cardStyle': 'gradient', 'colorA': '#B45309', 'colorB': '#F59E0B', 'angle': 135, 'borderWidth': 0, 'glow': true, 'accent': '#FDE68A', 'radius': 24},
  'Midnight': {'cardStyle': 'gradient', 'colorA': '#0F172A', 'colorB': '#4338CA', 'angle': 150, 'borderWidth': 0, 'shadow': true, 'accent': '#A5B4FC', 'radius': 24},
  'Aurora': {'cardStyle': 'gradient', 'colorA': '#0EA5E9', 'colorB': '#7C3AED', 'angle': 120, 'borderWidth': 0, 'glow': true, 'accent': '#FFFFFF', 'radius': 28},
  'Glass': {'cardStyle': 'glass', 'borderWidth': 1, 'shadow': true, 'radius': 28},
  'Minimal': {'cardStyle': 'outline', 'borderWidth': 1, 'radius': 14, 'benefitsLayout': 'chips'},
  'Rose': {'cardStyle': 'gradient', 'colorA': '#BE185D', 'colorB': '#F97316', 'angle': 135, 'borderWidth': 0, 'accent': '#FFFFFF', 'radius': 22},
};

const _swatches = ['#F59E0B', '#2563EB', '#7C3AED', '#DB2777', '#EF4444', '#10B981', '#0F172A', '#FFFFFF', '#64748B'];

const _icons = <String, IconData>{
  'star': Icons.star_rounded,
  'crown': Icons.workspace_premium_rounded,
  'diamond': Icons.diamond_rounded,
  'bolt': Icons.bolt_rounded,
  'heart': Icons.favorite_rounded,
  'shield': Icons.shield_rounded,
  'rocket': Icons.rocket_launch_rounded,
  'gift': Icons.card_giftcard_rounded,
  'check': Icons.check_circle_rounded,
  'lock': Icons.lock_rounded,
  'palette': Icons.palette_rounded,
  'chat': Icons.chat_bubble_rounded,
  'download': Icons.download_rounded,
  'bookmark': Icons.bookmark_rounded,
  'speed': Icons.speed_rounded,
  'sparkle': Icons.auto_awesome_rounded,
  'support': Icons.support_agent_rounded,
  'filter': Icons.filter_alt_rounded,
  'note': Icons.edit_note_rounded,
  'ad_off': Icons.block_rounded,
  'verified': Icons.verified_rounded,
  'trophy': Icons.emoji_events_rounded,
  'fire': Icons.local_fire_department_rounded,
  'key': Icons.vpn_key_rounded,
};

const _builtInPerks = <List<String>>[
  ['Custom Accent Theme', 'Start with Premium Blue, pick a signature look, or mix your own custom colour.'],
  ['Premium Badge', 'A gold badge next to your name on reviews and profile, visible to everyone.'],
  ['Highlighted Reviews', 'Your reviews stand out with a gold accent in every app\'s review list.'],
  ['Ad-light catalog', 'Hide promotional ad badges on app cards while browsing the store.'],
  ['Priority support chat', 'Message developers with a premium indicator so they can prioritise replies.'],
  ['Smart catalogue filters', 'Ad-free and Under 25 MB filters inside every category list.'],
  ['Private app notes', 'Write a private note on any app (compatibility, reminders). Saved on your phone, only you see it.'],
  ['Unlimited saved apps', 'Free members can save 10 apps for later; Premium saves without a limit.'],
  ['Early feature access', 'Try new DarkStore tools (collections, update center) as they roll out.'],
];

const _builtInSwatches = <List<Object>>[
  ['Premium Blue', 0xFF2563EB],
  ['Amber Gold', 0xFFFBBF24],
  ['Cosmic Purple', 0xFFA78BFA],
  ['Holo Cyan', 0xFF22D3EE],
  ['Matrix Green', 0xFF34D399],
  ['Ruby Rose', 0xFFF472B6],
];

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

List<Map<String, dynamic>> _items(Object? v) =>
    v is List ? [for (final e in v) if (e is Map) {for (final k in e.keys) '$k': e[k]}] : <Map<String, dynamic>>[];

/// Draws the Premium card like the store app does (light theme), from a config map.
Widget premiumCardPreview(Map<String, dynamic> c, bool premium) {
  const ink = Color(0xFF1A2332), sub0 = Color(0xFF5A6577), mute0 = Color(0xFF8B95A5), line0 = Color(0xFFE2E8F0);
  const goldDef = Color(0xFFF59E0B);
  final style = _pick(c['cardStyle'], const ['tint', 'gradient', 'glass', 'solid', 'outline'], 'tint');
  final gold = _col(c['accent']) ?? goldDef;
  final a = _col(c['colorA']) ?? gold;
  final b = _col(c['colorB']) ?? Color.lerp(a, const Color(0xFF6C5CE7), .6)!;
  final filled = style == 'gradient' || style == 'solid';
  final fillBase = style == 'solid' ? (_col(c['colorA']) ?? Colors.white) : a;
  final fg = _col(c['textColor']) ?? (filled ? (fillBase.computeLuminance() > .55 ? const Color(0xFF111827) : Colors.white) : ink);
  final sub = filled ? fg.withOpacity(.75) : sub0;
  final mute = filled ? fg.withOpacity(.5) : mute0;
  final lineC = filled ? fg.withOpacity(.25) : line0;
  final onAccent = filled ? fg : gold;
  final radius = _num(c['radius'], 18, 0, 44);
  final bw = _num(c['borderWidth'], 1.5, 0, 6);
  final padv = _num(c['padding'], 16, 8, 32);
  final center = _pick(c['titleAlign'], const ['start', 'center'], 'start') == 'center';

  Color? bg;
  Gradient? grad;
  var border = premium ? gold : line0;
  switch (style) {
    case 'gradient':
      final r = _num(c['angle'], 135, 0, 360) * math.pi / 180;
      grad = LinearGradient(begin: Alignment(-math.cos(r), -math.sin(r)), end: Alignment(math.cos(r), math.sin(r)), colors: [a, b]);
      border = Colors.transparent;
    case 'solid':
      bg = _col(c['colorA']) ?? Colors.white;
      border = Colors.transparent;
    case 'glass':
      bg = Colors.white.withOpacity(.6);
      border = gold.withOpacity(.45);
    case 'outline':
      bg = Colors.transparent;
      border = gold;
    default:
      bg = premium ? gold.withOpacity(.08) : Colors.white;
  }
  if (_col(c['borderColor']) != null) border = _col(c['borderColor'])!;

  IconData ic(String n, IconData d) => _icons[n.trim().toLowerCase()] ?? d;

  Widget block(Map<String, dynamic> k) {
    final type = s(k['type']).toLowerCase();
    final al = _pick(k['align'], const ['start', 'center', 'end'], 'start');
    final cross = al == 'center' ? CrossAxisAlignment.center : (al == 'end' ? CrossAxisAlignment.end : CrossAxisAlignment.start);
    final ta = al == 'center' ? TextAlign.center : (al == 'end' ? TextAlign.end : TextAlign.start);
    final col = _col(k['color']) ?? fg;
    final size = _num(k['size'], 13, 9, 30);
    final height = _num(k['height'], 120, 20, 320);
    switch (type) {
      case 'space':
        return SizedBox(height: height.clamp(4, 80).toDouble());
      case 'divider':
        return Padding(padding: const EdgeInsets.symmetric(vertical: 8), child: Divider(height: 1, color: (_col(k['color']) ?? sub).withOpacity(.4)));
      case 'image':
        final u = s(k['imageUrl']);
        if (!u.startsWith('http')) return _hint('Image block: add a picture link');
        return Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: Image.network(u, height: height, width: double.infinity, fit: BoxFit.cover, errorBuilder: (_, __, ___) => _hint('Picture could not load')),
          ),
        );
      case 'button':
        return Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: Align(
            alignment: al == 'center' ? Alignment.center : (al == 'end' ? Alignment.centerRight : Alignment.centerLeft),
            child: FilledButton.tonalIcon(
              onPressed: () {},
              icon: Icon(ic(s(k['icon']), Icons.open_in_new_rounded), size: 18),
              label: Text(s(k['buttonText']).isEmpty ? (s(k['title']).isEmpty ? 'Open' : s(k['title'])) : s(k['buttonText'])),
              style: FilledButton.styleFrom(foregroundColor: _col(k['color']) ?? onAccent),
            ),
          ),
        );
      default:
        return Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: Column(crossAxisAlignment: cross, children: [
            if (s(k['title']).isNotEmpty) Text(s(k['title']), textAlign: ta, style: TextStyle(fontSize: size + 1, fontWeight: FontWeight.w800, color: col)),
            if (s(k['text']).isNotEmpty) Text(s(k['text']), textAlign: ta, style: TextStyle(fontSize: size, height: 1.4, fontWeight: _flag(k['bold'], false) ? FontWeight.w700 : FontWeight.w400, color: col)),
          ]),
        );
    }
  }

  Widget blocksAt(String place) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        for (final k in _items(c['blocks']))
          if (_pick(k['place'], const ['top', 'afterBenefits', 'bottom'], 'bottom') == place &&
              !(_flag(k['premiumOnly'], false) && !premium) &&
              !(_flag(k['freeOnly'], false) && premium))
            block(k),
      ]);

  final custom = [for (final p in _items(c['perks'])) if (s(p['title']).trim().isNotEmpty) p];
  final perks = custom.isNotEmpty ? custom : [for (final p in _builtInPerks) {'icon': '', 'title': p[0], 'desc': p[1]}];
  IconData pic(Map<String, dynamic> p) => s(p['icon']).isNotEmpty ? ic(s(p['icon']), Icons.check_circle) : (premium ? Icons.check_circle : Icons.lock_outline);
  final layout = _pick(c['benefitsLayout'], const ['list', 'grid', 'chips'], 'list');

  Widget perksW() {
    if (layout == 'chips') {
      return Wrap(spacing: 8, runSpacing: 8, children: [
        for (final p in perks)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(color: (premium ? onAccent : mute).withOpacity(.12), borderRadius: BorderRadius.circular(99)),
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              Icon(pic(p), size: 14, color: premium ? onAccent : mute),
              const SizedBox(width: 6),
              Text(s(p['title']), style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: fg)),
            ]),
          ),
      ]);
    }
    if (layout == 'grid') {
      return Wrap(spacing: 10, runSpacing: 10, children: [
        for (final p in perks)
          Container(
            width: 150,
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: (premium ? onAccent : mute).withOpacity(.08), borderRadius: BorderRadius.circular(12), border: Border.all(color: lineC)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Icon(pic(p), size: 20, color: premium ? onAccent : mute),
              const SizedBox(height: 6),
              Text(s(p['title']), style: TextStyle(fontWeight: FontWeight.w700, fontSize: 12.5, color: fg)),
              if (s(p['desc']).isNotEmpty) Text(s(p['desc']), maxLines: 3, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 11, color: sub)),
            ]),
          ),
      ]);
    }
    return Column(children: [
      for (final p in perks)
        Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(pic(p), size: 18, color: premium ? onAccent : mute),
            const SizedBox(width: 10),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(s(p['title']), style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: fg)),
                if (s(p['desc']).isNotEmpty) Text(s(p['desc']), style: TextStyle(fontSize: 11, color: sub)),
              ]),
            ),
          ]),
        ),
    ]);
  }

  Widget dot(Color col, {bool custom = false}) => Container(
        width: 38,
        height: 38,
        decoration: BoxDecoration(shape: BoxShape.circle, color: custom ? null : col, gradient: custom ? const SweepGradient(colors: [Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF22C55E), Color(0xFF06B6D4), Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFFEF4444)]) : null),
        child: custom ? const Icon(Icons.add, size: 18, color: Colors.white) : null,
      );

  final badge = premium ? s(c['badgeActive']) : s(c['badgeInactive']);
  final intro = premium ? s(c['introActive']) : s(c['introFree']);
  final ctaStyle = _pick(c['ctaStyle'], const ['outlined', 'filled', 'text'], 'outlined');
  final ctaText = s(c['ctaText']).isEmpty ? 'Invite friends to unlock' : s(c['ctaText']);
  final ctaIcon = const Icon(Icons.card_giftcard_outlined);
  final btnColor = filled ? fg : gold;

  final inner = Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    blocksAt('top'),
    Row(mainAxisAlignment: center ? MainAxisAlignment.center : MainAxisAlignment.start, children: [
      Icon(ic(s(c['icon']), Icons.star_rounded), color: onAccent, size: 24),
      const SizedBox(width: 8),
      Flexible(
        child: Text(s(c['title']).isEmpty ? 'DARKSTORE PREMIUM' : s(c['title']),
            textAlign: center ? TextAlign.center : TextAlign.start,
            style: TextStyle(fontWeight: FontWeight.w900, fontSize: _num(c['titleSize'], 14, 10, 28), letterSpacing: .5, color: premium ? onAccent : fg)),
      ),
      if (_flag(c['showBadge'], true) && badge.isNotEmpty) ...[
        const SizedBox(width: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
          decoration: BoxDecoration(color: (premium ? onAccent : sub).withOpacity(.15), borderRadius: BorderRadius.circular(6)),
          child: Text(badge, style: TextStyle(fontSize: 9, fontWeight: FontWeight.w700, color: premium ? onAccent : sub)),
        ),
      ],
    ]),
    if (intro.isNotEmpty) ...[
      const SizedBox(height: 10),
      Text(intro, textAlign: center ? TextAlign.center : TextAlign.start, style: TextStyle(fontSize: 12, color: fg, height: 1.35)),
    ],
    if (_flag(c['showBenefits'], true)) ...[
      const SizedBox(height: 14),
      if (s(c['benefitsTitle']).isNotEmpty) Padding(padding: const EdgeInsets.only(bottom: 8), child: Text(s(c['benefitsTitle']), style: TextStyle(fontWeight: FontWeight.w800, fontSize: 13, color: fg))),
      perksW(),
    ],
    blocksAt('afterBenefits'),
    if (_flag(c['showStatus'], true)) ...[
      Divider(height: 22, color: lineC),
      Row(children: [
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(s(c['statusTitle']), style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: fg)),
            Text(premium ? s(c['statusActive']) : s(c['statusFree']), style: TextStyle(fontSize: 11, color: sub)),
          ]),
        ),
        Icon(premium ? Icons.check_circle : Icons.lock, color: premium ? onAccent : mute, size: 26),
      ]),
    ],
    if (!premium && _flag(c['ctaShow'], true)) ...[
      const SizedBox(height: 12),
      if (ctaStyle == 'filled')
        FilledButton.icon(onPressed: () {}, icon: ctaIcon, label: Text(ctaText), style: FilledButton.styleFrom(backgroundColor: btnColor, foregroundColor: filled ? (fg.computeLuminance() > .5 ? Colors.black : Colors.white) : Colors.black, minimumSize: const Size.fromHeight(44)))
      else if (ctaStyle == 'text')
        TextButton.icon(onPressed: () {}, icon: ctaIcon, label: Text(ctaText), style: TextButton.styleFrom(foregroundColor: btnColor))
      else
        OutlinedButton.icon(onPressed: () {}, icon: ctaIcon, label: Text(ctaText), style: OutlinedButton.styleFrom(foregroundColor: btnColor, side: BorderSide(color: btnColor), minimumSize: const Size.fromHeight(44))),
    ],
    if (premium && _flag(c['engineShow'], true)) ...[
      Divider(height: 24, color: lineC),
      Text(s(c['engineTitle']), style: TextStyle(fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: 1, color: onAccent)),
      if (s(c['engineSubtitle']).isNotEmpty) ...[const SizedBox(height: 4), Text(s(c['engineSubtitle']), style: TextStyle(fontSize: 11, color: sub))],
      const SizedBox(height: 12),
      Wrap(spacing: 12, runSpacing: 12, children: [
        if (!_flag(c['hideBuiltInSwatches'], false)) for (final t in _builtInSwatches) dot(Color(t[1] as int)),
        for (final x in _items(c['swatches'])) if (_col(x['color']) != null) dot(_col(x['color'])!),
        if (_flag(c['allowCustomColor'], true)) dot(Colors.grey, custom: true),
      ]),
    ],
    blocksAt('bottom'),
  ]);

  return Container(
    clipBehavior: Clip.antiAlias,
    padding: EdgeInsets.all(padv),
    decoration: BoxDecoration(
      color: bg,
      gradient: grad,
      borderRadius: BorderRadius.circular(radius),
      border: bw > 0 && border != Colors.transparent ? Border.all(color: border, width: bw) : null,
      image: s(c['bgImage']).startsWith('http') ? DecorationImage(image: NetworkImage(s(c['bgImage'])), fit: BoxFit.cover, opacity: (_num(c['bgOpacity'], 25, 0, 100) / 100)) : null,
      boxShadow: [
        if (_flag(c['shadow'], false)) BoxShadow(color: Colors.black.withOpacity(.14), blurRadius: 18, offset: const Offset(0, 6)),
        if (_flag(c['glow'], false)) BoxShadow(color: gold.withOpacity(.45), blurRadius: 26, spreadRadius: 1),
      ],
    ),
    child: inner,
  );
}

Widget _hint(String t) => Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(color: Colors.grey.withOpacity(.15), borderRadius: BorderRadius.circular(10)),
      child: Text(t, style: const TextStyle(fontSize: 12, color: Colors.grey)),
    );

class PremiumUiTab extends StatefulWidget {
  const PremiumUiTab({super.key});
  @override
  State<PremiumUiTab> createState() => _PremiumUiTabState();
}

class _PremiumUiTabState extends State<PremiumUiTab> {
  Map<String, dynamic> cfg = {...kPremiumUiDefaults};
  String saved = '';
  String? loadError;
  bool loading = true, busy = false, uploading = false, asMember = true;
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

  Map<String, dynamic> _sorted(Map<String, dynamic> m) => {for (final k in (m.keys.toList()..sort())) k: m[k]};
  bool get dirty => jsonEncode(_sorted(cfg)) != saved;

  Future<void> _load() async {
    setState(() {
      loading = true;
      loadError = null;
    });
    try {
      final m = await Db.map('premiumUiConfig');
      cfg = {...kPremiumUiDefaults, for (final e in m.entries) if (kPremiumUiDefaults.containsKey(e.key)) e.key: e.value};
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
      final keep = {for (final k in const ['enabled', 'title', 'perks', 'blocks', 'swatches', 'introActive', 'introFree', 'ctaText', 'ctaLink', 'bgImage']) k: cfg[k]};
      cfg = {...kPremiumUiDefaults, ...keep, ..._presets[name]!};
      _syncControllers();
    });
  }

  Future<void> _save() async {
    setState(() => busy = true);
    try {
      await Db.upd('premiumUiConfig', {...cfg});
      await Db.audit('PREMIUM_UI', 'config', 'premiumUiConfig', 'Premium card', 'saved');
      saved = jsonEncode(_sorted(cfg));
      if (mounted) toast(context, 'Saved. Members see the new design within moments.');
    } catch (e) {
      if (mounted) toast(context, 'Failed: $e');
    }
    if (mounted) setState(() => busy = false);
  }

  Future<void> _reset() async {
    if (!await confirm(context, 'Back to the built-in card?', 'Removes your custom design. Everyone sees the original Premium card again.') || !mounted) return;
    setState(() => busy = true);
    try {
      await Db.del('premiumUiConfig');
      await Db.audit('PREMIUM_UI', 'config', 'premiumUiConfig', 'Premium card', 'reset to default');
      cfg = {...kPremiumUiDefaults};
      saved = jsonEncode(_sorted(cfg));
      _syncControllers();
      if (mounted) toast(context, 'Reset to the built-in card');
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
        final url = await Uploader.upload(f, folder: 'premium-ui');
        _tc('bgImage').text = url;
        cfg['bgImage'] = url;
      }
    } catch (e) {
      if (mounted) toast(context, '$e');
    }
    if (mounted) setState(() => uploading = false);
  }

  // ───────────── list editors (benefits, colours, blocks) ─────────────
  Future<void> _editItem(String key, String label, List<F> fields, {int? index, Map<String, dynamic> start = const {}}) async {
    final list = _items(cfg[key]);
    final init = index == null ? {...start} : {...list[index]};
    final r = await editSheet(context, index == null ? 'Add $label' : 'Edit $label', fields, init);
    if (r == null || !mounted) return;
    final out = <String, dynamic>{for (final f in fields) f.k: r[f.k]};
    for (final f in fields) {
      if (f.t == 'num') out[f.k] = _num(out[f.k], 0, -1000, 1000);
    }
    setState(() {
      if (index == null) {
        list.add(out);
      } else {
        list[index] = out;
      }
      cfg[key] = list;
    });
  }

  void _moveItem(String key, int i, int d) {
    final list = _items(cfg[key]);
    final j = i + d;
    if (j < 0 || j >= list.length) return;
    final x = list.removeAt(i);
    list.insert(j, x);
    setState(() => cfg[key] = list);
  }

  void _removeItem(String key, int i) {
    final list = _items(cfg[key])..removeAt(i);
    setState(() => cfg[key] = list);
  }

  Widget _listSection(String title, String help, String key, String noun, List<F> fields, String Function(Map<String, dynamic>) head, String Function(Map<String, dynamic>) subtitle,
      {Map<String, dynamic> start = const {}, Widget? extra}) {
    final list = _items(cfg[key]);
    return _section(title, [
      Text(help, style: const TextStyle(fontSize: 12, color: Colors.grey)),
      const SizedBox(height: 6),
      if (extra != null) extra,
      if (list.isEmpty) _hint('Nothing added yet.'),
      for (var i = 0; i < list.length; i++)
        Card(
          margin: const EdgeInsets.symmetric(vertical: 3),
          child: ListTile(
            dense: true,
            title: Text(head(list[i]).isEmpty ? '(no title)' : head(list[i]), maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700)),
            subtitle: Text(subtitle(list[i]), maxLines: 1, overflow: TextOverflow.ellipsis),
            onTap: () => _editItem(key, noun, fields, index: i),
            trailing: Row(mainAxisSize: MainAxisSize.min, children: [
              IconButton(visualDensity: VisualDensity.compact, tooltip: 'Move up', icon: const Icon(Icons.arrow_upward_rounded, size: 18), onPressed: i == 0 ? null : () => _moveItem(key, i, -1)),
              IconButton(visualDensity: VisualDensity.compact, tooltip: 'Move down', icon: const Icon(Icons.arrow_downward_rounded, size: 18), onPressed: i == list.length - 1 ? null : () => _moveItem(key, i, 1)),
              IconButton(visualDensity: VisualDensity.compact, tooltip: 'Delete', icon: const Icon(Icons.delete_outline_rounded, size: 18), onPressed: () => _removeItem(key, i)),
            ]),
          ),
        ),
      Align(
        alignment: Alignment.centerLeft,
        child: TextButton.icon(onPressed: () => _editItem(key, noun, fields, start: start), icon: const Icon(Icons.add_rounded), label: Text('Add $noun')),
      ),
    ]);
  }

  static const _iconOpts = ['', 'star', 'crown', 'diamond', 'bolt', 'heart', 'shield', 'rocket', 'gift', 'check', 'lock', 'palette', 'chat', 'download', 'bookmark', 'speed', 'sparkle', 'support', 'filter', 'note', 'ad_off', 'verified', 'trophy', 'fire', 'key'];

  static const _perkFields = [
    F('icon', 'Icon', 'select', _iconOpts),
    F('title', 'Title'),
    F('desc', 'Description', 'area'),
  ];

  static const _swatchFields = [F('name', 'Colour name'), F('color', 'Colour (hex, like #2563EB)')];

  static const _blockFields = [
    F('type', 'Type', 'select', ['text', 'image', 'button', 'divider', 'space']),
    F('place', 'Where', 'select', ['top', 'afterBenefits', 'bottom']),
    F('title', 'Heading / button name'),
    F('text', 'Text', 'area'),
    F('imageUrl', 'Picture link (Image type)', 'image'),
    F('link', 'Opens (/referral, /settings or https://…)'),
    F('buttonText', 'Button text'),
    F('icon', 'Button icon', 'select', _iconOpts),
    F('align', 'Align', 'select', ['start', 'center', 'end']),
    F('color', 'Colour (hex, blank = card text)'),
    F('size', 'Text size (9-30)', 'num'),
    F('height', 'Picture / space height', 'num'),
    F('bold', 'Bold text', 'bool'),
    F('premiumOnly', 'Show to Premium members only', 'bool'),
    F('freeOnly', 'Show to non-members only', 'bool'),
  ];

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

  Widget _iconChoice(String label, String key) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label, style: const TextStyle(fontSize: 12, color: Colors.grey)),
          const SizedBox(height: 4),
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final e in _icons.entries)
              InkWell(
                borderRadius: BorderRadius.circular(10),
                onTap: () => _set(key, e.key),
                child: Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(10),
                    color: s(cfg[key]) == e.key ? Theme.of(context).colorScheme.primaryContainer : Colors.grey.withOpacity(.12),
                    border: Border.all(color: s(cfg[key]) == e.key ? Theme.of(context).colorScheme.primary : Colors.transparent, width: 2),
                  ),
                  child: Icon(e.value, size: 20),
                ),
              ),
          ]),
        ]),
      );

  Widget _slider(String label, String key, double lo, double hi, {String unit = ''}) {
    final v = _num(cfg[key], lo, lo, hi);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(children: [
        SizedBox(width: 108, child: Text(label, style: const TextStyle(fontSize: 13))),
        Expanded(child: Slider(value: v, min: lo, max: hi, divisions: math.max(1, (hi - lo).round()), label: '${v.round()}$unit', onChanged: (x) => _set(key, x.round()))),
        SizedBox(width: 48, child: Text('${v.round()}$unit', textAlign: TextAlign.end, style: const TextStyle(fontSize: 12, color: Colors.grey))),
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

  Widget _text(String label, String key, {String? hint, int lines = 1, Widget? trailing}) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: TextField(
          controller: _tc(key),
          maxLines: lines,
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
    final h = (MediaQuery.of(c).size.height * .34).clamp(220.0, 360.0).toDouble();
    return Column(children: [
      Container(
        width: double.infinity,
        color: Theme.of(c).colorScheme.surfaceContainerHighest.withOpacity(.5),
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
        child: Column(children: [
          Row(children: [
            const Text('LIVE PREVIEW', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
            const Spacer(),
            ChoiceChip(visualDensity: VisualDensity.compact, label: const Text('Premium member', style: TextStyle(fontSize: 11)), selected: asMember, onSelected: (_) => setState(() => asMember = true)),
            const SizedBox(width: 6),
            ChoiceChip(visualDensity: VisualDensity.compact, label: const Text('Free member', style: TextStyle(fontSize: 11)), selected: !asMember, onSelected: (_) => setState(() => asMember = false)),
          ]),
          SizedBox(
            height: h,
            child: SingleChildScrollView(child: Center(child: SizedBox(width: 360, child: premiumCardPreview(cfg, asMember)))),
          ),
          TextButton.icon(
            icon: const Icon(Icons.fullscreen, size: 18),
            label: const Text('Full size'),
            onPressed: () => showDialog<void>(
              context: c,
              builder: (d) => Dialog(insetPadding: const EdgeInsets.all(12), child: SingleChildScrollView(padding: const EdgeInsets.all(12), child: premiumCardPreview(cfg, asMember))),
            ),
          ),
        ]),
      ),
      Expanded(
        child: ListView(padding: const EdgeInsets.all(12), children: [
          _section('Use a custom design', [
            _switch('Custom Premium design is ON', 'enabled'),
            const Text('Off = every user sees the original built-in Premium card.', style: TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 6),
            const Text('Quick styles (keeps your texts, benefits and blocks)', style: TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 4),
            Wrap(spacing: 8, runSpacing: 4, children: [for (final n in _presets.keys) ActionChip(label: Text(n), onPressed: () => _preset(n))]),
            const SizedBox(height: 6),
          ]),
          _section('Card look', [
            _choice('Style', 'cardStyle', const {'tint': 'Soft tint', 'gradient': 'Gradient', 'solid': 'Solid', 'glass': 'Glass', 'outline': 'Outline'}),
            _colorField('Colour 1 (gradient / solid)', 'colorA'),
            _colorField('Colour 2 (gradient)', 'colorB'),
            if (s(cfg['cardStyle']) == 'gradient') _slider('Gradient angle', 'angle', 0, 360, unit: '°'),
            _colorField('Accent (icons, title, buttons)', 'accent'),
            _colorField('Text colour', 'textColor'),
            _colorField('Border colour', 'borderColor'),
            _slider('Border width', 'borderWidth', 0, 6, unit: ' px'),
            _slider('Corner radius', 'radius', 0, 44, unit: ' px'),
            _slider('Inner space', 'padding', 8, 32, unit: ' px'),
            _switch('Soft shadow', 'shadow', false),
            _switch('Glow in the accent colour', 'glow', false),
            _text('Background picture link', 'bgImage',
                trailing: uploading
                    ? const Padding(padding: EdgeInsets.all(12), child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)))
                    : IconButton(tooltip: 'Pick picture from device', icon: const Icon(Icons.add_photo_alternate_outlined), onPressed: _upload)),
            if (s(cfg['bgImage']).isNotEmpty) _slider('Picture strength', 'bgOpacity', 0, 100, unit: ' %'),
          ]),
          _section('Header and texts', [
            _text('Title', 'title'),
            _iconChoice('Title icon', 'icon'),
            _slider('Title size', 'titleSize', 10, 28),
            _choice('Title position', 'titleAlign', const {'start': 'Left', 'center': 'Centered'}),
            _switch('Show the small badge', 'showBadge'),
            _text('Badge text for members', 'badgeActive'),
            _text('Badge text for free users', 'badgeInactive'),
            _text('Message for members', 'introActive', lines: 3),
            _text('Message for free users', 'introFree', lines: 3),
          ]),
          _section('Benefits', [
            _switch('Show the benefits', 'showBenefits'),
            _text('Heading above the benefits (optional)', 'benefitsTitle'),
            _choice('Layout', 'benefitsLayout', const {'list': 'List', 'grid': 'Two columns', 'chips': 'Chips'}),
          ]),
          _listSection(
            'Benefit lines',
            'Empty = the ${_builtInPerks.length} built-in benefits. Add your own to replace them completely.',
            'perks',
            'benefit',
            _perkFields,
            (m) => s(m['title']),
            (m) => s(m['desc']),
            start: const {'icon': 'check'},
            extra: Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: () => setState(() => cfg['perks'] = [for (final p in _builtInPerks) {'icon': '', 'title': p[0], 'desc': p[1]}]),
                icon: const Icon(Icons.copy_all_rounded, size: 18),
                label: const Text('Start from the built-in list'),
              ),
            ),
          ),
          _section('Status and button', [
            _switch('Show the status row', 'showStatus'),
            _text('Status title', 'statusTitle'),
            _text('Status text (member)', 'statusActive'),
            _text('Status text (free)', 'statusFree'),
            const Divider(),
            _switch('Show the unlock button (free users)', 'ctaShow'),
            _text('Button text', 'ctaText'),
            _text('Button opens', 'ctaLink', hint: '/referral or https://…'),
            _choice('Button style', 'ctaStyle', const {'outlined': 'Outlined', 'filled': 'Filled', 'text': 'Text'}),
          ]),
          _section('Colour engine (members choose their accent)', [
            _switch('Show the colour picker to members', 'engineShow'),
            _text('Title', 'engineTitle'),
            _text('Short help text', 'engineSubtitle'),
            _switch('Let members mix their own colour', 'allowCustomColor'),
            _switch('Hide the 6 built-in colours', 'hideBuiltInSwatches', false),
          ]),
          _listSection('Extra colours', 'Colours you offer to members besides the built-in ones.', 'swatches', 'colour', _swatchFields, (m) => s(m['name']), (m) => s(m['color']), start: const {'name': 'New colour', 'color': '#7C3AED'}),
          _listSection(
            'Extra blocks',
            'Free-form pieces: a heading with text, a picture, a button, a divider or empty space. Place them at the top, after the benefits or at the bottom, and show them only to members or only to free users.',
            'blocks',
            'block',
            _blockFields,
            (m) => '${s(m['type'])}: ${s(m['title']).isEmpty ? s(m['text']) : s(m['title'])}',
            (m) => 'Place: ${s(m['place'])}${_flag(m['premiumOnly'], false) ? ' · members' : ''}${_flag(m['freeOnly'], false) ? ' · free users' : ''}',
            start: const {'type': 'text', 'place': 'bottom', 'align': 'start', 'size': 13, 'height': 120},
          ),
          const SizedBox(height: 8),
          const Text('Changes reach the store app live (it also remembers the last design on the phone). Colours use hex codes such as #2563EB. The colour a member picks stays on their phone.', style: TextStyle(fontSize: 12, color: Colors.grey)),
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
                label: Text(dirty ? 'Save design' : 'Saved'),
              ),
            ),
          ]),
        ),
      ),
    ]);
  }
}
