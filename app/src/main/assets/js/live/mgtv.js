(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    if (document.getElementById("tvbox-mgtv-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-mgtv-css";
    s.textContent =
      "html,body{margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;height:100%!important;width:100%!important}" +
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
    return document.querySelector(".live-player-tv-module")
      || document.querySelector(".player-container .player");
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

  function tellReady(gen) {
    if (gen !== window.__tvboxMgtvPickGen) return;
    if (!pictureReady()) return;
    unmute();
    window.__tvboxLayoutReady = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function cameraIdFrom(url) {
    try {
      return new URL(url || location.href, location.href).searchParams.get("cameraId") || "";
    } catch (e) {}
    return "";
  }

  function compact(s) {
    return (s || "").replace(/\s+/g, "");
  }

  function channelLabel(el) {
    return (el.getAttribute("title") || el.getAttribute("data-title") || el.textContent || "");
  }

  function isSelected(el) {
    return (" " + (el.className || "") + " ").indexOf(" selected ") >= 0;
  }

  function clickById(id) {
    var links = document.querySelectorAll(".channels a.channel");
    for (var i = 0; i < links.length; i++) {
      var el = links[i];
      if ((el.getAttribute("data-channel-sid") || "") !== id) continue;
      if (isSelected(el)) return true;
      el.click();
      return true;
    }
    return false;
  }

  function clickByName(name) {
    var want = compact(name);
    if (!want) return false;
    var links = document.querySelectorAll(".channels a.channel");
    for (var i = 0; i < links.length; i++) {
      var el = links[i];
      if (compact(channelLabel(el)) !== want) continue;
      if (isSelected(el)) return true;
      el.click();
      return true;
    }
    return false;
  }

  function clickWant(id, liveName) {
    if (id) {
      if (clickById(id)) {
        window.__tvboxMgtvTab = 0;
        return true;
      }
    } else if (liveName) {
      if (clickByName(liveName)) {
        window.__tvboxMgtvTab = 0;
        return true;
      }
    } else {
      return !!playerBox();
    }
    var tabs = document.querySelectorAll(".channels .tabs .item");
    if (!tabs.length) return false;
    var i = window.__tvboxMgtvTab || 0;
    if (i >= tabs.length) {
      window.__tvboxMgtvTab = 0;
      return false;
    }
    var tab = tabs[i];
    window.__tvboxMgtvTab = i + 1;
    if ((" " + tab.className + " ").indexOf(" selected ") < 0) tab.click();
    return false;
  }

  function waitFrame(gen) {
    var n = 0;
    function tick() {
      if (gen !== window.__tvboxMgtvPickGen) return;
      try {
        layout();
        unmute();
        if (pictureReady()) {
          tellReady(gen);
          return;
        }
      } catch (e) {}
      if (++n < 48) setTimeout(tick, 150);
    }
    tick();
  }

  window.__tvboxInPageGo = function (url, liveName) {
    var id = cameraIdFrom(url);
    var name = (liveName || "").replace(/^\s+|\s+$/g, "");
    var want = id ? ("id:" + id) : (name ? ("nm:" + name) : "");
    if (want && want === window.__tvboxMgtvWant) {
      if (pictureReady()) tellReady(window.__tvboxMgtvPickGen);
      else layout();
      return;
    }
    window.__tvboxMgtvWant = want;
    window.__tvboxMgtvTab = 0;
    window.__tvboxMgtvPickGen = (window.__tvboxMgtvPickGen || 0) + 1;
    var gen = window.__tvboxMgtvPickGen;
    if (window.__tvboxMgtvTimer) {
      clearTimeout(window.__tvboxMgtvTimer);
      window.__tvboxMgtvTimer = 0;
    }
    var n = 0;
    function tryClick() {
      if (gen !== window.__tvboxMgtvPickGen) return;
      window.__tvboxMgtvTimer = 0;
      if (clickWant(id, name)) {
        waitFrame(gen);
        return;
      }
      if (++n < 24) window.__tvboxMgtvTimer = setTimeout(tryClick, 150);
      else waitFrame(gen);
    }
    tryClick();
  };

  window.__tvboxRelayout = function () {
    var el = playerBox();
    if (el) el.removeAttribute("data-tvbox-box");
    layout();
  };

  cssOnce();
  if (!window.__tvboxMgtvResize) {
    window.__tvboxMgtvResize = 1;
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
    }
  }
})();
