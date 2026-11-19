(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    if (document.getElementById("tvbox-cditv-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-cditv-css";
    s.textContent =
      "html,body,.content,.list-live,.mt40,.fl,.video,#J_player{margin:0!important;padding:0!important;" +
      "overflow:hidden!important;background:#000!important;width:100%!important;height:100%!important}" +
      ".header,.footer,.tab-change,.bdb,.show-title,.show-title-fix,.nav-live-tv,.mini-nav," +
      ".ck-bar,.ckplayer-prompt,.ckplayer-ckplayer .ck-bar,[class*=\"ck-bar\"]," +
      ".tempTime,#ckplayer_debug" +
      "{display:none!important;pointer-events:none!important}" +
      "#J_player{position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "z-index:9999!important;max-width:none!important;max-height:none!important}" +
      "#J_player video,#J_player canvas,#J_player .ckplayer,#J_player .ckplayer-ckplayer" +
      "{width:100%!important;height:100%!important;max-width:none!important;max-height:none!important;" +
      "object-fit:contain!important;background:#000!important;left:0!important;top:0!important}" +
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
    return document.getElementById("J_player");
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
        if (tag === "SCRIPT" || tag === "STYLE" || tag === "LINK") continue;
        c.style.setProperty("display", "none", "important");
      }
      n = p;
    }
  }

  function pin() {
    cssOnce();
    var el = playerBox();
    if (!el || !el.style) return false;
    var s = vp();
    if (s.w < 8 || s.h < 8) return false;
    var key = s.x + "," + s.y + "," + s.w + "," + s.h;
    if (el.getAttribute("data-tvbox-box") !== key || (el.offsetWidth || 0) < s.w - 8) {
      el.setAttribute("data-tvbox-box", key);
      el.style.cssText =
        "position:fixed!important;left:" + s.x + "px!important;top:" + s.y + "px!important;" +
        "width:" + s.w + "px!important;height:" + s.h + "px!important;" +
        "z-index:9999!important;margin:0!important;padding:0!important;background:#000!important;" +
        "overflow:hidden!important;max-width:none!important;max-height:none!important";
      hideSiblings(el);
    }
    var v = el.querySelector("video");
    if (v) {
      try {
        v.controls = false;
      } catch (e) {}
    }
    return true;
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

  function pictureReady() {
    var v = videoEl();
    return !!(v && v.videoWidth * v.videoHeight !== 0);
  }

  var readySent = 0;
  var mo = null;
  var boxMo = null;

  function tellReady() {
    pin();
    unmute();
    window.__tvboxLayoutReady = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function maybeReady() {
    pin();
    unmute();
    watchBox();
    if (readySent) return;
    if (!pictureReady()) return;
    readySent = 1;
    if (mo) {
      try {
        mo.disconnect();
      } catch (e) {}
      mo = null;
    }
    tellReady();
  }

  function watchBox() {
    var el = playerBox();
    if (!el || boxMo || !window.MutationObserver) return;
    try {
      boxMo = new MutationObserver(function () {
        pin();
      });
      boxMo.observe(el, { attributes: true, attributeFilter: ["style"] });
    } catch (e) {}
  }

  function bindVideo() {
    var v = videoEl();
    if (!v || v.getAttribute("data-tvbox-v")) return;
    v.setAttribute("data-tvbox-v", "1");
    v.addEventListener("loadeddata", maybeReady);
    v.addEventListener("playing", maybeReady);
    v.addEventListener("canplay", maybeReady);
  }

  window.__tvboxRelayout = function () {
    var el = playerBox();
    if (el) el.removeAttribute("data-tvbox-box");
    pin();
  };

  cssOnce();
  if (!window.__tvboxCditvResize) {
    window.__tvboxCditvResize = 1;
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
    }
  }

  if (window.__tvboxCditvBoot) {
    pin();
    watchBox();
    bindVideo();
    maybeReady();
    return;
  }
  window.__tvboxCditvBoot = 1;

  if (window.MutationObserver) {
    try {
      mo = new MutationObserver(function () {
        pin();
        watchBox();
        bindVideo();
        maybeReady();
      });
      mo.observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) {}
  }

  var n = 0;
  function tick() {
    try {
      pin();
      watchBox();
      bindVideo();
      maybeReady();
      if (readySent) return;
    } catch (e) {}
    n += 1;
    if (n < 40) setTimeout(tick, 80);
    else if (n < 70) setTimeout(tick, 200);
    else if (!readySent) tellReady();
  }
  tick();
})();
