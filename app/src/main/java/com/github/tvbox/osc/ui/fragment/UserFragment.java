package com.github.tvbox.osc.ui.fragment;

import android.animation.ValueAnimator;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.blankj.utilcode.util.ToastUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.adapter.HomeHeroAdapter;
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
import com.squareup.picasso.Target;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 液态玻璃推荐首页：Hero 主推 + 热门推荐竖版网格。
 * 数据来源保持不变：站点推荐 / 豆瓣热门。
 */
public class UserFragment extends BaseLazyFragment {

    /** Hero 轮播最多展示的影片数 */
    private static final int HERO_VIDEO_COUNT = 10;

    private HomePosterAdapter posterAdapter;
    private HomeHeroAdapter heroAdapter;
    private List<Movie.Video> homeSourceRec;

    private View homeScroll;
    private View heroCard;
    private ViewPager heroPager;
    private View heroFade;
    private View categorySection;
    private LinearLayout heroDots;
    private LinearLayout chipContainer;
    private TextView tvHeroTitle;
    private TextView tvHeroDesc;
    private TextView tvHotCount;
    private TextView tvHotEmpty;
    private RecyclerView rvHot;

    private List<Movie.Video> heroVideos = new ArrayList<>();

    /** Hero 图片主色缓存：页面底色/底部渐变跟随海报主色（参考 AVBox 的 seed color） */
    private final Map<String, Integer> heroSeedCache = new HashMap<>();
    private final Map<String, Target> heroSeedTargets = new HashMap<>();
    private int heroHeightPx;
    private int pageTintColor;
    private ValueAnimator tintAnimator;

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
        heroPager = findViewById(R.id.heroPager);
        heroFade = findViewById(R.id.heroFade);
        heroDots = findViewById(R.id.heroDots);
        categorySection = findViewById(R.id.categorySection);
        chipContainer = findViewById(R.id.chipContainer);
        tvHeroTitle = findViewById(R.id.tvHeroTitle);
        tvHeroDesc = findViewById(R.id.tvHeroDesc);
        tvHotCount = findViewById(R.id.tvHotCount);
        tvHotEmpty = findViewById(R.id.tvHotEmpty);
        rvHot = findViewById(R.id.rvHot);

