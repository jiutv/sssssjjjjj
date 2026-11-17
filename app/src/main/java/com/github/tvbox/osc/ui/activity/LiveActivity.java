package com.github.tvbox.osc.ui.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.blankj.utilcode.util.ConvertUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.CastVideo;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LivePlayerManager;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.player.controller.LiveNewController;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupNewAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemNewAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingItemAdapter;
import com.github.tvbox.osc.ui.dialog.AllChannelsRightDialog;
import com.github.tvbox.osc.ui.dialog.CastListDialog;
import com.github.tvbox.osc.ui.dialog.LivePasswordDialog;
import com.github.tvbox.osc.ui.dialog.LiveSettingDialog;
import com.github.tvbox.osc.ui.dialog.LiveSettingRightDialog;
import com.github.tvbox.osc.ui.tv.widget.ViewObj;
import com.github.tvbox.osc.ui.widget.LinearSpacingItemDecoration;
import com.github.tvbox.osc.ui.widget.PlayerMenuView;
import com.github.tvbox.osc.ui.widget.PlayerTitleView;
import com.github.tvbox.osc.util.BottomNavHelper;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.live.LiveSniffer;
import com.github.tvbox.osc.util.live.LiveSniffMemory;
import com.github.tvbox.osc.util.live.TxtSubscribe;
import com.github.tvbox.osc.util.live.WebLiveProfile;
import com.github.tvbox.osc.util.live.WebLiveScripts;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.enums.PopupPosition;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import xyz.doikki.videocontroller.component.LiveControlView;
import xyz.doikki.videocontroller.component.TitleView;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveActivity extends BaseActivity {
    // P0 fix: 静态 Activity 引用改为弱引用，避免旋转/退后台后泄漏
    private static volatile java.lang.ref.WeakReference<LiveActivity> sInstanceRef;

    private VideoView mVideoView;
    private TextView tvChannelInfo;
    private LinearLayout tvLeftChannelListLayout;
    private RecyclerView mChannelGroupView;
    private RecyclerView mLiveChannelView;
    public LiveChannelGroupNewAdapter liveChannelGroupAdapter;
    public LiveChannelItemNewAdapter liveChannelItemAdapter;

    private LinearLayout tvRightSettingLayout;
    private TvRecyclerView mSettingGroupView;
    private TvRecyclerView mSettingItemView;
    private LiveSettingGroupAdapter liveSettingGroupAdapter;
    private LiveSettingItemAdapter liveSettingItemAdapter;
    private List<LiveSettingGroup> liveSettingGroupList = new ArrayList<>();

    public static  int currentChannelGroupIndex = 0;
    // P0 fix: 显式指定主 Looper，避免后台线程 new Handler() 崩溃
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private List<LiveChannelGroup> liveChannelGroupList = new ArrayList<>();
    private int currentLiveChannelIndex = -1;
    private int currentLiveChangeSourceTimes = 0;
    private LiveChannelItem currentLiveChannelItem = null;
    private LivePlayerManager livePlayerManager = new LivePlayerManager();
    private ArrayList<Integer> channelGroupPasswordConfirmed = new ArrayList<>();

    // ===== 直播三种起播方式（直连 / video:// 后台嗅探 / webview://→FullWebActivity SNIFF）=====
    private static final String PROTOCOL_VIDEO = "video://";
    private static final String PROTOCOL_WEBVIEW = "webview://";
    private static final String PROTOCOL_WEBVIEW_SNIFF = "webview-sniff://";
    private LiveSniffer mLiveSniffer;
    private boolean mIsSniffing = false;
    private String mResolvedPlayUrl = null;
    private boolean mPlayFromWebSniff = false;
    /** 当前交给 FullWebActivity 嗅探的地址，用于回写嗅探记忆 */
    private String mPendingWebSniffUrl = null;
    private String mWebSniffFallbackChannel = null;
    private boolean mFirstResume = true;
    private int mWebLiveRelaunchCount = 0;
    private long mLastWebLiveRelaunchAt = 0;
    private int mLiveListRetry = 0;
    private boolean mLiveSourceDirectLoaded = false;

    // ===== 内嵌网页播放：央视频这类站点网页解码正常，直接嵌在当前视频区域，
    // 保持直播页自身的排版与频道列表（和 NewBox 一致），不跳独立网页页面 =====
    private WebView mWebLiveView = null;
    private int mWebLiveTries = 0;
    private ImageView mWebFullBtn = null;
    private LinearLayout mWebControlBar = null;
    private ImageView mWebPlayBtn = null;
    private TextView mWebStatus = null;
    private View mWebLoading = null;
    private boolean mWebPaused = false;
    /** 网页播放器是否已经出过画面（用于判断线路是否可用） */
    private boolean mWebPlayStarted = false;
    /** 网页线路一直没画面时自动切下一条线路：同一频道最多一次，避免来回循环 */
    private int mWebAutoSwitchCount = 0;
    private String mWebAutoSwitchChannel = null;
    private boolean mWebFullscreen = false;
    // 进入全屏前的方向，退出时恢复；手机/云手机统一回竖屏
    private int mOrientationBeforeWebFull = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private int mContainerHeightPx = 0;
    private int mVideoHeightPx = 0;
    /** 当前内嵌网页的地址与频道名，供站点适配脚本使用 */
    private String mWebLiveUrl = null;
    private String mWebLiveChannel = null;
    private final Runnable mWebLiveInjectRun = new Runnable() {
        @Override
        public void run() {
            if (mWebLiveView == null || mWebLiveTries++ >= 20) return;
            try {
                mWebLiveView.evaluateJavascript(FullWebActivity.AUTO_PLAY_JS, null);
                // 站点适配脚本（NewBox 同款）：藏站点 UI + 播放器铺满 + 必要时点选频道
                WebLiveScripts.inject(mWebLiveView, mWebLiveUrl, mWebLiveChannel);
                // 页面里的 video 真正出画后再撤掉"正在打开网页…"，避免露出网页界面
                mWebLiveView.evaluateJavascript(
                        "(function(){try{"
                                + "function has(d){var vs=d.querySelectorAll('video');"
                                + "for(var i=0;i<vs.length;i++){var v=vs[i];"
                                + "if(v.videoWidth>0&&(v.currentTime>0||v.readyState>=2))return true;}"
                                + "var fs=d.querySelectorAll('iframe');"
                                + "for(var j=0;j<fs.length;j++){try{"
                                + "if(fs[j].contentDocument&&has(fs[j].contentDocument))return true;}catch(e){}}"
                                + "return false;}return has(document);"
                                + "}catch(e){return false;}})();",
                        value -> {
                            if ("true".equals(value)) {
                                mWebPlayStarted = true;
                                hideWebLoading();
                            } else if (!mWebPlayStarted && mWebLiveTries >= 6 && mWebStatus != null) {
                                // 页面已加载完但一直没有画面：多半是站点改版/需要登录，给个明确提示
                                mWebStatus.setVisibility(View.VISIBLE);
                                mWebStatus.setText("网页线路未就绪，可切换线路");
                                // 再等几秒仍无画面就自动切下一条线路（同一频道只切一次）
                                String ch = currentLiveChannelItem == null ? null
                                        : currentLiveChannelItem.getChannelName();
                                if (mWebLiveTries >= 8 && mWebAutoSwitchCount < 1) {
                                    mWebAutoSwitchCount++;
                                    mWebAutoSwitchChannel = ch;
                                    ToastUtils.showShort("网页线路无画面，自动切换线路");
                                    playNextSource();
                                }
                            }
                        });
            } catch (Throwable ignored) {
            }
            mHandler.postDelayed(this, 2000);
        }
    };

    /** 兜底：迟迟没有画面时继续盖着黑幕（绝不露出网页），只更新提示文字 */
    private final Runnable mWebLoadingTimeoutRun = new Runnable() {
        @Override
        public void run() {
            if (mWebPlayStarted) return;
            if (mWebStatus != null) {
                mWebStatus.setVisibility(View.VISIBLE);
                mWebStatus.setText("网页线路未就绪，可切换线路");
            }
            mHandler.postDelayed(this, 5000);
        }
    };

    /** 撤掉"正在打开网页…"黑幕，露出网页播放画面 */
    private void hideWebLoading() {
        mHandler.removeCallbacks(mWebLoadingTimeoutRun);
        if (mWebLoading != null) mWebLoading.setVisibility(View.GONE);
        if (mWebControlBar != null) mWebControlBar.bringToFront();
    }

//EPG   by 龍
    private static LiveChannelItem  channel_Name = null;
    private static Hashtable hsEpg = new Hashtable();
    private CountDownTimer countDownTimer;
//    private CountDownTimer countDownTimerRightTop;
    TextView tv_channelnum;
    TextView tip_chname;

    TextView tv_srcinfo;
    public String epgStringAddress ="";

    private boolean isSHIYI = false;
    private boolean isBack = false;
    public static String playUrl;
    //kenson
    private ImageView imgLiveIcon;
    SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd");
    private CountDownTimer countDownTimer3;
    private int videoWidth = 1920;
    private int videoHeight = 1080;
    private  boolean show = false;
    private PlayerTitleView mPlayerTitleView;
    private BasePopupView mSettingRightDialog;
    private BasePopupView mSettingBottomDialog;
    private BasePopupView mAllChannelRightDialog;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live;
    }

    @Override
    protected void init() {
        ImmersionBar.with(this)
                .statusBarColor(R.color.black)
                .statusBarDarkFont(false)
                .navigationBarColor(R.color.black)
                .fitsSystemWindows(true)
                .hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR)
                .init();
        // P0 fix: 只保留 WeakReference，删掉原来的 static Context context 赋值
        sInstanceRef = new java.lang.ref.WeakReference<>(this);
        epgStringAddress = Hawk.get(HawkConfig.EPG_URL,"");
        if(epgStringAddress == null || epgStringAddress.length()<5)
            epgStringAddress = "http://epg.51zmt.top:8000/api/diyp/";

        setLoadSir(findViewById(R.id.live_root));
        BottomNavHelper.INSTANCE.attach(this, findViewById(R.id.bottom_nav), R.id.navigation_live);
        mVideoView = findViewById(R.id.mVideoView);

        tvLeftChannelListLayout = findViewById(R.id.tvLeftChannnelListLayout);

        mChannelGroupView = findViewById(R.id.mGroupGridView);
        mLiveChannelView = findViewById(R.id.mChannelGridView);
        mChannelGroupView.addItemDecoration(new LinearSpacingItemDecoration(20,true));
        mLiveChannelView.addItemDecoration(new LinearSpacingItemDecoration(20,true));

        tvRightSettingLayout = findViewById(R.id.tvRightSettingLayout);
        mSettingGroupView = findViewById(R.id.mSettingGroupView);
        mSettingItemView = findViewById(R.id.mSettingItemView);
        tvChannelInfo = findViewById(R.id.tvChannel);

        //EPG  findViewById  by 龍
        tip_chname = findViewById(R.id.tv_channel_bar_name);//底部名称
        tip_chname.setOnClickListener(view -> {
            mChannelGroupView.scrollToPosition(currentChannelGroupIndex);
            mLiveChannelView.scrollToPosition(currentLiveChannelIndex);
        });
        tv_channelnum = findViewById(R.id.tv_channel_bottom_number); //底部数字

        tv_srcinfo = findViewById(R.id.tv_source);//线路状态

        //源切换
        findViewById(R.id.ic_pre_source).setOnClickListener(view -> playPreSource());
        findViewById(R.id.ic_next_source).setOnClickListener(view -> playNextSource());
        tv_srcinfo.setOnClickListener(view -> playNextSource());
        // 长按线路信息：清除「网页嗅探失败」记忆，让所有频道重新尝试嗅探
        tv_srcinfo.setOnLongClickListener(view -> {
            LiveSniffMemory.clear();
            ToastUtils.showShort("已清除嗅探记忆，下次重新尝试");
            return true;
        });
        //投屏/设置
        findViewById(R.id.ic_setting).setOnClickListener(view -> showSettingDialog(false));
        findViewById(R.id.ic_cast).setOnClickListener(view -> showCastDialog());
        mWebFullBtn = findViewById(R.id.btn_web_fullscreen);
        if (mWebFullBtn != null) {
            mWebFullBtn.setOnClickListener(view -> toggleWebFullscreen());
        }
        mWebControlBar = findViewById(R.id.web_control_bar);
        mWebLoading = findViewById(R.id.web_loading);
        mWebPlayBtn = findViewById(R.id.btn_web_play);
        mWebStatus = findViewById(R.id.tv_web_status);
        if (mWebPlayBtn != null) {
            mWebPlayBtn.setOnClickListener(view -> toggleWebPlay());
        }
        ImageView prev = findViewById(R.id.btn_web_prev);
        if (prev != null) prev.setOnClickListener(view -> playPreSource());
        ImageView next = findViewById(R.id.btn_web_next);
        if (next != null) next.setOnClickListener(view -> playNextSource());
        ImageView reload = findViewById(R.id.btn_web_reload);
        if (reload != null) reload.setOnClickListener(view -> reloadWebLive());

        initVideoView();
        initChannelGroupView();
        initLiveChannelView();
        initSettingGroupView();
        initSettingItemView();
        initLiveChannelList();
        initLiveSettingGroupList();
    }

    //显示底部EPG
    private void showBottomEpg() {
        if (isSHIYI)
            return;
        if (channel_Name.getChannelName() != null) {
            mPlayerTitleView.setTitle(channel_Name.getChannelName());
            tip_chname.setText(channel_Name.getChannelName());
            tv_channelnum.setText("" + channel_Name.getChannelNum());
            //todo 上一/下一/当前节目信息
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }

            if (channel_Name == null || channel_Name.getSourceNum() <= 0) {
                tv_srcinfo.setText("1/1");
            } else {
                tv_srcinfo.setText("线路" + (channel_Name.getSourceIndex() + 1) + "/" + channel_Name.getSourceNum());
            }

            Handler handler = new Handler(Looper.getMainLooper());

        }
    }


    @Override
    public void onBackPressed() {
        if (mWebFullscreen) {
            exitWebFullscreen();
            return;
        }
        if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        } else if(isBack){
            isBack= false;
            playPreSource();
        } else if(mSettingBottomDialog!=null && mSettingBottomDialog.isShow()){//适配底部导航栏(手势条闪屏)变成view模式后在back时手动隐藏
            mSettingBottomDialog.dismiss();
        } else if(mSettingRightDialog!=null && mSettingRightDialog.isShow()){
            mSettingRightDialog.dismiss();
        }  else if(mAllChannelRightDialog!=null && mAllChannelRightDialog.isShow()){
            mAllChannelRightDialog.dismiss();
        } else if (!mVideoView.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                //showSettingGroup();
            } else if (!isListOrSettingLayoutVisible()) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false))
                            playNext();
                        else
                            playPrevious();
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false))
                            playPrevious();
                        else
                            playNext();
                        break;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if(isBack){

                        }else{
                            //showSettingGroup();
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if(isBack){

                        }else{
                            playNextSource();
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        showChannelList();
                        break;
                }
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mVideoView != null) {
            mVideoView.resume();
        }
        if (mWebLiveView != null) {
            mWebLiveView.onResume();
            mWebLiveView.evaluateJavascript(
                    "(function(){try{var v=document.querySelector('video');if(v)v.play();}catch(e){}})();", null);
        }
        mFirstResume = false;
        // 回到前台时重刷沉浸式，防止系统导航条漏出来
        applyImmersive();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    /** 重新应用沉浸式隐藏：系统导航条 / 状态栏都不给露出来，和 NewBox 一致 */
    private void applyImmersive() {
        try {
            View decor = getWindow().getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decor.setSystemUiVisibility(flags);
        } catch (Throwable ignored) {
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null) {
            mVideoView.pause();
        }
        if (mWebLiveView != null) {
            mWebLiveView.onPause();
            mWebLiveView.evaluateJavascript(
                    "(function(){try{var v=document.querySelector('video');if(v)v.pause();}catch(e){}})();", null);
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        mIsSniffing = false;
        if (mLiveSniffer != null) {
            mLiveSniffer.destroy();
            mLiveSniffer = null;
        }
        releaseEmbeddedWebLive();
        if (mWebFullscreen) {
            // 全屏状态下直接退出页面时，也要把方向恢复，否则会一直停在横屏
            mWebFullscreen = false;
            restoreOrientationAfterFullscreen();
        }
        super.onDestroy();
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
    }

    private void showChannelList() {
        if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
            return;
        }
        //重新载入上一次状态
        liveChannelItemAdapter.setNewData(getLiveChannels(currentChannelGroupIndex));
        if (currentLiveChannelIndex > -1){
            mLiveChannelView.smoothScrollToPosition(currentLiveChannelIndex);
            if (currentChannelGroupIndex==0){
                mChannelGroupView.scrollToPosition(currentChannelGroupIndex);
            }else {
                mChannelGroupView.smoothScrollToPosition(currentChannelGroupIndex);
            }
        }
    }

    private void showChannelInfo() {
        tvChannelInfo.setText(String.format(Locale.getDefault(), "%d %s %s(%d/%d)", currentLiveChannelItem.getChannelNum(),
                currentLiveChannelItem.getChannelName(), currentLiveChannelItem.getSourceName(),
                currentLiveChannelItem.getSourceIndex() + 1, currentLiveChannelItem.getSourceNum()));

        FrameLayout.LayoutParams lParams = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            lParams.gravity = Gravity.LEFT;
            lParams.leftMargin = 60;
            lParams.topMargin = 30;
        } else {
            lParams.gravity = Gravity.RIGHT;
            lParams.rightMargin = 60;
            lParams.topMargin = 30;
        }
        tvChannelInfo.setLayoutParams(lParams);

        tvChannelInfo.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mHideChannelInfoRun);
        mHandler.postDelayed(mHideChannelInfoRun, 3000);
    }

    private Runnable mHideChannelInfoRun = new Runnable() {
        @Override
        public void run() {
            tvChannelInfo.setVisibility(View.INVISIBLE);
        }
    };

    private boolean playChannel(int channelGroupIndex, int liveChannelIndex, boolean changeSource) {
        if ((channelGroupIndex == currentChannelGroupIndex && liveChannelIndex == currentLiveChannelIndex && !changeSource)
                || (changeSource && currentLiveChannelItem.getSourceNum() == 1)) {
           // showChannelInfo();
            return true;
        }
        mVideoView.release();
        if (!changeSource) {
            currentChannelGroupIndex = channelGroupIndex;
            currentLiveChannelIndex = liveChannelIndex;
            currentLiveChannelItem = getLiveChannels(currentChannelGroupIndex).get(currentLiveChannelIndex);
            Hawk.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem.getChannelName());
            livePlayerManager.getLiveChannelPlayer(mVideoView, currentLiveChannelItem.getChannelName());
            // 切换频道后重置网页直播自动重开计数
            mWebLiveRelaunchCount = 0;
        }

        channel_Name = currentLiveChannelItem;
        isSHIYI=false;
        isBack = false;
        if(currentLiveChannelItem.getUrl().indexOf("PLTV/8888") !=-1){
            currentLiveChannelItem.setinclude_back(true);
        }else {
            currentLiveChannelItem.setinclude_back(false);
        }
        showBottomEpg();

        startLivePlay(currentLiveChannelItem);
        return true;
    }

    /**
     * 统一直播起播入口：
     * 1. http/https/rtmp/m3u8 等直连地址直接交给原生播放器；
     * 2. video:// 启动 LiveSniffer 后台嗅探，命中后把真实地址交给原生播放器；
     * 3. webview:// / webview-sniff:// 打开 FullWebActivity SNIFF 模式：
     *    用户能看到网页（自动全屏），shouldInterceptRequest 抓流，
     *    命中回传给 LiveActivity → 原生播放器接手；
     *    45 秒内没抓到 → FullWebActivity 自己留在网页播放，用户手动按 back 返回。
     */
    private static final int REQ_FULL_WEB_SNIFF = 0x4657; // 'FW'

    private void startLivePlay(LiveChannelItem item) {
        if (item == null) return;
        String rawUrl = item.getUrl();
        // 换了频道就重置「网页无画面自动切线路」的计数，同频道内最多自动切一次
        String curChannel = item.getChannelName();
        if (curChannel == null || !curChannel.equals(mWebAutoSwitchChannel)) {
            mWebAutoSwitchCount = 0;
            mWebAutoSwitchChannel = null;
        }
        if (TextUtils.isEmpty(rawUrl)) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    onLivePlayFailed();
                }
            }, 1000);
            return;
        }
        stopSniff();
        mPlayFromWebSniff = false;

        String pureUrl = unwrapProtocol(rawUrl);
        if (rawUrl.startsWith("webview://") || rawUrl.startsWith("webview-sniff://")) {
            // 所有网页频道统一内嵌播放：网页在底层，原生控制条盖在上面，
            // 打开时先显示"正在打开网页…"，避免直接露出网页界面
            LOG.i("🎬 直播线路：内嵌网页播放 → " + pureUrl);
            startEmbeddedWebLive(pureUrl);
            return;
        } else if (rawUrl.startsWith("video://")) {
            if (LiveSniffMemory.isFailed(pureUrl)) {
                // 后台嗅探是黑屏等待，失败过的地址改为「网页 + 嗅探」：
                // 至少能看到网页，同时仍然继续尝试抓流，不会永久退化成网页
                LOG.i("🎬 video:// 上次未识别 → 网页+嗅探 → " + pureUrl);
                ToastUtils.showShort("该频道上次未识别到直链，改用网页播放");
                mPendingWebSniffUrl = pureUrl;
                mLastWebLiveRelaunchAt = System.currentTimeMillis();
                Intent intent = new Intent(this, FullWebActivity.class);
                intent.putExtra(FullWebActivity.EXTRA_URL, pureUrl);
                intent.putExtra(FullWebActivity.EXTRA_MODE, FullWebActivity.MODE_SNIFF);
                startActivityForResult(intent, REQ_FULL_WEB_SNIFF);
            } else {
                startSniff(pureUrl);
            }
        } else {
            playDirect(rawUrl, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FULL_WEB_SNIFF) return;
        String sniffedUrl = mPendingWebSniffUrl;
        mPendingWebSniffUrl = null;
        if (resultCode != RESULT_OK || data == null) {
            // 网页里没抓到流就退出了：记住该地址，下次直接网页播放
            if (!TextUtils.isEmpty(sniffedUrl)) LiveSniffMemory.markFailed(sniffedUrl);
            return;
        }
        try {
            String streamUrl = data.getStringExtra(FullWebActivity.RESULT_STREAM_URL);
            String headersEncoded = data.getStringExtra(FullWebActivity.RESULT_STREAM_HEADERS);
            if (TextUtils.isEmpty(streamUrl)) return;
            if (!TextUtils.isEmpty(sniffedUrl)) LiveSniffMemory.markSuccess(sniffedUrl);
            LOG.i("🎬 FullWebActivity sniff 回传: " + streamUrl);
            HashMap<String, String> headers = FullWebActivity.decodeHeaders(headersEncoded);
            mResolvedPlayUrl = streamUrl;
            mPlayFromWebSniff = true;
            playDirect(streamUrl, headers);
        } catch (Throwable t) {
            LOG.i("onActivityResult REQ_FULL_WEB_SNIFF crash: " + t);
        }
    }

    /**
     * 内嵌网页播放：把 WebView 放进当前 240dp 视频区域，网页播放器自动播放并铺满该区域，
     * 直播页的频道列表、线路信息、底部导航全部保持可用。
     */
    private void startEmbeddedWebLive(final String url) {
        final View container = findViewById(R.id.video_container);
        if (container == null) {
            // 布局异常时退回独立网页页面
            Intent intent = new Intent(this, FullWebActivity.class);
            intent.putExtra(FullWebActivity.EXTRA_URL, url);
            intent.putExtra(FullWebActivity.EXTRA_MODE, FullWebActivity.MODE_SNIFF);
            startActivityForResult(intent, REQ_FULL_WEB_SNIFF);
            return;
        }

        // 同一域名下的频道间换台 → 复用 WebView，只在页面里切台（像 NewBox CCTV1→CCTV2 那样）
        // 跨域名才销毁重建
        if (mWebLiveView != null && isSameHost(mWebLiveUrl, url)) {
            switchChannelInPage(url, currentLiveChannelItem == null ? null : currentLiveChannelItem.getChannelName());
            return;
        }

        releaseEmbeddedWebLive();
        mWebLiveUrl = url;
        mWebLiveChannel = currentLiveChannelItem == null ? null : currentLiveChannelItem.getChannelName();
        mWebPlayStarted = false;
        try {
            mVideoView.setVisibility(View.GONE);
            mVideoView.pause();

            // 网页播放期间隐藏 App 自己的底部导航（首页/直播/设置），
            // 只保留画面 + 原生播放控制条，和 NewBox 一致
            hideView(R.id.bottom_nav);

            WebView wv = new WebView(this);
            WebSettings s = wv.getSettings();
            s.setUserAgentString(WebLiveProfile.uaFor(url));
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }
            wv.setBackgroundColor(Color.BLACK);
            // 网页只当画面用（和 NewBox 一致）：不给焦点、吞掉所有触摸，
            // 控制一律走原生控制栏，避免误点到网页里的其它页面。
            wv.setFocusable(false);
            wv.setFocusableInTouchMode(false);
            wv.setClickable(false);
            wv.setLongClickable(false);
            wv.setOnTouchListener((v, e) -> true);
            wv.setOnLongClickListener(v -> true);
            s.setSupportZoom(false);
            s.setBuiltInZoomControls(false);
            s.setDisplayZoomControls(false);
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String u) {
                    mWebLiveTries = 0;
                    mHandler.removeCallbacks(mWebLiveInjectRun);
                    mHandler.post(mWebLiveInjectRun);
                    // 注意：这里不能撤黑幕。页面加载完不代表已经在播，
                    // 提前撤掉会把网页本身（导航栏、频道列表）露出来。
                    if (!mWebPlayStarted && mWebLoading != null) {
                        mWebLoading.setVisibility(View.VISIBLE);
                        mWebLoading.bringToFront();
                        if (mWebControlBar != null) mWebControlBar.bringToFront();
                    }
                    if (!mWebPlayStarted) {
                        mHandler.removeCallbacks(mWebLoadingTimeoutRun);
                        mHandler.postDelayed(mWebLoadingTimeoutRun, 15000);
                    }
                }
            });
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT);
            ((ViewGroup) container).addView(wv, lp);
            mWebLiveView = wv;
            mWebLiveTries = 0;
            wv.loadUrl(url);
            LOG.i("🎬 内嵌 WebView 已创建：" + url);
            // WebView 是最后 addView 的，会盖住加载层，这里重新置顶
            if (mWebLoading != null) {
                mWebLoading.setVisibility(View.VISIBLE);
                mWebLoading.bringToFront();
            }
            // 手机版直接用原生控制栏（上一台/播放/重载/下一台/全屏）
            if (mWebControlBar != null) {
                mWebControlBar.setVisibility(View.VISIBLE);
                mWebControlBar.bringToFront();
            }
            if (mWebFullBtn != null) mWebFullBtn.setVisibility(View.VISIBLE);
            // 兜底：15 秒还没出画面就保持黑幕并提示，绝不把网页界面露出来
            mHandler.postDelayed(mWebLoadingTimeoutRun, 15000);
            mWebPaused = false;
        } catch (Throwable t) {
            LOG.i("内嵌 WebView 创建失败：" + t);
            mVideoView.setVisibility(View.VISIBLE);
        }
    }

    /** 释放内嵌 WebView，恢复原生播放器显示 */
    /** 同一域名下换台：复用 WebView，只在页面里切目标频道，不 reload 网页，
     * 避免每次换台都重新加载整个页面（像 NewBox CCTV1→CCTV2 那样）。 */
    private void switchChannelInPage(String newUrl, String newChannelName) {
        if (mWebLiveView == null) return;
        mWebLiveUrl = newUrl;
        mWebLiveChannel = newChannelName;
        mWebLiveTries = 0;
        mWebPlayStarted = false;
        mWebPaused = false;
        mWebAutoSwitchCount = 0;
        if (mWebPlayBtn != null) mWebPlayBtn.setImageResource(R.drawable.ic_ctl_pause);
        if (mWebStatus != null) mWebStatus.setText("切换频道…");
        // 先停掉旧视频，避免两个播放器同时存在
        try {
            mWebLiveView.evaluateJavascript(
                    "(function(){try{var v=document.querySelector('video');if(v){v.pause();try{v.src='';}catch(e){}}}catch(e){}})();",
                    null);
        } catch (Throwable ignored) {
        }
        // 盖回黑幕，别让切换过程露出网页
        if (mWebLoading != null) {
            mWebLoading.setVisibility(View.VISIBLE);
            mWebLoading.bringToFront();
        }
        if (mWebControlBar != null) mWebControlBar.bringToFront();
        mHandler.removeCallbacks(mWebLiveInjectRun);
        mHandler.removeCallbacks(mWebLoadingTimeoutRun);
        mHandler.postDelayed(mWebLiveInjectRun, 200);
        mHandler.postDelayed(mWebLoadingTimeoutRun, 15000);
        LOG.i("🎬 同域切台（复用 WebView）：" + newChannelName);
    }

    /** 两个 URL 是否同域名（只取 host，忽略 www 前缀和路径） */
    private static boolean isSameHost(String a, String b) {
        if (TextUtils.isEmpty(a) || TextUtils.isEmpty(b)) return false;
        try {
            String ha = Uri.parse(a).getHost();
            String hb = Uri.parse(b).getHost();
            if (ha == null || hb == null) return false;
            ha = ha.toLowerCase();
            hb = hb.toLowerCase();
            if (ha.equals(hb)) return true;
            // 忽略 www. 前缀差异
            if (ha.startsWith("www.")) ha = ha.substring(4);
            if (hb.startsWith("www.")) hb = hb.substring(4);
            return ha.equals(hb);
        } catch (Throwable t) {
            return false;
        }
    }

    private void releaseEmbeddedWebLive() {
        mWebLiveUrl = null;
        mWebLiveChannel = null;
        mHandler.removeCallbacks(mWebLiveInjectRun);
        mHandler.removeCallbacks(mWebLoadingTimeoutRun);
        if (mWebFullscreen) exitWebFullscreen();
        if (mWebFullBtn != null) mWebFullBtn.setVisibility(View.GONE);
        if (mWebControlBar != null) mWebControlBar.setVisibility(View.GONE);
        if (mWebLoading != null) mWebLoading.setVisibility(View.GONE);
        // 退出网页播放时还原 App 底部导航
        showView(R.id.bottom_nav);
        if (mWebLiveView != null) {
            try {
                ViewGroup parent = (ViewGroup) mWebLiveView.getParent();
                if (parent != null) parent.removeView(mWebLiveView);
                mWebLiveView.loadUrl("about:blank");
                mWebLiveView.destroy();
            } catch (Throwable ignored) {
            }
            mWebLiveView = null;
        }
        if (mVideoView != null) mVideoView.setVisibility(View.VISIBLE);
    }

    /** 网页播放的暂停/继续：直接控制页面里的 video */
    private void toggleWebPlay() {
        if (mWebLiveView == null) return;
        mWebPaused = !mWebPaused;
        String js = "(function(){try{var v=document.querySelector('video');if(!v)return;"
                + (mWebPaused ? "v.pause();" : "v.muted=false;v.play();")
                + "}catch(e){}})();";
        mWebLiveView.evaluateJavascript(js, null);
        if (mWebPlayBtn != null) {
            mWebPlayBtn.setImageResource(mWebPaused ? R.drawable.ic_ctl_play : R.drawable.ic_ctl_pause);
        }
        if (mWebStatus != null) mWebStatus.setText(mWebPaused ? "已暂停" : "网页播放");
    }

    /** 网页播放刷新：重新加载页面并重新注入适配脚本 */
    private void reloadWebLive() {
        if (mWebLiveView == null) return;
        mWebLiveTries = 0;
        mWebPaused = false;
        mWebPlayStarted = false;
        mWebAutoSwitchCount = 0;
        if (mWebPlayBtn != null) mWebPlayBtn.setImageResource(R.drawable.ic_ctl_pause);
        if (mWebStatus != null) mWebStatus.setText("重新加载中…");
        // 重新加载期间盖回黑幕，别让网页界面露出来
        if (mWebLoading != null) {
            mWebLoading.setVisibility(View.VISIBLE);
            mWebLoading.bringToFront();
            if (mWebControlBar != null) mWebControlBar.bringToFront();
        }
        mHandler.removeCallbacks(mWebLoadingTimeoutRun);
        mHandler.postDelayed(mWebLoadingTimeoutRun, 15000);
        mWebLiveView.reload();
        mHandler.removeCallbacks(mWebLiveInjectRun);
        mHandler.postDelayed(mWebLiveInjectRun, 3000);
    }

    /** 全屏 / 退出全屏：网页播放同样支持横屏铺满（原播放器的全屏能力） */
    private void toggleWebFullscreen() {
        if (mWebFullscreen) exitWebFullscreen();
        else enterWebFullscreen();
    }

    private void enterWebFullscreen() {
        View container = findViewById(R.id.video_container);
        if (container == null) return;
        mWebFullscreen = true;
        mOrientationBeforeWebFull = getRequestedOrientation();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        if (mContainerHeightPx == 0) mContainerHeightPx = container.getHeight();
        if (mVideoHeightPx == 0 && mVideoView != null) mVideoHeightPx = mVideoView.getHeight();

        // 隐藏直播页其它区域，只保留视频
        hideView(R.id.ll_epg);
        hideView(R.id.tvLeftChannnelListLayout);
        hideView(R.id.bottom_nav);
        hideView(R.id.tvChannel);

        ViewGroup.LayoutParams lp = container.getLayoutParams();
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        container.setLayoutParams(lp);
        if (mVideoView != null) {
            ViewGroup.LayoutParams vlp = mVideoView.getLayoutParams();
            vlp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mVideoView.setLayoutParams(vlp);
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if (mWebFullBtn != null) mWebFullBtn.setImageResource(R.drawable.ic_web_fullscreen_exit);
        if (mWebControlBar != null) mWebControlBar.bringToFront();
        syncWebLiveViewport();
    }

    /**
     * 全屏进入/退出后同步网页视口。
     *
     * <p>站点脚本按当前可视区域算尺寸（部分是像素值），容器一变化就必须重新排版，
     * 否则会出现「退出全屏后网页还按全屏尺寸显示、盖住频道列表」。</p>
     */
    private void syncWebLiveViewport() {
        if (mWebLiveView == null) return;
        try {
            mWebLiveView.evaluateJavascript(
                    "(function(){try{window.dispatchEvent(new Event('resize'));"
                            + "if(window.__tvboxRelayout)window.__tvboxRelayout();"
                            + "if(window.__mboxFallback)window.__mboxFallback();}catch(e){}})()", null);
        } catch (Throwable ignored) {
        }
        final String url = mWebLiveUrl;
        final String channel = mWebLiveChannel;
        // 等布局稳定后按新尺寸重新铺一次
        mHandler.postDelayed(() -> {
            if (mWebLiveView != null && url != null) {
                WebLiveScripts.inject(mWebLiveView, url, channel);
            }
        }, 500);
    }

    private void exitWebFullscreen() {
        if (!mWebFullscreen) return;
        mWebFullscreen = false;
        restoreOrientationAfterFullscreen();

        showView(R.id.ll_epg);
        showView(R.id.tvLeftChannnelListLayout);
        // 如果还在网页播放就不还原底部导航（网页播放全程隐藏）
        if (mWebLiveView == null) showView(R.id.bottom_nav);
        showView(R.id.tvChannel);

        View container = findViewById(R.id.video_container);
        if (container != null) {
            ViewGroup.LayoutParams lp = container.getLayoutParams();
            lp.height = mContainerHeightPx > 0 ? mContainerHeightPx : ConvertUtils.dp2px(240);
            container.setLayoutParams(lp);
            // 网页内容超出容器时不能画到频道列表上去
            if (container instanceof ViewGroup) {
                ((ViewGroup) container).setClipChildren(true);
                ((ViewGroup) container).setClipToPadding(true);
            }
        }
        if (mWebLiveView != null) {
            ViewGroup.LayoutParams wlp = mWebLiveView.getLayoutParams();
            wlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            wlp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mWebLiveView.setLayoutParams(wlp);
            mWebLiveView.requestLayout();
        }
        if (mVideoView != null) {
            ViewGroup.LayoutParams vlp = mVideoView.getLayoutParams();
            vlp.height = mVideoHeightPx > 0 ? mVideoHeightPx : ConvertUtils.dp2px(300);
            mVideoView.setLayoutParams(vlp);
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if (mWebFullBtn != null) mWebFullBtn.setImageResource(R.drawable.ic_web_fullscreen_enter);
        syncWebLiveViewport();
    }

    /**
     * 退出全屏后恢复方向：
     * 手机/云手机固定回竖屏（这类设备全屏后不会自动复位，会一直停在横屏）；
     * 电视/平板则恢复到进入全屏前的方向。
     */
    private void restoreOrientationAfterFullscreen() {
        if (isPhoneLikeScreen()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (mOrientationBeforeWebFull != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            setRequestedOrientation(mOrientationBeforeWebFull);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private void hideView(int id) {
        View v = findViewById(id);
        if (v != null) v.setVisibility(View.GONE);
    }

    private void showView(int id) {
        View v = findViewById(id);
        if (v != null) v.setVisibility(View.VISIBLE);
    }

    /** 直连起播：完全沿用原有播放器配置、解码方式与错误处理 */
    private void playDirect(String url, HashMap<String, String> headers) {
        releaseEmbeddedWebLive();
        if (headers != null && !headers.isEmpty()) {
            mVideoView.setUrl(url, headers);
        } else {
            mVideoView.setUrl(url);
        }
        mVideoView.start();
    }

    /** video:// 嗅探起播，命中后交给原生播放器 */
    private void startSniff(String pageUrl) {
        stopSniff();
        mIsSniffing = true;
        // 嗅探期间由嗅探超时控制换源，暂停播放器状态超时
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
        ToastUtils.showShort("正在嗅探直播地址…");
        if (mLiveSniffer != null) {
            mLiveSniffer.destroy();
            mLiveSniffer = null;
        }
        mLiveSniffer = new LiveSniffer(this, new LiveSniffer.Callback() {
            @Override
            public void onSniffed(String url, HashMap<String, String> headers) {
                if (!mIsSniffing || isFinishing() || mVideoView == null) {
                    return;
                }
                mIsSniffing = false;
                mResolvedPlayUrl = url;
                LiveSniffMemory.markSuccess(pageUrl);
                if (mLiveSniffer != null) {
                    mLiveSniffer.stop();
                }
                playDirect(url, headers);
            }

            @Override
            public void onFailed(String reason) {
                if (!mIsSniffing || isFinishing() || mVideoView == null) {
                    return;
                }
                mIsSniffing = false;
                LiveSniffMemory.markFailed(pageUrl);
                ToastUtils.showShort(reason);
                onLivePlayFailed();
            }
        });
        mLiveSniffer.start(pageUrl);
    }

    private void stopSniff() {
        mIsSniffing = false;
        if (mLiveSniffer != null) {
            mLiveSniffer.stop();
        }
    }

    /**
     * 嗅探失败处理：完全复用原仓库的超时换源规则
     * 先切换下一线路，当前频道所有线路都失败后按跨选分类规则换台
     */
    private void onLivePlayFailed() {
        if (currentLiveChannelItem == null) {
            return;
        }
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
        // 网页嗅探出来的直链播不了（DRM / 防盗链 / 只给播放器用）：
        // 退回网页播放，而不是直接跳到下一个频道，避免用户看到黑屏后频道乱跳
        if (mPlayFromWebSniff) {
            mPlayFromWebSniff = false;
            String channelUrl = currentLiveChannelItem.getUrl();
            String channelKey = currentLiveChannelItem.getChannelName() + "|" + channelUrl;
            if (!TextUtils.isEmpty(channelUrl)
                    && (channelUrl.startsWith(PROTOCOL_WEBVIEW) || channelUrl.startsWith(PROTOCOL_WEBVIEW_SNIFF))
                    && !channelKey.equals(mWebSniffFallbackChannel)) {
                // 同一频道只回退一次，避免「网页→直链→失败→网页」死循环
                mWebSniffFallbackChannel = channelKey;
                ToastUtils.showShort("该地址无法直连播放，改用网页播放");
                mResolvedPlayUrl = null;
                startLivePlay(currentLiveChannelItem);
                return;
            }
        }
        currentLiveChangeSourceTimes++;
        if (currentLiveChannelItem.getSourceNum() == currentLiveChangeSourceTimes) {
            currentLiveChangeSourceTimes = 0;
            Integer[] groupChannelIndex = getNextChannel(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false) ? -1 : 1);
            playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
        } else {
            playNextSource();
        }
    }

    /** 去掉 webview:// / webview-sniff:// / video:// 前缀，兼容 xxx://https://... 与 xxx://host/path */
    private String unwrapProtocol(String url) {
        if (url.startsWith(PROTOCOL_WEBVIEW_SNIFF)) {
            url = url.substring(PROTOCOL_WEBVIEW_SNIFF.length());
        } else if (url.startsWith(PROTOCOL_WEBVIEW)) {
            url = url.substring(PROTOCOL_WEBVIEW.length());
        } else if (url.startsWith(PROTOCOL_VIDEO)) {
            url = url.substring(PROTOCOL_VIDEO.length());
        }
        if (url.startsWith("//")) {
            url = url.substring(2);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return url;
    }

    private void playNext() {
        if (!isCurrentLiveChannelValid()) return;
        Integer[] groupChannelIndex = getNextChannel(1);
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
    }

    private void playPrevious() {
        if (!isCurrentLiveChannelValid()) return;
        Integer[] groupChannelIndex = getNextChannel(-1);
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
    }

    public void playPreSource() {
        if (!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.preSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }

    public void playNextSource() {
        if (!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.nextSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }

    //显示设置列表
    private void showSettingGroup() {

        if (tvRightSettingLayout.getVisibility() == View.INVISIBLE) {
            if (!isCurrentLiveChannelValid()) return;
            //重新载入默认状态
            loadCurrentSourceList();
            liveSettingGroupAdapter.setNewData(liveSettingGroupList);
            selectSettingGroup(0, false);
            mSettingGroupView.smoothScrollToPosition(0);
            mSettingItemView.smoothScrollToPosition(currentLiveChannelItem.getSourceIndex());
            mHandler.postDelayed(mFocusAndShowSettingGroup, 200);
        } else {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        }
    }

    private Runnable mFocusAndShowSettingGroup = new Runnable() {
        @Override
        public void run() {
            if (mSettingGroupView.isScrolling() || mSettingItemView.isScrolling() || mSettingGroupView.isComputingLayout() || mSettingItemView.isComputingLayout()) {
                mHandler.postDelayed(this, 100);
            } else {
                RecyclerView.ViewHolder holder = mSettingGroupView.findViewHolderForAdapterPosition(0);
                if (holder != null)
                    holder.itemView.requestFocus();
                tvRightSettingLayout.setVisibility(View.VISIBLE);
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvRightSettingLayout.getLayoutParams();
                if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
                    ViewObj viewObj = new ViewObj(tvRightSettingLayout, params);
                    ObjectAnimator animator = ObjectAnimator.ofObject(viewObj, "marginRight", new IntEvaluator(), -tvRightSettingLayout.getLayoutParams().width, 0);
                    animator.setDuration(200);
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            mHandler.postDelayed(mHideSettingLayoutRun, 5000);
                        }
                    });
                    animator.start();
                }
            }
        }
    };

    private Runnable mHideSettingLayoutRun = new Runnable() {
        @Override
        public void run() {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvRightSettingLayout.getLayoutParams();
            if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
                ViewObj viewObj = new ViewObj(tvRightSettingLayout, params);
                ObjectAnimator animator = ObjectAnimator.ofObject(viewObj, "marginRight", new IntEvaluator(), 0, -tvRightSettingLayout.getLayoutParams().width);
                animator.setDuration(200);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        tvRightSettingLayout.setVisibility(View.INVISIBLE);
                        liveSettingGroupAdapter.setSelectedGroupIndex(-1);
                    }
                });
                animator.start();
            }
        }
    };

