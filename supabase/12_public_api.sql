-- =============================================================================
-- 12: DarkStore public API (for other stores / sites to list DarkStore's published apps).
-- Served by the Supabase edge function `store-api`; ALL rules live here and are controlled
-- from the admin app (tab "API"). Safe to re-run.
-- =============================================================================
create table if not exists public.api_keys (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  owner_contact text not null default '',
  note text not null default '',
  key_prefix text not null,
  key_hash text not null unique,
  enabled boolean not null default true,
  scopes text[] not null default array['apps','search','download'],
  rate_per_min int,
  rate_per_day int,
  allowed_ips text[] not null default '{}',
  allowed_origins text[] not null default '{}',
  allowed_categories text[] not null default '{}',
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  last_used_at timestamptz,
  total_requests bigint not null default 0
);
create table if not exists public.api_app_rules (
  app_id text primary key,
  hidden boolean not null default false,
  listed boolean not null default false,
  hide_download boolean not null default false,
  note text not null default '',
  updated_at timestamptz not null default now()
);
create table if not exists public.api_usage (
  id bigserial primary key,
  at timestamptz not null default now(),
  key_id uuid references public.api_keys(id) on delete cascade,
  ip text,
  path text,
  status int,
  ms int
);
create index if not exists api_usage_key_at on public.api_usage (key_id, at desc);
create index if not exists api_usage_ip_at  on public.api_usage (ip, at desc) where key_id is null;
create index if not exists api_usage_at     on public.api_usage (at);

alter table public.api_keys enable row level security;
alter table public.api_app_rules enable row level security;
alter table public.api_usage enable row level security;
drop policy if exists api_keys_admin on public.api_keys;
drop policy if exists api_rules_admin on public.api_app_rules;
drop policy if exists api_usage_admin on public.api_usage;
create policy api_keys_admin  on public.api_keys      for all using (public.is_admin()) with check (public.is_admin());
create policy api_rules_admin on public.api_app_rules for all using (public.is_admin()) with check (public.is_admin());
create policy api_usage_admin on public.api_usage     for all using (public.is_admin()) with check (public.is_admin());
revoke all on public.api_keys, public.api_app_rules, public.api_usage from anon;

-- ---- settings (app_config key "apiConfig"; missing keys fall back to these defaults) ----
create or replace function public.api_cfg() returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'enabled', true, 'requireKey', true, 'maintenanceMessage', 'The DarkStore API is temporarily switched off.',
    'ratePerMin', 60, 'ratePerDay', 5000, 'publicRatePerMin', 20, 'publicRatePerDay', 500,
    'maxPageSize', 100, 'defaultPageSize', 20, 'expose', 'all', 'excludeCategories', '[]'::jsonb,
    'includeDownload', true, 'siteUrl', '',
    'fields', jsonb_build_object('description', true, 'screenshots', true, 'video', true, 'changelog', true),
    'attribution', ''
  ) || coalesce((select value from public.app_config where key = 'apiConfig'), '{}'::jsonb)
$$;

