(function () {
  window.__tvboxSkipVideoFill = 1;

  var pickGen = 0;
  var wantKey = "";
  var timer = 0;
  var laying = 0;
  var boot = 0;
  var needNew = 0;
  var prevSrc = "";
  var prevTime = 0;
  var seenGap = 0;

  function cssOnce() {
    var head = document.head || document.documentElement;
    if (!head || document.getElementById("tvbox-ysp-css")) return;
    if (!document.getElementById("tvbox-ysp-vp")) {
      var meta = document.createElement("meta");
      meta.id = "tvbox-ysp-vp";
      meta.name = "viewport";
      meta.content = "width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no";
      head.insertBefore(meta, head.firstChild);
    }
    var s = document.createElement("style");
    s.id = "tvbox-ysp-css";
    s.textContent =
      "html,body{margin:0!important;padding:0!important;overflow:hidden!important;" +
      "background:#000!important;width:100%!important;height:100%!important}" +
      ".play-div{height:auto!important;padding:0!important}" +
      "header,.header,.max-footer,.footer,.tv-zhan,.tv-main-con-r," +
      ".y-full-control,.y-control-outside,.y-progress-wrapper,.y-full-bg," +
      ".volume-muted-tip,.volume-muted-tip-container,.voice-control-cont,.video-status-tip," +
      ".tv-main-con-l-vid .con,.tv-main-con-l-vid .black-bg," +
      ".tv-main-con-l-vid .trans-black-bg,.tv-main-con-l-vid .loading," +
      ".tv-main-con-l-vid .loading-main,.tv-main-con-l-vid .loading1," +
      ".tv-main-con-l-vid .pic-loading,.tv-main-con-l-vid .poster-con," +
      ".tv-main-con-l-vid .poster-con-cover,.tv-main-con-l-vid .playBtn," +
      ".tv-main-con-l-vid .play-btn" +
      "{display:none!important;visibility:hidden!important;pointer-events:none!important}" +
      "video{object-fit:contain!important;object-position:center center!important;" +
      "transform:none!important;zoom:1!important}" +
      "video::-webkit-media-controls{display:none!important}";
    head.appendChild(s);
  }

  function vp() {
    var v = window.visualViewport;
    if (v && v.width > 0 && v.height > 0) {
      return { x: v.offsetLeft || 0, y: v.offsetTop || 0, w: v.width, h: v.height };
    }
    return { x: 0, y: 0, w: window.innerWidth || 0, h: window.innerHeight || 0 };
  }

  function playerBox() {
    return document.querySelector(".tv-main-con-l-vid");
  }

  function videoEl() {
    var box = playerBox();
    return box ? box.querySelector("video") : null;
  }

  function hideSiblings(el) {
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
        c.style.setProperty("visibility", "hidden", "important");
      }
      n = p;
    }
  }

  function unclip(el) {
    var n = el;
    while (n && n !== document.documentElement) {
      if (n.getAttribute && n.getAttribute("data-tvbox-box")) {
        n = n.parentElement;
        continue;
      }
      if (n.style) {
        n.style.setProperty("overflow", "visible", "important");
        n.style.setProperty("transform", "none", "important");
        n.style.setProperty("filter", "none", "important");
        n.style.setProperty("clip-path", "none", "important");
        n.style.setProperty("zoom", "1", "important");
        n.style.setProperty("background", "#000", "important");
      }
      n = n.parentElement;
    }
  }

  function pinBox(el, s) {
    if (!el || !el.style || s.w < 8 || s.h < 8) return;
    var key = s.x + "," + s.y + "," + s.w + "," + s.h;
    if (el.getAttribute("data-tvbox-box") === key && (el.offsetWidth || 0) >= s.w - 8) return;
    el.setAttribute("data-tvbox-box", key);
    el.style.cssText =
      "display:block!important;position:fixed!important;" +
      "left:" + s.x + "px!important;top:" + s.y + "px!important;" +
      "width:" + s.w + "px!important;height:" + s.h + "px!important;" +
      "max-width:none!important;max-height:none!important;min-width:0!important;min-height:0!important;" +
      "margin:0!important;padding:0!important;border:none!important;" +
      "z-index:9998!important;background:#000!important;overflow:hidden!important;" +
      "transform:none!important;zoom:1!important";
  }

  function fillInner(el) {
    if (!el || !el.style || el.getAttribute("data-tvbox-box")) return;
    el.style.cssText =
      "display:block!important;position:absolute!important;" +
      "left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "width:100%!important;height:100%!important;" +
      "max-width:none!important;max-height:none!important;min-width:0!important;min-height:0!important;" +
      "margin:0!important;padding:0!important;border:none!important;" +
      "background:transparent!important;overflow:hidden!important;" +
      "transform:none!important;zoom:1!important";
  }

  function fillVideo(el) {
    if (!el || !el.style) return;
    try {
      el.removeAttribute("width");
      el.removeAttribute("height");
      el.controls = false;
      el.removeAttribute("x5-video-player-fullscreen");
      el.removeAttribute("x5-video-orientation");
      el.setAttribute("playsinline", "");
      el.setAttribute("webkit-playsinline", "true");
    } catch (e) {}
    el.style.cssText =
      "display:block!important;position:absolute!important;" +
      "left:0!important;top:0!important;right:0!important;bottom:0!important;" +
      "width:100%!important;height:100%!important;" +
      "max-width:100%!important;max-height:100%!important;min-width:0!important;min-height:0!important;" +
      "margin:0!important;padding:0!important;border:none!important;" +
      "object-fit:contain!important;object-position:center center!important;" +
      "transform:none!important;zoom:1!important;background:#000!important";
  }

  function flattenPlayer(box) {
    var nodes = box.querySelectorAll("live-player,.play-div,.play-div-div,.c-container,.video-con");
    for (var i = 0; i < nodes.length; i++) fillInner(nodes[i]);
  }

  function hideSiteChrome(box) {
    var nodes = box.querySelectorAll(
      ".con,.bg,.black-bg,.trans-black-bg,.loading,.loading-main,.loading1," +
      ".pic-loading,.poster-con,.poster-con-cover,.playBtn,.play-btn"
    );
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (!el || el.tagName === "VIDEO" || el.querySelector("video")) continue;
      el.style.setProperty("display", "none", "important");
      el.style.setProperty("visibility", "hidden", "important");
      el.style.setProperty("pointer-events", "none", "important");
    }
  }

  function layoutWork() {
    cssOnce();
    var s = vp();
    if (s.w < 8 || s.h < 8) return false;
    var box = playerBox();
    var v = videoEl();
    if (!box) return false;
    hideSiblings(box);
    unclip(box);
    pinBox(box, s);
    flattenPlayer(box);
    hideSiteChrome(box);
    if (v) {
      unclip(v);
      fillVideo(v);
    }
    var ok = !!(v && s.w > 8 && s.h > 8);
    window.__tvboxLayoutReady = ok && !needNew;
    return ok;
  }

  function layout() {
    if (laying) return !!window.__tvboxLayoutReady;
    laying = 1;
    try {
      return layoutWork();
    } finally {
      laying = 0;
    }
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

  function srcOf(v) {
    if (!v) return "";
    try {
      return v.currentSrc || v.src || "";
    } catch (e) {
      return "";
    }
  }

  function markSwitch() {
    var v = videoEl();
    prevSrc = srcOf(v);
    prevTime = v ? v.currentTime || 0 : 0;
    seenGap = 0;
    needNew = 1;
    window.__tvboxLayoutReady = 0;
  }

  function pictureReady() {
    var v = videoEl();
    if (!v || v.videoWidth * v.videoHeight === 0 || v.readyState < 2) {
      if (needNew) seenGap = 1;
      return false;
    }
    if (!layout()) return false;
    if (!needNew) return true;
    var src = srcOf(v);
    if (prevSrc && src && src !== prevSrc) return true;
    if (v.currentTime + 0.35 < prevTime) return true;
    return !!seenGap;
  }

  function bindVideo(gen) {
    var v = videoEl();
    if (!v || v.getAttribute("data-tvbox-g") === String(gen)) return;
    v.setAttribute("data-tvbox-g", String(gen));
    function onEv() {
      if (gen !== pickGen) return;
      if (v.readyState < 2 || v.videoWidth * v.videoHeight === 0) seenGap = 1;
      if (pictureReady()) tellReady(gen);
    }
    v.addEventListener("emptied", onEv);
    v.addEventListener("loadstart", onEv);
    v.addEventListener("loadeddata", onEv);
    v.addEventListener("playing", onEv);
    v.addEventListener("canplay", onEv);
  }

  function tellReady(gen) {
    if (gen !== pickGen) return;
    if (!pictureReady()) return;
    needNew = 0;
    unmute();
    window.__tvboxLayoutReady = 1;
    try {
      if (window.TvboxWebLive) window.TvboxWebLive.onReady(window.__tvboxCoverSeq || 0);
    } catch (e) {}
  }

  function parsePid(url) {
    try {
      return new URL(url || location.href, location.href).searchParams.get("pid") || "";
    } catch (e) {
      return "";
    }
  }

  function compact(s) {
    return String(s || "").replace(/\s+/g, "");
  }

  function sameName(got, want) {
    var a = compact(got);
    var b = compact(want);
    if (!a || !b) return false;
    if (a === b) return true;
    return a === b + "综合" || a === b + "综合频道"
      || b === a + "综合" || b === a + "综合频道";
  }

  function vmHasChange(vm) {
    if (!vm) return null;
    if (typeof vm.changeTV === "function") return vm;
    var ch = vm.$children || [];
    for (var i = 0; i < ch.length; i++) {
      var hit = vmHasChange(ch[i]);
      if (hit) return hit;
    }
    return null;
  }

  function hallVm() {
    var n = document.querySelector(".tv-main-con-l-vid")
      || document.querySelector(".tv-main-con")
      || document.querySelector(".tv")
      || document.getElementById("app")
      || document.body;
    while (n) {
      var hit = vmHasChange(n.__vue__);
      if (hit) return hit;
      n = n.parentElement;
    }
    return vmHasChange(document.getElementById("app") && document.getElementById("app").__vue__);
  }

  function items(vm) {
    var out = [];
    var tabs = vm.tvTabs;
    var i;
    if (tabs) {
      for (i = 0; i < tabs.length; i++) {
        if (tabs[i] && tabs[i].length) out = out.concat(tabs[i]);
      }
    }
    if (vm.tabA && vm.tabA.length) out = out.concat(vm.tabA);
    if (vm.tabB && vm.tabB.length) out = out.concat(vm.tabB);
    return out;
  }

  function findItem(vm, pid, name) {
    var list = items(vm);
    var i;
    if (pid) {
      for (i = 0; i < list.length; i++) {
        if (list[i] && String(list[i].pid) === String(pid)) return list[i];
      }
    }
    if (name) {
      for (i = 0; i < list.length; i++) {
        if (list[i] && sameName(list[i].channelName, name)) return list[i];
      }
    }
    return null;
  }

  function currentPid(vm) {
    return String((vm.tvIndex && vm.tvIndex.pid) || "");
  }

  function clickChannel(name) {
    if (!name) return -1;
    var nodes = document.querySelectorAll(".tv-main-con-r-list-left-imga");
    for (var i = 0; i < nodes.length; i++) {
      if (!sameName(nodes[i].textContent, name)) continue;
      if ((" " + (nodes[i].className || "") + " ").indexOf(" tvSelect ") >= 0) return 0;
      nodes[i].click();
      return 1;
    }
    return -1;
  }

  function changeTo(pid, name) {
    var vm = hallVm();
    if (vm) {
      if (pid && currentPid(vm) === String(pid)) return 0;
      var item = findItem(vm, pid, name);
      if (item) {
        if (String(item.pid) === currentPid(vm)) return 0;
        vm.changeTV(item);
        return 1;
      }
    }
    return clickChannel(name);
  }

  function waitFrame(gen) {
    var n = 0;
    function tick() {
      if (gen !== pickGen) return;
      try {
        layout();
        bindVideo(gen);
        if (pictureReady()) {
          tellReady(gen);
          return;
        }
      } catch (e) {}
      if (++n < 72) setTimeout(tick, n < 12 ? 80 : 150);
    }
    tick();
  }

  window.__tvboxInPageGo = function (url, liveName) {
    var pid = parsePid(url || location.href);
    var name = compact(liveName);
    var next = pid ? ("pid:" + pid) : (name ? ("nm:" + name) : "");
    if (next && next === wantKey) {
      if (pictureReady()) tellReady(pickGen);
      else layout();
      return;
    }
    wantKey = next;
    pickGen += 1;
    var gen = pickGen;
    if (timer) {
      clearTimeout(timer);
      timer = 0;
    }
    var n = 0;
    function tryGo() {
      if (gen !== pickGen) return;
      timer = 0;
      cssOnce();
      if (!pid && !name) {
        waitFrame(gen);
        return;
      }
      var hit = changeTo(pid, name);
      if (hit >= 0) {
        if (hit) markSwitch();
        waitFrame(gen);
        return;
      }
      if (++n < 24) timer = setTimeout(tryGo, 150);
      else waitFrame(gen);
    }
    tryGo();
  };

  window.__tvboxRelayout = function () {
    var el = playerBox();
    if (el) el.removeAttribute("data-tvbox-box");
    layout();
  };

  cssOnce();
  if (!window.__tvboxYspResize) {
    window.__tvboxYspResize = 1;
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", window.__tvboxRelayout);
      window.visualViewport.addEventListener("scroll", window.__tvboxRelayout);
    }
    window.addEventListener("resize", window.__tvboxRelayout);
  }

  if (!boot) {
    boot = 1;
    setTimeout(function () {
      if (pickGen) return;
      pickGen = 1;
      waitFrame(1);
    }, 0);
  }
})();
