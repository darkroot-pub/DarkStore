-- =============================================================================
-- 07: device registry + fraud guard, admin "delete user", notifier state.
-- Run ONCE in Supabase -> SQL Editor (safe to re-run). Needs uid() and is_admin().
-- It builds on the tables the admin app already reads: devices, user_devices,
-- fraud_alerts, fraud_strikes.  (Replaces the earlier 06_device_guard.sql.)
-- =============================================================================

-- 0) Remove the objects of the earlier draft if it was ever run.
do $$
begin
  if exists (select 1 from information_schema.columns
             where table_schema='public' and table_name='devices' and column_name='device_hash')
     and not exists (select 1 from information_schema.columns
             where table_schema='public' and table_name='devices' and column_name='device_id') then
    drop table if exists public.device_accounts cascade;
    drop table if exists public.devices cascade;
  end if;
end $$;
drop view     if exists public.device_overview;
drop function if exists public.register_device(text, boolean, text);

-- 1) Tables (created only if missing) + the extra columns we need
create table if not exists public.devices (device_id text primary key, owner_uid text);
alter table public.devices
  add column if not exists owner_uid   text,
  add column if not exists model       text        not null default '',
  add column if not exists is_emulator boolean     not null default false,
  add column if not exists is_banned   boolean     not null default false,
  add column if not exists ban_reason  text        not null default '',
  add column if not exists first_seen  timestamptz not null default now(),
  add column if not exists last_seen   timestamptz not null default now();

create table if not exists public.user_devices (
  device_id text not null, uid text not null, seen_at timestamptz not null default now());
alter table public.user_devices
  add column if not exists email          text        not null default '',
  add column if not exists login_count    int         not null default 1,
  add column if not exists first_seen     timestamptz not null default now(),
  add column if not exists last_seen      timestamptz not null default now(),
  add column if not exists fcm_token      text        not null default '',
  add column if not exists fcm_updated_at timestamptz,
  add column if not exists allowed        boolean     not null default false;
do $$ begin
  create unique index if not exists user_devices_dev_uid_uq on public.user_devices (device_id, uid);
exception when others then null; end $$;
create index if not exists user_devices_uid_idx on public.user_devices (uid);

-- 2) Settings for the guard (one row)
create table if not exists public.device_guard_config (
  id int primary key default 1 check (id = 1),
  max_accounts int not null default 1 check (max_accounts >= 1),
  auto_suspend boolean not null default true,
  block_emulators boolean not null default false);
insert into public.device_guard_config (id) values (1) on conflict (id) do nothing;
alter table public.device_guard_config enable row level security;
drop policy if exists device_guard_config_admin on public.device_guard_config;
create policy device_guard_config_admin on public.device_guard_config
  for all to authenticated using (is_admin()) with check (is_admin());
grant select, update on public.device_guard_config to authenticated;

-- 3) Accounts deleted by an admin can never be re-created by a still-valid login
create table if not exists public.deleted_users (uid text primary key, email text, deleted_at timestamptz not null default now());
alter table public.deleted_users enable row level security;   -- no policy: only the server / functions touch it

create or replace function public.block_deleted_users() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if exists (select 1 from deleted_users d where d.uid = new.uid) then
    raise exception 'account was deleted by an admin' using errcode = 'P0001';
  end if;
  return new;
end $$;
drop trigger if exists users_block_deleted on public.users;
create trigger users_block_deleted before insert on public.users
  for each row execute function public.block_deleted_users();

-- 4) State of the notifier worker (which apps / submissions / chats it already pushed)
create table if not exists public.notifier_state (key text primary key, value jsonb not null default '{}'::jsonb);
alter table public.notifier_state enable row level security;  -- no policy: service key only

-- 5) helpers
create or replace function public.mask_email(e text) returns text language sql immutable as $$
  select case when e is null or e = '' then ''
    when position('@' in e) < 2 then e
    else left(e, least(2, position('@' in e) - 1)) || '***' || substr(e, position('@' in e)) end $$;

