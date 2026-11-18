import 'dart:async';
import 'package:flutter/material.dart';
import 'core.dart';
import 'sb.dart';
import 'security_api.dart' show asMaps;
import 'shell.dart';

// ───────────── Team & roles (administrators only) ─────────────
// Roles live in `staff_roles`; a person gets one role through `users.staff_role`.
// The database enforces what each role may read or change; this screen only manages them.

const _roleColors = ['#1F6FEB', '#0EA5A5', '#16A34A', '#F59E0B', '#F97316', '#EF4444', '#A855F7', '#64748B'];

Future<Widget> teamTab(BuildContext c, VoidCallback rl) async {
  final r = await Future.wait([
    Sb.c.from('staff_roles').select().order('label'),
    Sb.c.rpc('staff_list'),
  ]);
  final roles = asMaps(r[0]);
  final staff = asMaps(r[1]);
  final cs = Theme.of(c).colorScheme;
  final roleByKey = {for (final x in roles) s(x['key']): x};
  int members(String k) => staff.where((x) => s(x['staff_role']) == k).length;

  Widget heading(String t, String sub, {Widget? trailing}) => Padding(
        padding: const EdgeInsets.fromLTRB(2, 18, 2, 8),
        child: Row(children: [
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(t, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
              Text(sub, style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)),
            ]),
          ),
          if (trailing != null) trailing,
        ]),
      );

  return listPage([
    Card(
      color: cs.primaryContainer.withOpacity(.4),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(Icons.info_outline_rounded, color: cs.primary),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'Give a trusted person a role so they can help run the store without full admin power. '
              'The person must have opened the DarkStore app and signed in once. '
              'Only administrators can manage this team, send notifications, push app updates, or change the server and backups.',
              style: TextStyle(fontSize: 12.5, height: 1.4, color: cs.onSurface),
            ),
          ),
        ]),
      ),
    ),
    heading('Team members', '${staff.length} ${staff.length == 1 ? 'person' : 'people'} with panel access',
        trailing: FilledButton.icon(onPressed: () => _addMember(c, roles, rl), icon: const Icon(Icons.person_add_alt_1_rounded, size: 18), label: const Text('Add'))),
    if (staff.isEmpty)
      const Card(child: Padding(padding: EdgeInsets.all(18), child: Text('No one yet. Tap Add to give someone a role.')))
    else
      for (final p in staff) _memberCard(c, p, roleByKey[s(p['staff_role'])], roles, rl),
    heading('Roles', 'What each role can open and change',
        trailing: OutlinedButton.icon(onPressed: () => _editRole(c, null, rl), icon: const Icon(Icons.add_rounded, size: 18), label: const Text('New role'))),
    for (final x in roles) _roleCard(c, x, members(s(x['key'])), rl),
    const SizedBox(height: 28),
  ]);
}

Widget _avatar(Map<String, dynamic> p, Color col) {
  final photo = s(p['photo']);
  final name = s(p['name']);
  return CircleAvatar(
    radius: 21,
    backgroundColor: col.withOpacity(.16),
    backgroundImage: photo.startsWith('http') ? NetworkImage(photo) : null,
    child: photo.startsWith('http') ? null : Text(name.isEmpty ? '?' : name[0].toUpperCase(), style: TextStyle(color: col, fontWeight: FontWeight.w800)),
  );
}

Widget _memberCard(BuildContext c, Map<String, dynamic> p, Map<String, dynamic>? role, List<Map<String, dynamic>> roles, VoidCallback rl) {
  final isAdmin = bo(p['admin']);
  final col = isAdmin ? const Color(0xFFEF4444) : hexColor(s(role?['color']), Colors.teal);
  final label = isAdmin ? 'Administrator' : (s(role?['label']).isEmpty ? s(p['staff_role']) : s(role?['label']));
  return Card(
    child: Padding(
      padding: const EdgeInsets.fromLTRB(12, 10, 4, 10),
      child: Row(children: [
        _avatar(p, col),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(s(p['name']), maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700)),
            Text(s(p['email']), maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 6),
            Wrap(spacing: 6, runSpacing: 4, children: [
              RoleBadge(label, col),
              if (bo(p['suspended'])) const RoleBadge('Suspended', Colors.red),
            ]),
          ]),
        ),
        if (isAdmin)
          const Padding(padding: EdgeInsets.only(right: 12), child: Icon(Icons.lock_outline_rounded, size: 18, color: Colors.grey))
        else
          PopupMenuButton<String>(
            tooltip: 'Options',
            onSelected: (v) async {
              if (v == 'change') {
                final k = await _pickRole(c, roles, s(p['staff_role']));
                if (k == null || !c.mounted) return;
                await act(c, () async => Sb.c.rpc('staff_set_role', params: {'p_uid': s(p['uid']), 'p_role': k}), rl, 'Role changed');
              } else if (v == 'remove') {
                if (!await confirm(c, 'Remove access?', '${s(p['name'])} will no longer be able to use the admin panel.')) return;
                if (!c.mounted) return;
                await act(c, () async => Sb.c.rpc('staff_set_role', params: {'p_uid': s(p['uid']), 'p_role': ''}), rl, 'Access removed');
              }
            },
            itemBuilder: (_) => const [
              PopupMenuItem(value: 'change', child: ListTile(dense: true, leading: Icon(Icons.swap_horiz_rounded), title: Text('Change role'))),
              PopupMenuItem(value: 'remove', child: ListTile(dense: true, leading: Icon(Icons.person_remove_rounded), title: Text('Remove access'))),
            ],
          ),
      ]),
    ),
  );
}

