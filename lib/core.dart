import 'dart:convert';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_database/firebase_database.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'uploader.dart';

const dbUrl = 'https://dark-store-6836d-default-rtdb.asia-southeast1.firebasedatabase.app';
const ownerEmail = 'davidstha900@gmail.com';
const defaultServer = 'https://darkstore.bot-keep.xyz';
const accent = Color(0xFF1F6FEB);

Map<String, dynamic> _m(Object? v) =>
    v is Map ? v.map((k, x) => MapEntry('$k', x is Map ? _m(x) : x)) : <String, dynamic>{};
int ms() => DateTime.now().millisecondsSinceEpoch;
bool bo(Object? v) => v == true || v == 'true' || v == 1;
String s(Object? v) => v?.toString() ?? '';
int ni(Object? v) => v is num ? v.toInt() : int.tryParse('$v') ?? 0;
String when(Object? t) {
  final n = ni(t);
  if (n == 0) return '-';
  final d = DateTime.fromMillisecondsSinceEpoch(n);
  String p(int x) => x.toString().padLeft(2, '0');
  return '${d.year}-${p(d.month)}-${p(d.day)} ${p(d.hour)}:${p(d.minute)}';
}

class Db {
  static DatabaseReference r(String p) => FirebaseDatabase.instance.ref(p);
  static Future<Map<String, dynamic>> map(String p) async => _m((await r(p).get()).value);
  static Future<List<Map<String, dynamic>>> list(String p) async => (await map(p))
      .entries
      .map((e) => e.value is Map ? <String, dynamic>{...e.value as Map<String, dynamic>, 'id': e.key} : <String, dynamic>{'id': e.key, 'value': e.value})
      .toList();
  static Future<void> upd(String p, Map<String, Object?> v) => r(p).update(v);
  static Future<void> put(String p, Object? v) => r(p).set(v);
  static Future<void> del(String p) => r(p).remove();
  static Future<void> push(String p, Map<String, Object?> v) => r(p).push().set(v);
  static Future<void> audit(String action, String type, String id, String name, [String d = '']) async {
    final u = FirebaseAuth.instance.currentUser;
    try {
      await push('auditLog', {'action': action, 'targetType': type, 'targetId': id, 'targetName': name, 'adminEmail': u?.email ?? '', 'adminUid': u?.uid ?? '', 'details': d, 'timestamp': ms()});
    } catch (_) {}
  }
}

/// DarkStore notifier server (push, premium, status). Needs the admin ID token.
class Api {
  static Future<String> base() async {
    try {
      final v = s((await Db.r('notifierConfig/baseUrl').get()).value).trim().replaceAll('"', '').replaceAll(RegExp(r'/+$'), '');
      final h = Uri.tryParse(v)?.host ?? '';
      if (v.startsWith('https://') && (h.endsWith('bot-keep.xyz') || h.endsWith('wisp.uno'))) return v;
    } catch (_) {}
    return defaultServer;
  }

  static Future<Map<String, dynamic>?> call(String path, {Map<String, dynamic>? body, bool auth = true, String method = 'POST'}) async {
    try {
      final h = {'Content-Type': 'application/json'};
      if (auth) h['Authorization'] = 'Bearer ${await FirebaseAuth.instance.currentUser!.getIdToken(true)}';
      final u = Uri.parse('${await base()}$path');
      final res = method == 'GET'
          ? await http.get(u, headers: h).timeout(const Duration(seconds: 12))
          : await http.post(u, headers: h, body: jsonEncode(body ?? {})).timeout(const Duration(seconds: 25));
      return _m(jsonDecode(res.body));
    } catch (_) {
      return null;
    }
  }
}

void toast(BuildContext c, String m) =>
    ScaffoldMessenger.of(c)..hideCurrentSnackBar()..showSnackBar(SnackBar(content: Text(m)));

Future<bool> confirm(BuildContext c, String title, String msg) async =>
    await showDialog<bool>(
        context: c,
        builder: (d) => AlertDialog(title: Text(title), content: Text(msg), actions: [
              TextButton(onPressed: () => Navigator.pop(d, false), child: const Text('Cancel')),
              FilledButton(onPressed: () => Navigator.pop(d, true), child: const Text('Yes')),
            ])) ??
    false;

/// Runs a write, shows result, then refreshes the tab.
Future<void> act(BuildContext c, Future<void> Function() f, VoidCallback reload, [String ok = 'Done']) async {
  try {
    await f();
    if (c.mounted) toast(c, ok);
  } catch (e) {
    if (c.mounted) toast(c, 'Failed: $e');
  }
  reload();
}

