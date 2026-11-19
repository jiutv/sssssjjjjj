(function () {
  window.__tvboxSkipVideoFill = 1;

  var ID_EN = {
    534: "jsws", 535: "jscs", 536: "jsxw", 556: "jszy", 542: "jsys",
    537: "jsxx", 545: "jsjy", 544: "jsgj", 543: "ymkt",
    663: "jszy", 664: "jsys", 665: "jsty", 666: "jsjy",
    667: "ymkt", 668: "jsxw", 669: "jscs", 670: "jsws", 671: "jsgj"
  };
  var EN_NAME = {
    jsws: "江苏卫视",
    jscs: "江苏城市",
    jsxw: "江苏新闻",
    jszy: "江苏综艺",
    jsys: "江苏影视",
    jsxx: "江苏学习",
    jsty: "体育休闲",
    jsjy: "江苏教育",
    jsgj: "江苏国际",
    ymkt: "优漫卡通"
  };

  function cssOnce() {
    if (document.getElementById("tvbox-jstv-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-jstv-css";
    s.textContent =
      "html,body,#app{margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;height:100%!important}" +
      "header,.header,.footer,.footer-con,.swiper-container,.swiper-wrapper," +
      ".swiper-slide,.swiper-button-prev,.swiper-button-next{position:fixed!important;left:-9999px!important;opacity:0!important;pointer-events:none!important}" +
      "#mse,.xgplayer,xg-video{display:block!important;position:fixed!important;left:0!important;top:0!important;" +
      "width:100%!important;height:100%!important;z-index:9999!important;margin:0!important;padding:0!important;background:#000!important}";
    parent.appendChild(s);
  }

  function vp() {
    var v = window.visualViewport;
    if (v && v.width > 0 && v.height > 0) {
      return { x: v.offsetLeft || 0, y: v.offsetTop || 0, w: v.width, h: v.height };
    }
    return { x: 0, y: 0, w: window.innerWidth, h: window.innerHeight };
  }

  function fill(el) {
    if (!el || !el.style) return;
    var s = vp();
    el.style.cssText =
      "position:fixed!important;left:" + s.x + "px!important;top:" + s.y + "px!important;" +
      "width:" + s.w + "px!important;height:" + s.h + "px!important;" +
      "z-index:9999!important;margin:0!important;padding:0!important;background:#000!important;display:block!important";
  }

  function playerBox() {
    return document.getElementById("mse") || document.querySelector(".xgplayer") || document.querySelector("video");
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
      var t = (q.tvbox || q.en || "").toLowerCase();
      if (t) return t;
      var id = q.channelid || q.channelld || q.channel_id;
      if (id && ID_EN[id]) return ID_EN[id];
    } catch (e) {}
    return "";
  }

  function compact(s) {
    return (s || "").replace(/\s+/g, "");
  }

  function itemTitle(el) {
    var t = el && el.querySelector(".title");
    return compact(t ? t.textContent : "");
  }

  function isOn(el) {
    var n = el;
    while (n && n !== document.body && n !== document.documentElement) {
      var c = " " + (n.className || "") + " ";
      if (c.indexOf(" active ") >= 0) return true;
      n = n.parentElement;
    }
    return false;
  }

  function findWant(key) {
    var items = document.querySelectorAll(".item");
    if (!items.length) return null;
    var raw = (key || "").replace(/^\s+|\s+$/g, "");
    if (!raw) return items[0];
    var code = raw.toLowerCase();
    var want = compact(EN_NAME[code] || raw);
    var skip4k = want === "江苏卫视" || code === "jsws";
    var i;
    var imgHit = null;
    var exact = null;
    for (i = 0; i < items.length; i++) {
      var el = items[i];
      var title = itemTitle(el);
      if (!title) continue;
      if (skip4k && title.indexOf("4K") >= 0) continue;
      if (EN_NAME[code]) {
        var img = el.querySelector("img");
        var src = (img && img.getAttribute("src")) || "";
        if (src.indexOf(code + "_live-screenshot") >= 0 || src.indexOf("/" + code + "_") >= 0) {
          imgHit = el;
          break;
        }
      }
      if (title === want) {
        exact = el;
        break;
      }
    }
    return imgHit || exact;
  }

  function clickWant(key) {
    var el = findWant(key);
    if (!el) return false;
    if (isOn(el)) return true;
    try {
      el.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true, view: window }));
    } catch (e) {
      try {
        el.click();
      } catch (e2) {
        return false;
      }
    }
    return true;
  }

  function unmute() {
    if (window.__tvboxMediaPaused) return;
    var n = document.querySelectorAll("video,audio");
    for (var i = 0; i < n.length; i++) {
      try {
        n[i].muted = false;
        n[i].defaultMuted = false;
        n[i].volume = 1;
        n[i].removeAttribute("muted");
        if (n[i].paused) n[i].play();
      } catch (e) {}
    }
    try {
      var roots = document.querySelectorAll(".xgplayer");
      for (var j = 0; j < roots.length; j++) {
        var p = roots[j].player || roots[j].__player;
        if (!p) continue;
        if (typeof p.volume === "function") p.volume(1);
        else p.volume = 1;
        p.muted = false;
      }
    } catch (e) {}
  }

  function srcOf(v) {
    if (!v) return "";
    try {
      return v.currentSrc || v.src || "";
    } catch (e) {
      return "";
    }
  }

  function tellReady(gen) {
    if (gen !== window.__tvboxJstvPickGen) return;
    window.__tvboxLayoutReady = 1;
    unmute();
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function waitFrame(gen, prevSrc, needNew) {
    var n = 0;
    function tick() {
      if (gen !== window.__tvboxJstvPickGen) return;
      cssOnce();
      var box = playerBox();
      if (box) fill(box);
      var v = document.querySelector("video");
      var src = srcOf(v);
      var sized = !!(v && v.videoWidth * v.videoHeight !== 0 && v.readyState >= 2);
      var fresh = !needNew || !prevSrc || src !== prevSrc;
      if (sized && fresh) {
        tellReady(gen);
        return;
      }
      if (++n < 40) setTimeout(tick, 250);
    }
    tick();
  }

  window.__tvboxInPageGo = window.__tvboxJstvGo = function (url, liveName) {
    var v0 = document.querySelector("video");
    var prevSrc = srcOf(v0);
    var needNew = !!(prevSrc || (v0 && v0.videoWidth * v0.videoHeight !== 0));
    window.__tvboxJstvKey = parseKey(url || location.href, liveName);
    window.__tvboxJstvPickGen = (window.__tvboxJstvPickGen || 0) + 1;
    var gen = window.__tvboxJstvPickGen;
    if (window.__tvboxJstvTimer) {
      clearTimeout(window.__tvboxJstvTimer);
      window.__tvboxJstvTimer = 0;
    }
    var n = 0;
    function tryClick() {
      if (gen !== window.__tvboxJstvPickGen) return;
      window.__tvboxJstvTimer = 0;
      cssOnce();
      if (clickWant(window.__tvboxJstvKey)) {
        window.__tvboxJstvPicked = 1;
        waitFrame(gen, prevSrc, needNew);
        return;
      }
      if (++n < 80) window.__tvboxJstvTimer = setTimeout(tryClick, n < 20 ? 50 : 120);
      else waitFrame(gen, prevSrc, needNew);
    }
    tryClick();
  };

  window.__tvboxRelayout = function () {
    var box = playerBox();
    if (box) fill(box);
    unmute();
  };

  cssOnce();
  if (!window.__tvboxJstvBoot) {
    window.__tvboxJstvBoot = 1;
    window.addEventListener("resize", window.__tvboxRelayout);
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
    }
  }
})();
