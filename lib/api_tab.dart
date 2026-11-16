import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'core.dart';
import 'sb.dart';
import 'security_api.dart' show asMaps;

// ─────────────────────────────────────────────────────────────────────────────
// Public API control (the store-api edge function reads everything set here).
//   Overview   master switch, today's numbers, usage per key, endpoints
//   Keys       create / limit / pause / rotate / delete API keys
//   Apps       which apps the API shows (and which hide their download link)
//   Settings   default limits, fields, categories, text
//   Log        the latest requests
// ─────────────────────────────────────────────────────────────────────────────

const kApiBaseDefault = 'https://mjhuwxmpbxmqgtfkqvsc.supabase.co/functions/v1/store-api/v1';

const Map<String, Object?> kApiDefaults = {
  'enabled': true,
  'requireKey': true,
  'maintenanceMessage': 'The DarkStore API is temporarily switched off.',
  'ratePerMin': 60,
  'ratePerDay': 5000,
  'publicRatePerMin': 20,
  'publicRatePerDay': 500,
  'maxPageSize': 100,
  'defaultPageSize': 20,
  'expose': 'all',
  'excludeCategories': <String>[],
  'includeDownload': true,
  'siteUrl': '',
  'baseUrl': '',
  'attribution': '',
  'fields': {'description': true, 'screenshots': true, 'video': true, 'changelog': true},
};

int _apiSec = 0;
int _apiDays = 7;
String _apiFind = '';

bool _on(Object? v, [bool d = true]) => v == null ? d : (v != false && s(v).toLowerCase() != 'false' && s(v) != '0');

List<String> _csv(Object? v) {
  final raw = v is List ? v.map((e) => '$e').toList() : s(v).split(RegExp(r'[,\n]+'));
  final out = <String>[];
  for (final w in raw) {
    final t = w.trim();
    if (t.isNotEmpty && !out.contains(t)) out.add(t);
  }
  return out;
}

List<String> _strs(Object? v) => v is List ? v.map((e) => '$e').toList() : <String>[];

String _ago(Object? iso) {
  final t = DateTime.tryParse(s(iso))?.toLocal();
  if (t == null) return 'never';
  final d = DateTime.now().difference(t);
  if (d.inMinutes < 1) return 'just now';
  if (d.inHours < 1) return '${d.inMinutes} min ago';
  if (d.inDays < 1) return '${d.inHours} h ago';
  return '${d.inDays} d ago';
}

Future<void> _copy(BuildContext c, String text, [String what = 'Copied']) async {
  await Clipboard.setData(ClipboardData(text: text));
  if (c.mounted) toast(c, what);
}

