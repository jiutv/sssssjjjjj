(function () {
  window.__tvboxSkipVideoFill = 1;

  var timer = 0;
  var readySent = 0;
  var picked = 0;

  function cssOnce() {
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.getElementById("tvbox-sxtygdy-css");
    if (!s) {
      s = document.createElement("style");
      s.id = "tvbox-sxtygdy-css";
    }
    s.textContent =
      "html,body,.topMainDiv,.main_center,.player,#player{" +
      "margin:0!important;padding:0!important;width:100%!important;height:100%!important;" +
      "max-width:none!important;overflow:hidden!important;background:#000!important}" +
      ".player,#player{" +
      "position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "z-index:9!important;background:#000!important}" +
      ".headerDiv,.headerMenuDiv,.playList,.footerDiv,.flex-right,.header-jubao" +
      "{display:none!important;visibility:hidden!important;pointer-events:none!important}" +
      "video::-webkit-media-controls{display:none!important}";
    parent.appendChild(s);
  }

  function compact(s) {
    return (s || "").replace(/\s+/g, "");
  }

  function findWant(key) {
    var items = document.querySelectorAll(".list");
    var want = compact(key);
    var i;
    var t;
    if (!items.length) return null;
    if (!want) return items[0];
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

  function canSwitch() {
    try {
      return !!(window.cmcPlayer && typeof window.cmcPlayer.playerPlay === "function");
    } catch (e) {
      return false;
    }
  }

  function tellReady() {
    if (readySent) return;
    readySent = 1;
    window.__tvboxLayoutReady = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function pick(el) {
    if (picked) return;
    if (el.classList.contains("hover")) {
      if (document.querySelector("#player video") || document.querySelector("video")) {
        picked = 1;
      }
      return;
    }
    if (!canSwitch()) return;
    picked = 1;
    try {
      el.click();
    } catch (e) {
      picked = 0;
    }
  }

  window.__tvboxInPageGo = function (url, liveName) {
    var key = compact(liveName || "");
    if (!key) {
      try {
        key = compact(new URL(url || location.href, location.href).searchParams.get("name") || "");
      } catch (e) {}
    }
    readySent = 0;
    picked = 0;
    window.__tvboxLayoutReady = 0;
    if (timer) clearInterval(timer);
    cssOnce();
    var n = 0;
    timer = setInterval(function () {
      cssOnce();
      var el = findWant(key);
      if (el) pick(el);
      var v = document.querySelector("#player video") || document.querySelector("video");
      if (v && (v.readyState >= 2 || v.currentTime > 0.05)) tellReady();
      n += 1;
      if (n > 50) clearInterval(timer);
    }, 200);
  };

  window.__tvboxRelayout = function () {
    cssOnce();
  };

  cssOnce();
})();
