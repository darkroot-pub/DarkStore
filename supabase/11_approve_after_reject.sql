-- v17b: approving a submission that was rejected earlier.
-- The function already accepted any status; the only change is the developer-facing message:
-- a rejected submission must not keep the old rejection reason as its "Approved" feedback.
CREATE OR REPLACE FUNCTION public.approve_submission(p_id text, p_feedback text DEFAULT ''::text)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
    s public.submissions%ROWTYPE;
    a public.apps%ROWTYPE;
    new_id text;
    entry jsonb;
    upcoming boolean;
BEGIN
    IF NOT (public.is_admin() OR auth.role() = 'service_role') THEN
        RAISE EXCEPTION 'admin only';
    END IF;

    SELECT * INTO s FROM public.submissions WHERE id = p_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'submission not found';
    END IF;

    upcoming := COALESCE(s.is_upcoming, false);

    entry := jsonb_build_object(
        'version', s.version,
        'versionCode', s.version_code,
        'changelog', s.changelog,
        'apkUrl', s.apk_url,
        'date', (EXTRACT(EPOCH FROM now()) * 1000)::bigint
    );

    SELECT * INTO a FROM public.apps
    WHERE lower(package_name) = lower(s.package_name)
    LIMIT 1 FOR UPDATE;

    IF FOUND THEN
        IF a.owner_uid IS DISTINCT FROM s.submitted_by_uid THEN
            RAISE EXCEPTION 'package belongs to another developer';
        END IF;

        UPDATE public.apps SET
            name = s.name,
            version = s.version,
            version_code = s.version_code,
            apk_url = COALESCE(NULLIF(s.apk_url, ''), apk_url),
            changelog = s.changelog,
            description = COALESCE(NULLIF(s.description, ''), description),
            category = COALESCE(NULLIF(s.category, ''), category),
            logo = COALESCE(NULLIF(s.logo, ''), logo),
            screenshots = CASE WHEN cardinality(s.screenshots) > 0 THEN s.screenshots ELSE screenshots END,
            video_url = s.video_url,
            size = COALESCE(NULLIF(TRIM(s.size), ''), size, ''),
            has_ads = s.has_ads,
            is_upcoming = upcoming,
            expected_release = s.expected_release,
            is_recent = true,
            is_approved = true,
            version_history = COALESCE(version_history, '[]'::jsonb) || jsonb_build_array(entry)
        WHERE id = a.id;

        new_id := a.id;
    ELSE
        new_id := 'app_'
            || (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::bigint
            || '_' || floor(random() * 1000)::int;

        INSERT INTO public.apps (
            id, package_name, name, version, version_code, description, category, changelog,
            apk_url, logo, screenshots, video_url, expected_release, size, has_ads, is_upcoming,
            owner_uid, submitted_by, developer, is_approved, is_recent, version_history
        ) VALUES (
            new_id, s.package_name, s.name, s.version,
            GREATEST(COALESCE(s.version_code, 1), 1),
            s.description, s.category, s.changelog, s.apk_url, s.logo, s.screenshots,
            s.video_url, s.expected_release, COALESCE(NULLIF(TRIM(s.size), ''), ''),
            s.has_ads, upcoming, s.submitted_by_uid, s.submitted_by, s.developer,
            true, true, jsonb_build_array(entry)
        );
    END IF;

    UPDATE public.submissions SET
        status = 'Approved',
        feedback = COALESCE(
            NULLIF(p_feedback, ''),
            -- a rejected submission's old feedback is the rejection reason: never reuse it here
            CASE WHEN lower(COALESCE(s.status, '')) = 'rejected' THEN NULL ELSE NULLIF(s.feedback, '') END,
            'Approved! Your application matches all guidelines and has been published inside our catalog.'
        )
    WHERE id = p_id;

    RETURN new_id;
END;
$function$;

-- v17c: reviews hidden by moderation no longer count towards an app's rating.
CREATE OR REPLACE FUNCTION public.refresh_app_rating()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare aid text := coalesce(new.app_id, old.app_id);
begin
  update public.apps set rating = coalesce((select round(avg(stars)::numeric, 1)
                                            from public.reviews
                                            where app_id = aid and not coalesce(hidden, false)), 0)
  where id = aid;
  return null;
end $function$;
