(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.getElementById("tvbox-yntv-css");
    if (!s) {
      s = document.createElement("style");
      s.id = "tvbox-yntv-css";
      s.textContent =
        "html,body,.pindao-main-box,.pindao-main-bottom,.pindao-main-bottom-le{" +
        "margin:0!important;padding:0!important;width:100%!important;height:100%!important;" +
        "max-width:none!important;overflow:hidden!important;background:#000!important;min-width:0!important;zoom:1!important}" +
        ".pindao-main-bottom-le,#live-video{" +
        "position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;" +
        "width:100%!important;height:100%!important;z-index:9!important;background:#000!important}" +
        "#live-video video,.video-js video{" +
        "position:absolute!important;left:0!important;top:0!important;width:100%!important;height:100%!important;" +
        "object-fit:contain!important;z-index:10!important;background:#000!important}" +
        ".header,.pindao-main-topbox,.pindao-main-bottom-ri,.pindao-lanm,.category,.footer," +
        ".vjs-control-bar,.vjs-big-play-button,.vjs-poster,.vjs-loading-spinner,.vjs-text-track-display" +
        "{display:none!important;visibility:hidden!important;pointer-events:none!important}" +
        "video::-webkit-media-controls{display:none!important}";
    }
    parent.appendChild(s);
  }

  function videoEl() {
    var root = document.getElementById("live-video");
    if (root && root.tagName === "VIDEO") return root;
    if (root) {
      var inner = root.querySelector("video");
      if (inner) return inner;
    }
    return document.querySelector("video");
  }

  function srcOf(v) {
    return v ? ((v.currentSrc || v.src || "") + "") : "";
  }

  function unmute() {
    if (window.__tvboxMediaPaused) return;
    var v = videoEl();
    if (!v || v.tagName !== "VIDEO") return;
    try {
      v.controls = false;
      v.muted = false;
      v.volume = 1;
      if (v.paused && v.readyState >= 1) v.play();
    } catch (e) {}
  }

  function pictureReady() {
    var v = videoEl();
    return !!(v && v.tagName === "VIDEO" && v.videoWidth * v.videoHeight !== 0);
  }

  var readySent = 0;
  var pickGen = 0;

  function tellReady(gen) {
    cssOnce();
    unmute();
    if (gen != null && gen !== pickGen) return;
    if (readySent) return;
    if (!pictureReady()) return;
    readySent = 1;
    window.__tvboxLayoutReady = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function bindVideo(gen) {
    var v = videoEl();
    if (!v || v.tagName !== "VIDEO") return;
    if (v.getAttribute("data-tvbox-g") === String(gen)) return;
    v.setAttribute("data-tvbox-g", String(gen));
    function onEv() {
      tellReady(gen);
    }
    v.addEventListener("loadeddata", onEv);
    v.addEventListener("playing", onEv);
    v.addEventListener("canplay", onEv);
  }

  function compact(s) {
    return (s || "").replace(/\s+/g, "");
  }

  function parseKey(url, liveName) {
    var name = (liveName || "").replace(/^\s+|\s+$/g, "");
    if (name) return name;
    try {
      var u = new URL(url || location.href, location.href);
      return u.searchParams.get("name") || "";
    } catch (e) {}
    return "";
  }

  function findWant(key) {
    var items = document.querySelectorAll(".pindao-lmlist-item");
    if (!items.length) return null;
    var want = compact(key);
    if (!want) return items[0];
    var i;
    var t;
    for (i = 0; i < items.length; i++) {
      t = compact(items[i].textContent);
      if (t === want) return items[i];
    }
    for (i = 0; i < items.length; i++) {
      t = compact(items[i].textContent);
      if (want.length >= 2 && t.indexOf(want) >= 0) return items[i];
    }
    return null;
  }

  function playerReady() {
    return typeof window.jmd3 === "function" && !!window.player;
  }

  function alreadyOn(el) {
    var pd = el && el.getAttribute("data-pdname");
    return !!(pd && window.names && window.names === pd);
  }

  function waitFrame(gen, prevSrc, needNewSrc) {
    var n = 0;
    function tick() {
      if (gen !== pickGen) return;
      cssOnce();
      unmute();
      bindVideo(gen);
      var v = videoEl();
      var src = srcOf(v);
      var srcOk = !needNewSrc || !prevSrc || src !== prevSrc;
      if (srcOk && pictureReady()) {
        tellReady(gen);
        return;
      }
      if (++n < 50) setTimeout(tick, n < 20 ? 80 : 200);
    }
    tick();
  }

  window.__tvboxInPageGo = function (url, liveName) {
    pickGen += 1;
    var gen = pickGen;
    readySent = 0;
    window.__tvboxLayoutReady = 0;
    var key = parseKey(url, liveName);
    var n = 0;
    function tryClick() {
      if (gen !== pickGen) return;
      cssOnce();
      if (!playerReady()) {
        if (++n < 50) setTimeout(tryClick, n < 20 ? 50 : 120);
        else waitFrame(gen, srcOf(videoEl()), false);
        return;
      }
      var el = findWant(key);
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
      if (++n < 50) setTimeout(tryClick, n < 20 ? 50 : 120);
      else waitFrame(gen, srcOf(videoEl()), false);
    }
    tryClick();
  };

  window.__tvboxRelayout = function () {
    cssOnce();
  };

  cssOnce();
  if (window.__tvboxYntvBoot) {
    bindVideo(pickGen);
    tellReady(pickGen);
    return;
  }
  window.__tvboxYntvBoot = 1;
})();