create or replace function public.api_item(j jsonb, can_dl boolean, cfg jsonb) returns jsonb
language sql stable set search_path = public as $$
  select jsonb_build_object(
      'id', j->>'id', 'packageName', coalesce(j->>'package_name',''), 'name', j->>'name',
      'developer', coalesce(nullif(j->>'developer_name',''), j->>'developer', ''),
      'category', coalesce(j->>'category',''), 'version', coalesce(j->>'version',''),
      'versionCode', coalesce(nullif(j->>'version_code','')::bigint, 0), 'size', coalesce(j->>'size',''),
      'rating', coalesce(nullif(j->>'rating','')::numeric, 0), 'logo', coalesce(j->>'logo',''),
      'hasAds', coalesce((j->>'has_ads')::boolean, false), 'featured', coalesce((j->>'is_featured')::boolean, false),
      'createdAt', j->>'created_at', 'updatedAt', j->>'updated_at',
      'storeUrl', case when coalesce(cfg->>'siteUrl','') <> '' then (cfg->>'siteUrl') || '/app.html?id=' || (j->>'id') end)
    || case when coalesce((cfg->'fields'->>'description')::boolean, true)
            then jsonb_build_object('description', left(coalesce(j->>'description',''), 4000)) else '{}'::jsonb end
    || case when coalesce((cfg->'fields'->>'screenshots')::boolean, true)
            then jsonb_build_object('screenshots', coalesce(j->'screenshots', '[]'::jsonb)) else '{}'::jsonb end
    || case when coalesce((cfg->'fields'->>'video')::boolean, true)
            then jsonb_build_object('video', coalesce(j->>'video_url','')) else '{}'::jsonb end
    || case when coalesce((cfg->'fields'->>'changelog')::boolean, true)
            then jsonb_build_object('changelog', left(coalesce(j->>'changelog',''), 2000)) else '{}'::jsonb end
    || case when can_dl and coalesce(j->>'apk_url','') <> ''
            then jsonb_build_object('download', jsonb_build_object('url', j->>'apk_url')) else '{}'::jsonb end
$$;
revoke all on function public.api_item(jsonb, boolean, jsonb) from public, anon, authenticated;

-- ---- the gateway: auth, limits, logging and data in ONE call (service role only) ----
create or replace function public.api_handle(p_key_hash text, p_ip text, p_origin text, p_route text, p_params jsonb)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare
  cfg jsonb := public.api_cfg();
  k public.api_keys;
  has_key boolean := coalesce(p_key_hash, '') <> '';
  t0 timestamptz := clock_timestamp();
  lim_min int; lim_day int; used_min int; used_day int;
  scope text; q text; cat text; srt text; lim int; off int; since timestamptz;
  excl text[]; expose text := cfg->>'expose'; can_dl boolean;
  items jsonb; total int; body jsonb; hdr jsonb; pkg text; rec record;

