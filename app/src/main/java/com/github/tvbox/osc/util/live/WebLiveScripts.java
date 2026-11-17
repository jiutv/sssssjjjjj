package com.github.tvbox.osc.util.live;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 网页直播的站点适配脚本。
 *
 * <p>脚本来自 NewBox（TVBox 派生，GPL-3.0）的 assets/js，已按本工程需要裁剪目录：
 * 每个脚本只做「藏站点 UI + 把播放器铺满 + 必要时点选频道」，不触碰 DOM 结构，
 * 避免 WebView 重建 Surface 造成花屏。命中不到站点时用 webview.js 通用兜底。</p>
 */
public final class WebLiveScripts {

    private static final String TAG = "WebLiveScripts";

    /** 通用兜底脚本：选面积最大的 video/iframe 铺满 */
    private static final String GENERIC = "js/live/webview.js";

    /**
     * 最终兜底：不认任何站点选择器，按「面积最大的播放器」铺满。
     * 站点脚本失效（改版）时靠它保证至少能全屏观看。
     */
    private static final String FALLBACK = "js/live/fallback.js";

    /** [域名正则, 脚本] —— 顺序敏感，特殊域名放前面 */
    private static final String[][] SITES = {
            {"(^|\\.)cctv\\.com$|(^|\\.)cctv\\.cn$", "js/live/cctv.js"},
            {"(^|\\.)yangshipin\\.cn$", "js/live/yangshipin.js"},
            {"(^|\\.)mgtv\\.com$", "js/live/mgtv.js"},
            {"(^|\\.)migu\\.cn$", "js/live/migu.js"},
            {"(^|\\.)douyin\\.com$|(^|\\.)iesdouyin\\.com$", "js/live/douyin.js"},
            {"(^|\\.)xuexi\\.cn$", "js/live/xuexi.js"},
            {"(^|\\.)cztv\\.com$|(^|\\.)zjstv\\.com$", "js/live/cztv.js"},
            {"(^|\\.)jstv\\.com$", "js/live/jstv.js"},
            {"(^|\\.)gdtv\\.cn$|(^|\\.)gdtv\\.com$", "js/live/gdtv.js"},
            {"(^|\\.)gxtv\\.cn$", "js/live/gxtv.js"},
            {"(^|\\.)gzstv\\.com$", "js/live/gzstv.js"},
            {"(^|\\.)hbtv\\.com(\\.cn)?$", "js/live/hbtv.js"},
            {"(^|\\.)hnntv\\.cn$", "js/live/hnntv.js"},
            {"(^|\\.)hoolo\\.tv$", "js/live/hoolo.js"},
            {"(^|\\.)iptv345\\.com$", "js/live/iptv345.js"},
            {"(^|\\.)jlntv\\.cn$", "js/live/jlntv.js"},
            {"(^|\\.)nmtv\\.cn$", "js/live/nmtv.js"},
            {"(^|\\.)nxtv\\.cn$", "js/live/nxtv.js"},
            {"(^|\\.)snrtv\\.com$", "js/live/snrtv.js"},
            {"(^|\\.)sxrtv\\.com$", "js/live/sxrtv.js"},
            {"(^|\\.)sxtygdy\\.com$", "js/live/sxtygdy.js"},
            {"(^|\\.)xjtv\\.com\\.cn$|(^|\\.)xjtvs\\.com$", "js/live/xjtvs.js"},
            {"(^|\\.)yntv\\.cn$", "js/live/yntv.js"},
            {"(^|\\.)ylrb\\.com$", "js/live/ylrb.js"},
            {"(^|\\.)cditv\\.(cn|com)$", "js/live/cditv.js"},
            {"(^|\\.)dtradio\\.(com|cn)$", "js/live/dtradio.js"},
            {"(^|\\.)fjtv\\.net$", "js/live/fjtv.js"},
    };

    private static final Map<String, String> CACHE = new HashMap<>();

    private WebLiveScripts() {
    }

    /** 该地址是否命中站点专用脚本（未命中则只用通用兜底） */
    public static boolean hasSite(String url) {
        return siteAsset(url) != null;
    }

    private static String siteAsset(String url) {
        String host = hostOf(url);
        if (host == null) return null;
        for (String[] s : SITES) {
            try {
                if (host.matches(s[0])) return s[1];
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String hostOf(String url) {
        if (TextUtils.isEmpty(url)) return null;
        try {
            String h = Uri.parse(url).getHost();
            return TextUtils.isEmpty(h) ? null : h.toLowerCase();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 往网页里注入适配脚本。
     *
     * <p>顺序固定：先站点脚本、再通用脚本。站点脚本会设置 __tvboxSkipVideoFill=1，
     * 通用脚本看到该标记就不再重复铺满，避免两套样式互相打架。</p>
     *
     * @param channelName 频道名，供支持自动点选频道的脚本使用，可为空
     */
    public static void inject(WebView view, String pageUrl, String channelName) {
        if (view == null || TextUtils.isEmpty(pageUrl)) return;
        Context ctx = view.getContext();
        String site = siteAsset(pageUrl);
        try {
            if (site != null) {
                String js = read(ctx, site);
                if (!TextUtils.isEmpty(js)) view.evaluateJavascript(js, null);
            }
            String generic = read(ctx, GENERIC);
            if (!TextUtils.isEmpty(generic)) view.evaluateJavascript(generic, null);
            String fallback = read(ctx, FALLBACK);
            if (!TextUtils.isEmpty(fallback)) view.evaluateJavascript(fallback, null);
            // 支持频道自动点选的站点脚本需要页面地址与频道名
            String call = "(function(){try{if(window.__tvboxInPageGo){window.__tvboxInPageGo("
                    + JSONObject.quote(pageUrl) + "," + JSONObject.quote(channelName == null ? "" : channelName)
                    + ");}}catch(e){}})()";
            view.evaluateJavascript(call, null);
        } catch (Throwable t) {
            Log.e(TAG, "注入脚本失败: " + pageUrl, t);
        }
    }

    private static synchronized String read(Context ctx, String asset) {
        String cached = CACHE.get(asset);
        if (cached != null) return cached;
        InputStream in = null;
        try {
            in = ctx.getAssets().open(asset);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            String js = new String(bos.toByteArray(), "UTF-8");
            CACHE.put(asset, js);
            return js;
        } catch (Throwable t) {
            Log.e(TAG, "读取脚本失败: " + asset, t);
            return "";
        } finally {
            try {
                if (in != null) in.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
