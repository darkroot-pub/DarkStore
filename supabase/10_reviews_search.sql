-- v17: better reviews (helpful votes, developer replies, reports, verified-install flag) + search
-- (search log, trending). Already applied to the DarkStore project. Safe to re-run.

-- ───────────── reviews ─────────────
alter table public.reviews
  add column if not exists helpful_count int not null default 0,
  add column if not exists installed boolean not null default false,
  add column if not exists dev_reply text not null default '',
  add column if not exists dev_reply_at timestamptz,
  add column if not exists hidden boolean not null default false;

create table if not exists public.review_votes (
  review_id text not null references public.reviews(id) on delete cascade,
  user_id text not null,
  created_at timestamptz not null default now(),
  primary key (review_id, user_id)
);
create table if not exists public.review_reports (
  review_id text not null references public.reviews(id) on delete cascade,
  reporter_id text not null,
  reason text not null default '',
  created_at timestamptz not null default now(),
  primary key (review_id, reporter_id)
);
alter table public.review_votes enable row level security;
alter table public.review_reports enable row level security;
revoke all on public.review_votes, public.review_reports from anon;
drop policy if exists review_votes_own on public.review_votes;
create policy review_votes_own on public.review_votes for select to authenticated using (user_id = uid());
drop policy if exists review_reports_admin on public.review_reports;
create policy review_reports_admin on public.review_reports for select to authenticated using (is_admin());
-- writes happen only through the functions below (no insert/update/delete policy for clients)

-- Counters, replies and the hidden flag can only be changed by the functions below or an admin;
-- review text length rules come from app_config.reviewConfig. Never blocks on a settings problem.
create or replace function public.reviews_guard() returns trigger language plpgsql as $f$
declare cfg jsonb; mn int := 0; mx int := 0; adm boolean := false; priv boolean;
begin
  priv := coalesce(current_setting('app.review_priv', true), '') = '1';
  if not priv then
    begin adm := coalesce(public.is_admin(), false); exception when others then adm := false; end;
  end if;
  if not priv and not adm then
    if tg_op = 'INSERT' then
      new.helpful_count := 0; new.dev_reply := ''; new.dev_reply_at := null; new.hidden := false;
    else
      new.helpful_count := old.helpful_count; new.dev_reply := old.dev_reply;
      new.dev_reply_at := old.dev_reply_at; new.hidden := old.hidden;
    end if;
  end if;
  if not priv and not adm and (tg_op = 'INSERT' or new.msg is distinct from old.msg) then
    begin
      select value into cfg from public.app_config where key = 'reviewConfig';
      if cfg is not null then
        mn := coalesce(nullif(cfg->>'minLength','')::int, 0);
        mx := coalesce(nullif(cfg->>'maxLength','')::int, 0);
        if coalesce((cfg->>'requireText')::boolean, false) and length(trim(coalesce(new.msg,''))) = 0 then
          raise exception 'Please write a few words about the app.' using errcode = 'P0001';
        end if;
        if length(trim(coalesce(new.msg,''))) > 0 and mn > 0 and length(trim(new.msg)) < mn then
          raise exception 'Your review is too short (at least % characters).', mn using errcode = 'P0001';
        end if;
        if mx > 0 and length(new.msg) > mx then
          raise exception 'Your review is too long (at most % characters).', mx using errcode = 'P0001';
        end if;
      end if;
    exception
      when sqlstate 'P0001' then raise;
      when others then null;
    end;
  end if;
  return new;
end $f$;
drop trigger if exists reviews_guard_t on public.reviews;
create trigger reviews_guard_t before insert or update on public.reviews
  for each row execute function public.reviews_guard();

create or replace function public.review_cfg(k text, d boolean) returns boolean
language plpgsql stable security definer set search_path = public as $f$
declare v jsonb;
begin
  select value into v from public.app_config where key = 'reviewConfig';
  if v is null or v->>k is null then return d; end if;
  return (v->>k)::boolean;
exception when others then return d;
end $f$;

create or replace function public.toggle_review_helpful(p_review_id text) returns jsonb
language plpgsql security definer set search_path = public as $f$
declare me text := public.uid(); owner text; n int; had boolean;
begin
  if me is null then raise exception 'Sign in to vote.' using errcode = 'P0001'; end if;
  if not public.review_cfg('allowHelpful', true) then raise exception 'Voting is turned off.' using errcode = 'P0001'; end if;
  select user_id into owner from public.reviews where id = p_review_id;
  if not found then raise exception 'This review no longer exists.' using errcode = 'P0001'; end if;
  if owner = me then raise exception 'You cannot vote on your own review.' using errcode = 'P0001'; end if;
  delete from public.review_votes where review_id = p_review_id and user_id = me returning true into had;
  if had is null then insert into public.review_votes(review_id, user_id) values (p_review_id, me); end if;
  select count(*) into n from public.review_votes where review_id = p_review_id;
  perform set_config('app.review_priv', '1', true);
  update public.reviews set helpful_count = n where id = p_review_id;
  perform set_config('app.review_priv', '', true);
  return jsonb_build_object('helpful', had is null, 'count', n);
