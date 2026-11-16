import 'package:flutter/material.dart';
import 'core.dart';
import 'sb.dart';
import 'security_api.dart' show asMaps;

// ─────────────────────────────────────────────────────────────────────────────
// v17 remote controls (rows in `app_config`, read live by the store app).
//   Search    how search behaves + what people search and do not find
//   Reviews   review rules + the moderation queue (reported / hidden reviews)
// The App box designer is in app_box_tab.dart.
// ─────────────────────────────────────────────────────────────────────────────

bool _flagOn(Map<String, dynamic> m, String k, [bool d = true]) =>
    m[k] == null ? d : (m[k] != false && s(m[k]).toLowerCase() != 'false' && s(m[k]) != '0');

List<String> _wordList(Object? v) {
  final raw = v is List ? v.map((e) => '$e').toList() : s(v).split(RegExp(r'[,\n]+'));
  final out = <String>[];
  for (final w in raw) {
    final t = w.trim();
    if (t.isNotEmpty && !out.any((x) => x.toLowerCase() == t.toLowerCase())) out.add(t);
  }
  return out;
}

/// One synonym group per line: `whatsapp, wa, whats app`.
List<String> _lines(Object? v) {
  final raw = v is List ? v.map((e) => '$e').toList() : s(v).split(RegExp(r'[\n;]+'));
  return [for (final l in raw) if (l.trim().isNotEmpty) l.trim()];
}

int _searchDays = 14;

// ═══════════════════════════════ SEARCH ═══════════════════════════════

