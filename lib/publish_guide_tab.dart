import 'package:flutter/material.dart';
import 'core.dart';
import 'sb.dart';

// ─────────────────────────────────────────────────────────────────────────────
//  Publish guide: the small dismissible notice on Home ("Made an app? Publish it on DarkStore")
//  and the page behind its button (intro, VIDEO, numbered steps, "Submit my app").
//  One row in `app_config`, key `publishGuideConfig`; the store app reads it live.
// ─────────────────────────────────────────────────────────────────────────────

const _pgFields = [
  F('enabled', 'Guide on (turns the notice AND the page on or off)', 'bool'),
  F('showNotice', 'Show the small notice on Home', 'bool'),
  F('noticeEmoji', 'Notice emoji (optional)'),
  F('noticeText', 'Notice text, e.g. "Made an app? Publish it on DarkStore."'),
  F('noticeButton', 'Notice button text, e.g. "How to publish"'),
  F('title', 'Page title'),
  F('intro', 'Intro text at the top of the page', 'area'),
  F('showVideo', 'Show the video on the page', 'bool'),
  F('videoUrl', 'Video link: YouTube link or a direct .mp4 / .webm link'),
  F('videoTitle', 'Text above the video'),
  F('ctaLabel', 'Big button text (opens the submit form)'),
  F('footerNote', 'Small note under the steps (optional)', 'area'),
];

const _pgStepFields = [
  F('title', 'Step title'),
  F('body', 'Step text', 'area'),
  F('visible', 'Visible to users', 'bool'),
];

const _pgDefaults = <String, dynamic>{
  'enabled': true,
  'showNotice': true,
  'noticeEmoji': '🚀',
  'noticeText': 'Made an app? Publish it on DarkStore.',
  'noticeButton': 'How to publish',
  'title': 'Publish your own app',
  'intro': 'Share your Android app with everyone on DarkStore. It takes a few minutes, it is free, and every app is scanned for safety first.',
  'showVideo': true,
  'videoUrl': '',
  'videoTitle': 'Watch: how to submit your app',
  'ctaLabel': 'Submit my app',
  'footerNote': '',
};

/// A message to show instead of saving when a value cannot work, or null when fine.
String? _pgProblem(Map<String, dynamic> x) {
  final v = s(x['videoUrl']).trim();
  if (v.isNotEmpty) {
    final l = v.toLowerCase();
    final ok = (l.startsWith('http://') || l.startsWith('https://') || l.contains('.')) && !l.contains(' ');
    if (!ok) return 'The video link does not look right. Paste a YouTube link or a link ending in .mp4 / .webm';
  }
  if (x['showNotice'] != false && x['enabled'] != false && s(x['noticeText']).trim().isEmpty) {
    return 'Write the notice text, or turn the notice off';
  }
  return null;
}

Widget _pgPreview(Map<String, dynamic> v) {
  final on = v['enabled'] != false && v['showNotice'] != false;
  final emoji = s(v['noticeEmoji']);
  final text = s(v['noticeText']).trim().isEmpty ? s(_pgDefaults['noticeText']) : s(v['noticeText']).trim();
  final btn = s(v['noticeButton']).trim().isEmpty ? s(_pgDefaults['noticeButton']) : s(v['noticeButton']).trim();
  const accent = Color(0xFF1FA9A0);
  return Opacity(
    opacity: on ? 1 : .35,
    child: Container(
      padding: const EdgeInsets.fromLTRB(12, 8, 6, 8),
      decoration: BoxDecoration(
        color: accent.withOpacity(.10),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: accent.withOpacity(.35)),
      ),
      child: Row(children: [
        if (emoji.isNotEmpty) Padding(padding: const EdgeInsets.only(right: 10), child: Text(emoji, style: const TextStyle(fontSize: 20))),
        Expanded(child: Text(text, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600))),
        const SizedBox(width: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
          decoration: BoxDecoration(color: accent, borderRadius: BorderRadius.circular(16)),
          child: Text(btn, style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w800)),
        ),
        const Padding(padding: EdgeInsets.only(left: 6), child: Icon(Icons.close_rounded, size: 18, color: Colors.grey)),
      ]),
    ),
  );
}

