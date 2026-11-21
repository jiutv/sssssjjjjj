package com.github.tvbox.osc.ui.fragment;

import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.blankj.utilcode.util.ToastUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.adapter.HomeContinueAdapter;
import com.github.tvbox.osc.ui.adapter.HomePosterAdapter;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.UA;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 液态玻璃推荐首页：Hero 主推 + 横向海报轨 + 继续观看。
 * 数据来源保持不变：站点推荐 / 豆瓣热门。
 */
public class UserFragment extends BaseLazyFragment {

    private HomePosterAdapter posterAdapter;
    private HomeContinueAdapter continueAdapter;
    private List<Movie.Video> homeSourceRec;

    private View homeScroll;
    private View heroCard;
    private View continueHeader;
    private ImageView ivHero;
    private TextView tvHeroTitle;
    private TextView tvHeroDesc;
    private TextView tvHotCount;
    private TextView tvHotEmpty;
    private RecyclerView rvHot;
    private RecyclerView rvContinue;

    private Movie.Video heroVod;

    public static UserFragment newInstance(List<Movie.Video> recVod) {
        return new UserFragment().setArguments(recVod);
    }

    public UserFragment setArguments(List<Movie.Video> recVod) {
        this.homeSourceRec = recVod;
        return this;
    }

    /**
     * 平板横屏：AutoSize 按真实屏宽 1:1 换算，避免 360 设计稿被整体放大；其余场景保持原逻辑。
     */
    @Override
    public float getSizeInDp() {
        Configuration config = getResources().getConfiguration();
        if (config.smallestScreenWidthDp >= 600 && config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return config.screenWidthDp;
        }
        return super.getSizeInDp();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_user;
    }

