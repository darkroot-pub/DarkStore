import 'package:flutter/material.dart';
import 'core.dart';
import 'sb.dart';

// ─────────────────────────────────────────────────────────────────────────────
// v16 remote controls. All rows live in `app_config`; the store app reads them live.
//   Rules         submission limits + chat rules (enforced by the DATABASE, not just the app)
//   Suggestions   how "Suggested for you" / "Similar apps" / "New & rising" are computed
//   Home layout   which shelves Home shows, and their titles
//   Share text    the message people send when sharing an app or their invite code
// ─────────────────────────────────────────────────────────────────────────────

double _dn(Object? v, double d) => v is num ? v.toDouble() : (double.tryParse(s(v)) ?? d);
String _fmt(double v) => v == v.roundToDouble() ? '${v.toInt()}' : v.toStringAsFixed(1);
bool _on(Map<String, dynamic> m, String k, [bool d = true]) => m[k] == null ? d : (m[k] != false && s(m[k]).toLowerCase() != 'false' && s(m[k]) != '0');

List<String> _ids(Object? v) {
  final raw = v is List ? v.map((e) => '$e').toList() : s(v).split(RegExp(r'[,\s]+'));
  final out = <String>[];
  for (final e in raw) {
    final t = e.trim();
    if (t.isNotEmpty && !out.contains(t)) out.add(t);
  }
  return out;
}

List<String> _words(Object? v) {
  final raw = v is List ? v.map((e) => '$e').toList() : s(v).split(RegExp(r'[,\n]+'));
  return [for (final w in raw) if (w.trim().isNotEmpty) w.trim().toLowerCase()];
}

final _linkRe = RegExp(r'(https?://|www\.|t\.me/|[a-z0-9-]+\.(com|net|org|io|me|xyz|ly)(/|\s|$))', caseSensitive: false);

/// The same checks the database makes, so you can try a message before people hit the rule.
String? _chatVerdict(Map<String, dynamic> chat, String msg) {
  if (!_on(chat, 'enabled')) return null;
  final mx = ni(chat['maxLength']);
  if (mx > 0 && msg.length > mx) return 'Blocked: too long (${msg.length} characters, the limit is $mx).';
  if (bo(chat['blockLinks']) && _linkRe.hasMatch(msg)) return 'Blocked: links are not allowed.';
  final low = msg.toLowerCase();
  for (final w in _words(chat['bannedWords'])) {
    if (low.contains(w)) return 'Blocked: contains "$w".';
  }
  return null;
}

Future<List<String>?> _pickApps(BuildContext c, String title, List<Map<String, dynamic>> apps, List<String> start) {
  final sel = <String>[...start];
  var q = '';
  return showDialog<List<String>>(
    context: c,
    builder: (d) => StatefulBuilder(builder: (d2, set) {
      final shown = apps.where((a) => q.isEmpty || '${a['name']} ${a['packageName']}'.toLowerCase().contains(q)).take(80).toList();
      return AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: double.maxFinite,
          height: 420,
          child: Column(children: [
            TextField(
              decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Search apps'),
              onChanged: (x) => set(() => q = x.trim().toLowerCase()),
            ),
            Expanded(
              child: ListView(children: [
                for (final a in shown)
                  CheckboxListTile(
                    dense: true,
                    value: sel.contains(s(a['id'])),
                    title: Text(s(a['name']), maxLines: 1, overflow: TextOverflow.ellipsis),
                    subtitle: Text(s(a['category'])),
                    onChanged: (v) => set(() {
                      final id = s(a['id']);
                      if (v == true) {
                        if (!sel.contains(id)) sel.add(id);
                      } else {
                        sel.remove(id);
                      }
                    }),
                  ),
              ]),
            ),
          ]),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(d2), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(d2, sel), child: Text('Save (${sel.length})')),
        ],
      );
    }),
  );
}

// ═══════════════════════════════ RULES (limits + chat) ═══════════════════════════════

