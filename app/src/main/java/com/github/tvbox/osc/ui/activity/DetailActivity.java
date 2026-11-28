package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Rational;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.ConvertUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.NotificationUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.ServiceUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseVbActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.CastVideo;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.constant.IntentKey;
import com.github.tvbox.osc.databinding.ActivityDetailBinding;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.picasso.BlurTransformation;
import com.github.tvbox.osc.picasso.RoundTransformation;
import com.github.tvbox.osc.receiver.BatteryReceiver;
import com.github.tvbox.osc.service.PlayService;
import com.github.tvbox.osc.ui.adapter.ParseAdapter;
import com.github.tvbox.osc.ui.adapter.SeriesAdapter;
import com.github.tvbox.osc.ui.adapter.SeriesFlagAdapter;
import com.github.tvbox.osc.ui.dialog.AllVodSeriesBottomDialog;
import com.github.tvbox.osc.ui.dialog.AllVodSeriesRightDialog;
import com.github.tvbox.osc.ui.dialog.CastListDialog;
import com.github.tvbox.osc.ui.dialog.QuickSearchDialog;
import com.github.tvbox.osc.ui.dialog.VideoDetailDialog;
import com.github.tvbox.osc.ui.fragment.PlayFragment;
import com.github.tvbox.osc.ui.widget.GridSpacingItemDecoration;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayCache;
import com.github.tvbox.osc.util.PlaySpeedMeter;
import com.github.tvbox.osc.util.ScreenShotListenManager;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.tvbox.osc.util.SubtitleHelper;
import com.github.tvbox.osc.util.Utils;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.gyf.immersionbar.ImmersionBar;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.enums.PopupPosition;
import com.lxj.xpopup.interfaces.OnSelectListener;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.squareup.picasso.Picasso;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */

public class DetailActivity extends BaseVbActivity<ActivityDetailBinding> {
    @Override
    protected ActivityDetailBinding vbInflate(android.view.LayoutInflater inflater, android.view.ViewGroup parent, boolean attachToRoot) {
        return ActivityDetailBinding.inflate(inflater, parent, attachToRoot);
    }

    private PlayFragment playFragment = null;
    private SourceViewModel sourceViewModel;
    private Movie.Video mVideo;
    private VodInfo vodInfo;
    public SeriesFlagAdapter seriesFlagAdapter;
    public SeriesAdapter seriesAdapter;
    public String vodId;
    public String sourceKey;
    /** 搜索结果带过来的同剧其他播放源（与 id 一一对应），用于在播放页内直接切源 */
    private final ArrayList<String> playSourceKeys = new ArrayList<>();
    private final ArrayList<String> playSourceIds = new ArrayList<>();
    private final ArrayList<String> playSourceNotes = new ArrayList<>();
    /** 各源的搜索响应耗时(ms)，与上面的 key 一一对应，-1 表示未知 */
    private long[] playSourceCosts;
    /** 播放测速结果：sourceKey -> 读 256KB 耗时(ms)，以及实际字节数 */
    private final HashMap<String, Long> speedCost = new HashMap<>();
    /** 各源当前的总集数（测速时顺手从详情里取），用于播放源卡片显示「共 N 集」 */
    private final HashMap<String, Integer> sourceEpisodes = new HashMap<>();
    /** 从站点列表进入详情页时，自动搜索同片的其他源，让两个入口的播放页功能一致 */
    private String autoSourceTitle = "";
    private int autoSourceSession = 0;
    private boolean autoSourceRunning = false;
    private final HashSet<String> autoSourcePending = new HashSet<>();
    private ExecutorService autoSourceExecutor;
    private final Handler autoSourceHandler = new Handler(Looper.getMainLooper());
    private final HashMap<String, Long> speedBytes = new HashMap<>();
    /** 已测速缓存：key|vodId -> {耗时ms, 时间戳}，10 分钟内不重复测 */
    private static final HashMap<String, long[]> SPEED_CACHE = new HashMap<>();
    /** 同片多源搜索结果缓存：15 分钟内重进同一部片，播放源立即出现 */
    private static final HashMap<String, SourceHit> AUTO_SOURCE_CACHE = new HashMap<>();

