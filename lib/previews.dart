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
        Builder(builder: (ctx) {
          final l = shots(v['screenshots']);
          return SizedBox(
            height: 130,
            child: ListView(scrollDirection: Axis.horizontal, children: [
              for (var i = 0; i < l.length; i++)
                Padding(padding: const EdgeInsets.only(top: 8, right: 6), child: GestureDetector(onTap: () => showShotViewer(ctx, l, i), child: AdaptiveShot(l[i], height: 120))),
            ]),
          );
        }),
      if (shots(v['screenshots']).isNotEmpty) Padding(padding: const EdgeInsets.only(top: 4), child: Text('${shots(v['screenshots']).length} screenshot(s). Tap to view full size.', style: const TextStyle(fontSize: 11, color: Colors.grey))),
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

/// Keeps each screenshot's own shape: tall phone shots stay tall, landscape games stay wide.
class AdaptiveShot extends StatefulWidget {
  final String url;
  final double height;
  const AdaptiveShot(this.url, {super.key, this.height = 120});
  @override
  State<AdaptiveShot> createState() => _AdaptiveShotState();
}

class _AdaptiveShotState extends State<AdaptiveShot> {
  double _a = .56;
  @override
  void initState() {
    super.initState();
    final st = NetworkImage(widget.url).resolve(ImageConfiguration.empty);
    late ImageStreamListener l;
    l = ImageStreamListener((i, _) {
      if (mounted && i.image.height > 0) setState(() => _a = i.image.width / i.image.height);
      st.removeListener(l);
    }, onError: (_, __) => st.removeListener(l));
    st.addListener(l);
  }

  @override
  Widget build(BuildContext context) {
    final a = _a.clamp(.4, 2.4).toDouble();
    var h = widget.height, w = h * a;
    final max = MediaQuery.of(context).size.width * .8;
    if (w > max) {
      w = max;
      h = w / a;
    }
    return netImg(widget.url, w: w, h: h, r: 8);
  }
}

void showShotViewer(BuildContext c, List<String> urls, int start) {
  final pc = PageController(initialPage: start);
  showDialog<void>(
    context: c,
    builder: (d) => Dialog.fullscreen(
      backgroundColor: Colors.black,
      child: Stack(children: [
        PageView.builder(
          controller: pc,
          itemCount: urls.length,
          itemBuilder: (_, i) => InteractiveViewer(maxScale: 5, child: Center(child: Image.network(urls[i], fit: BoxFit.contain, errorBuilder: (_, __, ___) => const Icon(Icons.broken_image, color: Colors.white54, size: 48)))),
        ),
        SafeArea(child: Align(alignment: Alignment.topRight, child: IconButton(icon: const Icon(Icons.close, color: Colors.white), onPressed: () => Navigator.pop(d)))),
      ]),
    ),
  );
}
