-- =============================================================================
-- 13: Staff roles. Owner/admins (users.role='admin') keep full power. Other people get a ROLE
-- (users.staff_role) = a list of permissions. Everything is enforced HERE (RLS + RPC guards),
-- the admin app only hides what you may not use.
-- Permission strings:  'area' = view,  'area:w' = view + change.
-- Areas: submissions security catalog reviews users fraud support content design ads settings api audit insights
-- Admin-only forever: team/roles, deleting users, Notify, Push-update server tools.
-- =============================================================================
create table if not exists public.staff_roles (
  key text primary key,
  label text not null,
  description text not null default '',
  color text not null default '#6366F1',
  perms text[] not null default '{}',
  builtin boolean not null default false,
  created_at timestamptz not null default now()
);
alter table public.users add column if not exists staff_role text references public.staff_roles(key) on update cascade on delete set null;
alter table public.staff_roles enable row level security;
drop policy if exists staff_roles_read on public.staff_roles;
drop policy if exists staff_roles_admin on public.staff_roles;
create policy staff_roles_read  on public.staff_roles for select using (public.uid() is not null);
create policy staff_roles_admin on public.staff_roles for all using (public.is_admin()) with check (public.is_admin());

insert into public.staff_roles(key, label, description, color, perms, builtin) values
 ('moderator', 'Moderator', 'Reviews apps and reviews, handles reports, warnings and suspensions.', '#F59E0B',
   array['submissions:w','security','catalog','reviews:w','users','fraud:w','support:w','insights'], true),
 ('reviewer', 'App reviewer', 'Approves or rejects submitted apps and reads their security reports.', '#10B981',
   array['submissions:w','security:w','catalog','reviews'], true),
 ('support', 'Support agent', 'Answers support messages and user reports. Can read users and reviews.', '#3B82F6',
   array['support:w','users','reviews','submissions'], true),
 ('content', 'Content manager', 'Runs the catalog, collections, banners, events, announcements and home design.', '#8B5CF6',
   array['catalog:w','content:w','design:w','ads','insights'], true),
 ('viewer', 'Viewer (read only)', 'Can look at everything except settings. Cannot change anything.', '#64748B',
   array['submissions','security','catalog','reviews','users','fraud','support','content','design','ads','api','audit','insights'], true)
on conflict (key) do nothing;

-- ---- permission helpers ----
create or replace function public.has_perm(p_area text, p_write boolean default false) returns boolean
language sql stable security definer set search_path = public as $$
  select public.is_admin() or coalesce((select (p_area || ':w') = any (r.perms) or (not p_write and p_area = any (r.perms))
                   from public.users u join public.staff_roles r on r.key = u.staff_role
                   where u.uid = public.uid() and not u.is_suspended and u.role <> 'banned'), false)
$$;
create or replace function public.is_staff() returns boolean language sql stable security definer set search_path = public as $$
  select public.is_admin() or exists (select 1 from public.users u where u.uid = public.uid() and u.staff_role is not null and not u.is_suspended)
$$;
create or replace function public.config_area(p_key text) returns text language sql immutable as $$
  select case
    when p_key in ('navUiConfig','profileUiConfig','homeLayout','appBoxUiConfig','recommendConfig') then 'design'
    when p_key in ('announceConfig','eventConfig','faqConfig','shareConfig','searchConfig') then 'content'
    when p_key in ('supportConfig','chatRules') then 'support'
    when p_key in ('adsConfig','unityAdsConfig') then 'ads'
    when p_key = 'reviewConfig' then 'reviews'
    when p_key = 'apiConfig' then 'api'
    else 'settings' end
$$;
create or replace function public.my_access() returns jsonb language sql stable security definer set search_path = public as $$
  select case
    when public.is_admin() then jsonb_build_object('admin', true, 'role', 'admin', 'label', 'Administrator', 'color', '#EF4444', 'perms', '[]'::jsonb)
    else coalesce((select jsonb_build_object('admin', false, 'role', r.key, 'label', r.label, 'color', r.color, 'perms', to_jsonb(r.perms))
                   from public.users u join public.staff_roles r on r.key = u.staff_role where u.uid = public.uid() and not u.is_suspended),
                  jsonb_build_object('admin', false, 'role', '', 'label', '', 'perms', '[]'::jsonb)) end