Future<Widget> rulesTab(BuildContext c, VoidCallback rl) async {
  final r = await Future.wait<Map<String, dynamic>>([Db.map('limitsConfig'), Db.map('chatRules')]);
  final lim = r[0];
  final chat = r[1];
  final pend = lim['maxPending'] == null ? 3 : ni(lim['maxPending']);
  final day = lim['maxPerDay'] == null ? 5 : ni(lim['maxPerDay']);
  final chatOn = _on(chat, 'enabled');
  final maxLen = ni(chat['maxLength']);
  final slow = ni(chat['slowModeSeconds']);
  final words = _words(chat['bannedWords']);

  Future<void> putLim(Map<String, Object?> patch, String what) => act(c, () async {
        await Db.upd('limitsConfig', patch);
        await Db.audit('LIMITS', 'config', 'limitsConfig', 'Submission limits', what);
      }, rl, 'Saved');

  Future<void> putChat(Map<String, Object?> patch, String what) => act(c, () async {
        await Db.upd('chatRules', patch);
        await Db.audit('CHAT_RULES', 'config', 'chatRules', 'Chat rules', what);
      }, rl, 'Saved');

  Future<void> editLim(String key, String label) async {
    final x = await editSheet(c, label, [F(key, '$label (0 = no limit)', 'num')], {key: key == 'maxPending' ? pend : day});
    if (x != null && c.mounted) await putLim({key: ni(x[key]).clamp(0, 1000).toInt()}, '$key=${x[key]}');
  }

  Future<void> editChat(String key, String label, String type) async {
    final x = await editSheet(c, label, [F(key, label, type)], chat);
    if (x == null || !c.mounted) return;
    final v = type == 'num' ? ni(x[key]).clamp(0, 100000).toInt() : s(x[key]);
    await putChat({key: v}, '$key changed');
  }

  String chatSummary() {
    if (!chatOn) return 'Chat rules are OFF: nothing is checked.';
    final b = <String>[];
    b.add(maxLen > 0 ? 'messages up to $maxLen characters' : 'no length limit');
    b.add(slow > 0 ? 'one message every $slow second${slow == 1 ? '' : 's'} per person' : 'no slow mode');
    if (bo(chat['blockLinks'])) b.add('links blocked');
    b.add(words.isEmpty ? 'no banned words' : '${words.length} banned word${words.length == 1 ? '' : 's'}');
    return 'Everyone except admins: ${b.join(' · ')}.';
  }

  return listPage([
    Card(
      color: Colors.blue.withOpacity(.08),
      child: const Padding(
        padding: EdgeInsets.all(12),
        child: Text(
          'These rules are checked by the database itself, so they hold for every app version and cannot be bypassed from the phone. '
          'Changes apply to new submissions and new messages straight away.',
          style: TextStyle(fontSize: 13, height: 1.4),
        ),
      ),
    ),
    const SizedBox(height: 8),
    const Padding(padding: EdgeInsets.fromLTRB(4, 8, 4, 4), child: Text('Submission limits', style: TextStyle(fontWeight: FontWeight.bold))),
    Card(
      child: Column(children: [
        ListTile(
          leading: const Icon(Icons.hourglass_top),
          title: const Text('Apps waiting for review, per person'),
          subtitle: Text(pend == 0 ? 'No limit' : 'At most $pend at the same time'),
          trailing: const Icon(Icons.edit),
          onTap: () => editLim('maxPending', 'Pending submissions per person'),
        ),
        ListTile(
          leading: const Icon(Icons.today),
          title: const Text('Submissions per 24 hours, per person'),
          subtitle: Text(day == 0 ? 'No limit' : 'At most $day in any 24 hours'),
          trailing: const Icon(Icons.edit),
          onTap: () => editLim('maxPerDay', 'Submissions per 24 hours'),
        ),
      ]),
    ),
    const SizedBox(height: 8),
    Row(children: [
      const Expanded(child: Padding(padding: EdgeInsets.fromLTRB(4, 8, 4, 4), child: Text('Chat rules', style: TextStyle(fontWeight: FontWeight.bold)))),
      tagChip(chatOn ? 'ON' : 'OFF', chatOn ? Colors.green : Colors.grey),
    ]),
    Card(
      color: Colors.amber.withOpacity(.10),
      child: Padding(padding: const EdgeInsets.all(12), child: Text(chatSummary(), style: const TextStyle(fontSize: 13, height: 1.4))),
    ),
    Card(
      child: Column(children: [
        SwitchListTile(
          title: const Text('Check chat messages'),
          subtitle: const Text('Off = no chat rule is applied'),
          value: chatOn,
          onChanged: (v) => putChat({'enabled': v}, 'enabled=$v'),
        ),
        ListTile(
          title: const Text('Longest message (characters)'),
          subtitle: Text(maxLen == 0 ? '0 · no limit' : '$maxLen'),
          trailing: const Icon(Icons.edit),
          onTap: () => editChat('maxLength', 'Longest message (characters, 0 = no limit)', 'num'),
        ),
        ListTile(
          title: const Text('Slow mode (seconds between messages)'),
          subtitle: Text(slow == 0 ? '0 · off' : '$slow seconds'),
          trailing: const Icon(Icons.edit),
          onTap: () => editChat('slowModeSeconds', 'Seconds between messages (0 = off)', 'num'),
        ),
        SwitchListTile(
          title: const Text('Block links'),
          subtitle: const Text('http://, www., t.me/ and names like site.com'),
          value: bo(chat['blockLinks']),
          onChanged: (v) => putChat({'blockLinks': v}, 'blockLinks=$v'),
        ),
        ListTile(
          title: const Text('Banned words'),
          subtitle: Text(words.isEmpty ? 'None' : words.join(', '), maxLines: 3, overflow: TextOverflow.ellipsis),
          trailing: const Icon(Icons.edit),
          onTap: () => editChat('bannedWords', 'Banned words (separate with commas or new lines)', 'area'),
        ),
        ListTile(
          title: const Text('Message shown for a banned word'),
          subtitle: Text(s(chat['blockedMessage']).isEmpty ? 'Default: "Your message contains a word that is not allowed."' : s(chat['blockedMessage'])),
          trailing: const Icon(Icons.edit),
          onTap: () => editChat('blockedMessage', 'Message shown to the person', 'area'),
        ),
      ]),
    ),
    const SizedBox(height: 8),
    OutlinedButton.icon(
      icon: const Icon(Icons.rule),
      label: const Text('Test a message against the rules'),
      onPressed: () async {
        final x = await editSheet(c, 'Test a message', const [F('msg', 'Type a message', 'area')], const {});
        if (x == null || !c.mounted) return;
        final v = _chatVerdict(chat, s(x['msg']));
        await showDialog<void>(
          context: c,
          builder: (d) => AlertDialog(
            title: Text(v == null ? 'Allowed' : 'Blocked'),
            content: Text(v ?? 'This message would be sent.'),
            actions: [TextButton(onPressed: () => Navigator.pop(d), child: const Text('OK'))],
          ),
        );
      },
    ),
    const SizedBox(height: 10),
    const Text(
      'Admins are never limited by chat rules. Slow mode counts the person\'s last message in any chat. '
      'If a setting cannot be read, the database lets messages through instead of blocking chat.',
      style: TextStyle(fontSize: 12, color: Colors.grey, height: 1.4),
    ),
  ]);
}