-- 6) THE APP CALLS THIS at sign-in and at every start: records phone <-> account,
--    login count and the FCM token, and suspends extra accounts (fraud guard).
create or replace function public.register_device(
  p_hash text, p_emulator boolean default false, p_model text default '', p_fcm text default ''
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  me text := uid()::text;
  jwt_email text := coalesce(nullif(auth.jwt() ->> 'email', ''), '');
  v_email text; my_email text; is_adm boolean;
  cfg public.device_guard_config;
  dev public.devices;
  my_rank int; total int; v_allowed boolean;
  reason text := '';
  v_susp boolean; v_reason text; owner text; first_user text;
begin
  if me is null or me = '' then return jsonb_build_object('ok', false, 'message', 'not signed in'); end if;
  if exists (select 1 from deleted_users where uid = me) then
    return jsonb_build_object('ok', true, 'suspended', true, 'reason', 'This account was deleted.');
  end if;
  if p_hash is null or length(p_hash) < 8 or length(p_hash) > 128 then
    return jsonb_build_object('ok', false, 'message', 'bad device id');
  end if;

  select * into cfg from device_guard_config where id = 1;
  if not found then cfg.max_accounts := 1; cfg.auto_suspend := true; cfg.block_emulators := false; end if;

  select u.email, (u.role = 'admin') into v_email, is_adm from users u where u.uid = me;
  my_email := coalesce(nullif(v_email, ''), jwt_email);
  is_adm := coalesce(is_adm, false);

  -- the phone
  update devices set last_seen = now(),
         is_emulator = is_emulator or coalesce(p_emulator, false),
         model = case when coalesce(p_model, '') <> '' then left(p_model, 80) else model end
   where device_id = p_hash;
  if not found then
    insert into devices (device_id, model, is_emulator) values (p_hash, left(coalesce(p_model, ''), 80), coalesce(p_emulator, false));
  end if;

  -- this account on that phone (+ login count + token)
  update user_devices set last_seen = now(), login_count = login_count + 1,
         email = case when my_email <> '' then my_email else email end,
         fcm_token = case when coalesce(p_fcm, '') <> '' then p_fcm else fcm_token end,
         fcm_updated_at = case when coalesce(p_fcm, '') <> '' then now() else fcm_updated_at end
   where device_id = p_hash and uid = me;
  if not found then
    insert into user_devices (device_id, uid, email, fcm_token, fcm_updated_at, seen_at, first_seen, last_seen)
    values (p_hash, me, coalesce(my_email, ''), coalesce(p_fcm, ''),
            case when coalesce(p_fcm, '') <> '' then now() end, now(), now(), now());
  end if;

  -- owner = the first account that ever used the phone
  select x.uid into first_user from (
    select uid from user_devices where device_id = p_hash order by coalesce(first_seen, seen_at), uid limit 1) x;
  update devices set owner_uid = first_user where device_id = p_hash and owner_uid is distinct from first_user
         and (owner_uid is null or not exists (select 1 from user_devices u where u.device_id = p_hash and u.uid = devices.owner_uid));
  select * into dev from devices where device_id = p_hash;

  select count(*) into total from user_devices where device_id = p_hash;
  select x.rnk, x.allowed into my_rank, v_allowed from (
    select uid, allowed, row_number() over (order by coalesce(first_seen, seen_at), uid) as rnk
    from user_devices where device_id = p_hash) x where x.uid = me;

  if not is_adm and not coalesce(v_allowed, false) then
    if dev.is_banned then
      reason := coalesce(nullif(dev.ban_reason, ''), 'This device is blocked because of fraud.');
    elsif cfg.auto_suspend and my_rank > cfg.max_accounts then
      reason := 'More than ' || cfg.max_accounts || ' account(s) on one device is not allowed (fraud protection). Please use the first account you registered on this phone.';
    elsif cfg.block_emulators and dev.is_emulator then
      reason := 'Emulators are not allowed.';
    end if;
  end if;

  if reason <> '' then
    update users set is_suspended = true, suspension_reason = reason where uid = me and is_suspended is distinct from true;
    if found then
      begin
        if not exists (select 1 from fraud_alerts where uid = me and type = 'MULTI_ACCOUNT_DEVICE' and coalesce(status,'') <> 'dismissed') then
          insert into fraud_alerts (id, type, severity, status, auto_action, uid, related_uid, email, name, details, created_at)
          values ('f_' || (extract(epoch from clock_timestamp()) * 1000)::bigint || '_' || (1000 + floor(random() * 9000))::int,
                  'MULTI_ACCOUNT_DEVICE', 'MEDIUM', 'open', 'suspended', me, coalesce(dev.owner_uid, ''), coalesce(my_email, ''),
                  coalesce(my_email, ''), reason, now());
        end if;
        update fraud_strikes set strikes = coalesce(strikes, 0) + 1 where uid = me;
        if not found then insert into fraud_strikes (uid, strikes) values (me, 1); end if;
      exception when others then null;   -- an alert problem must never block sign-in
      end;
    end if;
  end if;

  select coalesce(u.is_suspended, false), coalesce(u.suspension_reason, '') into v_susp, v_reason from users u where u.uid = me;
  return jsonb_build_object('ok', true, 'suspended', coalesce(v_susp, false) or reason <> '',
                            'reason', coalesce(nullif(v_reason, ''), reason), 'accounts', total);
end $$;

-- 7) "Your account is suspended" page: who you are and which account may continue
create or replace function public.suspension_info(p_hash text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare me text := uid()::text; my_email text; v_reason text; prim_uid text; prim_email text;
begin
  if me is null or me = '' then return jsonb_build_object('ok', false); end if;
  select u.email, coalesce(u.suspension_reason, '') into my_email, v_reason from users u where u.uid = me;
  select d.uid, coalesce(nullif(u.email, ''), d.email) into prim_uid, prim_email
    from user_devices d left join users u on u.uid = d.uid
   where d.device_id = p_hash and coalesce(u.is_suspended, false) = false
   order by coalesce(d.first_seen, d.seen_at), d.uid limit 1;
  return jsonb_build_object('ok', true, 'reason', coalesce(v_reason, ''),
    'yourMasked', mask_email(coalesce(my_email, auth.jwt() ->> 'email', '')),
    'primaryMasked', mask_email(coalesce(prim_email, '')),
    'canContinue', prim_uid is not null and prim_uid <> me);
end $$;

-- 8) ADMIN: every device + account on it with login count, last login, FCM token
create or replace function public.admin_device_details() returns table (
  device_id text, uid text, email text, name text, model text, is_emulator boolean, is_banned boolean,
  is_owner boolean, login_count int, first_seen timestamptz, last_seen timestamptz,
  fcm_token text, fcm_updated_at timestamptz, is_suspended boolean)