end $f$;

create or replace function public.reply_to_review(p_review_id text, p_reply text) returns jsonb
language plpgsql security definer set search_path = public as $f$
declare me text := public.uid(); aid text; owner text; adm boolean; txt text := left(trim(coalesce(p_reply, '')), 600);
begin
  if me is null then raise exception 'Sign in first.' using errcode = 'P0001'; end if;
  select app_id into aid from public.reviews where id = p_review_id;
  if not found then raise exception 'This review no longer exists.' using errcode = 'P0001'; end if;
  select owner_uid into owner from public.apps where id = aid;
  adm := coalesce(public.is_admin(), false);
  if not adm and (owner is null or owner = '' or owner <> me) then
    raise exception 'Only the developer of this app can reply.' using errcode = 'P0001';
  end if;
  if not adm and not public.review_cfg('allowReplies', true) then
    raise exception 'Replies are turned off.' using errcode = 'P0001';
  end if;
  perform set_config('app.review_priv', '1', true);
  update public.reviews set dev_reply = txt, dev_reply_at = case when txt = '' then null else now() end where id = p_review_id;
  perform set_config('app.review_priv', '', true);
  return jsonb_build_object('ok', true, 'reply', txt);
end $f$;

create or replace function public.report_review(p_review_id text, p_reason text default '') returns jsonb
language plpgsql security definer set search_path = public as $f$
declare me text := public.uid(); owner text; n int; thr int := 3; cfg jsonb; hid boolean := false;
begin
  if me is null then raise exception 'Sign in to report.' using errcode = 'P0001'; end if;
  if not public.review_cfg('allowReports', true) then raise exception 'Reporting is turned off.' using errcode = 'P0001'; end if;
  select user_id into owner from public.reviews where id = p_review_id;
  if not found then raise exception 'This review no longer exists.' using errcode = 'P0001'; end if;
  if owner = me then raise exception 'You cannot report your own review.' using errcode = 'P0001'; end if;
  insert into public.review_reports(review_id, reporter_id, reason)
    values (p_review_id, me, left(coalesce(p_reason, ''), 200)) on conflict do nothing;
  select count(*) into n from public.review_reports where review_id = p_review_id;
  begin
    select value into cfg from public.app_config where key = 'reviewConfig';
    thr := coalesce(nullif(cfg->>'autoHideReports', '')::int, 3);
  exception when others then thr := 3; end;
  if thr > 0 and n >= thr then
    perform set_config('app.review_priv', '1', true);
    update public.reviews set hidden = true where id = p_review_id;
    perform set_config('app.review_priv', '', true);
    hid := true;
  end if;
  return jsonb_build_object('reported', true, 'reports', n, 'hidden', hid);
end $f$;

-- admin moderation (hide / unhide / clear reports / remove the developer reply / delete)
create or replace function public.admin_review_action(p_review_id text, p_action text) returns jsonb
language plpgsql security definer set search_path = public as $f$
begin
  if not coalesce(public.is_admin(), false) then raise exception 'Admins only.' using errcode = 'P0001'; end if;
  perform set_config('app.review_priv', '1', true);
  if p_action = 'hide' then update public.reviews set hidden = true where id = p_review_id;
  elsif p_action = 'unhide' then
    update public.reviews set hidden = false where id = p_review_id;
    delete from public.review_reports where review_id = p_review_id;
  elsif p_action = 'clear_reports' then delete from public.review_reports where review_id = p_review_id;
  elsif p_action = 'clear_reply' then update public.reviews set dev_reply = '', dev_reply_at = null where id = p_review_id;
  elsif p_action = 'delete' then delete from public.reviews where id = p_review_id;
  else raise exception 'Unknown action.' using errcode = 'P0001'; end if;
  perform set_config('app.review_priv', '', true);
  return jsonb_build_object('ok', true);
end $f$;

-- admin: reviews that were reported or hidden, with the reasons
create or replace function public.admin_review_queue() returns jsonb
language plpgsql stable security definer set search_path = public as $f$
begin
  if not coalesce(public.is_admin(), false) then raise exception 'Admins only.' using errcode = 'P0001'; end if;
  return coalesce((
    select jsonb_agg(x order by (x->>'reports')::int desc, x->>'createdAt' desc) from (
      select jsonb_build_object(
        'id', r.id, 'appId', r.app_id, 'app', coalesce(a.name, ''), 'user', r.user_name, 'userId', r.user_id,
        'stars', r.stars, 'msg', r.msg, 'hidden', r.hidden, 'helpful', r.helpful_count,
        'reply', r.dev_reply, 'createdAt', r.created_at,
        'reports', (select count(*) from public.review_reports q where q.review_id = r.id),
        'reasons', coalesce((select jsonb_agg(q.reason) from public.review_reports q where q.review_id = r.id and q.reason <> ''), '[]'::jsonb)
      ) as x
      from public.reviews r left join public.apps a on a.id = r.app_id
      where r.hidden or exists (select 1 from public.review_reports q where q.review_id = r.id)
      limit 200
    ) t), '[]'::jsonb);
