# v19 – Public API for other stores + admin control

NOT COMPILED here (no Flutter SDK). Database + edge function are LIVE already (applied/deployed from this session):
migration `v19_public_api`, edge function `store-api`. File copy: `supabase/12_public_api.sql` (safe to re-run).

## API (what other stores use)
Base: https://mjhuwxmpbxmqgtfkqvsc.supabase.co/functions/v1/store-api/v1  (header `X-API-Key`)
GET /apps (q, category, sort, limit, offset, updated_since) · GET /apps/{package} · GET /categories · GET /status
Docs page for developers: `api.html` on the website (link added in the footer).

## Admin app: new tab "API"
* Overview: master ON/OFF (+ message), "require key", requests today/hour, errors, rate-limited, per-day chart, usage per key, endpoints used, copy URL/example.
* Keys: create (shown once), pause, edit, rotate, delete. Per key: requests/min and /day, search + download permission, allowed IPs, allowed websites, allowed categories, expiry.
* Apps: all live apps or "only apps I pick"; per-app on/off and "hide download link"; show/hide all.
* Settings: default + no-key limits, page sizes, include download links, fields (description, screenshots, video, what's new), hidden categories, website address, credit line.
* Log: last 80 requests, clean old rows.
Only approved, non-suspended, non-hold, non-upcoming apps can ever appear.

## Notes
* Keys are stored as SHA-256 hashes; the plain key exists only in the "shown once" dialog.
* Rate limits are counted from the request log (blocked 429 requests are not counted). Log rows older than 45 days are removed automatically.
* Google Drive style APK links are returned as the app's stored link (a page, not a direct file).
