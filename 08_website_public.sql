-- =============================================================================
-- 08: public, read-only app catalog for the WEBSITE (anonymous visitors, no login).
-- Run once in Supabase -> SQL Editor. Safe to re-run.
-- Only live apps (approved, not suspended / on hold / upcoming) and only harmless
-- columns are exposed. Nothing else in your database becomes readable by anon.
-- =============================================================================
create or replace function public.public_apps() returns jsonb
language sql stable security definer set search_path = public as $$
  with f as (
    select j,
           case when j->>'rating' ~ '^[0-9]+(\.[0-9]+)?$' then (j->>'rating')::numeric else 0 end as r
    from (select to_jsonb(a) as j from public.apps_public a) t
    where coalesce((j->>'is_approved')::boolean,  true)
      and not coalesce((j->>'is_suspended')::boolean, false)
      and not coalesce((j->>'is_on_hold')::boolean,   false)
      and not coalesce((j->>'is_upcoming')::boolean,  false)
      and coalesce(j->>'name', '') <> ''
    order by r desc, j->>'name'
    limit 200
  )
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',          j->>'id',
    'name',        j->>'name',
    'developer',   coalesce(nullif(j->>'developer_name', ''), j->>'developer', ''),
    'category',    coalesce(j->>'category', ''),
    'rating',      coalesce(j->>'rating', ''),
    'logo',        coalesce(j->>'logo', ''),
    'version',     coalesce(j->>'version', ''),
    'size',        coalesce(j->>'size', ''),
    'description', left(coalesce(j->>'description', ''), 1200),
    'screenshots', coalesce(j->'screenshots', '[]'::jsonb),
    'packageName', coalesce(j->>'package_name', '')
  ) order by r desc, j->>'name'), '[]'::jsonb)
  from f
$$;

create or replace function public.public_app(p_id text) returns jsonb
language sql stable security definer set search_path = public as $$
  select x from jsonb_array_elements(public.public_apps()) x where x->>'id' = p_id limit 1
$$;

revoke all on function public.public_apps()      from public;
revoke all on function public.public_app(text)   from public;
grant execute on function public.public_apps()    to anon, authenticated;
grant execute on function public.public_app(text) to anon, authenticated;