end $f$;

revoke all on function public.toggle_review_helpful(text), public.reply_to_review(text, text), public.report_review(text, text),
  public.admin_review_action(text, text), public.admin_review_queue(), public.review_cfg(text, boolean) from public, anon;
grant execute on function public.toggle_review_helpful(text), public.reply_to_review(text, text), public.report_review(text, text),
  public.admin_review_action(text, text), public.admin_review_queue() to authenticated;

-- ───────────── search ─────────────
create table if not exists public.search_events (
  id bigint generated always as identity primary key,
  term text not null,
  results int not null default 0,
  uid text,
  created_at timestamptz not null default now()
);
create index if not exists search_events_time on public.search_events (created_at desc);
create index if not exists search_events_term on public.search_events (term);
alter table public.search_events enable row level security;
revoke all on public.search_events from anon;
drop policy if exists search_events_admin on public.search_events;
create policy search_events_admin on public.search_events for select to authenticated using (is_admin());

-- One search from the store app. Quiet and rate limited; never raises.
create or replace function public.log_search(p_term text, p_results int default 0) returns boolean
language plpgsql security definer set search_path = public as $f$
declare me text := public.uid(); t text := lower(trim(regexp_replace(coalesce(p_term, ''), '\s+', ' ', 'g')));
begin
  if me is null or length(t) < 2 or length(t) > 60 then return false; end if;
  if (select count(*) from public.search_events where uid = me and created_at > now() - interval '1 hour') >= 60 then return false; end if;
  if exists (select 1 from public.search_events where uid = me and term = t and created_at > now() - interval '10 minutes') then return false; end if;
  insert into public.search_events(term, results, uid) values (t, greatest(coalesce(p_results, 0), 0), me);
  if random() < 0.02 then delete from public.search_events where created_at < now() - interval '90 days'; end if;
  return true;
exception when others then return false;
end $f$;

-- Terms many different people searched that found something (shown as "Trending searches").
create or replace function public.trending_searches(p_days int default 7, p_limit int default 10, p_min_users int default 2) returns jsonb
language sql stable security definer set search_path = public as $f$
  select coalesce(jsonb_agg(term order by users desc, n desc), '[]'::jsonb) from (
    select term, count(distinct uid) users, count(*) n
    from public.search_events
    where created_at > now() - make_interval(days => least(greatest(p_days, 1), 60)) and results > 0
    group by term
    having count(distinct uid) >= greatest(p_min_users, 1)
    order by count(distinct uid) desc, count(*) desc
    limit least(greatest(p_limit, 1), 30)
  ) t
$f$;

-- Admin: what people search, and what they search without finding anything.
create or replace function public.admin_search_report(p_days int default 14) returns jsonb
language plpgsql stable security definer set search_path = public as $f$
begin
  if not coalesce(public.is_admin(), false) then raise exception 'Admins only.' using errcode = 'P0001'; end if;
  return jsonb_build_object(
    'total', (select count(*) from public.search_events where created_at > now() - make_interval(days => least(greatest(p_days, 1), 90))),
    'top', coalesce((select jsonb_agg(jsonb_build_object('term', term, 'searches', n, 'users', u, 'found', f) order by n desc) from (
        select term, count(*) n, count(distinct uid) u, round(avg(results))::int f from public.search_events
        where created_at > now() - make_interval(days => least(greatest(p_days, 1), 90)) group by term order by count(*) desc limit 40) a), '[]'::jsonb),
    'nothing', coalesce((select jsonb_agg(jsonb_build_object('term', term, 'searches', n, 'users', u) order by n desc) from (
        select term, count(*) n, count(distinct uid) u from public.search_events
        where created_at > now() - make_interval(days => least(greatest(p_days, 1), 90)) and results = 0
        group by term order by count(*) desc limit 40) b), '[]'::jsonb));
end $f$;

revoke all on function public.log_search(text, int), public.trending_searches(int, int, int), public.admin_search_report(int) from public, anon;
grant execute on function public.log_search(text, int), public.trending_searches(int, int, int), public.admin_search_report(int) to authenticated;

insert into public.app_config(key, value) values
 ('searchConfig', '{"fuzzy":true,"searchDescriptions":true,"showSuggestions":true,"showTrending":true,"autoTrending":true,"logSearches":true,"maxResults":120,"minUsers":2,"trending":"","synonyms":"","hint":""}'::jsonb),
 ('reviewConfig', '{"enabled":true,"requireText":false,"minLength":0,"maxLength":600,"allowHelpful":true,"allowReplies":true,"allowReports":true,"autoHideReports":3,"showVerified":true,"requireInstalled":false,"defaultSort":"helpful"}'::jsonb)
on conflict (key) do nothing;
