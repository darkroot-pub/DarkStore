import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_database/firebase_database.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:flutter/material.dart';
import 'core.dart';
import 'sb.dart';
import 'shell.dart';

// NOTE: this admin app never asks for / saves an FCM token. The user app already
// stores each person's token; the admin only SENDS pushes through the server.
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp();
  FirebaseDatabase.instance.databaseURL = dbUrl; // DarkStoreUpdate + notifier address only
  await Sb.init();
  runApp(const AdminApp());
}

class AdminApp extends StatelessWidget {
  const AdminApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'DarkStore Admin',
        debugShowCheckedModeBanner: false,
        themeMode: ThemeMode.system,
        theme: adminTheme(Brightness.light),
        darkTheme: adminTheme(Brightness.dark),
        home: const Gate(),
      );
}

class Gate extends StatefulWidget {
  const Gate({super.key});
  @override
  State<Gate> createState() => _GateState();
}

class _GateState extends State<Gate> {
  int _try = 0; // bump to re-check the role (after the owner gave this person one)
  @override
  Widget build(BuildContext context) => StreamBuilder<User?>(
        stream: FirebaseAuth.instance.authStateChanges(),
        builder: (_, snap) {
          if (snap.connectionState == ConnectionState.waiting) return const _Splash();
          final u = snap.data;
          if (u == null) {
            myAccess = Access.none;
            return const LoginPage();
          }
          return FutureBuilder<Access>(
            key: ValueKey('${u.uid}-$_try'),
            future: Access.load(u),
            builder: (_, a) {
              if (!a.hasData) return const _Splash();
              myAccess = a.data!;
              return myAccess.isStaff ? const Shell() : NotAdmin(onRetry: () => setState(() => _try++));
            },
          );
        },
      );
}

class _Splash extends StatelessWidget {
  const _Splash();
  @override
  Widget build(BuildContext context) => const Scaffold(body: Center(child: CircularProgressIndicator()));
}

class NotAdmin extends StatelessWidget {
  final VoidCallback onRetry;
  const NotAdmin({super.key, required this.onRetry});
  @override
  Widget build(BuildContext c) {
    final cs = Theme.of(c).colorScheme;
    final email = FirebaseAuth.instance.currentUser?.email ?? '';
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(28),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 400),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              Icon(Icons.lock_outline_rounded, size: 56, color: cs.primary),
              const SizedBox(height: 16),
              const Text('No access yet', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
              const SizedBox(height: 8),
              Text(
                '${email.isEmpty ? 'This account' : email} does not have a role in the DarkStore admin panel.\n\n'
                'Ask the owner to add you under Team & roles. You must have signed into the DarkStore app at least once.',
                textAlign: TextAlign.center,
                style: TextStyle(color: cs.onSurfaceVariant, height: 1.4),
              ),
              const SizedBox(height: 22),
              SizedBox(width: double.infinity, child: FilledButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh_rounded), label: const Text('Check again'))),
              const SizedBox(height: 10),
              SizedBox(width: double.infinity, child: OutlinedButton(onPressed: () => FirebaseAuth.instance.signOut(), child: const Text('Sign out'))),
            ]),
          ),
        ),
      ),
    );
  }
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
          Container(
            width: 72, height: 72,
            decoration: BoxDecoration(color: accent, borderRadius: BorderRadius.circular(22)),
            child: const Icon(Icons.admin_panel_settings_rounded, size: 40, color: Colors.white),
          ),
          const SizedBox(height: 14),
          const Text('DarkStore Admin', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
          const SizedBox(height: 4),
          const Text('Sign in with your staff account', style: TextStyle(color: Colors.grey)),
          const SizedBox(height: 24),
          TextField(controller: em, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Email', prefixIcon: Icon(Icons.mail_outline_rounded))),
          const SizedBox(height: 10),
          TextField(controller: pw, obscureText: true, decoration: const InputDecoration(labelText: 'Password', prefixIcon: Icon(Icons.lock_outline_rounded)), onSubmitted: (_) => go()),
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
