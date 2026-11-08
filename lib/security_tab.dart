import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import 'core.dart';
import 'security_api.dart';
import 'tabs.dart' show approveSub, rejectSub;

// ───────────────────────── look ─────────────────────────

const Color _green = Color(0xFF22C55E);
const Color _amber = Color(0xFFF59E0B);
const Color _red = Color(0xFFEF4444);
const Color _grey = Color(0xFF8A94A6);

class _V {
  final String label;
  final Color color;
  final IconData icon;
  const _V(this.label, this.color, this.icon);
}

_V statusV(String st) {
  switch (st) {
    case 'QUARANTINED':
      return const _V('Needs review', _amber, Icons.pause_circle_rounded);
    case 'SCAN_FAILED':
      return const _V('Scan failed', _red, Icons.sync_problem_rounded);
    case 'PUBLISH_FAILED':
      return const _V('Publish failed', _red, Icons.cloud_off_rounded);
    case 'REJECTED':
      return const _V('Rejected', _red, Icons.gpp_bad_rounded);
    case 'PUBLISHED':
      return const _V('Published', _green, Icons.check_circle_rounded);
    case 'APPROVED':
      return const _V('Approved', _green, Icons.verified_rounded);
    case 'PUBLISHING':
      return const _V('Publishing', accent, Icons.rocket_launch_rounded);
    case 'RECEIVED':
      return const _V('Queued', accent, Icons.inbox_rounded);
    case 'DOWNLOADING':
      return const _V('Downloading', accent, Icons.cloud_download_rounded);
    case 'ANALYZING':
      return const _V('Analysing', accent, Icons.manage_search_rounded);
    case 'VIRUSTOTAL_PENDING':
      return const _V('Malware scan', accent, Icons.shield_moon_rounded);
    default:
      return _V(st.isEmpty ? 'Not started' : st, _grey, Icons.hourglass_empty_rounded);
  }
}

Color riskColor(String r) {
  switch (r.toLowerCase()) {
    case 'low':
      return _green;
    case 'medium':
    case 'moderate':
      return _amber;
    case 'high':
    case 'critical':
      return _red;
    default:
      return _grey;
  }
}

Widget pill(String t, Color c, {IconData? icon}) => Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
      decoration: BoxDecoration(color: c.withOpacity(.14), borderRadius: BorderRadius.circular(99)),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        if (icon != null) ...[Icon(icon, size: 13, color: c), const SizedBox(width: 4)],
        Text(t, style: TextStyle(color: c, fontSize: 11, fontWeight: FontWeight.w800)),
      ]),
    );

String agoIso(Object? iso) {
  final t = DateTime.tryParse(s(iso))?.toLocal();
  if (t == null) return '-';
  final d = DateTime.now().difference(t);
  if (d.inSeconds < 45) return 'just now';
  if (d.inMinutes < 60) return '${d.inMinutes} min ago';
  if (d.inHours < 24) return '${d.inHours} h ago';
  return '${d.inDays} d ago';
}

String dateIso(Object? iso) {
  final t = DateTime.tryParse(s(iso))?.toLocal();
  if (t == null) return s(iso).isEmpty ? '-' : s(iso);
  String p(int x) => x.toString().padLeft(2, '0');
  return '${t.year}-${p(t.month)}-${p(t.day)} ${p(t.hour)}:${p(t.minute)}';
}

String bytes(Object? v) {
  final n = ni(v);
  if (n <= 0) return '-';
  if (n < 1024) return '$n B';
  if (n < 1024 * 1024) return '${(n / 1024).toStringAsFixed(1)} KB';
  return '${(n / 1024 / 1024).toStringAsFixed(1)} MB';
}

void _copy(BuildContext c, String v, [String what = 'Copied']) {
  Clipboard.setData(ClipboardData(text: v));
  toast(c, what);
}

Future<void> _open(String url) async {
  final u = Uri.tryParse(url);
  if (u != null) await launchUrl(u, mode: LaunchMode.externalApplication);
}

Widget _section(BuildContext c, String title, List<Widget> kids, {Widget? trailing}) => Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(child: Text(title, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 15))),
            if (trailing != null) trailing,
          ]),
          const SizedBox(height: 10),
          ...kids,
        ]),
      ),
    );

Widget _kv(BuildContext c, String k, Object? v, {bool copy = false, bool mono = false}) {
  final t = s(v).isEmpty ? '-' : s(v);
  return Padding(
    padding: const EdgeInsets.symmetric(vertical: 3),
    child: InkWell(
      onTap: copy && t != '-' ? () => _copy(c, t) : null,
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        SizedBox(width: 118, child: Text(k, style: const TextStyle(color: _grey, fontSize: 12.5))),
        Expanded(
          child: Text(t,
              style: TextStyle(fontSize: 12.5, fontFamily: mono ? 'monospace' : null, fontWeight: FontWeight.w600)),
        ),
        if (copy && t != '-') const Padding(padding: EdgeInsets.only(left: 4), child: Icon(Icons.copy_rounded, size: 13, color: _grey)),
      ]),
    ),
  );
}

// ───────────────────────── Security tab ─────────────────────────

/// "Security" tab: service health, counts per status (tap to filter), search and the
/// list of checked submissions. Refreshes by itself while something is being scanned.
class SecurityTab extends StatefulWidget {
  const SecurityTab({super.key});
  @override
  State<SecurityTab> createState() => _SecurityTabState();
}

