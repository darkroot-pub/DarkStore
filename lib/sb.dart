import 'dart:convert';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

/// DarkStore data lives in Supabase (Postgres). LOGIN stays on Firebase Auth:
/// every request carries the admin's Firebase ID token and the database
/// identifies the admin by its `sub` (= Firebase uid) + `users.role = 'admin'`.
class Sb {
  Sb._();
  static const _url = String.fromEnvironment('SUPABASE_URL');
  static const _key = String.fromEnvironment('SUPABASE_ANON_KEY');
  static bool _ready = false;

  static Future<void> init() async {
    if (_ready) return;
    if (_url.isEmpty || _key.isEmpty) {
      throw StateError('SUPABASE_URL / SUPABASE_ANON_KEY missing (add the two GitHub secrets).');
    }
    await Supabase.initialize(url: _url, anonKey: _key, accessToken: _token);
    _ready = true;
  }

  static SupabaseClient get c => Supabase.instance.client;

  static bool _hasRole(String? jwt) {
    try {
      final p = jwt!.split('.');
      final j = utf8.decode(base64Url.decode(base64Url.normalize(p[1])));
      return (jsonDecode(j) as Map)['role'] == 'authenticated';
    } catch (_) {
      return false;
    }
  }

  static Future<String?> _token() async {
    final u = FirebaseAuth.instance.currentUser;
    if (u == null) return null;
    try {
      var t = await u.getIdToken();
      if (!_hasRole(t)) t = await u.getIdToken(true);
      return _hasRole(t) ? t : null;
    } catch (_) {
      return null;
    }
  }
}

// ───────────────────────── field specs: Firebase-style name <-> column ─────────
// t: s text, b bool, i int, f number, ts epoch-ms <-> timestamptz,
//    arr comma text <-> text[], j JSON text <-> jsonb
class _C {
  final String col, t;
  const _C(this.col, [this.t = 's']);
}

class _T {
  final String table, pk;
  final String? view; // read from a view (apps_public carries the live developer name)
  final Map<String, _C> f;
  const _T(this.table, this.pk, this.f, {this.view});
}

const _apps = _T('apps', 'id', {
  'name': _C('name'), 'packageName': _C('package_name'), 'version': _C('version'), 'versionCode': _C('version_code', 'i'),
  'description': _C('description'), 'category': _C('category'), 'changelog': _C('changelog'), 'apkUrl': _C('apk_url'),
  'logo': _C('logo'), 'screenshots': _C('screenshots', 'arr'), 'videoUrl': _C('video_url'), 'expectedRelease': _C('expected_release'),
  'size': _C('size'), 'price': _C('price'), 'rating': _C('rating', 'f'), 'reportsJson': _C('reports_json'),
  'versionHistoryJson': _C('version_history', 'j'), 'submittedBy': _C('submitted_by'), 'ownerUid': _C('owner_uid'),
  'developer': _C('developer'), 'hasAds': _C('has_ads', 'b'), 'isApproved': _C('is_approved', 'b'), 'isFeatured': _C('is_featured', 'b'),
  'isPopular': _C('is_popular', 'b'), 'isPremium': _C('is_premium', 'b'), 'isRecent': _C('is_recent', 'b'),
  'isUpcoming': _C('is_upcoming', 'b'), 'isSuspended': _C('is_suspended', 'b'), 'suspensionReason': _C('suspension_reason'),
  'isOnHold': _C('is_on_hold', 'b'), 'holdReason': _C('hold_reason'), 'holdAt': _C('hold_at', 'ts'), 'createdAt': _C('created_at', 'ts'),
}, view: 'apps_public');

const _users = _T('users', 'uid', {
  'email': _C('email'), 'userName': _C('user_name'), 'displayName': _C('display_name'), 'devName': _C('dev_name'),
  'devBio': _C('dev_bio'), 'devLocation': _C('dev_location'), 'devGithub': _C('dev_github'), 'devWebsite': _C('dev_website'),
  'profilePhotoUrl': _C('profile_photo_url'), 'role': _C('role'), 'isDeveloper': _C('is_developer', 'b'),
  'isPremiumMember': _C('is_premium_member', 'b'), 'isEmailVerified': _C('is_email_verified', 'b'),
  'isSuspended': _C('is_suspended', 'b'), 'suspensionReason': _C('suspension_reason'), 'createdAt': _C('created_at', 'ts'),
  'guardExemptUntil': _C('guard_exempt_until', 'ts'),
});