        applyHeroHeight();
        pageTintColor = themeColor(R.attr.skin_page);
        applyHeroFade(pageTintColor);
        buildCategoryChips();

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
                applyHeroTint(heroAdapter.getItem(position));
            }

            @Override
            public void onPageScrollStateChanged(int state) {
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
        rvHot.setLayoutManager(new GridLayoutManager(mContext, getHotSpanCount()));
        rvHot.setAdapter(posterAdapter);

        setLoadSir2(homeScroll);
        initHomeHotVod();
    }

    /** 热门推荐竖版网格列数：手机 3 列，平板横屏按宽度铺更多列 */
    private int getHotSpanCount() {
        if (isTabletLandscape()) {
            int width = getResources().getConfiguration().screenWidthDp;
            return Math.max(3, width / 120);
        }
        return 3;
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
     * 渲染首页：前 10 条做 Hero 轮播（手动滑动），其余进入横向海报轨。
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
        applyHeroTint(heroVideos.get(0));
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

    /** Hero 使用竖版大图：高度取屏幕 53%，并夹在 340dp ~ 520dp */
    private void applyHeroHeight() {
        if (heroCard == null) return;
        int target = (int) (getResources().getDisplayMetrics().heightPixels * 0.53f);
        target = Math.max(dp(340), Math.min(dp(520), target));
        heroHeightPx = target;
        ViewGroup.LayoutParams lp = heroCard.getLayoutParams();
        if (lp != null && lp.height != target) {
            lp.height = target;
            heroCard.setLayoutParams(lp);
        }
    }

    /**
     * 参考 AVBox HeroSpotlightFadeStops：海报从 45% 高度开始，按曲线逐渐渐隐到底色，
     * 而不是一刀切的黑色蒙层。
     */
    private void applyHeroFade(int color) {
        if (heroFade == null) return;
        int height = heroHeightPx > 0 ? heroHeightPx : dp(440);
        float[] positions = {0.45f, 0.55f, 0.65f, 0.75f, 0.85f, 0.93f, 1f};
        float[] alphas = {0f, 0.22f, 0.47f, 0.70f, 0.90f, 0.975f, 1f};
        int[] colors = new int[positions.length];
        for (int i = 0; i < positions.length; i++) {
            colors[i] = (color & 0x00FFFFFF) | (Math.round(alphas[i] * 255) << 24);
        }
        ShapeDrawable fade = new ShapeDrawable(new RectShape());
        fade.getPaint().setShader(new LinearGradient(0, 0, 0, height, colors, positions,
                Shader.TileMode.CLAMP));
        heroFade.setBackground(fade);
    }

    /**
     * AVBox 的做法：从海报里提取主色（seed color），页面底色和海报底部渐变都跟随它，
     * 这样海报和下方内容才会是同一个色系。这里再与皮肤底色混合，保证固定文字色有足够对比度。
     */
    private void applyHeroTint(Movie.Video video) {
        if (video == null || TextUtils.isEmpty(video.pic)) return;
        final String pic = DefaultConfig.checkReplaceProxy(video.pic.trim());
        Integer cached = heroSeedCache.get(pic);
        if (cached != null) {
            animateTint(cached);
            return;
        }
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                heroSeedTargets.remove(pic);
                int seed = dominantColor(bitmap);
                heroSeedCache.put(pic, seed);
                Movie.Video current = heroAdapter == null ? null
                        : heroAdapter.getItem(heroPager.getCurrentItem());
                if (current != null && !TextUtils.isEmpty(current.pic)
                        && pic.equals(DefaultConfig.checkReplaceProxy(current.pic.trim()))) {
                    animateTint(seed);
                }
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                heroSeedTargets.remove(pic);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
            }
        };
        // Picasso 对 Target 是弱引用，必须自己持有，避免回调被回收
        heroSeedTargets.put(pic, target);
        Picasso.get().load(pic).resize(24, 24).centerCrop().into(target);
    }

    private void animateTint(int seed) {
        int pageColor = themeColor(R.attr.skin_page);
        // 与皮肤底色混合，避免亮色海报把浅色皮肤的文字吃掉
        int target = blendColor(pageColor, seed, 0.26f);
        if (target == pageTintColor) return;
        if (tintAnimator != null) tintAnimator.cancel();
        tintAnimator = ValueAnimator.ofArgb(pageTintColor, target);
        tintAnimator.setDuration(320);
        tintAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                pageTintColor = (Integer) animation.getAnimatedValue();
                if (rootView != null) rootView.setBackgroundColor(pageTintColor);
                applyHeroFade(pageTintColor);
            }
        });
        tintAnimator.start();
    }

    /** 取海报主色：按 4bit 色桶统计，偏向有彩度的色块，避免整张图偏灰/偏黑 */
    private static int dominantColor(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        int bucketCount = 1 << 12;
        int[] counts = new int[bucketCount];
        long[] sumR = new long[bucketCount];
        long[] sumG = new long[bucketCount];
        long[] sumB = new long[bucketCount];
        for (int pixel : pixels) {
            if (((pixel >>> 24) & 0xFF) < 128) continue;
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            int index = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
            counts[index]++;
            sumR[index] += r;
            sumG[index] += g;
            sumB[index] += b;
        }
        int best = -1;
        double bestScore = 0;
        for (int i = 0; i < bucketCount; i++) {
            if (counts[i] == 0) continue;
            int r = (int) (sumR[i] / counts[i]);
            int g = (int) (sumG[i] / counts[i]);
            int b = (int) (sumB[i] / counts[i]);
            int chroma = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
            double score = counts[i] * (32.0 + chroma);
            if (score > bestScore) {
                bestScore = score;
                best = Color.rgb(r, g, b);
            }
        }
        return best == -1 ? Color.GRAY : best;
    }

    private static int blendColor(int base, int tint, float ratio) {
        float rest = 1f - ratio;
        int r = (int) (((base >> 16) & 0xFF) * rest + ((tint >> 16) & 0xFF) * ratio);
        int g = (int) (((base >> 8) & 0xFF) * rest + ((tint >> 8) & 0xFF) * ratio);
        int b = (int) ((base & 0xFF) * rest + (tint & 0xFF) * ratio);
        return Color.rgb(r, g, b);
    }

    /** 分类入口放在海报下方（参考 AVBox），点击直接切到对应分类页 */
    private void buildCategoryChips() {
        if (chipContainer == null || categorySection == null) return;
        chipContainer.removeAllViews();
        Fragment parent = getParentFragment();
        if (isTabletLandscape() || !(parent instanceof HomeFragment)) {
            categorySection.setVisibility(View.GONE);
            return;
        }
        List<String> names = ((HomeFragment) parent).getCategoryNames();
        if (names == null || names.size() <= 1) {
            categorySection.setVisibility(View.GONE);
            return;
        }
        categorySection.setVisibility(View.VISIBLE);
        for (int i = 0; i < names.size(); i++) {
            final int index = i;
            TextView chip = new TextView(mContext);
            chip.setText(names.get(i));
            chip.setTextSize(11);
            chip.setSingleLine(true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(8), 0, dp(8), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
            lp.setMarginEnd(dp(4));
            chip.setLayoutParams(lp);
            if (i == 0) {
                chip.setBackgroundResource(R.drawable.bg_chip_active);
                chip.setTextColor(themeColor(R.attr.skin_on_accent));
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip);
                chip.setTextColor(themeColor(R.attr.skin_text_primary));
            }
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Fragment p = getParentFragment();
                    if (p instanceof HomeFragment) {
                        ((HomeFragment) p).selectCategory(index);
                    }
                }
            });
            chipContainer.addView(chip);
        }
    }

    private boolean isTabletLandscape() {
        Configuration config = getResources().getConfiguration();
        return config.smallestScreenWidthDp >= 600 && config.orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private int themeColor(int attr) {
        TypedValue tv = new TypedValue();
        if (mContext.getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) {
                return ContextCompat.getColor(mContext, tv.resourceId);
            }
            return tv.data;
        }
        return Color.BLACK;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (tintAnimator != null) {
            tintAnimator.cancel();
            tintAnimator = null;
        }
        for (Target target : heroSeedTargets.values()) {
            Picasso.get().cancelRequest(target);
        }
        heroSeedTargets.clear();
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