// ═══════════════════════════════ SUGGESTIONS ═══════════════════════════════

const _weightFields = [
  F('wCategory', 'Same category as apps they use (0-10)'),
  F('wDeveloper', 'Same developer (0-10)'),
  F('wText', 'Similar words in name / description (0-10)'),
  F('wRating', 'Good rating (0-10)'),
  F('wFeatured', 'Featured apps (0-10)'),
  F('wPopular', 'Popular apps (0-10)'),
  F('wFresh', 'Newly added apps (0-10)'),
  F('wSaved', 'How much a saved app says about taste (0-10)'),
];

const _presets = <(String, String, Map<String, Object>)>[
  ('Balanced', 'The default mix', {'wCategory': 3, 'wDeveloper': 2, 'wText': 2, 'wRating': 1, 'wFeatured': 1, 'wPopular': 1, 'wFresh': 1, 'wSaved': 1.5}),
  ('Personal first', 'Mostly what matches their taste', {'wCategory': 4, 'wDeveloper': 3, 'wText': 3, 'wRating': 0.5, 'wFeatured': 0.5, 'wPopular': 0.5, 'wFresh': 0.5, 'wSaved': 2}),
  ('Quality first', 'Best rated, featured and popular apps', {'wCategory': 1, 'wDeveloper': 1, 'wText': 1, 'wRating': 3, 'wFeatured': 2, 'wPopular': 2, 'wFresh': 1, 'wSaved': 1}),
  ('Discover new', 'Favours newly added apps', {'wCategory': 2, 'wDeveloper': 1, 'wText': 1, 'wRating': 1, 'wFeatured': 1, 'wPopular': 0.5, 'wFresh': 4, 'wSaved': 1}),
];

