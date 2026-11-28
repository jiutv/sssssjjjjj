package com.github.tvbox.osc.util;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 需求2：播放相关缓存（剧集列表 / 已解析播放线路）。
 * 1. 剧集列表：内存 LRU + 磁盘 JSON 缓存（冷启动也能秒出选集，随后后台刷新）；
 * 2. 播放线路：纯内存 + TTL，地址带时效，不做磁盘缓存。
 */
public class PlayCache {

    /** 剧集列表缓存有效期：10 分钟 */
    private static final long DETAIL_TTL = 10 * 60 * 1000L;
    /** 播放线路缓存有效期：10 分钟（解析地址可能带时效，不宜过长） */
    private static final long PLAY_TTL = 10 * 60 * 1000L;
    /** 磁盘剧集缓存有效期：12 小时（过期视为不可用，重新走网络） */
    private static final long DETAIL_DISK_TTL = 12 * 60 * 60 * 1000L;
    /** 最多缓存的剧集条数（只有短时间来回切换才有意义） */
    private static final int MAX_DETAIL = 4;
    /** 磁盘剧集缓存最多保留条数 */
    private static final int MAX_DETAIL_DISK = 24;
    /** 最多缓存的播放线路条数 */
    private static final int MAX_PLAY = 30;

    private static final String DETAIL_DISK_DIR = "detail_cache";
    private static final ExecutorService DISK_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Gson GSON = new Gson();

    /** LRU 内存缓存：剧集列表 */
    private static final LinkedHashMap<String, CacheEntry<AbsXml>> DETAIL_CACHE =
            new LinkedHashMap<String, CacheEntry<AbsXml>>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry<AbsXml>> eldest) {
                    return size() > MAX_DETAIL;
                }
            };

    /** LRU 内存缓存：播放线路 */
    private static final LinkedHashMap<String, CacheEntry<JSONObject>> PLAY_CACHE =
            new LinkedHashMap<String, CacheEntry<JSONObject>>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry<JSONObject>> eldest) {
                    return size() > MAX_PLAY;
                }
            };

    private static final class CacheEntry<T> {
        final T value;
        final long time;

        CacheEntry(T value) {
            this.value = value;
            this.time = System.currentTimeMillis();
        }

        boolean expired(long ttl) {
            return System.currentTimeMillis() - time > ttl;
        }
    }

    /** 剧集列表缓存 key */
    public static String detailKey(String sourceKey, String vodId) {
        return "detail_" + sourceKey + "_" + vodId;
    }

    /** 播放线路缓存 key */
    public static String playKey(String sourceKey, String vodId, String flag, int index) {
        return "play_" + sourceKey + "_" + vodId + "_" + flag + "_" + index;
    }

    /** 写入剧集列表缓存（纯内存，纳秒级，无 IO） */
    public static synchronized void putDetail(String key, AbsXml xml) {
        if (key == null || xml == null) return;
        DETAIL_CACHE.put(key, new CacheEntry<>(xml));
    }

    /** 读取剧集列表缓存，过期或不存在返回 null */
    public static synchronized AbsXml getDetail(String key) {
        if (key == null) return null;
        CacheEntry<AbsXml> entry = DETAIL_CACHE.get(key);
        if (entry == null) return null;
        if (entry.expired(DETAIL_TTL)) {
            DETAIL_CACHE.remove(key);
            return null;
        }
        return entry.value;
    }

    /** 写入已解析播放线路缓存（纯内存，无 IO） */
    public static synchronized void putPlay(String key, JSONObject info) {
        if (key == null || info == null) return;
        PLAY_CACHE.put(key, new CacheEntry<>(info));
    }

    /** 读取播放线路缓存，过期或不存在返回 null */
    public static synchronized JSONObject getPlay(String key) {
        if (key == null) return null;
        CacheEntry<JSONObject> entry = PLAY_CACHE.get(key);
        if (entry == null) return null;
        if (entry.expired(PLAY_TTL)) {
            PLAY_CACHE.remove(key);
            return null;
        }
        return entry.value;
    }

    /** 移除单条缓存（解析失败时调用，避免复用失效地址） */
    public static synchronized void remove(String key) {
        if (key == null) return;
        DETAIL_CACHE.remove(key);
        PLAY_CACHE.remove(key);
        if (key.startsWith("detail_")) {
            final File file = detailDiskFile(key);
            DISK_EXECUTOR.execute(file::delete);
        }
    }

    // ===================== 磁盘剧集缓存（冷启动秒出选集） =====================

    private static File detailDiskFile(String key) {
        File dir = new File(App.getInstance().getCacheDir(), DETAIL_DISK_DIR);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, MD5.string2MD5(key) + ".json");
    }

    /** 判断是否为磁盘剧集缓存 key */
    public static boolean isDetailKey(String key) {
        return key != null && key.startsWith("detail_");
    }

    /** 写入磁盘剧集缓存（异步写，只缓存网络拿到的新数据；内存里已有对应 key 才会被用到） */
    public static void putDetailDisk(String key, AbsXml xml) {
        if (key == null || xml == null || xml.movie == null) return;
        final String cacheKey = key;
        final AbsXml cacheXml = xml;
        DISK_EXECUTOR.execute(() -> {
            try {
                JSONObject obj = new JSONObject();
                obj.put("t", System.currentTimeMillis());
                obj.put("movie", GSON.toJson(cacheXml.movie));
                File file = detailDiskFile(cacheKey);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(obj.toString().getBytes(StandardCharsets.UTF_8));
                }
                trimDetailDisk(file.getParentFile());
            } catch (Throwable ignored) {
            }
        });
    }

    /** 读取磁盘剧集缓存：命中返回剧集数据，过期/损坏返回 null */
    public static AbsXml getDetailDisk(String key) {
        if (!isDetailKey(key)) return null;
        File file = detailDiskFile(key);
        if (!file.exists()) return null;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int len;
            while ((len = reader.read(buf)) > 0) {
                sb.append(buf, 0, len);
            }
            JSONObject obj = new JSONObject(sb.toString());
            if (System.currentTimeMillis() - obj.optLong("t", 0) > DETAIL_DISK_TTL) {
                file.delete();
                return null;
            }
            Movie movie = GSON.fromJson(obj.optString("movie", ""), Movie.class);
            if (movie == null || movie.videoList == null || movie.videoList.isEmpty()) return null;
            AbsXml xml = new AbsXml();
            xml.movie = movie;
            return xml;
        } catch (Throwable t) {
            file.delete();
            return null;
        }
    }

    /** 磁盘缓存条数超限时按最后修改时间淘汰最旧的 */
    private static void trimDetailDisk(File dir) {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null || files.length <= MAX_DETAIL_DISK) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int over = files.length - MAX_DETAIL_DISK;
        for (int i = 0; i < over; i++) {
            files[i].delete();
        }
    }
}