    @Override
    protected void init() {
        homeScroll = findViewById(R.id.homeScroll);
        heroCard = findViewById(R.id.heroCard);
        ivHero = findViewById(R.id.ivHero);
        tvHeroTitle = findViewById(R.id.tvHeroTitle);
        tvHeroDesc = findViewById(R.id.tvHeroDesc);
        tvHotCount = findViewById(R.id.tvHotCount);
        tvHotEmpty = findViewById(R.id.tvHotEmpty);
        rvHot = findViewById(R.id.rvHot);
        rvContinue = findViewById(R.id.rvContinue);
        continueHeader = findViewById(R.id.continueHeader);

        posterAdapter = new HomePosterAdapter();
        posterAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                openVideo((Movie.Video) adapter.getItem(position));
            }
        });
        posterAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                Movie.Video vod = (Movie.Video) adapter.getItem(position);
                if (vod != null) {
                    Bundle bundle = new Bundle();
                    bundle.putString("title", vod.name);
                    jumpActivity(FastSearchActivity.class, bundle);
                }
                return true;
            }
        });
        rvHot.setLayoutManager(new LinearLayoutManager(mContext, RecyclerView.HORIZONTAL, false));
        rvHot.setAdapter(posterAdapter);

        continueAdapter = new HomeContinueAdapter();
        continueAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                openVod((VodInfo) adapter.getItem(position));
            }
        });
        rvContinue.setLayoutManager(new LinearLayoutManager(mContext, RecyclerView.HORIZONTAL, false));
        rvContinue.setAdapter(continueAdapter);

        setLoadSir2(homeScroll);
        initHomeHotVod();
        loadContinueWatching();
    }

    private void initHomeHotVod() {
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
            if (homeSourceRec != null && homeSourceRec.size() > 0) {
                render(homeSourceRec);
            } else {
                showEmpty();
            }
            return;
        }
        try {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DATE);
            String today = String.format("%d%d%d", year, month, day);
            String requestDay = Hawk.get("home_hot_day", "");
            if (requestDay.equals(today)) {
                String json = Hawk.get("home_hot", "");
                if (!json.isEmpty()) {
                    ArrayList<Movie.Video> hotMovies = loadHots(json);
                    if (hotMovies != null && hotMovies.size() > 0) {
                        render(hotMovies);
                        return;
                    }
                }
            }
            String doubanUrl = "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=" + year + "," + year;
            OkGo.<String>get(doubanUrl)
                    .headers("User-Agent", UA.randomOne())
                    .execute(new AbsCallback<String>() {
                        @Override
                        public void onSuccess(Response<String> response) {
                            String netJson = response.body();
                            Hawk.put("home_hot_day", today);
                            Hawk.put("home_hot", netJson);
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ArrayList<Movie.Video> videos = loadHots(netJson);
                                    if (videos.size() > 0) {
                                        render(videos);
                                    } else {
                                        showEmpty();
                                    }
                                }
                            });
                        }

                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            return response.body().string();
                        }
                    });
        } catch (Throwable th) {
            th.printStackTrace();
            if (posterAdapter.getItemCount() == 0) {
                showEmpty();
            }
        }
    }

    /**
     * 渲染首页：第一条作为 Hero，其余进入横向海报轨。
     */
    private void render(List<Movie.Video> videos) {
        if (videos == null || videos.isEmpty()) {
            showEmpty();
            return;
        }
        bindHero(videos.get(0));
        List<Movie.Video> rail = videos.size() > 1
                ? new ArrayList<>(videos.subList(1, videos.size()))
                : new ArrayList<>(videos);
        posterAdapter.setNewData(rail);
        tvHotCount.setText("共 " + videos.size() + " 部");
        tvHotEmpty.setVisibility(rail.isEmpty() ? View.VISIBLE : View.GONE);
        showSuccess();
    }

    private void bindHero(Movie.Video vod) {
        if (vod == null) return;
        heroVod = vod;
        tvHeroTitle.setText(TextUtils.isEmpty(vod.name) ? "" : vod.name);

        StringBuilder desc = new StringBuilder();
        if (vod.year > 0) desc.append(vod.year);
        if (!TextUtils.isEmpty(vod.note)) {
            if (desc.length() > 0) desc.append(" · ");
            desc.append(vod.note);
        }
        if (!TextUtils.isEmpty(vod.type)) {
            if (desc.length() > 0) desc.append(" · ");
            desc.append(vod.type);
        }
        tvHeroDesc.setText(desc.toString());
        tvHeroDesc.setVisibility(desc.length() == 0 ? View.GONE : View.VISIBLE);

        if (!TextUtils.isEmpty(vod.pic)) {
            Picasso.get()
                    .load(DefaultConfig.checkReplaceProxy(vod.pic.trim()))
                    .placeholder(R.drawable.img_loading_placeholder)
                    .error(R.drawable.img_loading_placeholder)
                    .into(ivHero);
        } else {
            ivHero.setImageResource(R.drawable.img_loading_placeholder);
        }
        heroCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openVideo(heroVod);
            }
        });
    }

    /**
     * 读取本地历史记录，渲染「继续观看」。
     */
    private void loadContinueWatching() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<VodInfo> result = new ArrayList<>();
                try {
                    List<VodInfo> records = RoomDataManger.getAllVodRecord(10);
                    if (records != null) {
                        for (VodInfo vod : records) {
                            if (!TextUtils.isEmpty(vod.playNote)) {
                                vod.note = vod.playNote;
                            }
                            result.add(vod);
                        }
                    }
                } catch (Throwable ignored) {
                }
                if (mActivity == null) return;
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAdded() || continueAdapter == null) return;
                        if (result.isEmpty()) {
                            continueHeader.setVisibility(View.GONE);
                            rvContinue.setVisibility(View.GONE);
                        } else {
                            continueHeader.setVisibility(View.VISIBLE);
                            rvContinue.setVisibility(View.VISIBLE);
                            continueAdapter.setNewData(result);
                        }
                    }
                });
            }
        }).start();
    }

    private void openVideo(Movie.Video vod) {
        if (vod == null) return;
        if (ApiConfig.get().getSourceBeanList().isEmpty()) {
            ToastUtils.showShort("暂无订阅");
            return;
        }
        Bundle bundle = new Bundle();
        if (!TextUtils.isEmpty(vod.id)) {
            bundle.putString("id", vod.id);
            bundle.putString("sourceKey", vod.sourceKey);
            jumpActivity(DetailActivity.class, bundle);
        } else {
            bundle.putString("title", vod.name);
            jumpActivity(FastSearchActivity.class, bundle);
        }
    }

    private void openVod(VodInfo vod) {
        if (vod == null || TextUtils.isEmpty(vod.id)) return;
        Bundle bundle = new Bundle();
        bundle.putString("id", vod.id);
        bundle.putString("sourceKey", vod.sourceKey);
        jumpActivity(DetailActivity.class, bundle);
    }

    private ArrayList<Movie.Video> loadHots(String json) {
        ArrayList<Movie.Video> result = new ArrayList<>();
        try {
            JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
            JsonArray array = infoJson.getAsJsonArray("data");
            for (JsonElement ele : array) {
                JsonObject obj = (JsonObject) ele;
                Movie.Video vod = new Movie.Video();
                vod.name = obj.get("title").getAsString();
                vod.note = obj.get("rate").getAsString();
                if (!vod.note.isEmpty()) vod.note += " 分";
                vod.pic = obj.get("cover").getAsString() + "@Referer=https://movie.douban.com/@User-Agent=" + UA.random();
                result.add(vod);
            }
        } catch (Throwable th) {

        }
        return result;
    }
}