Future<Widget> apiAdminTab(BuildContext c, VoidCallback rl) async {
  final saved = await Db.map('apiConfig');
  final cfg = <String, dynamic>{...kApiDefaults, ...saved};
  final base = s(cfg['baseUrl']).trim().isEmpty ? kApiBaseDefault : s(cfg['baseUrl']).trim();

  Future<void> put(Map<String, Object?> patch, String what) => act(c, () async {
        await Db.upd('apiConfig', patch);
        await Db.audit('API_CONFIG', 'config', 'apiConfig', 'API', what);
      }, rl, 'Saved');

  Widget sw(String k, String title, String sub, [bool d = true]) => SwitchListTile(
        title: Text(title),
        subtitle: Text(sub),
        value: _on(cfg[k], d),
        onChanged: (v) => put({k: v}, '$k=$v'),
      );

  Future<void> editNum(String k, String label, int lo, int hi) async {
    final x = await editSheet(c, label, [F(k, label, 'num')], {k: cfg[k]});
    if (x != null && c.mounted) await put({k: ni(x[k]).clamp(lo, hi).toInt()}, '$k=${x[k]}');
  }

  Future<void> editText(String k, String title, String hint, {bool area = false}) async {
    final x = await editSheet(c, title, [F(k, hint, area ? 'area' : 'text')], {k: s(cfg[k])});
    if (x != null && c.mounted) await put({k: s(x[k]).trim()}, '$k changed');
  }

  final sections = <(String, IconData)>[
    ('Overview', Icons.insights),
    ('Keys', Icons.vpn_key),
    ('Apps', Icons.apps),
    ('Settings', Icons.tune),
    ('Log', Icons.list_alt),
  ];

  final head = <Widget>[
    SizedBox(
      height: 48,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 4),
        children: [
          for (var i = 0; i < sections.length; i++)
            Padding(
              padding: const EdgeInsets.only(right: 8, top: 6, bottom: 6),
              child: ChoiceChip(
                avatar: Icon(sections[i].$2, size: 16),
                label: Text(sections[i].$1),
                selected: _apiSec == i,
                onSelected: (_) {
                  _apiSec = i;
                  rl();
                },
              ),
            ),
        ],
      ),
    ),
    if (!_on(cfg['enabled']))
      Card(
        color: Colors.red.withOpacity(.1),
        child: const ListTile(
          leading: Icon(Icons.power_settings_new, color: Colors.red),
          title: Text('API is OFF'),
          subtitle: Text('Every request is refused with "temporarily switched off". Turn it on in Overview.'),
        ),
      ),
  ];

  Widget body;
  switch (_apiSec) {
    case 1:
      body = await _keysSection(c, rl, cfg);
      break;
    case 2:
      body = await _appsSection(c, rl, cfg, put);
      break;
    case 3:
      body = _settingsSection(c, cfg, sw, editNum, editText, put);
      break;
    case 4:
      body = await _logSection(c, rl);
      break;
    default:
      body = await _overviewSection(c, rl, cfg, base, sw, editText);
  }
  return listPage([...head, body]);
}

// ═══════════════════════════════ OVERVIEW ═══════════════════════════════

