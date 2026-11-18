-- =============================================================================
-- 08: suspension settings + "unsuspend sticks".
-- Run ONCE in Supabase -> SQL Editor (safe to re-run). Builds on 07.
--
-- Problem fixed: register_device() runs every time the app opens. A second account
-- on a phone was suspended again at the next start, even right after an admin had
-- unsuspended it (one account got 7 strikes this way).
--
-- Now:
--  * Unsuspending an account (admin app, SQL, anywhere) exempts it from the
--    automatic rules (forever, or for N days) and clears its strikes / warnings.
--  * Everything about auto-suspend is a setting in device_guard_config.
--  * A warning count: the first N violations only warn the user, then suspend.
-- =============================================================================

-- 1) Settings (one row, id = 1)
alter table public.device_guard_config
  add column if not exists warnings_before_suspend int     not null default 0,   -- 0 = suspend at once
  add column if not exists warn_cooldown_minutes   int     not null default 60,  -- one warning per this many minutes
  add column if not exists exempt_after_unsuspend  boolean not null default true,
  add column if not exists exempt_days             int     not null default 0,   -- 0 = forever
  add column if not exists reset_strikes_on_unsuspend boolean not null default true,
  add column if not exists warning_message         text    not null default '';

-- 2) Per-account state
alter table public.users          add column if not exists guard_exempt_until timestamptz;
alter table public.fraud_strikes  add column if not exists warnings int not null default 0,
                                  add column if not exists last_warned_at timestamptz;

-- 3) When an account goes from suspended to active: exempt it, clear counters, close alerts
create or replace function public.on_user_unsuspended_after() returns trigger
language plpgsql security definer set search_path = public as $$
declare cfg public.device_guard_config; have boolean;
begin
  if coalesce(old.is_suspended, false) and not coalesce(new.is_suspended, false) then
    select * into cfg from device_guard_config where id = 1;
    have := found;
    if not have or coalesce(cfg.exempt_after_unsuspend, true) then
      update users set guard_exempt_until =
        case when coalesce(cfg.exempt_days, 0) <= 0 then timestamptz '2999-01-01' else now() + make_interval(days => coalesce(cfg.exempt_days, 0)) end
       where uid = new.uid;
    end if;
    if not have or coalesce(cfg.reset_strikes_on_unsuspend, true) then
      begin
        update fraud_strikes set strikes = 0, warnings = 0, last_warned_at = null where uid = new.uid;
        update fraud_alerts set status = 'resolved', reviewed_at = now(), reviewed_by = 'unsuspend'
         where uid = new.uid and coalesce(status, '') in ('', 'open');
      exception when others then null;
      end;
    end if;
  end if;
  return null;
end $$;
drop trigger if exists users_unsuspended on public.users;
drop trigger if exists users_unsuspended_after on public.users;
create trigger users_unsuspended_after after update of is_suspended on public.users
  for each row execute function public.on_user_unsuspended_after();

-- 4) ADMIN: set / clear the exemption by hand.  p_days: 0 = forever, > 0 = days, < 0 = remove
create or replace function public.admin_set_guard_exempt(p_uid text, p_days int) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not is_admin() then raise exception 'admin only' using errcode = '42501'; end if;
  update users set guard_exempt_until = case
      when p_days < 0 then null
      when p_days = 0 then timestamptz '2999-01-01'
      else now() + make_interval(days => p_days) end
   where uid = p_uid;
  if not found then raise exception 'user not found'; end if;
end $$;
revoke execute on function public.admin_set_guard_exempt(text, int) from public, anon;
grant  execute on function public.admin_set_guard_exempt(text, int) to authenticated;

-- 5) The app calls this at sign-in and at every start (replaces the 07 version).
create or replace function public.register_device(
  p_hash text, p_emulator boolean default false, p_model text default '', p_fcm text default ''
) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  me text := uid()::text;
  jwt_email text := coalesce(nullif(auth.jwt() ->> 'email', ''), '');
  v_email text; my_email text; is_adm boolean; v_exempt timestamptz;
  cfg public.device_guard_config;
  dev public.devices;
  my_rank int; total int; v_allowed boolean;
  reason text := '';
  kind text := '';            -- ban | multi | emulator
  v_susp boolean; v_reason text; first_user text;
  w int; lw timestamptz; warn boolean := false; warn_left int := 0; warn_msg text := '';
