(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    if (document.getElementById("tvbox-iptv345-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-iptv345-css";
    s.textContent =
      "html,body,.ui-page,.ui-content,.ui-page-theme-a{margin:0!important;padding:0!important;" +
      "overflow:hidden!important;background:#000!important;height:100%!important;width:100%!important}" +
      ".headerNfooter,[data-role=header],[data-role=footer]," +
      "#ad-container,[id^=container-],a[href*='down.php'],.ui-grid-a,#errorTip,.ui-loader," +
      ".ui-listview li,iframe[width='1'],iframe[height='1']" +
      "{display:none!important;pointer-events:none!important}" +
      "video::-webkit-media-controls{display:none!important}";
    parent.appendChild(s);
  }

  function playerBox() {
    return document.getElementById("vstPlayer")
      || document.querySelector("video[name=vstPlayer]");
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
        if (tag === "SCRIPT" || tag === "STYLE" || tag === "LINK" || tag === "META") continue;
        c.style.setProperty("display", "none", "important");
      }
      n = p;
    }
  }

  function sweepAds() {
    var nodes = document.querySelectorAll(
      "#ad-container,iframe[src*='n6wxm'],iframe[src*='ldrws'],iframe[src*='alwaysmulticultural']"
    );
    var i;
    for (i = 0; i < nodes.length; i++) {
      try {
        nodes[i].remove();
      } catch (e) {}
    }
  }

  function fillBox() {
    var el = playerBox();
    if (!el || !el.style) return false;
    if (el.getAttribute("data-tvbox-box") === "1") return true;
    el.setAttribute("data-tvbox-box", "1");
    el.setAttribute("playsinline", "");
    el.setAttribute("webkit-playsinline", "");
    el.controls = false;
    el.autoplay = true;
    el.style.cssText =
      "position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "width:100%!important;height:100%!important;z-index:9999!important;" +
      "margin:0!important;padding:0!important;background:#000!important;" +
      "object-fit:contain!important;overflow:hidden!important";
    hideSiblings(el);
    try {
      window.dispatchEvent(new Event("resize"));
    } catch (e) {}
    window.__tvboxLayoutReady = 1;
    return true;
  }

  function unmute() {
    if (window.__tvboxMediaPaused) return;
    var v = playerBox();
    if (!v) return;
    try {
      v.controls = false;
      v.muted = false;
      v.volume = 1;
      if (v.paused && v.readyState >= 1) v.play();
    } catch (e) {}
  }

  function layout() {
    cssOnce();
    sweepAds();
    return fillBox();
  }

  var readySent = 0;
  function tellReady() {
    if (!layout()) return;
    unmute();
    if (readySent) return;
    var v = playerBox();
    if (!(v && v.videoWidth * v.videoHeight !== 0)) return;
    readySent = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function bindVideo() {
    var v = playerBox();
    if (!v || v.getAttribute("data-tvbox-v")) return;
    v.setAttribute("data-tvbox-v", "1");
    v.addEventListener("loadedmetadata", tellReady);
    v.addEventListener("loadeddata", tellReady);
    v.addEventListener("playing", tellReady);
    v.addEventListener("canplaythrough", tellReady);
  }

  window.__tvboxRelayout = function () {
    var el = playerBox();
    if (el) el.removeAttribute("data-tvbox-box");
    layout();
  };

  cssOnce();
  if (window.__tvboxIptv345Boot) {
    layout();
    bindVideo();
    tellReady();
    return;
  }
  window.__tvboxIptv345Boot = 1;

  var n = 0;
  function tick() {
    try {
      layout();
      unmute();
      bindVideo();
      tellReady();
      if (readySent) return;
    } catch (e) {}
    if (++n < 80) setTimeout(tick, n < 16 ? 50 : 120);
  }
  tick();
})();
