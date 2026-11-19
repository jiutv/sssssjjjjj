(function () {
  window.__tvboxSkipVideoFill = 1;

  (function patchChunkSrc() {
    if (window.__tvboxGxtvChunk) return;
    window.__tvboxGxtvChunk = 1;
    var proto = window.HTMLScriptElement && HTMLScriptElement.prototype;
    if (!proto) return;
    var desc = Object.getOwnPropertyDescriptor(proto, "src");
    if (!(desc && desc.set)) return;
    var set = desc.set;
    Object.defineProperty(proto, "src", {
      configurable: true,
      enumerable: desc.enumerable,
      get: desc.get,
      set: function (v) {
        if (typeof v === "string" && /chunk\.[a-f0-9]+\.min\.js/i.test(v) && v.indexOf("rainbow") < 0) {
          v = "https://tv.gxtv.cn/static/js/rainbow/" + v.replace(/^[\s\S]*\//, "");
        }
        set.call(this, v);
      }
    });
  })();

  function viewportOnce() {
    if (document.getElementById("tvbox-gxtv-vp")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var m = document.createElement("meta");
    m.id = "tvbox-gxtv-vp";
    m.name = "viewport";
    m.content = "width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no";
    parent.insertBefore(m, parent.firstChild);
  }

  function cssOnce() {
    viewportOnce();
    if (document.getElementById("tvbox-gxtv-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-gxtv-css";
    s.textContent =
      "html,body{margin:0!important;padding:0!important;width:100%!important;height:100%!important;" +
      "overflow:hidden!important;background:#000!important}" +
      ".Header,.Live_list,.info,.Live_name,.gxntv_logo,#flashContent,.loading," +
      ".Footer,footer,.layui-layer,.layui-layer-shade,#player .controlbar," +
      ".coordinator .control-bar,.coordinator .logo-layer,.coordinator .loading-layer," +
      ".coordinator .playback-layer,.prompter" +
      "{display:none!important;visibility:hidden!important;pointer-events:none!important}" +
      "#player video,#DivVideo video,#player .dhc-video,#player canvas,#player .dhc-canvas{" +
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
    return document.getElementById("player")
      || document.getElementById("DivVideo")
      || document.querySelector(".CVideo")
      || document.querySelector(".Live_show");
  }

  function videoEl() {
    var box = playerBox();
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
        n.style.setProperty("clip", "auto", "important");
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
    if (!el || !el.style) return;
    if (el.getAttribute("data-tvbox-in") === "1") return;
    el.setAttribute("data-tvbox-in", "1");
    el.style.cssText =
      "display:block!important;position:absolute!important;" +
      "left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "width:100%!important;height:100%!important;" +
      "max-width:none!important;max-height:none!important;min-width:0!important;min-height:0!important;" +
      "margin:0!important;padding:0!important;border:none!important;" +
      "background:#000!important;overflow:hidden!important;" +
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

  function stripX5(v) {
    if (!v) return;
    try {
      v.removeAttribute("x5-video-player-fullscreen");
      v.removeAttribute("x5-video-orientation");
      v.setAttribute("playsinline", "");
      v.setAttribute("webkit-playsinline", "true");
      v.controls = false;
    } catch (e) {}
  }

  function syncOfficialSize(s) {
    try {
      window.width = s.w + "px";
      window.height = s.h + "px";
    } catch (e) {}
  }

  var lastPin = "";
  var lastResizeAt = 0;

  function notifyPlayer(s) {
    var key = s.x + "," + s.y + "," + s.w + "," + s.h;
    if (key === lastPin) return;
    lastPin = key;
    var now = Date.now();
    if (now - lastResizeAt < 200) return;
    lastResizeAt = now;
    try {
      window.dispatchEvent(new Event("resize"));
    } catch (e) {}
    try {
      var p = window._dhPlayer;
      if (p && typeof p.resize === "function") p.resize();
    } catch (e2) {}
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
    syncOfficialSize(s);

    var wrap = document.getElementById("DivVideo") || document.querySelector(".CVideo");
    var box = document.getElementById("player") || wrap || playerBox();
    if (wrap) {
      hideSiblings(wrap);
      unclip(wrap);
      pinBox(wrap, s, 9);
    }
    if (box) {
      if (box !== wrap) {
        hideSiblings(box);
        unclip(box);
      }
      pinBox(box, s, 10);
      var inners = box.querySelectorAll(".player-container,.coordinator,.dhc-player");
      var i;
      for (i = 0; i < inners.length; i++) fillInner(inners[i]);
      var medias = box.querySelectorAll("video,canvas,.dhc-video,.dhc-canvas");
      for (i = 0; i < medias.length; i++) fillMedia(medias[i]);
    }

    var v = videoEl();
    if (v) {
      unclip(v);
      stripX5(v);
      fillMedia(v);
    }

    notifyPlayer(s);
    window.__tvboxLayoutReady = !!(v && s.w > 8 && s.h > 8);
    return window.__tvboxLayoutReady;
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

  function pictureReady() {
    var v = videoEl();
    return !!(v && v.videoWidth * v.videoHeight !== 0 && layout());
  }

  var readySent = 0;
  var mo = null;
  var boxMo = null;

  function tellReady() {
    layout();
    unmute();
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function maybeReady() {
    layout();
    unmute();
    watchBox();
    if (readySent) return;
    if (!pictureReady()) return;
    readySent = 1;
    tellReady();
  }

  function watchBox() {
    var el = playerBox();
    if (!el || boxMo || !window.MutationObserver) return;
    try {
      boxMo = new MutationObserver(function () {
        layout();
      });
      boxMo.observe(el, { attributes: true, attributeFilter: ["style", "class"] });
      var wrap = document.getElementById("DivVideo");
      if (wrap && wrap !== el) {
        boxMo.observe(wrap, { attributes: true, attributeFilter: ["style", "class"] });
      }
    } catch (e) {}
  }

  function bindVideo() {
    var v = videoEl();
    if (!v || v.getAttribute("data-tvbox-v")) return;
    v.setAttribute("data-tvbox-v", "1");
    v.addEventListener("loadeddata", maybeReady);
    v.addEventListener("playing", maybeReady);
    v.addEventListener("canplay", maybeReady);
    v.addEventListener("resize", maybeReady);
  }

  window.__tvboxRelayout = function () {
    lastPin = "";
    var wrap = document.getElementById("DivVideo");
    var box = document.getElementById("player");
    if (wrap) wrap.removeAttribute("data-tvbox-box");
    if (box) box.removeAttribute("data-tvbox-box");
    layout();
  };

  cssOnce();
  if (!window.__tvboxGxtvResize) {
    window.__tvboxGxtvResize = 1;
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
      window.visualViewport.addEventListener("scroll", window.__tvboxRelayout);
    }
    window.addEventListener("resize", window.__tvboxRelayout);
  }

  if (window.__tvboxGxtvBoot) {
    layout();
    watchBox();
    bindVideo();
    maybeReady();
    return;
  }
  window.__tvboxGxtvBoot = 1;

  if (window.MutationObserver) {
    try {
      mo = new MutationObserver(function () {
        layout();
        watchBox();
        bindVideo();
        if (!readySent) maybeReady();
      });
      mo.observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) {}
  }

  var n = 0;
  function tick() {
    try {
      layout();
      watchBox();
      bindVideo();
      if (!readySent) maybeReady();
      else if (n > 80) return;
    } catch (e) {}
    n += 1;
    if (n < 40) setTimeout(tick, 80);
    else if (n < 80) setTimeout(tick, 200);
    else if (!readySent) tellReady();
  }
  tick();
})();
