package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.LOG;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;

/**
 * webview:// 直播承载页
 *
 * 由网页内播放器自行拉流，本页只负责：
 * - 加载网页与失败/超时重试入口；
 * - 网页播放器的全屏回调（onShowCustomView / onHideCustomView）；
 * - 返回键优先退出全屏；
 * - 横竖屏跟随传感器切换。
 */
public class WebLiveActivity extends BaseActivity {

    public static final String EXTRA_URL = "web_live_url";
    public static final String EXTRA_TITLE = "web_live_title";

    private static final long LOAD_TIMEOUT = 30 * 1000L;

    private WebView mWebView;
    private FrameLayout mFullscreenContainer;
    private View mFullscreenView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private View mRetryLayout;
    private TextView mRetryTip;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private String mCurrentUrl;
    private boolean mPageFinished;
    private boolean mIsFullscreen;

    private final Runnable mTimeoutRun = new Runnable() {
        @Override
        public void run() {
            if (!mPageFinished) {
                showRetry("网页加载超时，点击重试");
            }
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_web_live;
    }

    @Override
    protected void init() {
        ImmersionBar.with(this)
                .statusBarColor(R.color.black)
                .navigationBarColor(R.color.black)
                .fitsSystemWindows(false)
                .hideBar(BarHide.FLAG_HIDE_BAR)
                .init();

        mWebView = findViewById(R.id.web_view);
        mFullscreenContainer = findViewById(R.id.web_fullscreen_container);
        mRetryLayout = findViewById(R.id.web_retry_layout);
        mRetryTip = findViewById(R.id.web_retry_tip);
        mRetryLayout.setOnClickListener(v -> retry());
        findViewById(R.id.web_retry_btn).setOnClickListener(v -> retry());

        configWebView();

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (TextUtils.isEmpty(url)) {
            showRetry("网页地址为空，点击重试");
            return;
        }
        loadUrl(url);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configWebView() {
        WebSettings settings = mWebView.getSettings();
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
        mWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mWebView.setBackgroundColor(Color.BLACK);

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (mIsFullscreen) {
                    callback.onCustomViewHidden();
                    return;
                }
                mCustomViewCallback = callback;
                enterFullscreen(view);
            }

            @Override
            public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
                onShowCustomView(view, callback);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }

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
        mWebView.setWebViewClient(new WebLiveClient());
    }

    private void loadUrl(String url) {
        mCurrentUrl = url;
        mPageFinished = false;
        hideRetry();
        mHandler.removeCallbacks(mTimeoutRun);
        mHandler.postDelayed(mTimeoutRun, LOAD_TIMEOUT);
        mWebView.loadUrl(url);
        LOG.i("WebLiveActivity load:" + url);
    }

    private void retry() {
        if (TextUtils.isEmpty(mCurrentUrl)) {
            finish();
            return;
        }
        loadUrl(mCurrentUrl);
    }

    private void showRetry(String tip) {
        mHandler.removeCallbacks(mTimeoutRun);
        mRetryTip.setText(tip);
        mRetryLayout.setVisibility(View.VISIBLE);
    }

    private void hideRetry() {
        mRetryLayout.setVisibility(View.GONE);
    }

    private class WebLiveClient extends WebViewClient {

        @SuppressLint("WebViewClientOnReceivedSslError")
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.proceed();
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            if (mCurrentUrl != null && mCurrentUrl.equals(failingUrl)) {
                showRetry("网页加载失败，点击重试");
            }
        }

        @TargetApi(Build.VERSION_CODES.M)
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request != null && request.isForMainFrame()) {
                showRetry("网页加载失败，点击重试");
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (request != null && request.isForMainFrame() && errorResponse != null
                    && errorResponse.getStatusCode() >= 400) {
                showRetry("网页请求失败(" + errorResponse.getStatusCode() + ")，点击重试");
            }
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            mPageFinished = false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            mPageFinished = true;
            mHandler.removeCallbacks(mTimeoutRun);
            hideRetry();
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            // 遵守原仓库广告规则：命中广告 host 直接返回空资源
            if (!TextUtils.isEmpty(url) && AdBlocker.isAd(url)) {
                return AdBlocker.createEmptyResource();
            }
            return null;
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (request != null && request.getUrl() != null
                    && AdBlocker.isAd(request.getUrl().toString())) {
                return AdBlocker.createEmptyResource();
            }
            return null;
        }
    }

    private void enterFullscreen(View view) {
        if (view == null) {
            return;
        }
        if (view.getParent() != null) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        mIsFullscreen = true;
        mFullscreenView = view;
        mFullscreenContainer.removeAllViews();
        mFullscreenContainer.addView(view,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        mFullscreenContainer.setVisibility(View.VISIBLE);
        mWebView.setVisibility(View.INVISIBLE);
        // 网页全屏支持横竖屏切换
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        ImmersionBar.with(this)
                .hideBar(BarHide.FLAG_HIDE_BAR)
                .navigationBarColor(R.color.black)
                .fitsSystemWindows(false)
                .init();
    }

    private void exitFullscreen() {
        if (!mIsFullscreen) {
            return;
        }
        mIsFullscreen = false;
        mFullscreenContainer.removeAllViews();
        mFullscreenContainer.setVisibility(View.GONE);
        mFullscreenView = null;
        mWebView.setVisibility(View.VISIBLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (mCustomViewCallback != null) {
            mCustomViewCallback.onCustomViewHidden();
            mCustomViewCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        // 返回键优先退出网页全屏
        if (mIsFullscreen) {
            exitFullscreen();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWebView != null) {
            mWebView.onResume();
            mWebView.resumeTimers();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWebView != null) {
            mWebView.onPause();
            mWebView.pauseTimers();
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        if (mCustomViewCallback != null) {
            mCustomViewCallback.onCustomViewHidden();
            mCustomViewCallback = null;
        }
        if (mWebView != null) {
            mWebView.stopLoading();
            mWebView.loadUrl("about:blank");
            mWebView.removeAllViews();
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroy();
    }
}
