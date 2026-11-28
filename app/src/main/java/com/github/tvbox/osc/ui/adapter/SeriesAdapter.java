package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.blankj.utilcode.util.ConvertUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;

import java.util.ArrayList;

/**
 * 选集 Adapter
 * item_series 根容器 id 保持 @id/sl，setSelected 触发 background selector 切换选中/未选中态
 */
public class SeriesAdapter extends BaseQuickAdapter<VodInfo.VodSeries, BaseViewHolder> {
    private boolean isGird;

    public SeriesAdapter(boolean isGird) {
        super(R.layout.item_series, new ArrayList<>());
        this.isGird = isGird;
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo.VodSeries item) {
        View sl = helper.getView(R.id.sl);
        TextView tvSeries = helper.getView(R.id.tvSeries);
        sl.setSelected(item.selected);
        tvSeries.setText(item.name);

        if (!isGird) {
            ViewGroup.LayoutParams layoutParams = sl.getLayoutParams();
            layoutParams.width = ConvertUtils.dp2px(120);
            sl.setLayoutParams(layoutParams);
        }
    }

    public void setGird(boolean gird) {
        isGird = gird;
    }
}
