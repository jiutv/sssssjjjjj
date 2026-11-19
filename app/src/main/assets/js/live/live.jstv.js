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
    jsty: "江苏体育",
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

  function parseKey(url) {
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

  function unmute() {
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

  function clickWant(key) {
    var items = document.querySelectorAll(".item");
    if (!items.length) return false;
    if (!key) {
      items[0].click();
      return true;
    }
    var name = EN_NAME[key] || "";
    var i;
    for (i = 0; i < items.length; i++) {
      var el = items[i];
      var img = el.querySelector("img");
      var src = (img && img.getAttribute("src")) || "";
      if (src.indexOf(key + "_live-screenshot") >= 0 || src.indexOf("/" + key + "_") >= 0) {
        el.click();
        return true;
      }
    }
    if (name) {
      for (i = 0; i < items.length; i++) {
        var text = (items[i].textContent || "").replace(/\s+/g, "");
        if (key === "jsws" && text.indexOf("4K") >= 0) continue;
        if (text.indexOf(name.replace(/\s+/g, "")) >= 0) {
          items[i].click();
          return true;
        }
      }
    }
    return false;
  }

  function tellReady(gen) {
    if (gen !== window.__tvboxJstvPickGen) return;
    window.__tvboxLayoutReady = 1;
    unmute();
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function waitFrame(gen) {
    var n = 0;
    function tick() {
      if (gen !== window.__tvboxJstvPickGen) return;
      cssOnce();
      var box = playerBox();
      if (box) fill(box);
      unmute();
      var v = document.querySelector("video");
      if (v && v.videoWidth * v.videoHeight !== 0) {
        tellReady(gen);
        return;
      }
      if (++n < 40) setTimeout(tick, 250);
      else tellReady(gen);
    }
    tick();
  }

  window.__tvboxInPageGo = window.__tvboxJstvGo = function (url) {
    window.__tvboxJstvKey = parseKey(url || location.href);
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
        setTimeout(function () {
          if (gen !== window.__tvboxJstvPickGen) return;
          waitFrame(gen);
        }, 400);
        return;
      }
      if (++n < 40) window.__tvboxJstvTimer = setTimeout(tryClick, 200);
      else waitFrame(gen);
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