const _subs = _T('submissions', 'id', {
  'name': _C('name'), 'packageName': _C('package_name'), 'version': _C('version'), 'versionCode': _C('version_code', 'i'),
  'description': _C('description'), 'category': _C('category'), 'changelog': _C('changelog'), 'apkUrl': _C('apk_url'),
  'logo': _C('logo'), 'screenshots': _C('screenshots', 'arr'), 'videoUrl': _C('video_url'), 'expectedRelease': _C('expected_release'),
  'hasAds': _C('has_ads', 'b'), 'isUpcoming': _C('is_upcoming', 'b'), 'isUpdateSubmission': _C('is_update_submission', 'b'),
  'status': _C('status'), 'feedback': _C('feedback'), 'submittedBy': _C('submitted_by'), 'submittedByUid': _C('submitted_by_uid'),
  'developer': _C('developer'), 'createdAt': _C('created_at', 'ts'),
});

const _reports = _T('darkstore_reports', 'id', {
  'category': _C('category'), 'message': _C('message'), 'reporterUid': _C('reporter_uid'), 'reporterEmail': _C('reporter_email'),
  'reporterName': _C('reporter_name'), 'appVersion': _C('app_version'), 'device': _C('device'), 'createdAt': _C('created_at', 'ts'),
});

const _alerts = _T('fraud_alerts', 'id', {
  'type': _C('type'), 'severity': _C('severity'), 'status': _C('status'), 'autoAction': _C('auto_action'), 'uid': _C('uid'),
  'relatedUid': _C('related_uid'), 'email': _C('email'), 'name': _C('name'), 'details': _C('details'),
  'reviewedBy': _C('reviewed_by'), 'reviewedAt': _C('reviewed_at', 'ts'), 'createdAt': _C('created_at', 'ts'),
});

const _audit = _T('audit_log', 'id', {
  'action': _C('action'), 'adminUid': _C('admin_uid'), 'adminEmail': _C('admin_email'), 'targetType': _C('target_type'),
  'targetId': _C('target_id'), 'targetName': _C('target_name'), 'details': _C('details'), 'timestamp': _C('created_at', 'ts'),
});

const _banners = _T('banners', 'id', {
  'title': _C('title'), 'subtitle': _C('subtitle'), 'imageUrl': _C('image_url'), 'buttonText': _C('button_text'),
  'targetType': _C('target_type'), 'targetValue': _C('target_value'), 'isActive': _C('is_active', 'b'),
  'order': _C('sort_order', 'i'), 'createdAt': _C('created_at', 'ts'),
});

const _collections = _T('collections', 'id', {
  'title': _C('title'), 'titleHi': _C('title_hi'), 'titleNe': _C('title_ne'), 'subtitle': _C('subtitle'),
  'subtitleHi': _C('subtitle_hi'), 'subtitleNe': _C('subtitle_ne'), 'color': _C('color'), 'imageUrl': _C('image_url'),
  'isActive': _C('is_active', 'b'), 'order': _C('sort_order', 'i'), 'createdAt': _C('created_at', 'ts'),
});

const _appAds = _T('app_ads', 'app_id', {
  'adsEnabled': _C('ads_enabled', 'b'), 'appsPerReward': _C('apps_per_reward', 'i'), 'availableForInstall': _C('available_for_install', 'b'),
  'unlockableByRewardedAd': _C('unlockable_by_rewarded_ad', 'b'), 'updatedAt': _C('updated_at', 'ts'), 'updatedBy': _C('updated_by'),
});

const _tables = <String, _T>{
  'apps': _apps, 'users': _users, 'submissions': _subs, 'darkstoreReports': _reports, 'fraudAlerts': _alerts,
  'auditLog': _audit, 'banners': _banners, 'collections': _collections, 'appAds': _appAds,
};

/// Settings stored as one JSON row each in `app_config`.
const _configs = {'adsConfig', 'unityAdsConfig', 'maintenanceConfig', 'premiumConfig', 'eventConfig', 'profileUiConfig', 'navUiConfig', 'announceConfig', 'featureFlags'};