class _SecurityTabState extends State<SecurityTab> {
  bool _loading = true, _setup = false, _more = false;
  String? _error;
  Map<String, dynamic> _ov = {}, _svc = {};
  List<Map<String, dynamic>> _items = [];
  int _total = 0;
  String? _filter;
  String _q = '';
  Map<String, Map<String, dynamic>> _subs = {};
  DateTime _subsAt = DateTime.fromMillisecondsSinceEpoch(0);
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _load();
    _timer = Timer.periodic(const Duration(seconds: 12), (_) {
      if (!mounted || _more || _loading) return;
      final counts = asMap(_ov['counts']);
      final active = secStatuses.any((st) => secActive(st) && ni(counts[st]) > 0);
      if (active) _load(silent: true);
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _load({bool silent = false, bool force = false}) async {
    if (!silent && mounted) setState(() { _loading = true; _error = null; });
    try {
      if (!await SecApi.configured()) {
        if (mounted) setState(() { _setup = true; _loading = false; });
        return;
      }
      if (force || DateTime.now().difference(_subsAt) > const Duration(seconds: 90)) {
        final L = await Db.list('submissions');
        _subs = {for (final x in L) s(x['id']): x};
        _subsAt = DateTime.now();
      }
      final r = await Future.wait<Map<String, dynamic>>([
        SecApi.overview(),
        SecApi.list(status: _filter, limit: 25, offset: 0),
        SecApi.serviceStatus().catchError((Object _) => <String, dynamic>{}),
      ]);
      if (!mounted) return;
      setState(() {
        _setup = false;
        _ov = r[0];
        _items = asMaps(r[1]['items']);
        _total = ni(r[1]['total']);
        _svc = r[2];
        _loading = false;
        _error = null;
      });
    } on SecError catch (e) {
      if (mounted) setState(() { _error = e.message; _loading = false; });
    } catch (e) {
      if (mounted) setState(() { _error = '$e'; _loading = false; });
    }
  }

  Future<void> _loadMore() async {
    if (_more || _items.length >= _total) return;
    setState(() => _more = true);
    try {
      final r = await SecApi.list(status: _filter, limit: 25, offset: _items.length);
      if (mounted) setState(() => _items = [..._items, ...asMaps(r['items'])]);
    } on SecError catch (e) {
      if (mounted) toast(context, e.message);
    }
    if (mounted) setState(() => _more = false);
  }

  Future<void> _setUrl() async {
    final cur = await SecApi.base();
    if (!mounted) return;
    final x = await editSheet(context, 'Security service address',
        const [F('url', 'https://security.yourdomain.com')], {'url': cur});
    if (x == null || !mounted) return;
    final v = s(x['url']).trim().replaceAll(RegExp(r'/+$'), '');
    if (!v.startsWith('https://')) {
      toast(context, 'The address must start with https://');
      return;
    }
    if (!await confirm(context, 'Use this address?',
        'Your admin sign-in token is sent to this server. Only use the address of your own security backend.')) {
      return;
    }
    await act(context, () async {
      await Db.put('notifierConfig/securityApiUrl', v);
      await Db.audit('SECURITY_URL', 'config', 'securityApiUrl', 'Security service', v);
    }, () => _load(force: true), 'Saved');
  }

  @override
  Widget build(BuildContext c) {
    if (_loading && _items.isEmpty && _ov.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_setup) return _setupCard(c);
    final counts = asMap(_ov['counts']);
    int n(String k) => ni(counts[k]);
    final inProgress = secStatuses.where(secActive).fold<int>(0, (a, st) => a + n(st));

    final q = _q.toLowerCase();
    final shown = _items.where((it) {
      if (q.isEmpty) return true;
      final sub = _subs[s(it['submission_id'])];
      return '${it['package_name']} ${sub?['name']} ${sub?['submittedBy']}'.toLowerCase().contains(q);
    }).toList();

    return RefreshIndicator(
      onRefresh: () => _load(force: true),
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(12),
        children: [
          if (_error != null)
            Card(
              color: _red.withOpacity(.12),
              child: ListTile(
                leading: const Icon(Icons.error_outline, color: _red),
                title: Text(_error!),
                trailing: TextButton(onPressed: _load, child: const Text('Retry')),
              ),
            ),
          _serviceCard(c),
          const SizedBox(height: 10),
          Wrap(spacing: 8, runSpacing: 8, children: [
            _tile('Needs review', n('QUARANTINED'), _amber, () => _setFilter('QUARANTINED')),
            _tile('Scan failed', n('SCAN_FAILED'), _red, () => _setFilter('SCAN_FAILED')),
            _tile('Publish failed', n('PUBLISH_FAILED'), _red, () => _setFilter('PUBLISH_FAILED')),
            _tile('In progress', inProgress, accent, null),
            _tile('Published', n('PUBLISHED'), _green, () => _setFilter('PUBLISHED')),
            _tile('Rejected', n('REJECTED'), _grey, () => _setFilter('REJECTED')),
          ]),
          const SizedBox(height: 12),
          SizedBox(
            height: 38,
            child: ListView(scrollDirection: Axis.horizontal, children: [
              _fchip('All', null, ni(_ov['total'])),
              for (final st in secStatuses)
                if (n(st) > 0 || _filter == st) _fchip(statusV(st).label, st, n(st)),
            ]),
          ),
          const SizedBox(height: 8),
          TextField(
            decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search), hintText: 'Search app, package or developer…', border: OutlineInputBorder(), isDense: true),
            onChanged: (x) => setState(() => _q = x),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Text('${shown.length} shown · $_total ${_filter == null ? 'checked' : statusV(_filter!).label.toLowerCase()}',
                style: const TextStyle(color: _grey, fontSize: 12)),
          ),
          if (shown.isEmpty && !_loading)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 40),
              child: Center(child: Text('Nothing here.', style: TextStyle(color: _grey))),
            ),
          for (final it in shown) _card(c, it),
          if (_items.length < _total)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 10),
              child: Center(
                child: OutlinedButton.icon(
                  onPressed: _more ? null : _loadMore,
                  icon: _more
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.expand_more),
                  label: Text('Load more (${_total - _items.length} left)'),
                ),
              ),
            ),
          const SizedBox(height: 40),
        ],
      ),
    );
  }

  void _setFilter(String? st) {
    setState(() => _filter = _filter == st ? null : st);
    _load();
  }

  Widget _fchip(String label, String? st, int n) => Padding(
        padding: const EdgeInsets.only(right: 6),
        child: ChoiceChip(
          label: Text('$label  $n'),
          selected: _filter == st,
          onSelected: (_) => _setFilter(st),
        ),
      );

  Widget _tile(String label, int n, Color col, VoidCallback? onTap) => SizedBox(
        width: 108,
        child: Card(
          margin: EdgeInsets.zero,
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onTap,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                TweenAnimationBuilder<double>(
                  tween: Tween(end: n.toDouble()),
                  duration: const Duration(milliseconds: 600),
                  curve: Curves.easeOutCubic,
                  builder: (_, v, __) => Text('${v.round()}',
                      style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800, color: n > 0 ? col : null)),
                ),
                Text(label, style: const TextStyle(fontSize: 11, color: _grey)),
              ]),
            ),
          ),
        ),
      );

  Widget _serviceCard(BuildContext c) {
    final online = _svc.isNotEmpty;
    final up = ni(_svc['uptime_seconds']);
    return Card(
      child: ExpansionTile(
        leading: Icon(Icons.circle, size: 12, color: online ? _green : _red),
        title: Text(online ? 'Security service online' : 'Security service not answering',
            style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 14)),
        subtitle: online
            ? Text('v${s(_svc['version'])} · up ${up >= 3600 ? '${(up / 3600).toStringAsFixed(1)} h' : '${(up / 60).round()} min'}',
                style: const TextStyle(fontSize: 12))
            : null,
        trailing: IconButton(tooltip: 'Change address', icon: const Icon(Icons.settings), onPressed: _setUrl),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        children: [
          for (final e in _svc.entries)
            if (e.value is! Map && e.value is! List) _kv(c, e.key, e.value)
            else _kv(c, e.key, e.value.toString()),
        ],
      ),
    );
  }

  Widget _card(BuildContext c, Map<String, dynamic> it) {
    final id = s(it['submission_id']);
    final sub = _subs[id];
    final st = s(it['status']);
    final v = statusV(st);
    final name = s(sub?['name']).isEmpty ? s(it['package_name']) : s(sub?['name']);
    final risk = s(it['risk_level']);
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => Navigator.of(c)
            .push(MaterialPageRoute(builder: (_) => SecurityDetailPage(id: id, sub: sub)))
            .then((_) => _load(silent: true)),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              netImg(s(sub?['logo']), w: 46, h: 46, r: 12),
              const SizedBox(width: 12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 15)),
                  Text('${it['package_name']} · v${it['version_name']} (${it['version_code']})',
                      maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 11.5, color: _grey)),
                ]),
              ),
              AnimatedSwitcher(
                duration: const Duration(milliseconds: 300),
                child: KeyedSubtree(key: ValueKey(st), child: pill(v.label, v.color, icon: v.icon)),
              ),
            ]),
            if (s(it['summary']).isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(s(it['summary']), maxLines: 3, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12.5)),
            ],
            const SizedBox(height: 8),
            Row(children: [
              if (risk.isNotEmpty) ...[pill('risk $risk', riskColor(risk)), const SizedBox(width: 6)],
              if (bo(sub?['isUpdateSubmission'])) ...[tagChip('UPDATE', Colors.orange)],
              const Spacer(),
              Text(agoIso(it['updated_at']), style: const TextStyle(fontSize: 11, color: _grey)),
            ]),
          ]),
        ),
      ),
    );
  }

  Widget _setupCard(BuildContext c) => ListView(padding: const EdgeInsets.all(16), children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Icon(Icons.shield_moon_rounded, size: 40, color: accent),
              const SizedBox(height: 10),
              const Text('Connect the security service', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
              const SizedBox(height: 6),
              const Text(
                  'Enter the HTTPS address of your DarkStore Security Backend. You will then see every APK check, its full report, and can approve, reject or retry.',
                  style: TextStyle(height: 1.4)),
              const SizedBox(height: 14),
              FilledButton.icon(onPressed: _setUrl, icon: const Icon(Icons.link), label: const Text('Set address')),
              const SizedBox(height: 10),
              const Text('Tip: building with --dart-define=SECURITY_API=https://… (GitHub secret SECURITY_API) sets it for everyone.',
                  style: TextStyle(fontSize: 12, color: _grey)),
            ]),
          ),
        ),
      ]);
}

