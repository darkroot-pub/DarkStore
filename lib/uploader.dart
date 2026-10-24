import 'dart:convert';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';

/// Pick photos from the phone and upload them; returns public https URLs.
/// 1) ImgBB (same host as the store apps) when the build has IMGBB_API_KEY (`--dart-define`, GitHub secret).
/// 2) Firebase Storage as a fallback (needs your Storage rules to allow signed-in uploads).
class Uploader {
  static const _key = String.fromEnvironment('IMGBB_API_KEY');
  static const _bucket = 'gs://dark-store-6836d.firebasestorage.app';
  static const maxBytes = 12 * 1024 * 1024;
  static final _picker = ImagePicker();

  static Future<XFile?> one() => _picker.pickImage(source: ImageSource.gallery, maxWidth: 2000, maxHeight: 2000, imageQuality: 88);

  static Future<List<XFile>> many({int limit = 8}) async {
    final l = await _picker.pickMultiImage(maxWidth: 2000, maxHeight: 2000, imageQuality: 88);
    return l.length > limit ? l.sublist(0, limit) : l;
  }

  static Future<String> upload(XFile f, {String folder = 'admin'}) async {
    final bytes = await f.readAsBytes();
    if (bytes.length > maxBytes) throw 'Image is too large (max 12 MB).';
    String? err;
    if (_key.isNotEmpty) {
      try {
        final r = await http.post(Uri.parse('https://api.imgbb.com/1/upload?key=$_key'), body: {'image': base64Encode(bytes), 'name': _safe(f.name)}).timeout(const Duration(seconds: 45));
        final j = jsonDecode(r.body);
        final u = '${j['data']?['url'] ?? j['data']?['display_url'] ?? ''}';
        if (r.statusCode == 200 && u.startsWith('http')) return u.replaceFirst('http://', 'https://');
        err = 'ImgBB: ${j['error']?['message'] ?? r.statusCode}';
      } catch (e) {
        err = '$e';
      }
    }
    try {
      final ref = FirebaseStorage.instanceFor(bucket: _bucket).ref('$folder/${DateTime.now().millisecondsSinceEpoch}_${_safe(f.name)}');
      await ref.putData(bytes, SettableMetadata(contentType: 'image/jpeg'));
      return await ref.getDownloadURL();
    } catch (e) {
      debugPrint('storage upload: $e');
      throw 'Upload failed${_key.isEmpty ? ' (no IMGBB_API_KEY in this build, and Firebase Storage refused)' : ''}. ${err ?? e}';
    }
  }

  static String _safe(String n) => n.replaceAll(RegExp(r'[^A-Za-z0-9._-]'), '_');
}