Future<Widget> searchAdminTab(BuildContext c, VoidCallback rl) async {
  final cfg = await Db.map('searchConfig');
  Map<String, dynamic> report = {};
  String? reportError;
  try {
    final r = await Sb.c.rpc('admin_search_report', params: {'p_days': _searchDays});
    if (r is Map) report = Map<String, dynamic>.from(r);
  } catch (e) {
    reportError = '$e';
  }

  final trending = _wordList(cfg['trending']);
  final synonyms = _lines(cfg['synonyms']);
  final top = asMaps(report['top']);
  final nothing = asMaps(report['nothing']);

  Future<void> put(Map<String, Object?> patch, String what) => act(c, () async {
        await Db.upd('searchConfig', patch);
        await Db.audit('SEARCH_CONFIG', 'config', 'searchConfig', 'Search', what);
      }, rl, 'Saved');

  Widget sw(String k, String title, String sub, [bool d = true]) => SwitchListTile(
        title: Text(title),
        subtitle: Text(sub),
        value: _flagOn(cfg, k, d),
        onChanged: (v) => put({k: v}, '$k=$v'),
      );

  Future<void> editNum(String k, String label, int def, int lo, int hi) async {
    final x = await editSheet(c, label, [F(k, label, 'num')], {k: cfg[k] ?? def});
    if (x != null && c.mounted) await put({k: ni(x[k]).clamp(lo, hi).toInt()}, '$k=${x[k]}');
  }

  Future<void> editList(String k, String label, String hint, {required bool lines}) async {
    final cur = lines ? synonyms : trending;
    final x = await editSheet(c, label, [F('v', hint, 'area')], {'v': cur.join('\n')});
    if (x == null || !c.mounted) return;
    final v = lines ? _lines(x['v']) : _wordList(x['v']);
    await put({k: v}, '$k changed (${v.length})');
  }

  Future<void> addTrending(String term) async {
    if (trending.any((w) => w.toLowerCase() == term.toLowerCase())) {
      toast(c, '"$term" is already in the list');
      return;
    }
    await put({'trending': [...trending, term]}, 'trending + $term');
  }

  Future<void> addSynonym(String term) async {
    final x = await editSheet(c, 'Same meaning as "$term"', const [F('same', 'Other words people may use (separate with commas)', 'area')], {'same': ''});
    if (x == null || !c.mounted) return;
    final more = _wordList(x['same']).where((w) => w.toLowerCase() != term.toLowerCase()).toList();
    if (more.isEmpty) return;
    await put({'synonyms': [...synonyms, [term, ...more].join(', ')]}, 'synonym: $term');
  }

  Widget row(Map<String, dynamic> x, {required bool miss}) {
    final term = s(x['term']);
    return ListTile(
      dense: true,
      title: Text(term),
      subtitle: Text(miss
          ? '${x['searches']} search${ni(x['searches']) == 1 ? '' : 'es'} · ${x['users']} ${ni(x['users']) == 1 ? 'person' : 'people'}'
          : '${x['searches']} searches · ${x['users']} ${ni(x['users']) == 1 ? 'person' : 'people'} · about ${x['found']} result${ni(x['found']) == 1 ? '' : 's'}'),
      trailing: miss
          ? TextButton(onPressed: () => addSynonym(term), child: const Text('Add synonym'))
          : IconButton(tooltip: 'Add to trending', icon: const Icon(Icons.trending_up), onPressed: () => addTrending(term)),
    );
  }

  return listPage([
    Card(
      color: Colors.blue.withOpacity(.08),
      child: const Padding(
        padding: EdgeInsets.all(12),
        child: Text(
          'Search runs on the phone. Every word must match (name, developer, package, category, and optionally the description), '
          'close spellings are forgiven, and results are ranked by how well the name matches. '
          'People see "Trending searches" and "Did you mean" suggestions. Changes apply within moments.',
          style: TextStyle(fontSize: 12.5, height: 1.4),
        ),
      ),
    ),
    const SizedBox(height: 8),
    Card(
      child: Column(children: [
        sw('fuzzy', 'Forgive typos', '"whatsap" still finds WhatsApp'),
        sw('searchDescriptions', 'Search descriptions too', 'Off = only name, developer, package and category'),
        sw('showSuggestions', '"Suggested for you" before typing', 'Uses your Suggestions settings'),
        sw('showTrending', '"Trending searches" before typing', 'Your words and, if on, what people search'),
        sw('autoTrending', 'Add popular searches automatically', 'Only words that found apps and many people searched'),
        sw('logSearches', 'Record searched words', 'Words only, no names. Needed for the report below and automatic trending'),
        ListTile(
          title: const Text('Most results shown'),
          subtitle: Text('${ni(cfg['maxResults'] == null ? 120 : cfg['maxResults'])} (20 - 300)'),
          trailing: const Icon(Icons.edit),
          onTap: () => editNum('maxResults', 'Most results shown (20 - 300)', 120, 20, 300),
        ),
        ListTile(
          title: const Text('People needed for automatic trending'),
          subtitle: Text('${ni(cfg['minUsers'] == null ? 2 : cfg['minUsers'])} different people (1 - 50)'),
          trailing: const Icon(Icons.edit),
          onTap: () => editNum('minUsers', 'Different people needed (1 - 50)', 2, 1, 50),
        ),
        ListTile(
          title: const Text('Text inside the search box'),
          subtitle: Text(s(cfg['hint']).isEmpty ? 'Default: "Search apps & games…"' : s(cfg['hint'])),
          trailing: const Icon(Icons.edit),
          onTap: () async {
            final x = await editSheet(c, 'Search box text', const [F('hint', 'Hint (blank = default, translated)')], cfg);
            if (x != null && c.mounted) await put({'hint': s(x['hint']).trim()}, 'hint changed');
          },
        ),
      ]),
    ),
    const SizedBox(height: 8),
    Card(
      child: Column(children: [
        ListTile(
          leading: const Icon(Icons.trending_up),
          title: const Text('Your trending words'),
          subtitle: Text(trending.isEmpty ? 'None. They show first, before the automatic ones.' : trending.join(' · '), maxLines: 3, overflow: TextOverflow.ellipsis),
          trailing: const Icon(Icons.edit),
          onTap: () => editList('trending', 'Trending words', 'One per line (or separate with commas)', lines: false),
        ),
        ListTile(
          leading: const Icon(Icons.compare_arrows),
          title: const Text('Words that mean the same'),
          subtitle: Text(
              synonyms.isEmpty ? 'None. Example: "whatsapp, wa, whats app" so any of them finds the others.' : '${synonyms.length} group${synonyms.length == 1 ? '' : 's'}: ${synonyms.take(3).join('  |  ')}',
              maxLines: 3,
              overflow: TextOverflow.ellipsis),
          trailing: const Icon(Icons.edit),
          onTap: () => editList('synonyms', 'Synonym groups', 'One group per line, words separated by commas', lines: true),
        ),
      ]),
    ),
    const SizedBox(height: 12),
    Row(children: [
      const Expanded(child: Text('What people search', style: TextStyle(fontWeight: FontWeight.bold))),
      PopupMenuButton<int>(
        tooltip: 'Period',
        initialValue: _searchDays,
        onSelected: (d) {
          _searchDays = d;
          rl();
        },
        itemBuilder: (_) => [for (final d in const [1, 7, 14, 30, 90]) PopupMenuItem(value: d, child: Text(d == 1 ? 'Last 24 hours' : 'Last $d days'))],
        child: Chip(label: Text(_searchDays == 1 ? 'Last 24 hours' : 'Last $_searchDays days'), avatar: const Icon(Icons.date_range, size: 16)),
      ),
    ]),
    if (reportError != null)
      Card(color: Colors.red.withOpacity(.08), child: Padding(padding: const EdgeInsets.all(12), child: Text('Could not load the report: $reportError', style: const TextStyle(fontSize: 12)))),
    if (reportError == null) ...[
      Padding(padding: const EdgeInsets.symmetric(vertical: 6), child: Text('${report['total'] ?? 0} searches recorded', style: const TextStyle(color: Colors.grey))),
      const Padding(padding: EdgeInsets.fromLTRB(4, 6, 4, 2), child: Text('Searched but nothing found', style: TextStyle(fontWeight: FontWeight.w600))),
      const Padding(
        padding: EdgeInsets.fromLTRB(4, 0, 4, 4),
        child: Text('Apps people want that the store does not have, or words that need a synonym.', style: TextStyle(fontSize: 12, color: Colors.grey)),
      ),
      Card(child: nothing.isEmpty ? const ListTile(dense: true, title: Text('Nothing yet')) : Column(children: [for (final x in nothing.take(25)) row(x, miss: true)])),
      const Padding(padding: EdgeInsets.fromLTRB(4, 10, 4, 4), child: Text('Top searches', style: TextStyle(fontWeight: FontWeight.w600))),
      Card(child: top.isEmpty ? const ListTile(dense: true, title: Text('Nothing yet')) : Column(children: [for (final x in top.take(25)) row(x, miss: false)])),
    ],
  ]);
}

