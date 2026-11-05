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
    ANON: "sb_publishable_1cVwbCsAjrY3cHStxiULpw_Z2d-sWqc"
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

  return { C: C, get: get, release: release, bind: bind, apps: apps, cachedApps: cachedApps, app: app, img: img, icon: icon, el: el, copy: copy, direct: direct, toast: toast, shots: shots, rate: rate, stars: stars, theme: theme };
})();
