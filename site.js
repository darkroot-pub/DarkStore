/* DarkRoot Store website: shared loader. Nothing about the APK is written in the HTML.
   - APK link + version : Firebase Realtime Database -> DarkStoreUpdate (same value the Android app's update dialog uses)
   - App catalog / pages: Supabase, through the public read-only functions public_apps() / public_app() (08_website_public.sql)
   - Invite page        : the notifier server (referral)
   The Supabase key below is the PUBLISHABLE key. It is meant to be public. Never put the secret / service key here. */
window.DS = (function () {
  var C = {
    RTDB: "https://dark-store-6836d-default-rtdb.asia-southeast1.firebasedatabase.app/",
    BACKEND: "https://darkstore.bot-keep.xyz",
    PACKAGE: "com.darkstore.darkroot", SCHEME: "darkstore",
    SUPABASE: "https://mjhuwxmpbxmqgtfkqvsc.supabase.co",
    ANON: "sb_publishable_1cVwbCsAjrY3cHStxiULpw_Z2d-sWqc",
    /* Published catalog API. This is the DEFAULT PUBLIC key: it is rate-limited and meant for
       read-only demo use. Issue a scoped private key for production and keep that one server-side. */
    API: "https://mjhuwxmpbxmqgtfkqvsc.supabase.co/functions/v1/store-api/v1",
    API_KEY: "dsk_4be1239d1f9532e99d545f3eab1e3a1023df8d0114c6a121",
    /* Other stores that carry DarkRoot Store. Paste the listing link here, or set fdroidUrl / orionUrl in Firebase
       DarkStoreUpdate (no redeploy needed; Firebase wins). Empty = the card shows "Coming soon". */
    STORES: { fdroid: "", orion: "" }
  };
  var KEY = "ds_release_v1", CK = "ds_catalog_v1";
  function store(k, v) { try { if (v === undefined) return JSON.parse(localStorage.getItem(k)); localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} return null; }

  /* ---------- theme (light / dark / follow system) ---------- */
  function theme(next) {
    var root = document.documentElement;
    if (next) { root.setAttribute("data-theme", next); try { localStorage.setItem("ds_theme", next); } catch (e) {} }
    var cur = root.getAttribute("data-theme") || (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    var m = document.querySelector('meta[name="theme-color"]'); if (m) m.content = cur === "dark" ? "#080b12" : "#f5f8fc";
    return cur;
  }
  function initThemeButtons() {
    theme();
    Array.prototype.forEach.call(document.querySelectorAll("[data-theme-toggle]"), function (b) {
      function sync() { var d = theme() === "dark"; b.setAttribute("aria-label", d ? "Switch to light theme" : "Switch to dark theme"); b.setAttribute("aria-pressed", d ? "true" : "false"); }
      b.addEventListener("click", function () { theme(theme() === "dark" ? "light" : "dark"); sync(); }); sync();
    });
  }

  /* ---------- Firebase RTDB (APK link only) ---------- */
  function timed(url, opts, ms) {
    var c = ("AbortController" in window) ? new AbortController() : null, t = setTimeout(function () { if (c) c.abort(); }, ms || 8000);
    opts = opts || {}; if (c) opts.signal = c.signal;
    return fetch(url, opts).then(function (r) { clearTimeout(t); return r; }, function (e) { clearTimeout(t); throw e; });
  }
  function get(path, ms) {
    return timed(C.RTDB + path + ".json", {}, ms).then(function (r) { return r.ok ? r.json() : null; }).catch(function () { return null; });
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
      if (r && typeof r.apkDownloadUrl === "string" && /^https:\/\//i.test(r.apkDownloadUrl)) { store(KEY, r); return r; }
      return store(KEY);   // offline: last known release
    });
    return pending;
  }
  function bind(force) {
    var links = document.querySelectorAll("[data-dl]");
    Array.prototype.forEach.call(links, function (a) { if (!a.dataset.ready) a.classList.add("busy"); });
    return release(force).then(function (r) {
      var ok = r && r.apkDownloadUrl;
      Array.prototype.forEach.call(links, function (a) {
        a.classList.remove("busy"); a.classList.toggle("off", !ok);
        if (ok) { a.href = direct(r.apkDownloadUrl); a.removeAttribute("aria-disabled"); a.dataset.ready = "1"; a.setAttribute("download", ""); }
        else { a.href = "#"; a.setAttribute("aria-disabled", "true"); a.dataset.ready = ""; }
        var l = a.querySelector("[data-label]"); if (l) l.textContent = ok ? (a.dataset.text || "Download for Android") : "Download unavailable. Tap to retry";
      });
      Array.prototype.forEach.call(document.querySelectorAll("[data-ver]"), function (e) { e.textContent = r && r.latestVersionName ? "Version " + r.latestVersionName : ""; });
      return r;
    });
  }
  /* ---------- Published catalog API: direct APK links for website visitors ---------- */
  var AK = "ds_api_apps_v1", apiP = null;
  function apiList() {
    var cache = store(AK);
    return timed(C.API + "/apps?limit=100", { headers: { "X-API-Key": C.API_KEY } }, 9000)
      .then(function (r) { return r.ok ? r.json() : null; }, function () { return null; })
      .then(function (j) {
        var l = (j && Array.isArray(j.data)) ? j.data.filter(function (a) {
          return a && a.id && a.download && /^https:\/\//i.test(a.download.url || "");
        }) : null;
        if (l && l.length) { store(AK, l); return l; }
        return Array.isArray(cache) && cache.length ? cache : [];
      })
      .catch(function () { return Array.isArray(cache) && cache.length ? cache : []; });
  }
  function apiApps() { if (!apiP) apiP = apiList(); return apiP; }
  function apiUrl(id) {
    return apiApps().then(function (l) {
      for (var i = 0; i < l.length; i++) if (l[i].id === id) return direct(l[i].download.url);
      return null;
    });
  }
  // Fills every [data-api-dl][data-id] link with the verified APK url from the API.
  function bindApiDownloads() {
    var links = document.querySelectorAll("[data-api-dl]");
    Array.prototype.forEach.call(links, function (a) { if (!a.dataset.ready) a.classList.add("busy"); });
    return apiApps().then(function (list) {
      var map = {};
      list.forEach(function (a) { map[a.id] = a; });
      Array.prototype.forEach.call(links, function (a) {
        var app = map[a.dataset.id], u = app && app.download && app.download.url;
        a.classList.remove("busy");
        if (u && /^https:\/\//i.test(u)) {
          a.href = direct(u); a.setAttribute("download", ""); a.setAttribute("target", "_blank"); a.setAttribute("rel", "noopener");
          a.dataset.ready = "1"; a.removeAttribute("aria-disabled");
        } else {
          a.href = "#"; a.dataset.ready = ""; a.setAttribute("aria-disabled", "true");
        }
        var l = a.querySelector("[data-apk-label]");
        if (l) l.textContent = u ? (a.dataset.apkText || "Download APK") : "Download unavailable";
      });
      return list;
    });
  }
  document.addEventListener("click", function (e) {
    var a = e.target.closest && e.target.closest("[data-api-dl]");
    if (a && !a.dataset.ready) { e.preventDefault(); bindApiDownloads(); }
  });

  /* Hand off to the native app WITHOUT replacing this page.
     A hidden frame is used on purpose: if DarkRoot Store is not installed,
     Android fails quietly inside the frame instead of showing an
     "Unknown scheme / chrome-error" screen. If we are still visible after a
     moment, tell the visitor what to do and let them use the direct APK link. */
  function openInApp(intent, status, missing) {
    var noted = false;
    function note() { if (!noted && status) { status.textContent = missing; noted = true; } }
    try {
      var f = document.createElement("iframe");
      f.style.cssText = "display:none;width:0;height:0;border:0";
      f.setAttribute("aria-hidden", "true");
      f.src = intent;
      document.body.appendChild(f);
      setTimeout(function () { if (f.parentNode) f.parentNode.removeChild(f); }, 2500);
    } catch (e) {}
    setTimeout(function () { if (document.visibilityState === "visible") note(); }, 1400);
    return false;
  }

  // Listing links for the other stores. Fills every [data-store="fdroid|orion"] card/link on the page.
  function stores() {
    return release().then(function (r) {
      var out = {};
      ["fdroid", "orion"].forEach(function (k) {
        var u = (r && r[k + "Url"]) || C.STORES[k] || "";
        out[k] = /^https:\/\//i.test(u) ? u : "";
      });
      Array.prototype.forEach.call(document.querySelectorAll("[data-store]"), function (a) {
        var u = out[a.dataset.store]; a.classList.toggle("soon", !u);
        if (u) { a.href = u; a.target = "_blank"; a.rel = "noopener"; a.removeAttribute("aria-disabled"); }
        else { a.removeAttribute("href"); a.setAttribute("aria-disabled", "true"); }
        var l = a.querySelector("[data-slabel]"); if (l) l.textContent = u ? (a.dataset.open || "Open") : "Coming soon";
      });
      Array.prototype.forEach.call(document.querySelectorAll("[data-if-store]"), function (e) { e.hidden = !(out.fdroid || out.orion); });
      return out;
    });
  }
  document.addEventListener("click", function (e) {
    var a = e.target.closest && e.target.closest("[data-dl]");
    if (a && !a.dataset.ready) { e.preventDefault(); bind(true); }
  });

  /* ---------- Supabase catalog ---------- */
  function live(a) { return a && a.name && a.isApproved !== false && !a.isSuspended && !a.isOnHold && !a.isUpcoming; }
  function rate(a) { return parseFloat(a && a.rating) || 0; }
  function sortApps(l) { return l.sort(function (x, y) { return rate(y) - rate(x) || (x.name > y.name ? 1 : -1); }); }
  function configured() { return C.SUPABASE && C.ANON && C.SUPABASE.indexOf("__") !== 0 && C.ANON.indexOf("__") !== 0; }
  // POST /rest/v1/rpc/<fn>. Resolves to the JSON result, or undefined when Supabase is not set up / unreachable.
  function rpc(fn, args, ms) {
    if (!configured()) return Promise.resolve(undefined);
    return timed(C.SUPABASE.replace(/\/+$/, "") + "/rest/v1/rpc/" + fn,
      { method: "POST", headers: { "apikey": C.ANON, "Authorization": "Bearer " + C.ANON, "Content-Type": "application/json" }, body: JSON.stringify(args || {}) }, ms || 9000)
      .then(function (r) { return r.ok ? r.json() : undefined; }, function () { return undefined; });
  }
  function fromFirebase() {
    return get("apps", 9000).then(function (o) { var l = []; for (var k in (o || {})) { if (live(o[k])) { o[k].id = k; l.push(o[k]); } } return sortApps(l); });
  }
  // Fresh catalog. Resolves to {list, ok}; ok=false means "could not reach any source" (offline), not "no apps".
  function apps() {
    return rpc("public_apps").then(function (list) {
      if (Array.isArray(list)) { store(CK, list); return { list: list, ok: true }; }
      var c = store(CK);
      if (Array.isArray(c) && c.length) return { list: c, ok: false, stale: true };
      return fromFirebase().then(function (l) { return { list: l, ok: l.length > 0 || list !== undefined }; });
    });
  }
  // Last catalog stored on this device (shown instantly while the fresh one loads)
  function cachedApps() { var c = store(CK); return Array.isArray(c) && c.length ? c : null; }
  // One app for app.html (null = not found / not public)
  function app(id) {
    return rpc("public_app", { p_id: id }).then(function (a) {
      if (a && typeof a === "object" && a.name) return a;
      if (a === undefined) {
        var c = cachedApps(); var hit = c && c.filter(function (x) { return x.id === id; })[0];
        return hit || get("apps/" + encodeURIComponent(id), 9000);   // offline: cache, then the old Firebase data
      }
      return null;
    });
  }

  /* ---------- small helpers ---------- */
  function hue(s) { var h = 0; s = String(s || "?"); for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360; return h; }
  // App icon: the real logo, or a coloured letter tile when the logo is missing / fails to load.
  function icon(a, cls) {
    var w = document.createElement("span"); w.className = "ic " + (cls || "");
    var letter = (String(a && a.name || "?").trim().charAt(0) || "?").toUpperCase();
    function fb() { w.textContent = letter; w.style.setProperty("--h", hue(a && a.name)); w.classList.add("fb"); }
    if (/^https:\/\//i.test(a && a.logo || "")) {
      var i = new Image(); i.alt = ""; i.loading = "lazy"; i.decoding = "async"; i.referrerPolicy = "no-referrer";
      i.onerror = function () { i.remove(); fb(); }; i.src = a.logo; w.appendChild(i);
    } else fb();
    return w;
  }
  function img(src, alt) { var i = new Image(); i.alt = alt || ""; i.loading = "lazy"; i.decoding = "async"; i.referrerPolicy = "no-referrer"; if (/^https:\/\//i.test(src || "")) i.src = src; i.onerror = function () { i.style.visibility = "hidden"; }; return i; }
  function el(t, c, x) { var e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; }
  function copy(text) { if (navigator.clipboard && navigator.clipboard.writeText) return navigator.clipboard.writeText(text).catch(function () {}); return new Promise(function (r) { var t = document.createElement("textarea"); t.value = text; document.body.appendChild(t); t.select(); try { document.execCommand("copy"); } catch (e) {} t.remove(); r(); }); }
  var toastT;
  function toast(msg) {
    var t = document.getElementById("toast");
    if (!t) { t = el("div", "toast"); t.id = "toast"; t.setAttribute("role", "status"); document.body.appendChild(t); }
    t.textContent = msg; t.classList.add("show"); clearTimeout(toastT); toastT = setTimeout(function () { t.classList.remove("show"); }, 2200);
  }
  function shots(a) {
    var s = Array.isArray(a.screenshots) ? a.screenshots : String(a.screenshots || "").split(/[,|\n]/);
    return s.map(function (x) { return String(x).trim(); }).filter(function (x) { return /^https:\/\//i.test(x); }).slice(0, 8);
  }
  function stars(a) { var r = rate(a); return r > 0 ? r.toFixed(1) : ""; }

  /* ---------- shared page chrome: theme buttons, mobile menu ---------- */
  document.addEventListener("DOMContentLoaded", function () {
    initThemeButtons();
    var b = document.getElementById("menuBtn"), n = document.getElementById("menu");
    if (b && n) {
      var set = function (o) { n.classList.toggle("open", o); b.setAttribute("aria-expanded", o ? "true" : "false"); };
      b.addEventListener("click", function () { set(!n.classList.contains("open")); });
      n.addEventListener("click", function (e) { if (e.target.closest("a")) set(false); });
      document.addEventListener("keydown", function (e) { if (e.key === "Escape") set(false); });
    }
  });

  return { C: C, get: get, release: release, bind: bind, stores: stores, apps: apps, cachedApps: cachedApps, app: app,
    apiApps: apiApps, apiUrl: apiUrl, bindApiDownloads: bindApiDownloads, openInApp: openInApp,
    img: img, icon: icon, el: el, copy: copy, direct: direct, toast: toast, shots: shots, rate: rate, stars: stars, theme: theme };
})();
