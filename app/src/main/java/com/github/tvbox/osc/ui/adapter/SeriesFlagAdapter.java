package com.github.tvbox.osc.ui.adapter;

import android.view.View;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;

import java.util.ArrayList;

/**
 * 线路 tab Adapter
 * 根容器 id 还是根 LinearLayout（没有 id），通过 BaseViewHolder 的 itemView 取根。
 * setSelected(item.selected) 触发 background selector 切换 accent_soft 填充态 / 玻璃底态
 * vFlag 旧底部横线已废弃（visibility=gone），保留 id 兼容。
 */
public class SeriesFlagAdapter extends BaseQuickAdapter<VodInfo.VodSeriesFlag, BaseViewHolder> {
    public SeriesFlagAdapter() {
        super(R.layout.item_select_flag, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo.VodSeriesFlag item) {
        // 给根容器设 selected → background selector 自动切换 accent 高亮态
        helper.itemView.setSelected(item.selected);
        helper.setText(R.id.tvFlag, item.name);
    }
}
