/* DarkRoot Store website: shared loader. Nothing about the APK is written in the HTML.
   - APK link + version  : Firebase Realtime Database -> DarkStoreUpdate (same value the Android app's update dialog uses)
   - App catalog / pages : Supabase, through the public read-only functions public_apps() / public_app() (08_website_public.sql)
   - Invite page         : the notifier server (referral). Fill SUPABASE + ANON below. */
window.DS = (function () {
  var C = { RTDB: "https://dark-store-6836d-default-rtdb.asia-southeast1.firebasedatabase.app/", BACKEND: "https://darkstore.bot-keep.xyz", PACKAGE: "com.darkstore.darkroot", SCHEME: "darkstore",
    SUPABASE: "__SUPABASE_URL__",      /* filled in at deploy time from the GitHub secret SUPABASE_URL */
    ANON: "__SUPABASE_ANON_KEY__"      /* filled in from SUPABASE_ANON_KEY (publishable/anon key; NEVER the secret/service key) */ };
  var KEY = "ds_release_v1";
  function get(path, ms) {
    var c = ("AbortController" in window) ? new AbortController() : null, t = setTimeout(function () { if (c) c.abort(); }, ms || 8000);
    return fetch(C.RTDB + path + ".json", c ? { signal: c.signal } : {}).then(function (r) { clearTimeout(t); return r.ok ? r.json() : null; }).catch(function () { clearTimeout(t); return null; });
  }
  // Google Drive "view" links do not download; turn them into a direct download. Any other https link is used as is.
  function direct(u) {
    var m = /drive\.google\.com\/(?:file\/d\/|open\?id=|uc\?(?:export=download&)?id=)([\w-]+)/.exec(u || "");
    return m ? "https://drive.usercontent.google.com/download?id=" + m[1] + "&export=download&confirm=t" : u;
  }
  var pending = null;
  function release(force) {
    if (pending && !force) return pending;
    pending = get("DarkStoreUpdate").then(function (r) {
      if (r && typeof r.apkDownloadUrl === "string" && /^https:\/\//i.test(r.apkDownloadUrl)) { try { localStorage.setItem(KEY, JSON.stringify(r)); } catch (e) {} return r; }
      try { return JSON.parse(localStorage.getItem(KEY)); } catch (e) { return null; }   // offline: last known release
    });
    return pending;
  }
  function bind(force) {
    var links = document.querySelectorAll("[data-dl]");
    return release(force).then(function (r) {
      var ok = r && r.apkDownloadUrl;
      Array.prototype.forEach.call(links, function (a) {
        a.classList.toggle("off", !ok);
        if (ok) { a.href = direct(r.apkDownloadUrl); a.removeAttribute("aria-disabled"); a.dataset.ready = "1"; }
        else { a.href = "#"; a.setAttribute("aria-disabled", "true"); a.dataset.ready = ""; }
        var l = a.querySelector("[data-label]"); if (l) l.textContent = ok ? (a.dataset.text || "Download for Android") : "Download unavailable. Tap to retry";
      });
      Array.prototype.forEach.call(document.querySelectorAll("[data-ver]"), function (e) { e.textContent = r && r.latestVersionName ? "Version " + r.latestVersionName : ""; });
      return r;
    });
  }
  document.addEventListener("click", function (e) {
    var a = e.target.closest && e.target.closest("[data-dl]");
    if (a && !a.dataset.ready) { e.preventDefault(); bind(true); }
  });
  function live(a) { return a && a.name && a.isApproved !== false && !a.isSuspended && !a.isOnHold && !a.isUpcoming; }
  function sortApps(l) { return l.sort(function (x, y) { return (parseFloat(y.rating) || 0) - (parseFloat(x.rating) || 0) || (x.name > y.name ? 1 : -1); }); }
  // Supabase: POST /rest/v1/rpc/<fn>. Resolves to the JSON result, or undefined when Supabase is not set up / unreachable.
  function rpc(fn, args, ms) {
    if (!C.SUPABASE || !C.ANON || C.SUPABASE.indexOf("__") === 0 || C.ANON.indexOf("__") === 0) return Promise.resolve(undefined);  // not configured (e.g. opened locally)
    var c = ("AbortController" in window) ? new AbortController() : null, t = setTimeout(function () { if (c) c.abort(); }, ms || 9000);
    var o = { method: "POST", headers: { "apikey": C.ANON, "Authorization": "Bearer " + C.ANON, "Content-Type": "application/json" }, body: JSON.stringify(args || {}) };
    if (c) o.signal = c.signal;
    return fetch(C.SUPABASE.replace(/\/+$/, "") + "/rest/v1/rpc/" + fn, o)
      .then(function (r) { clearTimeout(t); return r.ok ? r.json() : undefined; })
      .catch(function () { clearTimeout(t); return undefined; });
  }
  var CK = "ds_catalog_v1";
  function apps() {
    return rpc("public_apps").then(function (list) {
      if (Array.isArray(list)) { try { localStorage.setItem(CK, JSON.stringify(list)); } catch (e) {} return list; }
      if (list === undefined) {
        // Supabase not configured / offline: last known catalog, else the old Firebase list
        try { var c = JSON.parse(localStorage.getItem(CK)); if (Array.isArray(c) && c.length) return c; } catch (e) {}
        return get("apps", 9000).then(function (o) { var l = []; for (var k in (o || {})) { if (live(o[k])) { o[k].id = k; l.push(o[k]); } } return sortApps(l); });
      }
      return [];
    });
  }
  // One app for app.html (null = not found / not public)
  function app(id) {
    return rpc("public_app", { p_id: id }).then(function (a) {
      if (a && typeof a === "object" && a.name) return a;
      if (a === undefined) return get("apps/" + encodeURIComponent(id), 9000);   // fallback: old Firebase data
      return null;
    });
  }
  function img(src, alt) { var i = new Image(); i.alt = alt || ""; i.loading = "lazy"; i.referrerPolicy = "no-referrer"; if (/^https:\/\//i.test(src || "")) i.src = src; i.onerror = function () { i.style.visibility = "hidden"; }; return i; }
  function el(t, c, x) { var e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; }
  function copy(text) { if (navigator.clipboard && navigator.clipboard.writeText) return navigator.clipboard.writeText(text); return new Promise(function (r) { var t = document.createElement("textarea"); t.value = text; document.body.appendChild(t); t.select(); try { document.execCommand("copy"); } catch (e) {} t.remove(); r(); }); }
  return { C: C, get: get, release: release, bind: bind, apps: apps, app: app, img: img, el: el, copy: copy, direct: direct };
})();
