(function () {
  if (window.__tvboxWebLiveBoot) return;
  window.__tvboxWebLiveBoot = 1;

  var CTRL = [
    ".xg-controls", ".xgplayer-controls", ".prism-controlbar", ".prism-big-play-btn",
    ".prism-detect-info", ".prism-info-display", ".vjs-control-bar", ".dplayer-controller",
    "#control_bar_player", "#pic_in_pic_player", ".con.poster", ".vjs-big-play-button",
    ".dplayer-bezel", ".dplayer-menu"
  ];

  try {
    var proto = Element.prototype;
    proto.requestFullscreen = proto.webkitRequestFullscreen = proto.mozRequestFullScreen =
      proto.msRequestFullscreen = function () {
        return window.Promise ? window.Promise.resolve() : undefined;
      };
  } catch (e) {}
  try {
    var vp = HTMLVideoElement.prototype;
    vp.webkitEnterFullscreen = vp.webkitEnterFullScreen = function () {};
  } catch (e2) {}

  if (window.__tvboxSkipVideoFill) return;

  function cssOnce() {
    if (document.getElementById("tvbox-web-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-web-css";
    s.textContent =
      "html,body{margin:0!important;padding:0!important;width:100%!important;height:100%!important;" +
      "overflow:hidden!important;background:#000!important}" +
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

  function pickVideo(root) {
    if (!root || !root.querySelectorAll) return null;
    var list = root.querySelectorAll("video");
    var best = null;
    var score = -1;
    var i;
    for (i = 0; i < list.length; i++) {
      var s = (list[i].videoWidth || 0) * (list[i].videoHeight || 0);
      if (s > score) {
        score = s;
        best = list[i];
      }
    }
    return best || list[0] || null;
  }

  function videoEl() {
    var v = pickVideo(document);
    if (v) return v;
    var all = document.querySelectorAll("*");
    var i;
    for (i = 0; i < all.length; i++) {
      try {
        if (all[i].shadowRoot) {
          v = pickVideo(all[i].shadowRoot);
          if (v) return v;
        }
      } catch (e) {}
    }
    return null;
  }

  function iframeEl() {
    var list = document.querySelectorAll("iframe");
    var best = null;
    var score = 0;
    var i;
    for (i = 0; i < list.length; i++) {
      var f = list[i];
      var s = (f.offsetWidth || 0) * (f.offsetHeight || 0);
      if (s > score) {
        score = s;
        best = f;
      }
    }
    return best;
  }

  function hideCtrl() {
    var i;
    var j;
    for (i = 0; i < CTRL.length; i++) {
      var nodes = document.querySelectorAll(CTRL[i]);
      for (j = 0; j < nodes.length; j++) {
        nodes[j].style.setProperty("display", "none", "important");
      }
    }
  }

  function unclip(el) {
    var n = el;
    while (n && n !== document.documentElement) {
      if (n.style) {
        n.style.setProperty("overflow", "visible", "important");
        n.style.setProperty("transform", "none", "important");
        n.style.setProperty("filter", "none", "important");
        n.style.setProperty("clip-path", "none", "important");
      }
      n = n.parentElement;
    }
  }

  function pin(el, s) {
    if (!el || !el.style || s.w < 8 || s.h < 8) return;
    try {
      el.controls = false;
      el.autoplay = true;
    } catch (e) {}
    el.style.cssText =
      "display:block!important;position:fixed!important;" +
      "left:" + s.x + "px!important;top:" + s.y + "px!important;" +
      "width:" + s.w + "px!important;height:" + s.h + "px!important;" +
      "max-width:none!important;max-height:none!important;" +
      "object-fit:contain!important;z-index:9999!important;margin:0!important;padding:0!important;" +
      "border:none!important;background:#000!important;transform:none!important";
  }

  function paint(el) {
    if (!el) return;
    cssOnce();
    var s = vp();
    var key = s.x + "," + s.y + "," + s.w + "," + s.h;
    if (el.getAttribute("data-tvbox-fill") === key) return;
    el.setAttribute("data-tvbox-fill", key);
    unclip(el);
    hideCtrl();
    pin(el, s);
  }

  function layout() {
    if (window.__tvboxSkipVideoFill) return !!window.__tvboxLayoutReady;
    var v = videoEl();
    if (v && v.videoWidth * v.videoHeight !== 0) {
      paint(v);
      window.__tvboxLayoutReady = 1;
      return true;
    }
    if (v) return false;
    var f = iframeEl();
    if (f && (f.offsetWidth || 0) >= 120 && (f.offsetHeight || 0) >= 80) {
      paint(f);
      window.__tvboxLayoutReady = 1;
      return true;
    }
    return false;
  }

  function unmute() {
    if (window.__tvboxMediaPaused) return;
    var v = videoEl();
    if (!v) return;
    try {
      v.controls = false;
      v.autoplay = true;
      v.muted = false;
      v.volume = 1;
      if (v.paused) v.play();
    } catch (e) {}
  }

  function pictureReady() {
    var v = videoEl();
    if (v) return v.videoWidth * v.videoHeight !== 0;
    var f = iframeEl();
    return !!(f && (f.offsetWidth || 0) >= 120 && (f.offsetHeight || 0) >= 80);
  }

  var readySent = 0;
  var mo = null;

  function stopWatch() {
    if (mo) {
      try {
        mo.disconnect();
      } catch (e) {}
      mo = null;
    }
  }

  function tellReady() {
    layout();
    unmute();
    if (readySent) return;
    if (!window.__tvboxLayoutReady) return;
    if (!pictureReady()) return;
    readySent = 1;
    stopWatch();
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function bindVideo() {
    var list = document.querySelectorAll("video");
    var i;
    for (i = 0; i < list.length; i++) {
      var v = list[i];
      if (v.getAttribute("data-tvbox-v")) continue;
      v.setAttribute("data-tvbox-v", "1");
      v.addEventListener("loadeddata", tellReady);
      v.addEventListener("playing", tellReady);
      v.addEventListener("canplay", tellReady);
    }
  }

  window.__tvboxRelayout = window.__tvboxRelayout || function () {
    if (window.__tvboxSkipVideoFill) return;
    var el = videoEl() || iframeEl();
    if (!el) return;
    el.removeAttribute("data-tvbox-fill");
    paint(el);
  };

  if (!window.__tvboxSkipVideoFill && document.body && window.MutationObserver) {
    try {
      mo = new MutationObserver(function () {
        if (readySent) return;
        bindVideo();
        tellReady();
      });
      mo.observe(document.body, { childList: true, subtree: true });
    } catch (e) {}
  }

  cssOnce();
  var n = 0;
  function tick() {
    if (readySent) return;
    try {
      bindVideo();
      tellReady();
      if (readySent) return;
    } catch (e) {}
    if (++n < 150) setTimeout(tick, 100);
  }
  tick();
})();