$$;
grant execute on function public.has_perm(text, boolean), public.is_staff(), public.config_area(text), public.my_access() to authenticated;

-- ---- rewrite table policies: "is_admin()" becomes "is_admin() or has_perm(area[, write])" ----
do $$
declare
  m jsonb := '{"apps":"catalog","collections":"catalog","collection_apps":"catalog","submissions":"submissions",
    "apk_processing_jobs":"security","apk_security_events":"security","apk_security_reports":"security","apk_trusted_signers":"security","github_release_records":"security",
    "reviews":"reviews","review_reports":"reviews","users":"users","user_devices":"users","premium_grants":"users","referral_codes":"users","referral_stats":"users","user_unlocks":"users","ad_rewards":"users",
    "fraud_alerts":"fraud","fraud_strikes":"fraud","devices":"fraud","device_guard_config":"fraud","darkstore_reports":"support","global_messages":"support",
    "banners":"content","notices":"content","app_ads":"ads","app_policy":"settings","app_config":"*","audit_log":"audit","search_events":"insights",
    "api_keys":"api","api_app_rules":"api","api_usage":"api"}';
  r record; area text; rd text; wr text; q text; wc text; base text;
begin
  for r in select * from pg_policies where schemaname = 'public' and tablename in (select jsonb_object_keys(m))
           and (coalesce(qual, '') ilike '%is_admin()%' or coalesce(with_check, '') ilike '%is_admin()%') and policyname not like '%\_stf\_%'
           and (coalesce(qual, '') || coalesce(with_check, '')) not ilike '%has_perm%' loop
    area := m->>r.tablename;
    if area = '*' then
      rd := 'true'; wr := 'public.has_perm(public.config_area(key), true)';
    else
      rd := format('public.has_perm(%L)', area); wr := format('public.has_perm(%L, true)', area);
    end if;
    if r.tablename = 'users' and r.policyname = 'users_admin' then wr := 'false'; end if;   -- staff never write users rows directly (RPCs below)
    if r.tablename = 'audit_log' and r.cmd = 'INSERT' then wr := 'public.is_staff()'; end if;
    if r.cmd = 'SELECT' then
      q := regexp_replace(r.qual, '(public\.)?is_admin\(\)', '(public.is_admin() or ' || rd || ')', 'g');
      execute format('drop policy %I on public.%I', r.policyname, r.tablename);
      execute format('create policy %I on public.%I for select using (%s)', r.policyname, r.tablename, q);
    elsif r.cmd in ('INSERT', 'UPDATE', 'DELETE') then
      q := regexp_replace(r.qual, '(public\.)?is_admin\(\)', '(public.is_admin() or ' || wr || ')', 'g');
      wc := regexp_replace(r.with_check, '(public\.)?is_admin\(\)', '(public.is_admin() or ' || wr || ')', 'g');
      execute format('drop policy %I on public.%I', r.policyname, r.tablename);
      execute format('create policy %I on public.%I for %s %s %s', r.policyname, r.tablename, lower(r.cmd),
        case when q is not null then 'using (' || q || ')' else '' end, case when wc is not null then 'with check (' || wc || ')' else '' end);
    elsif r.cmd = 'ALL' then
      execute format('drop policy %I on public.%I', r.policyname, r.tablename);
      execute format('create policy %I on public.%I for select using (public.is_admin() or %s)', r.policyname || '_stf_r', r.tablename, rd);
      execute format('create policy %I on public.%I for insert with check (public.is_admin() or %s)', r.policyname || '_stf_i', r.tablename, wr);
      execute format('create policy %I on public.%I for update using (public.is_admin() or %s) with check (public.is_admin() or %s)', r.policyname || '_stf_u', r.tablename, wr, wr);
      execute format('create policy %I on public.%I for delete using (public.is_admin() or %s)', r.policyname || '_stf_d', r.tablename, wr);
    end if;
  end loop;
end $$;