//    private void initVideoView() {
//        StandardVideoController controller = new StandardVideoController(this);
//        controller.addControlComponent(new LiveControlView(this)); //直播控制条
//        controller.setEnableInNormal(true);
//        controller.setGestureEnabled(true);
//        mVideoView.setVideoController(controller);
//        mVideoView.setProgressManager(null);
//    }

    private void initVideoView() {
        LiveNewController controller = new LiveNewController(this);
        PlayerMenuView playerMenuView = getPlayerMenuView();
        controller.addControlComponent(playerMenuView); //菜单栏,设置投屏等
        controller.addControlComponent(new LiveControlView(this)); //直播控制条
        //标题栏
        mPlayerTitleView = new PlayerTitleView(this);
        controller.addControlComponent(mPlayerTitleView);
        controller.setListener(new LiveNewController.LiveControlListener() {

            @Override
            public void setting() {
                //showSettingGroup();
            }

            @Override
            public void playStateChanged(int playState) {
                switch (playState) {
                    case VideoView.STATE_IDLE:
                    case VideoView.STATE_PAUSED:
                        break;
                    case VideoView.STATE_PREPARED:
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PLAYING:
                        currentLiveChangeSourceTimes = 0;
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        break;
                    case VideoView.STATE_ERROR:
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, 2000);
                        break;
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1) + 1) * 5000);
                        break;
                }
            }

            @Override
            public void changeSource(int direction) {
                if (direction > 0){
                    playNextSource();
                } else {
                    playPreSource();
                }
            }
        });
        controller.setCanChangePosition(false);
        controller.setEnableInNormal(true);
        controller.setGestureEnabled(true);
        controller.setDoubleTapTogglePlayEnabled(false);
        mVideoView.setVideoController(controller);
        mVideoView.setProgressManager(null);
    }

    @NonNull
    private PlayerMenuView getPlayerMenuView() {
        PlayerMenuView playerMenuView = new PlayerMenuView(this);
        playerMenuView.setOnPlayerMenuClickListener(new PlayerMenuView.OnPlayerMenuClickListener() {
            @Override
            public void expand() {
                showAllChannelDialog();
            }

            @Override
            public void onSetting() {
                showSettingDialog(true);
            }

            @Override
            public void onCast() {
                showCastDialog();
            }
        });
        return playerMenuView;
    }

    private Runnable mConnectTimeoutChangeSourceRun = new Runnable() {
        @Override
        public void run() {
            currentLiveChangeSourceTimes++;
            if (currentLiveChannelItem.getSourceNum() == currentLiveChangeSourceTimes) {
                currentLiveChangeSourceTimes = 0;
                Integer[] groupChannelIndex = getNextChannel(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false) ? -1 : 1);
                playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
            } else {
                playNextSource();
            }
        }
    };

    private void initChannelGroupView() {
        mChannelGroupView.setHasFixedSize(true);
        mChannelGroupView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveChannelGroupAdapter = new LiveChannelGroupNewAdapter();
        mChannelGroupView.setAdapter(liveChannelGroupAdapter);

        //手机/模拟器
        liveChannelGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectChannelGroup(position, false, -1);
            }
        });
    }

    private void selectChannelGroup(int groupIndex, boolean focus, int liveChannelIndex) {
        if (focus) {
            liveChannelGroupAdapter.setFocusedGroupIndex(groupIndex);
            liveChannelItemAdapter.setFocusedChannelIndex(-1);
        }
        if ((groupIndex > -1 && groupIndex != liveChannelGroupAdapter.getSelectedGroupIndex()) || isNeedInputPassword(groupIndex)) {
            liveChannelGroupAdapter.setSelectedGroupIndex(groupIndex);
            if (isNeedInputPassword(groupIndex)) {
                showPasswordDialog(groupIndex, liveChannelIndex);
                return;
            }
            loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
        }
    }

    private void initLiveChannelView() {
        mLiveChannelView.setHasFixedSize(true);
        mLiveChannelView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveChannelItemAdapter = new LiveChannelItemNewAdapter();
        mLiveChannelView.setAdapter(liveChannelItemAdapter);

        liveChannelItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                clickLiveChannel(position);
            }
        });
    }

    private void clickLiveChannel(int position) {
        liveChannelItemAdapter.setSelectedChannelIndex(position);
        playChannel(liveChannelGroupAdapter.getSelectedGroupIndex(), position, false);
    }

    private void initSettingGroupView() {
        mSettingGroupView.setHasFixedSize(true);
        mSettingGroupView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveSettingGroupAdapter = new LiveSettingGroupAdapter();
        mSettingGroupView.setAdapter(liveSettingGroupAdapter);
        mSettingGroupView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, 5000);
            }
        });

        liveSettingGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectSettingGroup(position, false);
            }
        });
    }

    private void selectSettingGroup(int position, boolean focus) {
        if (!isCurrentLiveChannelValid()) return;
        if (focus) {
            liveSettingGroupAdapter.setFocusedGroupIndex(position);
            liveSettingItemAdapter.setFocusedItemIndex(-1);
        }
        if (position == liveSettingGroupAdapter.getSelectedGroupIndex() || position < -1)
            return;

        liveSettingGroupAdapter.setSelectedGroupIndex(position);
        liveSettingItemAdapter.setNewData(liveSettingGroupList.get(position).getLiveSettingItems());

        switch (position) {
            case 0:
                liveSettingItemAdapter.selectItem(currentLiveChannelItem.getSourceIndex(), true, false);
                break;
            case 1:
                liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerScale(), true, true);
                break;
            case 2:
                liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerType(), true, true);
                break;
        }
        int smoothScrollToPosition = liveSettingItemAdapter.getSelectedItemIndex();
        if (smoothScrollToPosition < 0) smoothScrollToPosition = 0;
        mSettingItemView.smoothScrollToPosition(smoothScrollToPosition);
        mHandler.removeCallbacks(mHideSettingLayoutRun);
        mHandler.postDelayed(mHideSettingLayoutRun, 5000);
    }

    private void initSettingItemView() {
        mSettingItemView.setHasFixedSize(true);
        mSettingItemView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveSettingItemAdapter = new LiveSettingItemAdapter();
        mSettingItemView.setAdapter(liveSettingItemAdapter);
        mSettingItemView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, 5000);
            }
        });

        liveSettingItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                clickSettingItem(position);
            }
        });
    }

    private void clickSettingItem(int position) {
        int settingGroupIndex = liveSettingGroupAdapter.getSelectedGroupIndex();
        if (settingGroupIndex < 4) {
            if (position == liveSettingItemAdapter.getSelectedItemIndex())
                return;
            liveSettingItemAdapter.selectItem(position, true, true);
        }
        switch (settingGroupIndex) {
            case 0://线路切换
                currentLiveChannelItem.setSourceIndex(position);
                playChannel(currentChannelGroupIndex, currentLiveChannelIndex,true);
                break;
            case 1://画面比例
                livePlayerManager.changeLivePlayerScale(mVideoView, position, currentLiveChannelItem.getChannelName());
                break;
            case 2://播放解码
                mVideoView.release();
                livePlayerManager.changeLivePlayerType(mVideoView, position, currentLiveChannelItem.getChannelName());
                startLivePlay(currentLiveChannelItem);
                break;
            case 3://超时换源
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, position);
                break;
            case 4://超时换源
                boolean select = false;
                switch (position) {
                    case 0:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_TIME, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_TIME, select);
                        break;
                    case 1:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_NET_SPEED, select);
                        break;
                    case 2:
                        select = !Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false);
                        Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE, select);
                        break;
                    case 3:
                        select = !Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false);
                        Hawk.put(HawkConfig.LIVE_CROSS_GROUP, select);
                        break;
                }
                liveSettingItemAdapter.selectItem(position, select, false);
                break;
        }
        mHandler.removeCallbacks(mHideSettingLayoutRun);
        mHandler.postDelayed(mHideSettingLayoutRun, 5000);
    }

    private void initLiveChannelList() {
        List<LiveChannelGroup> list = ApiConfig.get().getChannelGroupList();
        if (!list.isEmpty()) {
            if (list.size() == 1 && list.get(0).getGroupName().startsWith("http://127.0.0.1")) {
                loadProxyLives(list.get(0).getGroupName());
            } else {
                liveChannelGroupList.clear();
                liveChannelGroupList.addAll(list);
                showSuccess();
                initLiveState();
            }
            return;
        }

        // 分组为空：点播配置可能仍在解析，或点播配置里没有 lives。
        // 直播订阅里已选中直播源时直接加载，避免直播完全依赖点播配置
        String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "");
        if (!TextUtils.isEmpty(liveUrl) && !mLiveSourceDirectLoaded) {
            mLiveSourceDirectLoaded = true;
            loadLiveSource(liveUrl);
            return;
        }
        // 配置自带的 lives 可能仍在后台解析，短暂重试，避免误报“频道列表为空”
        if (!mLiveSourceDirectLoaded && mLiveListRetry < 3) {
            mLiveListRetry++;
            showLoading();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    initLiveChannelList();
                }
            }, 600);
            return;
        }
        Toast.makeText(App.getInstance(), "频道列表为空，请检查直播源", Toast.LENGTH_SHORT).show();
        finish();
    }

    public void loadProxyLives(String url) {
        String sourceUrl;
        try {
            Uri parsedUrl = Uri.parse(url);
            sourceUrl = new String(Base64.decode(parsedUrl.getQueryParameter("ext"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
        } catch (Throwable th) {
            Toast.makeText(App.getInstance(), "频道列表为空，请检查直播源", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loadLiveSource(sourceUrl);
    }

    /**
     * 解析直播 JSON，兼容 {"groups":[...]}、裸数组、单个分组对象三种写法
     */
    private JsonArray parseJsonLives(String content) {
        try {
            com.google.gson.JsonElement element = new com.google.gson.JsonParser().parse(content);
            if (element.isJsonArray()) {
                return normalizeLives(element.getAsJsonArray());
            }
            if (element.isJsonObject()) {
                com.google.gson.JsonObject obj = element.getAsJsonObject();
                if (obj.has("groups") && obj.get("groups").isJsonArray()) {
                    return normalizeLives(obj.getAsJsonArray("groups"));
                }
                if (obj.has("channels")) {
                    JsonArray single = new JsonArray();
                    single.add(obj);
                    return normalizeLives(single);
                }
            }
        } catch (Throwable e) {
            android.util.Log.i("LiveSource", "直播 JSON 解析失败: " + e.getMessage());
        }
        return new JsonArray();
    }

    /**
     * 统一成 ApiConfig.loadLives 需要的结构：{group, channels:[{name, urls:[]}]}
     */
    private JsonArray normalizeLives(JsonArray groups) {
        JsonArray result = new JsonArray();
        for (com.google.gson.JsonElement groupElement : groups) {
            if (!groupElement.isJsonObject()) continue;
            com.google.gson.JsonObject group = groupElement.getAsJsonObject();
            String groupName = jsonStr(group, "group", jsonStr(group, "groupName", "未分组"));
            JsonArray channels = group.has("channels") && group.get("channels").isJsonArray()
                    ? group.getAsJsonArray("channels") : new JsonArray();
            JsonArray outChannels = new JsonArray();
            for (com.google.gson.JsonElement channelElement : channels) {
                if (!channelElement.isJsonObject()) continue;
                com.google.gson.JsonObject channel = channelElement.getAsJsonObject();
                String name = jsonStr(channel, "name", jsonStr(channel, "channelName", ""));
                if (name.isEmpty()) continue;
                JsonArray urls = new JsonArray();
                if (channel.has("urls") && channel.get("urls").isJsonArray()) {
                    for (com.google.gson.JsonElement urlElement : channel.getAsJsonArray("urls")) {
                        String url = urlElement.isJsonPrimitive() ? urlElement.getAsString().trim() : "";
                        if (!url.isEmpty()) urls.add(url);
                    }
                } else if (channel.has("url") && channel.get("url").isJsonPrimitive()) {
                    String url = channel.get("url").getAsString().trim();
                    if (!url.isEmpty()) urls.add(url);
                }
                if (urls.size() == 0) continue;
                JsonObject item = new JsonObject();
                item.addProperty("name", name);
                item.add("urls", urls);
                outChannels.add(item);
            }
            if (outChannels.size() == 0) continue;
            JsonObject outGroup = new JsonObject();
            outGroup.addProperty("group", groupName.isEmpty() ? "未分组" : groupName);
            outGroup.add("channels", outChannels);
            result.add(outGroup);
        }
        return result;
    }

    private String jsonStr(com.google.gson.JsonObject obj, String key, String def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try {
                String value = obj.get(key).getAsString().trim();
                if (!value.isEmpty()) return value;
            } catch (Throwable ignored) {
            }
        }
        return def;
    }

    /** 拉取并解析直播源（txt / m3u / json），成功后交给直播列表 */
    private void loadLiveSource(String url) {
        showLoading();
        android.util.Log.i("LiveSource", "加载直播源: " + url);
        OkGo.<String>get(url).execute(new AbsCallback<String>() {

            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                return response.body() == null ? "" : response.body().string();
            }

            @Override
            public void onSuccess(Response<String> response) {
                JsonArray livesArray;
                String content = response.body() == null ? "" : response.body();
                String trimmed = content.trim();
                if (trimmed.startsWith("\uFEFF")) {
                    // 部分订阅源带 UTF-8 BOM，会导致首行判断失败
                    trimmed = trimmed.substring(1).trim();
                }
                String format;
                LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap = new LinkedHashMap<>();
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    // 标准 TVBox 直播 JSON：{"groups":[{"group":"央视","channels":[{"name":"CCTV-1","urls":["..."]}]}]}
                    format = "json";
                    livesArray = parseJsonLives(trimmed);
                } else if (trimmed.startsWith("#EXTM3U") || trimmed.contains("#EXTINF")) {
                    // M3U/M3U8 播放列表：常见订阅源格式
                    format = "m3u";
                    TxtSubscribe.parseM3u(linkedHashMap, content);
                    livesArray = TxtSubscribe.live2JsonArray(linkedHashMap);
                } else {
                    format = "txt";
                    TxtSubscribe.parse(linkedHashMap, content);
                    livesArray = TxtSubscribe.live2JsonArray(linkedHashMap);
                }
                android.util.Log.i("LiveSource", "直播源格式: " + format + "，长度: " + content.length()
                        + "，分组数: " + livesArray.size());

                ApiConfig.get().loadLives(livesArray);
                List<LiveChannelGroup> list = ApiConfig.get().getChannelGroupList();
                if (list.isEmpty()) {
                    Toast.makeText(App.getInstance(), "直播源内容为空或格式不支持", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                liveChannelGroupList.clear();
                liveChannelGroupList.addAll(list);

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        LiveActivity.this.showSuccess();
                        initLiveState();
                    }
                });
            }

            @Override
            public void onError(Response<String> response) {
                super.onError(response);
                String reason;
                if (response.getException() != null) {
                    reason = response.getException().getMessage();
                } else if (response.code() > 0) {
                    reason = "HTTP " + response.code() + (TextUtils.isEmpty(response.message()) ? "" : " " + response.message());
                } else {
                    reason = "网络异常";
                }
                android.util.Log.e("LiveSource", "直播源加载失败: " + reason);
                Toast.makeText(App.getInstance(), "直播源加载失败：" + reason, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void initLiveState() {
        String lastChannelName = Hawk.get(HawkConfig.LIVE_CHANNEL, "");

        int lastChannelGroupIndex = -1;
        int lastLiveChannelIndex = -1;
        for (LiveChannelGroup liveChannelGroup : liveChannelGroupList) {
            for (LiveChannelItem liveChannelItem : liveChannelGroup.getLiveChannels()) {
                if (liveChannelItem.getChannelName().equals(lastChannelName)) {
                    lastChannelGroupIndex = liveChannelGroup.getGroupIndex();
                    lastLiveChannelIndex = liveChannelItem.getChannelIndex();
                    break;
                }
            }
            if (lastChannelGroupIndex != -1) break;
        }
        if (lastChannelGroupIndex == -1) {
            lastChannelGroupIndex = getFirstNoPasswordChannelGroup();
            if (lastChannelGroupIndex == -1)
                lastChannelGroupIndex = 0;
            lastLiveChannelIndex = 0;
        }

        livePlayerManager.init(mVideoView);

        tvRightSettingLayout.setVisibility(View.INVISIBLE);

        liveChannelGroupAdapter.setNewData(liveChannelGroupList);
        selectChannelGroup(lastChannelGroupIndex, false, lastLiveChannelIndex);
    }

    private boolean isListOrSettingLayoutVisible() {
        return tvLeftChannelListLayout.getVisibility() == View.VISIBLE || tvRightSettingLayout.getVisibility() == View.VISIBLE;
    }

    private void initLiveSettingGroupList() {
        ArrayList<String> groupNames = new ArrayList<>(Arrays.asList("线路选择", "画面比例", "播放解码", "超时换源", "偏好设置"));
        ArrayList<ArrayList<String>> itemsArrayList = new ArrayList<>();
        ArrayList<String> sourceItems = new ArrayList<>();
        ArrayList<String> scaleItems = new ArrayList<>(Arrays.asList("默认", "16:9", "4:3", "填充", "原始", "裁剪"));
        ArrayList<String> playerDecoderItems = new ArrayList<>(Arrays.asList("系统", "ijk硬解", "ijk软解", "exo"));
        ArrayList<String> timeoutItems = new ArrayList<>(Arrays.asList("5s", "10s", "15s", "20s", "25s", "30s"));
        ArrayList<String> personalSettingItems = new ArrayList<>(Arrays.asList("显示时间", "显示网速", "换台反转", "跨选分类"));
        itemsArrayList.add(sourceItems);
        itemsArrayList.add(scaleItems);
        itemsArrayList.add(playerDecoderItems);
        itemsArrayList.add(timeoutItems);
        itemsArrayList.add(personalSettingItems);

        liveSettingGroupList.clear();
        for (int i = 0; i < groupNames.size(); i++) {
            LiveSettingGroup liveSettingGroup = new LiveSettingGroup();
            ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
            liveSettingGroup.setGroupIndex(i);
            liveSettingGroup.setGroupName(groupNames.get(i));
            for (int j = 0; j < itemsArrayList.get(i).size(); j++) {
                LiveSettingItem liveSettingItem = new LiveSettingItem();
                liveSettingItem.setItemIndex(j);
                liveSettingItem.setItemName(itemsArrayList.get(i).get(j));
                liveSettingItemList.add(liveSettingItem);
            }
            liveSettingGroup.setLiveSettingItems(liveSettingItemList);
            liveSettingGroupList.add(liveSettingGroup);
        }
        liveSettingGroupList.get(3).getLiveSettingItems().get(Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1)).setItemSelected(true);
        liveSettingGroupList.get(4).getLiveSettingItems().get(0).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_TIME, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(1).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(2).setItemSelected(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(3).setItemSelected(Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false));
    }

    private void loadCurrentSourceList() {
        ArrayList<String> currentSourceNames = currentLiveChannelItem.getChannelSourceNames();
        ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
        for (int j = 0; j < currentSourceNames.size(); j++) {
            LiveSettingItem liveSettingItem = new LiveSettingItem();
            liveSettingItem.setItemIndex(j);
            liveSettingItem.setItemName(currentSourceNames.get(j));
            liveSettingItemList.add(liveSettingItem);
        }
        liveSettingGroupList.get(0).setLiveSettingItems(liveSettingItemList);
    }

    private void showPasswordDialog(int groupIndex, int liveChannelIndex) {

        LivePasswordDialog dialog = new LivePasswordDialog(this);
        dialog.setOnListener(new LivePasswordDialog.OnListener() {
            @Override
            public void onChange(String password) {
                if (password.equals(liveChannelGroupList.get(groupIndex).getGroupPassword())) {
                    channelGroupPasswordConfirmed.add(groupIndex);
                    loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
                } else {
                    Toast.makeText(App.getInstance(), "密码错误", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancel() {
                if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    int groupIndex = liveChannelGroupAdapter.getSelectedGroupIndex();
                    liveChannelItemAdapter.setNewData(getLiveChannels(groupIndex));
                }
            }
        });
        dialog.show();
    }

    private void loadChannelGroupDataAndPlay(int groupIndex, int liveChannelIndex) {
        liveChannelItemAdapter.setNewData(getLiveChannels(groupIndex));
        if (groupIndex == currentChannelGroupIndex) {
            if (currentLiveChannelIndex > -1)
                mLiveChannelView.smoothScrollToPosition(currentLiveChannelIndex);
            liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
        }
        else {
            mLiveChannelView.smoothScrollToPosition(0);
            liveChannelItemAdapter.setSelectedChannelIndex(-1);
        }

        if (liveChannelIndex > -1) {
            clickLiveChannel(liveChannelIndex);
            if (groupIndex==0){//部分手机smoothScrollToPosition向上划出屏幕
                mChannelGroupView.scrollToPosition(groupIndex);
            }else {
                mChannelGroupView.smoothScrollToPosition(groupIndex);
            }

            mLiveChannelView.smoothScrollToPosition(liveChannelIndex);
            playChannel(groupIndex, liveChannelIndex, false);
        }
    }

    private boolean isNeedInputPassword(int groupIndex) {
        return !liveChannelGroupList.get(groupIndex).getGroupPassword().isEmpty()
                && !isPasswordConfirmed(groupIndex);
    }

    private boolean isPasswordConfirmed(int groupIndex) {
        for (Integer confirmedNum : channelGroupPasswordConfirmed) {
            if (confirmedNum == groupIndex)
                return true;
        }
        return false;
    }

    private ArrayList<LiveChannelItem> getLiveChannels(int groupIndex) {
        if (!isNeedInputPassword(groupIndex)) {
            return liveChannelGroupList.get(groupIndex).getLiveChannels();
        } else {
            return new ArrayList<>();
        }
    }

    private Integer[] getNextChannel(int direction) {
        int channelGroupIndex = currentChannelGroupIndex;
        int liveChannelIndex = currentLiveChannelIndex;

        //跨选分组模式下跳过加密频道分组（遥控器上下键换台/超时换源）
        if (direction > 0) {
            liveChannelIndex++;
            if (liveChannelIndex >= getLiveChannels(channelGroupIndex).size()) {
                liveChannelIndex = 0;
                if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex++;
                        if (channelGroupIndex >= liveChannelGroupList.size())
                            channelGroupIndex = 0;
                    } while (!liveChannelGroupList.get(channelGroupIndex).getGroupPassword().isEmpty() || channelGroupIndex == currentChannelGroupIndex);
                }
            }
        } else {
            liveChannelIndex--;
            if (liveChannelIndex < 0) {
                if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex--;
                        if (channelGroupIndex < 0)
                            channelGroupIndex = liveChannelGroupList.size() - 1;
                    } while (!liveChannelGroupList.get(channelGroupIndex).getGroupPassword().isEmpty() || channelGroupIndex == currentChannelGroupIndex);
                }
                liveChannelIndex = getLiveChannels(channelGroupIndex).size() - 1;
            }
        }

        Integer[] groupChannelIndex = new Integer[2];
        groupChannelIndex[0] = channelGroupIndex;
        groupChannelIndex[1] = liveChannelIndex;

        return groupChannelIndex;
    }

    private int getFirstNoPasswordChannelGroup() {
        for (LiveChannelGroup liveChannelGroup : liveChannelGroupList) {
            if (liveChannelGroup.getGroupPassword().isEmpty())
                return liveChannelGroup.getGroupIndex();
        }
        return -1;
    }

    private boolean isCurrentLiveChannelValid() {
        if (currentLiveChannelItem == null) {
            Toast.makeText(App.getInstance(), "请先选择频道", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    //计算两个时间相差的秒数
    public static long getTime(String startTime, String endTime)  {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long eTime = 0;
        try {
            eTime = df.parse(endTime).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long sTime = 0;
        try {
            sTime = df.parse(startTime).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long diff = (eTime - sTime) / 1000;
        return diff;
    }
    private  String durationToString(int duration) {
        String result = "";
        int dur = duration / 1000;
        int hour=dur/3600;
        int min = (dur / 60) % 60;
        int sec = dur % 60;
        if(hour>0){
            if (min > 9) {
                if (sec > 9) {
                    result =hour+":"+ min + ":" + sec;
                } else {
                    result =hour+":"+ min + ":0" + sec;
                }
            } else {
                if (sec > 9) {
                    result =hour+":"+ "0" + min + ":" + sec;
                } else {
                    result = hour+":"+"0" + min + ":0" + sec;
                }
            }
        }else{
            if (min > 9) {
                if (sec > 9) {
                    result = min + ":" + sec;
                } else {
                    result = min + ":0" + sec;
                }
            } else {
                if (sec > 9) {
                    result ="0" + min + ":" + sec;
                } else {
                    result = "0" + min + ":0" + sec;
                }
            }
        }
        return result;
    }

    public void showAllChannelDialog() {
        mAllChannelRightDialog = new XPopup.Builder(this)
                .isViewMode(true)
                .hasNavigationBar(false)
                .hasShadowBg(false)
                .popupHeight(ScreenUtils.getScreenHeight())
                .popupPosition(PopupPosition.Right)
                .asCustom(new AllChannelsRightDialog(this));
        mAllChannelRightDialog.show();
    }

    public void showCastDialog() {
        if (currentLiveChannelItem!=null){
            String castUrl = TextUtils.isEmpty(mResolvedPlayUrl) ? currentLiveChannelItem.getUrl() : mResolvedPlayUrl;
            if (castUrl.startsWith(PROTOCOL_VIDEO) || castUrl.startsWith(PROTOCOL_WEBVIEW)) {
                castUrl = unwrapProtocol(castUrl);
            }
            new XPopup.Builder(this)
                    .maxWidth(ConvertUtils.dp2px(360))
                    .asCustom(new CastListDialog(this,new CastVideo(currentLiveChannelItem.getChannelName(),castUrl)))
                    .show();
        }
    }

    public LivePlayerManager getLivePlayerManager(){
        return livePlayerManager;
    }

    public LiveChannelItem getCurrentLiveChannelItem(){
        return currentLiveChannelItem;
    }

    /**
     * 切换某个线路播放
     * @param position
     */
    public void switchingLine2Replay(int position){
        currentLiveChannelItem.setSourceIndex(position);
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex,true);
    }

    /**
     * 切换缩放比例
     * @param position
     */
    public void changeScale(int position){
        livePlayerManager.changeLivePlayerScale(mVideoView, position, currentLiveChannelItem.getChannelName());
    }

    /**
     * 更换播放解码
     * @param position
     */
    public void changePlayer(int position){
        mVideoView.release();
        livePlayerManager.changeLivePlayerType(mVideoView, position, currentLiveChannelItem.getChannelName());
        startLivePlay(currentLiveChannelItem);
    }

    /**
     * 设置弹窗
     * @param fullScreenStyle 全屏显示侧边弹窗
     */
    private void showSettingDialog(boolean fullScreenStyle) {
        if (!isCurrentLiveChannelValid()){
            ToastUtils.showShort("当前频道未加载");
            return;
        }
        if (fullScreenStyle){
            mSettingRightDialog = new XPopup.Builder(this)
                    .isViewMode(true)
                    .hasNavigationBar(false)
                    .hasShadowBg(false)
                    .popupHeight(ScreenUtils.getScreenHeight())
                    .popupWidth(ConvertUtils.dp2px(300))
                    .popupPosition(PopupPosition.Right)
                    .asCustom(new LiveSettingRightDialog(this));
            mSettingRightDialog.show();
        }else {
            mSettingBottomDialog = new XPopup.Builder(this)
                    .isViewMode(true)
                    .popupHeight(ScreenUtils.getScreenHeight()/2)
                    .hasNavigationBar(false)
                    .hasShadowBg(false)
                    .asCustom(new LiveSettingDialog(this));
            mSettingBottomDialog.show();
        }

    }

}