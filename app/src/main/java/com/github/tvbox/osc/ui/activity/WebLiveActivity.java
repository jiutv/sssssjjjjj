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
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
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
import com.github.tvbox.osc.util.VideoParseRuler;
import com.github.tvbox.osc.util.live.LiveSniffer;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;

import java.util.HashMap;
import java.util.Map;

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

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
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
        // 移动端页面常常把直播流藏在 H5 播放器里，桌面 UA 更容易拿到可嗅探的媒体请求
        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        mWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mWebView.setBackgroundColor(Color.BLACK);
        // JS 嗅探桥接：页面内 hook 发现的媒体地址会通过 sniffBridge.onUrl 回传
        mWebView.addJavascriptInterface(new SniffBridge(), "sniffBridge");

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
        // 每次加载/重试都重置嗅探状态
        mSniffFound = false;
        mSniffEnabled = true;
        mHandler.removeCallbacks(mTimeoutRun);
        mHandler.removeCallbacks(mSniffTimeoutRun);
        mHandler.postDelayed(mTimeoutRun, LOAD_TIMEOUT);
        mHandler.postDelayed(mSniffTimeoutRun, SNIFF_TIMEOUT_MS);
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
            if (!mSniffEnabled) {
                // 嗅探已超时：让网页里的 HTML5 播放器铺满屏幕，用户仍能直接看网页直播
                injectForceFullscreen();
                return;
            }
            // 注入嗅探 hook（有些页面会重写 hook，隔一段时间再补一次）
            injectHookJs();
            mHandler.postDelayed(WebLiveActivity.this::injectHookJs, 1000);
            mHandler.postDelayed(WebLiveActivity.this::injectHookJs, 2500);
            // 静音自动播放，促使页面主动起播并发出媒体请求（嗅探阶段不隐藏站点导航，保持可点击）
            injectAutoPlay();
            mHandler.postDelayed(WebLiveActivity.this::injectAutoPlay, 1200);
            mHandler.postDelayed(WebLiveActivity.this::injectAutoPlay, 3000);
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (TextUtils.isEmpty(url)) {
                return null;
            }
            if (AdBlocker.isAd(url)) {
                return AdBlocker.createEmptyResource();
            }
            checkSniff(url, null);
            return null;
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (request == null || request.getUrl() == null) {
                return null;
            }
            String url = request.getUrl().toString();
            if (AdBlocker.isAd(url)) {
                return AdBlocker.createEmptyResource();
            }
            checkSniff(url, request.getRequestHeaders());
            return null;
        }
    }

    /**
     * 网页内实时嗅探（参考 jiutv/TVBoxOS 的 WebView 嗅探引擎）：
     * 1. shouldInterceptRequest 捕获媒体请求；
     * 2. 页面内 JS hook（fetch / XHR / video.src / currentSrc 轮询 / MutationObserver）通过
     *    sniffBridge.onUrl 回传动态播放器地址，解决 URL 无媒体后缀、JS 动态加载的问题。
     * 命中后把地址 + headers 回传 LiveActivity，由原生播放器接手播放。
     */
    private static final long SNIFF_TIMEOUT_MS = 30 * 1000L;
    private volatile boolean mSniffEnabled = true;
    private volatile boolean mSniffFound = false;

    /** 嗅探超时：不再切播放器，改为把网页里的 HTML5 播放器强制铺满，用户继续看网页直播 */
    private final Runnable mSniffTimeoutRun = new Runnable() {
        @Override
        public void run() {
            if (mSniffFound) {
                return;
            }
            mSniffEnabled = false;
            LOG.i("WebLiveActivity 嗅探超时，保留网页播放");
            injectForceFullscreen();
            mHandler.postDelayed(WebLiveActivity.this::injectForceFullscreen, 800);
            mHandler.postDelayed(WebLiveActivity.this::injectForceFullscreen, 2000);
        }
    };

    private synchronized void checkSniff(String url, Map<String, String> requestHeaders) {
        if (!mSniffEnabled || mSniffFound) {
            return;
        }
        if (TextUtils.isEmpty(url) || !url.startsWith("http") || TextUtils.isEmpty(mCurrentUrl)) {
            return;
        }
        // 跳过主文档自身，避免把页面当媒体流
        if (url.equals(mCurrentUrl)) {
            return;
        }
        try {
            if (url.endsWith("/favicon.ico") || AdBlocker.isAd(url)) {
                return;
            }
            if (VideoParseRuler.isFilter(mCurrentUrl, url)) {
                return;
            }
            if (!isVideoCandidate(url)) {
                return;
            }
        } catch (Throwable th) {
            th.printStackTrace();
            return;
        }
        mSniffFound = true;
        mSniffEnabled = false;
        mHandler.removeCallbacks(mSniffTimeoutRun);
        LOG.i("WebLiveActivity 嗅探命中:" + url);

        HashMap<String, String> headers = new HashMap<>();
        if (requestHeaders != null) {
            for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                String k = entry.getKey();
                if (k != null && (k.equalsIgnoreCase("user-agent")
                        || k.equalsIgnoreCase("referer")
                        || k.equalsIgnoreCase("origin"))) {
                    headers.put(k, entry.getValue() == null ? "" : entry.getValue());
                }
            }
        }
        if (!headers.containsKey("Referer") && !headers.containsKey("referer")) {
            headers.put("Referer", mCurrentUrl);
        }
        // JS hook 上报的地址没有请求头，用 WebView 的 Cookie 兜底
        String cookie = CookieManager.getInstance().getCookie(url);
        if (!TextUtils.isEmpty(cookie)) {
            headers.put("Cookie", cookie);
        }
        LiveSniffer.holdSniffed(url, headers);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    /**
     * 媒体地址判定：严格规则（DefaultConfig / VideoParseRuler）之外再补一层宽松规则，
     * 覆盖 URL 无后缀、由接口动态下发播放地址的场景。
     */
    private boolean isVideoCandidate(String url) {
        if (LiveSniffer.isVideoUrl(url, mCurrentUrl)) {
            return true;
        }
        String lower = url.toLowerCase();
        // 明确排除静态资源与页面本身，避免误判
        if (lower.contains(".css") || lower.contains(".js") || lower.contains(".json")
                || lower.contains(".html") || lower.contains(".htm") || lower.contains(".php")
                || lower.contains(".png") || lower.contains(".jpg") || lower.contains(".jpeg")
                || lower.contains(".gif") || lower.contains(".svg") || lower.contains(".woff")
                || lower.contains(".ttf")) {
            return false;
        }
        if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains(".mp4")
                || lower.contains(".flv") || lower.contains(".mkv") || lower.contains(".webm")
                || lower.contains(".m4v") || lower.contains(".mov")) {
            return true;
        }
        if (lower.contains("manifest.mpd") || lower.contains("playlist.m3u8")
                || lower.contains("master.m3u8")) {
            return true;
        }
        return lower.contains("/hls/") || lower.contains("/dash/") || lower.contains("/m3u/")
                || lower.contains("/live/") || lower.contains("/stream/") || lower.contains("/vod/");
    }

    private void injectHookJs() {
        if (mWebView == null || !mSniffEnabled) {
            return;
        }
        try {
            mWebView.evaluateJavascript(buildHookJs(), null);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void injectAutoPlay() {
        if (mWebView == null) {
            return;
        }
        try {
            mWebView.evaluateJavascript(LiveSniffer.AUTOPLAY_JS, null);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void injectForceFullscreen() {
        if (mWebView == null) {
            return;
        }
        try {
            mWebView.evaluateJavascript(buildForceFullscreenJs(), null);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    /**
     * 页面内嗅探 hook：fetch / XHR / video.src / createElement('video') / 常见播放器入口函数 /
     * WebSocket / video.currentSrc 定时轮询 / MutationObserver，命中后通过 sniffBridge 回传。
     */
    private String buildHookJs() {
        return "(function(){"
                + "if(window.__mboxSniffHook){return;}"
                + "window.__mboxSniffHook=true;"
                + "var bridge=window.sniffBridge||null;var reported={};"
                + "function report(u){try{if(!u||u.length<8)return;if(reported[u])return;reported[u]=true;if(bridge)bridge.onUrl(u);}catch(e){}}"
                + "function ok(u){try{if(!u||u.length<8)return false;var l=(''+u).toLowerCase();"
                + "if(l.indexOf('.css')>=0||l.indexOf('.js')>=0||l.indexOf('.png')>=0||l.indexOf('.jpg')>=0||l.indexOf('.jpeg')>=0||l.indexOf('.gif')>=0||l.indexOf('.svg')>=0||l.indexOf('.ico')>=0||l.indexOf('.html')>=0||l.indexOf('.htm')>=0||l.indexOf('.json')>=0||l.indexOf('.xml')>=0||l.indexOf('.woff')>=0||l.indexOf('.ttf')>=0||l.indexOf('favicon')>=0)return false;"
                + "if(l.indexOf('.m3u8')>=0||l.indexOf('.mpd')>=0||l.indexOf('.mp4')>=0||l.indexOf('.flv')>=0||l.indexOf('.mkv')>=0||l.indexOf('.webm')>=0||l.indexOf('.m4v')>=0||l.indexOf('.mov')>=0)return true;"
                + "if(l.indexOf('manifest.mpd')>=0||l.indexOf('playlist.m3u8')>=0||l.indexOf('master.m3u8')>=0)return true;"
                + "return l.indexOf('/hls/')>=0||l.indexOf('/dash/')>=0||l.indexOf('/m3u/')>=0||l.indexOf('/live/')>=0||l.indexOf('/stream/')>=0||l.indexOf('/vod/')>=0;}catch(e){return false;}}"
                + "function fromArg(a){try{if(!a)return;if(typeof a==='string'){if(ok(a))report(a);return;}"
                + "if(typeof a==='object'){var ks=['url','src','file','playUrl','m3u8','mpd','stream','mediaUrl','videoUrl','source','sources','hd','sd','downloadUrl','download_url','initUrl'];"
                + "for(var i=0;i<ks.length;i++){if(a[ks[i]])fromArg(a[ks[i]]);}"
                + "if(a.data)fromArg(a.data);if(a.urls&&a.urls.length){for(var j=0;j<a.urls.length;j++)fromArg(a.urls[j]);}}}catch(e){}}"
                + "try{var _f=window.fetch;if(_f){window.fetch=function(){try{var u='';if(arguments[0]&&arguments[0].url)u=arguments[0].url;else if(typeof arguments[0]==='string')u=arguments[0];if(ok(u))report(u);}catch(e){}return _f.apply(this,arguments);};}}catch(e){}"
                + "try{var _o=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(m,u){try{if(ok(u))report(u);}catch(e){}return _o.apply(this,arguments);};}catch(e){}"
                + "try{var d=Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype,'src');if(d&&d.set){Object.defineProperty(HTMLMediaElement.prototype,'src',{set:function(u){try{if(ok(u))report(u);}catch(e){}return d.set.call(this,u);},get:function(){return d.get.call(this);}});}}catch(e){}"
                + "try{var _c=document.createElement;document.createElement=function(t){var el=_c.apply(this,arguments);try{if(t&&(''+t).toLowerCase()==='video'){var sa=el.setAttribute.bind(el);el.setAttribute=function(n,v){try{if(n==='src'&&ok(v))report(v);}catch(e){}return sa(n,v);};}}catch(e){}return el;};}catch(e){}"
                + "var fns=['videoPlay','video_play','VideoPlay','player','initPlayer','startPlayer','playVideo','play','start','setup','loadSource','load','attachMedia','newPlayer','createPlayer','newHls','playStream','playUrl','setVideo','openVideo','showVideo','openStream'];"
                + "for(var fi=0;fi<fns.length;fi++){try{var n=fns[fi];if(typeof window[n]==='function'){var og=window[n];window[n]=function(){try{for(var a=0;a<arguments.length;a++)fromArg(arguments[a]);}catch(e){}return og.apply(this,arguments);};}}catch(e){}}"
                + "try{var _w=window.WebSocket;if(_w){window.WebSocket=function(u,p){try{if(ok(u))report(u);}catch(e){}return p?new _w(u,p):new _w(u);};}}catch(e){}"
                + "var n=0;var iv=setInterval(function(){n++;if(n>30){clearInterval(iv);return;}"
                + "try{var vs=document.querySelectorAll('video');for(var i=0;i<vs.length;i++){var s=vs[i].currentSrc||vs[i].src;if(s&&s.length>8)report(s);}}catch(e){}"
                + "try{var html=document.documentElement.innerHTML||'';var re=/https?:\\/\\/[^\"'\\s<>]+?(?:\\.(?:m3u8|mpd|mp4|flv|mkv|webm|m4v|mov)(?:\\?[^\"'\\s<>]*)?)?(?:[?&][^\"'\\s<>]+)?/gi;var m;var cnt=0;while((m=re.exec(html))!==null&&cnt<50){cnt++;if(ok(m[0]))report(m[0]);}}catch(e){}"
                + "},1000);"
                + "try{var mo=new MutationObserver(function(ms){for(var i=0;i<ms.length;i++){var ns=ms[i].addedNodes;for(var j=0;j<ns.length;j++){try{if(ns[j]&&ns[j].querySelectorAll){var vs=ns[j].querySelectorAll('video');for(var k=0;k<vs.length;k++){var s=vs[k].currentSrc||vs[k].src;if(s&&s.length>8)report(s);}}}}catch(e){}}}});mo.observe(document.documentElement,{childList:true,subtree:true});}catch(e){}"
                + "})();";
    }

    /** 把网页里的 HTML5 播放器铺满屏幕（嗅探失败时的兜底），不依赖具体站点 */
    private String buildForceFullscreenJs() {
        return "(function(){"
                + "var hide=['header','nav','footer','aside','[class*=Header]','[class*=header]','[class*=Nav]','[class*=nav]','[class*=Footer]','[class*=footer]','[class*=Sidebar]','[class*=sidebar]','[class*=TopBar]','[class*=topBar]','[class*=SearchBar]','[class*=searchBar]','[class*=Copyright]','[class*=copyright]','[class*=advert]','[class*=Advert]','[class*=ads]','[class*=Ads]','[class*=channel-list]','[class*=ChannelList]','[class*=user-info]','[class*=UserInfo]','[class*=logo]','[class*=Logo]','[id*=header]','[id*=Header]','[id*=nav]','[id*=Nav]','[id*=sidebar]','[id*=Sidebar]','[id*=footer]','[id*=Footer]'];"
                + "for(var i=0;i<hide.length;i++){try{var es=document.querySelectorAll(hide[i]);for(var j=0;j<es.length;j++){es[j].style.display='none';}}catch(e){}}"
                + "var vs=document.querySelectorAll('video');"
                + "for(var k=0;k<vs.length;k++){var v=vs[k];"
                + "try{v.muted=false;v.controls=true;v.setAttribute('playsinline','');v.setAttribute('webkit-playsinline','');}catch(e){}"
                + "try{v.style.cssText='position:fixed !important;left:0 !important;top:0 !important;width:100vw !important;height:100vh !important;object-fit:cover !important;z-index:2147483647 !important;background:#000 !important;';}catch(e){}"
                + "var p=v.parentElement;var d=0;while(p&&d<12){try{p.style.cssText='width:100% !important;height:100% !important;margin:0 !important;padding:0 !important;overflow:hidden !important;background:#000 !important;';}catch(e){}p=p.parentElement;d++;}"
                + "try{v.play();}catch(e){}"
                + "try{v.requestFullscreen();}catch(e){}"
                + "try{v.webkitRequestFullscreen();}catch(e){}"
                + "}"
                + "try{document.documentElement.style.cssText='width:100% !important;height:100% !important;margin:0 !important;padding:0 !important;overflow:hidden !important;background:#000 !important;';"
                + "document.body.style.cssText='width:100% !important;height:100% !important;margin:0 !important;padding:0 !important;overflow:hidden !important;background:#000 !important;';}catch(e){}"
                + "var fs=['[class*=fullscreen]','[class*=Fullscreen]','[class*=fullScreen]','[class*=FullScreen]','[class*=fs-btn]','[title*=全屏]','[aria-label*=全屏]','[data-action*=fullscreen]'];"
                + "for(var s=0;s<fs.length;s++){try{var bs=document.querySelectorAll(fs[s]);for(var b=0;b<bs.length;b++){var r=bs[b].getBoundingClientRect();if(r.width>0&&r.height>0){bs[b].click();break;}}}catch(e){}}"
                + "})();";
    }

    /** JS → Java 嗅探桥接（WebView 线程回调，统一 post 到主线程处理） */
    public class SniffBridge {
        @JavascriptInterface
        public void onUrl(final String url) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    checkSniff(url, null);
                }
            });
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
