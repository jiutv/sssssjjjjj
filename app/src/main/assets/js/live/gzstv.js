(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    if (document.getElementById("tvbox-gzstv-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-gzstv-css";
    s.textContent =
      "html,body,#__nuxt,#__layout,.container-view,.live-main-wrap,.videoplayer-wrap{" +
      "margin:0!important;padding:0!important;background:#000!important}" +
      "html,body{width:100%!important;height:100%!important;overflow:hidden!important}" +
      ".page-header,.menubar,.gzstv-logo,.menu,.navbtn,.nav-menu,.dropdown-menu," +
      ".video-attr,.djnews,.footer,.links-wrap,.links,.site-info,.nuxt-progress," +
      ".vjs-poster,.vjs-big-play-button,.vjs-control-bar,.vjs-loading-spinner," +
      ".vjs-error-display,.vjs-text-track-display,.vjs-modal-dialog,.vjs-overlay," +
      ".vjs-dock,.vjs-title-bar,.vjs-menu,.vjs-hidden" +
      "{display:none!important;visibility:hidden!important;pointer-events:none!important;opacity:0!important}" +
      ".video-js>:not(video):not(.vjs-tech){display:none!important;pointer-events:none!important}" +
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
    return document.querySelector(".videoplayer-wrap")
      || document.querySelector(".video-js")
      || document.getElementById("vPlayer")
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
      if (n.style) {
        n.style.setProperty("overflow", "visible", "important");
        n.style.setProperty("transform", "none", "important");
        n.style.setProperty("filter", "none", "important");
        n.style.setProperty("perspective", "none", "important");
        n.style.setProperty("clip", "auto", "important");
        n.style.setProperty("clip-path", "none", "important");
        n.style.setProperty("contain", "none", "important");
        n.style.setProperty("background", "#000", "important");
      }
      n = n.parentElement;
    }
  }

  function pin(el, s, z) {
    if (!el || !el.style || s.w < 8 || s.h < 8) return;
    el.style.cssText =
      "display:block!important;position:fixed!important;" +
      "left:" + s.x + "px!important;top:" + s.y + "px!important;" +
      "width:" + s.w + "px!important;height:" + s.h + "px!important;" +
      "max-width:none!important;max-height:none!important;min-width:0!important;min-height:0!important;" +
      "margin:0!important;padding:0!important;border:none!important;" +
      "z-index:" + z + "!important;background:#000!important;overflow:visible!important;" +
      "object-fit:contain!important;transform:none!important";
  }

  function layout() {
    cssOnce();
    var s = vp();
    var box = playerBox();
    var v = videoEl();
    if (box) hideSiblings(box);
    if (v) {
      unclip(v);
      try {
        v.controls = false;
      } catch (e) {}
      pin(v, s, 10000);
    }
    window.__tvboxLayoutReady = !!(v && s.w > 8 && s.h > 8);
    return window.__tvboxLayoutReady;
  }

  function unmute() {
    if (window.__tvboxMediaPaused) return;
    var v = videoEl();
    if (!v) return;
    try {
      v.muted = false;
      v.volume = 1;
      if (v.paused) v.play();
    } catch (e) {}
  }

  function pictureReady() {
    var v = videoEl();
    return !!(v && v.videoWidth * v.videoHeight !== 0 && layout());
  }

  var readySent = 0;
  var mo = null;

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
    if (readySent) return;
    if (!pictureReady()) return;
    readySent = 1;
    tellReady();
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
    layout();
  };

  cssOnce();
  if (!window.__tvboxGzstvResize) {
    window.__tvboxGzstvResize = 1;
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
    }
  }

  if (window.__tvboxGzstvBoot) {
    layout();
    bindVideo();
    maybeReady();
    return;
  }
  window.__tvboxGzstvBoot = 1;

  if (window.MutationObserver) {
    try {
      mo = new MutationObserver(function () {
        layout();
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
