package com.github.tvbox.osc.util.live;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.VideoParseRuler;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 直播 video:// 嗅探器
 *
 * 使用隐藏 WebView 打开直播网页，在 WebView 的网络请求中识别媒体地址（m3u8 / mp4 / mpd / flv / rtmp 等），
 * 命中后把真实地址与请求头交给直播播放器。
 *
 * 规则与点播侧 PlayFragment 的嗅探保持一致：
 * - 复用 DefaultConfig.isVideoFormat / VideoParseRuler 判定，不硬编码任何站点规则；
 * - 复用 AdBlocker 广告过滤与 VideoParseRuler 过滤规则；
 * - Cookie 从 CookieManager 读取，不硬编码 User-Agent / Cookie；
 * - 超时时间与点播嗅探一致。
 */
public class LiveSniffer {

    public interface Callback {
        /** 嗅探到可播放地址 */
        void onSniffed(String url, HashMap<String, String> headers);

        /** 嗅探失败（超时 / 页面加载失败 / 没有媒体请求） */
        void onFailed(String reason);
    }

    /** 与点播嗅探保持一致 */
    private static final long SNIFF_TIMEOUT = 20 * 1000L;

    /**
     * 通用自动播放脚本：多数网页播放器要等用户点击才起播，
     * 隐藏 WebView 里没有点击条件，这里把媒体元素静音后主动 play，
     * 促使页面发出 m3u8/mp4 等媒体请求。不包含任何站点选择器。
     */
    private static final String AUTOPLAY_JS =
            "(function(){"
                    + "if(window.__mbox_live_autoplay){return;}"
                    + "var kick=function(){"
                    + "var list=document.querySelectorAll('video,audio');"
                    + "for(var i=0;i<list.length;i++){var m=list[i];try{m.muted=true;m.autoplay=true;"
                    + "if(m.paused){var p=m.play();if(p&&p.catch){p.catch(function(){});}}}catch(e){}}"
                    + "};"
                    + "kick();"
                    + "window.__mbox_live_autoplay=setInterval(kick,1200);"
                    + "})()";

    private final Activity mActivity;
    private final Callback mCallback;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private WebView mWebView;
    private String mPageUrl;
    private volatile boolean mFinished;

    private final Runnable mTimeoutRun = new Runnable() {
        @Override
        public void run() {
            fail("嗅探超时");
        }
    };

    public LiveSniffer(Activity activity, Callback callback) {
        mActivity = activity;
        mCallback = callback;
    }

    public void start(String pageUrl) {
        start(pageUrl, SNIFF_TIMEOUT);
    }

    /**
     * @param timeoutMs 本次嗅探的超时时间；webview:// 这类「先嗅探、失败退回网页」的场景可以给更短的时间
     */
    public void start(String pageUrl, long timeoutMs) {
        if (TextUtils.isEmpty(pageUrl)) {
            fail("网页地址为空");
            return;
        }
        mPageUrl = pageUrl;
        mFinished = false;
        ensureWebView();
        mHandler.removeCallbacks(mTimeoutRun);
        mHandler.postDelayed(mTimeoutRun, timeoutMs > 0 ? timeoutMs : SNIFF_TIMEOUT);
        mWebView.stopLoading();
        mWebView.loadUrl(pageUrl);
        LOG.i("LiveSniffer start:" + pageUrl);
    }

    /** 停止当前嗅探，保留 WebView 以便复用 */
    public void stop() {
        mFinished = true;
        mHandler.removeCallbacks(mTimeoutRun);
        if (mWebView != null) {
            mWebView.stopLoading();
            mWebView.loadUrl("about:blank");
        }
    }

