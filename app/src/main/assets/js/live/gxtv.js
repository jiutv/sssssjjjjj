(function () {
  window.__tvboxSkipVideoFill = 1;

  (function patchChunkSrc() {
    if (window.__tvboxGxtvChunk) return;
    window.__tvboxGxtvChunk = 1;
    var proto = window.HTMLScriptElement && HTMLScriptElement.prototype;
    if (!proto) return;
    var desc = Object.getOwnPropertyDescriptor(proto, "src");
    if (!(desc && desc.set)) return;
    var set = desc.set;
    Object.defineProperty(proto, "src", {
      configurable: true,
      enumerable: desc.enumerable,
      get: desc.get,
      set: function (v) {
        if (typeof v === "string" && /chunk\.[a-f0-9]+\.min\.js/i.test(v) && v.indexOf("rainbow") < 0) {
          v = "https://tv.gxtv.cn/static/js/rainbow/" + v.replace(/^[\s\S]*\//, "");
        }
        set.call(this, v);
      }
    });
  })();

  function cssOnce() {
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.getElementById("tvbox-gxtv-css");
    if (!s) {
      s = document.createElement("style");
      s.id = "tvbox-gxtv-css";
      s.textContent =
        "html,body,.Live_channel_wrap,.Live_channel,.Live_box,.Live_show,#DivVideo,.CVideo,#player{" +
        "margin:0!important;padding:0!important;width:100%!important;height:100%!important;" +
        "max-width:none!important;max-height:none!important;overflow:hidden!important;background:#000!important}" +
        "#DivVideo,#player{" +
        "position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;" +
        "z-index:9!important}" +
        "#player video,#DivVideo video,#player canvas{" +
        "position:absolute!important;left:0!important;top:0!important;width:100%!important;height:100%!important;" +
        "max-width:none!important;max-height:none!important;object-fit:contain!important;" +
        "z-index:10!important;background:#000!important}" +
        ".Header,.Live_list,.info,.Live_name,.gxntv_logo,#flashContent,.loading," +
        ".Footer,footer,.layui-layer,.layui-layer-shade,#player .controlbar" +
        "{display:none!important;visibility:hidden!important;pointer-events:none!important}" +
        "video::-webkit-media-controls{display:none!important}";
    }
    parent.appendChild(s);
  }

  function videoEl() {
    return document.querySelector("#player video")
      || document.querySelector("#DivVideo video")
      || document.querySelector("video");
  }

  function unmute() {
    if (window.__tvboxMediaPaused) return;
    var v = videoEl();
    if (!v) return;
    try {
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
  if (window.__tvboxGxtvBoot) {
    bindVideo();
    tellReady();
    return;
  }
  window.__tvboxGxtvBoot = 1;

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
