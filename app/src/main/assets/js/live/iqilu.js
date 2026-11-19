(function () {
  window.__tvboxSkipVideoFill = 1;

  var laying = 0;
  var boot = 0;

  function cssOnce() {
    var head = document.head || document.documentElement;
    if (!head || document.getElementById("tvbox-iqilu-css")) return;
    if (!document.getElementById("tvbox-iqilu-vp")) {
      var meta = document.createElement("meta");
      meta.id = "tvbox-iqilu-vp";
      meta.name = "viewport";
      meta.content = "width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no";
      head.insertBefore(meta, head.firstChild);
    }
    var s = document.createElement("style");
    s.id = "tvbox-iqilu-css";
    s.textContent =
      "html,body{margin:0!important;padding:0!important;overflow:hidden!important;" +
      "background:#000!important;width:100%!important;height:100%!important}" +
      ".vjs-control-bar,.vjs-big-play-button,.vjs-poster,.vjs-loading-spinner," +
      ".vjs-modal-dialog,.vjs-error-display,.vjs-text-track-display," +
      ".vjs-title-bar,.tcp-context-menu,.vjs-contextmenu" +
      "{display:none!important;visibility:hidden!important;pointer-events:none!important}" +
      "video,.player{object-fit:contain!important;object-position:center center!important;" +
      "transform:none!important;zoom:1!important}" +
      "video::-webkit-media-controls{display:none!important}";
    head.appendChild(s);
  }

  function vp() {
    var v = window.visualViewport;
    if (v && v.width > 0 && v.height > 0) {
      return { x: v.offsetLeft || 0, y: v.offsetTop || 0, w: v.width, h: v.height };
    }
    return { x: 0, y: 0, w: window.innerWidth || 0, h: window.innerHeight || 0 };
  }

  function playerBox() {
    return document.querySelector(".player-container");
  }

  function videoEl() {
    var box = playerBox();
    return box ? box.querySelector("video") : null;
  }

  function hideSiblings(el) {
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

  function pinBox(el, s) {
    if (!el || !el.style || s.w < 8 || s.h < 8) return;
    var key = s.x + "," + s.y + "," + s.w + "," + s.h;
    if (el.getAttribute("data-tvbox-box") === key && (el.offsetWidth || 0) >= s.w - 8) return;
    el.setAttribute("data-tvbox-box", key);
    el.style.cssText =
      "display:block!important;position:fixed!important;" +
      "left:" + s.x + "px!important;top:" + s.y + "px!important;" +
      "width:" + s.w + "px!important;height:" + s.h + "px!important;" +
      "max-width:none!important;max-height:none!important;min-width:0!important;min-height:0!important;" +
      "margin:0!important;padding:0!important;border:none!important;" +
      "z-index:9998!important;background:#000!important;overflow:hidden!important;" +
      "transform:none!important;zoom:1!important";
  }

  function fillInner(el) {
    if (!el || !el.style || el.tagName === "VIDEO" || el.getAttribute("data-tvbox-box")) return;
    el.style.cssText =
      "display:block!important;position:absolute!important;" +
      "left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "width:100%!important;height:100%!important;padding:0!important;padding-top:0!important;" +
      "max-width:none!important;max-height:none!important;min-width:0!important;min-height:0!important;" +
      "margin:0!important;border:none!important;" +
      "background:transparent!important;overflow:hidden!important;" +
      "transform:none!important;zoom:1!important";
    if (el.classList) {
      el.classList.remove("vjs-fluid", "vjs-16-9", "vjs-4-3", "vjs-fullscreen", "vjs-fullscreen-parent");
    }
  }

  function fillVideo(el) {
    if (!el || !el.style) return;
    try {
      el.removeAttribute("width");
      el.removeAttribute("height");
      el.controls = false;
      el.removeAttribute("x5-video-player-fullscreen");
      el.removeAttribute("x5-video-orientation");
      el.setAttribute("playsinline", "");
      el.setAttribute("webkit-playsinline", "true");
    } catch (e) {}
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
    var nodes = box.querySelectorAll(".tcp-player,.video-js");
    for (var i = 0; i < nodes.length; i++) fillInner(nodes[i]);
  }

  function layoutWork() {
    cssOnce();
    var s = vp();
    if (s.w < 8 || s.h < 8) return false;
    var box = playerBox();
    var v = videoEl();
    if (!box) return false;
    hideSiblings(box);
    unclip(box);
    pinBox(box, s);
    flattenPlayer(box);
    if (v) {
      unclip(v);
      fillVideo(v);
    }
    window.__tvboxLayoutReady = !!(v && s.w > 8 && s.h > 8);
    return window.__tvboxLayoutReady;
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

  function pictureReady() {
    var v = videoEl();
    return !!(v && v.videoWidth * v.videoHeight !== 0 && layout());
  }

  function tellReady() {
    if (!pictureReady()) return;
    unmute();
    window.__tvboxLayoutReady = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function waitFrame() {
    var n = 0;
    function tick() {
      try {
        layout();
        unmute();
        if (pictureReady()) {
          tellReady();
          return;
        }
      } catch (e) {}
      if (++n < 48) setTimeout(tick, n < 12 ? 80 : 150);
      else {
        layout();
        unmute();
        try {
          if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
        } catch (e2) {}
      }
    }
    tick();
  }

  window.__tvboxRelayout = function () {
    var el = playerBox();
    if (el) el.removeAttribute("data-tvbox-box");
    layout();
  };

  cssOnce();
  if (!window.__tvboxIqiluResize) {
    window.__tvboxIqiluResize = 1;
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
      window.visualViewport.addEventListener("scroll", window.__tvboxRelayout);
    }
    window.addEventListener("resize", window.__tvboxRelayout);
  }

  if (!boot) {
    boot = 1;
    setTimeout(waitFrame, 0);
  }
})();