language plpgsql security definer set search_path = public as $$
begin
  if not is_admin() then raise exception 'admin only' using errcode = '42501'; end if;
  return query
    select d.device_id, ud.uid, coalesce(nullif(ud.email,''), u.email, ''),
           coalesce(nullif(u.display_name,''), nullif(u.user_name,''), ''),
           d.model, d.is_emulator, d.is_banned, (d.owner_uid = ud.uid), ud.login_count,
           coalesce(ud.first_seen, ud.seen_at), ud.last_seen,
           coalesce(nullif(ud.fcm_token,''), p.fcm_token, ''), ud.fcm_updated_at, coalesce(u.is_suspended, false)
      from user_devices ud
      join devices d on d.device_id = ud.device_id
      left join users u on u.uid = ud.uid
      left join user_private p on p.uid = ud.uid
     order by ud.last_seen desc;
end $$;

-- 9) ADMIN: ban / unban a phone
create or replace function public.admin_ban_device(p_hash text, p_ban boolean, p_reason text default '') returns void
language plpgsql security definer set search_path = public as $$
begin
  if not is_admin() then raise exception 'admin only' using errcode = '42501'; end if;
  update devices set is_banned = p_ban, ban_reason = case when p_ban then coalesce(p_reason, '') else '' end where device_id = p_hash;
  if p_ban then
    update users set is_suspended = true,
           suspension_reason = coalesce(nullif(p_reason, ''), 'This device is blocked because of fraud.')
     where uid in (select uid from user_devices where device_id = p_hash) and coalesce(role, 'user') <> 'admin';
  end if;
end $$;