Future<Widget> _overviewSection(BuildContext c, VoidCallback rl, Map<String, dynamic> cfg, String base,
    Widget Function(String, String, String, [bool]) sw, Future<void> Function(String, String, String, {bool area}) editText) async {
  Map<String, dynamic> st = {};
  String? err;
  try {
    final r = await Sb.c.rpc('api_stats', params: {'p_days': _apiDays});
    if (r is Map) st = Map<String, dynamic>.from(r);
  } catch (e) {
    err = '$e';
  }
  final daily = asMaps(st['daily']);
  final perKey = asMaps(st['perKey']);
  final paths = asMaps(st['paths']);
  final maxN = daily.fold<int>(1, (m, d) => ni(d['n']) > m ? ni(d['n']) : m);

  Widget stat(String label, Object? v, IconData ic, [Color? col]) => Expanded(
        child: Card(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
            child: Column(children: [
              Icon(ic, size: 20, color: col),
              const SizedBox(height: 4),
              Text('${v ?? 0}', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
              Text(label, style: const TextStyle(fontSize: 11), textAlign: TextAlign.center),
            ]),
          ),
        ),
      );

  final curl = 'curl -H "X-API-Key: YOUR_KEY" "$base/apps?limit=20"';

  return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Card(
      child: Column(children: [
        SwitchListTile(
          secondary: const Icon(Icons.power_settings_new),
          title: const Text('API enabled', style: TextStyle(fontWeight: FontWeight.w700)),
          subtitle: const Text('Master switch. Off = every request is refused at once.'),
          value: _on(cfg['enabled']),
          onChanged: (v) async {
            if (!v && !await confirm(c, 'Turn the API off?', 'All connected stores stop receiving apps immediately.')) return;
            await act(c, () async {
              await Db.upd('apiConfig', {'enabled': v});
              await Db.audit('API_MASTER', 'config', 'apiConfig', 'API', 'enabled=$v');
            }, rl, v ? 'API is on' : 'API is off');
          },
        ),
        sw('requireKey', 'Require an API key', 'Off = anyone can read the list, with the stricter "no key" limits (Settings)'),
        ListTile(
          leading: const Icon(Icons.campaign_outlined),
          title: const Text('Message when the API is off'),
          subtitle: Text(s(cfg['maintenanceMessage'])),
          trailing: const Icon(Icons.edit),
          onTap: () => editText('maintenanceMessage', 'Message when the API is off', 'Message', area: true),
        ),
      ]),
    ),
    if (err != null)
      Card(color: Colors.orange.withOpacity(.12), child: Padding(padding: const EdgeInsets.all(12), child: Text('Numbers unavailable: $err\nRun supabase/12_public_api.sql first.')))
    else ...[
      Row(children: [
        stat('requests today', st['today'], Icons.today),
        stat('last hour', st['lastHour'], Icons.schedule),
        stat('errors today', st['errorsToday'], Icons.error_outline, Colors.orange),
        stat('rate-limited', st['limitedToday'], Icons.speed, Colors.red),
      ]),
      Row(children: [
        stat('keys', st['keys'], Icons.vpn_key),
        stat('active keys', st['activeKeys'], Icons.check_circle_outline, Colors.green),
      ]),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              const Expanded(child: Text('Requests per day', style: TextStyle(fontWeight: FontWeight.w700))),
              for (final d in const [7, 14, 30])
                Padding(
                  padding: const EdgeInsets.only(left: 6),
                  child: ChoiceChip(
                    label: Text('${d}d'),
                    selected: _apiDays == d,
                    onSelected: (_) {
                      _apiDays = d;
                      rl();
                    },
                  ),
                ),
            ]),
            const SizedBox(height: 10),
            SizedBox(
              height: 90,
              child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                for (final d in daily)
                  Expanded(
                    child: Tooltip(
                      message: '${d['day']}: ${d['n']} (${d['err']} errors)',
                      child: Container(
                        margin: const EdgeInsets.symmetric(horizontal: 1.5),
                        height: 4 + 82 * (ni(d['n']) / maxN),
                        decoration: BoxDecoration(color: Colors.blue.withOpacity(.75), borderRadius: BorderRadius.circular(3)),
                      ),
                    ),
                  ),
              ]),
            ),
          ]),
        ),
      ),
      if (perKey.isNotEmpty)
        Card(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Padding(padding: EdgeInsets.fromLTRB(14, 12, 14, 0), child: Text('Usage per key', style: TextStyle(fontWeight: FontWeight.w700))),
            for (final k in perKey)
              ListTile(
                dense: true,
                title: Text(s(k['name'])),
                subtitle: Text('${k['n']} requests · ${k['err']} errors · ${k['avgMs']} ms average'),
              ),
          ]),
        ),
      if (paths.isNotEmpty)
        Card(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Padding(padding: EdgeInsets.fromLTRB(14, 12, 14, 0), child: Text('Endpoints used', style: TextStyle(fontWeight: FontWeight.w700))),
            for (final p in paths) ListTile(dense: true, title: Text(s(p['path'])), trailing: Text('${p['n']}')),
          ]),
        ),
    ],
    Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('For developers', style: TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 6),
          SelectableText(base, style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
          const SizedBox(height: 6),
          const Text('GET /apps  ·  GET /apps/{package}  ·  GET /categories  ·  GET /status\n'
              'Parameters: q, category, sort (updated | rating | name | new), limit, offset, updated_since\n'
              'Header: X-API-Key: <key>',
              style: TextStyle(fontSize: 12, height: 1.4)),
          const SizedBox(height: 6),
          Wrap(spacing: 8, children: [
            OutlinedButton.icon(onPressed: () => _copy(c, base, 'Base URL copied'), icon: const Icon(Icons.copy, size: 16), label: const Text('Copy URL')),
            OutlinedButton.icon(onPressed: () => _copy(c, curl, 'Example copied'), icon: const Icon(Icons.terminal, size: 16), label: const Text('Copy example')),
          ]),
        ]),
      ),
    ),
  ]);
}

// ═══════════════════════════════ KEYS ═══════════════════════════════