Widget _roleCard(BuildContext c, Map<String, dynamic> x, int count, VoidCallback rl) {
  final col = hexColor(s(x['color']), Colors.teal);
  final perms = <String>{for (final p in (x['perms'] is List ? x['perms'] as List : const [])) '$p'};
  final builtin = bo(x['builtin']);
  return Card(
    child: Padding(
      padding: const EdgeInsets.fromLTRB(14, 12, 6, 12),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(width: 12, height: 12, decoration: BoxDecoration(color: col, shape: BoxShape.circle)),
          const SizedBox(width: 10),
          Expanded(child: Text(s(x['label']), style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800))),
          Text('$count ${count == 1 ? 'person' : 'people'}', style: const TextStyle(fontSize: 12, color: Colors.grey)),
          IconButton(tooltip: 'Edit role', icon: const Icon(Icons.edit_rounded, size: 20), onPressed: () => _editRole(c, x, rl)),
          if (!builtin)
            IconButton(
              tooltip: 'Delete role',
              icon: const Icon(Icons.delete_outline_rounded, size: 20),
              onPressed: () async {
                if (!await confirm(c, 'Delete role?', 'People with "${s(x['label'])}" lose their panel access.')) return;
                if (!c.mounted) return;
                await act(c, () async {
                  await Sb.c.from('staff_roles').delete().eq('key', s(x['key']));
                  await Db.audit('STAFF_ROLE_DELETE', 'role', s(x['key']), s(x['label']));
                }, rl, 'Role deleted');
              },
            ),
        ]),
        if (s(x['description']).isNotEmpty)
          Padding(padding: const EdgeInsets.only(right: 8, top: 2), child: Text(s(x['description']), style: const TextStyle(fontSize: 12.5, color: Colors.grey, height: 1.35))),
        const SizedBox(height: 10),
        Wrap(runSpacing: 4, children: [
          for (final a in kAreas)
            if (perms.contains('${a.$1}:w'))
              tagChip('${a.$2} · edit', col)
            else if (perms.contains(a.$1))
              tagChip('${a.$2} · view'),
          if (perms.isEmpty) const Text('No access yet', style: TextStyle(fontSize: 12, color: Colors.grey)),
        ]),
      ]),
    ),
  );
}

Future<String?> _pickRole(BuildContext c, List<Map<String, dynamic>> roles, String current) => showDialog<String>(
      context: c,
      builder: (d) => SimpleDialog(
        title: const Text('Choose a role'),
        children: [
          for (final x in roles)
            SimpleDialogOption(
              onPressed: () => Navigator.pop(d, s(x['key'])),
              child: Row(children: [
                Container(width: 12, height: 12, decoration: BoxDecoration(color: hexColor(s(x['color']), Colors.teal), shape: BoxShape.circle)),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(s(x['label']), style: TextStyle(fontWeight: FontWeight.w700, decoration: s(x['key']) == current ? TextDecoration.underline : null)),
                    if (s(x['description']).isNotEmpty) Text(s(x['description']), maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, color: Colors.grey)),
                  ]),
                ),
              ]),
            ),
        ],
      ),
    );

// ───────────── Add a person ─────────────

