package com.github.tvbox.osc.bean;

public class CastVideo {

    private final String name;
    private final String url;

    public CastVideo(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }
}