// ═══════════════════════════════ REVIEWS ═══════════════════════════════

Future<Widget> reviewsAdminTab(BuildContext c, VoidCallback rl) async {
  final cfg = await Db.map('reviewConfig');
  List<Map<String, dynamic>> queue = [];
  String? queueError;
  try {
    final r = await Sb.c.rpc('admin_review_queue');
    queue = asMaps(r);
  } catch (e) {
    queueError = '$e';
  }

  Future<void> put(Map<String, Object?> patch, String what) => act(c, () async {
        await Db.upd('reviewConfig', patch);
        await Db.audit('REVIEW_CONFIG', 'config', 'reviewConfig', 'Reviews', what);
      }, rl, 'Saved');

  Widget sw(String k, String title, String sub, [bool d = true]) => SwitchListTile(
        title: Text(title),
        subtitle: Text(sub),
        value: _flagOn(cfg, k, d),
        onChanged: (v) => put({k: v}, '$k=$v'),
      );

  Future<void> editNum(String k, String label, int def, int lo, int hi) async {
    final x = await editSheet(c, label, [F(k, label, 'num')], {k: cfg[k] ?? def});
    if (x != null && c.mounted) await put({k: ni(x[k]).clamp(lo, hi).toInt()}, '$k=${x[k]}');
  }

  final sort = s(cfg['defaultSort']).isEmpty ? 'helpful' : s(cfg['defaultSort']);
  final autoHide = cfg['autoHideReports'] == null ? 3 : ni(cfg['autoHideReports']);

  Future<void> doAction(Map<String, dynamic> x, String action, String okText, {String? ask}) async {
    if (ask != null && !await confirm(c, ask, 'This cannot be undone.')) return;
    if (!c.mounted) return;
    await act(c, () async {
      await Sb.c.rpc('admin_review_action', params: {'p_review_id': s(x['id']), 'p_action': action});
      await Db.audit('REVIEW_${action.toUpperCase()}', 'review', s(x['id']), s(x['app']), '${x['user']}: ${s(x['msg']).length > 80 ? s(x['msg']).substring(0, 80) : s(x['msg'])}');
    }, rl, okText);
  }

  Widget qCard(Map<String, dynamic> x) {
    final hidden = bo(x['hidden']);
    final reports = ni(x['reports']);
    final reasons = (x['reasons'] is List) ? (x['reasons'] as List).map((e) => '$e').toList() : <String>[];
    final counts = <String, int>{};
    for (final r in reasons) {
      counts[r] = (counts[r] ?? 0) + 1;
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(child: Text('${x['app']}', style: const TextStyle(fontWeight: FontWeight.w700))),
            if (hidden) tagChip('HIDDEN', Colors.orange),
            if (reports > 0) tagChip('$reports REPORT${reports == 1 ? '' : 'S'}', Colors.red),
          ]),
          const SizedBox(height: 2),
          Text('${x['user']} · ${'★' * ni(x['stars'])}${'☆' * (5 - ni(x['stars']).clamp(0, 5))} · ${ni(x['helpful'])} helpful', style: const TextStyle(fontSize: 12, color: Colors.grey)),
          if (s(x['msg']).isNotEmpty) Padding(padding: const EdgeInsets.only(top: 6), child: Text(s(x['msg']))),
          if (counts.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Wrap(spacing: 4, runSpacing: 4, children: [for (final e in counts.entries) tagChip('${e.key} ×${e.value}', Colors.red)]),
            ),
          if (s(x['reply']).isNotEmpty)
            Container(
              margin: const EdgeInsets.only(top: 8),
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(color: Colors.blue.withOpacity(.08), borderRadius: BorderRadius.circular(8)),
              child: Text('Developer reply: ${x['reply']}', style: const TextStyle(fontSize: 12.5)),
            ),
          const SizedBox(height: 8),
          Wrap(spacing: 8, runSpacing: 4, children: [
            if (hidden)
              FilledButton.tonal(onPressed: () => doAction(x, 'unhide', 'Review is visible again'), child: const Text('Show again'))
            else
              FilledButton.tonal(onPressed: () => doAction(x, 'hide', 'Review hidden'), child: const Text('Hide')),
            if (reports > 0) OutlinedButton(onPressed: () => doAction(x, 'clear_reports', 'Reports dismissed'), child: const Text('Dismiss reports')),
            if (s(x['reply']).isNotEmpty) OutlinedButton(onPressed: () => doAction(x, 'clear_reply', 'Reply removed'), child: const Text('Remove reply')),
            OutlinedButton(
              style: OutlinedButton.styleFrom(foregroundColor: Colors.red),
              onPressed: () => doAction(x, 'delete', 'Review deleted', ask: 'Delete this review?'),
              child: const Text('Delete'),
            ),
          ]),
        ]),
      ),
    );
  }

  return listPage([
    Card(
      color: Colors.blue.withOpacity(.08),
      child: const Padding(
        padding: EdgeInsets.all(12),
        child: Text(
          'The length rules, "Helpful" votes, developer replies and reports are enforced by the database, so they cannot be bypassed from the phone. '
          'A hidden review stops counting towards the app\'s rating; only its writer still sees it, with a note.',
          style: TextStyle(fontSize: 12.5, height: 1.4),
        ),
      ),
    ),
    const SizedBox(height: 8),
    Card(
      child: Column(children: [
        sw('enabled', 'Allow new reviews', 'Off = nobody can write or edit; existing reviews stay visible'),
        sw('requireText', 'A written review is required', 'Off = a star rating alone is enough', false),
        sw('requireInstalled', 'Only people who installed the app can review', 'Checked in the app; reviews written with the app installed also get an "Installed" badge', false),
        sw('showVerified', 'Show the "Installed" badge', 'On reviews written with the app installed'),
        sw('allowHelpful', 'Helpful votes', 'People can mark a review helpful (not their own)'),
        sw('allowReplies', 'Developer replies', 'The app\'s developer can answer reviews in public'),
        sw('allowReports', 'Report a review', 'People can flag a review for you to check'),
        ListTile(
          title: const Text('Hide a review automatically after'),
          subtitle: Text('$autoHide report${autoHide == 1 ? '' : 's'} from different people'),
          trailing: const Icon(Icons.edit),
          onTap: () => editNum('autoHideReports', 'Reports before a review is hidden (1 - 20)', 3, 1, 20),
        ),
        ListTile(
          title: const Text('Shortest written review (characters)'),
          subtitle: Text(ni(cfg['minLength']) == 0 ? '0 · no minimum' : '${ni(cfg['minLength'])}'),
          trailing: const Icon(Icons.edit),
          onTap: () => editNum('minLength', 'Shortest review (0 = no minimum)', 0, 0, 1000),
        ),
        ListTile(
          title: const Text('Longest review (characters)'),
          subtitle: Text((cfg['maxLength'] == null ? 600 : ni(cfg['maxLength'])) == 0 ? '0 · no limit' : '${cfg['maxLength'] ?? 600}'),
          trailing: const Icon(Icons.edit),
          onTap: () => editNum('maxLength', 'Longest review (0 = no limit)', 600, 0, 5000),
        ),
        ListTile(
          title: const Text('Reviews are ordered by'),
          subtitle: Text(const {'helpful': 'Most helpful', 'newest': 'Newest', 'highest': 'Highest rating', 'lowest': 'Lowest rating'}[sort] ?? sort),
          trailing: const Icon(Icons.edit),
          onTap: () async {
            final x = await editSheet(c, 'Default order', const [F('defaultSort', 'Order', 'select', ['helpful', 'newest', 'highest', 'lowest'])], {'defaultSort': sort});
            if (x != null && c.mounted) await put({'defaultSort': s(x['defaultSort'])}, 'defaultSort=${x['defaultSort']}');
          },
        ),
      ]),
    ),
    const SizedBox(height: 12),
    Row(children: [
      const Expanded(child: Text('Needs your attention', style: TextStyle(fontWeight: FontWeight.bold))),
      tagChip('${queue.length}', queue.isEmpty ? Colors.green : Colors.red),
    ]),
    const Padding(padding: EdgeInsets.fromLTRB(0, 2, 0, 6), child: Text('Reported or hidden reviews, most reported first.', style: TextStyle(fontSize: 12, color: Colors.grey))),
    if (queueError != null)
      Card(color: Colors.red.withOpacity(.08), child: Padding(padding: const EdgeInsets.all(12), child: Text('Could not load the queue: $queueError', style: const TextStyle(fontSize: 12))))
    else if (queue.isEmpty)
      const Card(child: ListTile(leading: Icon(Icons.check_circle, color: Colors.green), title: Text('No reported or hidden reviews')))
    else
      ...queue.map(qCard),
  ]);
}
