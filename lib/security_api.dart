import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:http/http.dart' as http;

import 'core.dart';

/// Address of the DarkStore Security Backend. Preferred: set at build time
///   --dart-define=SECURITY_API=https://security.yourdomain.com   (GitHub secret SECURITY_API)
/// Fallback: an admin can save it inside the app (Security tab > gear); it is kept
/// in the Realtime Database at notifierConfig/securityApiUrl.
const String _envUrl = String.fromEnvironment('SECURITY_API', defaultValue: '');

class SecError implements Exception {
  final int status; // HTTP status, 0 = no answer
  final String code;
  final String message;
  const SecError(this.status, this.code, this.message);
  @override
  String toString() => message;
}

Map<String, dynamic> asMap(Object? v) =>
    v is Map ? v.map((k, x) => MapEntry('$k', x)) : <String, dynamic>{};
List<Map<String, dynamic>> asMaps(Object? v) =>
    v is List ? [for (final e in v) if (e is Map) asMap(e)] : <Map<String, dynamic>>[];
List<String> asStrings(Object? v) => v is List ? [for (final e in v) '$e'] : <String>[];

/// Statuses the backend uses, in the order an admin cares about them.
const List<String> secStatuses = [
  'QUARANTINED',
  'SCAN_FAILED',
  'PUBLISH_FAILED',
  'RECEIVED',
  'DOWNLOADING',
  'ANALYZING',
  'VIRUSTOTAL_PENDING',
  'APPROVED',
  'PUBLISHING',
  'PUBLISHED',
  'REJECTED',
];

/// Still being worked on by the backend: the screens refresh by themselves.
bool secActive(String st) => const {
      'RECEIVED',
      'DOWNLOADING',
      'ANALYZING',
      'VIRUSTOTAL_PENDING',
      'APPROVED',
      'PUBLISHING',
    }.contains(st);

/// REST client for the admin endpoints (the same Firebase ID token the app already
/// uses; the server checks the admin role itself).
class SecApi {
  SecApi._();

  static Future<String> base() async {
    if (_envUrl.isNotEmpty) return _clean(_envUrl);
    try {
      final v = s((await Db.r('notifierConfig/securityApiUrl').get()).value).trim();
      if (v.startsWith('https://')) return _clean(v);
    } catch (_) {}
    return '';
  }

  static String _clean(String v) => v.trim().replaceAll(RegExp(r'/+$'), '');

  static Future<bool> configured() async => (await base()).isNotEmpty;

  static Future<Map<String, dynamic>> call(String method, String path,
      {Object? body, bool retried = false}) async {
    final b = await base();
    if (b.isEmpty) {
      throw const SecError(0, 'not_configured', 'The security service address is not set yet.');
    }
    final user = FirebaseAuth.instance.currentUser;
    if (user == null) throw const SecError(401, 'signin', 'Sign in again.');
    final token = await user.getIdToken(retried);
    final req = http.Request(method, Uri.parse('$b$path'))
      ..headers.addAll({
        'Authorization': 'Bearer $token',
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      });
    if (body != null) req.body = jsonEncode(body);
    final client = http.Client();
    try {
      final streamed = await client.send(req).timeout(const Duration(seconds: 25));
      final res = await http.Response.fromStream(streamed);
      if (res.statusCode == 401 && !retried) {
        return call(method, path, body: body, retried: true);
      }
      dynamic json;
      try {
        json = jsonDecode(utf8.decode(res.bodyBytes));
      } catch (_) {}
      if (res.statusCode >= 200 && res.statusCode < 300) return asMap(json);
      final err = json is Map ? asMap(json['error']) : <String, dynamic>{};
      throw SecError(
        res.statusCode,
        s(err['code']).isEmpty ? 'error' : s(err['code']),
        s(err['message']).isEmpty
            ? 'The security service answered with an error (${res.statusCode}).'
            : s(err['message']),
      );
    } on TimeoutException {
      throw const SecError(0, 'timeout', 'The security service did not answer in time.');
    } on SocketException {
      throw const SecError(0, 'offline', 'Could not reach the security service.');
    } on http.ClientException {
      throw const SecError(0, 'offline', 'Could not reach the security service.');
    } finally {
      client.close();
    }
  }

  // ── service ──
  static Future<Map<String, dynamic>> health() => call('GET', '/health');
  static Future<Map<String, dynamic>> serviceStatus() => call('GET', '/api/v1/status');

  // ── admin: security ──
  static Future<Map<String, dynamic>> overview() =>
      call('GET', '/api/v1/admin/security/overview');

  static Future<Map<String, dynamic>> list({String? status, int limit = 25, int offset = 0}) =>
      call('GET',
          '/api/v1/admin/security/submissions?limit=$limit&offset=$offset${status == null ? '' : '&status=$status'}');

  static Future<Map<String, dynamic>> detail(String id) =>
      call('GET', '/api/v1/admin/security/submissions/$id');

  static Future<Map<String, dynamic>> approve(String id, String note) =>
      call('POST', '/api/v1/admin/security/submissions/$id/approve', body: {'note': note});

  static Future<Map<String, dynamic>> reject(String id, String reason) =>
      call('POST', '/api/v1/admin/security/submissions/$id/reject', body: {'reason': reason});

  static Future<Map<String, dynamic>> retry(String id) =>
      call('POST', '/api/v1/admin/security/submissions/$id/retry');

  static Future<Map<String, dynamic>> rescan(String id) =>
      call('POST', '/api/v1/submissions/$id/rescan');

  /// Starts a check for a submission that has none yet (safe to repeat).
  static Future<Map<String, dynamic>> process(String id) =>
      call('POST', '/api/v1/submissions/$id/process');

  // ── admin: publishing ──
  static Future<Map<String, dynamic>> publishing(String id) =>
      call('GET', '/api/v1/admin/publishing/$id');

  static Future<Map<String, dynamic>> retryPublishing(String id) =>
      call('POST', '/api/v1/admin/publishing/$id/retry');
}