class F {
  final String k, l, t; // t: text area num bool select
  final List<String> opts;
  const F(this.k, this.l, [this.t = 'text', this.opts = const []]);
}

/// Bottom-sheet editor with a LIVE PREVIEW (the preview rebuilds on every keystroke).
Future<Map<String, dynamic>?> editSheet(BuildContext c, String title, List<F> fields, Map<String, dynamic> init,
    {Widget Function(Map<String, dynamic>)? preview}) {
  return showModalBottomSheet<Map<String, dynamic>>(
    context: c,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => _Editor(title, fields, init, preview),
  );
}

class _Editor extends StatefulWidget {
  final String title;
  final List<F> fields;
  final Map<String, dynamic> init;
  final Widget Function(Map<String, dynamic>)? preview;
  const _Editor(this.title, this.fields, this.init, this.preview);
  @override
  State<_Editor> createState() => _EditorState();
}

class _EditorState extends State<_Editor> {
  late Map<String, dynamic> v = {...widget.init};
  final _ctl = <String, TextEditingController>{};
  final _busy = <String>{};

  @override
  void dispose() {
    for (final c in _ctl.values) {
      c.dispose();
    }
    super.dispose();
  }

  TextEditingController _c(String k) => _ctl.putIfAbsent(k, () => TextEditingController(text: s(v[k])));

  List<String> _urls(String k) => s(v[k]).split(RegExp(r'[,|\n]')).map((e) => e.trim()).where((e) => e.isNotEmpty).toList();

  void _say(String m) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(m)));

  /// Pick from the phone gallery and upload; [multi] appends to a comma separated list.
  Future<void> _pick(F f, bool multi) async {
    if (_busy.contains(f.k)) return;
    setState(() => _busy.add(f.k));
    try {
      final files = multi ? await Uploader.many() : [if (await Uploader.one() case final x?) x];
      final urls = multi ? _urls(f.k) : <String>[];
      for (final x in files) {
        urls.add(await Uploader.upload(x));
      }
      if (files.isNotEmpty) {
        v[f.k] = multi ? urls.join(', ') : urls.first;
        _c(f.k).text = '${v[f.k]}';
      }
    } catch (e) {
      _say('$e');
    }
    if (mounted) setState(() => _busy.remove(f.k));
  }
  @override
  Widget build(BuildContext c) {
    return Padding(
      padding: EdgeInsets.fromLTRB(16, 12, 16, MediaQuery.of(c).viewInsets.bottom + 12),
      child: ListView(shrinkWrap: true, children: [
        Text(widget.title, style: Theme.of(c).textTheme.titleLarge),
        if (widget.preview != null) ...[
          const SizedBox(height: 10),
          const Text('PREVIEW', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
          const SizedBox(height: 6),
          widget.preview!(v),
        ],
        const SizedBox(height: 8),
        for (final f in widget.fields) _field(f),
        const SizedBox(height: 12),
        Row(children: [
          Expanded(child: OutlinedButton(onPressed: () => Navigator.pop(c), child: const Text('Cancel'))),
          const SizedBox(width: 10),
          Expanded(child: FilledButton(onPressed: () => Navigator.pop(c, v), child: const Text('Save'))),
        ]),
      ]),
    );
  }

  Widget _field(F f) {
    if (f.t == 'bool') {
      return SwitchListTile(contentPadding: EdgeInsets.zero, title: Text(f.l), value: bo(v[f.k]), onChanged: (x) => setState(() => v[f.k] = x));
    }
    if (f.t == 'image' || f.t == 'images') {
      final multi = f.t == 'images';
      final urls = multi ? _urls(f.k) : [if (s(v[f.k]).startsWith('http')) s(v[f.k])];
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          TextField(
            controller: _c(f.k),
            minLines: 1,
            maxLines: multi ? 4 : 1,
            decoration: InputDecoration(labelText: f.l, border: const OutlineInputBorder()),
            onChanged: (x) => setState(() => v[f.k] = x),
          ),
          if (urls.isNotEmpty)
            SizedBox(
              height: 78,
              child: ListView(scrollDirection: Axis.horizontal, children: [
                for (var i = 0; i < urls.length; i++)
                  Padding(
                    padding: const EdgeInsets.only(top: 8, right: 8),
                    child: Stack(clipBehavior: Clip.none, children: [
                      netImg(urls[i], w: 64, h: 64, r: 8),
                      if (multi)
                        Positioned(
                          top: -6,
                          right: -6,
                          child: GestureDetector(
                            onTap: () => setState(() {
                              final l = _urls(f.k)..removeAt(i);
                              v[f.k] = l.join(', ');
                              _c(f.k).text = '${v[f.k]}';
                            }),
                            child: const CircleAvatar(radius: 10, backgroundColor: Colors.black87, child: Icon(Icons.close, size: 12, color: Colors.white)),
                          ),
                        ),
                    ]),
                  ),
              ]),
            ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: _busy.contains(f.k) ? null : () => _pick(f, multi),
              icon: _busy.contains(f.k) ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.add_photo_alternate_outlined),
              label: Text(_busy.contains(f.k) ? 'Uploading…' : (multi ? 'Add photos from device (select many)' : 'Pick photo from device')),
            ),
          ),
        ]),
      );
    }
    if (f.t == 'date') {
      final n = ni(v[f.k]);
      return ListTile(
        contentPadding: EdgeInsets.zero,
        title: Text(f.l),
        subtitle: Text(n == 0 ? 'No limit' : when(n)),
        trailing: n == 0 ? const Icon(Icons.event) : IconButton(icon: const Icon(Icons.clear), onPressed: () => setState(() => v[f.k] = 0)),
        onTap: () async {
          final now = DateTime.now();
          final d = await showDatePicker(context: context, initialDate: n == 0 ? now : DateTime.fromMillisecondsSinceEpoch(n), firstDate: DateTime(2024), lastDate: DateTime(2040));
          if (d == null || !mounted) return;
          final tm = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(n == 0 ? now : DateTime.fromMillisecondsSinceEpoch(n)));
          if (tm == null) return;
          setState(() => v[f.k] = DateTime(d.year, d.month, d.day, tm.hour, tm.minute).millisecondsSinceEpoch);
        },
      );
    }
    if (f.t == 'select') {
      final cur = f.opts.contains(s(v[f.k])) ? s(v[f.k]) : f.opts.first;
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: DropdownButtonFormField<String>(
          value: cur,
          decoration: InputDecoration(labelText: f.l, border: const OutlineInputBorder()),
          items: [for (final o in f.opts) DropdownMenuItem(value: o, child: Text(o))],
          onChanged: (x) => setState(() => v[f.k] = x),
        ),
      );
    }
    final num = f.t == 'num';
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: TextFormField(
        initialValue: s(v[f.k]),
        minLines: f.t == 'area' ? 3 : 1,
        maxLines: f.t == 'area' ? 8 : 1,
        keyboardType: num ? TextInputType.number : null,
        decoration: InputDecoration(labelText: f.l, border: const OutlineInputBorder()),
        onChanged: (x) => setState(() => v[f.k] = num ? (int.tryParse(x) ?? 0) : x),
      ),
    );
  }
}

