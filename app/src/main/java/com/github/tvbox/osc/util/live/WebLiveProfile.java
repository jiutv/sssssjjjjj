package com.github.tvbox.osc.util.live;

import android.text.TextUtils;

/**
 * 网页直播的站点画像：按域名决定 UA 与播放方式。
 *
 * <p>为什么需要：很多卫视/央媒站点对移动 UA 只给 MSE 加密流或直接引导下载 App，
 * 对桌面 UA 才给干净的 HLS 直链。统一用移动 UA 会让嗅探大量落空。</p>
 */
public final class WebLiveProfile {

    public static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 13) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    /** 只认桌面页面的站点：桌面页给 HLS 直链，移动页给 MSE 加密流或下载引导 */
    private static final String[] DESKTOP_HOSTS = {
            "yangshipin.cn", "cctv.com", "cctv.cn", "hebtv.com",
            "mgtv.com", "migu.cn", "jstv.com", "gdtv.cn", "cztv.com",
            "hnntv.cn", "sdtv.cn", "btv.org", "gxtv.cn", "nxnews.net"
    };

    /** 网页播放器在本机解码正常、但把流交给原生播放器会花屏（常见为 H.265）的站点 */
    private static final String[] IN_PAGE_HOSTS = {"yangshipin.cn"};

    private WebLiveProfile() {
    }

    private static boolean matchAny(String url, String[] hosts) {
        if (TextUtils.isEmpty(url)) return false;
        String l = url.toLowerCase();
        for (String host : hosts) {
            if (l.contains(host)) return true;
        }
        return false;
    }

    public static boolean isDesktop(String url) {
        return matchAny(url, DESKTOP_HOSTS);
    }

    public static boolean isInPageHost(String url) {
        return matchAny(url, IN_PAGE_HOSTS);
    }

    /** 未知站点返回移动 UA（与 WebView 默认 UA 行为一致） */
    public static String uaFor(String url) {
        return isDesktop(url) ? DESKTOP_UA : MOBILE_UA;
    }
}
