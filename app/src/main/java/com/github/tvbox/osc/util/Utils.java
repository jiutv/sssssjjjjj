package com.github.tvbox.osc.util;

import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Build;
import android.provider.MediaStore;

import androidx.appcompat.app.AppCompatDelegate;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.VideoInfo;
import com.github.tvbox.osc.bean.VodInfo;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;


public class Utils {

    public static boolean supportsPiPMode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static int getSeriesSpanCount(List<VodInfo.VodSeries> list) {
        int spanCount = 4;
        int total = 0;
        for (VodInfo.VodSeries item : list) total += item.name.length();
        int offset = (int) Math.ceil((double) total / list.size());
        if (offset >= 12) spanCount = 1;
        else if (offset >= 8) spanCount = 2;
        else if (offset >= 4) spanCount = 3;
        else if (offset >= 2) spanCount = 4;
        return spanCount;
    }

    public static String stringForTime(long timeMs) {
//        if (timeMs <= 0 || timeMs >= 24 * 60 * 60 * 1000) {
//            return "00:00";
//        }
        long totalSeconds = timeMs / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        StringBuilder stringBuilder = new StringBuilder();
        Formatter mFormatter = new Formatter(stringBuilder, Locale.getDefault());
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    public static List<VideoInfo> getVideoList() {
        List<VideoInfo> videoList = new ArrayList<>();
        Cursor cursor = App.getInstance().getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                new String[] { // 查询内容
                        MediaStore.Video.Media._ID, // 视频id
                        MediaStore.Video.Media.DATA, // 视频路径
                        MediaStore.Video.Media.SIZE, // 视频字节大小
                        MediaStore.Video.Media.DISPLAY_NAME, // 视频名称 xxx.mp4
                        MediaStore.Video.Media.TITLE, // 视频标题
                        MediaStore.Video.Media.DURATION, // 视频时长
                        MediaStore.Video.Media.RESOLUTION, // 视频分辨率 X x Y格式
                        MediaStore.Video.Media.IS_PRIVATE,
                        MediaStore.Video.Media.BUCKET_ID,
                        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                        MediaStore.Video.Media.BOOKMARK // 上次视频播放的位置
                },
                null,
                null,
                null
        );
        if (cursor != null && cursor.moveToFirst()) {
            do {
                VideoInfo videoInfo = new VideoInfo();
                videoInfo.setId(cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)));
                videoInfo.setPath(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)));
                videoInfo.setSize(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)));
                videoInfo.setDisplayName(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)));
                videoInfo.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)));
                videoInfo.setDuration(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)));
                videoInfo.setResolution(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION)));
                videoInfo.setIsPrivate(cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.IS_PRIVATE)));
                videoInfo.setBucketId(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)));
                videoInfo.setBucketDisplayName(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)));
                videoInfo.setBookmark(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BOOKMARK)));
                videoList.add(videoInfo);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return videoList;
    }

    /** 浅色皮肤：雾蓝(7) / 奶油(9)，其余皮肤均为深色 */
    public static boolean isLightSkin(int tag) {
        return tag == 7 || tag == 9;
    }

    public static boolean isDarkTheme(){
        // 皮肤方案：深色皮肤按深色处理，浅色皮肤（雾蓝/奶油）按浅色处理
        int tag = getSkinTag();
        if (tag > 0) {
            return !isLightSkin(tag);
        }
        int currentNightMode = App.getInstance().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES || AppCompatDelegate.getDefaultNightMode()==AppCompatDelegate.MODE_NIGHT_YES;
    }

    public static void initTheme(){
        // 皮肤方案：深色皮肤锁定深色模式，浅色皮肤锁定浅色模式，避免系统配色干扰
        int tag = getSkinTag();
        if (tag > 0) {
            AppCompatDelegate.setDefaultNightMode(isLightSkin(tag) ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
            return;
        }
        switch (Hawk.get(HawkConfig.THEME_TAG,0)) {
            case 0:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }
    }

    /**
     * 当前主题方案：0 默认（跟随系统明暗），
     * 1 黑金凤雅 / 2 青黑科技 / 3 蓝紫沉浸 / 4 暖橙怀旧 / 5 液态玻璃（默认）/ 6 樱花粉
     * 7 雾蓝（浅色）/ 8 森林绿 / 9 奶油（浅色暖调）
     */
    public static int getSkinTag() {
        return Hawk.get(HawkConfig.SKIN_TAG, 5);
    }

    /**
     * 旧版皮肤编号迁移：去掉「深色磨砂满天星」后重新编号，仅执行一次。
     * 旧 1(满天星)→5(液态玻璃) / 2→1 / 3→2 / 4→3 / 5→4 / 6→5
     */
    public static void migrateSkinTag() {
        if (Hawk.get("skin_tag_renumber_v1", false)) return;
        if (Hawk.contains(HawkConfig.SKIN_TAG)) {
            int old = Hawk.get(HawkConfig.SKIN_TAG, 0);
            int mapped;
            switch (old) {
                case 1: mapped = 5; break;
                case 2: mapped = 1; break;
                case 3: mapped = 2; break;
                case 4: mapped = 3; break;
                case 5: mapped = 4; break;
                case 6: mapped = 5; break;
                default: mapped = old;
            }
            Hawk.put(HawkConfig.SKIN_TAG, mapped);
        }
        Hawk.put("skin_tag_renumber_v1", true);
    }

    /**
     * 当前主题方案对应的主题资源, 0 表示沿用默认 AppTheme
     */
    public static int getSkinThemeRes() {
        switch (getSkinTag()) {
            case 1:
                return R.style.SkinGold;
            case 2:
                return R.style.SkinTech;
            case 3:
                return R.style.SkinAurora;
            case 4:
                return R.style.SkinRetro;
            case 5:
                return R.style.SkinLiquidGlass;
            case 6:
                return R.style.SkinPink;
            case 7:
                return R.style.SkinMist;
            case 8:
                return R.style.SkinForest;
            case 9:
                return R.style.SkinCream;
            default:
                return 0;
        }
    }
}