Future<Widget> suggestTab(BuildContext c, VoidCallback rl) async {
  final r = await Future.wait<Object>([Db.map('recommendConfig'), Db.list('apps')]);
  final e = r[0] as Map<String, dynamic>;
  final apps = (r[1] as List<Map<String, dynamic>>).where((a) => s(a['id']).isNotEmpty).toList();
  apps.sort((a, b) => s(a['name']).toLowerCase().compareTo(s(b['name']).toLowerCase()));
  final byId = {for (final a in apps) s(a['id']): a};
  final pinned = _ids(e['pinned']);
  final blocked = _ids(e['blocked']);
  final enabled = _on(e, 'enabled');

  Future<void> put(Map<String, Object?> patch, String what) => act(c, () async {
        await Db.upd('recommendConfig', patch);
        await Db.audit('SUGGESTIONS', 'config', 'recommendConfig', 'Suggestions', what);
      }, rl, 'Saved');

  Widget sw(String k, String title, String sub) => SwitchListTile(
        title: Text(title),
        subtitle: Text(sub),
        value: _on(e, k),
        onChanged: enabled || k == 'enabled' ? (v) => put({k: v}, '$k=$v') : null,
      );

  Future<void> editNum(String k, String label, int def) async {
    final x = await editSheet(c, label, [F(k, label, 'num')], {k: e[k] ?? def});
    if (x != null && c.mounted) await put({k: ni(x[k])}, '$k=${x[k]}');
  }

  Widget idChips(List<String> ids, Color col) {
    if (ids.isEmpty) return const Text('None', style: TextStyle(color: Colors.grey));
    return Wrap(spacing: 4, runSpacing: 4, children: [
      for (final id in ids) tagChip(byId[id] == null ? '$id (not found)' : s(byId[id]!['name']), byId[id] == null ? Colors.red : col),
    ]);
  }

  return listPage([
    Row(children: [tagChip(enabled ? 'SUGGESTIONS ON' : 'SUGGESTIONS OFF', enabled ? Colors.green : Colors.grey)]),
    const SizedBox(height: 6),
    Card(
      color: Colors.blue.withOpacity(.08),
      child: const Padding(
        padding: EdgeInsets.all(12),
        child: Text(
          'How it works: the app builds a taste profile on each phone from the apps the person has installed, saved and opened (nothing is sent to the server). '
          'Every public app is scored by how well it matches that taste, plus its rating, whether it is featured or popular, and how new it is. '
          'New people with no history simply see the best, newest and featured apps. '
          'Pinned apps come first, blocked apps never appear, and no row shows too many apps of one category.',
          style: TextStyle(fontSize: 12.5, height: 1.4),
        ),
      ),
    ),
    const SizedBox(height: 8),
    Card(
      child: Column(children: [
        sw('enabled', 'Suggestions', 'Master switch for every suggestion row'),
        sw('showForYou', '"Suggested for you" on Home', 'Personal row'),
        sw('showBecause', '"Because you looked at / saved / use …" on Home', 'Apps similar to one the person touched'),
        sw('showSimilar', '"Similar apps" on an app page', 'Under "More from developer"'),
        sw('showTrending', '"New & rising" on Home', 'The same for everybody'),
        sw('hideInstalled', 'Hide apps already installed', 'Never suggest what the phone already has'),
      ]),
    ),
    const SizedBox(height: 8),
    Card(
      child: Column(children: [
        ListTile(
          title: const Text('Apps per row'),
          subtitle: Text('${e['maxItems'] ?? 10}'),
          trailing: const Icon(Icons.edit),
          onTap: () => editNum('maxItems', 'Apps per row (3-30)', 10),
        ),
        ListTile(
          title: const Text('Most apps of one category in a row'),
          subtitle: Text('${e['maxPerCategory'] ?? 4}  (0 = no cap)'),
          trailing: const Icon(Icons.edit),
          onTap: () => editNum('maxPerCategory', 'Most apps of one category per row (0 = no cap)', 4),
        ),
        ListTile(
          title: const Text('Daily shuffle'),
          subtitle: Text('${e['explore'] ?? 10}%  · how much the order may change from day to day (0 = never)'),
          trailing: const Icon(Icons.edit),
          onTap: () => editNum('explore', 'Daily shuffle, 0-100', 10),
        ),
      ]),
    ),
    const SizedBox(height: 8),
    Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('What matters most (0-10 each)', style: TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 6),
          Text(
            'Category ${_fmt(_dn(e['wCategory'], 3))} · Developer ${_fmt(_dn(e['wDeveloper'], 2))} · Words ${_fmt(_dn(e['wText'], 2))} · Rating ${_fmt(_dn(e['wRating'], 1))} · '
            'Featured ${_fmt(_dn(e['wFeatured'], 1))} · Popular ${_fmt(_dn(e['wPopular'], 1))} · New ${_fmt(_dn(e['wFresh'], 1))} · Saved ${_fmt(_dn(e['wSaved'], 1.5))}',
            style: const TextStyle(fontSize: 12.5, height: 1.4),
          ),
          const SizedBox(height: 8),
          Wrap(spacing: 8, runSpacing: 4, children: [
            for (final p in _presets)
              ActionChip(
                label: Text(p.$1),
                tooltip: p.$2,
                onPressed: () => put({...p.$3}, 'preset ${p.$1}'),
              ),
            ActionChip(
              avatar: const Icon(Icons.tune, size: 16),
              label: const Text('Custom…'),
              onPressed: () async {
                final init = {for (final f in _weightFields) f.k: _fmt(_dn(e[f.k], f.k == 'wSaved' ? 1.5 : 1))};
                final x = await editSheet(c, 'Suggestion weights', _weightFields, init);
                if (x == null || !c.mounted) return;
                final patch = <String, Object?>{for (final f in _weightFields) f.k: _dn(x[f.k], 1).clamp(0, 10).toDouble()};
                await put(patch, 'custom weights');
              },
            ),
          ]),
        ]),
      ),
    ),
    const SizedBox(height: 8),
    Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            const Expanded(child: Text('Pinned apps (always first)', style: TextStyle(fontWeight: FontWeight.bold))),
            TextButton(
              child: const Text('Choose'),
              onPressed: () async {
                final x = await _pickApps(c, 'Pinned apps', apps, pinned);
                if (x != null && c.mounted) await put({'pinned': x.join(',')}, 'pinned ${x.length}');
              },
            ),
          ]),
          idChips(pinned, Colors.blue),
          const Divider(height: 22),
          Row(children: [
            const Expanded(child: Text('Never suggest these', style: TextStyle(fontWeight: FontWeight.bold))),
            TextButton(
              child: const Text('Choose'),
              onPressed: () async {
                final x = await _pickApps(c, 'Never suggest', apps, blocked);
                if (x != null && c.mounted) await put({'blocked': x.join(',')}, 'blocked ${x.length}');
              },
            ),
          ]),
          idChips(blocked, Colors.red),
        ]),
      ),
    ),
    const SizedBox(height: 8),
    Card(
      child: Column(children: [
        for (final t in const [
          ('forYouTitle', 'Title of "Suggested for you"'),
          ('becauseTitle', 'Title of the "Because you …" row (use {app} for the app name)'),
          ('similarTitle', 'Title of "Similar apps"'),
          ('trendingTitle', 'Title of "New & rising"'),
        ])
          ListTile(
            title: Text(t.$2),
            subtitle: Text(s(e[t.$1]).isEmpty ? 'Default (translated to the person\'s language)' : s(e[t.$1])),
            trailing: const Icon(Icons.edit),
            onTap: () async {
              final x = await editSheet(c, t.$2, [F(t.$1, 'Title (empty = default)')], e);
              if (x != null && c.mounted) await put({t.$1: s(x[t.$1]).trim()}, '${t.$1} changed');
            },
          ),
      ]),
    ),
    const SizedBox(height: 10),
    Text(
      'Only DarkStore apps are suggested (${apps.length} in the catalogue). Apps that are suspended, on hold or upcoming are never suggested.',
      style: const TextStyle(fontSize: 12, color: Colors.grey, height: 1.4),
    ),
  ]);
}

