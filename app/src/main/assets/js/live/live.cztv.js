(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    if (document.getElementById("tvbox-cztv-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-cztv-css";
    s.textContent =
      "html,body,#app,.main_box,.content,.show{margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;height:100%!important;width:100%!important}" +
      ".channel,.list,.show-bottom,.dateWeek,header,footer,.font" +
      "{display:none!important;pointer-events:none!important}" +
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
    return document.querySelector(".ksbfq")
      || document.querySelector(".videoKS")
      || document.querySelector("video");
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
    if (box && box.tagName === "VIDEO") return box;
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

  function tellReady() {
    layout();
    unmute();
    window.__tvboxLayoutReady = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  window.__tvboxRelayout = function () {
    var el = playerBox();
    if (el) el.removeAttribute("data-tvbox-box");
    layout();
  };

  cssOnce();
  if (!window.__tvboxCztvResize) {
    window.__tvboxCztvResize = 1;
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
    }
  }

  if (window.__tvboxCztvBoot) {
    layout();
    return;
  }
  window.__tvboxCztvBoot = 1;

  var n = 0;
  var readySent = 0;
  function tick() {
    try {
      layout();
      unmute();
      if (!readySent && pictureReady()) {
        readySent = 1;
        tellReady();
      }
    } catch (e) {}
    if (++n < 40) setTimeout(tick, 150);
  }
  tick();
})();
