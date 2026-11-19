(function () {
  window.__tvboxSkipVideoFill = 1;

  function cssOnce() {
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.getElementById("tvbox-ysp-css");
    if (!s) {
      s = document.createElement("style");
      s.id = "tvbox-ysp-css";
    }
    s.textContent =
      "html,body,#app{margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;height:100%!important;width:100%!important}" +
      "header,.header,.max-footer,.footer,.tv-zhan,.tv-main-con-r{position:fixed!important;left:-9999px!important;top:0!important;opacity:0!important;pointer-events:none!important}" +
      ".tv,.tv-main,.maxWidth,.tv-main-con,.tv-main-con-l,.tv-main-con-l-vid,.max-live-height{" +
      "margin:0!important;padding:0!important;background:#000!important;overflow:hidden!important;" +
      "max-width:none!important;max-height:none!important;width:100%!important;height:100%!important}" +
      ".tv-main-con-l-vid .play-div,.tv-main-con-l-vid .play-div-div," +
      ".tv-main-con-l-vid .c-container,.tv-main-con-l-vid .video-con{" +
      "position:absolute!important;left:0!important;top:0!important;" +
      "width:100%!important;height:100%!important;padding:0!important;margin:0!important}" +
      ".tv-main-con-l-vid .y-full-control,.tv-main-con-l-vid .y-full-control-btn," +
      ".tv-main-con-l-vid .y-full-bg,.tv-main-con-l-vid .y-full," +
      ".tv-main-con-l-vid .y-control-outside,.tv-main-con-l-vid .volume-muted-tip-container," +
      ".tv-main-con-l-vid .volume-muted-tip,.tv-main-con-l-vid .video-status-tip," +
      ".tv-main-con-l-vid .con.poster,.tv-main-con-l-vid .container," +
      ".tv-main-con-l-vid .pip,.tv-main-con-l-vid .videoFull,.tv-main-con-l-vid .full," +
      ".tv-main-con-l-vid .voice,.tv-main-con-l-vid .bei,.tv-main-con-l-vid .progress-bar," +
      ".txp_controls,.txp_top,.txp_bottom,.txp_overlay,.txp_gradient_top,.txp_gradient_bottom," +
      ".txp_left_controls,.txp_right_controls,.txp_center_controls,.txp_progress,.txp_popup," +
      ".txp_btn,.txp_title,.plugin_ctrl_bar,video::-webkit-media-controls{" +
      "display:none!important;opacity:0!important;height:0!important;width:0!important;" +
      "overflow:hidden!important;pointer-events:none!important}" +
      ".tv-main-con-l-vid video,.tv-main-con-l-vid canvas{width:100%!important;height:100%!important;" +
      "object-fit:contain!important;background:#000!important}";
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
    var el = document.querySelector(".tv-main-con-l-vid");
    if (!el || !el.style) return;
    var s = vp();
    var key = s.x + "," + s.y + "," + s.w + "," + s.h;
    if (el.getAttribute("data-tvbox-box") === key) return;
    el.setAttribute("data-tvbox-box", key);
    el.style.cssText =
      "position:fixed!important;left:" + s.x + "px!important;top:" + s.y + "px!important;" +
      "width:" + s.w + "px!important;height:" + s.h + "px!important;" +
      "z-index:9999!important;margin:0!important;padding:0!important;background:#000!important;display:block!important";
  }

  function layout() {
    cssOnce();
    fillBox();
  }

  function parsePid(url) {
    try {
      var u = new URL(url || location.href, location.href);
      return (u.searchParams.get("pid") || "").trim();
    } catch (e) {
      return "";
    }
  }

  function vmHasChange(vm) {
    if (!vm) return null;
    if (typeof vm.changeTV === "function") return vm;
    var ch = vm.$children || [];
    for (var i = 0; i < ch.length; i++) {
      if (typeof ch[i].changeTV === "function") return ch[i];
    }
    return null;
  }

  function findVmDown(el, depth) {
    if (!el || depth > 8) return null;
    var hit = vmHasChange(el.__vue__);
    if (hit) return hit;
    var kids = el.children || [];
    for (var k = 0; k < kids.length && k < 16; k++) {
      hit = findVmDown(kids[k], depth + 1);
      if (hit) return hit;
    }
    return null;
  }

  function findVm() {
    var n = document.querySelector(".tv");
    while (n) {
      var hit = vmHasChange(n.__vue__);
      if (hit) return hit;
      n = n.parentElement;
    }
    return findVmDown(document.getElementById("app") || document.body, 0);
  }

  function collect(vm) {
    var out = [];
    var tabs = vm.tvTabs;
    if (tabs && tabs.length) {
      for (var i = 0; i < tabs.length; i++) {
        if (tabs[i] && tabs[i].length) out = out.concat(tabs[i]);
      }
    }
    if (vm.tabA && vm.tabA.length) out = out.concat(vm.tabA);
    if (vm.tabB && vm.tabB.length) out = out.concat(vm.tabB);
    return out;
  }

  function findItem(vm, pid) {
    if (!pid) return null;
    var list = collect(vm);
    for (var i = 0; i < list.length; i++) {
      if (list[i] && String(list[i].pid) === String(pid)) return list[i];
    }
    return null;
  }

  function videoEl() {
    return document.querySelector(".tv-main-con-l-vid video") || document.querySelector("video");
  }

  function videoSig() {
    var v = videoEl();
    if (!v) return "";
    return (v.currentSrc || v.src || "") + "@" + (v.videoWidth || 0);
  }

  function unmute() {
    if (window.__tvboxMediaPaused) return;
    var v = videoEl();
    if (!v) return;
    try {
      v.controls = false;
      v.muted = false;
      v.defaultMuted = false;
      v.volume = 1;
      v.removeAttribute("muted");
      if (v.paused) v.play();
    } catch (e) {}
  }

  function tellReady(gen) {
    if (gen !== window.__tvboxYspPickGen) return;
    window.__tvboxLayoutReady = 1;
    layout();
    unmute();
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function pictureOk(v, switched, prevSig, n) {
    if (!v) return false;
    if (switched && prevSig && n < 16 && videoSig() === prevSig) return false;
    if (v.videoWidth * v.videoHeight !== 0) return true;
    if (v.readyState >= 2 && !v.paused) return true;
    return v.currentTime > 0.05;
  }

  function waitFrame(gen, switched, prevSig) {
    var n = 0;
    function tick() {
      if (gen !== window.__tvboxYspPickGen) return;
      layout();
      if (pictureOk(videoEl(), switched, prevSig, n)) {
        tellReady(gen);
        return;
      }
      if (++n < 40) setTimeout(tick, 200);
    }
    tick();
  }

  function pick(pid) {
    layout();
    var vm = findVm();
    if (!vm) return 0;
    if (String((vm.tvIndex && vm.tvIndex.pid) || "") === String(pid)) return 1;
    var item = findItem(vm, pid);
    if (!item) return 0;
    try {
      vm.changeTV(item);
      return 2;
    } catch (e) {
      return 0;
    }
  }

  window.__tvboxInPageGo = function (url) {
    var pid = parsePid(url);
    var now = Date.now();
    if (pid && pid === window.__tvboxYspLastPid && now - (window.__tvboxYspGoAt || 0) < 1200) {
      return;
    }
    window.__tvboxYspLastPid = pid;
    window.__tvboxYspGoAt = now;
    window.__tvboxYspPickGen = (window.__tvboxYspPickGen || 0) + 1;
    var gen = window.__tvboxYspPickGen;
    if (window.__tvboxYspTimer) {
      clearTimeout(window.__tvboxYspTimer);
      window.__tvboxYspTimer = 0;
    }
    var prev = videoSig();
    var n = 0;
    function tryPick() {
      if (gen !== window.__tvboxYspPickGen) return;
      window.__tvboxYspTimer = 0;
      var st = pid ? pick(pid) : 1;
      if (st) {
        waitFrame(gen, st === 2, prev);
        return;
      }
      if (++n < 40) window.__tvboxYspTimer = setTimeout(tryPick, 150);
      else waitFrame(gen, false, "");
    }
    tryPick();
  };

  window.__tvboxRelayout = function () {
    layout();
  };

  cssOnce();
  if (!window.__tvboxYspBoot) {
    window.__tvboxYspBoot = 1;
    window.addEventListener("resize", window.__tvboxRelayout);
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
    }
  }
})();