class FutureTab extends StatefulWidget {
  final Future<Widget> Function(BuildContext, VoidCallback) build;
  const FutureTab(this.build, {super.key});
  @override
  State<FutureTab> createState() => _FutureTabState();
}

class _FutureTabState extends State<FutureTab> {
  Future<Widget>? f;
  void reload() => setState(() => f = widget.build(context, reload));
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    f ??= widget.build(context, reload);
  }

  @override
  Widget build(BuildContext c) => FutureBuilder<Widget>(
        future: f,
        builder: (_, snap) {
          if (snap.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
          if (snap.hasError) return Center(child: Padding(padding: const EdgeInsets.all(20), child: Text('Error: ${snap.error}\n\nPull down to retry.')));
          return RefreshIndicator(onRefresh: () async => reload(), child: snap.data!);
        },
      );
}

Widget tagChip(String t, [Color? col]) => Container(
      margin: const EdgeInsets.only(right: 4),
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
      decoration: BoxDecoration(color: (col ?? Colors.grey).withOpacity(.18), borderRadius: BorderRadius.circular(99)),
      child: Text(t, style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: col ?? Colors.grey)),
    );

Widget netImg(String u, {double w = 44, double h = 44, double r = 10, BoxFit fit = BoxFit.cover}) => ClipRRect(
      borderRadius: BorderRadius.circular(r),
      child: u.isEmpty
          ? Container(width: w, height: h, color: Colors.black12)
          : Image.network(u, width: w, height: h, fit: fit, errorBuilder: (_, __, ___) => Container(width: w, height: h, color: Colors.black12, child: const Icon(Icons.broken_image, size: 16))),
    );

Widget listPage(List<Widget> children) => ListView(physics: const AlwaysScrollableScrollPhysics(), padding: const EdgeInsets.all(12), children: children);
