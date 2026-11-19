package com.github.tvbox.osc.util.live;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

/**
 * 网页直播脚本加载器：域名正则 → asset/js/live/*.js 映射，
 * 匹配到站点脚本后会先注入站点脚本，再注入通用兜底（fallback），
 * 最后调 __tvboxInPageGo(url, channelName) 让脚本切台。
 *
 * 脚本全部来自 NewBox 新版（assets/js/live.xxx.js），已内置:
 *   visualViewport 定位铺满 / 隐藏网页 DOM 控制条 / TvboxWebLive.onReady 桥 / __tvboxRelayout
 */
public class WebLiveScripts {
    private static final String TAG = "WebLiveScripts";

    private static final String FALLBACK = "js/live/live.webview.js";

    /** (regex, asset-path) 对，按顺序匹配，先命中先得 */
    private static final String[][] SITES = new String[][]{
            // 央视系
            {"(?i)yangshipin\\.(cn|com|tv)",        "js/live/live.yangshipin.js"},
            {"(?i)app\\.cctv\\.(com|cn)",           "js/live/live.cctv.app.js"},
            {"(?i)\\bcctv\\.(com|cn)\\b",           "js/live/live.cctv.js"},
            // 芒果
            {"(?i)(m\\.)?mgtv\\.com",                "js/live/live.mgtv.js"},
            // 各大卫视/地方台
            {"(?i)(www\\.)?gdtv\\.cn",               "js/live/live.gdtv.js"},
            {"(?i)hbtv\\.com\\.cn",                   "js/live/live.hbtv.js"},
            {"(?i)(www\\.)?snrtv\\.com",             "js/live/live.snrtv.js"},
            {"(?i)\\bnxtv\\.com\\b",                 "js/live/live.nxtv.js"},
            {"(?i)live\\.fjtv\\.net",                "js/live/live.fjtv.js"},
            {"(?i)xuexi\\.cn",                       "js/live/live.xuexi.js"},
            {"(?i)cditv\\.cn",                       "js/live/live.cditv.js"},
            {"(?i)cztv\\.com",                       "js/live/live.cztv.js"},
            {"(?i)dtradio\\.com\\.cn",                "js/live/live.dtradio.js"},
            {"(?i)gzstv\\.com",                      "js/live/live.gzstv.js"},
            {"(?i)hnntv\\.cn",                       "js/live/live.hnntv.js"},
            {"(?i)jlntv\\.cn",                       "js/live/live.jlntv.js"},
            {"(?i)jlstv\\.com",                      "js/live/live.jstv.js"},
            {"(?i)nmtv\\.cn",                        "js/live/live.nmtv.js"},
            {"(?i)sxrtv\\.com",                      "js/live/live.sxrtv.js"},
            {"(?i)sxtygdy\\.com",                    "js/live/live.sxtygdy.js"},
            {"(?i)xjtvs\\.com\\.cn",                  "js/live/live.xjtvs.js"},
            {"(?i)yntv\\.cn",                        "js/live/live.yntv.js"},
            {"(?i)gxtv\\.com\\.cn",                   "js/live/live.gxtv.js"},
            // 其他
            {"(?i)(www\\.)?miguvideo\\.com",          "js/live/live.migu.js"},
            {"(?i)douyin\\.com",                     "js/live/live.douyin.js"},
            {"(?i)hoolo\\.tv",                       "js/live/live.hoolo.js"},
            {"(?i)iptv345\\.com",                    "js/live/live.iptv345.js"},
    };

    public static String matchAsset(String url) {
        if (TextUtils.isEmpty(url)) return FALLBACK;
        for (String[] p : SITES) {
            try {
                if (Pattern.compile(p[0]).matcher(url).find()) return p[1];
            } catch (Throwable ignored) {}
        }
        return FALLBACK;
    }

    public static boolean hasSite(String url) {
        return !FALLBACK.equals(matchAsset(url));
    }

    public static String loadAsset(Context ctx, String assetPath) {
        if (TextUtils.isEmpty(assetPath)) return "";
        String cacheKey = assetPath.intern();
        // 简单 static cache，不用每次都磁盘读
        String cached = cache.get(cacheKey);
        if (cached != null) return cached;
        try {
            InputStream is = ctx.getAssets().open(assetPath);
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder(is.available() + 128);
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            is.close();
            String src = sb.toString();
            cache.put(cacheKey, src);
            return src;
        } catch (Throwable t) {
            Log.w(TAG, "loadAsset fail: " + assetPath + " " + t);
            return "";
        }
    }

    public static void clearCache() {
        cache.clear();
    }

    private static final java.util.HashMap<String, String> cache = new java.util.HashMap<>();
}
