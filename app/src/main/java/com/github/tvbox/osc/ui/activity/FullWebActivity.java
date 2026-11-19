package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.VideoParseRuler;
import com.github.tvbox.osc.util.live.LiveSniffMemory;
import com.github.tvbox.osc.util.live.StreamScore;
import com.github.tvbox.osc.util.live.WebLiveProfile;
import com.github.tvbox.osc.util.live.WebLiveScripts;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 全屏 WebView 容器 Activity（对标 NewBox / TVBoxOS 的 webview:// 协议）
 * 支持两种模式：
 *
 * 1. BROWSE ("webview://") — 纯网页浏览模式
 *    - 不劫持任何资源，WebView 内播放 HTML5 视频
 *    - 页面自带返回/刷新/关闭按钮栏，auto-hide
 *    -遥控器 BACK 先尝试网页历史后退，再关闭 Activity
 *
 * 2. SNIFF ("webview-sniff://") — 网页嗅探模式
 *    - shouldInterceptRequest 拦截资源请求
 *    - VideoParseRuler.checkIsVideoForParse 识别 m3u8/mp4/mpd
 *    - isFilter + AdBlocker 过滤静态资源和广告
 *    - 抓到第一个有效流 → setResult(RESULT_OK, streamUrl + headers) + finish()
 *    - 嗅探超时（2 分钟）仍没抓到 → 停止嗅探，留在网页播放
 *
 * Intent extras（Activity 启动方传入）：
 *   url  (String, 必填)  —— 真实 URL（已去掉 scheme 前缀）
 *   mode (String, 可选)  —— "sniff" 或其他（默认 browse）
 *
 * 回传 extras（RESULT_OK）：
 *   RESULT_STREAM_URL    —— 嗅探到的视频流直链
 *   RESULT_STREAM_HEADERS —— Base64(JSON(headers))，供原生播放器用
 */
