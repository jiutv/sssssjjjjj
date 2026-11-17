package com.github.tvbox.osc.util.live;

import android.text.TextUtils;

import com.orhanobut.hawk.Hawk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 直播网页嗅探结果记忆：记录哪些网页地址「抓不到可播的流」。
 *
 * <p>没抓到的地址下次直接进网页播放，不再干等一轮嗅探；
 * 抓成功过的地址会清掉记录，保证站点改版/源更新后能重新尝试。
 * 记录带 7 天有效期，避免一次失败永久粘住。</p>
 */
public final class LiveSniffMemory {

    private static final String KEY = "live_sniff_fail_v1";
    private static final long TTL_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_SIZE = 200;

    private LiveSniffMemory() {
    }

    private static String normalize(String pageUrl) {
        if (TextUtils.isEmpty(pageUrl)) return null;
        return pageUrl.trim().toLowerCase();
    }

    private static HashMap<String, Long> load() {
        Object obj = Hawk.get(KEY, null);
        if (obj instanceof HashMap) {
            try {
                //noinspection unchecked
                return new HashMap<>((HashMap<String, Long>) obj);
            } catch (Throwable ignored) {
            }
        }
        return new HashMap<>();
    }

    public static synchronized boolean isFailed(String pageUrl) {
        String key = normalize(pageUrl);
        if (key == null) return false;
        HashMap<String, Long> map = load();
        Long at = map.get(key);
        if (at == null) return false;
        if (System.currentTimeMillis() - at > TTL_MS) {
            map.remove(key);
            Hawk.put(KEY, map);
            return false;
        }
        return true;
    }

    public static synchronized void markFailed(String pageUrl) {
        String key = normalize(pageUrl);
        if (key == null) return;
        HashMap<String, Long> map = load();
        map.put(key, System.currentTimeMillis());
        if (map.size() > MAX_SIZE) {
            // 超量时先丢最早的一批，避免无限增长
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, Long>> it = map.entrySet().iterator();
            while (it.hasNext() && map.size() > MAX_SIZE) {
                Map.Entry<String, Long> e = it.next();
                if (now - e.getValue() > TTL_MS) it.remove();
            }
            while (map.size() > MAX_SIZE && !map.isEmpty()) {
                String oldest = null;
                long oldestAt = Long.MAX_VALUE;
                for (Map.Entry<String, Long> e : map.entrySet()) {
                    if (e.getValue() < oldestAt) {
                        oldestAt = e.getValue();
                        oldest = e.getKey();
                    }
                }
                if (oldest == null) break;
                map.remove(oldest);
            }
        }
        Hawk.put(KEY, map);
    }

    public static synchronized void markSuccess(String pageUrl) {
        String key = normalize(pageUrl);
        if (key == null) return;
        HashMap<String, Long> map = load();
        if (map.remove(key) != null) {
            Hawk.put(KEY, map);
        }
    }

    public static synchronized void clear() {
        Hawk.delete(KEY);
    }
}