begin
  if not coalesce((cfg->>'enabled')::boolean, true) then
    return jsonb_build_object('status', 503, 'error', jsonb_build_object('code', 'api_disabled', 'message', cfg->>'maintenanceMessage'));
  end if;

  if has_key then
    select * into k from public.api_keys where key_hash = p_key_hash;
    if not found or not k.enabled or (k.expires_at is not null and k.expires_at < now()) then
      insert into public.api_usage(ip, path, status, ms) values (left(p_ip, 64), left(p_route, 80), 401, 0);
      return jsonb_build_object('status', 401, 'error', jsonb_build_object('code', 'invalid_key', 'message', 'The API key is missing, disabled or expired.'));
    end if;
    if cardinality(k.allowed_ips) > 0 and not (coalesce(p_ip, '') = any (k.allowed_ips)) then
      insert into public.api_usage(key_id, ip, path, status, ms) values (k.id, left(p_ip, 64), left(p_route, 80), 403, 0);
      return jsonb_build_object('status', 403, 'error', jsonb_build_object('code', 'ip_not_allowed', 'message', 'This key is not allowed from this IP address.'));
    end if;
    if cardinality(k.allowed_origins) > 0 and not (coalesce(p_origin, '') = any (k.allowed_origins)) then
      insert into public.api_usage(key_id, ip, path, status, ms) values (k.id, left(p_ip, 64), left(p_route, 80), 403, 0);
      return jsonb_build_object('status', 403, 'error', jsonb_build_object('code', 'origin_not_allowed', 'message', 'This key is not allowed from this website.'));
    end if;
  elsif coalesce((cfg->>'requireKey')::boolean, true) then
    return jsonb_build_object('status', 401, 'error', jsonb_build_object('code', 'key_required', 'message', 'Send your API key in the X-API-Key header.'));
  end if;

  q := nullif(btrim(coalesce(p_params->>'q', '')), '');
  scope := case when q is not null then 'search' else 'apps' end;
  if has_key and not (scope = any (k.scopes)) then
    return jsonb_build_object('status', 403, 'error', jsonb_build_object('code', 'scope', 'message', 'This key may not use "' || scope || '".'));
  end if;
  can_dl := coalesce((cfg->>'includeDownload')::boolean, true) and (not has_key or 'download' = any (k.scopes));

  -- rate limits (429 rows are logged but not counted, so a blocked client recovers by itself)
  if has_key then
    lim_min := coalesce(k.rate_per_min, (cfg->>'ratePerMin')::int);
    lim_day := coalesce(k.rate_per_day, (cfg->>'ratePerDay')::int);
    select count(*) filter (where at > now() - interval '1 minute'), count(*) into used_min, used_day
      from public.api_usage where key_id = k.id and at > now() - interval '1 day' and status <> 429;
  else
    lim_min := (cfg->>'publicRatePerMin')::int;
    lim_day := (cfg->>'publicRatePerDay')::int;
    select count(*) filter (where at > now() - interval '1 minute'), count(*) into used_min, used_day
      from public.api_usage where key_id is null and ip = left(p_ip, 64) and at > now() - interval '1 day' and status <> 429;
  end if;
  hdr := jsonb_build_object('limitMinute', lim_min, 'remainingMinute', greatest(lim_min - used_min - 1, 0),
                            'limitDay', lim_day, 'remainingDay', greatest(lim_day - used_day - 1, 0));
  if used_min >= lim_min or used_day >= lim_day then
    insert into public.api_usage(key_id, ip, path, status, ms) values (case when has_key then k.id end, left(p_ip, 64), left(p_route, 80), 429, 0);
    return jsonb_build_object('status', 429, 'retryAfter', case when used_min >= lim_min then 60 else 3600 end, 'limits', hdr,
      'error', jsonb_build_object('code', 'rate_limited', 'message', 'Too many requests. Slow down and retry later.'));
  end if;

  excl := array(select jsonb_array_elements_text(coalesce(cfg->'excludeCategories', '[]'::jsonb)));

  if p_route = 'categories' then
    select coalesce(jsonb_agg(jsonb_build_object('name', c, 'count', n) order by n desc, c), '[]'::jsonb) into items from (
      select coalesce(a.category, '') c, count(*) n from public.apps_public a left join public.api_app_rules r on r.app_id = a.id
      where a.is_approved and not a.is_suspended and not a.is_on_hold and not a.is_upcoming and coalesce(a.name, '') <> ''
        and not coalesce(r.hidden, false) and (expose = 'all' or coalesce(r.listed, false))
        and not (coalesce(a.category, '') = any (excl))
        and (not has_key or cardinality(k.allowed_categories) = 0 or a.category = any (k.allowed_categories))
      group by 1) s where c <> '';
    body := jsonb_build_object('data', items);
  else
    cat := nullif(btrim(coalesce(p_params->>'category', '')), '');
    srt := coalesce(p_params->>'sort', 'updated');
    pkg := nullif(btrim(coalesce(p_params->>'package', '')), '');
    begin
      since := nullif(p_params->>'updated_since', '')::timestamptz;
    exception when others then
      return jsonb_build_object('status', 400, 'limits', hdr, 'error', jsonb_build_object('code', 'bad_request', 'message', 'updated_since must be an ISO date, e.g. 2026-01-31T00:00:00Z'));
    end;
    lim := least(greatest(coalesce(nullif(p_params->>'limit', '')::int, (cfg->>'defaultPageSize')::int), 1), (cfg->>'maxPageSize')::int);
    off := greatest(coalesce(nullif(p_params->>'offset', '')::int, 0), 0);

    create temp table if not exists _api_sel (j jsonb, hd boolean, t timestamptz, r numeric, n text, c timestamptz) on commit drop;
    delete from _api_sel;
    insert into _api_sel
    select to_jsonb(a), coalesce(r.hide_download, false), a.updated_at, coalesce(a.rating, 0), lower(a.name), a.created_at
    from public.apps_public a left join public.api_app_rules r on r.app_id = a.id
    where a.is_approved and not a.is_suspended and not a.is_on_hold and not a.is_upcoming and coalesce(a.name, '') <> ''
      and not coalesce(r.hidden, false) and (expose = 'all' or coalesce(r.listed, false))
      and not (coalesce(a.category, '') = any (excl))
      and (not has_key or cardinality(k.allowed_categories) = 0 or a.category = any (k.allowed_categories))
      and (cat is null or lower(a.category) = lower(cat))
      and (pkg is null or a.package_name = pkg)
      and (since is null or a.updated_at >= since)
      and (q is null or a.name ilike '%' || replace(replace(q, '%', ''), '_', '') || '%'
           or a.package_name ilike '%' || replace(replace(q, '%', ''), '_', '') || '%'
           or coalesce(a.developer_name, a.developer, '') ilike '%' || replace(replace(q, '%', ''), '_', '') || '%'
           or coalesce(a.description, '') ilike '%' || replace(replace(q, '%', ''), '_', '') || '%');
    select count(*) into total from _api_sel;

    if p_route = 'app' then
      select * into rec from _api_sel limit 1;
      if not found then
        insert into public.api_usage(key_id, ip, path, status, ms) values (case when has_key then k.id end, left(p_ip, 64), left(p_route, 80), 404, 0);
        return jsonb_build_object('status', 404, 'limits', hdr, 'error', jsonb_build_object('code', 'not_found', 'message', 'No published app with this package name.'));
      end if;
      body := jsonb_build_object('data', public.api_item(rec.j, can_dl and not rec.hd, cfg));
    else
      select coalesce(jsonb_agg(public.api_item(j, can_dl and not hd, cfg) order by rn), '[]'::jsonb) into items from (
        select j, hd, row_number() over (order by
               case srt when 'rating' then -r end, case srt when 'name' then n end,
               case srt when 'new' then c end desc, case when srt not in ('rating', 'name', 'new') then t end desc nulls last, n) rn
        from _api_sel) s where rn > off and rn <= off + lim;
      body := jsonb_build_object('data', items, 'meta', jsonb_build_object('total', total, 'limit', lim, 'offset', off,
                'count', jsonb_array_length(items), 'hasMore', off + lim < total));
    end if;
  end if;

  if coalesce(cfg->>'attribution', '') <> '' then body := body || jsonb_build_object('attribution', cfg->>'attribution'); end if;
  insert into public.api_usage(key_id, ip, path, status, ms)
    values (case when has_key then k.id end, left(p_ip, 64), left(p_route, 80), 200, (extract(milliseconds from clock_timestamp() - t0))::int);
  if has_key then update public.api_keys set last_used_at = now(), total_requests = total_requests + 1 where id = k.id; end if;
  if random() < 0.01 then delete from public.api_usage where at < now() - interval '45 days'; end if;
  return jsonb_build_object('status', 200, 'body', body, 'limits', hdr);
