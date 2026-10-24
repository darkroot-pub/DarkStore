/* DarkRoot Store website: shared loader. Nothing about the APK is written in the HTML: the link comes from
   Firebase Realtime Database -> DarkStoreUpdate/apkDownloadUrl, the same value the Android app's update dialog uses. */
window.DS = (function () {
  var C = { RTDB: "https://dark-store-6836d-default-rtdb.asia-southeast1.firebasedatabase.app/", BACKEND: "https://darkstore.bot-keep.xyz", PACKAGE: "com.darkstore.darkroot", SCHEME: "darkstore" };
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
  function apps() { return get("apps", 9000).then(function (o) { var l = []; for (var k in (o || {})) { if (live(o[k])) { o[k].id = k; l.push(o[k]); } } l.sort(function (x, y) { return (parseFloat(y.rating) || 0) - (parseFloat(x.rating) || 0) || (x.name > y.name ? 1 : -1); }); return l; }); }
  function img(src, alt) { var i = new Image(); i.alt = alt || ""; i.loading = "lazy"; i.referrerPolicy = "no-referrer"; if (/^https:\/\//i.test(src || "")) i.src = src; i.onerror = function () { i.style.visibility = "hidden"; }; return i; }
  function el(t, c, x) { var e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; }
  function copy(text) { if (navigator.clipboard && navigator.clipboard.writeText) return navigator.clipboard.writeText(text); return new Promise(function (r) { var t = document.createElement("textarea"); t.value = text; document.body.appendChild(t); t.select(); try { document.execCommand("copy"); } catch (e) {} t.remove(); r(); }); }
  return { C: C, get: get, release: release, bind: bind, apps: apps, img: img, el: el, copy: copy, direct: direct };
})();