Future<void> _addMember(BuildContext c, List<Map<String, dynamic>> roles, VoidCallback rl) async {
  final picked = await showDialog<Map<String, dynamic>>(context: c, builder: (_) => const _FindPerson());
  if (picked == null || !c.mounted) return;
  final key = await _pickRole(c, roles, '');
  if (key == null || !c.mounted) return;
  await act(c, () async => Sb.c.rpc('staff_set_role', params: {'p_uid': s(picked['uid']), 'p_role': key}), rl, '${s(picked['name'])} added to the team');
}

class _FindPerson extends StatefulWidget {
  const _FindPerson();
  @override
  State<_FindPerson> createState() => _FindPersonState();
}

class _FindPersonState extends State<_FindPerson> {
  final ctl = TextEditingController();
  Timer? _t;
  List<Map<String, dynamic>> found = [];
  bool busy = false, searched = false;
  String err = '';

  @override
  void dispose() {
    _t?.cancel();
    ctl.dispose();
    super.dispose();
  }

  void onChanged(String v) {
    _t?.cancel();
    if (v.trim().length < 3) {
      setState(() { found = []; searched = false; err = ''; });
      return;
    }
    _t = Timer(const Duration(milliseconds: 350), () => search(v.trim()));
  }

  Future<void> search(String q) async {
    setState(() { busy = true; err = ''; });
    try {
      final r = await Sb.c.rpc('staff_find', params: {'p_q': q});
      if (!mounted) return;
      setState(() { found = asMaps(r); searched = true; });
    } catch (e) {
      if (mounted) setState(() => err = 'Search failed: $e');
    }
    if (mounted) setState(() => busy = false);
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: const Text('Add a team member'),
        content: SizedBox(
          width: 420,
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            TextField(
              controller: ctl,
              autofocus: true,
              keyboardType: TextInputType.emailAddress,
              decoration: InputDecoration(prefixIcon: const Icon(Icons.search_rounded), hintText: 'Search by e-mail or username', suffixIcon: busy ? const Padding(padding: EdgeInsets.all(12), child: SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))) : null),
              onChanged: onChanged,
            ),
            const SizedBox(height: 8),
            if (err.isNotEmpty) Text(err, style: const TextStyle(color: Colors.red, fontSize: 12)),
            if (ctl.text.trim().length < 3)
              const Text('Type at least 3 letters. The person must have signed into the DarkStore app once.', style: TextStyle(fontSize: 12, color: Colors.grey))
            else if (searched && found.isEmpty && !busy)
              const Text('No one found. Ask them to open the DarkStore app and sign in first.', style: TextStyle(fontSize: 12, color: Colors.grey)),
            Flexible(
              child: ListView(shrinkWrap: true, children: [
                for (final p in found)
                  ListTile(
                    leading: _avatar(p, accent),
                    title: Text(s(p['name']), maxLines: 1, overflow: TextOverflow.ellipsis),
                    subtitle: Text(bo(p['admin']) ? '${s(p['email'])} · already an administrator' : (s(p['staff_role']).isNotEmpty ? '${s(p['email'])} · has role: ${s(p['staff_role'])}' : s(p['email'])), maxLines: 1, overflow: TextOverflow.ellipsis),
                    enabled: !bo(p['admin']),
                    onTap: () => Navigator.pop(context, p),
                  ),
              ]),
            ),
          ]),
        ),
        actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel'))],
      );
}

// ───────────── Create / edit a role ─────────────

Future<void> _editRole(BuildContext c, Map<String, dynamic>? role, VoidCallback rl) async {
  final res = await showModalBottomSheet<Map<String, dynamic>>(
    context: c,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => _RoleEditor(role),
  );
  if (res == null || !c.mounted) return;
  await act(c, () async {
    await Sb.c.from('staff_roles').upsert(res);
    await Db.audit(role == null ? 'STAFF_ROLE_CREATE' : 'STAFF_ROLE_EDIT', 'role', s(res['key']), s(res['label']), (res['perms'] as List).join(', '));
  }, rl, 'Role saved');
}

class _RoleEditor extends StatefulWidget {
  final Map<String, dynamic>? role;
  const _RoleEditor(this.role);
  @override
  State<_RoleEditor> createState() => _RoleEditorState();
}

class _RoleEditorState extends State<_RoleEditor> {
  late final label = TextEditingController(text: s(widget.role?['label']));
  late final desc = TextEditingController(text: s(widget.role?['description']));
  late String color = s(widget.role?['color']).isEmpty ? _roleColors.first : s(widget.role?['color']);
  late final Map<String, int> lvl = {
    for (final a in kAreas) a.$1: 0,
  };
  String err = '';