end $$;
revoke all on function public.api_handle(text, text, text, text, jsonb) from public, anon, authenticated;
grant execute on function public.api_handle(text, text, text, text, jsonb) to service_role;

-- ---- admin helpers ----
create or replace function public.api_key_create(p_name text, p_contact text default '', p_note text default '',
    p_scopes text[] default array['apps','search','download'], p_rate_min int default null, p_rate_day int default null,
    p_ips text[] default '{}', p_origins text[] default '{}', p_categories text[] default '{}', p_expires timestamptz default null)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare raw text; kid uuid;
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  if coalesce(btrim(p_name), '') = '' then raise exception 'name required'; end if;
  raw := 'dsk_' || encode(gen_random_bytes(24), 'hex');
  insert into public.api_keys(name, owner_contact, note, key_prefix, key_hash, scopes, rate_per_min, rate_per_day, allowed_ips, allowed_origins, allowed_categories, expires_at)
  values (btrim(p_name), coalesce(p_contact, ''), coalesce(p_note, ''), left(raw, 10), encode(digest(raw, 'sha256'), 'hex'), p_scopes, p_rate_min, p_rate_day,
          coalesce(p_ips, '{}'), coalesce(p_origins, '{}'), coalesce(p_categories, '{}'), p_expires) returning id into kid;
  return jsonb_build_object('id', kid, 'key', raw);
