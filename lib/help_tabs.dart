import 'package:flutter/material.dart';
import 'core.dart';
import 'sb.dart';

// ─────────────────────────────────────────────────────────────────────────────
//  Support  : who people contact (suspension page, Settings > Help & support)
//  Help/FAQ : questions and answers shown in the store app's Help screen
//  Both are rows in `app_config`; the store app reads them live.
// ─────────────────────────────────────────────────────────────────────────────

Map<String, Object?> _only(Map<String, dynamic> r, List<F> f) => {for (final x in f) x.k: r[x.k]};

// ═══════════════════════════════ SUPPORT ═══════════════════════════════

const _suFields = [
  F('telegram', 'Telegram: @name, t.me/name or a full link'),
  F('email', 'Support e-mail (optional)'),
  F('website', 'Website (optional)'),
  F('privacyUrl', 'Privacy policy link (optional)'),
  F('termsUrl', 'Terms of use link (optional)'),
  F('extraLabel', 'Extra button text (optional, e.g. "Join our channel")'),
  F('extraUrl', 'Extra button link'),
  F('responseNote', 'Response note, e.g. "We reply within 24 hours"'),
  F('suspendTitle', 'Suspension page title (empty = "Account suspended")'),
  F('suspendHelp', 'Suspension page message (empty = default)', 'area'),
  F('showOnSuspend', 'Show support buttons on the suspension page', 'bool'),
];

String _tgLink(String raw) {
  final t = raw.trim();
  if (t.isEmpty) return '';
  if (t.startsWith('@')) return 'https://t.me/${t.substring(1)}';
  final l = t.toLowerCase();
  if (l.startsWith('http://') || l.startsWith('https://')) return t;
  if (l.startsWith('t.me/') || l.startsWith('telegram.me/')) return 'https://$t';
  return 'https://t.me/$t';
}

String _tgLabel(String raw) {
  final u = _tgLink(raw);
  final i = u.lastIndexOf('/');
  return u.isEmpty ? '' : (i < 0 || i == u.length - 1 ? u : '@${u.substring(i + 1)}');
}

/// A message to show instead of saving when a value cannot work, or null when fine.
String? _supportProblem(Map<String, dynamic> x) {
  bool badUrl(String k) {
    final t = s(x[k]).trim();
    return t.isNotEmpty && (t.contains(' ') || !t.contains('.'));
  }

  final mail = s(x['email']).trim();
  if (mail.isNotEmpty && (!mail.contains('@') || mail.contains(' '))) return 'That e-mail address does not look right';
  for (final k in ['website', 'privacyUrl', 'termsUrl', 'extraUrl']) {
    if (badUrl(k)) return 'Check the link in "$k" (it needs a dot, like example.com)';
  }
  if (s(x['extraUrl']).trim().isNotEmpty && s(x['extraLabel']).trim().isEmpty) return 'Give the extra button a text';
  return null;
}

/// Dark card like the store app's suspension page support block.
Widget supportPreview(Map<String, dynamic> v) {
  final tg = _tgLabel(s(v['telegram']).trim().isEmpty ? 't.me/davidstha01' : s(v['telegram']));
  final title = s(v['suspendTitle']).trim().isEmpty ? 'Account suspended' : s(v['suspendTitle']).trim();
  final help = s(v['suspendHelp']).trim().isEmpty ? 'If this is a mistake, contact support and we will review it.' : s(v['suspendHelp']).trim();
  Widget btn(IconData i, String t, {bool main = false}) => Container(
        margin: const EdgeInsets.only(top: 8),
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 10),
        decoration: BoxDecoration(
          color: main ? const Color(0xFF229ED9) : Colors.transparent,
          borderRadius: BorderRadius.circular(24),
          border: main ? null : Border.all(color: Colors.white30),
        ),
        child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          Icon(i, size: 18, color: Colors.white),
          const SizedBox(width: 8),
          Flexible(child: Text(t, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600))),
        ]),
      );
  final show = v['showOnSuspend'] != false;
  return Container(
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(color: const Color(0xFF0B1020), borderRadius: BorderRadius.circular(16)),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Icon(Icons.gpp_bad_rounded, color: Colors.redAccent, size: 34),
      const SizedBox(height: 6),
      Text(title, style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w800)),
      const SizedBox(height: 4),
      Text(help, style: const TextStyle(color: Colors.white70, fontSize: 13, height: 1.35)),
      if (s(v['responseNote']).trim().isNotEmpty) Text(s(v['responseNote']).trim(), style: const TextStyle(color: Colors.white54, fontSize: 12)),
      if (show) ...[
        if (tg.isNotEmpty) btn(Icons.send_rounded, 'Contact support on Telegram  $tg', main: true),
        if (s(v['email']).trim().isNotEmpty) btn(Icons.mail_outline_rounded, 'E-mail support  ${s(v['email']).trim()}'),
        if (s(v['website']).trim().isNotEmpty) btn(Icons.language_rounded, 'Website'),
        if (s(v['extraUrl']).trim().isNotEmpty && s(v['extraLabel']).trim().isNotEmpty) btn(Icons.link_rounded, s(v['extraLabel']).trim()),
      ],
    ]),
  );
}