  @override
  void initState() {
    super.initState();
    final perms = <String>{for (final p in (widget.role?['perms'] is List ? widget.role!['perms'] as List : const [])) '$p'};
    for (final a in kAreas) {
      lvl[a.$1] = perms.contains('${a.$1}:w') ? 2 : (perms.contains(a.$1) ? 1 : 0);
    }
  }

  @override
  void dispose() {
    label.dispose();
    desc.dispose();
    super.dispose();
  }

  void setAll(int v) => setState(() {
        for (final a in kAreas) {
          lvl[a.$1] = v;
        }
      });

  static String slug(String t) => t.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]+'), '_').replaceAll(RegExp(r'^_+|_+$'), '');

  void save() {
    final name = label.text.trim();
    if (name.isEmpty) {
      setState(() => err = 'Give the role a name.');
      return;
    }
    final key = widget.role != null ? s(widget.role!['key']) : slug(name);
    if (key.length < 2 || key.length > 24 || key == 'admin') {
      setState(() => err = 'Choose a different name (2–24 letters or numbers, not "admin").');
      return;
    }
    final perms = <String>[
      for (final a in kAreas)
        if (lvl[a.$1] == 2) '${a.$1}:w' else if (lvl[a.$1] == 1) a.$1,
    ];
    if (perms.isEmpty) {
      setState(() => err = 'Allow at least one area, otherwise this role can open nothing.');
      return;
    }
    Navigator.pop(context, {
      'key': key,
      'label': name,
      'description': desc.text.trim(),
      'color': color,
      'perms': perms,
    });
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final pad = MediaQuery.viewInsetsOf(context).bottom;
    return Padding(
      padding: EdgeInsets.only(bottom: pad),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(18, 4, 18, 20),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(widget.role == null ? 'New role' : 'Edit role', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
          const SizedBox(height: 14),
          TextField(controller: label, textCapitalization: TextCapitalization.words, decoration: const InputDecoration(labelText: 'Role name', hintText: 'e.g. Community moderator')),
          const SizedBox(height: 10),
          TextField(controller: desc, maxLines: 2, decoration: const InputDecoration(labelText: 'What is this role for?')),
          const SizedBox(height: 14),
          const Text('Colour', style: TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 8),
          Wrap(spacing: 10, runSpacing: 10, children: [
            for (final h in _roleColors)
              InkWell(
                customBorder: const CircleBorder(),
                onTap: () => setState(() => color = h),
                child: Container(
                  width: 34, height: 34,
                  decoration: BoxDecoration(color: hexColor(h), shape: BoxShape.circle, border: Border.all(color: color.toLowerCase() == h.toLowerCase() ? cs.onSurface : Colors.transparent, width: 2.5)),
                  child: color.toLowerCase() == h.toLowerCase() ? const Icon(Icons.check_rounded, size: 18, color: Colors.white) : null,
                ),
              ),
          ]),
          const SizedBox(height: 18),
          Row(children: [
            const Expanded(child: Text('What this role can do', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15))),
            TextButton(onPressed: () => setAll(1), child: const Text('View all')),
            TextButton(onPressed: () => setAll(0), child: const Text('Clear')),
          ]),
          const Text('None = hidden · View = can look · Edit = can look and change', style: TextStyle(fontSize: 12, color: Colors.grey)),
          const SizedBox(height: 8),
          for (final a in kAreas)
            Card(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [
                    Icon(a.$4, size: 20, color: cs.primary),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(a.$2, style: const TextStyle(fontWeight: FontWeight.w700)),
                        Text(a.$3, style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)),
                      ]),
                    ),
                  ]),
                  const SizedBox(height: 8),
                  SizedBox(
                    width: double.infinity,
                    child: SegmentedButton<int>(
                      showSelectedIcon: false,
                      style: const ButtonStyle(visualDensity: VisualDensity.compact),
                      segments: const [
                        ButtonSegment(value: 0, label: Text('None')),
                        ButtonSegment(value: 1, label: Text('View')),
                        ButtonSegment(value: 2, label: Text('Edit')),
                      ],
                      selected: {lvl[a.$1] ?? 0},
                      onSelectionChanged: (v) => setState(() => lvl[a.$1] = v.first),
                    ),
                  ),
                ]),
              ),
            ),
          if (err.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 8), child: Text(err, style: const TextStyle(color: Colors.red))),
          const SizedBox(height: 14),
          Row(children: [
            Expanded(child: OutlinedButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel'))),
            const SizedBox(width: 12),
            Expanded(child: FilledButton(onPressed: save, child: const Text('Save role'))),
          ]),
        ]),
      ),
    );
  }
}