/// device_guard_config (one row): suspension settings, edited in the admin Guard tab.
const _guardCols = <String, _C>{
  'autoSuspend': _C('auto_suspend', 'b'), 'maxAccounts': _C('max_accounts', 'i'), 'blockEmulators': _C('block_emulators', 'b'),
  'warningsBeforeSuspend': _C('warnings_before_suspend', 'i'), 'warnCooldownMinutes': _C('warn_cooldown_minutes', 'i'),
  'exemptAfterUnsuspend': _C('exempt_after_unsuspend', 'b'), 'exemptDays': _C('exempt_days', 'i'),
  'resetStrikesOnUnsuspend': _C('reset_strikes_on_unsuspend', 'b'), 'warningMessage': _C('warning_message'),
};

dynamic _from(dynamic v, String t) {
  switch (t) {
    case 'b':
      return v == true;
    case 'i':
      return v is num ? v.toInt() : int.tryParse('$v') ?? 0;
    case 'f':
      return v == null ? '0' : '$v';
    case 'ts':
      if (v == null) return 0;
      if (v is num) return v.toInt();
      return DateTime.tryParse('$v')?.millisecondsSinceEpoch ?? 0;
    case 'arr':
      return v is List ? v.map((e) => '$e').join(', ') : (v?.toString() ?? '');
    case 'j':
      return jsonEncode(v ?? const []);
    default:
      return v?.toString() ?? '';
  }
}

dynamic _to(dynamic v, String t) {
  switch (t) {
    case 'b':
      return v == true || v == 'true' || v == 1;
    case 'i':
      return v is num ? v.toInt() : int.tryParse('$v') ?? 0;
    case 'f':
      return double.tryParse('$v') ?? 0;
    case 'ts':
      final n = v is num ? v.toInt() : int.tryParse('$v') ?? 0;
      return n > 0 ? DateTime.fromMillisecondsSinceEpoch(n, isUtc: true).toIso8601String() : null;
    case 'arr':
      if (v is List) return v.map((e) => '$e').toList();
      return '${v ?? ''}'.split(RegExp(r'[,|\n]')).map((e) => e.trim()).where((e) => e.isNotEmpty).toList();
    case 'j':
      if (v is String) {
        try {
          return jsonDecode(v.isEmpty ? '[]' : v);
        } catch (_) {
          return <dynamic>[];
        }
      }
      return v;
    default:
      return v?.toString() ?? '';
  }
}

Map<String, dynamic> _row(_T t, Map<String, dynamic> r) {
  final m = <String, dynamic>{'id': '${r[t.pk]}'};
  t.f.forEach((k, c) => m[k] = _from(r[c.col], c.t));
  // the live developer name (users.dev_name) comes with the apps view
  if (t.table == 'apps' && '${r['developer_name'] ?? ''}'.isNotEmpty) m['developer'] = '${r['developer_name']}';
  return m;
}

Map<String, dynamic> _patch(_T t, Map<String, Object?> v) {
  final o = <String, dynamic>{};
  v.forEach((k, x) {
    final c = t.f[k];
    if (c != null && k != 'createdAt') o[c.col] = _to(x, c.t);
  });
  return o;
}

String _rid(String p) => '${p}_${DateTime.now().millisecondsSinceEpoch}_${1000 + DateTime.now().microsecondsSinceEpoch % 9000}';

/// The Firebase-style Db calls the admin screens already use, answered by Supabase.
class SbDb {
  static String _root(String p) => p.split('/').first;
  static String? _id(String p) {
    final x = p.split('/');
    return x.length > 1 && x[1].isNotEmpty ? x[1] : null;
  }

  static bool handles(String p) {
    final r = _root(p);
    return _tables.containsKey(r) ||
        _configs.contains(r) ||
        const {'app_policy', 'guardConfig', 'fraudWarnings', 'fraudStrikes', 'deviceRegistry', 'deviceOwner', 'userDevices', 'submissionLocks'}.contains(r);
  }

  static Future<List<Map<String, dynamic>>> _all(String from, {String? order, bool asc = false}) async {
    final out = <Map<String, dynamic>>[];
    var a = 0;
    while (true) {
      final q = Sb.c.from(from).select();
      final res = order == null ? await q.range(a, a + 999) : await q.order(order, ascending: asc).range(a, a + 999);
      final page = (res as List).map((e) => Map<String, dynamic>.from(e as Map)).toList();
      out.addAll(page);
      if (page.length < 1000) break;
      a += 1000;
    }
    return out;
  }

