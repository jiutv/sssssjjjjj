package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.DefaultConfig;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页 Hero 海报轮播适配器。
 * 借鉴 AVBox 的做法：用一个足够大的 count 实现左右无限滑动，position 取模映射真实数据。
 */
public class HomeHeroAdapter extends PagerAdapter {

    /** 每份真实数据重复的次数，用于构造无限轮播 */
    private static final int LOOP_MULTIPLIER = 1000;

    private final List<Movie.Video> data = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Movie.Video item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<Movie.Video> videos) {
        data.clear();
        if (videos != null) {
            data.addAll(videos);
        }
        notifyDataSetChanged();
    }

    public Movie.Video getItem(int position) {
        if (data.isEmpty()) return null;
        return data.get(position % data.size());
    }

    public int getRealCount() {
        return data.size();
    }

    /** 无限轮播的起始位置：落在中间那一份数据上 */
    public int getInitialPosition() {
        if (data.size() <= 1) return 0;
        return data.size() * (LOOP_MULTIPLIER / 2);
    }

    @Override
    public int getCount() {
        if (data.size() <= 1) return data.size();
        return data.size() * LOOP_MULTIPLIER;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        final Movie.Video vod = getItem(position);

        // 竖版海报 FIT_CENTER 完整展示，两侧留白直接透出页面底色：
        // 底色由海报主色联动（参考 AVBox 的 seed color），所以两侧与下方内容同色，不再出现色带。
        FrameLayout frame = new FrameLayout(container.getContext());
        frame.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView image = new ImageView(container.getContext());
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("主推影片");
        frame.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        if (vod != null && !TextUtils.isEmpty(vod.pic)) {
            String pic = DefaultConfig.checkReplaceProxy(vod.pic.trim());
            Picasso.get()
                    .load(pic)
                    .placeholder(R.drawable.img_loading_placeholder)
                    .error(R.drawable.img_loading_placeholder)
                    .into(image);
        } else {
            image.setImageResource(R.drawable.img_loading_placeholder);
        }
        frame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null && vod != null) {
                    listener.onItemClick(vod);
                }
            }
        });

        container.addView(frame);
        return frame;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((View) object);
    }
}
