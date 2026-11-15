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
    public static final String RESULT_STREAM_URL = "streamUrl";
    public static final String RESULT_STREAM_HEADERS = "streamHeaders";

    private static final long TOP_BAR_HIDE_MS = 4000L;
    // 嗅探超时：2 分钟没抓到流就放弃（留用户手动操作网页）
    private static final long SNIFF_TIMEOUT_MS = 120000L;

    private WebView mWebView;
    private FrameLayout mRootContainer;
    private LinearLayout mTopBar;
    private TextView mBtnBack;
    private TextView mBtnRefresh;
    private TextView mBtnClose;
    private TextView mTvMode;

    /** 桌面站点集合：这些站的 PC 播放器可自动播放，移动 UA 会引导下载 App 或给不可播的流 */
    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static boolean isDesktopSite(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        return l.contains("yangshipin.cn") || l.contains("cctv.com")
                || l.contains("cctv.cn") || l.contains("hebtv.com");
    }

    /**
     * 这些站点适合「网页内播放」：页面自带播放器能正常解码，只是需要把网页 UI 藏掉、播放器铺满。
     * 不走嗅探转原生，避免拿到 H.265 蓝光流后原生解码花屏。
     */
    private static boolean isInPagePlayHost(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        return l.contains("yangshipin.cn");
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

    /** 嗅探超时 —— 超时后不再拦截任何资源，网页正常渲染 */
    private final Runnable mSniffTimeoutRun = new Runnable() {
        @Override
        public void run() {
            if (!mSniffFound) {
                LOG.i("🎬 FullWebActivity 嗅探超时，留在网页播放");
                mSniffFound = true; // 设为 true 阻止后续 shouldInterceptRequest 再拦截
            }
        }
    };

    /** 静音自动播放注入：轮询 15 次（每 2 秒一次），适配异步渲染的 H5 播放器 */
    private int mAutoPlayTries = 0;

    private static final String AUTO_PLAY_JS =
            "(function(){"
                    + "function kick(doc){"
                    + "  try{"
                    + "    var ms=doc.querySelectorAll('video,audio');"
                    + "    for(var i=0;i<ms.length;i++){"
                    + "      try{ms[i].muted=true;}catch(e){}"
                    + "      try{ms[i].play();}catch(e){}"
                    + "    }"
                    + "  }catch(e){}"
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
    private static final String PAGE_FULLSCREEN_JS =
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
                    + "height:100%!important;object-fit:contain!important;background:#000!important}';"
                    + "try{var v=document.querySelector('.tv-main-con-l-vid video')||document.querySelector('video');"
                    + "if(v){v.muted=false;v.controls=false;if(v.paused)v.play();}}catch(e){}"
                    + "})();";

    private final Runnable mAutoPlayRun = new Runnable() {
        @Override
        public void run() {
            if (mWebView == null || mSniffFound || isFinishing()) return;
            if (mAutoPlayTries++ >= 15) return;
            try {
                mWebView.evaluateJavascript(AUTO_PLAY_JS, null);
                if (isInPagePlayHost(mEntryUrl)) {
                    mWebView.evaluateJavascript(PAGE_FULLSCREEN_JS, null);
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

        if (mSniffMode) {
            mHandler.postDelayed(mSniffTimeoutRun, SNIFF_TIMEOUT_MS);
        }

        LOG.i("🎬 FullWebActivity 加载: url=" + mEntryUrl + " mode=" + (mSniffMode ? "sniff" : "browse"));
        mWebView.loadUrl(mEntryUrl);
    }

    /**
     * 初始化 WebView
     * - 央视频/央视网用桌面 Chrome UA，其余站点保持默认 UA
     * - 开启 JS、自动播放、Cookie 全放行
     * - 嗅探模式下挂 SniffWebViewClient，浏览模式下挂 BrowseWebViewClient
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebSettings s = mWebView.getSettings();

        // 站点差异：央视频 / 央视网 必须桌面 Chrome UA 才会给出 PC 播放器（H5 而非引导下载 App），
        // 也是 NewBox 规则表里对 yangshipin/cctv 的固定配置
        if (isDesktopSite(mEntryUrl)) {
            s.setUserAgentString(DESKTOP_UA);
        }

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
            String l = url.toLowerCase();
            if (l.contains("/ad/") || l.contains("/ads/") || l.contains("?ad=")
                    || l.contains("&ad=") || l.contains("/preroll") || l.contains("/midroll")
                    || l.contains("/postroll") || l.contains("trailer") || l.contains("preview")) {
                return -100;
            }
            int score = 0;
            if (l.contains(".m3u8")) score += 30;
            if (l.contains(".mpd")) score += 20;
            if (l.contains(".flv")) score += 15;
            if (l.contains("/live/") || l.contains("/hls/") || l.contains("/stream/")) score += 10;
            if (l.contains("index.m3u8") || l.contains("master.m3u8")
                    || l.contains("playlist.m3u8") || l.contains("manifest.m3u8")) {
                score += 15;
            }
            if (l.contains(".mp4")) score += 5;
            if (url.length() > 150) score += 3;
            if (url.length() > 250) score += 5;
            return score;
        }

        /**
         * 只接受「直播流」地址：
         * - 必须看起来是播放列表/容器：m3u8 / mpd / flv / m3u / manifest
         * - 普通 .mp4 多为页面宣传片、轮播广告，只有路径明确是直播时才接受
         * - 排除单片段 .ts / 图片等，避免把广告分片交给播放器造成黑屏
         */
        private boolean isLiveStream(String url) {
            String l = url.toLowerCase();
            if (l.contains(".ts?") || l.endsWith(".ts")) return false;
            if (l.contains(".mp4") || l.contains(".m4v") || l.contains(".mov")
                    || l.contains(".mkv") || l.contains(".webm")) {
                return l.contains("/live/") || l.contains("/stream/") || l.contains("/hls/");
            }
            if (l.contains(".m3u8") || l.contains(".mpd") || l.contains(".flv")
                    || l.contains(".m3u") || l.contains("manifest")) {
                return true;
            }
            return l.contains("/live/") || l.contains("/hls/") || l.contains("/stream/");
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
