(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    if (document.getElementById("tvbox-hnntv-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-hnntv-css";
    s.textContent =
      "html,body,.player{margin:0!important;padding:0!important;width:100%!important;height:100%!important;" +
      "overflow:hidden!important;background:#000!important}" +
      "header,footer,.player-list,.live-bars,#everybody-seeing," +
      ".vjs-poster,.vjs-big-play-button,.vjs-control-bar,.vjs-loading-spinner" +
      "{display:none!important;pointer-events:none!important}" +
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
    return document.querySelector(".video-box")
      || document.getElementById("myPlayer")
      || document.querySelector(".video-js")
      || document.querySelector("video");
  }

  function videoEl() {
    var box = playerBox();
    if (box && box.tagName === "VIDEO") return box;
    return (box && box.querySelector("video")) || document.querySelector("video");
  }

  function hideSiblings(el) {
    if (!el || el.getAttribute("data-tvbox-hid") === "1") return;
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
        if (tag === "SCRIPT" || tag === "STYLE" || tag === "LINK" || tag === "META") continue;
        c.style.setProperty("display", "none", "important");
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
      "z-index:" + z + "!important;background:#000!important;overflow:hidden!important;" +
      "object-fit:contain!important;transform:none!important";
  }

  function layout() {
    cssOnce();
    var s = vp();
    var box = playerBox();
    var v = videoEl();
    if (box) {
      hideSiblings(box);
      pin(box, s, 9998);
      var vjs = box.querySelector ? box.querySelector(".video-js") : null;
      if (vjs && vjs !== box) pin(vjs, s, 9999);
    }
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

  function srcOf(v) {
    return v ? ((v.currentSrc || v.src || "") + "") : "";
  }

  function unmute() {
    if (window.__tvboxMediaPaused) return;
    var v = videoEl();
    if (!v) return;
    try {
      v.muted = false;
      v.volume = 1;
      if (v.paused && v.readyState >= 1) v.play();
    } catch (e) {}
    try {
      if (window.playerObj && window.playerObj.play) window.playerObj.play();
    } catch (e2) {}
  }

  function channelIdFrom(url) {
    var m = String(url || "").match(/[?&]channelId=(\d+)/i);
    return m ? m[1] : "";
  }

  function compact(s) {
    return String(s || "").replace(/\s+/g, "");
  }

  function alias(name) {
    var n = compact(name);
    if (!n) return n;
    if (/公共|法治|社会/.test(n)) return "海南社会与法";
    if (/经视|自贸/.test(n)) return "海南自贸";
    return n;
  }

  function barLinks() {
    return document.querySelectorAll(".live-bars a");
  }

  function idOfLink(a) {
    var t = (a.getAttribute("onclick") || "") + "";
    var m = t.match(/getProgramList\((\d+)\s*,/);
    return m ? m[1] : "";
  }

  function findWant(id, name) {
    var nodes = barLinks();
    var i;
    var a;
    if (id) {
      for (i = 0; i < nodes.length; i++) {
        if (idOfLink(nodes[i]) === id) return nodes[i];
      }
    }
    var want = alias(name);
    if (!want) return null;
    for (i = 0; i < nodes.length; i++) {
      if (compact(nodes[i].textContent) === want) return nodes[i];
    }
    for (i = 0; i < nodes.length; i++) {
      a = compact(nodes[i].textContent);
      if (want.length >= 2 && a.indexOf(want) >= 0) return nodes[i];
    }
    return null;
  }

  function alreadyOn(el, id) {
    if (el && el.classList && el.classList.contains("active")) return true;
    return !!(id && id === channelIdFrom(location.href));
  }

  function pictureOk(prevSrc, needNewSrc) {
    var v = videoEl();
    if (!(v && v.videoWidth * v.videoHeight !== 0)) return false;
    if (!needNewSrc) return true;
    var src = srcOf(v);
    return !prevSrc || src !== prevSrc;
  }

  function tellReady(gen) {
    if (gen !== window.__tvboxHnntvPickGen) return false;
    if (window.__tvboxHnntvReadyGen === gen) return true;
    layout();
    unmute();
    window.__tvboxHnntvReadyGen = gen;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
    return true;
  }

  function bindVideo(gen, prevSrc, needNewSrc) {
    var v = videoEl();
    if (!v || v.getAttribute("data-tvbox-g") === String(gen)) return;
    v.setAttribute("data-tvbox-g", String(gen));
    function onEv() {
      if (gen !== window.__tvboxHnntvPickGen) return;
      layout();
      if (pictureOk(prevSrc, needNewSrc)) tellReady(gen);
    }
    v.addEventListener("loadeddata", onEv);
    v.addEventListener("playing", onEv);
    v.addEventListener("canplay", onEv);
    v.addEventListener("resize", onEv);
  }

  function waitFrame(gen, prevSrc, needNewSrc) {
    var n = 0;
    function tick() {
      if (gen !== window.__tvboxHnntvPickGen) return;
      layout();
      unmute();
      bindVideo(gen, prevSrc, needNewSrc);
      if (pictureOk(prevSrc, needNewSrc) && tellReady(gen)) return;
      if (++n < 60) setTimeout(tick, n < 12 ? 50 : 120);
      else tellReady(gen);
    }
    tick();
  }

  window.__tvboxInPageGo = function (url, liveName) {
    var id = channelIdFrom(url || location.href);
    var name = compact(liveName);
    window.__tvboxHnntvPickGen = (window.__tvboxHnntvPickGen || 0) + 1;
    var gen = window.__tvboxHnntvPickGen;
    if (window.__tvboxHnntvTimer) {
      clearTimeout(window.__tvboxHnntvTimer);
      window.__tvboxHnntvTimer = 0;
    }
    var n = 0;
    function tryClick() {
      if (gen !== window.__tvboxHnntvPickGen) return;
      window.__tvboxHnntvTimer = 0;
      cssOnce();
      var el = findWant(id, name);
      if (el || alreadyOn(null, id)) {
        var skip = alreadyOn(el, id);
        var prev = srcOf(videoEl());
        if (el && !skip) {
          try {
            el.click();
          } catch (e) {}
        }
        waitFrame(gen, prev, !skip);
        return;
      }
      if (++n < 40) window.__tvboxHnntvTimer = setTimeout(tryClick, n < 16 ? 50 : 120);
      else waitFrame(gen, srcOf(videoEl()), false);
    }
    tryClick();
  };

  window.__tvboxRelayout = function () {
    layout();
  };

  cssOnce();
  if (!window.__tvboxHnntvBoot) {
    window.__tvboxHnntvBoot = 1;
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
      window.visualViewport.addEventListener("scroll", window.__tvboxRelayout);
    }
  }
})();