    private static final class SourceHit {
        final ArrayList<String> keys = new ArrayList<>();
        final ArrayList<String> ids = new ArrayList<>();
        final ArrayList<String> notes = new ArrayList<>();
        final long time = System.currentTimeMillis();
    }
    private int speedSession = 0;
    private String speedVodId;
    private final HashSet<String> speedWaitingDetail = new HashSet<>();
    private int speedPending = 0;
    private View seriesFlagFocus = null;
    private boolean isReverse;
    /** 需求4：选集布局模式 0=完整(单列) 1=紧凑(固定4列) 2=智能(按屏宽自适应) */
    private int seriesLayoutMode = 2;
    /** 选集分段：每段 30 集，选集网格只显示当前段，避免集数多时整页铺满 */
    private static final int SERIES_SEGMENT_SIZE = 30;
    private int seriesSegment = 0;
    /** 需求2：详情缓存 key */
    private String detailCacheKey;
    /** 排查加载慢：记录详情请求发起时间，用于日志里输出真实耗时 */
    private long detailRequestStart;
    /** 需求2：详情是否已加载完成（用于超时判断） */
    private boolean detailLoaded;
    private static final long DETAIL_TIMEOUT = 20000L;
    private final Handler mTimeoutHandler = new Handler(Looper.getMainLooper());
    /** 需求2：获取剧集列表超时提示，只做页面内提示，不再整页覆盖空态 */
    private final Runnable detailTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!detailLoaded) {
                showLoadFailed("获取剧集列表超时");
            }
        }
    };
    private String preFlag = "";
    private HashMap<String, String> mCheckSources = null;
    BatteryReceiver mBatteryReceiver = new BatteryReceiver();
    //改为view模式无法自动响应返回键操作,onBackPress时手动dismiss
    private BasePopupView mAllSeriesRightDialog;
    private BasePopupView mAllSeriesBottomDialog;
    /**
     * Home键广播,用于触发后台服务
     */
    private BroadcastReceiver mHomeKeyReceiver;
    /**
     * 是否开启后台播放标记,不在广播开启,onPause根据标记开启
     */
    boolean openBackgroundPlay;
    private BroadcastReceiver mRemoteActionReceiver;

    /**
     * 截屏监听
     */
    ScreenShotListenManager screenShotListenManager;

    @Override
    protected void init() {
        initReceiver();
        initView();
        initViewModel();
        initData();
        registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        ImmersionBar.with(this)
                .statusBarColor(R.color.black)
                .navigationBarColor(R.color.white)
                .fitsSystemWindows(true)
                .statusBarDarkFont(false)
                .init();
        toggleScreenShotListen(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        openBackgroundPlay = false;
        playServerSwitch(false);
        mBinding.ivPrivateBrowsing.postDelayed(NotificationUtils::cancelAll, 800);
    }

    private void initView() {
        mBinding.ivPrivateBrowsing.setVisibility(Hawk.get(HawkConfig.PRIVATE_BROWSING, false) ? View.VISIBLE : View.GONE);
        mBinding.ivPrivateBrowsing.setOnClickListener(view -> ToastUtils.showShort("当前为无痕浏览"));
        mBinding.previewPlayerPlace.setVisibility(showPreview ? View.VISIBLE : View.GONE);
        // 修复：数据回来之前不显示预览播放器容器，否则 PlayFragment 里的 Lottie 会在顶部一直转圈，
        // 看起来像"整页加载很慢"；renderDetail 拿到数据后再显示
        mBinding.previewPlayer.setVisibility(View.GONE);
        initDetailBackground();

        mBinding.mGridView.setHasFixedSize(true);
        mBinding.mGridView.setNestedScrollingEnabled(false);
        mBinding.mGridViewFlag.setNestedScrollingEnabled(false);
        seriesAdapter = new SeriesAdapter(true);
        applySeriesLayout();
        mBinding.mGridView.setAdapter(seriesAdapter);
        mBinding.mGridViewFlag.setHasFixedSize(true);
        seriesFlagAdapter = new SeriesFlagAdapter();
        mBinding.mGridViewFlag.setAdapter(seriesFlagAdapter);
        isReverse = false;
        preFlag = "";
        if (showPreview) {
            playFragment = new PlayFragment();
            getSupportFragmentManager().beginTransaction().add(R.id.previewPlayer, playFragment).commit();
            getSupportFragmentManager().beginTransaction().show(playFragment).commitAllowingStateLoss();
        }

        findViewById(R.id.ll_title).setOnClickListener(view -> {
            if (!ensureDetailReady()) return;
            new XPopup.Builder(this)
                    .isViewMode(true)
                    .hasNavigationBar(false)
                    .asCustom(new VideoDetailDialog(this, vodInfo))
                    .show();
        });
        findViewById(R.id.tvDownload).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!ensureDetailReady()) return;
                use1DMDownload();
            }
        });
        mBinding.tvChangeSource.setOnClickListener(v -> quickSourceChange());
        mBinding.tvSort.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onClick(View v) {
                sortSeries();
            }
        });
        mBinding.tvCast.setOnClickListener(v -> {
            if (!ensureDetailReady()) return;
            showCastDialog();
        });
        mBinding.tvCollect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!ensureDetailReady()) return;
                String text = mBinding.tvCollect.getText().toString();
                if ("加入收藏".equals(text)) {
                    RoomDataManger.insertVodCollect(sourceKey, vodInfo);
                    Toast.makeText(DetailActivity.this, "已加入收藏夹", Toast.LENGTH_SHORT).show();
                    mBinding.tvCollect.setText("取消收藏");
                } else {
                    RoomDataManger.deleteVodCollect(sourceKey, vodInfo);
                    Toast.makeText(DetailActivity.this, "已移除收藏夹", Toast.LENGTH_SHORT).show();
                    mBinding.tvCollect.setText("加入收藏");
                }
            }
        });

        seriesFlagAdapter.setOnItemClickListener((adapter, view, position) -> {
            chooseFlag(position);
        });

        seriesAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                // 网格里只显示当前分段的 30 集，需换算成整部剧的绝对集数
                chooseSeries(seriesSegment * SERIES_SEGMENT_SIZE + position, false);
            }
        });

        // 需求4：原"全部"按钮改为"布局"按钮，点击循环切换 完整/紧凑/智能 三种选集布局
        updateSeriesLayoutLabel();
        mBinding.tvAllSeries.setOnClickListener(view -> {
            seriesLayoutMode = (seriesLayoutMode + 1) % 3;
            applySeriesLayout();
        });

        mBinding.tvSite.setOnClickListener(view -> {
            startQuickSearch();
            QuickSearchDialog quickSearchDialog = new QuickSearchDialog(DetailActivity.this);
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, quickSearchData));
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, quickSearchWord));
            quickSearchDialog.show();
            if (pauseRunnable != null && pauseRunnable.size() > 0) {
                searchExecutorService = Executors.newFixedThreadPool(5);
                for (Runnable runnable : pauseRunnable) {
                    searchExecutorService.execute(runnable);
                }
                pauseRunnable.clear();
                pauseRunnable = null;
            }
            quickSearchDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    try {
                        if (searchExecutorService != null) {
                            pauseRunnable = searchExecutorService.shutdownNow();
                            searchExecutorService = null;
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            });
        });
        mBinding.tvChangeLine.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            if (!ensureDetailReady()) return;
            quickLineChange();
        });
        setLoadSir(mBinding.llLayout);
    }

    /**
     * 剧集数据还没回来时，拦截需要 vodInfo 的点击操作。
     * 修复：原来加载中点击"详情 >>"会 new VideoDetailDialog(this, null)，在 onCreate 里读 vodInfo.name 直接闪退。
     */
    private boolean ensureDetailReady() {
        if (vodInfo == null || mVideo == null) {
            ToastUtils.showShort("正在获取剧集列表，请稍候");
            return false;
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (openBackgroundPlay) {
            playServerSwitch(true);
        }
    }

    /**
     * 背景遮罩：顶部半透明黑，向下渐变到页面底色
     */
    private void initDetailBackground() {
        TypedValue value = new TypedValue();
        int pageColor = 0xFF000000;
        if (getTheme().resolveAttribute(R.attr.skin_page, value, true)) {
            pageColor = value.data;
        }
        mBinding.bgScrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x4D000000, pageColor}));
    }

    /**
     * 绑定详情页封面、年份、导演、演员等信息
     */
    private void bindDetailInfo(Movie.Video video) {
        String picUrl = DefaultConfig.checkReplaceProxy(video.pic);
        if (!TextUtils.isEmpty(picUrl)) {
            mBinding.ivDetailBg.setVisibility(View.VISIBLE);
            // 修复：按控件实际尺寸解码 + 居中裁剪，避免模糊背景按原图全尺寸解码/输出造成卡顿
            Picasso.get().load(picUrl)
                    .resize(ScreenUtils.getScreenWidth(), ConvertUtils.dp2px(380))
                    .centerCrop()
                    .transform(new BlurTransformation())
                    .into(mBinding.ivDetailBg);
            Picasso.get().load(picUrl)
                    .placeholder(R.drawable.img_loading_placeholder)
                    .error(R.drawable.img_loading_placeholder)
                    .transform(new RoundTransformation("detail_poster")
                            .override(ConvertUtils.dp2px(104), ConvertUtils.dp2px(146))
                            .roundRadius(ConvertUtils.dp2px(8), RoundTransformation.RoundType.ALL))
                    .into(mBinding.ivPoster);
        }

        StringBuilder meta = new StringBuilder();
        if (!TextUtils.isEmpty(video.area)) {
            meta.append(video.area);
        }
        if (video.year > 0) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(video.year);
        }
        if (!TextUtils.isEmpty(video.type)) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(video.type);
        }
        mBinding.tvMeta.setText(meta.length() == 0 ? "暂无信息" : meta.toString());
        bindDetailText(mBinding.tvDirector, "导演：", video.director);
        bindDetailText(mBinding.tvActor, "演员：", video.actor);
        if (TextUtils.isEmpty(video.note)) {
            mBinding.tvNote.setVisibility(View.GONE);
        } else {
            mBinding.tvNote.setText(video.note);
            mBinding.tvNote.setVisibility(View.VISIBLE);
        }
    }

    private void bindDetailText(TextView view, String prefix, String value) {
        if (TextUtils.isEmpty(value)) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(prefix + value);
            view.setVisibility(View.VISIBLE);
        }
    }

    private void initReceiver() {
        // 注册广播接收器
        if (mHomeKeyReceiver == null) {
            mHomeKeyReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action != null && action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                        openBackgroundPlay = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0) == 1 && playFragment.getPlayer() != null && playFragment.getPlayer().isPlaying();
                    }
                }
            };
            registerReceiver(mHomeKeyReceiver, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        }
    }

    /**
     * 需求4：应用当前选集布局（完整/紧凑/智能）
     * 完整 = 单列铺满；紧凑 = 固定 4 列；智能 = 按屏幕宽度自适应列数
     */
    private void applySeriesLayout() {
        int span;
        switch (seriesLayoutMode) {
            case 0:
                span = 1;
                break;
            case 1:
                span = 4;
                break;
            default:
                span = Math.max(4, ScreenUtils.getScreenWidth() / ConvertUtils.dp2px(110));
                break;
        }
        mBinding.mGridView.setLayoutManager(new GridLayoutManager(this.mContext, span));
        // 间距装饰器依赖列数，切换布局时需要重建
        while (mBinding.mGridView.getItemDecorationCount() > 0) {
            mBinding.mGridView.removeItemDecorationAt(0);
        }
        mBinding.mGridView.addItemDecoration(new GridSpacingItemDecoration(span, ConvertUtils.dp2px(10), true));
        updateSeriesLayoutLabel();
        if (seriesAdapter != null) {
            seriesAdapter.notifyDataSetChanged();
        }
    }

    /** 需求4：更新"布局·xx"按钮文案 */
    private void updateSeriesLayoutLabel() {
        String[] labels = {"布局·完整", "布局·紧凑", "布局·智能"};
        mBinding.tvAllSeries.setText(labels[seriesLayoutMode]);
    }

    /**
     * 排序(倒序)
     */
    public void sortSeries() {
        if (vodInfo != null && vodInfo.seriesMap.size() > 0) {
            vodInfo.reverseSort = !vodInfo.reverseSort;
            isReverse = !isReverse;
            vodInfo.reverse();
            vodInfo.playIndex = (vodInfo.seriesMap.get(vodInfo.playFlag).size() - 1) - vodInfo.playIndex;
//                    insertVod(sourceKey, vodInfo);

            // 需求4：保留正序/倒序切换，并同步按钮文案
            mBinding.tvSort.setText(vodInfo.reverseSort ? "倒序" : "正序");
            // 列表已整体反转，播放集所在分段随之变化
            seriesSegment = Math.max(0, vodInfo.playIndex / SERIES_SEGMENT_SIZE);
            applySeriesData();
            rebuildSeriesJumpBar();
        }
    }

    public void showCastDialog() {
        VodInfo.VodSeries vodSeries = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex);
        new XPopup.Builder(this)
                .maxWidth(ConvertUtils.dp2px(360))
                .asCustom(new CastListDialog(this, new CastVideo(vodSeries.name
                        , TextUtils.isEmpty(playFragment.getFinalUrl()) ? vodSeries.url : playFragment.getFinalUrl())))
                .show();
    }

    public void showAllSeriesDialog() {
        if (fullWindows) {
            mAllSeriesRightDialog = new XPopup.Builder(this)
                    .isViewMode(true)//隐藏导航栏(手势条)在dialog模式下会闪一下,改为view模式,但需处理onBackPress的隐藏,下方同理
                    .hasNavigationBar(false)
                    .popupHeight(ScreenUtils.getScreenHeight())
                    .popupPosition(PopupPosition.Right)
                    .enableDrag(false)//禁用拖拽,内部有横向rv
                    .asCustom(new AllVodSeriesRightDialog(this));
            mAllSeriesRightDialog.show();
        } else {
            mAllSeriesBottomDialog = new XPopup.Builder(this)
                    .isViewMode(true)
                    .hasNavigationBar(false)
                    .maxHeight(ScreenUtils.getScreenHeight() - (ScreenUtils.getScreenHeight() / 4))
                    .asCustom(new AllVodSeriesBottomDialog(this, new ArrayList<>(fullSeries()), (position, text) -> {
                        chooseSeries(position, false);
                    }));
            mAllSeriesBottomDialog.show();
        }
    }

    private void chooseFlag(int position) {
        //新选中的flag
        String newFlag = seriesFlagAdapter.getData().get(position).name;
        if (vodInfo != null && !vodInfo.playFlag.equals(newFlag)) {
            for (int i = 0; i < vodInfo.seriesFlags.size(); i++) {//遍历flag集合
                VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(i);
                if (flag.name.equals(vodInfo.playFlag)) {//取消当前播放的选中状态
                    flag.selected = false;
                    seriesFlagAdapter.notifyItemChanged(i);
                    break;
                }
            }
            //新选中的flag
            VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(position);
            flag.selected = true;
            //清除上一个线路集数的选中状态
            List<VodInfo.VodSeries> currentSeriesList = vodInfo.seriesMap.get(vodInfo.playFlag);
            if (currentSeriesList.size() > vodInfo.playIndex) {//有效集数
                currentSeriesList.get(vodInfo.playIndex).selected = false;
            }
            vodInfo.playFlag = newFlag;
            seriesFlagAdapter.notifyItemChanged(position);
            refreshList();
        }
    }

    private void chooseSeries(int position, boolean reloadWithChangeLine) {
        List<VodInfo.VodSeries> full = fullSeries();
        if (vodInfo != null && position >= 0 && position < full.size()) {
            boolean reload = false;
            for (int j = 0; j < full.size(); j++) {
                full.get(j).selected = false;
            }
            //解决倒叙不刷新
            if (vodInfo.playIndex != position) {
                full.get(position).selected = true;
                vodInfo.playIndex = position;

                reload = true;
            }
            //解决当前集不刷新的BUG
            if (!preFlag.isEmpty() && !vodInfo.playFlag.equals(preFlag)) {
                reload = true;
            }

            full.get(vodInfo.playIndex).selected = true;

            // 选中的集不在当前分段时，自动切到它所在的分段
            int seg = vodInfo.playIndex / SERIES_SEGMENT_SIZE;
            if (seg != seriesSegment) {
                seriesSegment = seg;
                applySeriesData();
                rebuildSeriesJumpBar();
            } else {
                seriesAdapter.notifyDataSetChanged();
            }

            //选集全屏 想选集不全屏的注释下面一行
            if (!showPreview || reload || reloadWithChangeLine) {
                jumpToPlay();
            }
        }
    }

    /** 当前线路的全部集数（真实数据源，不受分段显示影响） */
    private List<VodInfo.VodSeries> fullSeries() {
        if (vodInfo == null || vodInfo.seriesMap == null) {
            return new ArrayList<>();
        }
        List<VodInfo.VodSeries> list = vodInfo.seriesMap.get(vodInfo.playFlag);
        return list == null ? new ArrayList<>() : list;
    }

    /** 把当前分段的集数装进选集网格 */
    private void applySeriesData() {
        List<VodInfo.VodSeries> full = fullSeries();
        int total = full.size();
        if (total == 0) {
            seriesAdapter.setNewData(new ArrayList<>());
            return;
        }
        int start = seriesSegment * SERIES_SEGMENT_SIZE;
        if (start >= total) {
            seriesSegment = 0;
            start = 0;
        }
        int end = Math.min(start + SERIES_SEGMENT_SIZE, total);
        seriesAdapter.setNewData(new ArrayList<>(full.subList(start, end)));
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    private List<Runnable> pauseRunnable = null;

    private void jumpToPlay() {
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            preFlag = vodInfo.playFlag;
            //更新播放地址
            Bundle bundle = new Bundle();
            //保存历史
            insertVod(sourceKey, vodInfo);
            bundle.putString("sourceKey", sourceKey);
//            bundle.putSerializable("VodInfo", vodInfo);
            App.getInstance().setVodInfo(vodInfo);
            if (previewVodInfo == null) {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(bos);
                    oos.writeObject(vodInfo);
                    oos.flush();
                    oos.close();
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
                    previewVodInfo = (VodInfo) ois.readObject();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (previewVodInfo != null) {
                previewVodInfo.playerCfg = vodInfo.playerCfg;
                previewVodInfo.playFlag = vodInfo.playFlag;
                previewVodInfo.playIndex = vodInfo.playIndex;
                previewVodInfo.seriesMap = vodInfo.seriesMap;
//                    bundle.putSerializable("VodInfo", previewVodInfo);
                App.getInstance().setVodInfo(previewVodInfo);
            }
            playFragment.setData(bundle);

            //定位选集
            mBinding.mGridView.scrollToPosition(vodInfo.playIndex);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    void refreshList() {
        int seriesSize = vodInfo.seriesMap.get(vodInfo.playFlag).size();
        if (seriesSize > 0 && seriesSize <= vodInfo.playIndex) {//当前集数大于新选线路的总集数,设置为最后一集
            vodInfo.playIndex = seriesSize - 1;
        }

        if (vodInfo.seriesMap.get(vodInfo.playFlag) != null) {
            boolean canSelect = true;
            for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                if (vodInfo.seriesMap.get(vodInfo.playFlag).get(j).selected) {
                    canSelect = false;
                    break;
                }
            }
            if (canSelect)
                vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).selected = true;
        }
        // 默认定位到当前播放集所在的分段
        seriesSegment = Math.max(0, vodInfo.playIndex / SERIES_SEGMENT_SIZE);
        applySeriesData();
        rebuildSeriesJumpBar();
    }

    /** 选集分段条：每 30 集一段，点击切换下方网格显示的分段 */
    private void rebuildSeriesJumpBar() {
        if (vodInfo == null || vodInfo.seriesMap == null) return;
        java.util.List<VodInfo.VodSeries> list = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (list == null) list = new java.util.ArrayList<>();
        int total = list.size();
        LinearLayout ll = mBinding.llSeriesJump;
        ll.removeAllViews();
        // 分段：每 30 集一段，最后一段不足 30 也独立一段；集数 ≤ 30 就不显示分段条（意义不大）
        if (total <= 30) {
            mBinding.hsvSeriesJump.setVisibility(View.GONE);
            return;
        }
        mBinding.hsvSeriesJump.setVisibility(View.VISIBLE);
        int step = SERIES_SEGMENT_SIZE;
        int textColor = resolveSkinColor(R.attr.skin_text_secondary);
        int selectedColor = resolveSkinColor(R.attr.skin_text_primary);
        for (int i = 0; i * step < total; i++) {
            int start = i * step;
            int end = Math.min(start + step, total);
            // 分段直接对应网格里显示的那 30 集（列表顺序已包含正/倒序）
            int startIndex = start;
            int lastIndex = end - 1;
            String label;
            try {
                String n1 = list.get(startIndex).name.replaceAll("\\D+", "");
                String n2 = list.get(lastIndex).name.replaceAll("\\D+", "");
                if (!n1.isEmpty() && !n2.isEmpty()) {
                    int a = Integer.parseInt(n1);
                    int b = Integer.parseInt(n2);
                    label = Math.min(a, b) + "-" + Math.max(a, b);
                } else {
                    label = (start + 1) + "-" + end;
                }
            } catch (Throwable th) {
                label = (start + 1) + "-" + end;
            }
            final int seg = i;
            boolean active = (i == seriesSegment);
            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTextColor(active ? selectedColor : textColor);
            int padX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
            int padY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
            tv.setPadding(padX, padY, padX, padY);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(padY * 1.2f);
            if (active) {
                bg.setColor(0x33FFFFFF);
                bg.setStroke(1, 0xFF3B7BFF);
            } else {
                bg.setStroke(1, 0x33888888);
            }
            tv.setBackground(bg);
            int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(margin / 2, 0, margin / 2, 0);
            tv.setLayoutParams(lp);
            tv.setOnClickListener(v -> selectSeriesSegment(seg));
            ll.addView(tv);
        }
    }

    /** 搜索结果里的同剧多源：在播放页展示源名称、集数/清晰度和实测速度，点击直接切换 */
    private void rebuildPlaySources() {
        boolean show = playSourceKeys.size() > 1;
        mBinding.llSourceSection.setVisibility(show ? View.VISIBLE : View.GONE);
        mBinding.llPlaySources.removeAllViews();
        if (!show) return;
        for (int i = 0; i < playSourceKeys.size(); i++) {
            mBinding.llPlaySources.addView(buildPlaySourceChip(i));
        }
    }

    private View buildPlaySourceChip(int index) {
        String key = playSourceKeys.get(index);
        SourceBean bean = ApiConfig.get().getSource(key);
        String name = bean != null && !TextUtils.isEmpty(bean.getName()) ? bean.getName() : key;
        // 站点后面的备注/集数/清晰度全部放到第二行；第一行只留站点名
        int eps = episodeCountFor(key);
        String note = index < playSourceNotes.size() ? playSourceNotes.get(index) : "";
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(note)) parts.add(note);
        if (eps > 0) parts.add("共 " + eps + " 集");
        if (parts.isEmpty()) parts.add("高清");
        Long measured = speedCost.get(key);
        if (measured != null && measured > 0) {
            long bytes = speedBytes.containsKey(key) ? speedBytes.get(key) : 0;
            long kbps = bytes > 0 ? bytes * 1000 / measured / 1024 : 0;
            parts.add(kbps > 0 ? kbps + "KB/s" : measured + "ms");
        }
        String info = TextUtils.join(" · ", parts);
        boolean active = key.equals(sourceKey);

        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        int padX = ConvertUtils.dp2px(12);
        int padY = ConvertUtils.dp2px(8);
        chip.setPadding(padX, padY, padX, padY);

        TextView nameView = new TextView(this);
        String fastest = fastestSourceKey();
        if (key.equals(fastest)) {
            SpannableString ss = new SpannableString(name + "  最快");
            ss.setSpan(new ForegroundColorSpan(resolveSkinColor(R.attr.skin_accent)),
                    name.length() + 2, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            nameView.setText(ss);
        } else {
            nameView.setText(name);
        }
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        nameView.setTextColor(resolveSkinColor(active ? R.attr.skin_text_primary : R.attr.skin_text_secondary));
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        chip.addView(nameView);

        TextView addressView = new TextView(this);
        addressView.setText(info);
        addressView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        addressView.setTextColor(resolveSkinColor(R.attr.skin_text_secondary));
        addressView.setSingleLine(true);
        addressView.setEllipsize(TextUtils.TruncateAt.END);
        addressView.setMaxWidth(ConvertUtils.dp2px(200));
        chip.addView(addressView);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ConvertUtils.dp2px(10));
        if (active) {
            bg.setColor(0x223B7BFF);
            bg.setStroke(1, 0xFF3B7BFF);
        } else {
            bg.setStroke(1, 0x33888888);
        }
        chip.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = ConvertUtils.dp2px(10);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> switchPlaySource(index));
        return chip;
    }

    /** 快捷换源：切到列表里的下一个源 */
    private void quickSourceChange() {
        if (playSourceKeys.size() <= 1) return;
        int current = playSourceKeys.indexOf(sourceKey);
        int next = current < 0 ? 0 : (current + 1) % playSourceKeys.size();
        switchPlaySource(next);
    }

    /** 在播放页内切换播放源：重新拉取该源的线路和选集 */
    private void switchPlaySource(int index) {
        if (index < 0 || index >= playSourceKeys.size() || index >= playSourceIds.size()) return;
        String key = playSourceKeys.get(index);
        String id = playSourceIds.get(index);
        if (TextUtils.isEmpty(id)) return;
        if (key.equals(sourceKey) && id.equals(vodId)) return;
        SourceBean bean = ApiConfig.get().getSource(key);
        String name = bean != null && !TextUtils.isEmpty(bean.getName()) ? bean.getName() : key;
        ToastUtils.showShort("切换到 " + name);
        loadDetail(id, key);
        rebuildPlaySources();
    }

    /** 切换选集分段：网格只显示这一段，并且把页面滚回选集顶部 */
    private void selectSeriesSegment(int seg) {
        if (seg == seriesSegment) return;
        seriesSegment = seg;
        applySeriesData();
        rebuildSeriesJumpBar();
        scrollGridToTop();
    }

    /** 把外层 ScrollView 滚到选集网格顶部，避免切段后还停在下方的空白处 */
    private void scrollGridToTop() {
        android.widget.ScrollView scroll = null;
        android.view.ViewParent p = mBinding.mGridView.getParent();
        while (p != null) {
            if (p instanceof android.widget.ScrollView) {
                scroll = (android.widget.ScrollView) p;
                break;
            }
            p = p.getParent();
        }
        if (scroll == null) return;
        int[] g = new int[2];
        int[] s = new int[2];
        mBinding.mGridView.getLocationOnScreen(g);
        scroll.getLocationOnScreen(s);
        int gridTop = g[1] - s[1] + scroll.getScrollY();
        scroll.smoothScrollTo(0, Math.max(0, gridTop - 60));
    }

    /** 读取当前主题 skin_* 属性颜色，取不到时退回半透明灰 */
    private int resolveSkinColor(int attr) {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(this, tv.resourceId);
            }
            return tv.data;
        }
        return 0x88FFFFFF;
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.detailResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                // 需求2：收到结果，取消超时保护
                detailLoaded = true;
                LogUtils.i("详情加载耗时：" + (System.currentTimeMillis() - detailRequestStart) + "ms");
                mTimeoutHandler.removeCallbacks(detailTimeoutRunnable);
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null
                        && absXml.movie.videoList.size() > 0) {
                    // 在播放页里切源时，上一次源的迟到结果直接丢弃
                    if (!TextUtils.isEmpty(absXml.sourceKey) && !absXml.sourceKey.equals(sourceKey)) {
                        return;
                    }
                    // 剧集列表写入内存缓存，短时间内重复打开优先命中
                    if (detailCacheKey != null) {
                        PlayCache.putDetail(detailCacheKey, absXml);
                    }
                    mBinding.tvLoadTip.setVisibility(View.GONE);
                    mBinding.tvLoadTip.setOnClickListener(null);
                    renderDetail(absXml);
                } else {
                    // 请求失败/无数据：页面保持可见，只在页面内提示并支持点击重试
                    showLoadFailed("获取剧集列表失败");
                }
            }
        });
        // 说明：SourceViewModel 的搜索结果是走 EventBus(TYPE_SEARCH_RESULT) 分发的，
        // 不走 LiveData，回包统一在 refresh(RefreshEvent) 里处理。
        // 播放测速：其他源的详情回来后，解析播放地址并实测加载速度
        sourceViewModel.speedTestDetail.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                if (absXml == null || TextUtils.isEmpty(absXml.sourceKey)) return;
                final String key = absXml.sourceKey;
                if (!speedWaitingDetail.remove(key)) return;
                final int session = speedSession;
                int eps = episodeCountOf(absXml);
                if (eps > 0) {
                    sourceEpisodes.put(key, eps);
                    rebuildPlaySources();
                }
                speedPending++;
                PlaySpeedMeter.measure(key, absXml, currentSeriesIndex(),
                        vodInfo == null ? null : vodInfo.playFlag,
                        new PlaySpeedMeter.Callback() {
                            @Override
                            public void onResult(String k, long scoreMs, long bytes) {
                                if (session != speedSession) return;
                                speedCost.put(k, scoreMs);
                                speedBytes.put(k, bytes);
                                speedPending--;
                                if (speedPending <= 0 && speedWaitingDetail.isEmpty()) {
                                    applySpeedOrder();
                                }
                            }
                        });
            }
        });
    }

    /** 测速时用当前正在看的集数，找不到就用第一集 */
    private int currentSeriesIndex() {
        return vodInfo == null ? 0 : Math.max(0, vodInfo.playIndex);
    }

    /** 取某源的总集数：优先测速时拿到的详情，其次当前源的选集列表 */
    private int episodeCountFor(String key) {
        Integer cached = sourceEpisodes.get(key);
        if (cached != null && cached > 0) return cached;
        if (key.equals(sourceKey) && vodInfo != null && vodInfo.seriesMap != null) {
            List<VodInfo.VodSeries> list = vodInfo.seriesMap.get(vodInfo.playFlag);
            if (list != null) return list.size();
        }
        return 0;
    }

    /** 当前列表里实测最快的源，没测出来返回 null；跳过音乐/短剧等不匹配的源 */
    private String fastestSourceKey() {
        String fastest = null;
        long best = Long.MAX_VALUE;
        for (String k : playSourceKeys) {
            if (isSuspectSource(k)) continue;
            Long c = speedCost.get(k);
            if (c != null && c > 0 && c < best) {
                best = c;
                fastest = k;
            }
        }
        return fastest;
    }

    /**
     * 判断源是否明显不是这部剧：
     * 1) 站点名带音乐/短剧/听书等关键词；
     * 2) 当前是剧集（≥2集），该源却只有 1 集。
     */
    private boolean isSuspectSource(String key) {
        SourceBean bean = ApiConfig.get().getSource(key);
        String name = bean != null && !TextUtils.isEmpty(bean.getName()) ? bean.getName() : key;
        String[] badWords = {"音乐", "短剧", "听书", "有声", "小说", "漫画", "歌曲", "评书", "MV", "DJ"};
        for (String word : badWords) {
            if (name.contains(word)) return true;
        }
        int currentEps = episodeCountFor(sourceKey);
        int eps = episodeCountFor(key);
        return currentEps >= 2 && eps == 1;
    }

    /** 从详情里取各线路中最大的集数 */
    private int episodeCountOf(AbsXml absXml) {
        try {
            if (absXml.movie == null || absXml.movie.videoList == null || absXml.movie.videoList.isEmpty()) return 0;
            Movie.Video video = absXml.movie.videoList.get(0);
            if (video.urlBean == null || video.urlBean.infoList == null) return 0;
            int max = 0;
            for (Movie.Video.UrlBean.UrlInfo info : video.urlBean.infoList) {
                if (info.beanList != null) max = Math.max(max, info.beanList.size());
            }
            return max;
        } catch (Throwable ignore) {
            return 0;
        }
    }

    /** 两个入口共用：把源加入播放源列表（去重，保持 key/id/note 三个列表对齐） */
    private void addPlaySource(String key, String id, String note) {
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(id) || playSourceKeys.contains(key)) return;
        playSourceKeys.add(key);
        playSourceIds.add(id);
        playSourceNotes.add(note == null ? "" : note);
    }

    /** 聚合同片的其他源：搜索页入口直接用现成结果，站点入口在后台补搜，15 秒兜底 */
    private void startAutoSourceSearch(String title) {
        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(sourceKey) || TextUtils.isEmpty(vodId)) return;
        if (autoSourceRunning || !TextUtils.isEmpty(autoSourceTitle)) return;
        autoSourceTitle = title;
        // 已经是从搜索页带进来的多源，无需再搜
        if (playSourceKeys.size() > 1) return;
        initCheckedSourcesForSearch();
        // 当前源也放进列表，保证高亮和快捷换源可用
        addPlaySource(sourceKey, vodId, "");
        // 15 分钟内搜过同一部片，直接复用结果，秒出播放源
        SourceHit hit = AUTO_SOURCE_CACHE.get(title);
        if (hit != null && System.currentTimeMillis() - hit.time < 15 * 60 * 1000L) {
            boolean changed = false;
            for (int i = 0; i < hit.keys.size(); i++) {
                if (!playSourceKeys.contains(hit.keys.get(i))) {
                    addPlaySource(hit.keys.get(i), hit.ids.get(i), hit.notes.get(i));
                    changed = true;
                }
            }
            if (changed) {
                rebuildPlaySources();
                maybeStartSpeedTest();
            }
            return;
        }
        List<String> keys = new ArrayList<>();
        for (SourceBean bean : ApiConfig.get().getSourceBeanList()) {
            if (bean == null || !bean.isSearchable()) continue;
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) continue;
            if (bean.getKey().equals(sourceKey)) continue;
            keys.add(bean.getKey());
        }
        if (keys.isEmpty()) return;
        autoSourceSession++;
        final int session = autoSourceSession;
        autoSourceRunning = true;
        autoSourcePending.clear();
        autoSourcePending.addAll(keys);
        if (autoSourceExecutor == null) autoSourceExecutor = Executors.newFixedThreadPool(10);
        final String searchTitle = title;
        for (final String key : keys) {
            autoSourceExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        sourceViewModel.getSearch(key, searchTitle, "auto_src_" + session);
                    } catch (Throwable ignore) {
                    }
                }
            });
        }
        autoSourceHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finishAutoSourceSearch(session);
            }
        }, 15000);
    }

    /** 自动聚合同片多源的搜索结果（EventBus 分发） */
    private void onAutoSearchResult(AbsXml absXml) {
        if (absXml == null || !autoSourceRunning) return;
        if (absXml.searchToken == null || !absXml.searchToken.equals("auto_src_" + autoSourceSession)) return;
        String key = absXml.sourceKey;
        if (key == null || !autoSourcePending.remove(key)) return;
        Movie.Video match = findSameMovie(absXml, autoSourceTitle);
        if (match != null && !playSourceKeys.contains(key)) {
            addPlaySource(key, match.id, match.note);
            rebuildPlaySources();
            // 找到一个就立刻测一个，不用等全部搜完
            maybeStartSpeedTest();
        }
        if (autoSourcePending.isEmpty()) {
            finishAutoSourceSearch(autoSourceSession);
        }
    }

    private void finishAutoSourceSearch(int session) {
        if (session != autoSourceSession || !autoSourceRunning) return;
        autoSourceRunning = false;
        autoSourcePending.clear();
        rebuildPlaySources();
        if (playSourceKeys.size() > 1) {
            // 缓存本次聚合结果，15 分钟内重进同一部片不再重复搜索
            SourceHit hit = new SourceHit();
            hit.keys.addAll(playSourceKeys);
            hit.ids.addAll(playSourceIds);
            hit.notes.addAll(playSourceNotes);
            if (AUTO_SOURCE_CACHE.size() > 20) AUTO_SOURCE_CACHE.clear();
            AUTO_SOURCE_CACHE.put(autoSourceTitle, hit);
            maybeStartSpeedTest();
        }
    }

    /** 在搜索结果里找同名影片 */
    private Movie.Video findSameMovie(AbsXml absXml, String title) {
        if (absXml.movie == null || absXml.movie.videoList == null) return null;
        String target = normalizeTitle(title);
        if (target.isEmpty()) return null;
        for (Movie.Video video : absXml.movie.videoList) {
            if (video == null || TextUtils.isEmpty(video.id)) continue;
            String name = normalizeTitle(video.name);
            if (name.isEmpty()) continue;
            if (name.equals(target) || name.contains(target) || target.contains(name)) return video;
        }
        return null;
    }

    private String normalizeTitle(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /** 打开播放页后，后台并行测各源的真实加载速度；源找到多少就先测多少，不必等全部搜完 */
    private void maybeStartSpeedTest() {
        if (playSourceKeys.size() <= 1 || vodInfo == null || TextUtils.isEmpty(vodId)) return;
        if (!vodId.equals(speedVodId)) {
            speedVodId = vodId;
            speedSession++;
            final int session = speedSession;
            speedCost.clear();
            speedBytes.clear();
            sourceEpisodes.clear();
            speedWaitingDetail.clear();
            speedPending = 0;
            long now = System.currentTimeMillis();
            for (String key : playSourceKeys) {
                long[] c = SPEED_CACHE.get(key + "|" + vodId);
                if (c != null && now - c[1] < 10 * 60 * 1000L) {
                    speedCost.put(key, c[0]);
                }
            }
            mTimeoutHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (session != speedSession) return;
                    speedWaitingDetail.clear();
                    applySpeedOrder();
                }
            }, 15000);
        }
        boolean requested = false;
        for (int i = 0; i < playSourceKeys.size() && i < playSourceIds.size(); i++) {
            String key = playSourceKeys.get(i);
            if (speedCost.containsKey(key) || speedWaitingDetail.contains(key)) continue;
            speedWaitingDetail.add(key);
            sourceViewModel.getDetailForSpeedTest(key, playSourceIds.get(i));
            requested = true;
        }
        if (!requested && speedWaitingDetail.isEmpty()) {
            applySpeedOrder();
        }
    }

    /** 按实测加载速度重排播放源：快的在前，测不出速度的排最后 */
    private void applySpeedOrder() {
        if (playSourceKeys.size() <= 1) return;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < playSourceKeys.size(); i++) order.add(i);
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                // 当前正在播的源固定排第一，其余按实测速度
                boolean aActive = sourceKey != null && sourceKey.equals(playSourceKeys.get(a));
                boolean bActive = sourceKey != null && sourceKey.equals(playSourceKeys.get(b));
                if (aActive != bActive) return aActive ? -1 : 1;
                // 音乐/短剧/听书等明显不匹配的源，无论多快都排到最后
                boolean aBad = isSuspectSource(playSourceKeys.get(a));
                boolean bBad = isSuspectSource(playSourceKeys.get(b));
                if (aBad != bBad) return aBad ? 1 : -1;
                long ca = scoreOf(playSourceKeys.get(a));
                long cb = scoreOf(playSourceKeys.get(b));
                if (ca <= 0 && cb <= 0) return 0;
                if (ca <= 0) return 1;
                if (cb <= 0) return -1;
                return Long.compare(ca, cb);
            }
        });
        ArrayList<String> keys = new ArrayList<>();
        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> notes = new ArrayList<>();
        long[] costs = playSourceCosts == null ? null : new long[playSourceCosts.length];
        for (int i = 0; i < order.size(); i++) {
            int old = order.get(i);
            keys.add(playSourceKeys.get(old));
            ids.add(playSourceIds.get(old));
            if (old < playSourceNotes.size()) notes.add(playSourceNotes.get(old));
            if (costs != null && playSourceCosts != null && old < playSourceCosts.length) {
                costs[i] = playSourceCosts[old];
            }
        }
        playSourceKeys.clear();
        playSourceKeys.addAll(keys);
        playSourceIds.clear();
        playSourceIds.addAll(ids);
        playSourceNotes.clear();
        playSourceNotes.addAll(notes);
        playSourceCosts = costs;
        long now = System.currentTimeMillis();
        for (String key : playSourceKeys) {
            Long cost = speedCost.get(key);
            if (cost != null && cost > 0) {
                SPEED_CACHE.put(key + "|" + vodId, new long[]{cost, now});
            }
        }
        rebuildPlaySources();
    }

    private long scoreOf(String key) {
        Long value = speedCost.get(key);
        return value == null ? -1 : value;
    }

    /**
     * 需求2：渲染详情数据（网络结果与本地缓存命中共用同一套渲染逻辑）
     */
    private void renderDetail(AbsXml absXml) {
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                    showSuccess();
                    // 数据已到，恢复预览播放区（加载期间是隐藏的，避免黑屏转圈）
                    mBinding.previewPlayerPlace.setVisibility(showPreview ? View.VISIBLE : View.GONE);
                    mVideo = absXml.movie.videoList.get(0);
                    vodInfo = new VodInfo();
                    vodInfo.setVideo(mVideo);
                    vodInfo.sourceKey = mVideo.sourceKey;

                    mBinding.tvName.setText(TextUtils.isEmpty(mVideo.name) ? "暂无信息" : mVideo.name);
                    SourceBean detailSource = ApiConfig.get().getSource(mVideo.sourceKey);
                    String srcName = detailSource == null ? null : detailSource.getName();
                    mBinding.tvSite.setText("来源：" + (TextUtils.isEmpty(srcName) ? "未知" : srcName));
                    bindDetailInfo(mVideo);
                    rebuildPlaySources();

                    if (vodInfo.seriesMap != null && vodInfo.seriesMap.size() > 0) {//线路
                        mBinding.mGridViewFlag.setVisibility(View.VISIBLE);
                        mBinding.mGridView.setVisibility(View.VISIBLE);
                        mBinding.mEmptyPlaylist.setVisibility(View.GONE);

                        VodInfo vodInfoRecord = RoomDataManger.getVodInfo(sourceKey, vodId);
                        // 读取历史记录
                        if (vodInfoRecord != null) {
                            vodInfo.playIndex = Math.max(vodInfoRecord.playIndex, 0);
                            vodInfo.playFlag = vodInfoRecord.playFlag;
                            vodInfo.playerCfg = vodInfoRecord.playerCfg;
                            vodInfo.reverseSort = vodInfoRecord.reverseSort;
                        } else {
                            vodInfo.playIndex = 0;
                            vodInfo.playFlag = null;
                            vodInfo.playerCfg = "";
                            vodInfo.reverseSort = false;
                        }

                        if (vodInfo.reverseSort) {
                            vodInfo.reverse();
                        }

                        if (vodInfo.playFlag == null || !vodInfo.seriesMap.containsKey(vodInfo.playFlag))
                            vodInfo.playFlag = (String) vodInfo.seriesMap.keySet().toArray()[0];

                        int flagScrollTo = 0;
                        for (int j = 0; j < vodInfo.seriesFlags.size(); j++) {
                            VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(j);
                            if (flag.name.equals(vodInfo.playFlag)) {
                                flagScrollTo = j;
                                flag.selected = true;
                            } else
                                flag.selected = false;
                        }
//                        setTextShow(tvPlayUrl, "播放地址：", vodInfo.seriesMap.get(vodInfo.playFlag).get(0).url);
                        //设置线路数据
                        seriesFlagAdapter.setNewData(vodInfo.seriesFlags);
                        mBinding.mGridViewFlag.scrollToPosition(flagScrollTo);

                        refreshList();
                        // 打开播放页后后台测速，测完按真实加载速度重排播放源
                        maybeStartSpeedTest();
                        // 非搜索入口进入时，自动补齐其他站点，让播放源和搜索入口一致
                        startAutoSourceSearch(vodInfo.name);
                        // 需求4：同步正序/倒序按钮文案
                        mBinding.tvSort.setText(vodInfo.reverseSort ? "倒序" : "正序");
                        if (showPreview) {
                            // 数据已到：先显示预览播放器容器，再起播
                            mBinding.previewPlayer.setVisibility(View.VISIBLE);
                            jumpToPlay();
                            toggleSubtitleTextSize();
                        }
                        // startQuickSearch();
                    } else {//空布局
                        mBinding.mGridViewFlag.setVisibility(View.GONE);
                        mBinding.mGridView.setVisibility(View.GONE);
                        mBinding.mEmptyPlaylist.setVisibility(View.VISIBLE);
                    }
                } else {
                    showEmpty();
                    mBinding.previewPlayer.setVisibility(View.GONE);
                }
    }

    private String getHtml(String label, String content) {
        if (content == null) {
            content = "";
        }
        return label + "<font color=\"#FFFFFF\">" + content + "</font>";
    }

    private void initData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            ArrayList<String> keys = bundle.getStringArrayList("searchSourceKeys");
            ArrayList<String> ids = bundle.getStringArrayList("searchSourceIds");
            ArrayList<String> notes = bundle.getStringArrayList("searchSourceNotes");
            playSourceCosts = bundle.getLongArray("searchSourceCosts");
            if (keys != null && ids != null && keys.size() == ids.size()) {
                playSourceKeys.clear();
                playSourceIds.clear();
                playSourceNotes.clear();
                for (int i = 0; i < keys.size(); i++) {
                    addPlaySource(keys.get(i), ids.get(i),
                            notes != null && i < notes.size() ? notes.get(i) : "");
                }
            }
            String title = bundle.getString("title", "");
            loadDetail(bundle.getString("id", null), bundle.getString("sourceKey", ""));
            rebuildPlaySources();
            // 详情请求与多源聚合并行：先用列表页带过来的剧名开搜，播放源更快出现
            startAutoSourceSearch(title);
        }
    }

    private void loadDetail(String vid, String key) {
        if (vid == null) return;
        vodId = vid;
        sourceKey = key;

        // ===== 恢复旧版时序：先发请求，页面绝不等待网络 =====
        detailCacheKey = PlayCache.detailKey(sourceKey, vodId);
        // 纯内存缓存读取（微秒级），命中则不重复请求；未命中立刻发起请求
        AbsXml cached = PlayCache.getDetail(detailCacheKey);
        if (cached == null) {
            detailRequestStart = System.currentTimeMillis();
            sourceViewModel.getDetail(sourceKey, vodId);
        }

        // ===== 页面先渲染出来（封面 / 标题 / 线路 / 选集区域先占位）=====
        showSuccess();
        showDetailSkeleton();
        // 历史/收藏入口带了标题与封面：先渲染头部，别让用户对着"加载中…"干等网络
        Intent intent = getIntent();
        if (intent != null) {
            applyPreviewHeader(intent.getStringExtra("title"), intent.getStringExtra("pic"));
        }

        if (cached != null && cached.movie != null) {
            // 内存缓存命中：直接把内容填进已渲染好的页面
            detailLoaded = true;
            mBinding.tvLoadTip.setVisibility(View.GONE);
            mBinding.tvLoadTip.setOnClickListener(null);
            renderDetail(cached);
        } else {
            // 缓存未命中：页面已在后台请求，这里只更新页面内的加载状态 + 超时保护
            detailLoaded = false;
            mBinding.tvLoadTip.setOnClickListener(null);
            mBinding.tvLoadTip.setText("正在获取剧集列表…");
            mBinding.tvLoadTip.setVisibility(View.VISIBLE);
            mTimeoutHandler.removeCallbacks(detailTimeoutRunnable);
            mTimeoutHandler.postDelayed(detailTimeoutRunnable, DETAIL_TIMEOUT);
        }

        // 收藏状态放到请求之后查询，避免主线程数据库查询阻塞首屏请求
        boolean isVodCollect = RoomDataManger.isVodCollect(sourceKey, vodId);
        mBinding.tvCollect.setText(isVodCollect ? "取消收藏" : "加入收藏");
    }

    /**
     * 需求2：加载失败/超时——只在页面内提示并可点击重试，
     * 不再整页替换成"暂无数据"，也不再让播放器区域一直转圈。
     */
    private void showLoadFailed(String msg) {
        detailLoaded = true;
        mTimeoutHandler.removeCallbacks(detailTimeoutRunnable);
        // 加载失败时不显示预览播放区（避免黑屏 + 无限转圈）
        mBinding.previewPlayerPlace.setVisibility(View.GONE);
        mBinding.previewPlayer.setVisibility(View.GONE);
        mBinding.tvLoadTip.setOnClickListener(v -> retryLoadDetail());
        // 显示具体失败原因，便于判断是源不存在、网络错误、爬虫异常还是解析失败
        String reason = SourceViewModel.lastDetailError;
        mBinding.tvLoadTip.setText(msg + (TextUtils.isEmpty(reason) ? "" : "（" + reason + "）") + "，点击重试");
        mBinding.tvLoadTip.setVisibility(View.VISIBLE);
    }

    /** 需求2：点击页面内提示重试，清掉缓存后重新加载 */
    private void retryLoadDetail() {
        if (TextUtils.isEmpty(vodId)) return;
        PlayCache.remove(PlayCache.detailKey(sourceKey, vodId));
        loadDetail(vodId, sourceKey);
    }

    /** 需求2：详情未返回前的骨架占位（页面结构先出来，不阻塞渲染） */
    private void showDetailSkeleton() {
        mBinding.tvName.setText("加载中…");
        mBinding.tvMeta.setText("");
        mBinding.tvDirector.setVisibility(View.GONE);
        mBinding.tvActor.setVisibility(View.GONE);
        mBinding.tvNote.setVisibility(View.GONE);
        mBinding.tvSite.setText("来源：");
        mBinding.mGridViewFlag.setVisibility(View.GONE);
        mBinding.mGridView.setVisibility(View.VISIBLE);
        mBinding.mEmptyPlaylist.setVisibility(View.GONE);
        // 加载完成前不显示预览播放区，避免顶部黑屏 + 一直转圈
        mBinding.previewPlayerPlace.setVisibility(View.GONE);
        mBinding.previewPlayer.setVisibility(View.GONE);
    }

    /**
     * 历史 / 收藏入口会带上标题和封面：网络数据回来前先把头部渲染出来，
     * 否则从记录页进来要一直盯着"加载中…"，观感就是"打开很慢"。
     */
    private void applyPreviewHeader(String title, String pic) {
        if (!TextUtils.isEmpty(title)) {
            mBinding.tvName.setText(title);
        }
        if (TextUtils.isEmpty(pic)) {
            return;
        }
        String picUrl = DefaultConfig.checkReplaceProxy(pic);
        if (TextUtils.isEmpty(picUrl)) {
            return;
        }
        mBinding.ivDetailBg.setVisibility(View.VISIBLE);
        Picasso.get().load(picUrl)
                .resize(ScreenUtils.getScreenWidth(), ConvertUtils.dp2px(380))
                .centerCrop()
                .transform(new BlurTransformation())
                .into(mBinding.ivDetailBg);
        Picasso.get().load(picUrl)
                .placeholder(R.drawable.img_loading_placeholder)
                .error(R.drawable.img_loading_placeholder)
                .transform(new RoundTransformation("detail_poster")
                        .override(ConvertUtils.dp2px(104), ConvertUtils.dp2px(146))
                        .roundRadius(ConvertUtils.dp2px(8), RoundTransformation.RoundType.ALL))
                .into(mBinding.ivPoster);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_REFRESH) {
            if (event.obj != null) {
                if (event.obj instanceof Integer) {
                    int index = (int) event.obj;
                    List<VodInfo.VodSeries> full = fullSeries();
                    for (int j = 0; j < full.size(); j++) {
                        full.get(j).selected = false;
                    }
                    if (index >= 0 && index < full.size()) {
                        full.get(index).selected = true;
                    }
                    vodInfo.playIndex = index;
                    // 切到当前播放集所在分段并刷新网格
                    seriesSegment = Math.max(0, index / SERIES_SEGMENT_SIZE);
                    applySeriesData();
                    rebuildSeriesJumpBar();
                    //保存历史
                    insertVod(sourceKey, vodInfo);
                } else if (event.obj instanceof JSONObject) {
                    vodInfo.playerCfg = ((JSONObject) event.obj).toString();
                    //保存历史
                    insertVod(sourceKey, vodInfo);
                }

            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_SELECT) {
            if (event.obj != null) {
                Movie.Video video = (Movie.Video) event.obj;
                loadDetail(video.id, video.sourceKey);
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD_CHANGE) {
            if (event.obj != null) {
                String word = (String) event.obj;
                switchSearchWord(word);
            }
        } else if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            onAutoSearchResult(event.obj == null ? null : (AbsXml) event.obj);
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private String searchTitle = "";
    private boolean hadQuickStart = false;
    private final List<Movie.Video> quickSearchData = new ArrayList<>();
    private final List<String> quickSearchWord = new ArrayList<>();
    private ExecutorService searchExecutorService = null;

    private void switchSearchWord(String word) {
        OkGo.getInstance().cancelTag("quick_search");
        quickSearchData.clear();
        searchTitle = word;
        searchResult();
    }

    private void startQuickSearch() {
        initCheckedSourcesForSearch();
        if (hadQuickStart)
            return;
        hadQuickStart = true;
        OkGo.getInstance().cancelTag("quick_search");
        quickSearchWord.clear();
        searchTitle = mVideo.name;
        quickSearchData.clear();
        quickSearchWord.addAll(SearchHelper.splitWords(searchTitle));
        // 分词
        OkGo.<String>get("http://api.pullword.com/get.php?source=" + URLEncoder.encode(searchTitle) + "&param1=0&param2=0&json=1")
                .tag("fenci")
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) {
                            return response.body().string();
                        } else {
                            throw new IllegalStateException("网络请求错误");
                        }
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        String json = response.body();
                        try {
                            for (JsonElement je : new Gson().fromJson(json, JsonArray.class)) {
                                quickSearchWord.add(je.getAsJsonObject().get("t").getAsString());
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                        List<String> words = new ArrayList<>(new HashSet<>(quickSearchWord));
                        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, words));
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                    }
                });

        searchResult();
    }

    private void searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        searchExecutorService = Executors.newFixedThreadPool(5);
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<String> siteKey = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable() || !bean.isQuickSearch()) {
                continue;
            }
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                continue;
            }
            siteKey.add(bean.getKey());
        }
        for (String key : siteKey) {
            searchExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    sourceViewModel.getQuickSearch(key, searchTitle);
                }
            });
        }
    }

    private void searchData(AbsXml absXml) {
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                // 去除当前相同的影片
                if (video.sourceKey.equals(sourceKey) && video.id.equals(vodId))
                    continue;
                data.add(video);
            }
            quickSearchData.addAll(data);
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, data));
        }
    }

    private void insertVod(String sourceKey, VodInfo vodInfo) {
        if (Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) {//无痕浏览
            return;
        }
        try {
            vodInfo.playNote = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).name;
        } catch (Throwable th) {
            vodInfo.playNote = "";
        }
        RoomDataManger.insertVodRecord(sourceKey, vodInfo);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH));
    }

    @Override
    protected void onDestroy() {
        if (fullWindows) {
            // 全屏状态下直接关闭详情页时也要复位方向，否则返回首页会一直停在横屏
            setRequestedOrientation(isPhoneLikeScreen()
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        registerActionReceiver(false);
        // 需求2：页面销毁时移除超时回调，避免泄漏
        mTimeoutHandler.removeCallbacks(detailTimeoutRunnable);
        // 页面销毁后测速回包不再更新 UI
        speedSession++;
        autoSourceSession++;
        autoSourceRunning = false;
        autoSourceHandler.removeCallbacksAndMessages(null);
        if (autoSourceExecutor != null) {
            autoSourceExecutor.shutdownNow();
            autoSourceExecutor = null;
        }
        super.onDestroy();
        unregisterReceiver(mBatteryReceiver);
        // 注销广播接收器
        if (mHomeKeyReceiver != null) {
            unregisterReceiver(mHomeKeyReceiver);
            mHomeKeyReceiver = null;
        }

        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        OkGo.getInstance().cancelTag("fenci");
        // 修复：OkGo 的 tag 是全局的，"detail" 又是所有详情页共用。
        // DetailActivity 是 singleTask，重复进入时旧实例先 onDestroy 会把新实例正在进行的
        // getDetail 请求一起取消，表现就是刚进详情页就直接"获取剧集列表失败"。
        // 详情请求本身只读、10 秒超时，让它自然结束即可，不再按 tag 取消。
        OkGo.getInstance().cancelTag("quick_search");
        toggleScreenShotListen(false);
    }

    @Override
    public void onBackPressed() {
        if (mAllSeriesRightDialog != null && mAllSeriesRightDialog.isShow()) {
            mAllSeriesRightDialog.dismiss();
            return;
        }
        if (mAllSeriesBottomDialog != null && mAllSeriesBottomDialog.isShow()) {
            mAllSeriesBottomDialog.dismiss();
            return;
        }
        if (playFragment.hideAllDialogSuccess()) {//fragment有弹窗隐藏并拦截返回
            return;
        }
        if (fullWindows) {
            toggleFullPreview();
            mBinding.mGridView.requestFocus();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && playFragment != null && fullWindows) {
            if (playFragment.dispatchKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // preview
    VodInfo previewVodInfo = null;
    boolean showPreview = Hawk.get(HawkConfig.SHOW_PREVIEW, true);
    ; // true 开启 false 关闭
    boolean fullWindows = false;
    ViewGroup.LayoutParams windowsPreview = null;
    ViewGroup.LayoutParams windowsFull = null;

    public void toggleFullPreview() {
        if (windowsPreview == null) {
            windowsPreview = mBinding.previewPlayer.getLayoutParams();
        }
        if (windowsFull == null) {//全屏尺寸
            windowsFull = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        fullWindows = !fullWindows;

        //交由fragment处理播放器全屏逻辑
        playFragment.changedLandscape(fullWindows);
        //activity处理预览尺寸(全屏/非全屏预览)
        mBinding.previewPlayer.setLayoutParams(fullWindows ? windowsFull : windowsPreview);
        mBinding.mGridView.setVisibility(fullWindows ? View.GONE : View.VISIBLE);
        mBinding.mGridViewFlag.setVisibility(fullWindows ? View.GONE : View.VISIBLE);

        //全屏下禁用详情页几个按键的焦点 防止上键跑过来
        mBinding.tvSort.setFocusable(!fullWindows);
        mBinding.tvCollect.setFocusable(!fullWindows);
        toggleSubtitleTextSize();
    }

    void toggleSubtitleTextSize() {
        int subtitleTextSize = SubtitleHelper.getTextSize(this);
        if (!fullWindows) {
            subtitleTextSize *= 0.6;
        }
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SUBTITLE_SIZE_CHANGE, subtitleTextSize));
    }

    public void use1DMDownload() {
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            VodInfo.VodSeries vod = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex);
            String url = TextUtils.isEmpty(playFragment.getFinalUrl()) ? vod.url : playFragment.getFinalUrl();
            // 创建Intent对象，启动1DM App
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setDataAndType(Uri.parse(url), "video/mp4");
            intent.putExtra("title", vodInfo.name + " " + vod.name); // 传入文件保存名
//            intent.setClassName("idm.internet.download.manager.plus", "idm.internet.download.manager.MainActivity");
            intent.setClassName("idm.internet.download.manager.plus", "idm.internet.download.manager.Downloader");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // 检查1DM App是否已安装
            PackageManager pm = getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            boolean isIntentSafe = activities.size() > 0;

            if (isIntentSafe) {
                startActivity(intent); // 启动1DM App
            } else {
                // 如果1DM App未安装，提示用户安装1DM App
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("请先安装1DM+下载管理器");
                builder.setMessage("为了下载视频，请先安装1DM+下载管理器。是否现在安装？");
                builder.setPositiveButton("立即下载", new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        // 跳转到下载链接
                        Intent downloadIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://od.lk/d/MzRfMTg0NTcxMDdf/1DM _v15.6.apk"));
                        startActivity(downloadIntent);
                    }
                });
                builder.setNegativeButton("取消", null);
                builder.show();
            }
        } else {
            ToastUtils.showShort("资源异常,请稍后重试");
        }
    }

    /**
     * 画中画模式
     */
    public void enterPip() {
        if (Utils.supportsPiPMode()) {
            // 创建一个Intent对象，模拟按下Home键
            // 部分机型（如三星 Android 13）会抛 IllegalStateException: Can't change activity type once set，
            // 此时跳过模拟Home直接进入画中画即可，避免崩溃
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Calculate Video Resolution
            int vWidth = playFragment.getPlayer().getVideoSize()[0];
            int vHeight = playFragment.getPlayer().getVideoSize()[1];
            Rational ratio;
            if (vWidth != 0) {
                if ((((double) vWidth) / ((double) vHeight)) > 2.39) {
                    vHeight = (int) (((double) vWidth) / 2.35);
                }
                ratio = new Rational(vWidth, vHeight);
            } else {
                ratio = new Rational(16, 9);
            }
            List<RemoteAction> actions = new ArrayList<>();
            actions.add(generateRemoteAction(android.R.drawable.ic_media_previous, IntentKey.BROADCAST_ACTION_PREV, "Prev", "Play Previous"));
            actions.add(generateRemoteAction(android.R.drawable.ic_media_play, IntentKey.BROADCAST_ACTION_PLAYPAUSE, "Play", "Play/Pause"));
            actions.add(generateRemoteAction(android.R.drawable.ic_media_next, IntentKey.BROADCAST_ACTION_NEXT, "Next", "Play Next"));
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(ratio)
                    .setActions(actions).build();
            playFragment.getPlayer().postDelayed(() -> {//代码模拟home键时会立即执行,toggleFullPreview中竖屏有切换横屏操作,
                if (!fullWindows) {
                    toggleFullPreview();
                }
            }, 300);
            enterPictureInPictureMode(params);
            playFragment.getController().hideBottom();

            playFragment.getPlayer().postDelayed(() -> {
                if (!playFragment.getPlayer().isPlaying()) {
                    playFragment.getController().togglePlay();
                }
            }, 400);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private RemoteAction generateRemoteAction(int iconResId, int actionCode, String title, String desc) {
        final PendingIntent intent =
                PendingIntent.getBroadcast(
                        DetailActivity.this,
                        actionCode,
                        new Intent(IntentKey.BROADCAST_ACTION).putExtra("action", actionCode),
                        0);
        final Icon icon = Icon.createWithResource(DetailActivity.this, iconResId);
        return (new RemoteAction(icon, title, desc, intent));
    }

    /**
     * 事件接收广播(画中画/后台播放点击事件)
     * @param isRegister 注册/注销
     */
    private void registerActionReceiver(boolean isRegister) {
        if (isRegister) {
            mRemoteActionReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !intent.getAction().equals(IntentKey.BROADCAST_ACTION) || playFragment.getController() == null) {
                        return;
                    }

                    int currentStatus = intent.getIntExtra("action", 1);
                    if (currentStatus == IntentKey.BROADCAST_ACTION_PREV) {
                        playFragment.playPrevious();
                    } else if (currentStatus == IntentKey.BROADCAST_ACTION_PLAYPAUSE) {
                        playFragment.getController().togglePlay();
                    } else if (currentStatus == IntentKey.BROADCAST_ACTION_NEXT) {
                        playFragment.playNext(false);
                    } else if (currentStatus == IntentKey.BROADCAST_ACTION_CLOSE) {
                        playServerSwitch(false);
                        finish();
                        NotificationUtils.cancelAll();
                    }
                }
            };
            registerReceiver(mRemoteActionReceiver, new IntentFilter(IntentKey.BROADCAST_ACTION));
        } else {
            if (mRemoteActionReceiver != null) {
                unregisterReceiver(mRemoteActionReceiver);
                mRemoteActionReceiver = null;
            }
            if (playFragment.getPlayer().isPlaying()) {// 退出画中画时,暂停播放(画中画的全屏也会触发,但全屏后会自动播放)
                playFragment.getController().togglePlay();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        registerActionReceiver(Utils.supportsPiPMode() && isInPictureInPictureMode);
    }

    /**
     * 后台播放服务开关,开启时注册操作广播,关闭时注销
     */
    private void playServerSwitch(boolean open) {
        if (open) {
            VodInfo.VodSeries vod = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex);
            PlayService.start(playFragment.getPlayer(), vodInfo.name + "&&" + vod.name);
            registerActionReceiver(true);
        } else {
            if (ServiceUtils.isServiceRunning(PlayService.class)) {
                PlayService.stop();
                registerActionReceiver(false);
            }
        }
    }

    public String getCurrentVodUrl() {
        return playFragment == null ? "" : playFragment.getFinalUrl();
    }

    public void quickLineChange() {
        List<VodInfo.VodSeriesFlag> flags = seriesFlagAdapter.getData();
        if (flags.size() > 1) {
            int currentIndex = 0;
            for (int i = 0; i < flags.size(); i++) {
                if (flags.get(i).selected) {
                    currentIndex = i;
                }
            }
            currentIndex += 1;
            if (currentIndex >= flags.size()) {
                currentIndex = 0;
            }
            mBinding.mGridViewFlag.smoothScrollToPosition(currentIndex);
            chooseFlag(currentIndex);
            mBinding.mGridView.postDelayed(() -> chooseSeries(vodInfo.playIndex, true), 300);
        }
    }

    public void showParseRoot(boolean show, ParseAdapter adapter) {
        mBinding.rvParse.setAdapter(adapter);
        int defaultIndex = 0;
        for (int i = 0; i < adapter.getData().size(); i++) {
            if (adapter.getData().get(i).isDefault()) {
                defaultIndex = i;
                break;
            }
        }
        if (defaultIndex != 0) {
            mBinding.rvParse.scrollToPosition(defaultIndex);
        }
        mBinding.parseRoot.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void toggleScreenShotListen(boolean open) {
        if (open){
            if (screenShotListenManager == null){
                screenShotListenManager = ScreenShotListenManager.newInstance(this);
            }
            screenShotListenManager.setListener(imagePath -> {

                if (playFragment.getPlayer().isInPlaybackState())return;

                new XPopup.Builder(this)
                        .isDarkTheme(Utils.isDarkTheme())
                        .asCenterList("",new String[]{"跳转阿狸","跳转优汐","跳转夸父","关闭"}, null, (position, text) -> {
                            String pkg = "";
                            String cls = "";
                            switch (position){
                                case 0:
                                    pkg = "com.alicloud.databox";
                                    cls = "com.alicloud.databox.launcher.splash.SplashActivity";
                                    break;
                                case 1:
                                    pkg = "com.UCMobile";
                                    cls = "com.uc.browser.InnerUCMobile";
                                    break;
                                case 2:
                                    pkg = "com.quark.browser";
                                    cls = "com.ucpro.MainActivity";
                                    break;
                                case 3:
                                    return;
                            }
                            try {
                                startActivity(new Intent().setComponent(new ComponentName(pkg, cls)));
                            }catch (Exception e){
                                ToastUtils.showShort("未找到应用");
                            }
                        })
                        .show();
            });
            screenShotListenManager.startListen();
        }else {
            if (screenShotListenManager != null) {
                screenShotListenManager.stopListen();
            }
        }
    }
}
