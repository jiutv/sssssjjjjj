package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.AbsXml;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 需求2：播放相关缓存（剧集列表 / 已解析播放线路）。
 * 改为纯内存缓存 + TTL（存活时间）：
 * 1. 不再做任何磁盘读写和 JSON 序列化，避免主线程卡顿（这是加载变慢的主要原因）；
 * 2. 短时间内重复打开同一视频时优先命中缓存，避免重复请求爬虫、重复解析线路。
 */
public class PlayCache {

    /** 剧集列表缓存有效期：10 分钟 */
    private static final long DETAIL_TTL = 10 * 60 * 1000L;
    /** 播放线路缓存有效期：10 分钟（解析地址可能带时效，不宜过长） */
    private static final long PLAY_TTL = 10 * 60 * 1000L;
    /** 最多缓存的剧集条数（只有短时间来回切换才有意义） */
    private static final int MAX_DETAIL = 4;
    /** 最多缓存的播放线路条数 */
    private static final int MAX_PLAY = 30;

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
    }
}
