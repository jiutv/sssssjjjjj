(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    if (document.getElementById("tvbox-snrtv-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-snrtv-css";
    s.textContent =
      "html,body,.bgDark,.responsiveBox,.mainBox,.playBox,#videoBox" +
      "{margin:0!important;padding:0!important;width:100%!important;height:100%!important;" +
      "overflow:hidden!important;background:#000!important}" +
      ".logo,.header-bg,.footer,.programBox,.btnStream,.comfirmBox," +
      ".dplayer-controller,.dplayer-bezel,.dplayer-menu,.dplayer-notice" +
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
    return document.getElementById("videoBox")
      || document.querySelector(".dplayer")
      || document.querySelector(".playBox")
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
      var dp = box.querySelector ? box.querySelector(".dplayer") : null;
      if (dp && dp !== box) pin(dp, s, 9999);
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
      if (window.GLOBAL && GLOBAL.LiveVideo && GLOBAL.LiveVideo.dplayer) {
        GLOBAL.LiveVideo.dplayer.play();
      }
    } catch (e2) {}
  }

  function compact(s) {
    return String(s || "").replace(/\s+/g, "");
  }

  function channelItems() {
    return document.querySelectorAll(".btnStream li");
  }

  function findWant(name) {
    var items = channelItems();
    if (!items.length) return null;
    var raw = compact(name);
    if (!raw) return items[0];
    var i;
    var t;
    for (i = 0; i < items.length; i++) {
      if ((items[i].getAttribute("data-num") || "") === "4") continue;
      t = compact(items[i].textContent);
      if (t === raw) return items[i];
    }
    for (i = 0; i < items.length; i++) {
      if ((items[i].getAttribute("data-num") || "") === "4") continue;
      t = compact(items[i].textContent);
      if (raw.length >= 2 && t.indexOf(raw) >= 0) return items[i];
    }
    for (i = 0; i < items.length; i++) {
      if ((items[i].getAttribute("data-num") || "") === "4") continue;
      t = compact(items[i].textContent);
      if (t.length >= 2 && raw.indexOf(t) >= 0) return items[i];
    }
    return null;
  }

  function pictureOk(prevSrc, needNewSrc) {
    var v = videoEl();
    if (!(v && v.videoWidth * v.videoHeight !== 0)) return false;
    if (!needNewSrc) return true;
    var src = srcOf(v);
    return !prevSrc || src !== prevSrc;
  }

  function tellReady(gen) {
    if (gen !== window.__tvboxSnrtvPickGen) return false;
    if (window.__tvboxSnrtvReadyGen === gen) return true;
    layout();
    unmute();
    window.__tvboxSnrtvReadyGen = gen;
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
      if (gen !== window.__tvboxSnrtvPickGen) return;
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
      if (gen !== window.__tvboxSnrtvPickGen) return;
      layout();
      unmute();
      bindVideo(gen, prevSrc, needNewSrc);
      if (pictureOk(prevSrc, needNewSrc) && tellReady(gen)) return;
      if (++n < 60) setTimeout(tick, n < 12 ? 50 : 120);
      else tellReady(gen);
    }
    tick();
  }

  function liveApi() {
    try {
      return (window.GLOBAL && GLOBAL.LiveVideo) || null;
    } catch (e) {
      return null;
    }
  }

  function playerReady() {
    var api = liveApi();
    return !!(api && api.dplayer && channelItems().length);
  }

  function alreadyOn(el) {
    if (!el) return false;
    if (el.classList && el.classList.contains("select")) return true;
    var api = liveApi();
    var tag = el.getAttribute("data-num") || "";
    return !!(api && tag && String(api.tag) === tag);
  }

  window.__tvboxInPageGo = function (url, liveName) {
    window.__tvboxSnrtvPickGen = (window.__tvboxSnrtvPickGen || 0) + 1;
    var gen = window.__tvboxSnrtvPickGen;
    if (window.__tvboxSnrtvTimer) {
      clearTimeout(window.__tvboxSnrtvTimer);
      window.__tvboxSnrtvTimer = 0;
    }
    var n = 0;
    function tryClick() {
      if (gen !== window.__tvboxSnrtvPickGen) return;
      window.__tvboxSnrtvTimer = 0;
      cssOnce();
      if (!playerReady()) {
        if (++n < 40) window.__tvboxSnrtvTimer = setTimeout(tryClick, n < 16 ? 50 : 120);
        else waitFrame(gen, srcOf(videoEl()), false);
        return;
      }
      var el = findWant(liveName);
      if (el) {
        var prev = srcOf(videoEl());
        var skip = alreadyOn(el);
        if (!skip) {
          try {
            el.click();
          } catch (e) {}
        }
        waitFrame(gen, prev, !skip);
        return;
      }
      if (++n < 40) window.__tvboxSnrtvTimer = setTimeout(tryClick, n < 16 ? 50 : 120);
      else waitFrame(gen, srcOf(videoEl()), false);
    }
    tryClick();
  };

  window.__tvboxRelayout = function () {
    layout();
  };

  cssOnce();
  if (!window.__tvboxSnrtvBoot) {
    window.__tvboxSnrtvBoot = 1;
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
      window.visualViewport.addEventListener("scroll", window.__tvboxRelayout);
    }
  }
})();
