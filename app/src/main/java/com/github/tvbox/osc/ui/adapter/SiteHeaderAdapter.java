package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.util.ArrayList;

/** 「全部站点结果」里的站点分组标题 */
public class SiteHeaderAdapter extends BaseQuickAdapter<String, BaseViewHolder> {

    public SiteHeaderAdapter() {
        super(R.layout.item_all_sites_header, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.tvSiteHeader, item);
    }
}