  // ---------------------------------------------------------------- read
  static Future<List<Map<String, dynamic>>> list(String p) async {
    final r = _root(p);
    final t = _tables[r];
    if (t == null) {
      return (await map(p)).entries.map((e) => e.value is Map<String, dynamic> ? <String, dynamic>{...e.value as Map<String, dynamic>, 'id': e.key} : <String, dynamic>{'id': e.key, 'value': e.value}).toList();
    }
    final rows = await _all(t.view ?? t.table, order: t.table == 'audit_log' ? 'created_at' : null);
    final out = rows.map((x) => _row(t, x)).toList();
    if (r == 'collections') {
      final links = await _all('collection_apps', order: 'position', asc: true);
      final by = <String, List<String>>{};
      for (final l in links) {
        (by['${l['collection_id']}'] ??= []).add('${l['app_id']}');
      }
      for (final m in out) {
        m['appIds'] = by[m['id']] ?? <String>[];
      }
    }
    return out;
  }

  static Future<Map<String, dynamic>> map(String p) async {
    final r = _root(p);
    if (_configs.contains(r)) {
      final x = await Sb.c.from('app_config').select('value').eq('key', r).limit(1);
      final l = x as List;
      final v = l.isEmpty ? null : (l.first as Map)['value'];
      return v is Map ? Map<String, dynamic>.from(v) : <String, dynamic>{};
    }
    switch (r) {
      case 'guardConfig':
        {
          final x = await Sb.c.from('device_guard_config').select().eq('id', 1).limit(1) as List;
          final row = x.isEmpty ? <String, dynamic>{} : Map<String, dynamic>.from(x.first as Map);
          return {for (final e in _guardCols.entries) e.key: _from(row[e.value.col], e.value.t)};
        }
      case 'fraudWarnings':
        return {for (final x in await _all('fraud_strikes')) if ((x['warnings'] ?? 0) is num && (x['warnings'] ?? 0) > 0) '${x['uid']}': x['warnings']};
      case 'app_policy':
        return _policy();
      case 'appAds':
        return {for (final x in await _all('app_ads')) '${x['app_id']}': _row(_appAds, x)};
      case 'fraudStrikes':
        return {for (final x in await _all('fraud_strikes')) '${x['uid']}': x['strikes'] ?? 0};
      case 'deviceRegistry':
        {
          final out = <String, dynamic>{};
          for (final x in await _all('user_devices')) {
            ((out['${x['device_id']}'] ??= <String, dynamic>{}) as Map<String, dynamic>)['${x['uid']}'] = _from(x['seen_at'], 'ts');
          }
          return out;
        }
      case 'userDevices':
        {
          final out = <String, dynamic>{};
          for (final x in await _all('user_devices')) {
            ((out['${x['uid']}'] ??= <String, dynamic>{}) as Map<String, dynamic>)['${x['device_id']}'] = _from(x['seen_at'], 'ts');
          }
          return out;
        }
      case 'deviceOwner':
        return {for (final x in await _all('devices')) if ('${x['owner_uid'] ?? ''}'.isNotEmpty) '${x['device_id']}': '${x['owner_uid']}'};
    }
    final t = _tables[r];
    if (t == null) return <String, dynamic>{};
    return {for (final m in await list(p)) '${m['id']}': m};
  }

  static Future<Map<String, dynamic>> _policy() async {
    final x = await Sb.c.from('app_policy').select().order('updated_at', ascending: false).limit(1) as List;
    if (x.isEmpty) return <String, dynamic>{};
    final m = Map<String, dynamic>.from(x.first as Map);
    return {
      'version': '${m['version'] ?? ''}',
      'title': '${m['title'] ?? ''}',
      'content': '${m['content'] ?? ''}',
      'updatedAt': '${m['updated_at'] ?? ''}'.split('T').first,
      'updatedBy': '${m['updated_by'] ?? ''}',
    };
  }

