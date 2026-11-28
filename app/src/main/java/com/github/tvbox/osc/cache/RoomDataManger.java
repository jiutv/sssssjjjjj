package com.github.tvbox.osc.cache;

import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.data.AppDataManager;
import com.google.gson.ExclusionStrategy;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.MD5;
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
                // 删历史记录 = 删这部片的所有进度缓存，避免"记录没了进度还在"
                clearProgress(sourceKey, vodInfo.id, vodInfo);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    /**
     * 记录当前播放进度到历史记录里（跟随记录一起删除）。
     * 只更新已存在的记录：没看过（没有历史）就不主动创建。
     */
    public static void saveVodProgress(VodInfo vodInfo) {
        if (vodInfo == null || TextUtils.isEmpty(vodInfo.id) || vodInfo.historyProgress <= 0) return;
        String sourceKey = vodInfo.sourceKey;
        WRITE_EXECUTOR.execute(() -> {
            try {
                VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodInfo.id);
                if (record == null) return;
                record.dataJson = getVodInfoGson().toJson(vodInfo);
                record.updateTime = System.currentTimeMillis();
                AppDataManager.get().getVodRecordDao().insert(record);
                rememberProgressKey(vodInfo);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    // ===== 进度清理索引：progress_index_<sourceKey|vodId> = 该片看过的所有集的进度 key =====
    private static final String PROGRESS_INDEX_PREFIX = "progress_index_";
    /** 每部片最多记多少集，避免索引无限增长 */
    private static final int MAX_PROGRESS_EPS = 300;

    private static String progressIndexKey(String sourceKey, String vodId) {
        return PROGRESS_INDEX_PREFIX + sourceKey + "|" + vodId;
    }

    /** 某一集的进度缓存 key（必须和 PlayFragment 起播时用的 key 完全一致） */
    private static String progressCacheKey(VodInfo vodInfo) {
        return vodInfo.sourceKey + vodInfo.id + vodInfo.playFlag + vodInfo.playIndex + vodInfo.playNote;
    }

    /** 把看过的集记进该片的进度索引 */
    private static void rememberProgressKey(VodInfo vodInfo) {
        String indexKey = progressIndexKey(vodInfo.sourceKey, vodInfo.id);
        List<String> keys = Hawk.get(indexKey, new ArrayList<String>());
        String key = progressCacheKey(vodInfo);
        if (keys.contains(key)) return;
        keys.add(key);
        while (keys.size() > MAX_PROGRESS_EPS) {
            keys.remove(0);
        }
        Hawk.put(indexKey, keys);
    }

    /** 清掉某部片所有集的进度缓存 */
    private static void clearProgress(String sourceKey, String vodId, VodInfo fallback) {
        String indexKey = progressIndexKey(sourceKey, vodId);
        List<String> keys = Hawk.get(indexKey, new ArrayList<String>());
        if (fallback != null) {
            // 兼容没有索引的老记录：至少把记录里那一集清掉
            String key = progressCacheKey(fallback);
            if (!keys.contains(key)) keys.add(key);
        }
        for (String key : keys) {
            CacheManager.delete(MD5.string2MD5(key), 0);
        }
        Hawk.delete(indexKey);
    }

    public static List<VodInfo> getAllVodRecord(int limit) {
        try {
            int count = AppDataManager.get().getVodRecordDao().getCount();
            Integer index = Hawk.get(HawkConfig.HISTORY_NUM, 0);
            Integer hisNum = HistoryHelper.getHisNum(index);
            if (count > hisNum) {
                // 被裁掉的老记录也要把进度一起清掉，否则进度会变成永远不会删的孤儿数据
                List<VodRecord> all = AppDataManager.get().getVodRecordDao().getAll(Integer.MAX_VALUE);
                if (all != null) {
                    for (int i = hisNum; i < all.size(); i++) {
                        VodRecord old = all.get(i);
                        clearProgress(old.sourceKey, old.vodId, null);
                    }
                }
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
                            // 回填最后观看时间，供记录页展示「N分钟前 / N天前」
                            info.historyTime = record.updateTime;
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
     * 删除全部历史记录（顺带清掉每条记录对应的进度缓存）
     */
    public static void deleteVodRecordAll() {
        WRITE_EXECUTOR.execute(() -> {
            try {
                List<VodRecord> records = AppDataManager.get().getVodRecordDao().getAll(Integer.MAX_VALUE);
                if (records != null) {
                    for (VodRecord record : records) {
                        try {
                            clearProgress(record.sourceKey, record.vodId, null);
                        } catch (Throwable ignored) {
                        }
                    }
                }
                AppDataManager.get().getVodRecordDao().deleteAll();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

}
