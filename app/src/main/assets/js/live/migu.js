(function () {
  window.__tvboxSkipVideoFill = 1;

  var readySent = 0;
  var clicked = 0;
  var uncontained = 0;
  var n = 0;

  function vp() {
    var v = window.visualViewport;
    if (v && v.width > 0 && v.height > 0) {
      return { x: v.offsetLeft || 0, y: v.offsetTop || 0, w: v.width, h: v.height };
    }
    return { x: 0, y: 0, w: window.innerWidth || 0, h: window.innerHeight || 0 };
  }

  function isPage(el) {
    return !el || el === document.body || el === document.documentElement
      || el.id === "app" || el.id === "app-shell";
  }

  function cssOnce() {
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.getElementById("tvbox-migu-css");
    if (!s) {
      s = document.createElement("style");
      s.id = "tvbox-migu-css";
    }
    s.textContent =
      "html,body,#app,#app-shell{margin:0!important;padding:0!important;overflow:hidden!important;" +
      "background:#000!important;height:100%!important;width:100%!important}" +
      ".skeleton-wraper,.skeleton-head,.skeleton-download,.skeleton-detail,.skeleton-adv," +
      ".skeleton-programe-list,.skeleton-split,.skeleton-gray," +
      "m-header,.m-header,header,.header,nav,.nav,.group-item,.loadFail," +
      "go-back-top,.popWindown,.browser-share{display:none!important;pointer-events:none!important}" +
      "#h5player_player,.h5player,.m-player,.content{margin:0!important;padding:0!important;" +
      "background:#000!important;overflow:hidden!important}" +
      "#h5player_player,.h5player,.m-player{" +
      "position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "width:100%!important;height:100%!important;z-index:9999!important;" +
      "border:0!important;border-radius:0!important;max-width:none!important;max-height:none!important;" +
      "transform:none!important}" +
      ".start-free,.h5player-control,.h5player-controls,.txp_controls,.txp_top,.txp_bottom," +
      "video::-webkit-media-controls{display:none!important;pointer-events:none!important}";
    parent.appendChild(s);
  }

  function compact(s) {
    return (s || "").replace(/\s+/g, "");
  }

  function clickPlay() {
    if (clicked) return;
    var el = document.querySelector(".start-free");
    if (!el) {
      var nodes = document.querySelectorAll("p,span,div,a,button");
      var i;
      var t;
      for (i = 0; i < nodes.length && i < 120; i++) {
        t = compact(nodes[i].innerText || nodes[i].textContent);
        if (t === "请点击播放" || t === "继续观看" || t === "免费观看") {
          el = nodes[i];
          break;
        }
      }
    }
    var v = document.querySelector("video");
    try {
      if (el) el.click();
      if (v) {
        v.click();
        if (v.paused) v.play();
      }
    } catch (e) {
      return;
    }
    if (el || v) clicked = 1;
  }

  function srcOf(v) {
    return ((v.currentSrc || v.src || "") + "").toLowerCase();
  }

  function isPlayable(v) {
    if (!v) return false;
    var s = srcOf(v);
    if (s.indexOf("iflyad") >= 0 || s.indexOf("ggv.") >= 0) return false;
    if (v.videoWidth && v.videoHeight) return true;
    return v.readyState >= 2 || v.currentTime > 0.05;
  }

  function playVideo() {
    var list = document.querySelectorAll("video");
    var i;
    for (i = 0; i < list.length; i++) {
      if (isPlayable(list[i])) return list[i];
    }
    return null;
  }

  function playerBox() {
    return document.getElementById("h5player_player")
      || document.querySelector(".h5player")
      || document.querySelector(".m-player")
      || document.querySelector(".common-player");
  }

  function uncontain(el) {
    if (uncontained) return;
    uncontained = 1;
    var n = el.parentElement;
    var i;
    for (i = 0; n && !isPage(n) && i < 8; i++) {
      if (n.style) n.style.setProperty("transform", "none", "important");
      n = n.parentElement;
    }
  }

  function fillBox() {
    var el = playerBox();
    if (!el || !el.style || isPage(el)) return false;
    uncontain(el);
    var s = vp();
    if (!(s.w > 8 && s.h > 8)) return false;
    var key = s.x + "," + s.y + "," + s.w + "," + s.h;
    if (el.getAttribute("data-tvbox-box") === key) return true;
    el.setAttribute("data-tvbox-box", key);
    el.style.cssText =
      "position:fixed!important;left:" + s.x + "px!important;top:" + s.y + "px!important;" +
      "width:" + s.w + "px!important;height:" + s.h + "px!important;" +
      "z-index:9999!important;margin:0!important;padding:0!important;" +
      "background:#000!important;display:block!important;border:0!important;" +
      "border-radius:0!important;overflow:hidden!important;transform:none!important";
    return true;
  }

  function unmute(v) {
    if (window.__tvboxMediaPaused || !v) return;
    try {
      v.controls = false;
      v.muted = false;
      v.volume = 1;
      if (v.paused) v.play();
    } catch (e) {}
  }

  function bindVideo() {
    var list = document.querySelectorAll("video");
    var i;
    var v;
    for (i = 0; i < list.length; i++) {
      v = list[i];
      if (v.getAttribute("data-tvbox-v")) continue;
      v.setAttribute("data-tvbox-v", "1");
      v.addEventListener("loadeddata", tellReady);
      v.addEventListener("playing", tellReady);
    }
  }

  function tellReady() {
    if (readySent) return;
    cssOnce();
    if (!fillBox()) return;
    var v = playVideo();
    if (!v) return;
    readySent = 1;
    window.__tvboxLayoutReady = 1;
    unmute(v);
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  window.__tvboxRelayout = function () {
    cssOnce();
    fillBox();
  };

  cssOnce();
  if (!window.__tvboxMiguBoot) {
    window.__tvboxMiguBoot = 1;
    window.addEventListener("resize", window.__tvboxRelayout);
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
    }
  }

  function tick() {
    clickPlay();
    bindVideo();
    fillBox();
    tellReady();
    if (readySent) return;
    if (++n < 80) setTimeout(tick, 120);
  }
  tick();
})();