Future<Widget> supportTab(BuildContext c, VoidCallback rl) async {
  final e = await Db.map('supportConfig');
  final shown = {'telegram': 't.me/davidstha01', 'showOnSuspend': true, ...e};

  return listPage([
    Row(children: [
      tagChip(shown['showOnSuspend'] == false ? 'HIDDEN ON SUSPENSION PAGE' : 'SHOWN ON SUSPENSION PAGE', shown['showOnSuspend'] == false ? Colors.orange : Colors.green),
      Text(' ${_tgLabel(s(shown['telegram']))}', style: const TextStyle(fontSize: 12)),
    ]),
    const SizedBox(height: 10),
    const Text('PREVIEW (suspension page in the store app)', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
    const SizedBox(height: 6),
    supportPreview(shown),
    const SizedBox(height: 12),
    FilledButton.icon(
      icon: const Icon(Icons.edit),
      label: const Text('Edit support details'),
      onPressed: () async {
        final x = await editSheet(c, 'Support details', _suFields, shown, preview: supportPreview);
        if (x == null || !c.mounted) return;
        final bad = _supportProblem(x);
        if (bad != null) return toast(c, bad);
        await act(c, () async {
          await Db.upd('supportConfig', _only(x, _suFields));
          await Db.audit('SUPPORT', 'config', 'supportConfig', 'Support details', _tgLabel(s(x['telegram'])));
        }, rl);
      },
    ),
    const SizedBox(height: 12),
    const Text(
      'Used on the suspension page and in Settings > Help & support. '
      'If you leave Telegram empty the app falls back to t.me/davidstha01, so a suspended person always has a way to reach you.',
      style: TextStyle(fontSize: 12, color: Colors.grey, height: 1.4),
    ),
  ]);
}

// ═══════════════════════════════ HELP / FAQ ═══════════════════════════════

const _faqFields = [
  F('q', 'Question'),
  F('a', 'Answer', 'area'),
  F('visible', 'Visible to users', 'bool'),
];

Future<Widget> faqTab(BuildContext c, VoidCallback rl) async {
  final m = await Db.map('faqConfig');
  final raw = m['items'];
  final items = <Map<String, dynamic>>[
    if (raw is List)
      for (final x in raw)
        if (x is Map) Map<String, dynamic>.from(x),
  ];

  Future<void> saveAll(List<Map<String, dynamic>> list, String what) => act(c, () async {
        await Db.upd('faqConfig', {'items': list});
        await Db.audit('FAQ', 'config', 'faqConfig', 'Help / FAQ', what);
      }, rl);

  Future<void> edit(int? i) async {
    final x = await editSheet(c, i == null ? 'New question' : 'Edit question', _faqFields, i == null ? {'visible': true} : items[i]);
    if (x == null || !c.mounted) return;
    if (s(x['q']).trim().isEmpty || s(x['a']).trim().isEmpty) return toast(c, 'Both the question and the answer are needed');
    final row = {'q': s(x['q']).trim(), 'a': s(x['a']).trim(), 'visible': x['visible'] != false};
    final list = [...items];
    if (i == null) {
      if (list.length >= 60) return toast(c, 'Maximum 60 questions');
      list.add(row);
    } else {
      list[i] = row;
    }
    await saveAll(list, i == null ? 'added' : 'edited');
  }

  final visible = items.where((x) => x['visible'] != false).length;

  return listPage([
    Row(children: [
      tagChip('$visible SHOWN', visible == 0 ? Colors.grey : Colors.green),
      Text(' of ${items.length} questions', style: const TextStyle(fontSize: 12)),
    ]),
    const SizedBox(height: 10),
    Align(
      alignment: Alignment.centerLeft,
      child: FilledButton.icon(icon: const Icon(Icons.add), label: const Text('Add question'), onPressed: () => edit(null)),
    ),
    const SizedBox(height: 8),
    if (items.isEmpty)
      const Padding(
        padding: EdgeInsets.all(20),
        child: Text('No questions yet. People then only see the contact buttons. Good first ones: "Why was my account suspended?", "How do I update an app?", "Is this app safe?".', style: TextStyle(color: Colors.grey, height: 1.4)),
      ),
    for (var i = 0; i < items.length; i++)
      Card(
        child: ListTile(
          leading: Icon(items[i]['visible'] == false ? Icons.visibility_off_outlined : Icons.help_outline, color: items[i]['visible'] == false ? Colors.grey : null),
          title: Text(s(items[i]['q']), maxLines: 2, overflow: TextOverflow.ellipsis),
          subtitle: Text(s(items[i]['a']), maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12)),
          onTap: () => edit(i),
          trailing: PopupMenuButton<String>(
            onSelected: (v) async {
              final list = [...items];
              if (v == 'up' && i > 0) {
                final t = list.removeAt(i);
                list.insert(i - 1, t);
                await saveAll(list, 'moved');
              } else if (v == 'down' && i < list.length - 1) {
                final t = list.removeAt(i);
                list.insert(i + 1, t);
                await saveAll(list, 'moved');
              } else if (v == 'hide') {
                list[i] = {...list[i], 'visible': list[i]['visible'] == false};
                await saveAll(list, 'visibility');
              } else if (v == 'del') {
                if (!await confirm(c, 'Delete this question?', s(items[i]['q'])) || !c.mounted) return;
                list.removeAt(i);
                await saveAll(list, 'deleted');
              }
            },
            itemBuilder: (_) => [
              if (i > 0) const PopupMenuItem(value: 'up', child: Text('Move up')),
              if (i < items.length - 1) const PopupMenuItem(value: 'down', child: Text('Move down')),
              PopupMenuItem(value: 'hide', child: Text(items[i]['visible'] == false ? 'Show to users' : 'Hide from users')),
              const PopupMenuItem(value: 'del', child: Text('Delete')),
            ],
          ),
        ),
      ),
  ]);
}
