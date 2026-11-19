(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.getElementById("tvbox-nxtv-css");
    if (!s) {
      s = document.createElement("style");
      s.id = "tvbox-nxtv-css";
      s.textContent =
        "html,body,#app-container,.main-container,.channel-container{" +
        "margin:0!important;padding:0!important;width:100%!important;height:100%!important;" +
        "overflow:hidden!important;background:#000!important}" +
        ".channel-container header{" +
        "position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;" +
        "width:100%!important;height:100%!important;max-width:none!important;max-height:none!important;" +
        "margin:0!important;padding:0!important;z-index:9!important;background:#000!important}" +
        ".jnsplayer,.jnsplayer .player-player,.jnsplayer .video-js{" +
        "position:relative!important;width:100%!important;height:100%!important;" +
        "max-width:none!important;max-height:none!important;margin:0!important;padding:0!important;" +
        "background:#000!important;overflow:hidden!important}" +
        ".jnsplayer video,.jnsplayer video.jnsvideohide,video.jnsvideohide,.jnsvideohide{" +
        "position:absolute!important;left:0!important;top:0!important;right:0!important;bottom:0!important;" +
        "width:100%!important;height:100%!important;min-width:0!important;min-height:0!important;" +
        "max-width:none!important;max-height:none!important;margin:0!important;padding:0!important;" +
        "z-index:10!important;opacity:1!important;visibility:visible!important;display:block!important;" +
        "object-fit:contain!important;background:#000!important;transform:none!important}" +
        ".bodyer,.wrap-head,.playbill-wrapper,.column-wrapper,.commentbox,.newsaudio-container," +
        ".newsaudio-placeholder,.audioplayer,.weui-dialog,.weui-mask,.vjs-poster,.player-icon," +
        ".nostream,.liveinfo,.channelnav-wrapper" +
        "{display:none!important;visibility:hidden!important;pointer-events:none!important}" +
        "video::-webkit-media-controls{display:none!important}";
    }
    parent.appendChild(s);
  }

  function videoEl() {
    return document.querySelector(".jnsplayer video") || document.querySelector("video");
  }

  function unmute() {
    if (window.__tvboxMediaPaused) return;
    var v = videoEl();
    if (!v) return;
    try {
      v.classList.remove("jnsvideohide");
      v.controls = false;
      v.muted = false;
      v.volume = 1;
      if (v.paused && v.readyState >= 1) v.play();
    } catch (e) {}
  }

  function pictureReady() {
    var v = videoEl();
    return !!(v && v.videoWidth * v.videoHeight !== 0);
  }

  var readySent = 0;

  function tellReady() {
    cssOnce();
    unmute();
    if (readySent) return;
    if (!pictureReady()) return;
    readySent = 1;
    window.__tvboxLayoutReady = 1;
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
    v.addEventListener("canplay", tellReady);
  }

  window.__tvboxRelayout = function () {
    cssOnce();
  };

  cssOnce();
  if (window.__tvboxNxtvBoot) {
    bindVideo();
    tellReady();
    return;
  }
  window.__tvboxNxtvBoot = 1;

  var n = 0;
  function tick() {
    cssOnce();
    bindVideo();
    unmute();
    if (!readySent) tellReady();
    n += 1;
    if (n < 50) setTimeout(tick, 80);
    else if (n < 100) setTimeout(tick, 200);
  }
  tick();
})();
