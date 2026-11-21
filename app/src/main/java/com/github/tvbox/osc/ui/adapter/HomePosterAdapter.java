package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.DefaultConfig;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

/**
 * 液态玻璃首页横向海报轨适配器（2:3 海报卡片）。
 */
public class HomePosterAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {

    public HomePosterAdapter() {
        super(R.layout.item_home_poster, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        helper.setText(R.id.tvName, TextUtils.isEmpty(item.name) ? "" : item.name);

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

        // 右上角评分 / 备注标记
        TextView tvNote = helper.getView(R.id.tvNote);
        if (TextUtils.isEmpty(item.note)) {
            tvNote.setVisibility(View.GONE);
        } else {
            tvNote.setVisibility(View.VISIBLE);
            tvNote.setText(item.note);
        }

        // 年份 · 类型
        StringBuilder meta = new StringBuilder();
        if (item.year > 0) {
            meta.append(item.year);
        }
        if (!TextUtils.isEmpty(item.type)) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(item.type);
        }
        TextView tvMeta = helper.getView(R.id.tvMeta);
        if (meta.length() == 0) {
            tvMeta.setVisibility(View.GONE);
        } else {
            tvMeta.setVisibility(View.VISIBLE);
            tvMeta.setText(meta.toString());
        }
    }
}