public class FullWebActivity extends BaseActivity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_SNIFF = "sniff";
    /** 只做网页播放，不启动嗅探（用于已知抓不到流的频道，避免白等一轮） */
    public static final String MODE_BROWSE = "browse";
    public static final String RESULT_STREAM_URL = "streamUrl";
    public static final String RESULT_STREAM_HEADERS = "streamHeaders";

    private static final long TOP_BAR_HIDE_MS = 4000L;
    // 嗅探超时：页面加载完成后 45 秒内没抓到流才留在网页播放。
    // 太短（15s）会让 SPA 站点来不及发出播放列表，导致大量频道退化成网页播放。
    private static final long SNIFF_TIMEOUT_MS = 45000L;

    private WebView mWebView;
    private FrameLayout mRootContainer;
    private LinearLayout mTopBar;
    private TextView mBtnBack;
    private TextView mBtnRefresh;
    private TextView mBtnClose;
    private TextView mTvMode;

    /** 桌面站点集合：这些站的 PC 播放器可自动播放，移动 UA 会引导下载 App 或给不可播的流 */
    static final String DESKTOP_UA = WebLiveProfile.DESKTOP_UA;

    static boolean isDesktopSite(String url) {
        return WebLiveProfile.isDesktop(url);
    }

    /**
     * 这些站点适合「网页内播放」：页面自带播放器能正常解码，只是需要把网页 UI 藏掉、播放器铺满。
     * 不走嗅探转原生，避免拿到 H.265 蓝光流后原生解码花屏。
     */
    static boolean isInPagePlayHost(String url) {
        return WebLiveProfile.isInPageHost(url);
    }

    private String mEntryUrl;
    private boolean mSniffMode = false;
    private boolean mSniffFound = false;
    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private WebChromeClient mChromeClient;

    /**
     * 候选流：同一页面会陆续发出多个 m3u8（广告、预加载、不同清晰度/编码），
     * 不能抓到第一个就切走。收集 PICK_DELAY_MS 后按分数取最优。
     */
    private static final long PICK_DELAY_MS = 2500L;
    private final HashMap<String, Integer> mCandidates = new HashMap<>();
    private boolean mPickScheduled = false;

    private final Runnable mPickBestRun = new Runnable() {
        @Override
        public void run() {
            pickBest();
        }
    };

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Runnable mHideTopBarRun = new Runnable() {
        @Override
        public void run() {
            if (mTopBar != null) mTopBar.setVisibility(View.GONE);
        }
    };

    /** 嗅探超时 —— 超时后不再拦截任何资源，网页正常渲染，并记住该地址下次不再白等 */
    private final Runnable mSniffTimeoutRun = new Runnable() {
        @Override
        public void run() {
            if (!mSniffFound) {
                LOG.i("🎬 FullWebActivity 嗅探超时，留在网页播放");
                mSniffFound = true; // 设为 true 阻止后续 shouldInterceptRequest 再拦截
                // 注意：这里不再写失败记忆。超时可能只是站点加载慢，
                // 记住会让该频道之后一直直接走网页、再也回不到原生播放。
                // 没抓到流时至少让网页播放器铺满整屏，避免停在小窗口
                try {
                    if (mWebView != null) {
                        WebLiveScripts.inject(mWebView, mEntryUrl, null);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    };

    /** 超时从页面加载完成开始计时，避免慢站点把整个窗口消耗在加载阶段 */
    private boolean mTimeoutScheduled = false;

    private void scheduleSniffTimeout() {
        if (!mSniffMode || mTimeoutScheduled) return;
        mTimeoutScheduled = true;
        mHandler.removeCallbacks(mSniffTimeoutRun);
        mHandler.postDelayed(mSniffTimeoutRun, SNIFF_TIMEOUT_MS);
    }

    /** 静音自动播放注入：轮询 15 次（每 2 秒一次），适配异步渲染的 H5 播放器 */
    private int mAutoPlayTries = 0;

    static final String AUTO_PLAY_JS =
            "(function(){"
                    + "function kick(doc){"
                    + "  var paused=false;"
                    + "  try{"
                    + "    var ms=doc.querySelectorAll('video,audio');"
                    + "    for(var i=0;i<ms.length;i++){"
                    + "      try{ms[i].muted=true;}catch(e){}"
                    + "      try{ms[i].setAttribute('playsinline','');}catch(e){}"
                    + "      try{if(ms[i].paused)paused=true;ms[i].play();}catch(e){}"
                    + "    }"
                    + "  }catch(e){}"
                    + "  if(paused){"
                    + "    try{"
                    + "      var pb=doc.querySelectorAll('.prism-big-play-btn,.vjs-big-play-button,.xgplayer-start,"
                    + "[class*=big-play],[class*=play-btn],[class*=player-play],[class*=poster-play]');"
                    + "      for(var k=0;k<pb.length;k++){"
                    + "        try{if(pb[k].offsetWidth>0||pb[k].offsetHeight>0)pb[k].click();}catch(e){}"
                    + "      }"
                    + "    }catch(e){}"
                    + "  }"
                    + "  try{"
                    + "    var fs=doc.querySelectorAll('iframe');"
                    + "    for(var j=0;j<fs.length;j++){"
                    + "      try{if(fs[j].contentDocument)kick(fs[j].contentDocument);}catch(e){}"
                    + "    }"
                    + "  }catch(e){}"
                    + "}"
                    + "kick(document);"
                    + "})();";

    /**
     * 网页内播放时把播放器铺满整屏：不搬动 video 元素，只改样式，
     * 避免重排（appendChild）导致 WebView 视频 Surface 重建后花屏。
     */
    static final String PAGE_FULLSCREEN_JS =
            "(function(){"
                    + "var st=document.getElementById('mbox-page-css');"
                    + "if(!st){st=document.createElement('style');st.id='mbox-page-css';"
                    + "(document.head||document.documentElement).appendChild(st);}"
                    + "st.textContent='html,body,#app{overflow:hidden!important;background:#000!important;"
                    + "height:100%!important;width:100%!important;margin:0!important;padding:0!important}'"
                    + "+'header,.header,.max-footer,.footer,.tv-zhan,.tv-main-con-r{display:none!important}'"
                    + "+'.tv,.tv-main,.maxWidth,.tv-main-con,.tv-main-con-l,.tv-main-con-l-vid{"
                    + "max-width:none!important;max-height:none!important;width:100%!important;"
                    + "height:100%!important;margin:0!important;padding:0!important}'"
                    + "+'.tv-main-con-l-vid{position:fixed!important;left:0!important;top:0!important;"
                    + "z-index:9999!important;background:#000!important}'"
                    + "+'.tv-main-con-l-vid video,.tv-main-con-l-vid canvas{width:100%!important;"
                    + "height:100%!important;object-fit:contain!important;background:#000!important}'"
                    + "+'.tv-main-con-l-vid [class*=control]{opacity:0!important;pointer-events:none!important}';"
                    + "try{var v=document.querySelector('.tv-main-con-l-vid video')||document.querySelector('video');"
                    + "if(v){v.muted=false;v.controls=false;if(v.paused)v.play();}}catch(e){}"
                    + "})();";

    /**
     * 页面内 JS 钩子：很多站点（如奔腾融媒等地方台）的流地址没有 .m3u8 后缀，
     * 单纯靠 shouldInterceptRequest 看 URL 识别不出来。
     * 这里在页面里挂钩 XMLHttpRequest / fetch，判断响应体是否以 #EXTM3U 开头，
     * 命中后通过 MBoxSniffer 桥回传给原生播放器；同时兜底扫描 <video> 的地址。
     */
    public static final String SNIFF_HOOK_JS = "(function(){"
            + "if(window.__mboxHookInstalled)return;window.__mboxHookInstalled=true;"
            + "function rep(u){try{if(u&&window.MBoxSniffer)window.MBoxSniffer.report(String(u));}catch(e){}}"
            + "function pl(t){return typeof t==='string'&&t.indexOf('#EXTM3U')>=0;}"
            + "var XO=XMLHttpRequest.prototype.open,XS=XMLHttpRequest.prototype.send;"
            + "XMLHttpRequest.prototype.open=function(m,u){try{this.__mboxUrl=u;}catch(e){}return XO.apply(this,arguments);};"
            + "XMLHttpRequest.prototype.send=function(){var x=this;"
            + "x.addEventListener('load',function(){try{"
            + "var ct=(x.getResponseHeader('Content-Type')||'').toLowerCase();"
            + "var u=x.responseURL||x.__mboxUrl||'';"
            + "if(ct.indexOf('mpegurl')>=0||ct.indexOf('m3u8')>=0){rep(u);return;}"
            + "if(x.responseType===''||x.responseType==='text'){if(pl(x.responseText))rep(u);}"
            + "}catch(e){}});return XS.apply(this,arguments);};"
            + "if(window.fetch){var F=window.fetch;window.fetch=function(i,o){"
            + "var u=(typeof i==='string')?i:((i&&i.url)||'');"
            + "return F.apply(this,arguments).then(function(r){try{"
            + "var ct=(r.headers.get('Content-Type')||'').toLowerCase();"
            + "if(ct.indexOf('mpegurl')>=0||ct.indexOf('m3u8')>=0){rep(r.url||u);}"
            + "else if(u.toLowerCase().indexOf('.m3u8')>=0){rep(r.url||u);}"
            + "else if(ct.indexOf('text')>=0||ct===''){"
            + "r.clone().text().then(function(t){if(pl(t))rep(r.url||u);}).catch(function(){});}"
            + "}catch(e){}return r;});};}"
            + "function scan(){try{var vs=document.querySelectorAll('video');"
            + "for(var i=0;i<vs.length;i++){var s=vs[i].currentSrc||vs[i].src||'';"
            + "if(s.indexOf('http')===0)rep(s);}}catch(e){}}"
            + "setInterval(scan,2000);document.addEventListener('DOMContentLoaded',scan);"
            + "})();";

    /** JS 层的搜索桥：页面脚本抓到播放列表地址后回传给原生 */
    private class SniffBridge {
        @android.webkit.JavascriptInterface
        public void report(final String url) {
            if (!mSniffMode || TextUtils.isEmpty(url)) return;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    onJsReport(url);
                }
            });
        }
    }

    private void relayoutWebPage() {
        if (mWebView == null) return;
        try {
            mWebView.evaluateJavascript(
                    "(function(){try{window.dispatchEvent(new Event('resize'));" +
                            "if(window.__tvboxRelayout)window.__tvboxRelayout();" +
                            "}catch(e){}})();", null);
        } catch (Throwable ignored) {
        }
    }

    private final Runnable mAutoPlayRun = new Runnable() {
        @Override
        public void run() {
            // 注意：不能因为 mSniffFound（嗅探超时）就停止补注。超时后页面还在，
            // SPA 一重建 DOM 样式就没了，网页顶栏/频道列表会重新露出来。
            if (mWebView == null || isFinishing()) return;
            // 站点多为 SPA，DOM 可能几十秒后才稳定，且重建 DOM 会把注入的 <style> 冲掉，
            // 所以只要页面还在就每 2 秒补注一次，不设次数上限
            mAutoPlayTries++;
            try {
                mWebView.evaluateJavascript(SNIFF_HOOK_JS, null);
                mWebView.evaluateJavascript(AUTO_PLAY_JS, null);
                // 首次注入同时触发页内切台；之后只补样式/铺满，不重复切台
                if (mAutoPlayTries == 1) {
                    WebLiveScripts.inject(mWebView, mEntryUrl, null);
                } else {
                    WebLiveScripts.injectScripts(mWebView, mEntryUrl);
                }
            } catch (Throwable t) {
                LOG.i("autoPlay 注入失败: " + t);
            }
            mHandler.postDelayed(this, 2000);
        }
    };

    private void scheduleAutoPlay() {
        mHandler.removeCallbacks(mAutoPlayRun);
        mAutoPlayTries = 0;
        mHandler.postDelayed(mAutoPlayRun, 800);
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_full_web;
    }

    @Override
    protected void init() {
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWebView = findViewById(R.id.full_web_view);
        // 窗口/全屏尺寸变化时同步网页视口：网页里 fixed 定位的播放器不会自动跟着缩，
        // 不然退出全屏后画面还按全屏尺寸排版，站点自己的频道列表又露出来
        mWebView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                    relayoutWebPage();
                }
            }
        });
        mRootContainer = findViewById(R.id.root_container);
        mTopBar = findViewById(R.id.top_bar);
        mBtnBack = findViewById(R.id.btn_back);
        mBtnRefresh = findViewById(R.id.btn_refresh);
        mBtnClose = findViewById(R.id.btn_close);
        mTvMode = findViewById(R.id.tv_mode);

        // 沉浸式全屏，网页铺满整屏
        ImmersionBar.with(this)
                .statusBarColor(R.color.black)
                .navigationBarColor(R.color.black)
                .fitsSystemWindows(false)
                .hideBar(BarHide.FLAG_HIDE_BAR)
                .init();

        Intent intent = getIntent();
        mEntryUrl = intent.getStringExtra(EXTRA_URL);
        String mode = intent.getStringExtra(EXTRA_MODE);
        // 央视频这类站点的网页播放器在本机解码正常（用户实测画面正确），
        // 但把它嗅探出的流交给原生播放器会花屏（蓝光通常是 H.265）。
        // 因此这些站点固定走「网页内播放 + 脚本铺满」，不再做嗅探切换。
        mSniffMode = MODE_SNIFF.equals(mode) && !isInPagePlayHost(mEntryUrl);

        if (TextUtils.isEmpty(mEntryUrl)) {
            LOG.i("🎬 FullWebActivity 未收到 url，关闭");
            finish();
            return;
        }

        initWebView();

        mBtnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideTopBar();
                if (mWebView != null && mWebView.canGoBack()) {
                    mWebView.goBack();
                } else {
                    finish();
                }
            }
        });
        mBtnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideTopBar();
                if (mWebView != null) mWebView.reload();
            }
        });
        mBtnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        showTopBar();
        mTvMode.setText(mSniffMode ? "嗅探模式：抓到流自动切换播放器" : "浏览模式：仅网页内播放");

        LOG.i("🎬 FullWebActivity 加载: url=" + mEntryUrl + " mode=" + (mSniffMode ? "sniff" : "browse"));
        mWebView.loadUrl(mEntryUrl);
    }

    /**
     * 初始化 WebView
     * - 央视频/央视网用桌面 Chrome UA，其余站点保持默认 UA
     * - 开启 JS、自动播放、Cookie 全放行
     * - 嗅探模式下挂 SniffWebViewClient，浏览模式下挂 BrowseWebViewClient
     */
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void initWebView() {
        WebSettings s = mWebView.getSettings();

        // 站点差异：央媒/卫视这类对移动 UA 只给 MSE 加密流或引导下载 App，
        // 按域名改用桌面 UA；其余站点用移动 UA（很多站点的适配逻辑以移动端为准）
        s.setUserAgentString(WebLiveProfile.uaFor(mEntryUrl));

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        // 允许网页视频自动播放（不走用户手势）
        s.setMediaPlaybackRequiresUserGesture(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
        }

        // 嗅探模式：暴露 JS 桥，页面脚本抓到播放列表地址后回传
        if (mSniffMode) {
            mWebView.addJavascriptInterface(new SniffBridge(), "MBoxSniffer");
        }

        // Cookie 全放行
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(mWebView, true);
        }

        // WebChromeClient：承接网页触发的全屏视频视图
        mChromeClient = new WebChromeClient() {
            @Override
            public View getVideoLoadingProgressView() {
                return new View(FullWebActivity.this);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (mRootContainer == null || view == null) return;
                if (mCustomView != null && mCustomViewCallback != null) {
                    mCustomViewCallback.onCustomViewHidden();
                }
                mCustomView = view;
                mCustomViewCallback = callback;
                mWebView.setVisibility(View.GONE);
                try {
                    mRootContainer.addView(view, new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
                    mRootContainer.bringChildToFront(view);
                } catch (Exception e) {
                    LOG.e("🎬 网页全屏失败：" + e.getMessage());
                }
                if (mTopBar != null) mTopBar.setVisibility(View.GONE);
                LOG.i("🎬 网页请求全屏，已由原生层接管");
            }

            @Override
            public void onHideCustomView() {
                if (mRootContainer != null && mCustomView != null) {
                    mRootContainer.removeView(mCustomView);
                }
                mCustomView = null;
                if (mCustomViewCallback != null) {
                    mCustomViewCallback.onCustomViewHidden();
                    mCustomViewCallback = null;
                }
                if (mWebView != null) mWebView.setVisibility(View.VISIBLE);
                showTopBar();
            }
        };
        mWebView.setWebChromeClient(mChromeClient);

        mWebView.setWebViewClient(mSniffMode ? new SniffWebViewClient() : new BrowseWebViewClient());
    }

    /** 浏览模式 Client —— 只做页面加载回调，不劫持资源 */
    private class BrowseWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            showTopBar();
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            showTopBar();
            super.onPageFinished(view, url);
            scheduleAutoPlay();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }
    }

    /**
     * 嗅探模式 Client
     * - shouldInterceptRequest 识别视频流 → setResult 回传 + finish
     * - isFilter + AdBlocker.isAd 过滤静态资源和广告
     * - 嗅探命中 / 超时 后不再拦截，让页面正常渲染
     */
    private class SniffWebViewClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            showTopBar();
            super.onPageStarted(view, url, favicon);
            // 尽早注入钩子，尽量赶在播放器发起请求之前
            try {
                view.evaluateJavascript(SNIFF_HOOK_JS, null);
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            showTopBar();
            super.onPageFinished(view, url);
            try {
                view.evaluateJavascript(SNIFF_HOOK_JS, null);
            } catch (Throwable ignored) {
            }
            scheduleSniffTimeout();
            scheduleAutoPlay();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (mSniffFound) return null; // 已命中或已超时，不再拦截

            String url = request.getUrl().toString();
            if (!url.startsWith("http")) return null;
            if (url.endsWith("/favicon.ico")) return null;

            // 被动嗅探：只观察，不替换任何响应。
            // 之前用 createEmptyResource() 屏蔽广告/过滤项会把站点播放器的配置请求打成空响应，
            // 导致网页里连声音都没有，这里改为一律放行。
            if (!isLiveStream(url)) return null;

            int score = scoreUrl(url);
            LOG.i("🎬 候选流 [" + score + "] " + url);
            if (score <= 0) return null;

            synchronized (mCandidates) {
                mCandidates.put(url, score);
            }
            // 收集窗口内继续观察，结束后统一挑分数最高的那个，
            // 避免把广告 / 预加载 / 低清副本提前交给原生播放器
            if (!mPickScheduled) {
                mPickScheduled = true;
                mHandler.postDelayed(mPickBestRun, PICK_DELAY_MS);
            }
            return null;
        }

        /** 给候选流打分：直播播放列表优先，广告/预告/试看直接判负 */
        private int scoreUrl(String url) {
            return StreamScore.score(mEntryUrl, url);
        }

        /**
         * 只接受「直播流」地址：
         * - 必须看起来是播放列表/容器：m3u8 / mpd / flv / m3u / manifest
         * - 普通 .mp4 多为页面宣传片、轮播广告，只有路径明确是直播时才接受
         * - 排除单片段 .ts / 图片等，避免把广告分片交给播放器造成黑屏
         */
        private boolean isLiveStream(String url) {
            return StreamScore.looksLikeLive(url);
        }
    }

    /**
     * 页面脚本通过 JS 桥上报的流地址（很多站点地址没有后缀，URL 规则识别不到）。
     * 这类地址直接给高分，优先于普通候选。
     */
    private void onJsReport(String url) {
        if (mSniffFound || isFinishing()) return;
        String l = url.toLowerCase();
        if (!l.startsWith("http")) return;
        if (StreamScore.isAd(url)) {
            LOG.i("🎬 JS 上报流丢弃（广告特征） " + url);
            return;
        }
        // 内容已验证过是播放列表，给高分基准；清晰度等启发式只做微调
        int bonus = Math.max(0, Math.min(StreamScore.score(mEntryUrl, url), 40));
        int score = 200 + bonus;
        if (l.contains(".mp4") && !l.contains("/live/") && !l.contains("/stream/") && !l.contains("/hls/")) {
            // 无后缀判定的 mp4 多为宣传片/预告，降权但仍保留
            score = 30;
        }
        LOG.i("🎬 JS 上报流 [" + score + "] " + url);
        synchronized (mCandidates) {
            mCandidates.put(url, score);
        }
        if (!mPickScheduled) {
            mPickScheduled = true;
            mHandler.postDelayed(mPickBestRun, PICK_DELAY_MS);
        }
    }

    /** 收集窗口结束：挑最高分候选回传给原生播放器 */
    private void pickBest() {
        if (isFinishing() || mSniffFound) return;
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        synchronized (mCandidates) {
            for (Map.Entry<String, Integer> e : mCandidates.entrySet()) {
                if (e.getValue() > bestScore) {
                    bestScore = e.getValue();
                    best = e.getKey();
                }
            }
        }
        if (best == null || bestScore <= 0) {
            // 没有合格候选：继续观察，等下一批
            mPickScheduled = false;
            return;
        }
        mSniffFound = true;
        mHandler.removeCallbacksAndMessages(null);
        LiveSniffMemory.markSuccess(mEntryUrl);
        LOG.i("🎬 选定直播流 [" + bestScore + "] " + best);
        Intent result = new Intent();
        result.putExtra(RESULT_STREAM_URL, best);
        result.putExtra(RESULT_STREAM_HEADERS, encodeHeaders(buildHeaders(best)));
        setResult(RESULT_OK, result);
        finish();
    }

    /** 用当前页面 Cookie + UA 组装播放器需要的请求头 */
    private HashMap<String, String> buildHeaders(String url) {
        HashMap<String, String> headers = new HashMap<>();
        String ua = mWebView != null ? mWebView.getSettings().getUserAgentString() : null;
        if (!TextUtils.isEmpty(ua)) headers.put("User-Agent", ua);
        if (!TextUtils.isEmpty(mEntryUrl)) {
            try {
                String origin = new java.net.URL(mEntryUrl).getProtocol() + "://"
                        + new java.net.URL(mEntryUrl).getHost();
                headers.put("Referer", mEntryUrl);
                headers.put("Origin", origin);
            } catch (Exception ignored) {
            }
        }
        String cookie = CookieManager.getInstance().getCookie(url);
        if (!TextUtils.isEmpty(cookie)) headers.put("Cookie", cookie);
        return headers;
    }

    /** 把 headers Map 序列化为 base64(JSON)，方便 Intent extras 传递 */
    public static String encodeHeaders(HashMap<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "";
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, String> e : headers.entrySet()) {
                obj.put(e.getKey(), e.getValue());
            }
            return Base64.encodeToString(obj.toString().getBytes(), Base64.NO_WRAP);
        } catch (Exception e) {
            LOG.e("🎬 encodeHeaders 失败：" + e.getMessage());
            return "";
        }
    }

    /** 把 base64(JSON) 反序列化为 headers Map */
    public static HashMap<String, String> decodeHeaders(String encoded) {
        HashMap<String, String> map = new HashMap<>();
        if (TextUtils.isEmpty(encoded)) return map;
        try {
            String json = new String(Base64.decode(encoded, Base64.NO_WRAP));
            JSONObject obj = new JSONObject(json);
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String v = obj.optString(k, "");
                // headers 前可能有个前导空格（FullWebActivity 特意加的以对齐某些播放器要求）
                if (v.startsWith(" ")) v = v.substring(1);
                map.put(k, v);
            }
        } catch (Exception e) {
            LOG.e("🎬 decodeHeaders 失败：" + e.getMessage());
        }
        return map;
    }

    // ============ 顶部栏 auto-hide ============

    private void showTopBar() {
        mHandler.removeCallbacks(mHideTopBarRun);
        if (mTopBar != null) mTopBar.setVisibility(View.VISIBLE);
        mHandler.postDelayed(mHideTopBarRun, TOP_BAR_HIDE_MS);
    }

    private void hideTopBar() {
        mHandler.removeCallbacks(mHideTopBarRun);
        if (mTopBar != null) mTopBar.setVisibility(View.GONE);
    }

    // ============ 遥控器按键 ============

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 网页全屏中：先退出全屏
            if (mCustomView != null && mChromeClient != null) {
                mChromeClient.onHideCustomView();
                return true;
            }
            if (mTopBar != null && mTopBar.getVisibility() == View.VISIBLE) {
                hideTopBar();
                return true;
            }
            if (mWebView != null && mWebView.canGoBack()) {
                mWebView.goBack();
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (mTopBar != null) {
                if (mTopBar.getVisibility() == View.VISIBLE) {
                    hideTopBar();
                } else {
                    showTopBar();
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ============ 生命周期 ============

    @Override
    protected void onPause() {
        super.onPause();
        if (mWebView != null) mWebView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWebView != null) {
            mWebView.onResume();
            showTopBar();
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        if (mWebView != null) {
            mWebView.stopLoading();
            mWebView.loadUrl("about:blank");
            mWebView.clearCache(true);
            mWebView.clearHistory();
            mWebView.removeAllViews();
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroy();
    }
}
