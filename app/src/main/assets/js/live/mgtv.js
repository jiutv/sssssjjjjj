(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    if (document.getElementById("tvbox-mgtv-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-mgtv-css";
    s.textContent =
      "html,body{margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;height:100%!important;width:100%!important}" +
      "video::-webkit-media-controls{display:none!important}";
    parent.appendChild(s);
  }

  function vp() {
    var v = window.visualViewport;
    if (v && v.width > 0 && v.height > 0) {
      return { x: v.offsetLeft || 0, y: v.offsetTop || 0, w: v.width, h: v.height };
    }
    return { x: 0, y: 0, w: window.innerWidth, h: window.innerHeight };
  }

  function playerBox() {
    return document.querySelector(".live-player-tv-module")
      || document.querySelector(".player-container .player");
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
        if (tag === "SCRIPT" || tag === "STYLE" || tag === "LINK") continue;
        c.style.setProperty("display", "none", "important");
      }
      n = p;
    }
  }

  function fillBox() {
    var el = playerBox();
    if (!el || !el.style) return false;
    var s = vp();
    var key = s.x + "," + s.y + "," + s.w + "," + s.h;
    var h = el.offsetHeight || 0;
    if (el.getAttribute("data-tvbox-box") === key && h >= s.h - 4) return true;
    el.setAttribute("data-tvbox-box", key);
    el.style.cssText =
      "position:fixed!important;left:" + s.x + "px!important;top:" + s.y + "px!important;" +
      "width:" + s.w + "px!important;height:" + s.h + "px!important;" +
      "z-index:9999!important;margin:0!important;padding:0!important;background:#000!important;overflow:hidden!important";
    hideSiblings(el);
    try {
      window.dispatchEvent(new Event("resize"));
    } catch (e) {}
    return (el.offsetHeight || 0) >= s.h - 4;
  }

  function videoEl() {
    var box = playerBox();
    return (box && box.querySelector("video")) || document.querySelector("video");
  }

  function unmute() {
    if (window.__tvboxMediaPaused) return;
    var v = videoEl();
    if (!v) return;
    try {
      v.controls = false;
      v.muted = false;
      v.volume = 1;
      if (v.paused) v.play();
    } catch (e) {}
  }

  function layout() {
    cssOnce();
    return fillBox();
  }

  function pictureReady() {
    var v = videoEl();
    return !!(v && v.videoWidth * v.videoHeight !== 0 && layout());
  }

  function tellReady(gen) {
    if (gen !== window.__tvboxMgtvPickGen) return;
    if (!pictureReady()) return;
    unmute();
    window.__tvboxLayoutReady = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function idFrom(url) {
    var id = "";
    try {
      var u = new URL(url || location.href, location.href);
      id = u.searchParams.get("sid") || u.searchParams.get("channelSid")
        || u.searchParams.get("channel") || u.searchParams.get("channelId")
        || u.searchParams.get("cameraId") || "";
    } catch (e) {}
    return /^\d+$/.test(id) ? id : "";
  }

  function compact(s) {
    return (s || "").replace(/[\s\u00a0]+/g, "");
  }

  function channelLabel(el) {
    var name = el.querySelector ? el.querySelector(".name") : null;
    return (el.getAttribute("data-channel-name") || el.getAttribute("title")
      || (name ? name.textContent : "") || el.textContent || "");
  }

  function isSelected(el) {
    return (" " + (el.className || "") + " ").indexOf(" selected ") >= 0;
  }

  /** 频道项：新版直播页是页面顶层的 a.channel[data-channel-sid]，旧版外面还包着一层 .channels */
  function channelEls() {
    return document.querySelectorAll("a.channel, .channels a.channel");
  }

  /** 按 sid 或频道名找频道：先精确匹配，再容忍「湖南都市 / 湖南都市频道」这类后缀差异 */
  function findChannel(id, name) {
    var els = channelEls();
    var i;
    if (id) {
      for (i = 0; i < els.length; i++) {
        if ((els[i].getAttribute("data-channel-sid") || "") === id) return els[i];
      }
    }
    var want = compact(name);
    if (!want) return null;
    var partial = null;
    for (i = 0; i < els.length; i++) {
      var lab = compact(channelLabel(els[i]));
      if (!lab) continue;
      if (lab === want) return els[i];
      if (!partial && (lab.indexOf(want) >= 0 || want.indexOf(lab) >= 0)) partial = els[i];
    }
    return partial;
  }

  function clickWant(id, liveName) {
    var el = findChannel(id, liveName);
    if (el) {
      window.__tvboxMgtvTab = 0;
      if (isSelected(el)) return true;
      var now = Date.now();
      // 站点没刷新 selected 样式时，别每 2 秒重复点同一条，避免流被反复重载
      if (window.__tvboxMgtvClickEl === el && now - (window.__tvboxMgtvClickAt || 0) < 10000) return true;
      window.__tvboxMgtvClickEl = el;
      window.__tvboxMgtvClickAt = now;
      el.click();
      return true;
    }
    if (id || liveName) {
      var tabs = document.querySelectorAll(".channels .tabs .item");
      if (!tabs.length) return false;
      var i = window.__tvboxMgtvTab || 0;
      if (i >= tabs.length) {
        window.__tvboxMgtvTab = 0;
        return false;
      }
      var tab = tabs[i];
      window.__tvboxMgtvTab = i + 1;
      if ((" " + tab.className + " ").indexOf(" selected ") < 0) tab.click();
      return false;
    }
    return !!playerBox();
  }

  function waitFrame(gen) {
    var n = 0;
    function tick() {
      if (gen !== window.__tvboxMgtvPickGen) return;
      try {
        layout();
        unmute();
        if (pictureReady()) {
          tellReady(gen);
          return;
        }
      } catch (e) {}
      if (++n < 48) setTimeout(tick, 150);
    }
    tick();
  }

  window.__tvboxInPageGo = function (url, liveName) {
    var id = idFrom(url);
    var name = (liveName || "").replace(/^\s+|\s+$/g, "");
    var want = id ? ("id:" + id) : (name ? ("nm:" + name) : "");
    if (want && want === window.__tvboxMgtvWant) {
      // 每次注入都会走到这里：只有目标频道已经选中、或页面压根没有频道列表时才提前返回。
      // 否则继续重试点击，避免频道列表比脚本晚渲染时一直停在默认频道（金鹰卡通）。
      var cur = findChannel(id, name);
      if (!cur || isSelected(cur)) {
        if (pictureReady()) tellReady(window.__tvboxMgtvPickGen);
        else layout();
        return;
      }
    }
    window.__tvboxMgtvWant = want;
    window.__tvboxMgtvTab = 0;
    window.__tvboxMgtvPickGen = (window.__tvboxMgtvPickGen || 0) + 1;
    var gen = window.__tvboxMgtvPickGen;
    if (window.__tvboxMgtvTimer) {
      clearTimeout(window.__tvboxMgtvTimer);
      window.__tvboxMgtvTimer = 0;
    }
    var n = 0;
    function tryClick() {
      if (gen !== window.__tvboxMgtvPickGen) return;
      window.__tvboxMgtvTimer = 0;
      if (clickWant(id, name)) {
        waitFrame(gen);
        return;
      }
      if (++n < 24) window.__tvboxMgtvTimer = setTimeout(tryClick, 150);
      else waitFrame(gen);
    }
    tryClick();
  };

  window.__tvboxRelayout = function () {
    var el = playerBox();
    if (el) el.removeAttribute("data-tvbox-box");
    layout();
  };

  cssOnce();
  if (!window.__tvboxMgtvResize) {
    window.__tvboxMgtvResize = 1;
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
    }
  }
})();
