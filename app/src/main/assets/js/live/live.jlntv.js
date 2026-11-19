(function () {
  window.__tvboxSkipVideoFill = 1;

  var EN_NAME = {
    jlws: "吉林卫视",
    jlds: "都市频道",
    jlsh: "生活频道",
    jlys: "影视频道",
    jlxc: "乡村频道",
    jlzy: "综艺",
    cczh: "长春综合"
  };

  function cssOnce() {
    if (document.getElementById("tvbox-jlntv-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-jlntv-css";
    s.textContent =
      "html,body,#app{margin:0!important;padding:0!important;overflow:hidden!important;" +
      "background:#000!important;height:100%!important;width:100%!important}" +
      "header,.header,footer,.footer,.brdeadcrumb,.video-list-wp," +
      ".vjs-poster,.vjs-big-play-button,.vjs-control-bar,.vjs-loading-spinner,.player-error" +
      "{display:none!important;pointer-events:none!important}" +
      ".player-content-wp .vjs-fluid,.player-content-wp .video-js.vjs-16-9" +
      "{padding-top:0!important;height:100%!important}" +
      "video::-webkit-media-controls{display:none!important}";
    parent.appendChild(s);
  }

  function playerBox() {
    return document.querySelector(".player-content-wp")
      || document.getElementById("live-player")
      || document.querySelector(".live-player");
  }

  function hideSiblings(el) {
    if (el.getAttribute("data-tvbox-hid")) return;
    el.setAttribute("data-tvbox-hid", "1");
    var n = el;
    while (n && n !== document.body && n !== document.documentElement) {
      var p = n.parentElement;
      if (!p) break;
      var kids = p.children;
      for (var i = 0; i < kids.length; i++) {
        var c = kids[i];
        if (c === n) continue;
        var tag = c.tagName;
        if (tag === "SCRIPT" || tag === "STYLE" || tag === "LINK" || tag === "META") continue;
        c.style.setProperty("display", "none", "important");
      }
      n = p;
    }
  }

  function vp() {
    var v = window.visualViewport;
    if (v && v.width > 0 && v.height > 0) {
      return { x: v.offsetLeft || 0, y: v.offsetTop || 0, w: v.width, h: v.height };
    }
    return { x: 0, y: 0, w: window.innerWidth, h: window.innerHeight };
  }

  function fillBox() {
    var el = playerBox();
    if (!el || !el.style) return false;
    var s = vp();
    var key = s.x + "," + s.y + "," + s.w + "," + s.h;
    var h = el.offsetHeight || 0;
    if (el.getAttribute("data-tvbox-box") === key && h >= s.h - 4) {
      window.__tvboxLayoutReady = 1;
      return true;
    }
    el.setAttribute("data-tvbox-box", key);
    el.style.cssText =
      "position:fixed!important;left:" + s.x + "px!important;top:" + s.y + "px!important;" +
      "width:" + s.w + "px!important;height:" + s.h + "px!important;" +
      "z-index:9999!important;margin:0!important;padding:0!important;" +
      "background:#000!important;overflow:hidden!important";
    hideSiblings(el);
    window.__tvboxLayoutReady = 1;
    return (el.offsetHeight || 0) >= s.h - 4;
  }

  function videoEl() {
    var box = playerBox();
    return (box && box.querySelector("video")) || document.querySelector("video");
  }

  function srcOf(v) {
    return v ? ((v.currentSrc || v.src || "") + "") : "";
  }

  function unmute() {
    if (window.__tvboxMediaPaused) return;
    var v = videoEl();
    if (!v) return;
    try {
      v.controls = false;
      v.muted = false;
      v.volume = 1;
      if (v.paused && v.readyState >= 1) v.play();
    } catch (e) {}
  }

  function parseQuery(search) {
    var out = {};
    var s = (search || "").replace(/^\?/, "");
    if (!s) return out;
    var parts = s.split("&");
    for (var i = 0; i < parts.length; i++) {
      var p = parts[i];
      var cut = p.indexOf("=");
      var k = cut < 0 ? p : p.slice(0, cut);
      var v = cut < 0 ? "" : p.slice(cut + 1);
      try {
        k = decodeURIComponent(k.replace(/\+/g, " ")).toLowerCase();
        v = decodeURIComponent(v.replace(/\+/g, " "));
      } catch (e) {}
      if (k && out[k] == null) out[k] = v;
    }
    return out;
  }

  function parseKey(url, liveName) {
    var name = (liveName || "").replace(/^\s+|\s+$/g, "");
    if (name) return name;
    try {
      var u = new URL(url || location.href, location.href);
      var q = parseQuery(u.search);
      return (q.tvbox || q.ch || q.name || q.title || "");
    } catch (e) {}
    return "";
  }

  function channelItems() {
    var a = document.querySelectorAll(".list-outer .item-wp");
    if (a.length) return a;
    return document.querySelectorAll(".phone-wp .list-wp .item-wp");
  }

  function compact(s) {
    return (s || "").replace(/\s+/g, "");
  }

  function findWant(key) {
    var items = channelItems();
    if (!items.length) return null;
    var raw = (key || "").replace(/^\s+|\s+$/g, "");
    if (!raw) return items[0];
    var want = compact(EN_NAME[raw.toLowerCase()] || raw);
    for (var i = 0; i < items.length; i++) {
      if (compact(items[i].textContent) === want) return items[i];
    }
    return null;
  }

  function layout() {
    cssOnce();
    return fillBox();
  }

  function pictureOk(prevSrc, needNewSrc) {
    var v = videoEl();
    if (!(v && v.videoWidth * v.videoHeight !== 0)) return false;
    if (!needNewSrc) return true;
    var src = srcOf(v);
    return !prevSrc || src !== prevSrc;
  }

  function tellReady(gen) {
    if (gen !== window.__tvboxJlntvPickGen) return false;
    if (window.__tvboxJlntvReadyGen === gen) return true;
    layout();
    unmute();
    window.__tvboxJlntvReadyGen = gen;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
    return true;
  }

  function bindVideo(gen, prevSrc, needNewSrc) {
    var v = videoEl();
    if (!v || v.getAttribute("data-tvbox-g") === String(gen)) return;
    v.setAttribute("data-tvbox-g", String(gen));
    function onEv() {
      if (gen !== window.__tvboxJlntvPickGen) return;
      if (pictureOk(prevSrc, needNewSrc)) tellReady(gen);
    }
    v.addEventListener("loadeddata", onEv);
    v.addEventListener("playing", onEv);
    v.addEventListener("canplay", onEv);
  }

  function waitFrame(gen, prevSrc, needNewSrc) {
    var n = 0;
    function tick() {
      if (gen !== window.__tvboxJlntvPickGen) return;
      layout();
      unmute();
      bindVideo(gen, prevSrc, needNewSrc);
      if (pictureOk(prevSrc, needNewSrc) && tellReady(gen)) return;
      if (++n < 60) setTimeout(tick, n < 12 ? 50 : 120);
      else tellReady(gen);
    }
    tick();
  }

  window.__tvboxInPageGo = window.__tvboxJlntvGo = function (url, liveName) {
    window.__tvboxJlntvKey = parseKey(url || location.href, liveName);
    window.__tvboxJlntvPickGen = (window.__tvboxJlntvPickGen || 0) + 1;
    var gen = window.__tvboxJlntvPickGen;
    if (window.__tvboxJlntvTimer) {
      clearTimeout(window.__tvboxJlntvTimer);
      window.__tvboxJlntvTimer = 0;
    }
    var n = 0;
    function tryClick() {
      if (gen !== window.__tvboxJlntvPickGen) return;
      window.__tvboxJlntvTimer = 0;
      cssOnce();
      var el = findWant(window.__tvboxJlntvKey);
      if (el) {
        var prev = srcOf(videoEl());
        var already = el.classList && el.classList.contains("active");
        if (!already) {
          try {
            el.click();
          } catch (e) {}
        }
        waitFrame(gen, prev, !already);
        return;
      }
      if (++n < 40) window.__tvboxJlntvTimer = setTimeout(tryClick, n < 16 ? 50 : 120);
      else waitFrame(gen, srcOf(videoEl()), false);
    }
    tryClick();
  };

  window.__tvboxRelayout = function () {
    var el = playerBox();
    if (el) el.removeAttribute("data-tvbox-box");
    layout();
  };

  cssOnce();
  if (!window.__tvboxJlntvBoot) {
    window.__tvboxJlntvBoot = 1;
    window.addEventListener("resize", window.__tvboxRelayout);
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
    }
  }
})();