Future<Widget> _keysSection(BuildContext c, VoidCallback rl, Map<String, dynamic> cfg) async {
  final keys = asMaps(await Sb.c.from('api_keys').select().order('created_at', ascending: false));

  Future<void> showKey(String title, String key) => showDialog<void>(
        context: c,
        barrierDismissible: false,
        builder: (d) => AlertDialog(
          title: Text(title),
          content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Copy it now. It is shown ONCE and cannot be read again (only a hash is stored).', style: TextStyle(fontSize: 13)),
            const SizedBox(height: 10),
            SelectableText(key, style: const TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.w700)),
          ]),
          actions: [
            TextButton(onPressed: () => Clipboard.setData(ClipboardData(text: key)), child: const Text('Copy')),
            FilledButton(onPressed: () => Navigator.pop(d), child: const Text('I saved it')),
          ],
        ),
      );

  List<F> fields() => const [
        F('name', 'Name (who is this key for?)'),
        F('owner_contact', 'Contact (email or website)'),
        F('note', 'Note', 'area'),
        F('rate_per_min', 'Requests per minute (0 = default)', 'num'),
        F('rate_per_day', 'Requests per day (0 = default)', 'num'),
        F('scope_search', 'May use search (q=)', 'bool'),
        F('scope_download', 'May receive download links', 'bool'),
        F('allowed_ips', 'Only from these IPs (comma separated, blank = any)'),
        F('allowed_origins', 'Only from these websites, e.g. https://store.example.com (blank = any)'),
        F('allowed_categories', 'Only these categories (comma separated, blank = all)'),
        F('expires', 'Expires on (YYYY-MM-DD, blank = never)'),
      ];

  Map<String, dynamic> initOf(Map<String, dynamic> k) => {
        'name': s(k['name']),
        'owner_contact': s(k['owner_contact']),
        'note': s(k['note']),
        'rate_per_min': k['rate_per_min'] ?? 0,
        'rate_per_day': k['rate_per_day'] ?? 0,
        'scope_search': _strs(k['scopes']).contains('search'),
        'scope_download': _strs(k['scopes']).contains('download'),
        'allowed_ips': _strs(k['allowed_ips']).join(', '),
        'allowed_origins': _strs(k['allowed_origins']).join(', '),
        'allowed_categories': _strs(k['allowed_categories']).join(', '),
        'expires': s(k['expires_at']).length >= 10 ? s(k['expires_at']).substring(0, 10) : '',
      };

  List<String> scopesOf(Map<String, dynamic> x) => [
        'apps',
        if (_on(x['scope_search'])) 'search',
        if (_on(x['scope_download'])) 'download',
      ];

  Future<void> create() async {
    final x = await editSheet(c, 'New API key', fields(), {
      'name': '', 'owner_contact': '', 'note': '', 'rate_per_min': 0, 'rate_per_day': 0,
      'scope_search': true, 'scope_download': true, 'allowed_ips': '', 'allowed_origins': '', 'allowed_categories': '', 'expires': '',
    });
    if (x == null || !c.mounted) return;
    if (s(x['name']).trim().isEmpty) {
      toast(c, 'Give the key a name');
      return;
    }
    try {
      final exp = DateTime.tryParse(s(x['expires']).trim());
      final r = await Sb.c.rpc('api_key_create', params: {
        'p_name': s(x['name']).trim(),
        'p_contact': s(x['owner_contact']).trim(),
        'p_note': s(x['note']).trim(),
        'p_scopes': scopesOf(x),
        'p_rate_min': ni(x['rate_per_min']) > 0 ? ni(x['rate_per_min']) : null,
        'p_rate_day': ni(x['rate_per_day']) > 0 ? ni(x['rate_per_day']) : null,
        'p_ips': _csv(x['allowed_ips']),
        'p_origins': _csv(x['allowed_origins']),
        'p_categories': _csv(x['allowed_categories']),
        'p_expires': exp?.toUtc().toIso8601String(),
      });
      await Db.audit('API_KEY_CREATE', 'apiKey', s(r['id']), s(x['name']), 'created');
      if (c.mounted) await showKey('Key for ${s(x['name']).trim()}', s(r['key']));
    } catch (e) {
      if (c.mounted) toast(c, 'Failed: $e');
    }
    rl();
  }

  Future<void> edit(Map<String, dynamic> k) async {
    final x = await editSheet(c, 'Edit ${s(k['name'])}', fields(), initOf(k));
    if (x == null || !c.mounted) return;
    final exp = DateTime.tryParse(s(x['expires']).trim());
    await act(c, () async {
      await Sb.c.from('api_keys').update({
        'name': s(x['name']).trim().isEmpty ? k['name'] : s(x['name']).trim(),
        'owner_contact': s(x['owner_contact']).trim(),
        'note': s(x['note']).trim(),
        'rate_per_min': ni(x['rate_per_min']) > 0 ? ni(x['rate_per_min']) : null,
        'rate_per_day': ni(x['rate_per_day']) > 0 ? ni(x['rate_per_day']) : null,
        'scopes': scopesOf(x),
        'allowed_ips': _csv(x['allowed_ips']),
        'allowed_origins': _csv(x['allowed_origins']),
        'allowed_categories': _csv(x['allowed_categories']),
        'expires_at': exp?.toUtc().toIso8601String(),
      }).eq('id', s(k['id']));
      await Db.audit('API_KEY_EDIT', 'apiKey', s(k['id']), s(k['name']), 'edited');
    }, rl, 'Saved');
  }

  Future<void> rotate(Map<String, dynamic> k) async {
    if (!await confirm(c, 'Make a new key?', 'The old key for "${k['name']}" stops working at once.')) return;
    try {
      final r = await Sb.c.rpc('api_key_rotate', params: {'p_id': s(k['id'])});
      await Db.audit('API_KEY_ROTATE', 'apiKey', s(k['id']), s(k['name']), 'rotated');
      if (c.mounted) await showKey('New key for ${s(k['name'])}', s(r['key']));
    } catch (e) {
      if (c.mounted) toast(c, 'Failed: $e');
    }
    rl();
  }

  Future<void> remove(Map<String, dynamic> k) async {
    if (!await confirm(c, 'Delete this key?', '"${k['name']}" and its request history are removed for good.')) return;
    await act(c, () async {
      await Sb.c.from('api_keys').delete().eq('id', s(k['id']));
      await Db.audit('API_KEY_DELETE', 'apiKey', s(k['id']), s(k['name']), 'deleted');
    }, rl, 'Deleted');
  }

  return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: FilledButton.icon(onPressed: create, icon: const Icon(Icons.add), label: const Text('New API key')),
    ),
    if (keys.isEmpty)
      const Card(child: Padding(padding: EdgeInsets.all(16), child: Text('No keys yet. Create one for each store that wants to connect, so you can limit or pause them one by one.'))),
    for (final k in keys)
      Card(
        child: Column(children: [
          ListTile(
            leading: Icon(Icons.vpn_key, color: _on(k['enabled']) ? Colors.green : Colors.grey),
            title: Text(s(k['name']), style: const TextStyle(fontWeight: FontWeight.w700)),
            subtitle: Text('${s(k['key_prefix'])}…  ·  ${k['total_requests']} requests  ·  last ${_ago(k['last_used_at'])}\n'
                '${k['rate_per_min'] ?? cfg['ratePerMin']}/min · ${k['rate_per_day'] ?? cfg['ratePerDay']}/day'
                '${_strs(k['allowed_categories']).isEmpty ? '' : ' · ${_strs(k['allowed_categories']).length} categories'}'),
            isThreeLine: true,
            trailing: Switch(
              value: _on(k['enabled']),
              onChanged: (v) => act(c, () async {
                await Sb.c.from('api_keys').update({'enabled': v}).eq('id', s(k['id']));
                await Db.audit('API_KEY_TOGGLE', 'apiKey', s(k['id']), s(k['name']), 'enabled=$v');
              }, rl, v ? 'Key enabled' : 'Key paused'),
            ),
            onTap: () => edit(k),
          ),
          if (s(k['expires_at']).isNotEmpty || _strs(k['allowed_ips']).isNotEmpty || _strs(k['allowed_origins']).isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 4),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Wrap(children: [
                  if (s(k['expires_at']).isNotEmpty) tagChip('expires ${s(k['expires_at']).substring(0, 10)}', Colors.orange),
                  if (_strs(k['allowed_ips']).isNotEmpty) tagChip('IP locked', Colors.blue),
                  if (_strs(k['allowed_origins']).isNotEmpty) tagChip('site locked', Colors.blue),
                  if (!_strs(k['scopes']).contains('download')) tagChip('no downloads', Colors.purple),
                ]),
              ),
            ),
          OverflowBar(children: [
            TextButton.icon(onPressed: () => rotate(k), icon: const Icon(Icons.autorenew, size: 16), label: const Text('New key')),
            TextButton.icon(
                onPressed: () => remove(k), icon: const Icon(Icons.delete_outline, size: 16, color: Colors.red), label: const Text('Delete', style: TextStyle(color: Colors.red))),
          ]),
        ]),
      ),
  ]);
}