Future<Widget> publishGuideTab(BuildContext c, VoidCallback rl) async {
  final m = await Db.map('publishGuideConfig');
  final shown = {..._pgDefaults, ...m};
  final raw = m['steps'];
  final steps = <Map<String, dynamic>>[
    if (raw is List)
      for (final x in raw)
        if (x is Map) Map<String, dynamic>.from(x),
  ];
  final enabled = shown['enabled'] != false;
  final noticeOn = enabled && shown['showNotice'] != false;
  final hasVideo = shown['showVideo'] != false && s(shown['videoUrl']).trim().isNotEmpty;

  Future<void> saveSteps(List<Map<String, dynamic>> list, String what) => act(c, () async {
        await Db.upd('publishGuideConfig', {'steps': list});
        await Db.audit('PUBLISH_GUIDE', 'config', 'publishGuideConfig', 'Publish guide steps', what);
      }, rl);

  Future<void> editStep(int? i) async {
    final x = await editSheet(c, i == null ? 'New step' : 'Edit step', _pgStepFields, i == null ? {'visible': true} : steps[i]);
    if (x == null || !c.mounted) return;
    if (s(x['title']).trim().isEmpty && s(x['body']).trim().isEmpty) return toast(c, 'Write a title or a text for the step');
    final row = {'title': s(x['title']).trim(), 'body': s(x['body']).trim(), 'visible': x['visible'] != false};
    final list = [...steps];
    if (i == null) {
      if (list.length >= 12) return toast(c, 'Maximum 12 steps');
      list.add(row);
    } else {
      list[i] = row;
    }
    await saveSteps(list, i == null ? 'added' : 'edited');
  }

  return listPage([
    Wrap(runSpacing: 4, children: [
      tagChip(enabled ? 'GUIDE ON' : 'GUIDE OFF', enabled ? Colors.green : Colors.orange),
      tagChip(noticeOn ? 'NOTICE SHOWN ON HOME' : 'NOTICE HIDDEN', noticeOn ? Colors.green : Colors.grey),
      tagChip(hasVideo ? 'VIDEO SET' : 'NO VIDEO', hasVideo ? Colors.green : Colors.grey),
    ]),
    const SizedBox(height: 10),
    const Text('PREVIEW (notice on Home)', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold)),
    const SizedBox(height: 6),
    _pgPreview(shown),
    const SizedBox(height: 12),
    Wrap(spacing: 8, runSpacing: 8, children: [
      FilledButton.icon(
        icon: const Icon(Icons.edit),
        label: const Text('Edit notice, video and texts'),
        onPressed: () async {
          final x = await editSheet(c, 'Publish guide', _pgFields, shown, preview: _pgPreview);
          if (x == null || !c.mounted) return;
          final bad = _pgProblem(x);
          if (bad != null) return toast(c, bad);
          final out = <String, Object?>{for (final f in _pgFields) f.k: x[f.k]};
          await act(c, () async {
            await Db.upd('publishGuideConfig', out);
            await Db.audit('PUBLISH_GUIDE', 'config', 'publishGuideConfig', 'Publish guide', enabled ? 'on' : 'off');
          }, rl);
        },
      ),
      OutlinedButton.icon(
        icon: const Icon(Icons.notifications_active_outlined),
        label: const Text('Show notice again to everyone'),
        onPressed: () async {
          if (!await confirm(c, 'Show the notice again?', 'People who closed the notice will see it on Home once more.') || !c.mounted) return;
          await act(c, () async {
            await Db.upd('publishGuideConfig', {'noticeId': 'n${DateTime.now().millisecondsSinceEpoch}'});
            await Db.audit('PUBLISH_GUIDE', 'config', 'publishGuideConfig', 'Publish guide', 'notice shown again');
          }, rl);
        },
      ),
    ]),
    const SizedBox(height: 16),
    Row(children: [
      const Expanded(child: Text('STEPS ON THE PAGE', style: TextStyle(fontSize: 11, color: Colors.grey, fontWeight: FontWeight.bold))),
      TextButton.icon(icon: const Icon(Icons.add), label: const Text('Add step'), onPressed: () => editStep(null)),
    ]),
    if (steps.isEmpty)
      const Padding(
        padding: EdgeInsets.all(12),
        child: Text('No steps of your own yet, so people see the built-in five: Sign in, Get your app ready, Tap "Submit my app", Safety scan and review, You are live. Add steps here to replace them.',
            style: TextStyle(color: Colors.grey, height: 1.4, fontSize: 12)),
      ),
    for (var i = 0; i < steps.length; i++)
      Card(
        child: ListTile(
          leading: CircleAvatar(radius: 14, child: Text('${i + 1}', style: const TextStyle(fontSize: 12))),
          title: Text(s(steps[i]['title']).isEmpty ? '(no title)' : s(steps[i]['title']), maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: Text(s(steps[i]['body']), maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12)),
          onTap: () => editStep(i),
          trailing: PopupMenuButton<String>(
            onSelected: (v) async {
              final list = [...steps];
              if (v == 'up' && i > 0) {
                final t = list.removeAt(i);
                list.insert(i - 1, t);
                await saveSteps(list, 'moved');
              } else if (v == 'down' && i < list.length - 1) {
                final t = list.removeAt(i);
                list.insert(i + 1, t);
                await saveSteps(list, 'moved');
              } else if (v == 'hide') {
                list[i] = {...list[i], 'visible': list[i]['visible'] == false};
                await saveSteps(list, 'visibility');
              } else if (v == 'del') {
                if (!await confirm(c, 'Delete this step?', s(steps[i]['title'])) || !c.mounted) return;
                list.removeAt(i);
                await saveSteps(list, 'deleted');
              }
            },
            itemBuilder: (_) => [
              if (i > 0) const PopupMenuItem(value: 'up', child: Text('Move up')),
              if (i < steps.length - 1) const PopupMenuItem(value: 'down', child: Text('Move down')),
              PopupMenuItem(value: 'hide', child: Text(steps[i]['visible'] == false ? 'Show to users' : 'Hide from users')),
              const PopupMenuItem(value: 'del', child: Text('Delete')),
            ],
          ),
        ),
      ),
    const SizedBox(height: 12),
    const Text(
      'The notice is a small card on Home with a button and a close (x). A person who closes it does not see it again '
      'until you press "Show notice again to everyone" or change the notice text. The button opens the guide page: '
      'intro, your video, the steps, and a big button into the real submit form. '
      'Video: paste a YouTube link, or a direct .mp4 / .webm link; turn "Show the video" off to hide it without losing the link.',
      style: TextStyle(fontSize: 12, color: Colors.grey, height: 1.4),
    ),
  ]);
}