/// Opens the full report of one submission (used from the Submissions tab too).
Future<void> openSecurityReport(BuildContext c, Map<String, dynamic> sub) =>
    Navigator.of(c).push(MaterialPageRoute(builder: (_) => SecurityDetailPage(id: s(sub['id']), sub: sub)));

// ───────────────────────── detail page ─────────────────────────

class SecurityDetailPage extends StatefulWidget {
  final String id;
  final Map<String, dynamic>? sub;
  const SecurityDetailPage({super.key, required this.id, this.sub});
  @override
  State<SecurityDetailPage> createState() => _SecurityDetailPageState();
}

class _SecurityDetailPageState extends State<SecurityDetailPage> {
  Map<String, dynamic>? _d;
  String? _err;
  bool _noReport = false, _loading = true, _busy = false;
  Map<String, dynamic>? _pub;
  bool _pubLoading = false;
  Timer? _t;

  Map<String, dynamic> get _sub => widget.sub ?? {};
  String get _status => s(_d?['status']);
  String get _name => s(_sub['name']).isEmpty ? s(_d?['package_name']) : s(_sub['name']);

  @override
  void initState() {
    super.initState();
    _load();
    _t = Timer.periodic(const Duration(seconds: 6), (_) {
      if (mounted && !_busy && secActive(_status)) _load(silent: true);
    });
  }

  @override
  void dispose() {
    _t?.cancel();
    super.dispose();
  }

