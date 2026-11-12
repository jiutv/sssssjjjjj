package com.github.tvbox.osc.util;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.AbsXml;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * 需求2：播放相关本地缓存（剧集列表 / 已解析播放线路）。
 * 采用文件缓存 + TTL（存活时间），短时间内重复打开同一视频时优先命中缓存，
 * 避免重复请求爬虫和重复解析，从而缩短首屏等待时间。
 */
public class PlayCache {

    /** 剧集列表缓存有效期：10 分钟 */
    private static final long DETAIL_TTL = 10 * 60 * 1000L;
    /** 播放线路缓存有效期：10 分钟（解析地址可能带时效，不宜过长） */
    private static final long PLAY_TTL = 10 * 60 * 1000L;
    private static final String DIR_NAME = "play_cache";
    private static final Gson GSON = new Gson();

    private static File dir() {
        File dir = new File(App.getInstance().getCacheDir(), DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 剧集列表缓存 key */
    public static String detailKey(String sourceKey, String vodId) {
        return "detail_" + sourceKey + "_" + vodId;
    }

    /** 播放线路缓存 key */
    public static String playKey(String sourceKey, String vodId, String flag, int index) {
        return "play_" + sourceKey + "_" + vodId + "_" + flag + "_" + index;
    }

    /** 写入剧集列表缓存 */
    public static void putDetail(String key, AbsXml xml) {
        try {
            write(key, GSON.toJson(xml));
        } catch (Throwable ignored) {
        }
    }

    /** 读取剧集列表缓存，过期或不存在返回 null */
    public static AbsXml getDetail(String key) {
        try {
            String json = read(key, DETAIL_TTL);
            return json == null ? null : GSON.fromJson(json, AbsXml.class);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 写入已解析播放线路缓存 */
    public static void putPlay(String key, JSONObject info) {
        try {
            if (info != null) write(key, info.toString());
        } catch (Throwable ignored) {
        }
    }

    /** 读取播放线路缓存，过期或不存在返回 null */
    public static JSONObject getPlay(String key) {
        try {
            String json = read(key, PLAY_TTL);
            return json == null ? null : new JSONObject(json);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void remove(String key) {
        try {
            fileOf(key).delete();
        } catch (Throwable ignored) {
        }
    }

    private static File fileOf(String key) {
        // 用 hash + 长度作为文件名，避免中文/特殊字符导致文件创建失败
        return new File(dir(), Integer.toHexString(key.hashCode()) + "_" + key.length() + ".cache");
    }

    private static void write(String key, String content) throws Exception {
        File file = fileOf(key);
        FileOutputStream fos = new FileOutputStream(file);
        try {
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
        } finally {
            fos.close();
        }
        file.setLastModified(System.currentTimeMillis());
    }

    private static String read(String key, long ttl) throws Exception {
        File file = fileOf(key);
        if (!file.exists()) return null;
        if (System.currentTimeMillis() - file.lastModified() > ttl) {
            file.delete();
            return null;
        }
        FileInputStream fis = new FileInputStream(file);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            fis.close();
        }
    }
}
