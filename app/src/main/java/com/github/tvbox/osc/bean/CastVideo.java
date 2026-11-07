package com.github.tvbox.osc.bean;

import java.util.UUID;

/**
 * DLNA-Cast v2.0.0 API 完全重构，不再实现 ICast 接口
 * 暂时作为 POJO 使用，投屏功能待用 cling 重写
 */
public class CastVideo {

    private final String name;
    private final String url;
    private final String id;

    public CastVideo(String name, String url) {
        this.name = name;
        this.url = url;
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public String getUri() {
        return url;
    }

    public String getName() {
        return name;
    }
}