// ═══════════════════════════════ APPS ═══════════════════════════════

Future<Widget> _appsSection(BuildContext c, VoidCallback rl, Map<String, dynamic> cfg,
    Future<void> Function(Map<String, Object?>, String) put) async {
  final apps = asMaps(await Sb.c
      .from('apps')
      .select('id,name,package_name,logo,category,is_approved,is_suspended,is_on_hold,is_upcoming')
      .order('name'));
  final rules = {for (final r in asMaps(await Sb.c.from('api_app_rules').select())) s(r['app_id']): r};
  final allow = s(cfg['expose']) == 'allowlist';
  final q = _apiFind.trim().toLowerCase();

  bool live(Map<String, dynamic> a) =>
      _on(a['is_approved']) && !_on(a['is_suspended'], false) && !_on(a['is_on_hold'], false) && !_on(a['is_upcoming'], false);

  bool shown(Map<String, dynamic> a) {
    final r = rules[s(a['id'])];
    if (_on(r?['hidden'], false)) return false;
    return allow ? _on(r?['listed'], false) : true;
  }

  Future<void> setRule(Map<String, dynamic> a, Map<String, Object?> patch, String what) => act(c, () async {
        await Sb.c.from('api_app_rules').upsert({'app_id': s(a['id']), ...patch, 'updated_at': DateTime.now().toUtc().toIso8601String()}, onConflict: 'app_id');
        await Db.audit('API_APP_RULE', 'app', s(a['id']), s(a['name']), what);
      }, rl, 'Saved');

  Future<void> bulk(bool show) async {
    if (!await confirm(c, show ? 'Show all live apps?' : 'Hide all apps?', 'This changes ${apps.where(live).length} apps in the API.')) return;
    await act(c, () async {
      final rows = [
        for (final a in apps.where(live))
          {
            'app_id': s(a['id']),
            if (allow) 'listed': show else 'hidden': !show,
            'updated_at': DateTime.now().toUtc().toIso8601String(),
          }
      ];
      if (rows.isNotEmpty) await Sb.c.from('api_app_rules').upsert(rows, onConflict: 'app_id');
      await Db.audit('API_APP_BULK', 'config', 'apiConfig', 'API', show ? 'show all' : 'hide all');
    }, rl, 'Saved');
  }

  final list = apps
      .where((a) => q.isEmpty || s(a['name']).toLowerCase().contains(q) || s(a['package_name']).toLowerCase().contains(q))
      .toList();
  final shownCount = apps.where((a) => live(a) && shown(a)).length;

  return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('Which apps does the API show?', style: TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 8),
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(value: 'all', label: Text('All live apps')),
              ButtonSegment(value: 'allowlist', label: Text('Only apps I pick')),
            ],
            selected: {allow ? 'allowlist' : 'all'},
            onSelectionChanged: (v) => put({'expose': v.first}, 'expose=${v.first}'),
          ),
          const SizedBox(height: 8),
          Text(
              'Live = approved and not suspended, on hold or upcoming (those are never shown). '
              '${allow ? 'Only apps you switch on below are shown.' : 'Every live app is shown except the ones you switch off below.'}\n'
              'Now shown: $shownCount of ${apps.where(live).length} live apps.',
              style: const TextStyle(fontSize: 12, height: 1.4)),
          Wrap(spacing: 8, children: [
            TextButton(onPressed: () => bulk(true), child: const Text('Show all')),
            TextButton(onPressed: () => bulk(false), child: const Text('Hide all')),
          ]),
        ]),
      ),
    ),
    Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: TextField(
        decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Find an app', border: OutlineInputBorder(), isDense: true),
        controller: TextEditingController(text: _apiFind)..selection = TextSelection.collapsed(offset: _apiFind.length),
        onSubmitted: (v) {
          _apiFind = v;
          rl();
        },
      ),
    ),
    for (final a in list)
      Card(
        child: ListTile(
          leading: netImg(s(a['logo'])),
          title: Text(s(a['name']), maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: Wrap(crossAxisAlignment: WrapCrossAlignment.center, children: [
            Text(s(a['package_name']), style: const TextStyle(fontSize: 11)),
            if (!live(a)) tagChip('not live', Colors.orange),
            if (_on(rules[s(a['id'])]?['hide_download'], false)) tagChip('no download', Colors.purple),
          ]),
          trailing: Row(mainAxisSize: MainAxisSize.min, children: [
            IconButton(
              tooltip: 'Hide the download link in the API',
              icon: Icon(_on(rules[s(a['id'])]?['hide_download'], false) ? Icons.download_for_offline : Icons.download_outlined,
                  color: _on(rules[s(a['id'])]?['hide_download'], false) ? Colors.purple : null),
              onPressed: () => setRule(a, {'hide_download': !_on(rules[s(a['id'])]?['hide_download'], false)}, 'hide_download toggled'),
            ),
            Switch(
              value: shown(a),
              onChanged: (v) => allow
                  ? setRule(a, {'listed': v, 'hidden': false}, 'listed=$v')
                  : setRule(a, {'hidden': !v}, 'hidden=${!v}'),
            ),
          ]),
        ),
      ),
  ]);
}

