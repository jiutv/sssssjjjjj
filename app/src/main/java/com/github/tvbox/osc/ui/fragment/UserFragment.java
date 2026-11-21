package com.github.tvbox.osc.ui.fragment;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

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
import com.github.tvbox.osc.ui.adapter.HomeHeroAdapter;
import com.github.tvbox.osc.ui.adapter.HomePosterAdapter;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 液态玻璃推荐首页：Hero 主推 + 横向海报轨 + 继续观看。
 * 数据来源保持不变：站点推荐 / 豆瓣热门。
 */
public class UserFragment extends BaseLazyFragment {

    /** Hero 轮播最多展示的影片数（与 AVBox 一致） */
    private static final int HERO_VIDEO_COUNT = 5;
    /** 自动轮播间隔 */
    private static final long HERO_AUTO_INTERVAL = 5000L;

    private HomePosterAdapter posterAdapter;
    private HomeContinueAdapter continueAdapter;
    private HomeHeroAdapter heroAdapter;
    private List<Movie.Video> homeSourceRec;

    private View homeScroll;
    private View continueHeader;
    private ViewPager heroPager;
    private LinearLayout heroDots;
    private TextView tvHeroTitle;
    private TextView tvHeroDesc;
    private TextView tvHotCount;
    private TextView tvHotEmpty;
    private RecyclerView rvHot;
    private RecyclerView rvContinue;

    private List<Movie.Video> heroVideos = new ArrayList<>();

    private Handler heroHandler;
    private Runnable heroAutoRunnable;

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
        heroPager = findViewById(R.id.heroPager);
        heroDots = findViewById(R.id.heroDots);
        tvHeroTitle = findViewById(R.id.tvHeroTitle);
        tvHeroDesc = findViewById(R.id.tvHeroDesc);
        tvHotCount = findViewById(R.id.tvHotCount);
        tvHotEmpty = findViewById(R.id.tvHotEmpty);
        rvHot = findViewById(R.id.rvHot);
        rvContinue = findViewById(R.id.rvContinue);
        continueHeader = findViewById(R.id.continueHeader);

        heroHandler = new Handler(Looper.getMainLooper());
        heroAutoRunnable = new Runnable() {
            @Override
            public void run() {
                if (heroAdapter == null || heroAdapter.getRealCount() <= 1) return;
                // 翻页后由 onPageSelected/onPageScrollStateChanged 续约下一次，避免重复调度
                heroPager.setCurrentItem(heroPager.getCurrentItem() + 1, true);
            }
        };

        heroAdapter = new HomeHeroAdapter();
        heroAdapter.setOnItemClickListener(new HomeHeroAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Movie.Video item) {
                openVideo(item);
            }
        });
        heroPager.setAdapter(heroAdapter);
        heroPager.setOffscreenPageLimit(1);
        heroPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                if (heroAdapter == null || heroAdapter.getRealCount() == 0) return;
                bindHeroInfo(heroAdapter.getItem(position));
                updateHeroDots(position % heroAdapter.getRealCount());
                startHeroAutoScroll();
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager.SCROLL_STATE_DRAGGING) {
                    stopHeroAutoScroll();
                } else if (state == ViewPager.SCROLL_STATE_IDLE) {
                    startHeroAutoScroll();
                }
            }
        });

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
     * 渲染首页：取前 5 条做 Hero 轮播，其余进入横向海报轨（与 AVBox 的取数方式一致）。
     */
    private void render(List<Movie.Video> videos) {
        if (videos == null || videos.isEmpty()) {
            showEmpty();
            return;
        }
        int heroCount = Math.min(HERO_VIDEO_COUNT, videos.size());
        heroVideos = new ArrayList<>(videos.subList(0, heroCount));
        setupHero();

        List<Movie.Video> rail;
        if (videos.size() > heroCount) {
            rail = new ArrayList<>(videos.subList(heroCount, videos.size()));
        } else {
            rail = new ArrayList<>(videos);
        }
        posterAdapter.setNewData(rail);
        tvHotCount.setText("共 " + videos.size() + " 部");
        tvHotEmpty.setVisibility(rail.isEmpty() ? View.VISIBLE : View.GONE);
        showSuccess();
    }

    private void setupHero() {
        if (heroVideos.isEmpty()) return;
        heroAdapter.setData(heroVideos);
        heroPager.setCurrentItem(heroAdapter.getInitialPosition(), false);
        bindHeroInfo(heroVideos.get(0));
        buildHeroDots(heroVideos.size(), 0);
        startHeroAutoScroll();
    }

    private void bindHeroInfo(Movie.Video vod) {
        if (vod == null) return;
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
    }

    private void buildHeroDots(int count, int selected) {
        heroDots.removeAllViews();
        if (count <= 1) {
            heroDots.setVisibility(View.GONE);
            return;
        }
        heroDots.setVisibility(View.VISIBLE);
        for (int i = 0; i < count; i++) {
            View dot = new View(mContext);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(i == selected ? 18 : 6), dp(6));
            lp.setMargins(dp(3), 0, dp(3), 0);
            dot.setLayoutParams(lp);
            dot.setBackground(dotDrawable(i == selected));
            heroDots.addView(dot);
        }
    }

    private void updateHeroDots(int selected) {
        int count = heroDots.getChildCount();
        for (int i = 0; i < count; i++) {
            View dot = heroDots.getChildAt(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            lp.width = dp(i == selected ? 18 : 6);
            lp.setMargins(dp(3), 0, dp(3), 0);
            dot.setLayoutParams(lp);
            dot.setBackground(dotDrawable(i == selected));
        }
    }

    private GradientDrawable dotDrawable(boolean active) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(3));
        drawable.setColor(active ? Color.WHITE : 0x66FFFFFF);
        return drawable;
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private void startHeroAutoScroll() {
        if (heroHandler == null || heroAutoRunnable == null) return;
        heroHandler.removeCallbacks(heroAutoRunnable);
        if (heroAdapter != null && heroAdapter.getRealCount() > 1) {
            heroHandler.postDelayed(heroAutoRunnable, HERO_AUTO_INTERVAL);
        }
    }

    private void stopHeroAutoScroll() {
        if (heroHandler != null) {
            heroHandler.removeCallbacks(heroAutoRunnable);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startHeroAutoScroll();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopHeroAutoScroll();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopHeroAutoScroll();
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
