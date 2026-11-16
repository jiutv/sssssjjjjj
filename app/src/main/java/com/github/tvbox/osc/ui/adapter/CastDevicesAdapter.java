package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.cast.DlnaCastManager;

/**
 * 局域网内可投屏的 DLNA 设备列表
 */
public class CastDevicesAdapter extends BaseQuickAdapter<DlnaCastManager.Device, BaseViewHolder> {

    public CastDevicesAdapter() {
        super(R.layout.item_title);
    }

    @Override
    protected void convert(BaseViewHolder helper, DlnaCastManager.Device item) {
        helper.setText(R.id.title, item.name);
    }
}
