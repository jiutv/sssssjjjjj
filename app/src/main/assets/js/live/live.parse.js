// 仅直播嗅探注入。点播只用 deep.parse.js，互不改对方选流。
// 读 <video>/<source> currentSrc（含无后缀签名链、相对路径、Shadow DOM），ext=videoel。
(function () {
    if (window.__liveParseInstalled) { return; }
    window.__liveParseInstalled = true;

    function emitVideo(url) {
        try { window.android.postMsg("addMedia", url, location.href, "videoel"); } catch (e) { }
    }

    function reportVideoSrc(v) {
        if (!v || typeof v !== "string") { return; }
        if (v.substring(0, 5) === "blob:" || v.substring(0, 11) === "javascript:") { return; }
        if (v.substring(0, 17).toLowerCase() === "data:application/") { return; }
        if (v.substring(0, 2) === "//") { v = location.protocol + v; }
        if (!/^https?:\/\//i.test(v)) {
            try { v = new URL(v, location.href).href; } catch (e) { return; }
        }
        if (!/^https?:\/\//i.test(v)) { return; }
        emitVideo(v);
    }

    function collectMediaEl(el) {
        try {
            var s = el.currentSrc || el.src;
            if (s) { reportVideoSrc(s); }
            if (!el.querySelectorAll) { return; }
            var sources = el.querySelectorAll("source");
            for (var i = 0; i < sources.length; i++) {
                reportVideoSrc(sources[i].src || sources[i].getAttribute("src"));
            }
        } catch (e) { }
    }

    function walkShadow(root) {
        if (!root || !root.querySelectorAll) { return; }
        try {
            var media = root.querySelectorAll("video, audio");
            for (var i = 0; i < media.length; i++) { collectMediaEl(media[i]); }
            var all = root.querySelectorAll("*");
            for (var j = 0; j < all.length; j++) {
                if (all[j].shadowRoot) { walkShadow(all[j].shadowRoot); }
            }
        } catch (e) { }
    }

    function scanDomVideos() {
        try { walkShadow(document); } catch (e) { }
    }

    try {
        if (typeof HTMLMediaElement !== "undefined") {
            var proto = HTMLMediaElement.prototype;
            var desc = Object.getOwnPropertyDescriptor(proto, "src");
            if (desc && desc.set && !proto.__liveSrcHooked) {
                Object.defineProperty(proto, "src", {
                    configurable: true, enumerable: desc.enumerable, get: desc.get,
                    set: function (v) { try { reportVideoSrc(v); } catch (e) { } return desc.set.call(this, v); }
                });
                proto.__liveSrcHooked = true;
            }
        }
        if (typeof Element !== "undefined" && Element.prototype.setAttribute && !Element.prototype.__liveSetAttrHooked) {
            var _setAttr = Element.prototype.setAttribute;
            Element.prototype.setAttribute = function (name, value) {
                try {
                    if (name && name.toLowerCase() === "src") {
                        var tag = this.tagName;
                        if (tag === "VIDEO" || tag === "SOURCE" || tag === "AUDIO") { reportVideoSrc(value); }
                    }
                } catch (e) { }
                return _setAttr.apply(this, arguments);
            };
            Element.prototype.__liveSetAttrHooked = true;
        }
    } catch (e) { }

    scanDomVideos();
    var scans = 0;
    var scanTimer = setInterval(function () {
        scans++;
        scanDomVideos();
        if (scans >= 40) { try { clearInterval(scanTimer); } catch (e) { } }
    }, 300);
    try {
        if (typeof MutationObserver === "function" && document.documentElement) {
            var moPending = false;
            var mo = new MutationObserver(function () {
                if (moPending) { return; }
                moPending = true;
                setTimeout(function () { moPending = false; scanDomVideos(); }, 200);
            });
            mo.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ["src"] });
        }
    } catch (e) { }
})();