  // ---------------------------------------------------------------- write
  static Future<void> upd(String p, Map<String, Object?> v) async {
    final r = _root(p), id = _id(p);
    if (r == 'submissionLocks') return; // now a unique index in the database
    if (_configs.contains(r)) {
      final cur = await map(r);
      await Sb.c.from('app_config').upsert({'key': r, 'value': {...cur, ...v}}, onConflict: 'key');
      return;
    }
    if (r == 'guardConfig') {
      final row = <String, dynamic>{'id': 1};
      v.forEach((k, x) {
        final c = _guardCols[k];
        if (c == null) return;
        final y = _to(x, c.t);
        row[c.col] = y is int ? (c.col == 'max_accounts' ? (y < 1 ? 1 : y) : (y < 0 ? 0 : y)) : y;
      });
      await Sb.c.from('device_guard_config').upsert(row, onConflict: 'id');
      return;
    }
    if (r == 'app_policy') {
      final cur = await _policy();
      final m = {...cur, ...v};
      await Sb.c.from('app_policy').upsert({
        'version': '${m['version']}'.isEmpty ? '1' : '${m['version']}',
        'title': '${m['title']}',
        'content': '${m['content']}',
        'updated_at': DateTime.now().toUtc().toIso8601String(),
        'updated_by': FirebaseAuth.instance.currentUser?.email ?? '',
      }, onConflict: 'version');
      return;
    }
    final t = _tables[r];
    if (t == null || id == null) throw 'Unsupported write: $p';
    if (r == 'users') {
      // role / suspension / premium are not writable from the app: a checked admin function does it
      final patch = <String, dynamic>{};
      v.forEach((k, x) {
        final c = t.f[k];
        if (c != null && const {'role', 'is_developer', 'is_suspended', 'suspension_reason', 'is_premium_member', 'dev_name'}.contains(c.col)) {
          patch[c.col] = _to(x, c.t);
        }
      });
      await Sb.c.rpc('admin_update_user', params: {'p_uid': id, 'p_patch': patch});
      return;
    }
    if (r == 'appAds') {
      await Sb.c.from('app_ads').upsert({'app_id': id, ..._patch(t, v)}, onConflict: 'app_id');
      return;
    }
    final row = _patch(t, v);
    if (row.isNotEmpty) await Sb.c.from(t.table).update(row).eq(t.pk, id);
    if (r == 'collections' && v.containsKey('appIds')) await _links(id, v['appIds']);
  }

  static Future<void> _links(String cid, Object? ids) async {
    final want = (ids is List ? ids.map((e) => '$e').toList() : <String>[]);
    final have = {for (final x in await _all('apps')) '${x['id']}'};
    await Sb.c.from('collection_apps').delete().eq('collection_id', cid);
    final rows = <Map<String, dynamic>>[
      for (var i = 0; i < want.length; i++)
        if (have.contains(want[i])) {'collection_id': cid, 'app_id': want[i], 'position': i},
    ];
    if (rows.isNotEmpty) await Sb.c.from('collection_apps').insert(rows);
  }

  static Future<void> push(String p, Map<String, Object?> v) async {
    final r = _root(p);
    final t = _tables[r];
    if (t == null || r == 'users' || r == 'apps') throw 'Unsupported create: $p';
    final id = _rid(r == 'auditLog' ? 'a' : r == 'banners' ? 'b' : 'c');
    await Sb.c.from(t.table).insert({t.pk: id, ..._patch(t, v)});
    if (r == 'collections' && v.containsKey('appIds')) await _links(id, v['appIds']);
  }

  static Future<void> del(String p) async {
    final r = _root(p), id = _id(p);
    if (r == 'submissionLocks') return;
    if (_configs.contains(r)) {
      await Sb.c.from('app_config').delete().eq('key', r);
      return;
    }
    if (r == 'fraudStrikes' && id != null) {
      await Sb.c.from('fraud_strikes').delete().eq('uid', id);
      return;
    }
    final t = _tables[r];
    if (t == null || id == null) throw 'Unsupported delete: $p';
    await Sb.c.from(t.table).delete().eq(t.pk, id);
  }

  // ---------------------------------------------------------------- helpers
  static Future<bool> isAdminUid(String uid) async {
    try {
      final x = await Sb.c.from('users').select('role').eq('uid', uid).limit(1) as List;
      return x.isNotEmpty && (x.first as Map)['role'] == 'admin';
    } catch (e) {
      debugPrint('isAdmin: $e');
      return false;
    }
  }
}
