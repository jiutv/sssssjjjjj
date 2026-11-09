package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Subscription;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SubscriptionAdapter extends BaseQuickAdapter<Subscription, BaseViewHolder> {
    public SubscriptionAdapter() {
        super(R.layout.item_subscription);
    }

    @Override
    protected void convert(BaseViewHolder helper, Subscription item) {
        // 名称 + URL
        helper.setText(R.id.tv_name, item.getName());
        helper.setText(R.id.tv_url, item.getUrl());

        // Switch 开关 (isChecked = 当前启用)
        helper.setChecked(R.id.cb, item.isChecked());

        // 置顶图标
        helper.setVisible(R.id.iv_pushpin, item.isTop());

        // 删除按钮可点击 (BaseQuickAdapter OnItemChildClickListener)
        helper.addOnClickListener(R.id.iv_del);

        // 禁用标签
        TextView tvDisabled = helper.getView(R.id.tv_disabled);
        tvDisabled.setVisibility(item.isDisabled() ? View.VISIBLE : View.GONE);

        // 卡片背景状态
        View itemView = helper.itemView;
        if (item.isDisabled()) {
            itemView.setBackgroundResource(R.drawable.bg_sub_card_disabled);
        } else if (item.isChecked()) {
            itemView.setBackgroundResource(R.drawable.bg_sub_card_selected);
        } else {
            itemView.setBackgroundResource(R.drawable.bg_sub_card);
        }

        // 源图标: 本地文件 vs HTTP
        ImageView ivIcon = helper.getView(R.id.iv_sub_icon);
        if (item.getUrl() != null && item.getUrl().startsWith("clan://")) {
            ivIcon.setImageResource(R.drawable.ic_sub_local);
        } else {
            ivIcon.setImageResource(R.drawable.ic_sub_source);
        }
    }

    /**
     * 刷新列表时候,添加去重和排序
     */
    @Override
    public void setNewData(@Nullable List<Subscription> data) {
        if (data != null) {
            // 去除 url 重复的订阅
            for (int i = 0; i < data.size(); i++) {
                for (int j = i + 1; j < data.size(); j++) {
                    if (data.get(i).getUrl().equals(data.get(j).getUrl())) {
                        data.remove(j);
                        j--;
                    }
                }
            }
            data.sort(mComparator);
        }
        super.setNewData(data);
    }

    Comparator<Subscription> mComparator = (s1, s2) -> {
        // 禁用的排最后
        if (s1.isDisabled() && !s2.isDisabled()) return 1;
        if (!s1.isDisabled() && s2.isDisabled()) return -1;
        if (s1.isTop() && !s2.isTop()) {
            return -1;
        } else if (!s1.isTop() && s2.isTop()) {
            return 1;
        } else if (s1.isChecked() && !s2.isChecked()) {
            return -1;
        } else if (!s1.isChecked() && s2.isChecked()) {
            return 1;
        } else {
            return 0;
        }
    };
}
