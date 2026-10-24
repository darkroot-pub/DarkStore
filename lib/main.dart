import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_database/firebase_database.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:flutter/material.dart';
import 'core.dart';
import 'tabs.dart' hide adsTab;
import 'ads_tab.dart';

// NOTE: this admin app never asks for / saves an FCM token. The user app already
// stores each person's token; the admin only SENDS pushes through the server.
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp();
  FirebaseDatabase.instance.databaseURL = dbUrl;
  runApp(const AdminApp());
}

class AdminApp extends StatelessWidget {
  const AdminApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'DarkStore Admin',
        debugShowCheckedModeBanner: false,
        themeMode: ThemeMode.system,
        theme: ThemeData(colorSchemeSeed: accent, useMaterial3: true),
        darkTheme: ThemeData(colorSchemeSeed: accent, brightness: Brightness.dark, useMaterial3: true),
        home: const Gate(),
      );
}

class Gate extends StatelessWidget {
  const Gate({super.key});
  @override
  Widget build(BuildContext context) => StreamBuilder<User?>(
        stream: FirebaseAuth.instance.authStateChanges(),
        builder: (_, snap) {
          if (snap.connectionState == ConnectionState.waiting) return const Scaffold(body: Center(child: CircularProgressIndicator()));
          final u = snap.data;
          if (u == null) return const LoginPage();
          return FutureBuilder<bool>(
            future: isAdmin(u),
            builder: (_, a) {
              if (!a.hasData) return const Scaffold(body: Center(child: CircularProgressIndicator()));
              return a.data! ? const Shell() : NotAdmin();
            },
          );
        },
      );
}

Future<bool> isAdmin(User u) async {
  if (u.email == ownerEmail && u.emailVerified) return true;
  try {
    return (await Db.r('users/${u.uid}/role').get()).value == 'admin';
  } catch (_) {
    return false;
  }
}

class NotAdmin extends StatelessWidget {
  NotAdmin({super.key});
  @override
  Widget build(BuildContext c) => Scaffold(body: Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
        const Icon(Icons.lock, size: 48), const SizedBox(height: 8), const Text('This account is not an admin.'),
        TextButton(onPressed: () => FirebaseAuth.instance.signOut(), child: const Text('Sign out')),
      ])));
}

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});
  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final em = TextEditingController(), pw = TextEditingController();
  bool busy = false;
  String err = '';
  // Web client id (client_type 3) from google-services.json: needed so Google returns an ID token for Firebase.
  static const _webClientId = '210511589455-90vu807op09vmokh1g9niflgid076dfd.apps.googleusercontent.com';

  Future<void> google() async {
    setState(() { busy = true; err = ''; });
    try {
      final g = GoogleSignIn(serverClientId: _webClientId, scopes: ['email']);
      await g.signOut(); // always show the account picker
      final acc = await g.signIn();
      if (acc == null) { if (mounted) setState(() => busy = false); return; }
      final a = await acc.authentication;
      await FirebaseAuth.instance.signInWithCredential(GoogleAuthProvider.credential(idToken: a.idToken, accessToken: a.accessToken));
    } on FirebaseAuthException catch (e) {
      err = e.message ?? e.code;
    } catch (e) {
      err = '$e'.contains('10') || '$e'.contains('sign_in_failed')
          ? 'Google sign-in failed (error 10): this build\'s signing key is not registered in Firebase. Build with the release keystore or add this SHA-1 in Firebase settings.'
          : 'Google sign-in failed: $e';
    }
    if (mounted) setState(() => busy = false);
  }

  Future<void> go() async {
    setState(() { busy = true; err = ''; });
    try {
      await FirebaseAuth.instance.signInWithEmailAndPassword(email: em.text.trim(), password: pw.text);
    } on FirebaseAuthException catch (e) {
      err = e.message ?? e.code;
    }
    if (mounted) setState(() => busy = false);
  }

  @override
  Widget build(BuildContext c) => Scaffold(
        body: Center(child: SingleChildScrollView(padding: const EdgeInsets.all(24), child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 380), child: Column(children: [
          const Icon(Icons.admin_panel_settings, size: 64, color: accent),
          const Text('DarkStore Admin', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
          const SizedBox(height: 20),
          TextField(controller: em, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Admin email', border: OutlineInputBorder())),
          const SizedBox(height: 10),
          TextField(controller: pw, obscureText: true, decoration: const InputDecoration(labelText: 'Password', border: OutlineInputBorder()), onSubmitted: (_) => go()),
          const SizedBox(height: 14),
          SizedBox(width: double.infinity, child: FilledButton(onPressed: busy ? null : go, child: Text(busy ? 'Signing in…' : 'Sign in'))),
          const SizedBox(height: 10),
          Row(children: const [Expanded(child: Divider()), Padding(padding: EdgeInsets.symmetric(horizontal: 10), child: Text('or', style: TextStyle(color: Colors.grey))), Expanded(child: Divider())]),
          const SizedBox(height: 10),
          SizedBox(width: double.infinity, child: OutlinedButton.icon(onPressed: busy ? null : google, icon: const Icon(Icons.account_circle_outlined), label: const Text('Continue with Google'))),
          if (err.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 10), child: Text(err, style: const TextStyle(color: Colors.red))),
        ])))),
      );
}

class Shell extends StatelessWidget {
  const Shell({super.key});
  @override
  Widget build(BuildContext c) {
    final tabs = <(String, IconData, Widget)>[
      ('Home', Icons.dashboard, FutureTab(homeTab)),
      ('Submissions', Icons.inbox, FutureTab(subsTab)),
      ('Catalog', Icons.store, FutureTab(catalogTab)),
      ('Update', Icons.system_update, FutureTab(updateTab)),
      ('Users', Icons.people, FutureTab(usersTab)),
      ('Notify', Icons.notifications, const NotifyTab()),
      ('Events', Icons.celebration, FutureTab(eventsTab)),
      ('Banners', Icons.view_carousel, FutureTab(bannersTab)),
      ('Collections', Icons.collections, FutureTab(collectionsTab)),
      ('Ads', Icons.campaign, FutureTab(adsTab)),
      ('Feedback', Icons.flag, FutureTab(feedbackTab)),
      ('Audit', Icons.history, FutureTab(auditTab)),
      ('Server', Icons.cloud, FutureTab(serverTab)),
    ];
    return DefaultTabController(
      length: tabs.length,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('DarkStore Admin'),
          actions: [IconButton(tooltip: 'Sign out', icon: const Icon(Icons.logout), onPressed: () => FirebaseAuth.instance.signOut())],
          bottom: TabBar(isScrollable: true, tabAlignment: TabAlignment.start, tabs: [for (final t in tabs) Tab(icon: Icon(t.$2, size: 18), text: t.$1)]),
        ),
        body: TabBarView(children: [for (final t in tabs) t.$3]),
      ),
    );
  }
}
