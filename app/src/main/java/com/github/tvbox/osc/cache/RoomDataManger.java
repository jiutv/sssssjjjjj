package com.github.tvbox.osc.cache;

import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.data.AppDataManager;
import com.google.gson.ExclusionStrategy;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import com.orhanobut.hawk.Hawk;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author pj567
 * @date :2021/1/7
 * @description:
 *
 * P0 fix: 所有 Room 写操作走后台线程；读操作保留同步（调用方可能在主线程，历史页面等数据回来再渲染），
 * 但 Room 默认 enforce 的主线程检查通过 allowMainThreadQueries 关闭了，所以直接调没问题。
 */
public class RoomDataManger {

    /** Room 后台写线程池，单线程保证写入顺序 */
    private static final ExecutorService WRITE_EXECUTOR = Executors.newSingleThreadExecutor();

    static ExclusionStrategy vodInfoStrategy = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesFlags")) {
                return true;
            }
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesMap")) {
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    };

    private static Gson getVodInfoGson() {
        return new GsonBuilder().addSerializationExclusionStrategy(vodInfoStrategy).create();
    }

    public static void insertVodRecord(String sourceKey, VodInfo vodInfo) {
        WRITE_EXECUTOR.execute(() -> {
            try {
                VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodInfo.id);
                if (record == null) {
                    record = new VodRecord();
                }
                record.sourceKey = sourceKey;
                record.vodId = vodInfo.id;
                record.updateTime = System.currentTimeMillis();
                record.dataJson = getVodInfoGson().toJson(vodInfo);
                AppDataManager.get().getVodRecordDao().insert(record);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public static VodInfo getVodInfo(String sourceKey, String vodId) {
        VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodId);
        try {
            if (record != null && record.dataJson != null && !TextUtils.isEmpty(record.dataJson)) {
                VodInfo vodInfo = getVodInfoGson().fromJson(record.dataJson, new TypeToken<VodInfo>() {
                }.getType());
                if (vodInfo.name == null)
                    return null;
                return vodInfo;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void deleteVodRecord(String sourceKey, VodInfo vodInfo) {
        WRITE_EXECUTOR.execute(() -> {
            try {
                VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodInfo.id);
                if (record != null) {
                    AppDataManager.get().getVodRecordDao().delete(record);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public static List<VodInfo> getAllVodRecord(int limit) {
        try {
            int count = AppDataManager.get().getVodRecordDao().getCount();
            Integer index = Hawk.get(HawkConfig.HISTORY_NUM, 0);
            Integer hisNum = HistoryHelper.getHisNum(index);
            if (count > hisNum) {
                AppDataManager.get().getVodRecordDao().reserver(hisNum);
            }
            List<VodRecord> recordList = AppDataManager.get().getVodRecordDao().getAll(limit);
            List<VodInfo> vodInfoList = new ArrayList<>();
            if (recordList != null) {
                for (VodRecord record : recordList) {
                    VodInfo info = null;
                    try {
                        if (record.dataJson != null && !TextUtils.isEmpty(record.dataJson)) {
                            info = getVodInfoGson().fromJson(record.dataJson, new TypeToken<VodInfo>() {
                            }.getType());
                            info.sourceKey = record.sourceKey;
                            SourceBean sourceBean = ApiConfig.get().getSource(info.sourceKey);
                            if (sourceBean == null || info.name == null)
                                info = null;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (info != null)
                        vodInfoList.add(info);
                }
            }
            return vodInfoList;
        } catch (Throwable t) {
            t.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void insertVodCollect(String sourceKey, VodInfo vodInfo) {
        WRITE_EXECUTOR.execute(() -> {
            try {
                VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id);
                if (record != null) {
                    return;
                }
                record = new VodCollect();
                record.sourceKey = sourceKey;
                record.vodId = vodInfo.id;
                record.updateTime = System.currentTimeMillis();
                record.name = vodInfo.name;
                record.pic = vodInfo.pic;
                AppDataManager.get().getVodCollectDao().insert(record);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public static void deleteVodCollect(int id) {
        WRITE_EXECUTOR.execute(() -> {
            try {
                AppDataManager.get().getVodCollectDao().delete(id);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public static void deleteVodCollect(String sourceKey, VodInfo vodInfo) {
        WRITE_EXECUTOR.execute(() -> {
            try {
                VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id);
                if (record != null) {
                    AppDataManager.get().getVodCollectDao().delete(record);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public static boolean isVodCollect(String sourceKey, String vodId) {
        VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodId);
        return record != null;
    }

    public static List<VodCollect> getAllVodCollect() {
        try {
            return AppDataManager.get().getVodCollectDao().getAll();
        } catch (Throwable t) {
            t.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 删除全部收藏
     */
    public static void deleteVodCollectAll() {
        WRITE_EXECUTOR.execute(() -> {
            try {
                AppDataManager.get().getVodCollectDao().deleteAll();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    /**
     * 删除全部历史记录
     */
    public static void deleteVodRecordAll() {
        WRITE_EXECUTOR.execute(() -> {
            try {
                AppDataManager.get().getVodRecordDao().deleteAll();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

}