// ═══════════════════════════════ HOME LAYOUT ═══════════════════════════════

const _shelves = <(String, String, String)>[
  ('collections', 'Collections strip', 'The collection cards under the categories'),
  ('saved', 'Saved for later', 'Apps the person saved'),
  ('newArrivals', 'New arrivals', 'The newest DarkStore apps'),
  ('recommended', 'Recommended', 'Apps you marked Featured'),
  ('popular', 'Popular', 'Apps you marked Popular'),
  ('upcoming', 'Coming soon', 'Apps you marked Upcoming'),
  ('free', 'Recent apps', 'Apps you marked Recent'),
];

Future<Widget> homeLayoutTab(BuildContext c, VoidCallback rl) async {
  final e = await Db.map('homeLayout');
  final hidden = _shelves.where((x) => !_on(e, 'show_${x.$1}')).length;

  return listPage([
    Row(children: [tagChip(hidden == 0 ? 'ALL SHELVES SHOWN' : '$hidden HIDDEN', hidden == 0 ? Colors.green : Colors.orange)]),
    const SizedBox(height: 8),
    Card(
      child: Column(children: [
        for (final x in _shelves) ...[
          SwitchListTile(
            title: Text(x.$2),
            subtitle: Text(x.$3),
            value: _on(e, 'show_${x.$1}'),
            onChanged: (v) => act(c, () async {
              await Db.upd('homeLayout', {'show_${x.$1}': v});
              await Db.audit('HOME_LAYOUT', 'config', 'homeLayout', x.$2, v ? 'shown' : 'hidden');
            }, rl, v ? '${x.$2} shown' : '${x.$2} hidden'),
          ),
          if (x.$1 != 'collections' && x.$1 != 'saved' && x.$1 != 'newArrivals')
            ListTile(
              dense: true,
              contentPadding: const EdgeInsetsDirectional.only(start: 32, end: 16),
              title: Text(s(e['title_${x.$1}']).isEmpty ? 'Title: default' : 'Title: ${s(e['title_${x.$1}'])}'),
              trailing: const Icon(Icons.edit, size: 18),
              onTap: () async {
                final k = 'title_${x.$1}';
                final r = await editSheet(c, '${x.$2}: title', [F(k, 'Title (empty = default, translated)')], e);
                if (r == null || !c.mounted) return;
                await act(c, () async {
                  await Db.upd('homeLayout', {k: s(r[k]).trim()});
                  await Db.audit('HOME_LAYOUT', 'config', 'homeLayout', x.$2, 'title');
                }, rl, 'Saved');
              },
            ),
        ],
      ]),
    ),
    const SizedBox(height: 10),
    const Text(
      'Hiding a shelf only removes it from Home; the apps stay in the store and in search. '
      'A custom title is shown as written (it is not translated). '
      'The suggestion rows are controlled in the Suggestions tab.',
      style: TextStyle(fontSize: 12, color: Colors.grey, height: 1.4),
    ),
  ]);
}