// ═══════════════════════════════ SETTINGS ═══════════════════════════════

Widget _settingsSection(
  BuildContext c,
  Map<String, dynamic> cfg,
  Widget Function(String, String, String, [bool]) sw,
  Future<void> Function(String, String, int, int) editNum,
  Future<void> Function(String, String, String, {bool area}) editText,
  Future<void> Function(Map<String, Object?>, String) put,
) {
  final fields = cfg['fields'] is Map ? Map<String, dynamic>.from(cfg['fields'] as Map) : <String, dynamic>{};
  Widget fsw(String k, String title) => SwitchListTile(
        title: Text(title),
        value: _on(fields[k]),
        onChanged: (v) => put({'fields': {...kApiDefaults['fields'] as Map, ...fields, k: v}}, 'field $k=$v'),
      );
  Widget numTile(String k, String title, String sub, int lo, int hi) => ListTile(
        title: Text(title),
        subtitle: Text('${cfg[k]}  ·  $sub'),
        trailing: const Icon(Icons.edit),
        onTap: () => editNum(k, title, lo, hi),
      );
  final excl = _strs(cfg['excludeCategories']);

  return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Card(
      child: Column(children: [
        const ListTile(title: Text('Default limits (keys can override)', style: TextStyle(fontWeight: FontWeight.w700))),
        numTile('ratePerMin', 'Requests per minute, per key', 'default for every key', 1, 10000),
        numTile('ratePerDay', 'Requests per day, per key', 'default for every key', 1, 1000000),
        numTile('publicRatePerMin', 'Per minute WITHOUT a key (per IP)', 'only used when keys are not required', 1, 1000),
        numTile('publicRatePerDay', 'Per day WITHOUT a key (per IP)', 'only used when keys are not required', 1, 100000),
        numTile('maxPageSize', 'Most apps in one answer', 'apps per request (1 - 500)', 1, 500),
        numTile('defaultPageSize', 'Apps per answer when not asked', 'apps per request', 1, 200),
      ]),
    ),
    Card(
      child: Column(children: [
        const ListTile(title: Text('What the API returns', style: TextStyle(fontWeight: FontWeight.w700))),
        sw('includeDownload', 'Include APK download links', 'Off = no key can receive them (keys can also be blocked one by one)'),
        fsw('description', 'Description'),
        fsw('screenshots', 'Screenshots'),
        fsw('video', 'Video link'),
        fsw('changelog', 'What is new'),
        ListTile(
          title: const Text('Hide whole categories'),
          subtitle: Text(excl.isEmpty ? 'None' : excl.join(', ')),
          trailing: const Icon(Icons.edit),
          onTap: () async {
            final x = await editSheet(c, 'Hide categories', const [F('v', 'Category names, comma separated', 'area')], {'v': excl.join(', ')});
            if (x != null && c.mounted) await put({'excludeCategories': _csv(x['v'])}, 'excludeCategories changed');
          },
        ),
      ]),
    ),
    Card(
      child: Column(children: [
        const ListTile(title: Text('Links and credit', style: TextStyle(fontWeight: FontWeight.w700))),
        ListTile(
          title: const Text('Website address'),
          subtitle: Text(s(cfg['siteUrl']).isEmpty ? 'Blank = apps have no "storeUrl"' : s(cfg['siteUrl'])),
          trailing: const Icon(Icons.edit),
          onTap: () => editText('siteUrl', 'Website address', 'https://your-site.com (no slash at the end)'),
        ),
        ListTile(
          title: const Text('API address shown to developers'),
          subtitle: Text(s(cfg['baseUrl']).isEmpty ? 'Default (the Supabase function address)' : s(cfg['baseUrl'])),
          trailing: const Icon(Icons.edit),
          onTap: () => editText('baseUrl', 'API address', 'Only if you put your own domain in front'),
        ),
        ListTile(
          title: const Text('Credit line added to every answer'),
          subtitle: Text(s(cfg['attribution']).isEmpty ? 'None' : s(cfg['attribution'])),
          trailing: const Icon(Icons.edit),
          onTap: () => editText('attribution', 'Credit line', 'e.g. Apps provided by DarkStore'),
        ),
      ]),
    ),
  ]);
}

