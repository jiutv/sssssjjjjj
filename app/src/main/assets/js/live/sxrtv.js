(function () {
  window.__tvboxSkipVideoFill = 1;

  var timer = 0;
  var readySent = 0;
  var picked = 0;

  function cssOnce() {
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.getElementById("tvbox-sxrtv-css");
    if (!s) {
      s = document.createElement("style");
      s.id = "tvbox-sxrtv-css";
    }
    s.textContent =
      "html,body,#watchtv,.player-contanier,.wd1200,.player-inner,#player,#showplayerbox{" +
      "margin:0!important;padding:0!important;width:100%!important;height:100%!important;" +
      "max-width:none!important;overflow:hidden!important;background:#000!important}" +
      "#player,#showplayerbox{" +
      "position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "z-index:9!important;background:#000!important}" +
      ".header,.nav,.tv-channel-date,#program-list,.footer,.content-line," +
      ".channels-list,.swiper-city,.date-list,.tide-control,.tide-bar,.tide-logo" +
      "{display:none!important;visibility:hidden!important;pointer-events:none!important}" +
      "video::-webkit-media-controls{display:none!important}";
    parent.appendChild(s);
  }

  function compact(s) {
    return (s || "").replace(/\s+/g, "");
  }

  function itemName(el) {
    var p = el.querySelector("p");
    return compact(p ? p.textContent : el.textContent);
  }

  function findWant(key) {
    var items = document.querySelectorAll(".channel-item");
    var want = compact(key);
    var i;
    var t;
    if (!items.length) return null;
    if (!want) return items[0];
    for (i = 0; i < items.length; i++) {
      t = itemName(items[i]);
      if (t === want) return items[i];
    }
    for (i = 0; i < items.length; i++) {
      t = itemName(items[i]);
      if (want.length >= 2 && t.indexOf(want) >= 0) return items[i];
    }
    return null;
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
    if (el.classList.contains("on")) {
      picked = 1;
      cssOnce();
      return;
    }
    if (!(window.liveList && typeof liveList.selectChannel === "function")) return;
    picked = 1;
    try {
      liveList.selectChannel(el);
    } catch (e) {
      picked = 0;
      return;
    }
    cssOnce();
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
      var v = document.querySelector("#showplayerbox video, #player video, video");
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
