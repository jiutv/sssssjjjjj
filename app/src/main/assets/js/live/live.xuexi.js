(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    if (document.getElementById("tvbox-xuexi-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-xuexi-css";
    s.textContent =
      "html,body{margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;height:100%!important;width:100%!important}" +
      ".prism-controlbar,.prism-big-play-btn,.prism-detect-info,.prism-info-display," +
      ".prism-progress,.prism-fullscreen-btn,.prism-volume,.prism-cover,.prism-unmute-btn," +
      ".gr-video-player__error-player,.gr-video-player__mask-container" +
      "{display:none!important;pointer-events:none!important}" +
      "video::-webkit-media-controls{display:none!important}";
    parent.appendChild(s);
  }

  function playerBox() {
    return document.querySelector(".prism-player")
      || document.querySelector(".ynpm-video-player-wrap");
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
    if (el.getAttribute("data-tvbox-box") === "1") return true;
    el.setAttribute("data-tvbox-box", "1");
    el.style.cssText =
      "position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "width:100%!important;height:100%!important;z-index:9999!important;" +
      "margin:0!important;padding:0!important;background:#000!important;overflow:hidden!important";
    hideSiblings(el);
    try {
      window.dispatchEvent(new Event("resize"));
    } catch (e) {}
    return true;
  }

  function videoEl() {
    var box = playerBox();
    return box ? box.querySelector("video") : null;
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

  var readySent = 0;
  function tellReady() {
    if (!layout()) return;
    unmute();
    if (readySent) return;
    var v = videoEl();
    if (!(v && v.videoWidth * v.videoHeight !== 0)) return;
    readySent = 1;
    window.__tvboxLayoutReady = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function bindVideo() {
    var v = videoEl();
    if (!v || v.getAttribute("data-tvbox-v")) return;
    v.setAttribute("data-tvbox-v", "1");
    v.addEventListener("loadeddata", tellReady);
    v.addEventListener("playing", tellReady);
  }

  window.__tvboxRelayout = function () {
    var el = playerBox();
    if (el) el.removeAttribute("data-tvbox-box");
    layout();
  };

  cssOnce();
  if (window.__tvboxXuexiBoot) {
    layout();
    bindVideo();
    tellReady();
    return;
  }
  window.__tvboxXuexiBoot = 1;

  var n = 0;
  function tick() {
    try {
      layout();
      unmute();
      bindVideo();
      tellReady();
      if (readySent) return;
    } catch (e) {}
    if (++n < 40) setTimeout(tick, n < 12 ? 80 : 150);
  }
  tick();
})();
