(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    if (document.getElementById("tvbox-cctv-app-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-cctv-app-css";
    s.textContent =
      "html,body,#app{margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;height:100%!important}" +
      "#app>div>*:not(.videoPosition){display:none!important}" +
      ".videoPosition,#videoContainer{position:fixed!important;left:0!important;top:0!important;" +
      "width:100%!important;height:100%!important;z-index:9999!important;margin:0!important;padding:0!important;" +
      "background:#000!important;display:block!important}";
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
    return document.getElementById("videoContainer") || document.querySelector(".videoPosition");
  }

  function stopChromeNet() {
    var frames = document.querySelectorAll("iframe.column");
    for (var i = 0; i < frames.length; i++) {
      try {
        frames[i].src = "about:blank";
      } catch (e) {}
    }
  }

  var laid = false;
  function apply() {
    cssOnce();
    var keep = playerBox();
    if (!keep) return false;
    fill(keep);
    if (keep.id === "videoContainer" && keep.parentElement) fill(keep.parentElement);
    if (!laid) {
      stopChromeNet();
      laid = true;
      window.__tvboxLayoutReady = 1;
    }
    return true;
  }

  function bindResize() {
    if (window.__tvboxCctvAppResize) return;
    window.__tvboxCctvAppResize = 1;
    window.__tvboxRelayout = function () {
      if (window.__tvboxCctvAppRaf) return;
      window.__tvboxCctvAppRaf = 1;
      requestAnimationFrame(function () {
        window.__tvboxCctvAppRaf = 0;
        try {
          apply();
        } catch (e) {}
      });
    };
    window.addEventListener("resize", window.__tvboxRelayout);
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
    }
  }

  cssOnce();
  bindResize();

  if (window.__tvboxCctvAppDone) {
    try {
      apply();
    } catch (e) {}
    return;
  }
  if (window.__tvboxCctvAppTimer) return;
  var n = 0;
  function tick() {
    window.__tvboxCctvAppTimer = 0;
    try {
      if (apply()) {
        window.__tvboxCctvAppDone = 1;
        return;
      }
    } catch (e) {}
    if (++n < 30) window.__tvboxCctvAppTimer = setTimeout(tick, 120);
  }
  tick();
})();
