(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    if (document.getElementById("tvbox-ylrb-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-ylrb-css";
    s.textContent =
      "html,body,.contentBox,.main_center{margin:0!important;padding:0!important;width:100%!important;height:100%!important;" +
      "max-width:none!important;overflow:hidden!important;background:#000!important}" +
      "#playercontainer,.playerBoxDiv{position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "width:100%!important;height:100%!important;z-index:9999!important;float:none!important;" +
      "margin:0!important;padding:0!important;background:#000!important;overflow:hidden!important}" +
      "#playercontainer>*{width:100%!important;height:100%!important;max-width:none!important;max-height:none!important;" +
      "float:none!important;margin:0!important;left:0!important;top:0!important}" +
      "#playercontainer video{object-fit:cover!important}" +
      "video::-webkit-media-controls{display:none!important}";
    parent.appendChild(s);
  }

  function patchCmc() {
    var C = window.CmcMediaPlayer;
    if (!C || C.__tvboxPatch) return;
    C.__tvboxPatch = 1;
    window.CmcMediaPlayer = window.CmcPlayer = function (id, vars) {
      if (vars) {
        vars.width = "100%";
        vars.height = "100%";
      }
      return new C(id, vars);
    };
  }

  function playerBox() {
    return document.getElementById("playercontainer");
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

  function layout() {
    cssOnce();
    patchCmc();
    var el = playerBox();
    if (!el) return false;
    hideSiblings(el);
    window.__tvboxLayoutReady = 1;
    return true;
  }

  function videoEl() {
    var box = playerBox();
    return box && box.querySelector("video");
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

  function pictureReady() {
    var v = videoEl();
    if (v && v.videoWidth * v.videoHeight !== 0) return true;
    var box = playerBox();
    var f = box && box.querySelector("iframe");
    return !!(f && (f.offsetWidth || 0) >= 120 && (f.offsetHeight || 0) >= 80);
  }

  var readySent = 0;
  function tellReady() {
    layout();
    unmute();
    if (readySent) return;
    if (!pictureReady()) return;
    readySent = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function bindVideo() {
    var v = videoEl();
    if (!v || v.getAttribute("data-tvbox-v")) return;
    v.setAttribute("data-tvbox-v", "1");
    v.addEventListener("loadeddata", tellReady);
    v.addEventListener("playing", tellReady);
  }

  window.__tvboxRelayout = function () {
    layout();
  };

  if (window.__tvboxYlrbBoot) {
    layout();
    bindVideo();
    tellReady();
    return;
  }
  window.__tvboxYlrbBoot = 1;

  var n = 0;
  function tick() {
    try {
      bindVideo();
      tellReady();
      if (readySent) return;
    } catch (e) {}
    if (++n < 40) setTimeout(tick, 200);
  }
  tick();
})();
