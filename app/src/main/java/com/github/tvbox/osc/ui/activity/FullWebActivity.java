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

    private String mEntryUrl;
    private boolean mSniffMode = false;
    private boolean mSniffFound = false;

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
        mSniffMode = MODE_SNIFF.equals(mode);

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
     * - 电视盒子 UA（NewBox 风格）
     * - 开启 JS、自动播放、Cookie 全放行
     * - 嗅探模式下挂 SniffWebViewClient，浏览模式下挂 BrowseWebViewClient
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebSettings s = mWebView.getSettings();

        // UA：电视盒子浏览器 UA（比桌面 UA 更贴近 NewBox 实际表现）
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; TV Build/TVBox) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

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
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public View getVideoLoadingProgressView() {
                return new View(FullWebActivity.this);
            }
        });

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
            // 网页加载完注入静音自动播放 JS，让 HTML5 video 自己起播
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mWebView != null) {
                            String autoPlayJs =
                                    "(function(){" +
                                            "  var videos = document.querySelectorAll('video');" +
                                            "  for (var i = 0; i < videos.length; i++) {" +
                                            "    try { videos[i].muted = true; } catch(e){}" +
                                            "    try { videos[i].play(); } catch(e){}" +
                                            "  }" +
                                            "})();";
                            mWebView.evaluateJavascript(autoPlayJs, null);
                        }
                    } catch (Throwable t) {
                        LOG.i("BrowseWebViewClient autoPlay crash: " + t);
                    }
                }
            }, 800);
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
            // 嗅探模式下也静音自动播放，促使需要点击才起播的 H5 播放器发出媒体请求
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mSniffFound || mWebView == null) return;
                    try {
                        mWebView.evaluateJavascript(
                                "(function(){var vs=document.querySelectorAll('video');"
                                        + "for(var i=0;i<vs.length;i++){try{vs[i].muted=true;}catch(e){}"
                                        + "try{vs[i].play();}catch(e){}}})();", null);
                    } catch (Throwable t) {
                        LOG.i("SniffWebViewClient autoPlay crash: " + t);
                    }
                }
            }, 800);
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

            if (AdBlocker.isAd(url)) {
                return AdBlocker.createEmptyResource();
            }

            if (VideoParseRuler.isFilter(mEntryUrl, url)) {
                return AdBlocker.createEmptyResource();
            }

            if (isLiveStream(url)) {
                mSniffFound = true;
                mHandler.removeCallbacksAndMessages(null);
                LOG.i("🎬 FullWebActivity 嗅探命中：" + url);

                // 组装 headers：从 WebView 请求里拷贝 ua / referer / origin + 当前 Cookie
                HashMap<String, String> headers = new HashMap<>();
                Map<String, String> reqHeaders = request.getRequestHeaders();
                if (reqHeaders != null) {
                    for (Map.Entry<String, String> e : reqHeaders.entrySet()) {
                        String k = e.getKey();
                        if (k.equalsIgnoreCase("user-agent")
                                || k.equalsIgnoreCase("referer")
                                || k.equalsIgnoreCase("origin")) {
                            headers.put(k, e.getValue() == null ? "" : " " + e.getValue());
                        }
                    }
                }
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null && !cookie.isEmpty()) {
                    headers.put("Cookie", " " + cookie);
                }

                // 回传：Intent extras 携带直链 + Base64(JSON headers)
                Intent result = new Intent();
                result.putExtra(RESULT_STREAM_URL, url);
                result.putExtra(RESULT_STREAM_HEADERS, encodeHeaders(headers));
                setResult(RESULT_OK, result);
                finish();
                return null; // 返回 null 让原请求也继续走，避免页面崩
            }

            return null;
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