begin
  if me is null or me = '' then return jsonb_build_object('ok', false, 'message', 'not signed in'); end if;
  if exists (select 1 from deleted_users where uid = me) then
    return jsonb_build_object('ok', true, 'suspended', true, 'reason', 'This account was deleted.');
  end if;
  if p_hash is null or length(p_hash) < 8 or length(p_hash) > 128 then
    return jsonb_build_object('ok', false, 'message', 'bad device id');
  end if;

  select * into cfg from device_guard_config where id = 1;
  if not found then cfg.max_accounts := 1; cfg.auto_suspend := true; cfg.block_emulators := false;
                    cfg.warnings_before_suspend := 0; cfg.warn_cooldown_minutes := 60; end if;

  select u.email, (u.role = 'admin'), u.guard_exempt_until into v_email, is_adm, v_exempt from users u where u.uid = me;
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

  -- admins, accounts marked "allowed", and accounts an admin unsuspended are never touched
  if not is_adm and not coalesce(v_allowed, false) and not (v_exempt is not null and v_exempt > now()) then
    if dev.is_banned then
      reason := coalesce(nullif(dev.ban_reason, ''), 'This device is blocked because of fraud.'); kind := 'ban';
    elsif cfg.auto_suspend and my_rank > cfg.max_accounts then
      reason := 'More than ' || cfg.max_accounts || ' account(s) on one device is not allowed (fraud protection). Please use the first account you registered on this phone.'; kind := 'multi';
    elsif cfg.block_emulators and dev.is_emulator then
      reason := 'Emulators are not allowed.'; kind := 'emulator';
    end if;
  end if;

  -- warnings first (not for a banned phone): the user is told, nothing is blocked yet
  if reason <> '' and kind <> 'ban' and coalesce(cfg.warnings_before_suspend, 0) > 0
     and not coalesce((select is_suspended from users where uid = me), false) then
    select f.warnings, f.last_warned_at into w, lw from fraud_strikes f where f.uid = me;
    w := coalesce(w, 0);
    if w < cfg.warnings_before_suspend then
      warn_msg := coalesce(nullif(cfg.warning_message, ''), reason);
      if lw is null or lw < now() - make_interval(mins => greatest(coalesce(cfg.warn_cooldown_minutes, 60), 0)) then
        update fraud_strikes set warnings = w + 1, last_warned_at = now() where uid = me;
        if not found then insert into fraud_strikes (uid, strikes, warnings, last_warned_at) values (me, 0, 1, now()); end if;
        w := w + 1; warn := true;
      end if;
      warn_left := greatest(cfg.warnings_before_suspend - w, 0);
      reason := '';   -- not suspended yet
    end if;
  end if;

  if reason <> '' then
    update users set is_suspended = true, suspension_reason = reason where uid = me and is_suspended is distinct from true;
    if found then
      begin
        if not exists (select 1 from fraud_alerts where uid = me and type = 'MULTI_ACCOUNT_DEVICE' and coalesce(status,'') not in ('dismissed','resolved')) then
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
                            'reason', coalesce(nullif(v_reason, ''), reason), 'accounts', total,
                            'warning', warn, 'warningsLeft', warn_left, 'warningMessage', warn_msg);
end $$;

-- ADMIN CHEAT-SHEET
--   update device_guard_config set warnings_before_suspend = 3 where id = 1;   -- warn 3 times, then suspend
--   update device_guard_config set exempt_days = 30 where id = 1;               -- unsuspended accounts are safe for 30 days (0 = forever)
--   update device_guard_config set exempt_after_unsuspend = false where id = 1; -- old behaviour (not recommended)
--   select admin_set_guard_exempt('<uid>', 0);                                  -- exempt one account forever