    /** 直播页销毁时调用 */
    public void destroy() {
        mFinished = true;
        mHandler.removeCallbacks(mTimeoutRun);
        if (mWebView != null) {
            mWebView.stopLoading();
            mWebView.loadUrl("about:blank");
            mWebView.removeAllViews();
            mWebView.destroy();
            mWebView = null;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void ensureWebView() {
        if (mWebView != null) {
            return;
        }
        WebView webView = new WebView(mActivity);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.clearFocus();
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setBackgroundColor(Color.BLACK);
        // 1x1 隐藏承载，不影响直播页布局
        mActivity.addContentView(webView, new ViewGroup.LayoutParams(1, 1));

        WebSettings settings = webView.getSettings();
        settings.setNeedInitialFocus(false);
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(false);
        settings.setDefaultTextEncodingName("utf-8");
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,
                                      android.webkit.JsPromptResult result) {
                return true;
            }
        });
        webView.setWebViewClient(new SniffWebClient());
        mWebView = webView;
    }

    private class SniffWebClient extends WebViewClient {

        @SuppressLint("WebViewClientOnReceivedSslError")
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.proceed();
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            // 兼容 API 23 以下的回调，只处理主文档
            if (failingUrl != null && failingUrl.equals(mPageUrl)) {
                fail("网页加载失败");
            }
        }

        @TargetApi(Build.VERSION_CODES.M)
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request != null && request.isForMainFrame()) {
                fail("网页加载失败");
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (request != null && request.isForMainFrame() && errorResponse != null
                    && errorResponse.getStatusCode() >= 400) {
                fail("网页请求失败(" + errorResponse.getStatusCode() + ")");
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (mFinished || view == null) {
                return;
            }
            // 页面加载完成后尝试静音自动播放，触发媒体请求
            try {
                view.evaluateJavascript(AUTOPLAY_JS, null);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (TextUtils.isEmpty(url)) {
                return null;
            }
            HashMap<String, String> headers = new HashMap<>();
            String cookie = CookieManager.getInstance().getCookie(url);
            if (!TextUtils.isEmpty(cookie)) {
                headers.put("Cookie", cookie);
            }
            return handleRequest(url, headers);
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (request == null || request.getUrl() == null) {
                return null;
            }
            String url = request.getUrl().toString();
            HashMap<String, String> headers = new HashMap<>();
            Map<String, String> requestHeaders = request.getRequestHeaders();
            if (requestHeaders != null) {
                for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                    String key = entry.getKey();
                    if (key == null) {
                        continue;
                    }
                    if (key.equalsIgnoreCase("user-agent")
                            || key.equalsIgnoreCase("referer")
                            || key.equalsIgnoreCase("origin")) {
                        headers.put(key, entry.getValue());
                    }
                }
            }
            String cookie = CookieManager.getInstance().getCookie(url);
            if (!TextUtils.isEmpty(cookie)) {
                headers.put("Cookie", cookie);
            }
            return handleRequest(url, headers);
        }
    }

    private WebResourceResponse handleRequest(String url, HashMap<String, String> headers) {
        if (mFinished) {
            return AdBlocker.createEmptyResource();
        }
        if (url.endsWith("/favicon.ico")) {
            return null;
        }
        try {
            // 复用原仓库过滤规则
            if (VideoParseRuler.isFilter(mPageUrl, url)) {
                LOG.i("LiveSniffer filter:" + url);
                return null;
            }
            // 复用原仓库广告规则
            if (AdBlocker.isAd(url)) {
                LOG.i("LiveSniffer ad:" + url);
                return AdBlocker.createEmptyResource();
            }
            if (isVideoUrl(url)) {
                if (!headers.containsKey("Referer") && !headers.containsKey("referer")
                        && !TextUtils.isEmpty(mPageUrl)) {
                    headers.put("Referer", mPageUrl);
                }
                found(url, headers);
                // 命中后阻断 WebView 继续拉流，避免与原生播放器同时抢流
                return AdBlocker.createEmptyResource();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    /**
     * 媒体地址判定：复用 DefaultConfig 的媒体后缀规则，额外补充 DASH 与 RTMP 协议。
     * 不使用任何站点硬编码。
     */
    private boolean isVideoUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("rtmp://") || lower.startsWith("rtsp://")) {
            return true;
        }
        if (lower.contains(".mpd")) {
            return true;
        }
        if (DefaultConfig.isVideoFormat(url)) {
            return true;
        }
        return VideoParseRuler.checkIsVideoForParse(mPageUrl, url);
    }

    private void found(String url, HashMap<String, String> headers) {
        if (mFinished) {
            return;
        }
        mFinished = true;
        mHandler.removeCallbacks(mTimeoutRun);
        final HashMap<String, String> resultHeaders = headers == null
                ? new HashMap<String, String>() : headers;
        LOG.i("LiveSniffer found:" + url);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mWebView != null) {
                    mWebView.stopLoading();
                }
                mCallback.onSniffed(url, resultHeaders);
            }
        });
    }

    private void fail(String reason) {
        if (mFinished) {
            return;
        }
        mFinished = true;
        mHandler.removeCallbacks(mTimeoutRun);
        if (mWebView != null) {
            mWebView.stopLoading();
        }
        final String msg = reason;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mCallback.onFailed(msg);
            }
        });
    }
}
