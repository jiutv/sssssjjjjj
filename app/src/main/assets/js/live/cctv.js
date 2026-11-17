(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    if (document.getElementById("tvbox-cctv-css")) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement("style");
    s.id = "tvbox-cctv-css";
    s.textContent =
      "html,body,#bid{margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;height:100%!important;width:100%!important}" +
      "#page_head_lt31,.video_right,.video_btnBar,.video_btn_l,.nav_gg,#yspiframe,.vspace," +
      ".leftBar,.share,.download,.shoucang,.floatNav,.column_wrapper,.playingVideo~*," +
      "#control_bar_player,#timeshift_bar_player,#timeshift_epg_player,#timeshiftbtn_player," +
      "#pic_in_pic_player,#contextmenu_player,#play_or_plause_player," +
      "#player_fullscreen_player,#player_pagefullscreen_player,#player_sound_btn_player," +
      "#player_resolution_player,#audio_player,#loading_player{display:none!important;pointer-events:none!important}" +
      "#h5player_player,#player video,#player canvas{width:100%!important;height:100%!important;" +
      "object-fit:contain!important;background:#000!important}" +
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

  function fillBox() {
    var el = document.getElementById("player") || document.querySelector(".video_flash");
    if (!el || !el.style) return false;
    var s = vp();
    var key = s.x + "," + s.y + "," + s.w + "," + s.h;
    if (el.getAttribute("data-tvbox-box") === key) return true;
    el.setAttribute("data-tvbox-box", key);
    el.style.cssText =
      "position:fixed!important;left:" + s.x + "px!important;top:" + s.y + "px!important;" +
      "width:" + s.w + "px!important;height:" + s.h + "px!important;" +
      "z-index:9999!important;margin:0!important;padding:0!important;background:#000!important";
    if (!window.__tvboxCctvHid) {
      window.__tvboxCctvHid = 1;
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
    window.__tvboxLayoutReady = 1;
    return true;
  }

  function videoEl() {
    var box = document.getElementById("player");
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

  function tellReady() {
    layout();
    unmute();
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  window.__tvboxRelayout = function () {
    layout();
  };

  cssOnce();
  if (!window.__tvboxCctvResize) {
    window.__tvboxCctvResize = 1;
    window.addEventListener("resize", window.__tvboxRelayout);
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
    }
  }

  if (window.__tvboxCctvBoot) {
    layout();
    return;
  }
  window.__tvboxCctvBoot = 1;

  var n = 0;
  function tick() {
    try {
      layout();
      unmute();
      var v = videoEl();
      if (v && v.videoWidth * v.videoHeight !== 0) {
        tellReady();
        return;
      }
    } catch (e) {}
    if (++n < 40) setTimeout(tick, 200);
    else tellReady();
  }
  tick();
})();
