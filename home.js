(function () {
  var $ = function (i) { return document.getElementById(i); };
  var ref = new URLSearchParams(location.search).get("ref");   // an invite link that landed on the home page
  if (ref && /^[A-Za-z0-9]{8}$/.test(ref)) { location.replace("invite.html?ref=" + encodeURIComponent(ref.toUpperCase())); return; }

  if (!/Android/i.test(navigator.userAgent)) $("desktopHint").hidden = false;

  DS.stores();
  DS.bind().then(function (r) {
    if (r && (r.updateTitle || r.updateMessage)) {
      $("relTitle").textContent = r.updateTitle || "DarkRoot Store";
      $("relMsg").textContent = r.updateMessage || "";
      $("release").hidden = false;
    }
  });

  // download bar on phones: appears after the hero scrolls away
  var dock = $("dock"), hero = document.querySelector(".hero");
  if ("IntersectionObserver" in window) new IntersectionObserver(function (e) { dock.classList.toggle("show", !e[0].isIntersecting); }).observe(hero);

  /* ---------- catalog ---------- */
  var PAGE = 12, all = [], cat = "all", shown = PAGE, ok = true;
  var grid = $("grid"), status = $("status"), q = $("q"), sort = $("sort"), more = $("more");

  function tile(a) {
    var t = DS.el("div", "tile");
    var m = DS.el("a", "tile-main"); m.href = "app.html?id=" + encodeURIComponent(a.id);
    m.appendChild(DS.icon(a, "m"));
    var d = DS.el("div"); d.appendChild(DS.el("b", "", a.name)); d.appendChild(DS.el("small", "", a.developer || a.category || "App"));
    if (a.developer && a.category) d.appendChild(DS.el("small", "", a.category));
    m.appendChild(d);
    if (DS.stars(a)) m.appendChild(DS.el("span", "rt", "★ " + DS.stars(a)));
    t.appendChild(m);
    // Direct APK download from the published catalog (no store app needed).
    var dl = DS.el("a", "tile-dl"); dl.href = "#"; dl.dataset.apiDl = ""; dl.dataset.id = a.id;
    dl.setAttribute("aria-label", "Download " + a.name + " APK"); dl.title = "Download APK";
    dl.appendChild(DS.el("span", "", "↓"));
    t.appendChild(dl);
    return t;
  }
  function visible() {
    var s = q.value.trim().toLowerCase();
    var l = all.filter(function (a) {
      return (cat === "all" || (a.category || "Other") === cat) &&
        (!s || (a.name + " " + (a.developer || "") + " " + (a.category || "")).toLowerCase().indexOf(s) > -1);
    });
    return l.sort(sort.value === "az"
      ? function (x, y) { return x.name.localeCompare(y.name); }
      : function (x, y) { return DS.rate(y) - DS.rate(x) || x.name.localeCompare(y.name); });
  }
  function render() {
    grid.setAttribute("aria-busy", "false"); grid.textContent = "";
    var l = visible();
    if (!all.length) {
      var e = DS.el("div", "empty"); e.style.gridColumn = "1/-1";
      e.appendChild(DS.el("b", "", ok ? "No apps are live yet" : "Can't load the catalog"));
      e.appendChild(DS.el("span", "", ok ? "Check back soon, or open the app." : "Check your connection and try again."));
      if (!ok) { var b = DS.el("button", "btn ghost", "Try again"); b.type = "button"; b.onclick = load; e.appendChild(document.createElement("br")); e.appendChild(b); }
      grid.appendChild(e); more.hidden = true; status.textContent = ""; return;
    }
    if (!l.length) {
      var n = DS.el("div", "empty"); n.style.gridColumn = "1/-1"; n.appendChild(DS.el("b", "", "No match")); n.appendChild(DS.el("span", "", "Try another word or category."));
      grid.appendChild(n); more.hidden = true; status.textContent = ""; return;
    }
    l.slice(0, shown).forEach(function (a) { grid.appendChild(tile(a)); });
    more.hidden = l.length <= shown;
    status.textContent = (l.length === all.length ? l.length + " app" + (l.length === 1 ? "" : "s") : l.length + " of " + all.length + " apps") + (ok ? "" : " · offline copy");
    DS.bindApiDownloads();
  }
  function cats() {
    var seen = {}; all.forEach(function (a) { var c = a.category || "Other"; seen[c] = (seen[c] || 0) + 1; });
    var names = Object.keys(seen).sort(function (x, y) { return seen[y] - seen[x] || x.localeCompare(y); });
    var bar = $("cats"); bar.textContent = ""; bar.hidden = names.length < 2;
    ["all"].concat(names).forEach(function (c) {
      var p = DS.el("button", "pill", c === "all" ? "All" : c); p.type = "button"; p.setAttribute("aria-pressed", c === cat ? "true" : "false");
      p.onclick = function () { cat = c; shown = PAGE; Array.prototype.forEach.call(bar.children, function (x) { x.setAttribute("aria-pressed", x === p ? "true" : "false"); }); render(); };
      bar.appendChild(p);
    });
  }
  function phone(list) {
    var box = $("phoneList"); box.textContent = "";
    if (!list.length) { box.textContent = "No apps yet. Open the app to browse."; box.className = "small muted"; return; }
    box.className = "";
    list.slice(0, 5).forEach(function (a) {
      var r = DS.el("a", "row"); r.href = "app.html?id=" + encodeURIComponent(a.id);
      r.appendChild(DS.icon(a));
      var t = DS.el("div"); t.appendChild(DS.el("b", "", a.name)); t.appendChild(DS.el("small", "", a.developer || a.category || ""));
      r.appendChild(t);
      if (DS.stars(a)) r.appendChild(DS.el("em", "", "★ " + DS.stars(a)));
      box.appendChild(r);
    });
    $("livetag").hidden = false;
  }
  function apply(list, fresh) {
    all = list; ok = fresh; var top = list.slice().sort(function (x, y) { return DS.rate(y) - DS.rate(x); });
    phone(top); cats();
    if (list.length) { $("count").textContent = list.length + " app" + (list.length === 1 ? "" : "s") + " live"; $("count").hidden = false; }
    render();
  }
  function load() {
    var c = DS.cachedApps(); if (c) apply(c, true);   // show last known catalog instantly, refresh below
    DS.apps().then(function (r) { apply(r.list, r.ok); });
  }
  q.addEventListener("input", function () { shown = PAGE; render(); });
  sort.addEventListener("change", render);
  more.addEventListener("click", function () { shown += PAGE; render(); });
  load();
})();
