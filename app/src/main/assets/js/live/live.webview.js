(function () {
  if (window.__tvboxWebLiveBoot) {
    // 站点脚本每次补注都会重新定义 __tvboxRelayout，这里要把它重新包一层
    try {
      if (window.__tvboxInstallRelayout) window.__tvboxInstallRelayout();
    } catch (e) {}
    return;
  }
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
   * 站点脚本会设置 __tvboxSkipVideoFill=1 把自己标记为「已接管」。
   * 但站点一旦改版，脚本里的类名就可能全部失效，而此时通用兜底又因为该标记直接退出，
   * 结果就是网页原样显示（顶栏、线路栏、频道列表全露）。
   * 这里给站点脚本 8 秒机会：期间它没把 __tvboxLayoutReady 置 1，就由通用脚本强制接管。
   */
  var siteMode = !!window.__tvboxSkipVideoFill;
  var forceAt = siteMode ? Date.now() + 8000 : 0;

  function canFill() {
    if (!siteMode) return true;
    // 站点脚本自称已铺满（__tvboxLayoutReady=1）时不能直接相信：
    // 类名失效后它可能只把播放器塞在页面顶部，下面的线路栏、频道列表照样露着。
    // 这里按「播放器有没有真的盖住视口」复核一次。
    if (window.__tvboxLayoutReady) return !playerShown();
    return Date.now() >= forceAt;
  }

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

  function coversViewport(el, s) {
    if (!el || !el.getBoundingClientRect) return false;
    var r = el.getBoundingClientRect();
    return r.width >= s.w * 0.92 && r.height >= s.h * 0.92
        && Math.abs(r.left - s.x) <= 6 && Math.abs(r.top - s.y) <= 6;
  }

  /** 播放器是否真的铺满视口（站点脚本的 data-tvbox-box 标记只作为参考） */
  function playerShown() {
    var s = vp();
    var v = videoEl();
    if (v && coversViewport(v, s)) return true;
    var box = document.querySelector("[data-tvbox-box]");
    if (box && coversViewport(box, s)) return true;
    return false;
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
    if (!canFill()) return !!window.__tvboxLayoutReady;
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

  function tellReady() {
    layout();
    unmute();
    if (readySent) return;
    if (!window.__tvboxLayoutReady) return;
    if (!pictureReady()) return;
    readySent = 1;
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

  // 站点脚本通常自己定义了 __tvboxRelayout，但它的 layout 可能因类名失效而空转；
  // 这里保留站点版本，同时叠加通用复检，保证退出全屏/窗口变化后还能重新铺满。
  // 站点脚本补注时会覆盖掉这个函数，所以包装器做成可重复安装的。
  function installRelayout() {
    var cur = window.__tvboxRelayout;
    if (cur && cur.__tvboxWrapped) return;
    var siteRelayout = cur;
    var wrap = function () {
      if (siteRelayout) {
        try {
          siteRelayout();
        } catch (e) {}
      }
      if (!canFill()) return;
      var el = videoEl() || iframeEl();
      if (!el) return;
      el.removeAttribute("data-tvbox-fill");
      paint(el);
    };
    wrap.__tvboxWrapped = 1;
    window.__tvboxRelayout = wrap;
  }
  window.__tvboxInstallRelayout = installRelayout;
  installRelayout();

  // 站点是 SPA，出画之后仍会频繁重建 DOM（切线路、切台、弹层），
  // 每次重建都可能把注入的 <style> 冲掉、把 fixed 尺寸清空。
  // 所以观察器常驻，DOM 一变就重新补样式/重新铺满，而不是出画后就撒手不管。
  var fixTimer = 0;
  function scheduleFix() {
    if (fixTimer) return;
    fixTimer = setTimeout(function () {
      fixTimer = 0;
      try {
        cssOnce();
        bindVideo();
        layout();
        tellReady();
      } catch (e) {}
    }, 300);
  }

  if (document.body && window.MutationObserver) {
    try {
      var mo = new MutationObserver(scheduleFix);
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
    // 40 秒窗口：站点改版时可能很久才渲染出播放器，兜底要等得住
    if (++n < 400) setTimeout(tick, 100);
  }
  tick();
})();
