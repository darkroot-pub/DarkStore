import 'package:flutter/material.dart';
import 'core.dart';

List<String> shots(Object? v) => v is List
    ? v.map((e) => '$e').where((e) => e.isNotEmpty).toList()
    : s(v).split(RegExp(r'[,|\n]')).map((e) => e.trim()).where((e) => e.isNotEmpty).toList();

Color hex(String h, [Color d = accent]) {
  final x = h.replaceAll('#', '');
  final n = int.tryParse(x.length == 6 ? 'FF$x' : x, radix: 16);
  return n == null ? d : Color(n);
}

Widget _frame(Widget child) => Container(
      width: double.infinity,
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(14), border: Border.all(color: Colors.black12), color: Colors.black.withOpacity(.03)),
      padding: const EdgeInsets.all(10),
      child: child,
    );

Widget bannerPrev(Map<String, dynamic> v) => _frame(AspectRatio(
      aspectRatio: 16 / 7,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: Stack(fit: StackFit.expand, children: [
          netImg(s(v['imageUrl']), w: double.infinity, h: double.infinity, r: 0),
          Container(decoration: const BoxDecoration(gradient: LinearGradient(begin: Alignment.bottomCenter, end: Alignment.topCenter, colors: [Colors.black87, Colors.transparent]))),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Column(mainAxisAlignment: MainAxisAlignment.end, crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(s(v['title']), style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 18)),
              Text(s(v['subtitle']), style: const TextStyle(color: Colors.white70, fontSize: 12)),
              if (s(v['buttonText']).isNotEmpty)
                Container(margin: const EdgeInsets.only(top: 6), padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5), decoration: BoxDecoration(color: accent, borderRadius: BorderRadius.circular(99)), child: Text(s(v['buttonText']), style: const TextStyle(color: Colors.white, fontSize: 12))),
            ]),
          ),
        ]),
      ),
    ));

/// Launch event popup exactly as users will see it.
Widget eventPrev(Map<String, dynamic> v) {
  final t = s(v['mediaType']);
  return _frame(Card(
    margin: EdgeInsets.zero,
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
    clipBehavior: Clip.antiAlias,
    child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
      if (t == 'image') netImg(s(v['mediaUrl']), w: double.infinity, h: 150, r: 0),
      if (t == 'video')
        Container(height: 150, color: Colors.black, alignment: Alignment.center, child: const Icon(Icons.play_circle_fill, color: Colors.white, size: 54)),
      Padding(
        padding: const EdgeInsets.all(14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(s(v['title']).isEmpty ? 'Event title' : s(v['title']), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          Text(s(v['message'])),
          const SizedBox(height: 10),
          Row(children: [
            if (s(v['targetType']) != 'none') Expanded(child: FilledButton(onPressed: () {}, child: Text(s(v['buttonText']).isEmpty ? 'Open' : s(v['buttonText'])))),
            if (s(v['targetType']) != 'none') const SizedBox(width: 8),
            OutlinedButton(onPressed: () {}, child: const Text('Close')),
          ]),
        ]),
      ),
    ]),
  ));
}

Widget noticePrev(Map<String, dynamic> v) => _frame(Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(14), boxShadow: const [BoxShadow(blurRadius: 8, color: Colors.black12)]),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const CircleAvatar(radius: 14, backgroundColor: accent, child: Icon(Icons.store, size: 16, color: Colors.white)),
        const SizedBox(width: 10),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(s(v['title']).isEmpty ? 'Title' : s(v['title']), style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.black)),
            Text(s(v['message']).isEmpty ? 'Message' : s(v['message']), style: const TextStyle(color: Colors.black87, fontSize: 13)),
            if (s(v['imageUrl']).isNotEmpty) Padding(padding: const EdgeInsets.only(top: 8), child: netImg(s(v['imageUrl']), w: double.infinity, h: 110)),
          ]),
        ),
      ]),
    ));

Widget appPrev(Map<String, dynamic> v) => _frame(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [
        netImg(s(v['logo']), w: 64, h: 64, r: 16),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(s(v['name']), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
            Text('${s(v['developer'])} · ${s(v['category'])}', style: const TextStyle(color: Colors.grey, fontSize: 12)),
            Text('v${s(v['version'])}  ${s(v['size'])}', style: const TextStyle(fontSize: 12)),
          ]),
        ),
        FilledButton(onPressed: () {}, child: const Text('Install')),
      ]),
      const SizedBox(height: 8),
      Text(s(v['description']), maxLines: 4, overflow: TextOverflow.ellipsis),
      if (shots(v['screenshots']).isNotEmpty)
        SizedBox(height: 120, child: ListView(scrollDirection: Axis.horizontal, children: [for (final u in shots(v['screenshots'])) Padding(padding: const EdgeInsets.only(top: 8, right: 6), child: netImg(u, w: 70, h: 112, r: 8))])),
      if (s(v['changelog']).isNotEmpty) Padding(padding: const EdgeInsets.only(top: 8), child: Text("What's new: ${s(v['changelog'])}", style: const TextStyle(fontSize: 12))),
    ]));

Widget collPrev(Map<String, dynamic> v) {
  final c = hex(s(v['color']), const Color(0xFF10B981));
  return _frame(Container(
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(borderRadius: BorderRadius.circular(14), gradient: LinearGradient(colors: [c, c.withOpacity(.6)])),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(s(v['title']), style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 18)),
      Text(s(v['subtitle']), style: const TextStyle(color: Colors.white70)),
      const SizedBox(height: 6),
      Text('${s(v['appIdsText']).split(',').where((e) => e.trim().isNotEmpty).length} apps', style: const TextStyle(color: Colors.white, fontSize: 12)),
    ]),
  ));
}

Widget updatePrev(Map<String, dynamic> v) => _frame(Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const Icon(Icons.system_update_rounded, size: 40, color: accent),
          const SizedBox(height: 8),
          Text(s(v['updateTitle']).isEmpty ? 'Update available' : s(v['updateTitle']), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
          Text(s(v['updateMessage']).isEmpty ? 'A new version (${s(v['latestVersionName'])}) is available.' : s(v['updateMessage']), textAlign: TextAlign.center),
          const SizedBox(height: 10),
          Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            if (!bo(v['forceUpdate'])) TextButton(onPressed: () {}, child: const Text('Later')),
            FilledButton(onPressed: () {}, child: const Text('Update now')),
          ]),
        ]),
      ),
    ));
