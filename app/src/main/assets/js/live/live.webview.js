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

  /*
   * 本 App 的定位就是「网页只当画面用」，所以不再赌站点脚本能不能把 UI 藏干净：
   * 播放器真正出画后 1.2 秒就强制接管——把 video/iframe 从原 DOM 摘出来塞到 body 顶层，
   * 并把 body 下所有其他节点隐藏（和 yourtv 同一思路）。站点怎么重建 DOM 都没用。
   * 站点脚本仍然先跑，能正常铺满的话接管只是兜底，不会有副作用。
   */
  var FORCE_AT = Date.now() + 1200;
  var FORCED = 0;
  var shadowRoots = null;
  var lastShadowScan = 0;

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

  /* 强制接管 CSS：所有播放器元素一律 fixed 全屏 */
  function forceCss() {
    if (document.getElementById("tvbox-force-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-force-css";
    s.textContent =
      "body>*{display:none!important}" +
      "video,iframe,object,embed{display:block!important;position:fixed!important;" +
      "top:0!important;left:0!important;width:100vw!important;height:100vh!important;" +
      "object-fit:contain!important;background:transparent!important;z-index:99999!important;" +
      "margin:0!important;padding:0!important;border:none!important}";
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
    // 影子 DOM 扫描很贵（* 选择器），最多 2 秒一次，避免每 100ms 拖慢页面
    var now = Date.now();
    if (!shadowRoots || now - lastShadowScan > 2000) {
      lastShadowScan = now;
      shadowRoots = [];
      var all = document.querySelectorAll("*");
      var i;
      for (i = 0; i < all.length; i++) {
        try {
          if (all[i].shadowRoot) shadowRoots.push(all[i].shadowRoot);
        } catch (e) {}
      }
    }
    var j;
    for (j = 0; j < shadowRoots.length; j++) {
      v = pickVideo(shadowRoots[j]);
      if (v) return v;
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

  function coversViewport(el, s) {
    if (!el || !el.getBoundingClientRect) return false;
    try {
      var r = el.getBoundingClientRect();
      return r.width >= s.w * 0.92 && r.height >= s.h * 0.92
          && Math.abs(r.left - s.x) <= 6 && Math.abs(r.top - s.y) <= 6;
    } catch (e) {
      return false;
    }
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

  /*
   * 强制接管：
   * 把 video 从原 DOM 里摘出来 appendChild 到 body 顶层，同时 body 所有其他子节点隐藏。
   * 站点 SPA 怎么重建都没关系——video 永远在 body 最上层。
   */
  function forceTakeover() {
    if (FORCED) return;
    // 还没找到真正出画的播放器就不能接管：forceCss 会把 body 其他节点全隐藏，
    // 万一把"后面才会出现/需要点击"的播放器也藏了，就再也播不出来了
    var probe = videoEl() || iframeEl();
    if (!probe) return;
    if (probe.tagName && probe.tagName.toLowerCase() === "video"
        && !(probe.videoWidth * probe.videoHeight)) return;
    FORCED = 1;
    forceCss();

    var v = videoEl() || iframeEl();
    if (v) {
      try {
        if (v.parentNode !== document.body) {
          try { document.body.appendChild(v); } catch (e) {}
        }
      } catch (e) {}
    }

    function repin() {
      v = videoEl() || iframeEl();
      if (!v) return;
      try {
        v.controls = false;
        v.autoplay = true;
        v.muted = false;
        v.volume = 1;
        if (v.paused) v.play();
      } catch (e) {}
      v.style.cssText =
        "display:block!important;position:fixed!important;top:0!important;left:0!important;" +
        "width:100vw!important;height:100vh!important;object-fit:contain!important;" +
        "background:transparent!important;z-index:99999!important;margin:0!important;padding:0!important;" +
        "border:none!important";
    }
    repin();
    setTimeout(repin, 300);
    setTimeout(repin, 1500);
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

  function siteActuallyCoveringViewport() {
    if (FORCED) return true;
    // 只看真正的播放器元素：站点给容器打 data-tvbox-box 也不代表用户看不到网页 UI
    // （容器可能是透明的，导航栏照样露在外面）
    var v = videoEl();
    if (v && coversViewport(v, vp())) return true;
    var f = iframeEl();
    if (f && coversViewport(f, vp())) return true;
    return false;
  }

  var readySent = 0;
  var mo = null;

  function stopWatch() {
    if (mo) {
      try { mo.disconnect(); } catch (e) {}
      mo = null;
    }
  }

  function tellReady() {
    layout();
    unmute();
    if (readySent) return;
    if (!window.__tvboxLayoutReady) return;
    if (!pictureReady()) return;
    // 关键：不能只信脚本自己标记的 ready。
    // 站点改版/尺寸异常时它可能"以为铺满了"，实际网页头部还在、画面只占下半屏，
    // 这时一旦回调 onReady 撤掉黑幕，用户就看到半个网页。必须实测覆盖视口。
    if (!FORCED && !siteActuallyCoveringViewport()) return;
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

  var siteRelayout = window.__tvboxRelayout;
  window.__tvboxRelayout = function () {
    if (siteRelayout) { try { siteRelayout(); } catch (e) {} }
    if (FORCED) { forceTakeover(); return; }
    var el = videoEl() || iframeEl();
    if (!el) return;
    el.removeAttribute("data-tvbox-fill");
    paint(el);
  };

  if (document.body && window.MutationObserver) {
    try {
      mo = new MutationObserver(function () {
        if (readySent && !FORCED) return;
        bindVideo();
        tellReady();
        if (FORCED) setTimeout(forceTakeover, 50);
      });
      mo.observe(document.body, { childList: true, subtree: true });
    } catch (e) {}
  }

  cssOnce();
  var n = 0;
  function tick() {
    try {
      bindVideo();
    } catch (e) {}
    if (!readySent) {
      try { tellReady(); } catch (e) {}
    }
    // watchdog 不能被 readySent 提前关掉：站点脚本可能误判 ready，
    // 这里要持续检查"是不是真的铺满了"，没铺满就强制接管
    if (!FORCED && FORCE_AT > 0 && Date.now() >= FORCE_AT && !siteActuallyCoveringViewport()) {
      forceTakeover();
      try { tellReady(); } catch (e) {}
    }

    if (++n < 400) setTimeout(tick, 100);
  }
  tick();

  // 原生层检测到"画面只占半屏"时直接调这个，立刻强制铺满
  window.__tvboxForceTakeover = function () {
    try {
      if (!FORCED) forceTakeover();
      tellReady();
    } catch (e) {}
  };
})();