  Future<void> _load({bool silent = false}) async {
    if (!silent && mounted) setState(() { _loading = true; _err = null; });
    try {
      final d = await SecApi.detail(widget.id);
      if (mounted) setState(() { _d = d; _noReport = false; _loading = false; _err = null; });
    } on SecError catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        if (e.status == 404) {
          _noReport = true;
        } else {
          _err = e.message;
        }
      });
    } catch (e) {
      if (mounted) setState(() { _loading = false; _err = '$e'; });
    }
  }

  Future<void> _do(String label, Future<Map<String, dynamic>> Function() f, [String detail = '']) async {
    setState(() => _busy = true);
    try {
      await f();
      if (mounted) toast(context, '$label done');
      await Db.audit('SECURITY_${label.toUpperCase().replaceAll(' ', '_')}', 'submission', widget.id, _name, detail);
    } on SecError catch (e) {
      if (mounted) toast(context, e.message);
    } catch (e) {
      if (mounted) toast(context, 'Failed: $e');
    }
    if (mounted) setState(() => _busy = false);
    await _load(silent: true);
  }

  Future<String?> _ask(String title, String hint, {int min = 0, String body = ''}) async {
    final ctl = TextEditingController();
    final r = await showDialog<String>(
      context: context,
      builder: (d) => StatefulBuilder(
        builder: (d, set) => AlertDialog(
          title: Text(title),
          content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            if (body.isNotEmpty) Padding(padding: const EdgeInsets.only(bottom: 10), child: Text(body, style: const TextStyle(fontSize: 13))),
            TextField(controller: ctl, minLines: 2, maxLines: 5, onChanged: (_) => set(() {}), decoration: InputDecoration(hintText: hint, border: const OutlineInputBorder())),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(d), child: const Text('Cancel')),
            FilledButton(
              onPressed: ctl.text.trim().length < min ? null : () => Navigator.pop(d, ctl.text.trim()),
              child: const Text('Confirm'),
            ),
          ],
        ),
      ),
    );
    ctl.dispose();
    return r;
  }

  @override
  Widget build(BuildContext c) {
    final tabs = const ['Overview', 'Checks', 'App', 'Signature', 'Permissions', 'Scan', 'Timeline'];
    return DefaultTabController(
      length: tabs.length,
      child: Scaffold(
        appBar: AppBar(
          title: Text(_name.isEmpty ? 'Security report' : _name, overflow: TextOverflow.ellipsis),
          actions: [
            IconButton(tooltip: 'Refresh', icon: const Icon(Icons.refresh), onPressed: _loading ? null : _load),
          ],
          bottom: _d == null
              ? null
              : TabBar(isScrollable: true, tabAlignment: TabAlignment.start, tabs: [for (final t in tabs) Tab(text: t)]),
        ),
        body: _body(c),
        bottomNavigationBar: _d == null ? null : _actionBar(c),
      ),
    );
  }

  Widget _body(BuildContext c) {
    if (_loading && _d == null) return const Center(child: CircularProgressIndicator());
    if (_noReport) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            const Icon(Icons.shield_outlined, size: 44, color: _grey),
            const SizedBox(height: 10),
            const Text('No security check yet', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 17)),
            const SizedBox(height: 6),
            const Text('This submission has not been scanned. Start a check now.', textAlign: TextAlign.center),
            const SizedBox(height: 14),
            FilledButton.icon(
              onPressed: _busy ? null : () => _do('Start check', () => SecApi.process(widget.id)),
              icon: const Icon(Icons.play_arrow_rounded),
              label: const Text('Start security check'),
            ),
          ]),
        ),
      );
    }
    if (_err != null && _d == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Text(_err!, textAlign: TextAlign.center),
            const SizedBox(height: 12),
            OutlinedButton(onPressed: _load, child: const Text('Try again')),
          ]),
        ),
      );
    }
    final d = _d!;
    return TabBarView(children: [
      _overview(c, d),
      _checks(c, d),
      _appTab(c, d),
      _signature(c, d),
      _permissions(c, d),
      _scan(c, d),
      _timeline(c, d),
    ]);
  }

  // ── bottom action bar ──
  Widget _actionBar(BuildContext c) {
    final st = _status;
    final btns = <Widget>[];
    Widget b(String label, IconData i, VoidCallback f, {Color? col, bool tonal = false}) {
      final style = col == null ? null : FilledButton.styleFrom(backgroundColor: col);
      final child = Row(mainAxisAlignment: MainAxisAlignment.center, children: [Icon(i, size: 18), const SizedBox(width: 6), Flexible(child: Text(label, overflow: TextOverflow.ellipsis))]);
      return Expanded(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          child: tonal
              ? FilledButton.tonal(onPressed: _busy ? null : f, child: child)
              : FilledButton(style: style, onPressed: _busy ? null : f, child: child),
        ),
      );
    }

    if (st == 'QUARANTINED') {
      btns.addAll([
        b('Approve', Icons.check_rounded, _approve, col: _green),
        b('Reject', Icons.close_rounded, _reject, col: _red),
        b('Rescan', Icons.replay_rounded, _rescan, tonal: true),
      ]);
    } else if (st == 'SCAN_FAILED') {
      btns.addAll([
        b('Retry', Icons.refresh_rounded, () => _do('Retry', () => SecApi.retry(widget.id))),
        b('Rescan', Icons.replay_rounded, _rescan, tonal: true),
        b('Reject', Icons.close_rounded, _reject, col: _red),
      ]);
    } else if (st == 'PUBLISH_FAILED') {
      btns.addAll([
        b('Retry publishing', Icons.cloud_upload_rounded, () => _do('Retry publishing', () => SecApi.retryPublishing(widget.id))),
        b('Rescan', Icons.replay_rounded, _rescan, tonal: true),
      ]);
    } else if (secActive(st)) {
      btns.add(Expanded(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
            const SizedBox(width: 10),
            Text('${statusV(st).label}… this page updates by itself', style: const TextStyle(color: _grey)),
          ]),
        ),
      ));
    } else {
      btns.add(b('Rescan', Icons.replay_rounded, _rescan, tonal: true));
    }
    return Material(
      elevation: 8,
      child: SafeArea(child: Padding(padding: const EdgeInsets.fromLTRB(8, 8, 8, 8), child: Row(children: btns))),
    );
  }

  Future<void> _approve() async {
    final note = await _ask('Approve ${_name}?', 'Note (optional)',
        body: 'The APK is published to GitHub releases and its signing certificate is trusted for this app. Store approval stays a separate step.');
    if (note == null) return;
    await _do('Approve', () => SecApi.approve(widget.id, note), note);
  }

  Future<void> _reject() async {
    final r = await _ask('Reject ${_name}?', 'Reason shown to the developer (min 3 characters)',
        min: 3, body: 'The submission is marked Rejected and the developer sees this reason.');
    if (r == null) return;
    await _do('Reject', () => SecApi.reject(widget.id, r), r);
  }

  Future<void> _rescan() async {
    if (!await confirm(context, 'Scan again?', 'Runs the whole analysis again on the stored copy of the APK.')) return;
    await _do('Rescan', () => SecApi.rescan(widget.id));
  }

  // ── Overview ──
  Widget _overview(BuildContext c, Map<String, dynamic> d) {
    final st = _status;
    final v = statusV(st);
    final vt = asMap(d['virustotal']);
    final sig = asMap(d['signature']);
    final release = asMap(d['release']);
    final storeSt = s(_sub['status']).isEmpty ? 'Pending' : s(_sub['status']);
    final storePending = storeSt.toLowerCase() == 'pending';
    final reasons = asStrings(d['reasons']);
    return ListView(padding: const EdgeInsets.all(12), children: [
      Card(
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(children: [
            netImg(s(_sub['logo']), w: 56, h: 56, r: 14),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(_name, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17)),
                Text('${d['package_name']} · v${d['version_name']} (${d['version_code']})', style: const TextStyle(fontSize: 12, color: _grey)),
                if (s(_sub['submittedBy']).isNotEmpty) Text('by ${_sub['submittedBy']}', style: const TextStyle(fontSize: 12, color: _grey)),
              ]),
            ),
          ]),
        ),
      ),
      AnimatedContainer(
        duration: const Duration(milliseconds: 400),
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          gradient: LinearGradient(colors: [v.color.withOpacity(.22), v.color.withOpacity(.07)]),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Icon(v.icon, color: v.color),
            const SizedBox(width: 8),
            Text(v.label, style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16, color: v.color)),
            const Spacer(),
            Text('updated ${agoIso(d['updated_at'])}', style: const TextStyle(fontSize: 11, color: _grey)),
          ]),
          if (s(d['summary']).isNotEmpty) ...[const SizedBox(height: 8), Text(s(d['summary']), style: const TextStyle(height: 1.35, fontSize: 13))],
          for (final r in reasons)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Icon(Icons.chevron_right_rounded, size: 18, color: v.color),
                Expanded(child: Text(r, style: const TextStyle(fontSize: 12.5, height: 1.35))),
              ]),
            ),
          if (s(d['last_error']).isNotEmpty) ...[
            const SizedBox(height: 8),
            Text('Last error: ${d['last_error']}', style: const TextStyle(fontSize: 12, color: _red)),
          ],
        ]),
      ),
      _section(c, 'At a glance', [
        Wrap(spacing: 8, runSpacing: 8, children: [
          _fact('Risk', s(d['risk_level']).isEmpty ? '-' : s(d['risk_level']), riskColor(s(d['risk_level']))),
          _fact('Permissions', s(d['permission_risk']).isEmpty ? '-' : s(d['permission_risk']), riskColor(s(d['permission_risk']))),
          _fact('Signature', sig['verified'] == true ? 'verified' : (sig['verified'] == false ? 'invalid' : '-'),
              sig['verified'] == true ? _green : (sig['verified'] == false ? _red : _grey)),
          _fact('VirusTotal', ni(vt['total_engines']) > 0 ? '${ni(vt['malicious']) + ni(vt['suspicious'])}/${vt['total_engines']}' : s(vt['status']).isEmpty ? '-' : s(vt['status']),
              (ni(vt['malicious']) + ni(vt['suspicious'])) == 0 ? _green : _red),
          _fact('APK size', bytes(d['apk_size']), _grey),
          _fact('Scans', '${ni(d['scan_count'])}', _grey),
        ]),
        const SizedBox(height: 8),
        _kv(c, 'SHA-256', d['apk_sha256'], copy: true, mono: true),
        _kv(c, 'Signer SHA-256', d['signer_sha256'], copy: true, mono: true),
        _kv(c, 'Created', dateIso(d['created_at'])),
        _kv(c, 'Completed', dateIso(d['completed_at'])),
        if (s(d['reviewed_by']).isNotEmpty) _kv(c, 'Reviewed by', d['reviewed_by']),
        if (s(d['reviewed_at']).isNotEmpty) _kv(c, 'Reviewed at', dateIso(d['reviewed_at'])),
        if (s(d['admin_note']).isNotEmpty) _kv(c, 'Admin note', d['admin_note']),
      ]),
      if (widget.sub != null)
        _section(c, 'Store submission', [
          Row(children: [
            pill(storeSt, storeSt.toLowerCase() == 'approved' ? _green : storeSt.toLowerCase() == 'rejected' ? _red : _amber),
            const SizedBox(width: 6),
            if (bo(_sub['isUpdateSubmission'])) tagChip('UPDATE', Colors.orange),
            if (bo(_sub['isUpcoming'])) tagChip('UPCOMING', Colors.purple),
            if (bo(_sub['hasAds'])) tagChip('ADS', _grey),
          ]),
          const SizedBox(height: 6),
          _kv(c, 'Submitted by', _sub['submittedBy']),
          _kv(c, 'Version', '${_sub['version']} (${_sub['versionCode']})'),
          _kv(c, 'APK link', _sub['apkUrl'], copy: true),
          if (s(_sub['feedback']).isNotEmpty) _kv(c, 'Feedback', _sub['feedback']),
          if (storePending) ...[
            const SizedBox(height: 10),
            Row(children: [
              Expanded(
                child: FilledButton(
                  style: FilledButton.styleFrom(backgroundColor: _green),
                  onPressed: () => approveSub(context, _sub, () => Navigator.of(context).maybePop()),
                  child: const Text('Approve in store'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: FilledButton(
                  style: FilledButton.styleFrom(backgroundColor: _red),
                  onPressed: () => rejectSub(context, _sub, () => Navigator.of(context).maybePop()),
                  child: const Text('Reject in store'),
                ),
              ),
            ]),
          ],
        ]),
      if (release.isNotEmpty) _release(c, release),
    ]);
  }

  Widget _release(BuildContext c, Map<String, dynamic> r) => _section(c, 'GitHub release', [
        Wrap(spacing: 6, children: [
          pill(s(r['state']).isEmpty ? 'release' : s(r['state']), s(r['state']) == 'published' ? _green : _amber),
        ]),
        const SizedBox(height: 6),
        _kv(c, 'Tag', r['tag_name'], copy: true),
        _kv(c, 'Asset', r['asset_name']),
        _kv(c, 'Size', bytes(r['asset_size'])),
        _kv(c, 'Asset SHA-256', r['asset_sha256'], copy: true, mono: true),
        _kv(c, 'Published', dateIso(r['published_at'])),
        if (s(r['error']).isNotEmpty) _kv(c, 'Error', r['error']),
        const SizedBox(height: 6),
        Wrap(spacing: 8, children: [
          if (s(r['release_url']).isNotEmpty)
            OutlinedButton.icon(onPressed: () => _open(s(r['release_url'])), icon: const Icon(Icons.open_in_new, size: 16), label: const Text('Release page')),
          if (s(r['download_url']).isNotEmpty)
            OutlinedButton.icon(onPressed: () => _copy(c, s(r['download_url']), 'Download link copied'), icon: const Icon(Icons.copy, size: 16), label: const Text('Copy download link')),
        ]),
      ]);

  Widget _fact(String k, String v, Color col) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(color: col.withOpacity(.12), borderRadius: BorderRadius.circular(12)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
          Text(k, style: const TextStyle(fontSize: 10.5, color: _grey)),
          Text(v, style: TextStyle(fontSize: 13, fontWeight: FontWeight.w800, color: col == _grey ? null : col)),
        ]),
      );

  // ── Checks ──
  Widget _checks(BuildContext c, Map<String, dynamic> d) {
    final all = asMaps(d['checks']);
    final failed = all.where((x) => x['passed'] != true).toList();
    final passed = all.where((x) => x['passed'] == true).toList();
    Widget row(Map<String, dynamic> x) {
      final ok = x['passed'] == true;
      final sev = s(x['severity']).toLowerCase();
      final col = ok ? _green : (sev.startsWith('fail') || sev == 'error' ? _red : _amber);
      return Card(
        child: ListTile(
          leading: Icon(ok ? Icons.check_circle_rounded : Icons.error_rounded, color: col),
          title: Text(s(x['code']).replaceAll('_', ' '), style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
          subtitle: Text(s(x['message']), style: const TextStyle(fontSize: 12.5)),
          trailing: ok ? null : pill(sev.isEmpty ? 'issue' : sev, col),
        ),
      );
    }

    if (all.isEmpty) return const Center(child: Text('No checks recorded yet.', style: TextStyle(color: _grey)));
    return ListView(padding: const EdgeInsets.all(12), children: [
      Text('${failed.length} need attention · ${passed.length} passed', style: const TextStyle(color: _grey, fontSize: 12)),
      const SizedBox(height: 8),
      for (final x in failed) row(x),
      if (passed.isNotEmpty)
        Card(
          child: ExpansionTile(
            title: Text('Passed checks (${passed.length})', style: const TextStyle(fontWeight: FontWeight.w700)),
            children: [for (final x in passed) row(x)],
          ),
        ),
    ]);
  }

  // ── App (manifest) ──
  Widget _appTab(BuildContext c, Map<String, dynamic> d) {
    final a = asMap(d['apk_info']);
    if (a.isEmpty) return const Center(child: Text('The APK has not been read yet.', style: TextStyle(color: _grey)));
    Widget comps(String title, Object? v) {
      final L = asMaps(v);
      if (L.isEmpty) return const SizedBox.shrink();
      final exported = L.where((x) => x['exported'] == true).toList();
      return Card(
        child: ExpansionTile(
          title: Text('$title (${L.length})', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
          subtitle: Text('${exported.length} exported', style: TextStyle(fontSize: 12, color: exported.isEmpty ? _grey : _amber)),
          children: [
            for (final x in L)
              ListTile(
                dense: true,
                title: Text(s(x['name']), style: const TextStyle(fontSize: 12, fontFamily: 'monospace')),
                subtitle: Text([
                  if (x['exported'] == true) 'exported',
                  if (x['has_intent_filter'] == true) 'intent filter',
                  if (s(x['permission']).isNotEmpty) 'needs ${x['permission']}',
                ].join(' · '), style: const TextStyle(fontSize: 11)),
              ),
          ],
        ),
      );
    }

    Widget flag(String t, bool? v, {bool badWhenTrue = false}) {
      final bad = v == true && badWhenTrue;
      return pill('$t: ${v == null ? '-' : (v ? 'yes' : 'no')}', v == null ? _grey : (bad ? _red : (v ? _green : _grey)));
    }

    final perms = asStrings(a['permissions']);
    final custom = asStrings(a['custom_permissions']);
    return ListView(padding: const EdgeInsets.all(12), children: [
      _section(c, 'Manifest', [
        _kv(c, 'Package', a['package_name'], copy: true),
        _kv(c, 'Label', a['app_label']),
        _kv(c, 'Version', '${a['version_name']} (${a['version_code']})'),
        _kv(c, 'Min / target SDK', '${a['min_sdk']} / ${a['target_sdk']}'),
        _kv(c, 'Shared user id', a['shared_user_id']),
        _kv(c, 'APK size', bytes(a['apk_size'])),
        _kv(c, 'Files in APK', a['entry_count']),
        _kv(c, 'DEX files', a['dex_files']),
        _kv(c, 'SHA-256', a['sha256'], copy: true, mono: true),
        const SizedBox(height: 8),
        Wrap(spacing: 6, runSpacing: 6, children: [
          flag('debuggable', a['debuggable'] as bool?, badWhenTrue: true),
          flag('test only', a['test_only'] as bool?, badWhenTrue: true),
          flag('backup', a['allow_backup'] as bool?),
          flag('cleartext HTTP', a['uses_cleartext_traffic'] as bool?, badWhenTrue: true),
          flag('resources.arsc', a['has_resources_arsc'] as bool?),
        ]),
      ]),
      if (asStrings(a['native_abis']).isNotEmpty || asStrings(a['native_libraries']).isNotEmpty)
        _section(c, 'Native code', [
          if (asStrings(a['native_abis']).isNotEmpty) Wrap(spacing: 6, children: [for (final x in asStrings(a['native_abis'])) tagChip(x, accent)]),
          const SizedBox(height: 6),
          for (final x in asStrings(a['native_libraries'])) Text(x, style: const TextStyle(fontSize: 11.5, fontFamily: 'monospace')),
        ]),
      Card(
        child: ExpansionTile(
          initiallyExpanded: perms.length <= 12,
          title: Text('Permissions requested (${perms.length})', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
          childrenPadding: const EdgeInsets.fromLTRB(14, 0, 14, 12),
          expandedCrossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(spacing: 6, runSpacing: 6, children: [for (final x in perms) tagChip(x.replaceFirst('android.permission.', ''), accent)]),
            if (custom.isNotEmpty) ...[
              const SizedBox(height: 10),
              const Text('Custom permissions', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 12.5)),
              const SizedBox(height: 4),
              for (final x in custom) Text(x, style: const TextStyle(fontSize: 11.5, fontFamily: 'monospace')),
            ],
          ],
        ),
      ),
      comps('Activities', a['activities']),
      comps('Services', a['services']),
      comps('Receivers', a['receivers']),
      comps('Providers', a['providers']),
    ]);
  }

  // ── Signature ──
  Widget _signature(BuildContext c, Map<String, dynamic> d) {
    final sg = asMap(d['signature']);
    if (sg.isEmpty) return const Center(child: Text('The signature has not been checked yet.', style: TextStyle(color: _grey)));
    final schemes = asMap(sg['schemes']);
    final signers = asMaps(sg['signers']);
    final errs = asStrings(sg['errors']);
    final warns = asStrings(sg['warnings']);
    return ListView(padding: const EdgeInsets.all(12), children: [
      _section(c, 'Verification', [
        Wrap(spacing: 6, runSpacing: 6, children: [
          pill(sg['verified'] == true ? 'Verified' : 'Not verified', sg['verified'] == true ? _green : _red,
              icon: sg['verified'] == true ? Icons.verified_rounded : Icons.gpp_bad_rounded),
          for (final e in schemes.entries)
            pill('${e.key.toUpperCase()} ${e.value == true ? 'signed' : (e.value == false ? 'invalid' : 'absent')}',
                e.value == true ? _green : (e.value == false ? _red : _grey)),
        ]),
        const SizedBox(height: 8),
        _kv(c, 'Verifier', sg['verifier']),
        _kv(c, 'Trusted fingerprint', d['signer_sha256'], copy: true, mono: true),
        for (final e in errs) Padding(padding: const EdgeInsets.only(top: 6), child: Text('• $e', style: const TextStyle(color: _red, fontSize: 12.5))),
        for (final w in warns) Padding(padding: const EdgeInsets.only(top: 6), child: Text('• $w', style: const TextStyle(color: _amber, fontSize: 12.5))),
      ]),
      for (var i = 0; i < signers.length; i++)
        _section(c, 'Signer certificate ${i + 1}', [
          _kv(c, 'Subject', signers[i]['subject']),
          _kv(c, 'Issuer', signers[i]['issuer']),
          _kv(c, 'Valid from', signers[i]['not_before']),
          _kv(c, 'Valid until', signers[i]['not_after']),
          _kv(c, 'Key', '${signers[i]['key_algorithm']} ${signers[i]['key_bits'] ?? ''}'),
          _kv(c, 'Signature alg.', signers[i]['signature_algorithm']),
          _kv(c, 'Serial', signers[i]['serial'], mono: true),
          _kv(c, 'SHA-256', signers[i]['sha256'], copy: true, mono: true),
          _kv(c, 'SHA-1', signers[i]['sha1'], copy: true, mono: true),
        ]),
    ]);
  }

  // ── Permissions ──
  Widget _permissions(BuildContext c, Map<String, dynamic> d) {
    final p = asMap(d['permission_analysis']);
    if (p.isEmpty) return const Center(child: Text('Permissions have not been analysed yet.', style: TextStyle(color: _grey)));
    final findings = asMaps(p['findings']);
    final combos = asStrings(p['combinations']);
    final inds = asMaps(p['indicators']);
    final rl = s(p['risk_level']);
    return ListView(padding: const EdgeInsets.all(12), children: [
      _section(c, 'Risk', [
        Row(children: [
          pill('risk $rl', riskColor(rl)),
          const SizedBox(width: 8),
          Text('score ${p['risk_score']}', style: const TextStyle(color: _grey, fontSize: 12)),
        ]),
        if (s(p['summary']).isNotEmpty) Padding(padding: const EdgeInsets.only(top: 8), child: Text(s(p['summary']), style: const TextStyle(height: 1.35, fontSize: 13))),
      ]),
      if (combos.isNotEmpty)
        _section(c, 'Risky combinations', [for (final x in combos) Padding(padding: const EdgeInsets.only(bottom: 6), child: Text('• $x', style: const TextStyle(fontSize: 12.5, height: 1.35)))]),
      if (findings.isNotEmpty)
        _section(c, 'Sensitive permissions (${findings.length})', [
          for (final f in findings)
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  pill(s(f['risk']), riskColor(s(f['risk']))),
                  const SizedBox(width: 6),
                  tagChip(s(f['category']), _grey),
                ]),
                const SizedBox(height: 3),
                Text(s(f['permission']).replaceFirst('android.permission.', ''), style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12.5, fontFamily: 'monospace')),
                Text(s(f['explanation']), style: const TextStyle(fontSize: 12.5, height: 1.35)),
              ]),
            ),
        ]),
      if (inds.isNotEmpty)
        _section(c, 'Manifest indicators', [
          for (final x in inds)
            ListTile(
              contentPadding: EdgeInsets.zero,
              dense: true,
              leading: Icon(Icons.flag_rounded, color: riskColor(s(x['risk'])), size: 20),
              title: Text(s(x['message']), style: const TextStyle(fontSize: 12.5)),
              subtitle: Text(s(x['code']), style: const TextStyle(fontSize: 11)),
            ),
        ]),
    ]);
  }

  // ── Scan (VirusTotal) ──
  Widget _scan(BuildContext c, Map<String, dynamic> d) {
    final v = asMap(d['virustotal']);
    if (v.isEmpty) return const Center(child: Text('The malware scan has not started.', style: TextStyle(color: _grey)));
    final mal = ni(v['malicious']), sus = ni(v['suspicious']), har = ni(v['harmless']), und = ni(v['undetected']);
    final other = ni(v['timeout']) + ni(v['failure']) + ni(v['type_unsupported']);
    final total = mal + sus + har + und + other;
    final dets = asMaps(v['detections']);
    final names = asStrings(v['threat_names']);
    Widget seg(int n, Color col) => n <= 0 ? const SizedBox.shrink() : Expanded(flex: n, child: Container(color: col));
    return ListView(padding: const EdgeInsets.all(12), children: [
      _section(c, 'VirusTotal', [
        Row(children: [
          pill(s(v['status']).isEmpty ? 'unknown' : s(v['status']), s(v['status']) == 'completed' ? _green : _amber),
          const SizedBox(width: 8),
          if (bo(v['from_existing_report'])) tagChip('EXISTING REPORT', accent),
          if (bo(v['uploaded'])) tagChip('UPLOADED', accent),
        ]),
        const SizedBox(height: 10),
        if (total > 0) ...[
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: SizedBox(
              height: 14,
              child: TweenAnimationBuilder<double>(
                tween: Tween(begin: 0, end: 1),
                duration: const Duration(milliseconds: 800),
                curve: Curves.easeOutCubic,
                builder: (_, t, __) => FractionallySizedBox(
                  alignment: Alignment.centerLeft,
                  widthFactor: t,
                  child: Row(children: [seg(mal, _red), seg(sus, _amber), seg(har, _green), seg(und, _grey.withOpacity(.5)), seg(other, _grey.withOpacity(.25))]),
                ),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Wrap(spacing: 10, runSpacing: 4, children: [
            _legend('malicious', mal, _red), _legend('suspicious', sus, _amber), _legend('harmless', har, _green),
            _legend('undetected', und, _grey), if (other > 0) _legend('no result', other, _grey),
          ]),
        ],
        const SizedBox(height: 8),
        _kv(c, 'Engines', v['total_engines']),
        _kv(c, 'Popular label', v['popular_threat_label']),
        _kv(c, 'Analysis date', dateIso(v['analysis_date'])),
        _kv(c, 'Analysis id', v['analysis_id'], copy: true, mono: true),
        _kv(c, 'File SHA-256', v['sha256'], copy: true, mono: true),
        if (s(v['error']).isNotEmpty) _kv(c, 'Error', v['error']),
        if (s(v['gui_url']).isNotEmpty)
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(onPressed: () => _open(s(v['gui_url'])), icon: const Icon(Icons.open_in_new, size: 16), label: const Text('Open the full VirusTotal report')),
          ),
      ]),
      if (names.isNotEmpty) _section(c, 'Threat names', [Wrap(spacing: 6, runSpacing: 6, children: [for (final x in names) tagChip(x, _red)])]),
      if (dets.isNotEmpty)
        _section(c, 'Engines that flagged the file (${dets.length})', [
          for (final x in dets)
            ListTile(
              contentPadding: EdgeInsets.zero,
              dense: true,
              leading: Icon(Icons.bug_report_rounded, color: s(x['category']) == 'malicious' ? _red : _amber, size: 20),
              title: Text(s(x['engine']), style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)),
              subtitle: Text(s(x['result']).isEmpty ? s(x['category']) : '${x['category']} · ${x['result']}', style: const TextStyle(fontSize: 12)),
            ),
        ]),
    ]);
  }

  Widget _legend(String t, int n, Color col) => Row(mainAxisSize: MainAxisSize.min, children: [
        Container(width: 9, height: 9, decoration: BoxDecoration(color: col, shape: BoxShape.circle)),
        const SizedBox(width: 4),
        Text('$n $t', style: const TextStyle(fontSize: 11.5)),
      ]);

  // ── Timeline ──
  Widget _timeline(BuildContext c, Map<String, dynamic> d) {
    final events = asMaps(d['events']);
    final jobs = asMaps(d['jobs']);
    return ListView(padding: const EdgeInsets.all(12), children: [
      _section(c, 'Publishing', [
        if (_pub == null)
          OutlinedButton.icon(
            onPressed: _pubLoading
                ? null
                : () async {
                    setState(() => _pubLoading = true);
                    try {
                      final r = await SecApi.publishing(widget.id);
                      if (mounted) setState(() => _pub = r);
                    } on SecError catch (e) {
                      if (mounted) toast(context, e.message);
                    }
                    if (mounted) setState(() => _pubLoading = false);
                  },
            icon: _pubLoading ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.cloud_done_outlined),
            label: const Text('Load publishing details'),
          )
        else ...[
          _kv(c, 'Status', _pub!['status']),
          if (s(_pub!['last_error']).isNotEmpty) _kv(c, 'Last error', _pub!['last_error']),
          if (asMap(_pub!['release']).isNotEmpty) ...[
            _kv(c, 'Tag', asMap(_pub!['release'])['tag_name'], copy: true),
            _kv(c, 'Asset', asMap(_pub!['release'])['asset_name']),
            _kv(c, 'Release state', asMap(_pub!['release'])['state']),
          ],
          for (final j in asMaps(_pub!['jobs'])) _kv(c, s(j['job_type']), '${j['status']} · ${j['attempts']} attempt(s)'),
        ],
      ]),
      if (jobs.isNotEmpty)
        _section(c, 'Jobs (${jobs.length})', [
          for (final j in jobs)
            ListTile(
              contentPadding: EdgeInsets.zero,
              dense: true,
              leading: Icon(s(j['status']) == 'done' ? Icons.check_circle : s(j['status']) == 'failed' ? Icons.error : Icons.hourglass_top, size: 20,
                  color: s(j['status']) == 'done' ? _green : s(j['status']) == 'failed' ? _red : _amber),
              title: Text('${j['job_type']} · ${j['status']}', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
              subtitle: Text('${j['attempts']} attempt(s)${s(j['last_error']).isEmpty ? '' : '\n${j['last_error']}'}', style: const TextStyle(fontSize: 11.5)),
            ),
        ]),
      _section(c, 'Events (${events.length})', [
        if (events.isEmpty) const Text('No events yet.', style: TextStyle(color: _grey)),
        for (final e in events.reversed)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Padding(padding: EdgeInsets.only(top: 3), child: Icon(Icons.circle, size: 8, color: accent)),
              const SizedBox(width: 10),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(s(e['event_type']).replaceAll('_', ' '), style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)),
                  if (s(e['message']).isNotEmpty) Text(s(e['message']), style: const TextStyle(fontSize: 12.5, height: 1.3)),
                  Text('${dateIso(e['created_at'])}${s(e['actor']).isEmpty ? '' : ' · ${e['actor']}'}', style: const TextStyle(fontSize: 11, color: _grey)),
                ]),
              ),
            ]),
          ),
      ]),
    ]);
  }
}