-- 10) DELETE A USER'S DATA. delete_user_data is for the server only (service key);
--     admin_delete_user is the checked version an admin can call from the app.
create or replace function public.delete_user_data(p_uid text, p_delete_apps boolean default false) returns jsonb
language plpgsql security definer set search_path = public as $$
declare r record; n bigint; v_email text; errs jsonb := '[]'::jsonb; counts jsonb := '{}'::jsonb;
begin
  if p_uid is null or length(p_uid) < 6 then raise exception 'bad uid'; end if;
  select email into v_email from users where uid = p_uid;
  insert into deleted_users (uid, email) values (p_uid, v_email) on conflict (uid) do nothing;

  for r in
    select c.table_name, c.column_name
      from information_schema.columns c
      join information_schema.tables t on t.table_schema = c.table_schema and t.table_name = c.table_name and t.table_type = 'BASE TABLE'
     where c.table_schema = 'public'
       and c.column_name in ('uid','user_id','sender_id','follower_uid','following_uid','reporter_uid','submitted_by_uid')
       and c.table_name not in ('users','apps','deleted_users','notifier_state','audit_log','fraud_alerts')
  loop
    begin
      execute format('delete from public.%I where %I::text = $1', r.table_name, r.column_name) using p_uid;
      get diagnostics n = row_count;
      if n > 0 then counts := counts || jsonb_build_object(r.table_name || '.' || r.column_name, n); end if;
    exception when others then
      errs := errs || jsonb_build_array(r.table_name || ': ' || sqlerrm);
    end;
  end loop;

  begin delete from fraud_alerts where uid = p_uid or related_uid = p_uid; exception when others then null; end;
  begin delete from chats where participant_a = p_uid or participant_b = p_uid; exception when others then errs := errs || jsonb_build_array('chats: ' || sqlerrm); end;
  begin update devices set owner_uid = null where owner_uid = p_uid; exception when others then null; end;
  if v_email is not null and v_email <> '' then
    begin delete from submissions where lower(submitted_by) = lower(v_email); exception when others then null; end;
  end if;
  if p_delete_apps then
    begin delete from apps where owner_uid = p_uid; exception when others then errs := errs || jsonb_build_array('apps: ' || sqlerrm); end;
  end if;
  delete from users where uid = p_uid;
  get diagnostics n = row_count;
  return jsonb_build_object('ok', n > 0, 'deleted', counts, 'errors', errs);
end $$;

create or replace function public.admin_delete_user(p_uid text, p_delete_apps boolean default false) returns jsonb
language plpgsql security definer set search_path = public as $$
declare tgt_role text;
begin
  if not is_admin() then raise exception 'admin only' using errcode = '42501'; end if;
  if p_uid = uid()::text then raise exception 'you cannot delete your own account'; end if;
  select role into tgt_role from users where uid = p_uid;
  if tgt_role = 'admin' then raise exception 'remove the admin role first'; end if;
  return public.delete_user_data(p_uid, p_delete_apps);
end $$;

-- 11) permissions
revoke execute on function public.register_device(text, boolean, text, text) from public, anon;
revoke execute on function public.suspension_info(text)                      from public, anon;
revoke execute on function public.admin_device_details()                     from public, anon;
revoke execute on function public.admin_ban_device(text, boolean, text)      from public, anon;
revoke execute on function public.admin_delete_user(text, boolean)           from public, anon;
revoke execute on function public.delete_user_data(text, boolean)            from public, anon, authenticated;
grant  execute on function public.register_device(text, boolean, text, text) to authenticated;
grant  execute on function public.suspension_info(text)                      to authenticated;
grant  execute on function public.admin_device_details()                     to authenticated;
grant  execute on function public.admin_ban_device(text, boolean, text)      to authenticated;
grant  execute on function public.admin_delete_user(text, boolean)           to authenticated;
grant  execute on function public.delete_user_data(text, boolean)            to service_role;

-- 12) Realtime for the tables the apps watch (ignore "already member" errors)
do $$ declare t text; begin
  foreach t in array array['users','apps','notices','user_devices','devices','fraud_alerts','submissions'] loop
    begin execute format('alter publication supabase_realtime add table public.%I', t);
    exception when others then null; end;
  end loop;
end $$;

-- ADMIN CHEAT-SHEET
--   update device_guard_config set max_accounts = 2 where id = 1;     -- 2 accounts per phone
--   update device_guard_config set auto_suspend = false where id = 1;  -- record only, never suspend
--   update device_guard_config set block_emulators = true where id = 1;
--   update user_devices set allowed = true where email = 'x@gmail.com';  -- never auto-suspend this account
