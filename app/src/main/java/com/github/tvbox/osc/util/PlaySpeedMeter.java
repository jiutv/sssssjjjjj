package com.github.tvbox.osc.util;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * 播放测速：解析某个源的播放地址，实际读取一小段数据，测真实加载速度。
 * 只做测速，不参与播放。
 */
public class PlaySpeedMeter {

    public interface Callback {
        /**
         * @param scoreMs 读取 CHUNK 字节耗时(ms)，失败为 -1
         * @param bytes   实际读到的字节数
         */
        void onResult(String sourceKey, long scoreMs, long bytes);
    }

    private static final int CHUNK = 256 * 1024;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build();

    public static void measure(final String sourceKey, final AbsXml detail, final int targetEp,
                               final String preferFlag, final Callback callback) {
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                long score = -1;
                long bytes = 0;
                try {
                    String url = resolveUrl(sourceKey, detail, targetEp, preferFlag);
                    if (!TextUtils.isEmpty(url)) {
                        String media = url;
                        if (url.contains(".m3u8")) {
                            String seg = firstSegment(url);
                            if (!TextUtils.isEmpty(seg)) media = seg;
                        }
                        long[] r = probe(media);
                        score = r[0];
                        bytes = r[1];
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                final long s = score;
                final long b = bytes;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(sourceKey, s, b);
                    }
                });
            }
        });
    }

    private static String resolveUrl(String sourceKey, AbsXml detail, int targetEp, String preferFlag) {
        if (detail == null || detail.movie == null || detail.movie.videoList == null
                || detail.movie.videoList.isEmpty()) return null;
        Movie.Video video = detail.movie.videoList.get(0);
        if (video.urlBean == null || video.urlBean.infoList == null || video.urlBean.infoList.isEmpty()) return null;
        Movie.Video.UrlBean.UrlInfo line = video.urlBean.infoList.get(0);
        if (!TextUtils.isEmpty(preferFlag)) {
            for (Movie.Video.UrlBean.UrlInfo one : video.urlBean.infoList) {
                if (preferFlag.equals(one.flag)) {
                    line = one;
                    break;
                }
            }
        }
        if (line.beanList == null || line.beanList.isEmpty()) return null;
        int idx = Math.max(0, Math.min(targetEp, line.beanList.size() - 1));
        Movie.Video.UrlBean.UrlInfo.InfoBean ep = line.beanList.get(idx);
        if (ep == null || TextUtils.isEmpty(ep.url)) return null;

        SourceBean bean = ApiConfig.get().getSource(sourceKey);
        if (bean == null) return null;
        try {
            if (bean.getType() == 3) {
                Spider spider = ApiConfig.get().getCSP(bean);
                String content = spider.playerContent(line.flag, ep.url, ApiConfig.get().getVipParseFlags());
                if (TextUtils.isEmpty(content)) return null;
                JSONObject obj = new JSONObject(content);
                // 需要解析器的源测不了真实播放速度，交给调用方按未知处理
                if (obj.optInt("parse", 0) != 0) return null;
                return obj.optString("url", "");
            }
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
        String url = ep.url;
        String low = url.toLowerCase();
        if (low.contains(".m3u8") || low.contains(".mp4") || low.contains(".flv")
                || low.contains(".ts") || low.contains(".mkv")) {
            return url;
        }
        return null;
    }

    private static String firstSegment(String m3u8Url) {
        try {
            Request request = new Request.Builder()
                    .url(m3u8Url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();
            okhttp3.Response response = CLIENT.newCall(request).execute();
            try {
                if (!response.isSuccessful() || response.body() == null) return null;
                String text = response.body().string();
                for (String line : text.split("\n")) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (line.startsWith("http")) return line;
                    int p = m3u8Url.lastIndexOf('/');
                    if (p > 0) return m3u8Url.substring(0, p + 1) + line;
                    return null;
                }
            } finally {
                response.close();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    /** 读取前 CHUNK 字节并计时，返回 {耗时ms, 字节数} */
    private static long[] probe(String url) {
        long start = System.currentTimeMillis();
        long total = 0;
        okhttp3.Response response = null;
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Range", "bytes=0-" + (CHUNK - 1))
                    .build();
            response = CLIENT.newCall(request).execute();
            if (!response.isSuccessful() || response.body() == null) return new long[]{-1, 0};
            InputStream is = response.body().byteStream();
            byte[] buf = new byte[8192];
            int r;
            while (total < CHUNK && (r = is.read(buf)) > 0) {
                total += r;
            }
        } catch (Throwable th) {
            return new long[]{-1, total};
        } finally {
            if (response != null) response.close();
        }
        if (total <= 0) return new long[]{-1, 0};
        return new long[]{System.currentTimeMillis() - start, total};
    }
}
