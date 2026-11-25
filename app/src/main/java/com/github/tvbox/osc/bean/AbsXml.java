package com.github.tvbox.osc.bean;

import com.thoughtworks.xstream.annotations.XStreamAlias;

import java.io.Serializable;

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
@XStreamAlias("rss")
public class AbsXml implements Serializable {
    @XStreamAlias("list")
    public Movie movie;
    /** 搜索批次标记：用于丢弃上一次搜索迟到的结果（空串/空表示非搜索场景） */
    public String searchToken;
    /** 搜索结果所属的源 key，空结果时也能定位是哪个源返回的 */
    public String sourceKey;
}