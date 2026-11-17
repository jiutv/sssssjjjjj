package com.github.tvbox.osc.util.live;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.StringReader;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class TxtSubscribe {
    public static void parse(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        ArrayList<String> arrayList;
        try {
            BufferedReader bufferedReader = new BufferedReader(new StringReader(str));
            String readLine = bufferedReader.readLine();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap2 = new LinkedHashMap<>();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap3 = linkedHashMap2;
            while (readLine != null) {
                if (readLine.trim().isEmpty()) {
                    readLine = bufferedReader.readLine();
                } else {
                    String[] split = readLine.split(",");
                    if (split.length < 2) {
                        readLine = bufferedReader.readLine();
                    } else {
                        if (readLine.contains("#genre#")) {
                            String trim = split[0].trim();
                            if (!linkedHashMap.containsKey(trim)) {
                                linkedHashMap3 = new LinkedHashMap<>();
                                linkedHashMap.put(trim, linkedHashMap3);
                            } else {
                                linkedHashMap3 = linkedHashMap.get(trim);
                            }
                        } else {
                            String trim2 = split[0].trim();
                            for (String str2 : split[1].trim().split("#")) {
                                String trim3 = str2.trim();
                                if (!trim3.isEmpty() && (trim3.startsWith("http") || trim3.startsWith("rtp") || trim3.startsWith("rtsp") || trim3.startsWith("rtmp") || trim3.startsWith("video://") || trim3.startsWith("webview://"))) {
                                    if (!linkedHashMap3.containsKey(trim2)) {
                                        arrayList = new ArrayList<>();
                                        linkedHashMap3.put(trim2, arrayList);
                                    } else {
                                        arrayList = linkedHashMap3.get(trim2);
                                    }
                                    if (!arrayList.contains(trim3)) {
                                        arrayList.add(trim3);
                                    }
                                }
                            }
                        }
                        readLine = bufferedReader.readLine();
                    }
                }
            }
            bufferedReader.close();
            if (linkedHashMap2.isEmpty()) {
                return;
            }
            linkedHashMap.put("未分组", linkedHashMap2);
        } catch (Throwable unused) {
        }
    }

    /**
     * 解析 M3U/M3U8 直播订阅（#EXTINF + group-title/tvg-name）
     */
    public static void parseM3u(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> out, String str) {
        try {
            BufferedReader reader = new BufferedReader(new StringReader(str));
            String group = "未分组";
            String name = null;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#EXTINF")) {
                    String title = "";
                    int comma = line.lastIndexOf(',');
                    if (comma >= 0 && comma < line.length() - 1) {
                        title = line.substring(comma + 1).trim();
                    }
                    String tvgName = attr(line, "tvg-name");
                    String groupTitle = attr(line, "group-title");
                    if (groupTitle != null && !groupTitle.trim().isEmpty()) {
                        group = groupTitle.trim();
                    }
                    name = (tvgName == null || tvgName.trim().isEmpty()) ? title : tvgName.trim();
                    if (name.isEmpty()) {
                        name = "未命名";
                    }
                } else if (line.startsWith("#EXTGRP:")) {
                    String extGroup = line.substring(8).trim();
                    group = extGroup.isEmpty() ? "未分组" : extGroup;
                } else if (line.startsWith("#")) {
                    continue;
                } else if (name != null) {
                    add(out, group, name, line);
                    name = null;
                }
            }
            reader.close();
        } catch (Throwable ignored) {
        }
    }

    private static void add(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> out,
                            String group, String name, String url) {
        if (!(url.startsWith("http") || url.startsWith("rtp") || url.startsWith("rtsp")
                || url.startsWith("rtmp") || url.startsWith("video://") || url.startsWith("webview://"))) {
            return;
        }
        LinkedHashMap<String, ArrayList<String>> channels = out.get(group);
        if (channels == null) {
            channels = new LinkedHashMap<>();
            out.put(group, channels);
        }
        ArrayList<String> urls = channels.get(name);
        if (urls == null) {
            urls = new ArrayList<>();
            channels.put(name, urls);
        }
        if (!urls.contains(url)) {
            urls.add(url);
        }
    }

    private static String attr(String line, String key) {
        int idx = line.indexOf(key + "=\"");
        if (idx < 0) {
            return null;
        }
        int start = idx + key.length() + 2;
        int end = line.indexOf('"', start);
        return end > start ? line.substring(start, end) : null;
    }

    public static JsonArray live2JsonArray(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap) {
        JsonArray jsonarr = new JsonArray();
        for (String str : linkedHashMap.keySet()) {
            JsonArray jsonarr2 = new JsonArray();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap2 = linkedHashMap.get(str);
            if (!linkedHashMap2.isEmpty()) {
                for (String str2 : linkedHashMap2.keySet()) {
                    ArrayList<String> arrayList = linkedHashMap2.get(str2);
                    if (!arrayList.isEmpty()) {
                        JsonArray jsonarr3 = new JsonArray();
                        for (int i = 0; i < arrayList.size(); i++) {
                            jsonarr3.add(arrayList.get(i));
                        }
                        JsonObject jsonobj = new JsonObject();
                        try {
                            jsonobj.addProperty("name", str2);
                            jsonobj.add("urls", jsonarr3);
                        } catch (Throwable e) {
                        }
                        jsonarr2.add(jsonobj);
                    }
                }
                JsonObject jsonobj2 = new JsonObject();
                try {
                    jsonobj2.addProperty("group", str);
                    jsonobj2.add("channels", jsonarr2);
                } catch (Throwable e) {
                }
                jsonarr.add(jsonobj2);
            }
        }
        return jsonarr;
    }
}
