(function () {
  window.__tvboxSkipVideoFill = 1;

  var IDS = {
    "1": "东方卫视",
    "2": "新闻综合",
    "11": "魔都眼",
    "5": "第一财经",
    "12": "新纪实",
    "10": "五星体育",
    "4": "都市频道",
    "9": "哈哈炫动"
  };

  var pickGen = 0;
  var wantKey = "";
  var clickTimer = 0;
  var laying = 0;
  var boot = 0;
  var needNew = 0;
  var prevSrc = "";
  var prevTime = 0;
  var seenGap = 0;
  var didSwitch = 0;

  function viewportOnce() {
    if (document.getElementById("tvbox-kankanews-vp")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var m = document.createElement("meta");
    m.id = "tvbox-kankanews-vp";
    m.name = "viewport";
    m.content = "width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no";
    parent.insertBefore(m, parent.firstChild);
  }

  function cssOnce() {
    viewportOnce();
    if (document.getElementById("tvbox-kankanews-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-kankanews-css";
    s.textContent =
      "html,body{margin:0!important;padding:0!important;overflow:hidden!important;" +
      "background:#000!important;width:100%!important;height:100%!important}" +
      ".live-container{height:auto!important;padding:0!important}" +
      "header,footer,.header,.main-nav-body,.channel-list,.channel-container," +
      ".program-list,.live-aside,.app-download,.filing,.banner," +
      ".xgplayer-controls,.xgplayer-start,.xgplayer-replay,.xgplayer-poster," +
      ".xgplayer-loading,.xgplayer-fullscreen,.xgplayer-volume,.xgplayer-progress," +
      ".loading-mask,.image-mask" +
      "{display:none!important;visibility:hidden!important;pointer-events:none!important}" +
      "video{object-fit:contain!important;object-position:center center!important;" +
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
    return document.querySelector(".live-player")
      || document.querySelector(".xgplayer")
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
        n.style.setProperty("perspective", "none", "important");
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

  function fillInner(el) {
    if (!el || !el.style || el.getAttribute("data-tvbox-box")) return;
    el.style.cssText =
      "display:block!important;position:absolute!important;" +
      "left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "width:100%!important;height:100%!important;" +
      "max-width:none!important;max-height:none!important;min-width:0!important;min-height:0!important;" +
      "margin:0!important;padding:0!important;border:none!important;" +
      "background:transparent!important;overflow:hidden!important;" +
      "transform:none!important;zoom:1!important";
  }

  function dirtyMedia(el) {
    if (el.getAttribute("data-tvbox-m") !== "1") return true;
    var tf = (el.style.transform || "").replace(/\s/g, "");
    if (tf && tf !== "none") return true;
    var w = el.style.width || "";
    var h = el.style.height || "";
    return w.indexOf("px") >= 0 || h.indexOf("px") >= 0
      || !!el.getAttribute("width") || !!el.getAttribute("height");
  }

  function fillMedia(el) {
    if (!el || !el.style || !dirtyMedia(el)) return;
    try {
      el.removeAttribute("width");
      el.removeAttribute("height");
    } catch (e) {}
    el.setAttribute("data-tvbox-m", "1");
    el.style.cssText =
      "display:block!important;position:absolute!important;" +
      "left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "width:100%!important;height:100%!important;" +
      "max-width:100%!important;max-height:100%!important;min-width:0!important;min-height:0!important;" +
      "margin:0!important;padding:0!important;border:none!important;" +
      "object-fit:contain!important;object-position:center center!important;" +
      "transform:none!important;zoom:1!important;background:#000!important";
  }

  function flattenPlayer(box) {
    var nodes = box.querySelectorAll(
      ".xgplayer,.xgplayer-container,.xgplayer-video-wrap,.xgplayer-video,xg-video,.xg-video"
    );
    var i;
    for (i = 0; i < nodes.length; i++) fillInner(nodes[i]);
    if (box.classList) {
      box.classList.remove(
        "xgplayer-is-cssfullscreen",
        "xgplayer-is-fullscreen",
        "xgplayer-fullscreen-parent",
        "xgplayer-cssfullscreen"
      );
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
      unclip(box);
      pinBox(box, s, 9998);
      flattenPlayer(box);
    }
    if (v) {
      unclip(v);
      try {
        v.controls = false;
        v.removeAttribute("x5-video-player-fullscreen");
        v.removeAttribute("x5-video-orientation");
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

  function layout() {
    if (laying) return !!window.__tvboxLayoutReady;
    laying = 1;
    try {
      return layoutWork();
    } finally {
      laying = 0;
    }
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
      if (gen !== pickGen) return;
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
    if (gen !== pickGen) return;
    if (!pictureReady()) return;
    needNew = 0;
    unmute();
    window.__tvboxLayoutReady = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function channelIdFrom(url) {
    var s = String(url || "");
    var m = s.match(/[?&#]id=(\d+)/i) || s.match(/\/huikan\/(\d+)/i);
    return m ? m[1] : "";
  }

  function compact(s) {
    return String(s || "").replace(/\s+/g, "");
  }

  function aliasName(name) {
    var n = compact(name);
    if (n === "上海卫视" || n === "东方") return "东方卫视";
    if (n === "上海新闻综合") return "新闻综合";
    if (n === "上海都市" || n === "上海都市频道") return "都市频道";
    if (n === "上海哈哈炫动") return "哈哈炫动";
    if (n === "上海第一财经") return "第一财经";
    if (n === "上海纪实" || n === "纪实人文" || n === "上海纪实人文") return "新纪实";
    return n;
  }

  function channelLabel(el) {
    var img = el.querySelector && el.querySelector("img");
    return (img && img.getAttribute("alt")) || el.textContent || "";
  }

  function channelNodes() {
    return document.querySelectorAll(".channel-list li");
  }

  function isSelected(el) {
    return (" " + (el.className || "") + " ").indexOf(" active ") >= 0;
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

  function clickByName(name) {
    var want = aliasName(name);
    if (!want) return false;
    var links = channelNodes();
    var i;
    for (i = 0; i < links.length; i++) {
      if (aliasName(channelLabel(links[i])) === want) return clickEl(links[i]);
    }
    return false;
  }

  function clickWant(id, liveName) {
    if (id && IDS[id] && clickByName(IDS[id])) return true;
    if (liveName && clickByName(liveName)) return true;
    if (!id && !liveName) return !!playerBox();
    return false;
  }

  function waitFrame(gen) {
    var n = 0;
    function tick() {
      if (gen !== pickGen) return;
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
    var id = channelIdFrom(url || location.href);
    var name = compact(liveName);
    var next = id ? ("id:" + id) : (name ? ("nm:" + name) : "");
    if (next && next === wantKey) {
      if (pictureReady()) tellReady(pickGen);
      else layout();
      return;
    }
    wantKey = next;
    pickGen += 1;
    var gen = pickGen;
    if (clickTimer) {
      clearTimeout(clickTimer);
      clickTimer = 0;
    }
    var n = 0;
    function tryClick() {
      if (gen !== pickGen) return;
      clickTimer = 0;
      cssOnce();
      didSwitch = 0;
      if (clickWant(id, name)) {
        if (didSwitch) markSwitch();
        waitFrame(gen);
        return;
      }
      if (++n < 24) clickTimer = setTimeout(tryClick, 150);
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
  if (!window.__tvboxKkResize) {
    window.__tvboxKkResize = 1;
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
      window.visualViewport.addEventListener("scroll", window.__tvboxRelayout);
    }
    window.addEventListener("resize", window.__tvboxRelayout);
  }

  if (!boot) {
    boot = 1;
    setTimeout(function () {
      if (pickGen) return;
      pickGen = 1;
      waitFrame(1);
    }, 0);
  }
})();
