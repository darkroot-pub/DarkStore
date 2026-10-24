import 'package:flutter/material.dart';
import 'core.dart';
import 'tabs.dart';

const _globalF = [
  F('enabled', 'Rewarded unlocks ON', 'bool'),
  F('unityGameId', 'Unity Game ID'),
  F('rewardedPlacementId', 'Rewarded placement id (e.g. BP_Rewarded_Android)'),
  F('defaultAppsPerReward', 'Default apps per reward', 'num'),
  F('requireS2S', 'Require Unity server-to-server verification (keep ON)', 'bool'),
  F('testMode', 'Unity test mode', 'bool'),
];
const _appF = [
  F('adsEnabled', 'Ads ON (app must be unlocked)', 'bool'),
  F('availableForInstall', 'Available for installation', 'bool'),
  F('unlockableByRewardedAd', 'Can be unlocked by rewarded ad', 'bool'),
  F('appsPerReward', 'Apps unlocked per completed ad (1-10)', 'num'),
];

/// Rewarded-ad control: global config, per-app Ads ON/OFF, real (verified) stats and history.
Future<Widget> adsTab(BuildContext c, VoidCallback rl) async {
  final r = await Future.wait([Db.list('apps'), Db.map('appAds'), Db.map('adsConfig'), Db.map('unityAdsConfig'), Api.call('/admin/ads/stats')]);
  final apps = r[0] as List<Map<String, dynamic>>;
  final ads = r[1] as Map<String, dynamic>;
  final g = r[2] as Map<String, dynamic>;
  final banner = r[3] as Map<String, dynamic>;
  final st = r[4] as Map<String, dynamic>?;
  apps.sort((a, b) => s(a['name']).toLowerCase().compareTo(s(b['name']).toLowerCase()));

  Future<void> save(String id, String name, Map<String, Object?> v) => act(c, () async {
        await Db.upd('appAds/$id', {...v, 'updatedAt': ms(), 'updatedBy': me()});
        await Db.audit('APP_ADS', 'app', id, name, v.entries.map((e) => '${e.key}=${e.value}').join(' '));
      }, rl, 'Saved');

  Widget stat(String a, Object? b) => SizedBox(width: 112, child: Card(child: Padding(padding: const EdgeInsets.all(10), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('${b ?? '-'}', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)), Text(a, style: const TextStyle(fontSize: 10, color: Colors.grey))]))));

  final header = <Widget>[
    Card(child: Column(children: [
      ListTile(
        title: Text('Rewarded unlocks ${bo(g['enabled'] ?? true) ? 'ON' : 'OFF'}'),
        subtitle: Text('Game ${g['unityGameId'] ?? '800408578'} · placement ${g['rewardedPlacementId'] ?? 'BP_Rewarded_Android'} · ${g['defaultAppsPerReward'] ?? 3} apps per ad · S2S ${g['requireS2S'] == false ? 'OFF (insecure)' : 'required'}'),
        trailing: const Icon(Icons.edit),
        onTap: () async {
          final x = await editSheet(c, 'Rewarded ads settings', _globalF, {'enabled': true, 'unityGameId': '800408578', 'rewardedPlacementId': 'BP_Rewarded_Android', 'defaultAppsPerReward': 3, 'requireS2S': true, ...g});
          if (x != null && c.mounted) act(c, () async { await Db.upd('adsConfig', only(x, _globalF)); await Db.audit('ADS_CONFIG', 'config', 'adsConfig', 'Rewarded ads'); }, rl, 'Saved');
        },
      ),
      SwitchListTile(title: const Text('Home banner (Unity BP_Banner_Android)'), value: bo(banner['enabled']), onChanged: (v) => act(c, () async { await Db.upd('unityAdsConfig', {'enabled': v}); await Db.audit('ADS', 'config', 'unityAdsConfig', 'Banner', 'enabled=$v'); }, rl)),
    ])),
    const Padding(padding: EdgeInsets.fromLTRB(4, 8, 4, 4), child: Text('Verified events only (server records)', style: TextStyle(fontWeight: FontWeight.bold))),
    if (st == null || st['ok'] != true)
      const Text('Stats unavailable — check the Server tab.')
    else
      Wrap(spacing: 6, runSpacing: 6, children: [
        stat('Reward attempts', st['started']), stat('Ads shown', st['shown']), stat('Completed (app-reported)', st['completedClientReported']),
        stat('Verified by Unity', st['verified']), stat('Rewards granted', st['granted']), stat('Verify failures', st['verificationFailures']),
        stat('Total unlocks', st['totalUnlocks']), stat('Users', st['usersUnlocking']),
      ]),
    TextButton.icon(
      icon: const Icon(Icons.history),
      label: const Text('Reward history'),
      onPressed: () async {
        final h = await Api.call('/admin/ads/history', body: {'limit': 100});
        if (!c.mounted) return;
        final items = (h?['items'] as List?) ?? [];
        showModalBottomSheet(context: c, isScrollControlled: true, useSafeArea: true, builder: (_) => ListView(padding: const EdgeInsets.all(12), children: [
          Text('Reward history', style: Theme.of(c).textTheme.titleMedium),
          if (items.isEmpty) const Padding(padding: EdgeInsets.all(16), child: Text('No rewards yet.')),
          for (final i in items)
            ListTile(dense: true, title: Text('${i['status']} · ${i['user']}'), subtitle: Text('${when(i['createdAt'])} · ${i['verification']} · unlocked ${(i['unlockedAppIds'] as List?)?.length ?? 0}')),
        ]));
      },
    ),
    const Divider(),
    const Padding(padding: EdgeInsets.all(4), child: Text('Per-app ads control', style: TextStyle(fontWeight: FontWeight.bold))),
  ];

  return Searchable(apps, (x) => '${x['name']} ${x['id']}', (x) {
    final a = (ads[x['id']] as Map?)?.cast<String, dynamic>() ?? const <String, dynamic>{};
    final on = bo(a['adsEnabled']);
    final per = (st?['perApp'] as Map?)?['${x['id']}'] ?? 0;
    return Card(
      child: ListTile(
        leading: netImg(s(x['logo'])),
        title: Text(s(x['name'])),
        subtitle: Wrap(children: [
          Text('id ${x['id']}  ', style: const TextStyle(fontSize: 11)),
          tagChip(on ? 'ADS ON' : 'ADS OFF', on ? Colors.orange : Colors.green),
          if (a['availableForInstall'] == false) tagChip('UNAVAILABLE', Colors.red),
          if (on && a['unlockableByRewardedAd'] == false) tagChip('NOT UNLOCKABLE', Colors.red),
          if (on) tagChip('${a['appsPerReward'] ?? 3}/ad', Colors.blue),
          if (per != 0) tagChip('$per unlocks', Colors.purple),
        ]),
        trailing: Switch(value: on, onChanged: (v) => save(s(x['id']), s(x['name']), {'adsEnabled': v, 'availableForInstall': a['availableForInstall'] ?? true, 'unlockableByRewardedAd': a['unlockableByRewardedAd'] ?? true, 'appsPerReward': a['appsPerReward'] ?? 3})),
        onTap: () async {
          final v = await editSheet(c, s(x['name']), _appF, {'adsEnabled': false, 'availableForInstall': true, 'unlockableByRewardedAd': true, 'appsPerReward': 3, ...a});
          if (v != null && c.mounted) save(s(x['id']), s(x['name']), only(v, _appF));
        },
      ),
    );
  }, header: header);
}
