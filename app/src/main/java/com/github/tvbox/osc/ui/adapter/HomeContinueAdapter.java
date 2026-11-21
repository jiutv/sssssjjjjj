package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.util.DefaultConfig;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

/**
 * 液态玻璃首页「继续观看」横向卡片适配器。
 */
public class HomeContinueAdapter extends BaseQuickAdapter<VodInfo, BaseViewHolder> {

    public HomeContinueAdapter() {
        super(R.layout.item_home_continue, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo item) {
        helper.setText(R.id.tvName, TextUtils.isEmpty(item.name) ? "未知影片" : item.name);

        String note = TextUtils.isEmpty(item.playNote) ? item.note : item.playNote;
        helper.setText(R.id.tvNote, TextUtils.isEmpty(note) ? "继续观看" : "上次看到 " + note);

        ImageView ivThumb = helper.getView(R.id.ivThumb);
        if (!TextUtils.isEmpty(item.pic)) {
            Picasso.get()
                    .load(DefaultConfig.checkReplaceProxy(item.pic.trim()))
                    .placeholder(R.drawable.img_loading_placeholder)
                    .error(R.drawable.img_loading_placeholder)
                    .into(ivThumb);
        } else {
            ivThumb.setImageResource(R.drawable.img_loading_placeholder);
        }
    }
}
