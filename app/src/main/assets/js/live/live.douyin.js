(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    if (document.getElementById("tvbox-douyin-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-douyin-css";
    s.textContent =
      "html,body{margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;height:100%!important;width:100%!important}" +
      "video::-webkit-media-controls{display:none!important}";
    parent.appendChild(s);
  }

  function isGateNode(n) {
    var t = ((n.id || "") + " " + (n.getAttribute("class") || "")).toLowerCase();
    return /captcha|verify-bar|web-login|passport/.test(t);
  }

  function hasGate() {
    return !!document.querySelector(
      "[class*='captcha'],[id*='captcha'],[class*='verify-bar'],[class*='web-login']"
    );
  }

  function playerBox() {
    return document.querySelector(".xgplayer");
  }

  function videoEl() {
    var box = playerBox();
    var v = (box && box.querySelector("video")) || document.querySelector("video");
    return v && v.videoWidth * v.videoHeight !== 0 ? v : null;
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
        if (isGateNode(c)) continue;
        c.style.setProperty("display", "none", "important");
      }
      n = p;
    }
  }

  function fillBox() {
    var el = playerBox();
    if (!el || !el.style) return false;
    cssOnce();
    if (el.getAttribute("data-tvbox-box") === "1") return true;
    el.setAttribute("data-tvbox-box", "1");
    el.style.cssText =
      "position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "width:100%!important;height:100%!important;z-index:9999!important;" +
      "margin:0!important;padding:0!important;background:#000!important;overflow:hidden!important";
    hideSiblings(el);
    try {
      window.dispatchEvent(new Event("resize"));
    } catch (e) {}
    window.__tvboxLayoutReady = 1;
    return true;
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

  var readySent = 0;
  function tellReady() {
    if (readySent) return;
    readySent = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  window.__tvboxRelayout = function () {
    var el = playerBox();
    if (el) el.removeAttribute("data-tvbox-box");
    if (videoEl()) fillBox();
  };

  if (window.__tvboxDouyinBoot) {
    if (videoEl()) {
      fillBox();
      unmute();
    }
    return;
  }
  window.__tvboxDouyinBoot = 1;

  var n = 0;
  function tick() {
    try {
      var v = videoEl();
      if (v) {
        fillBox();
        unmute();
        tellReady();
        return;
      }
      if (hasGate()) tellReady();
    } catch (e) {}
    if (++n < 32) setTimeout(tick, 250);
  }
  tick();
})();
