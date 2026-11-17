package com.github.tvbox.osc.util.live;

import android.net.Uri;
import android.text.TextUtils;

/**
 * 直播候选流打分。
 *
 * <p>同一个网页会陆续发出多个播放列表（贴片广告、预加载、不同清晰度、不同编码），
 * 抓到第一个就交给播放器很容易播到广告或低清预览，因此统一按分数排序后再选。</p>
 */
public final class StreamScore {

    private static final String[] AD_KEYWORDS = {
            "/ad/", "/ads/", "/adv/", "advert", "?ad=", "&ad=",
            "/preroll", "/midroll", "/postroll", "trailer", "preview", "guanggao"
    };

    private static final String[] HIGH_QUALITY = {
            "4k", "uhd", "fhd", "1080", "2000", "4000", "8000"
    };

    private static final String[] LOW_QUALITY = {
            "480p", "_480", "ld_", "low", "mini"
    };

    private StreamScore() {
    }

    public static boolean isAd(String url) {
        if (TextUtils.isEmpty(url)) return true;
        String l = url.toLowerCase();
        for (String k : AD_KEYWORDS) {
            if (l.contains(k)) return true;
        }
        return false;
    }

    /**
     * 是否像可交给播放器的直播流地址：
     * 播放列表/容器优先；单片段 .ts 与普通 .mp4 只有在路径明确是直播时才接受，
     * 避免把页面宣传片、广告分片交给播放器造成黑屏。
     */
    public static boolean looksLikeLive(String url) {
        if (TextUtils.isEmpty(url)) return false;
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
        return l.contains("/live/") || l.contains("/hls/") || l.contains("/stream/")
                || l.startsWith("rtmp://") || l.startsWith("rtsp://");
    }

    public static int score(String pageUrl, String streamUrl) {
        return score(pageUrl, streamUrl, false);
    }

    /**
     * @param fromVideoEl 地址来自页面 &lt;video&gt; 的 currentSrc/src（真实起播地址），权重更高
     * @return 负分表示直接淘汰
     */
    public static int score(String pageUrl, String streamUrl, boolean fromVideoEl) {
        if (!looksLikeLive(streamUrl)) return -100;
        if (isAd(streamUrl)) return -100;

        String l = streamUrl.toLowerCase();
        int score = 0;
        if (fromVideoEl) score += 50;

        if (l.contains(".m3u8")) score += 30;
        if (l.contains(".mpd")) score += 20;
        if (l.contains(".flv")) score += 15;
        if (l.contains("/live/") || l.contains("/hls/") || l.contains("/stream/")) score += 10;
        if (l.contains("index.m3u8") || l.contains("master.m3u8")
                || l.contains("playlist.m3u8") || l.contains("manifest.m3u8")) {
            score += 15;
        }
        for (String k : HIGH_QUALITY) {
            if (l.contains(k)) {
                score += 20;
                break;
            }
        }
        for (String k : LOW_QUALITY) {
            if (l.contains(k)) {
                score -= 15;
                break;
            }
        }
        // 与页面同域的更可能是主播放列表，跨域多为广告/统计/预加载
        String pageHost = host(pageUrl);
        String streamHost = host(streamUrl);
        if (!TextUtils.isEmpty(pageHost) && !TextUtils.isEmpty(streamHost)) {
            if (streamHost.endsWith(pageHost) || pageHost.endsWith(streamHost)) {
                score += 12;
            }
        }
        if (streamUrl.length() > 250) score += 5;
        return score;
    }

    private static String host(String url) {
        if (TextUtils.isEmpty(url)) return null;
        try {
            String h = Uri.parse(url).getHost();
            if (TextUtils.isEmpty(h)) return null;
            String[] parts = h.split("\\.");
            if (parts.length >= 3 && parts[parts.length - 1].length() == 2) {
                // xxx.com.cn 这类取最后三段
                return parts[parts.length - 3] + "." + parts[parts.length - 2] + "."
                        + parts[parts.length - 1];
            }
            if (parts.length >= 2) {
                return parts[parts.length - 2] + "." + parts[parts.length - 1];
            }
            return h;
        } catch (Throwable t) {
            return null;
        }
    }
}