end $$;

create or replace function public.api_key_rotate(p_id uuid) returns jsonb
language plpgsql security definer set search_path = public, extensions as $$
declare raw text;
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  raw := 'dsk_' || encode(gen_random_bytes(24), 'hex');
  update public.api_keys set key_prefix = left(raw, 10), key_hash = encode(digest(raw, 'sha256'), 'hex') where id = p_id;
  if not found then raise exception 'unknown key'; end if;
  return jsonb_build_object('id', p_id, 'key', raw);
end $$;

create or replace function public.api_stats(p_days int default 7) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare d int := least(greatest(coalesce(p_days, 7), 1), 45);
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  return jsonb_build_object(
    'today', (select count(*) from api_usage where at > date_trunc('day', now())),
    'lastHour', (select count(*) from api_usage where at > now() - interval '1 hour'),
    'errorsToday', (select count(*) from api_usage where at > date_trunc('day', now()) and status >= 400),
    'limitedToday', (select count(*) from api_usage where at > date_trunc('day', now()) and status = 429),
    'keys', (select count(*) from api_keys), 'activeKeys', (select count(*) from api_keys where enabled and (expires_at is null or expires_at > now())),
    'daily', (select coalesce(jsonb_agg(jsonb_build_object('day', to_char(g, 'YYYY-MM-DD'),
                'n', (select count(*) from api_usage u where u.at >= g and u.at < g + interval '1 day'),
                'err', (select count(*) from api_usage u where u.at >= g and u.at < g + interval '1 day' and status >= 400)) order by g), '[]'::jsonb)
              from generate_series(date_trunc('day', now()) - ((d - 1) || ' days')::interval, date_trunc('day', now()), interval '1 day') g),
    'perKey', (select coalesce(jsonb_agg(x order by (x->>'n')::int desc), '[]'::jsonb) from (
                select jsonb_build_object('id', k.id, 'name', k.name, 'n', count(u.id), 'err', count(u.id) filter (where u.status >= 400),
                       'avgMs', coalesce(round(avg(u.ms) filter (where u.status = 200)), 0)) x
                from api_keys k left join api_usage u on u.key_id = k.id and u.at > now() - (d || ' days')::interval group by k.id, k.name) s),
    'paths', (select coalesce(jsonb_agg(jsonb_build_object('path', path, 'n', n) order by n desc), '[]'::jsonb) from (
                select path, count(*) n from api_usage where at > now() - (d || ' days')::interval group by path order by n desc limit 8) p));
end $$;

create or replace function public.api_purge(p_days int default 30) returns int
language plpgsql security definer set search_path = public as $$
declare n int;
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  delete from api_usage where at < now() - (greatest(coalesce(p_days, 30), 0) || ' days')::interval;
  get diagnostics n = row_count; return n;
end $$;
grant execute on function public.api_key_create(text, text, text, text[], int, int, text[], text[], text[], timestamptz) to authenticated;
grant execute on function public.api_key_rotate(uuid), public.api_stats(int), public.api_purge(int) to authenticated;
revoke execute on function public.api_key_create(text, text, text, text[], int, int, text[], text[], text[], timestamptz), public.api_key_rotate(uuid), public.api_stats(int), public.api_purge(int) from anon;