// ═══════════════════════════════ LOG ═══════════════════════════════

Future<Widget> _logSection(BuildContext c, VoidCallback rl) async {
  final rows = asMaps(await Sb.c.from('api_usage').select('at,key_id,ip,path,status,ms').order('at', ascending: false).limit(80));
  final keys = {for (final k in asMaps(await Sb.c.from('api_keys').select('id,name'))) s(k['id']): s(k['name'])};

  Future<void> purge() async {
    final x = await editSheet(c, 'Delete old log rows', const [F('d', 'Delete rows older than (days)', 'num')], {'d': 30});
    if (x == null || !c.mounted) return;
    await act(c, () async {
      final n = await Sb.c.rpc('api_purge', params: {'p_days': ni(x['d'])});
      await Db.audit('API_PURGE', 'config', 'apiUsage', 'API', 'deleted $n rows');
    }, rl, 'Old rows deleted');
  }

  Color col(int st) => st >= 500 ? Colors.red : (st == 429 ? Colors.orange : (st >= 400 ? Colors.deepOrange : Colors.green));

  return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Wrap(spacing: 8, children: [
        OutlinedButton.icon(onPressed: rl, icon: const Icon(Icons.refresh, size: 16), label: const Text('Refresh')),
        OutlinedButton.icon(onPressed: purge, icon: const Icon(Icons.delete_sweep, size: 16), label: const Text('Clean old rows')),
      ]),
    ),
    if (rows.isEmpty) const Card(child: Padding(padding: EdgeInsets.all(16), child: Text('No requests yet.'))),
    for (final r in rows)
      Card(
        child: ListTile(
          dense: true,
          leading: Text('${r['status']}', style: TextStyle(fontWeight: FontWeight.w800, color: col(ni(r['status'])))),
          title: Text('${s(r['path'])}  ·  ${keys[s(r['key_id'])] ?? (s(r['key_id']).isEmpty ? 'no key' : 'deleted key')}'),
          subtitle: Text('${_ago(r['at'])} · ${s(r['ip'])} · ${r['ms']} ms'),
        ),
      ),
  ]);
}
