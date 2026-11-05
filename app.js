(function () {
  var $ = function (i) { return document.getElementById(i); };
  var id = (new URLSearchParams(location.search).get("id") || "").trim();
  DS.bind(); DS.stores();
  var android = /Android/i.test(navigator.userAgent);
  // The id is only passed on to the app when it looks like a normal id (any source: DarkRoot, F-Droid package, Orion ...)
  var valid = /^[A-Za-z0-9_.\-]{1,120}$/.test(id);
  var path = valid ? "app/" + encodeURIComponent(id) : "";
  var shareUrl = location.origin + location.pathname + (valid ? "?id=" + encodeURIComponent(id) : "");
  var intent = "intent://" + path + "#Intent;scheme=" + DS.C.SCHEME + ";package=" + DS.C.PACKAGE + ";end";
  var deep = android ? intent : DS.C.SCHEME + "://" + path;
  $("open").href = deep; $("fbOpen").href = deep;
  var fell = false;
  // Details could not be loaded (not in the database, offline, any source): never a dead end, hand over to the store app.
  function fallback() {
    if (fell) return; fell = true;
    $("loading").hidden = true; $("app").hidden = true; $("fallback").hidden = false;
    document.title = "Open in DarkRoot Store";
    $("fbStatus").textContent = android ? "Opening the app… if nothing happens, install DarkRoot Store first." : "DarkRoot Store is an Android app. Open this link on your Android phone.";
    if (!android) $("fbOpen").hidden = true;
  }
  $("fbCopy").addEventListener("click", function () { DS.copy(shareUrl).then(function () { DS.toast("Link copied"); }); });
  if (!valid) { fallback(); if (android) setTimeout(function () { location.href = intent; }, 400); return; }
  if (android) setTimeout(function () { location.href = intent; }, 700);   // best effort: open the app straight away, whatever the data says

  function fact(k, v) { if (!v) return; var d = DS.el("div"); d.appendChild(DS.el("dt", "", k)); d.appendChild(DS.el("dd", "", v)); $("facts").appendChild(d); }

  DS.app(id).then(function (a) {
    if (!a || a.isApproved === false || a.isSuspended === true || !a.name) { fallback(); return; }
    $("loading").hidden = true; $("app").hidden = false;
    document.title = a.name + " on DarkRoot Store";
    var d = document.querySelector('meta[name="description"]'); if (d) d.content = (a.description || "").slice(0, 150) || d.content;
    $("icon").appendChild(DS.icon(a, "xl"));
    $("name").textContent = a.name;
    if (a.developer) $("by").textContent = "by " + a.developer; else $("by").hidden = true;
    [a.category, DS.stars(a) ? "★ " + DS.stars(a) : "", a.version ? "v" + a.version : "", a.size].forEach(function (t) { if (t) $("chips").appendChild(DS.el("span", "chip", t)); });
    fact("Version", a.version); fact("Size", a.size); fact("Category", a.category); fact("Rating", DS.stars(a) ? DS.stars(a) + " / 5" : ""); fact("Developer", a.developer); fact("Package", a.packageName);

    var s = DS.shots(a), box = $("shots");
    s.forEach(function (u, i) {
      var b = DS.el("button"); b.type = "button"; b.setAttribute("aria-label", "View screenshot " + (i + 1));
      b.appendChild(DS.img(u, a.name + " screenshot " + (i + 1))); b.onclick = function () { $("lbi").src = u; $("lb").showModal && $("lb").showModal(); };
      box.appendChild(b);
    });
    if (s.length) box.hidden = false;

    if (a.description) {
      $("desc").textContent = a.description; $("about").hidden = false;
      if (a.description.length > 260 || a.description.split("\n").length > 5) {
        var r = $("readmore"); r.hidden = false;
        r.onclick = function () { var c = $("desc").classList.toggle("clamp"); r.textContent = c ? "Read more" : "Show less"; };
      } else $("desc").classList.remove("clamp");
    }
    if (android) $("status").textContent = "Opening the app… if nothing happens, install DarkRoot Store first.";
    else $("status").textContent = "DarkRoot Store is an Android app. Open this link on your Android phone.";

    // more apps
    DS.apps().then(function (r) {
      var l = r.list.filter(function (x) { return x.id !== id; });
      var same = l.filter(function (x) { return a.category && x.category === a.category; });
      l = same.concat(l.filter(function (x) { return same.indexOf(x) < 0; })).slice(0, 6);
      if (!l.length) return;
      l.forEach(function (x) {
        var t = DS.el("a", "tile"); t.href = "app.html?id=" + encodeURIComponent(x.id); t.appendChild(DS.icon(x, "m"));
        var dd = DS.el("div"); dd.appendChild(DS.el("b", "", x.name)); dd.appendChild(DS.el("small", "", x.developer || x.category || "")); t.appendChild(dd);
        if (DS.stars(x)) t.appendChild(DS.el("span", "rt", "★ " + DS.stars(x)));
        $("relGrid").appendChild(t);
      });
      $("related").hidden = false;
    });
  }).catch(fallback);

  $("share").addEventListener("click", function () {
    var t = $("name").textContent;
    if (navigator.share) navigator.share({ title: t + " on DarkRoot Store", url: shareUrl }).catch(function () {});
    else DS.copy(shareUrl).then(function () { DS.toast("Link copied"); });
  });
  var lb = $("lb"); $("lbx").onclick = function () { lb.close(); };
  lb.addEventListener("click", function (e) { if (e.target === lb) lb.close(); });
})();
