-- v16 (already applied to the DarkStore project). Safe to re-run.
insert into public.app_config(key, value) values
 ('limitsConfig', '{"maxPending":3,"maxPerDay":5}'::jsonb),
 ('chatRules', '{"enabled":true,"maxLength":1000,"slowModeSeconds":0,"blockLinks":false,"bannedWords":[],"blockedMessage":""}'::jsonb),
 ('homeLayout', '{}'::jsonb),
 ('shareConfig', '{}'::jsonb),
 ('recommendConfig', '{"enabled":true,"showForYou":true,"showBecause":true,"showSimilar":true,"showTrending":true,"maxItems":10,"hideInstalled":true,"maxPerCategory":4,"wCategory":3,"wDeveloper":2,"wText":2,"wRating":1,"wFeatured":1,"wPopular":1,"wFresh":1,"wSaved":1.5,"explore":10,"pinned":[],"blocked":[]}'::jsonb)
on conflict (key) do nothing;
-- Functions enforce_submission_limits() and enforce_chat_rules() (+ trigger chat_rules on chat_messages):
-- see the migrations v16_limits_chat_rules and v16_chat_rules_wordlist_text in Supabase.