// ═══════════════════════════════ SHARE TEXT ═══════════════════════════════

const _shareFields = [
  F('appTemplate', 'When someone shares an app', 'area'),
  F('inviteTemplate', 'When someone shares their invite code', 'area'),
];

const _defaultApp = 'Check out {app} on DarkRoot Store:\n{link}';
const _defaultInvite = 'Join DarkRoot Store with my code {code}\n{link}';

String _fillShare(String tpl, Map<String, String> vals) {
  var out = tpl;
  vals.forEach((k, v) => out = out.replaceAll('{$k}', v));
  final link = vals['link'] ?? '';
  if (!tpl.contains('{link}')) out = '$out\n$link';
  return out.replaceAll('{link}', link);
}

Widget _sharePreview(Map<String, dynamic> v) {
  final app = s(v['appTemplate']).trim().isEmpty ? _defaultApp : s(v['appTemplate']).trim();
  final inv = s(v['inviteTemplate']).trim().isEmpty ? _defaultInvite : s(v['inviteTemplate']).trim();
  final a = _fillShare(app, {'app': 'Sample App', 'developer': 'Sample Dev', 'category': 'Tools', 'link': 'https://darkrootstore.vercel.app/app.html?id=sample'});
  final i = _fillShare(inv, {'code': 'AB12CD34', 'link': 'https://darkrootstore.vercel.app/invite.html?ref=AB12CD34'});
  Widget bubble(String label, String text) => Container(
        width: double.infinity,
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(color: Colors.green.withOpacity(.12), borderRadius: BorderRadius.circular(12)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label, style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: Colors.grey)),
          const SizedBox(height: 3),
          Text(text, style: const TextStyle(fontSize: 13, height: 1.35)),
        ]),
      );
  return Column(children: [bubble('SHARING AN APP', a), bubble('SHARING AN INVITE', i)]);
}