-- ---- RPC guards ----
do $$
declare
  m jsonb := '{"admin_ban_device(p_hash text, p_ban boolean, p_reason text)":"fraud:w","admin_device_details()":"fraud",
    "admin_set_guard_exempt(p_uid text, p_days integer)":"fraud:w","admin_review_action(p_review_id text, p_action text)":"reviews:w",
    "admin_review_queue()":"reviews","admin_search_report(p_days integer)":"insights","admin_set_premium(p_uid text, p_enabled boolean, p_days integer)":"users:w",
    "admin_update_user(p_uid text, p_patch jsonb)":"users:w","approve_submission(p_id text, p_feedback text)":"submissions:w",
    "delete_message(p_table text, p_msg text)":"support:w","set_app_hold(p_app text, p_hold boolean, p_reason text)":"catalog:w",
    "api_stats(p_days integer)":"api","api_key_create(p_name text, p_contact text, p_note text, p_scopes text[], p_rate_min integer, p_rate_day integer, p_ips text[], p_origins text[], p_categories text[], p_expires timestamp with time zone)":"api:w",
    "api_key_rotate(p_id uuid)":"api:w","api_purge(p_days integer)":"api:w"}';
  k text; a text; w boolean; d text; oid_ oid;
begin
  for k, a in select * from jsonb_each_text(m) loop
    select p.oid into oid_ from pg_proc p join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname = split_part(k, '(', 1)
        and pg_get_function_identity_arguments(p.oid) = regexp_replace(k, '^[^(]*\((.*)\)$', '\1') limit 1;
    if oid_ is null then raise notice 'skip %', k; continue; end if;
    w := a like '%:w';
    d := pg_get_functiondef(oid_);
    d := regexp_replace(d, '(public\.)?is_admin\(\)', format('(public.is_admin() or public.has_perm(%L, %L))', split_part(a, ':', 1), w), 'g');
    if split_part(k, '(', 1) in ('admin_update_user', 'admin_set_premium') then
      d := replace(d, 'raise exception ''admin only''; end if;',
        'raise exception ''admin only''; end if; if not public.is_admin() and exists (select 1 from public.users x where x.uid = p_uid and (x.role = ''admin'' or x.staff_role is not null)) then raise exception ''only an administrator can change staff accounts''; end if;');
    end if;
    if split_part(k, '(', 1) = 'admin_update_user' then
      d := replace(d, 'case when p_patch ? ''role''', 'case when public.is_admin() and p_patch ? ''role''');
    end if;
    execute d;
  end loop;
end $$;

-- ---- team management (administrators only) ----
create or replace function public.staff_list() returns jsonb language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'admin only'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object('uid', u.uid, 'email', u.email, 'name', coalesce(nullif(u.display_name, ''), nullif(u.user_name, ''), u.email),
           'photo', u.profile_photo_url, 'admin', u.role = 'admin', 'staff_role', u.staff_role, 'suspended', u.is_suspended) order by (u.role = 'admin') desc, u.email)
         from public.users u where u.role = 'admin' or u.staff_role is not null), '[]'::jsonb);
end $$;
create or replace function public.staff_find(p_q text) returns jsonb language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'admin only'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object('uid', u.uid, 'email', u.email, 'name', coalesce(nullif(u.display_name, ''), nullif(u.user_name, ''), u.email),
           'photo', u.profile_photo_url, 'admin', u.role = 'admin', 'staff_role', u.staff_role))
         from (select * from public.users where length(btrim(coalesce(p_q, ''))) >= 3 and (email ilike '%' || btrim(p_q) || '%' or user_name ilike '%' || btrim(p_q) || '%') limit 8) u), '[]'::jsonb);
end $$;
create or replace function public.staff_set_role(p_uid text, p_role text) returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'admin only'; end if;
  if p_uid = public.uid() then raise exception 'You cannot change your own role.'; end if;
  if exists (select 1 from public.users where uid = p_uid and role = 'admin') then raise exception 'That person is an administrator. Change that in Users.'; end if;
  if nullif(p_role, '') is not null and not exists (select 1 from public.staff_roles where key = p_role) then raise exception 'unknown role'; end if;
  update public.users set staff_role = nullif(p_role, '') where uid = p_uid;
  if not found then raise exception 'user not found'; end if;
  insert into public.audit_log(action, admin_uid, target_type, target_id, target_name, details)
    values ('STAFF_ROLE', public.uid(), 'user', p_uid, coalesce((select email from public.users where uid = p_uid), ''), coalesce(nullif(p_role, ''), 'removed'));
end $$;
grant execute on function public.staff_list(), public.staff_find(text), public.staff_set_role(text, text) to authenticated;
