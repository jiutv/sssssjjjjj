(function () {
  window.__tvboxSkipVideoFill = 1;

  var needNew = 0;
  var prevSrc = "";
  var prevTime = 0;
  var seenGap = 0;
  var didSwitch = 0;

  function cssOnce() {
    if (document.getElementById("tvbox-mgtv-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-mgtv-css";
    s.textContent =
      "html,body,#__nuxt,#__layout,#mgtv-default-layout,.content,.channel-page,.channel-page-wrapper{" +
      "margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;" +
      "width:100%!important;height:100%!important}" +
      "#m-topheader,header,footer,.channel-left-nav,.channel-left-list,.channel-left-skeleton," +
      ".m-topheader-channel,.channel-top-bg-header,.side-float,.side-float-bar," +
      ".mango-control-bar,.ControlBar,[class*=\"ControlBar\"]," +
      ".vjs-control-bar,.vjs-big-play-button,.vjs-poster" +
      "{display:none!important;visibility:hidden!important;pointer-events:none!important}" +
      "video,.live-player-tv-module video{" +
      "object-fit:contain!important;object-position:center center!important;" +
      "transform:none!important;zoom:1!important}" +
      "video::-webkit-media-controls{display:none!important}";
    parent.appendChild(s);
  }

  function vp() {
    var v = window.visualViewport;
    if (v && v.width > 0 && v.height > 0) {
      return { x: v.offsetLeft || 0, y: v.offsetTop || 0, w: v.width, h: v.height };
    }
    return { x: 0, y: 0, w: window.innerWidth || 0, h: window.innerHeight || 0 };
  }

  function playerBox() {
    return document.querySelector(".live-player-tv-module")
      || document.querySelector(".player-container .player")
      || document.querySelector("[class*='live-player']")
      || document.querySelector("[class*='LivePlayer']")
      || document.querySelector(".mango-player")
      || document.querySelector("video");
  }

  function videoEl() {
    var box = playerBox();
    if (box && box.tagName === "VIDEO") return box;
    return (box && box.querySelector("video")) || document.querySelector("video");
  }

  function hideSiblings(el) {
    if (!el) return;
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
        c.style.setProperty("visibility", "hidden", "important");
      }
      n = p;
    }
  }

  function unclip(el) {
    var n = el;
    while (n && n !== document.documentElement) {
      if (n.getAttribute && n.getAttribute("data-tvbox-box")) {
        n = n.parentElement;
        continue;
      }
      if (n.style) {
        n.style.setProperty("overflow", "visible", "important");
        n.style.setProperty("transform", "none", "important");
        n.style.setProperty("filter", "none", "important");
        n.style.setProperty("clip-path", "none", "important");
        n.style.setProperty("zoom", "1", "important");
        n.style.setProperty("background", "#000", "important");
      }
      n = n.parentElement;
    }
  }

  function pinBox(el, s, z) {
    if (!el || !el.style || s.w < 8 || s.h < 8) return;
    var key = s.x + "," + s.y + "," + s.w + "," + s.h + "," + z;
    if (el.getAttribute("data-tvbox-box") === key && (el.offsetWidth || 0) >= s.w - 8) return;
    el.setAttribute("data-tvbox-box", key);
    el.style.cssText =
      "display:block!important;position:fixed!important;" +
      "left:" + s.x + "px!important;top:" + s.y + "px!important;" +
      "width:" + s.w + "px!important;height:" + s.h + "px!important;" +
      "max-width:none!important;max-height:none!important;min-width:0!important;min-height:0!important;" +
      "margin:0!important;padding:0!important;border:none!important;" +
      "z-index:" + z + "!important;background:#000!important;overflow:hidden!important;" +
      "transform:none!important;zoom:1!important";
  }

  function fillMedia(el) {
    if (!el || !el.style) return;
    var tf = (el.style.transform || "").replace(/\s/g, "");
    if (el.getAttribute("data-tvbox-m") === "1" && (!tf || tf === "none")) return;
    el.setAttribute("data-tvbox-m", "1");
    el.style.cssText =
      "display:block!important;position:absolute!important;" +
      "left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "width:100%!important;height:100%!important;" +
      "max-width:none!important;max-height:none!important;min-width:0!important;min-height:0!important;" +
      "margin:0!important;padding:0!important;border:none!important;" +
      "object-fit:contain!important;object-position:center center!important;" +
      "transform:none!important;zoom:1!important;background:#000!important";
  }

  var laying = 0;

  function layout() {
    if (laying) return !!window.__tvboxLayoutReady;
    laying = 1;
    try {
      return layoutWork();
    } finally {
      laying = 0;
    }
  }

  function layoutWork() {
    cssOnce();
    var s = vp();
    if (s.w < 8 || s.h < 8) return false;
    var box = playerBox();
    var v = videoEl();
    if (box) {
      hideSiblings(box);
      pinBox(box, s, 9998);
    }
    if (v) {
      unclip(v);
      try {
        v.controls = false;
        v.removeAttribute("x5-video-player-fullscreen");
        v.setAttribute("playsinline", "");
        v.setAttribute("webkit-playsinline", "true");
      } catch (e) {}
      if (box && v !== box) fillMedia(v);
      else pinBox(v, s, 10000);
    }
    var ok = !!(v && s.w > 8 && s.h > 8);
    window.__tvboxLayoutReady = ok && !needNew;
    return ok;
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

  function srcOf(v) {
    if (!v) return "";
    try {
      return v.currentSrc || v.src || "";
    } catch (e) {
      return "";
    }
  }

  function markSwitch() {
    var v = videoEl();
    prevSrc = srcOf(v);
    prevTime = v ? v.currentTime || 0 : 0;
    seenGap = 0;
    needNew = 1;
    window.__tvboxLayoutReady = 0;
  }

  function pictureReady() {
    var v = videoEl();
    if (!v || v.videoWidth * v.videoHeight === 0 || v.readyState < 2) {
      if (needNew) seenGap = 1;
      return false;
    }
    if (!layout()) return false;
    if (!needNew) return true;
    var src = srcOf(v);
    if (prevSrc && src && src !== prevSrc) return true;
    if (v.currentTime + 0.35 < prevTime) return true;
    return !!seenGap;
  }

  function bindVideo(gen) {
    var v = videoEl();
    if (!v || v.getAttribute("data-tvbox-g") === String(gen)) return;
    v.setAttribute("data-tvbox-g", String(gen));
    function onEv() {
      if (gen !== window.__tvboxMgtvPickGen) return;
      if (v.readyState < 2 || v.videoWidth * v.videoHeight === 0) seenGap = 1;
      if (pictureReady()) tellReady(gen);
    }
    v.addEventListener("emptied", onEv);
    v.addEventListener("loadstart", onEv);
    v.addEventListener("loadeddata", onEv);
    v.addEventListener("playing", onEv);
    v.addEventListener("canplay", onEv);
  }

  function tellReady(gen) {
    if (gen !== window.__tvboxMgtvPickGen) return;
    if (!pictureReady()) return;
    needNew = 0;
    unmute();
    window.__tvboxLayoutReady = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function cameraIdFrom(url) {
    var m = String(url || "").match(/[?&#]cameraId=(\d+)/i);
    if (m) return m[1];
    try {
      return new URL(url || location.href, location.href).searchParams.get("cameraId") || "";
    } catch (e) {}
    return "";
  }

  function compact(s) {
    return String(s || "").replace(/\s+/g, "");
  }

  function channelLabel(el) {
    return el.getAttribute("title")
      || el.getAttribute("data-title")
      || el.getAttribute("aria-label")
      || el.textContent
      || "";
  }

  function idOf(el) {
    return el.getAttribute("data-channel-sid")
      || el.getAttribute("data-sid")
      || cameraIdFrom(el.getAttribute("href") || "");
  }

  function channelNodes() {
    return document.querySelectorAll(
      "a.channel,[data-channel-sid],[data-sid],a[href*='cameraId='],.channels a"
    );
  }

  function isSelected(el) {
    var c = " " + (el.className || "") + " ";
    return c.indexOf(" selected ") >= 0 || c.indexOf(" active ") >= 0
      || el.getAttribute("aria-current") === "true";
  }

  function clickEl(el) {
    if (!el) return false;
    if (isSelected(el)) return true;
    didSwitch = 1;
    try {
      el.click();
    } catch (e) {}
    return true;
  }

  function clickById(id) {
    if (!id) return false;
    var links = channelNodes();
    var i;
    for (i = 0; i < links.length; i++) {
      if (idOf(links[i]) === id) return clickEl(links[i]);
    }
    return false;
  }

  function clickByName(name) {
    var want = compact(name);
    if (!want) return false;
    var links = channelNodes();
    var i;
    var label;
    for (i = 0; i < links.length; i++) {
      if (compact(channelLabel(links[i])) === want) return clickEl(links[i]);
    }
    links = document.querySelectorAll("a");
    for (i = 0; i < links.length; i++) {
      label = compact(channelLabel(links[i]));
      if (label === want) return clickEl(links[i]);
    }
    return false;
  }

  function clickTab() {
    var tabs = document.querySelectorAll(".channels .tabs .item");
    if (!tabs.length) {
      var names = { "热门": 1, "卫视台": 1, "卫视": 1, "地方台": 1, "特色频道": 1 };
      var all = document.querySelectorAll("a,button");
      var found = [];
      var i;
      for (i = 0; i < all.length; i++) {
        if (names[compact(channelLabel(all[i]))]) found.push(all[i]);
      }
      tabs = found;
    }
    if (!tabs.length) return false;
    var i = window.__tvboxMgtvTab || 0;
    if (i >= tabs.length) {
      window.__tvboxMgtvTab = 0;
      return false;
    }
    var tab = tabs[i];
    window.__tvboxMgtvTab = i + 1;
    if (!isSelected(tab)) {
      try {
        tab.click();
      } catch (e) {}
    }
    return false;
  }

  function clickWant(id, liveName) {
    if (id && clickById(id)) {
      window.__tvboxMgtvTab = 0;
      return true;
    }
    if (liveName && clickByName(liveName)) {
      window.__tvboxMgtvTab = 0;
      return true;
    }
    if (!id && !liveName) return !!playerBox();
    return clickTab();
  }

  function waitFrame(gen) {
    var n = 0;
    function tick() {
      if (gen !== window.__tvboxMgtvPickGen) return;
      try {
        layout();
        bindVideo(gen);
        if (pictureReady()) {
          tellReady(gen);
          return;
        }
      } catch (e) {}
      if (++n < 72) setTimeout(tick, n < 12 ? 80 : 150);
    }
    tick();
  }

  window.__tvboxInPageGo = function (url, liveName) {
    var id = cameraIdFrom(url || location.href);
    var name = compact(liveName);
    var want = id ? ("id:" + id) : (name ? ("nm:" + name) : "");
    if (want && want === window.__tvboxMgtvWant) {
      if (pictureReady()) tellReady(window.__tvboxMgtvPickGen);
      else layout();
      return;
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
      cssOnce();
      didSwitch = 0;
      if (clickWant(id, name)) {
        if (didSwitch) markSwitch();
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
    var v = videoEl();
    if (v) v.removeAttribute("data-tvbox-m");
    layout();
  };

  cssOnce();
  if (!window.__tvboxMgtvResize) {
    window.__tvboxMgtvResize = 1;
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
      window.visualViewport.addEventListener("scroll", window.__tvboxRelayout);
    }
    window.addEventListener("resize", window.__tvboxRelayout);
  }

  if (!window.__tvboxMgtvBoot) {
    window.__tvboxMgtvBoot = 1;
    setTimeout(function () {
      if (window.__tvboxMgtvPickGen) return;
      window.__tvboxMgtvPickGen = 1;
      waitFrame(1);
    }, 0);
  }
})();
