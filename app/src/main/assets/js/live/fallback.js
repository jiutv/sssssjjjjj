/*
 * 网页直播通用兜底脚本。
 *
 * 站点专用脚本（NewBox 那套）依赖具体 DOM 结构，站点一改版就会失效；
 * 这里不认任何 id/class，只按「面积最大的 video/canvas/iframe」来定位播放器，
 * 铺满时用 vw/vh 而不是像素，这样退出全屏、旋屏、容器尺寸变化后能自动跟随。
 */
(function () {
  if (window.__mboxFallback) {
    try { window.__mboxFallback(); } catch (e) {}
    return;
  }
  window.__mboxFallback = 1;

  var HIDDEN_TAGS = { SCRIPT: 1, STYLE: 1, LINK: 1, META: 1, BR: 1, NOSCRIPT: 1 };
  var last = 0;

  function area(el) {
    try {
      var r = el.getBoundingClientRect();
      return (r.width || 0) * (r.height || 0);
    } catch (e) {
      return 0;
    }
  }

  function pick() {
    var best = null;
    var max = 0;
    var ns = document.querySelectorAll('video,canvas');
    for (var i = 0; i < ns.length; i++) {
      var a = area(ns[i]);
      if (a > max) {
        max = a;
        best = ns[i];
      }
    }
    // 播放器在跨域 iframe 里时，只能把 iframe 本身铺满（内部 UI 改不到）
    var fs = document.querySelectorAll('iframe');
    for (var j = 0; j < fs.length; j++) {
      var fa = area(fs[j]);
      if (fa > max) {
        max = fa;
        best = fs[j];
      }
    }
    return { el: best, area: max };
  }

  function unclip(el) {
    var n = el.parentElement;
    for (var i = 0; i < 10 && n && n !== document.documentElement; i++) {
      try {
        n.style.setProperty('overflow', 'visible', 'important');
        n.style.setProperty('transform', 'none', 'important');
        n.style.setProperty('clip-path', 'none', 'important');
        n.style.setProperty('contain', 'none', 'important');
      } catch (e) {}
      n = n.parentElement;
    }
  }

  /** 从播放器往上，逐层隐藏同级节点（站点 UI 基本都在这些位置） */
  function hideUncles(el) {
    var n = el;
    for (var i = 0; i < 6 && n && n !== document.body && n !== document.documentElement; i++) {
      var p = n.parentElement;
      if (!p) break;
      var kids = p.children;
      for (var j = 0; j < kids.length; j++) {
        var c = kids[j];
        if (c === n) continue;
        if (HIDDEN_TAGS[c.tagName]) continue;
        if (c.contains(el)) continue;
        try {
          c.style.setProperty('display', 'none', 'important');
        } catch (e) {}
      }
      n = p;
    }
  }

  /** 隐藏页面外围框架：header/nav/侧栏/页脚等（不含播放器时） */
  function hideChrome(el) {
    var nodes = document.querySelectorAll('header,nav,aside,footer,' +
        '[id*=header],[id*=navbar],[id*=footer],[class*=header],[class*=navbar],[class*=footer],[class*=sidebar]');
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      if (!n || !n.style) continue;
      if (n === el || n.contains(el) || (el && el.contains(n))) continue;
      if (n.tagName === 'BODY' || n.tagName === 'HTML') continue;
      try {
        n.style.setProperty('display', 'none', 'important');
      } catch (e) {}
    }
  }

  function kick(v) {
    if (!v || v.tagName !== 'VIDEO') return;
    try {
      v.controls = false;
      v.muted = false;
      if (v.paused) {
        var p = v.play();
        if (p && p.catch) {
          p.catch(function () {
            try {
              v.muted = true;
              v.play();
            } catch (e) {}
          });
        }
      }
    } catch (e) {}
  }

  function apply() {
    var now = Date.now();
    if (now - last < 200) return;
    last = now;
    try {
      var picked = pick();
      var v = picked.el;
      if (!v) return;
      var vw = window.innerWidth || 0;
      var vh = window.innerHeight || 0;
      if (vw <= 0 || vh <= 0) return;
      // 还没起播或只是小窗广告时不动，避免把页面弄乱
      if (picked.area < vw * vh * 0.05) return;

      unclip(v);
      hideUncles(v);
      hideChrome(v);

      var s = v.style;
      s.setProperty('position', 'fixed', 'important');
      s.setProperty('left', '0', 'important');
      s.setProperty('top', '0', 'important');
      s.setProperty('right', '0', 'important');
      s.setProperty('bottom', '0', 'important');
      s.setProperty('width', '100vw', 'important');
      s.setProperty('height', '100vh', 'important');
      s.setProperty('max-width', 'none', 'important');
      s.setProperty('max-height', 'none', 'important');
      s.setProperty('margin', '0', 'important');
      s.setProperty('padding', '0', 'important');
      s.setProperty('z-index', '2147483000', 'important');
      s.setProperty('background', '#000', 'important');
      if (v.tagName === 'VIDEO') {
        s.setProperty('object-fit', 'contain', 'important');
      } else {
        s.setProperty('border', '0', 'important');
      }

      try {
        document.documentElement.style.setProperty('overflow', 'hidden', 'important');
        document.documentElement.style.setProperty('background', '#000', 'important');
        // 网页只当画面用：禁掉页面滚动与选中，触摸事件由原生层吞掉
        document.documentElement.style.setProperty('touch-action', 'none', 'important');
        document.documentElement.style.setProperty('-webkit-user-select', 'none', 'important');
        document.body.style.setProperty('overflow', 'hidden', 'important');
        document.body.style.setProperty('margin', '0', 'important');
        document.body.style.setProperty('background', '#000', 'important');
        document.body.style.setProperty('touch-action', 'none', 'important');
        document.body.style.setProperty('-webkit-user-select', 'none', 'important');
      } catch (e) {}

      kick(v);
    } catch (e) {}
  }

  window.__mboxFallback = apply;

  apply();
  var n = 0;
  var timer = setInterval(function () {
    apply();
    if (++n > 30) clearInterval(timer);
  }, 400);
  window.addEventListener('resize', apply);
  if (window.visualViewport) window.visualViewport.addEventListener('resize', apply);
  try {
    new MutationObserver(apply).observe(document.documentElement, { childList: true, subtree: true });
  } catch (e) {}

  // 兜底选台：没有站点专用脚本的页面，按频道名点一下页面里的同名频道/标签。
  // 仅在站点脚本没有定义 __tvboxInPageGo 时生效，避免和站点逻辑打架。
  if (!window.__tvboxInPageGo) {
    window.__tvboxInPageGo = function (url, liveName) {
      var name = (liveName || '').replace(/^\s+|\s+$/g, '');
      if (!name) return;
      if (window.__tvboxGenericWant === name) return;
      window.__tvboxGenericWant = name;
      var n = 0;
      function tryClick() {
        var els = document.querySelectorAll('a,li,button,[role=tab]');
        for (var i = 0; i < els.length; i++) {
          var t = (els[i].textContent || '').replace(/[\s\u00a0]+/g, '');
          if (!t || t.length > name.length + 4) continue;
          if (t === name || t.indexOf(name) >= 0) {
            var el = els[i];
            try { el.click(); } catch (e) {}
            try {
              var ev = document.createEvent('MouseEvents');
              ev.initMouseEvent('mousedown', true, true, window, 1, 0, 0, 0, 0,
                false, false, false, false, 0, null);
              el.dispatchEvent(ev);
            } catch (e) {}
            return true;
          }
        }
        return false;
      }
      function loop() {
        if (tryClick()) return;
        if (++n < 20) setTimeout(loop, 300);
      }
      setTimeout(loop, 300);
    };
  }
})();
