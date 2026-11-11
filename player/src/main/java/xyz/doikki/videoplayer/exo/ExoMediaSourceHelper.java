package xyz.doikki.videoplayer.exo;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Util;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import okhttp3.OkHttpClient;

public final class ExoMediaSourceHelper {

    private static volatile ExoMediaSourceHelper sInstance;

    // 自研的内容类型常量，避免依赖框架里已改名的 C.TYPE_* 常量
    private static final int TYPE_OTHER = 0;
    private static final int TYPE_DASH = 1;
    private static final int TYPE_HLS = 2;
    private static final int TYPE_RTSP = 3;

    /**
     * 统一的容错策略：HLS/DASH 分片加载失败时最多重试 6 次，
     * 明显降低弱网下花屏、切片跳帧的概率。
     */
    private static final LoadErrorHandlingPolicy LOAD_ERROR_POLICY =
            new DefaultLoadErrorHandlingPolicy(6);

    private final String mUserAgent;
    private final Context mAppContext;
    private OkHttpClient mOkClient = null;
    private Cache mCache;

    private ExoMediaSourceHelper(Context context) {
        mAppContext = context.getApplicationContext();
        mUserAgent = Util.getUserAgent(mAppContext, mAppContext.getApplicationInfo().name);
    }

    public static ExoMediaSourceHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ExoMediaSourceHelper.class) {
                if (sInstance == null) {
                    sInstance = new ExoMediaSourceHelper(context);
                }
            }
        }
        return sInstance;
    }

    public void setOkClient(OkHttpClient client) {
        mOkClient = client;
    }

    public MediaSource getMediaSource(String uri) {
        return getMediaSource(uri, null, false);
    }

    public MediaSource getMediaSource(String uri, Map<String, String> headers) {
        return getMediaSource(uri, headers, false);
    }

    public MediaSource getMediaSource(String uri, boolean isCache) {
        return getMediaSource(uri, null, isCache);
    }

    public MediaSource getMediaSource(String uri, Map<String, String> headers, boolean isCache) {
        return getMediaSource(uri, headers, isCache, -1);
    }

    public MediaSource getMediaSource(String uri, Map<String, String> headers, boolean isCache, int errorCode) {
        Uri contentUri = Uri.parse(uri);
        int contentType = inferContentType(uri);

        DataSource.Factory factory = isCache
                ? getCacheDataSourceFactory(headers)
                : getDataSourceFactory(headers);

        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
            // 部分直播源被识别成普通文件，这里强制按 HLS 解析
            return new DefaultMediaSourceFactory(factory, getExtractorsFactory())
                    .setLoadErrorHandlingPolicy(LOAD_ERROR_POLICY)
                    .createMediaSource(getMediaItem(uri, errorCode));
        }

        switch (contentType) {
            case TYPE_DASH:
                return new DashMediaSource.Factory(factory)
                        .setLoadErrorHandlingPolicy(LOAD_ERROR_POLICY)
                        .createMediaSource(MediaItem.fromUri(contentUri));
            case TYPE_HLS:
                return new HlsMediaSource.Factory(factory)
                        .setLoadErrorHandlingPolicy(LOAD_ERROR_POLICY)
                        .setAllowChunklessPreparation(true)
                        .createMediaSource(MediaItem.fromUri(contentUri));
            case TYPE_RTSP:
                return new RtspMediaSource.Factory()
                        .setForceUseRtpTcp(false)
                        .createMediaSource(MediaItem.fromUri(contentUri));
            default:
            case TYPE_OTHER:
                return new ProgressiveMediaSource.Factory(factory, getExtractorsFactory())
                        .setLoadErrorHandlingPolicy(LOAD_ERROR_POLICY)
                        .createMediaSource(MediaItem.fromUri(contentUri));
        }
    }

    private static MediaItem getMediaItem(String uri, int errorCode) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(uri.trim().replace("\\", "")));
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
            builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        return builder.build();
    }

    private static synchronized ExtractorsFactory getExtractorsFactory() {
        return new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                .setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 3);
    }

    private int inferContentType(String fileName) {
        fileName = fileName.toLowerCase();
        if ("rtsp".equals(Uri.parse(fileName).getScheme())) {
            return TYPE_RTSP;
        } else if (fileName.contains(".mpd")) {
            return TYPE_DASH;
        } else if (fileName.contains(".m3u8")) {
            return TYPE_HLS;
        } else {
            return TYPE_OTHER;
        }
    }

    private DataSource.Factory getCacheDataSourceFactory(Map<String, String> headers) {
        if (mCache == null) {
            mCache = newCache();
        }
        return new CacheDataSource.Factory()
                .setCache(mCache)
                .setUpstreamDataSourceFactory(getDataSourceFactory(headers))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private Cache newCache() {
        return new SimpleCache(
                new File(mAppContext.getExternalCacheDir(), "exo-video-cache"),//缓存目录
                new LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024),//缓存大小，默认512M，使用LRU算法实现
                new StandaloneDatabaseProvider(mAppContext));
    }

    /**
     * Returns a new DataSource factory.
     *
     * @return A new DataSource factory.
     */
    private DataSource.Factory getDataSourceFactory(Map<String, String> headers) {
        return new DefaultDataSource.Factory(mAppContext, getHttpDataSourceFactory(headers));
    }

    /**
     * 每次播放都新建一个 Http 数据源工厂，避免上一次播放的 header / UA 污染下一次播放。
     */
    private DataSource.Factory getHttpDataSourceFactory(Map<String, String> headers) {
        OkHttpClient client = mOkClient != null ? mOkClient : new OkHttpClient();
        OkHttpDataSource.Factory factory = new OkHttpDataSource.Factory(client);
        factory.setUserAgent(mUserAgent);
        if (headers != null && !headers.isEmpty()) {
            String userAgent = headers.get("User-Agent");
            if (!TextUtils.isEmpty(userAgent)) {
                factory.setUserAgent(userAgent.trim());
            }
            Map<String, String> requestHeaders = new HashMap<>();
            for (String key : headers.keySet()) {
                String value = headers.get(key);
                if (value != null && !"User-Agent".equalsIgnoreCase(key)) {
                    requestHeaders.put(key, value.trim());
                }
            }
            if (!requestHeaders.isEmpty()) {
                factory.setDefaultRequestProperties(requestHeaders);
            }
        }
        return factory;
    }

    public void setCache(Cache cache) {
        this.mCache = cache;
    }

}