Future<Widget> shareTab(BuildContext c, VoidCallback rl) async {
  final e = await Db.map('shareConfig');
  final custom = s(e['appTemplate']).trim().isNotEmpty || s(e['inviteTemplate']).trim().isNotEmpty;

  return listPage([
    Row(children: [tagChip(custom ? 'CUSTOM TEXT' : 'DEFAULT TEXT', custom ? Colors.blue : Colors.grey)]),
    const SizedBox(height: 8),
    const Text('PREVIEW', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
    const SizedBox(height: 6),
    _sharePreview(e),
    const SizedBox(height: 4),
    Wrap(spacing: 8, runSpacing: 8, children: [
      FilledButton.icon(
        icon: const Icon(Icons.edit),
        label: const Text('Edit share text'),
        onPressed: () async {
          final r = await editSheet(c, 'Share text', _shareFields, {'appTemplate': s(e['appTemplate']), 'inviteTemplate': s(e['inviteTemplate'])}, preview: _sharePreview);
          if (r == null || !c.mounted) return;
          await act(c, () async {
            await Db.upd('shareConfig', {'appTemplate': s(r['appTemplate']).trim(), 'inviteTemplate': s(r['inviteTemplate']).trim()});
            await Db.audit('SHARE_TEXT', 'config', 'shareConfig', 'Share text', 'edited');
          }, rl, 'Saved');
        },
      ),
      OutlinedButton.icon(
        icon: const Icon(Icons.restore),
        label: const Text('Back to default'),
        onPressed: () async {
          if (!await confirm(c, 'Use the default text?', 'Both messages go back to the built-in text.') || !c.mounted) return;
          await act(c, () async {
            await Db.upd('shareConfig', {'appTemplate': '', 'inviteTemplate': ''});
            await Db.audit('SHARE_TEXT', 'config', 'shareConfig', 'Share text', 'reset');
          }, rl, 'Reset');
        },
      ),
    ]),
    const SizedBox(height: 12),
    const Text(
      'Words in curly brackets are filled in for each share:\n'
      '  {app}  app name      {developer}  developer      {category}  category\n'
      '  {code}  invite code (invite message)      {link}  the link\n'
      'The link is always added, even if you forget {link}. Leave a box empty to use the default.',
      style: TextStyle(fontSize: 12, color: Colors.grey, height: 1.5),
    ),
  ]);
}
