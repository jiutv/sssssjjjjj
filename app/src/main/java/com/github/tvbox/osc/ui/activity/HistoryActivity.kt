package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.cache.VodCollect
import com.github.tvbox.osc.databinding.ActivityHistoryBinding
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.adapter.HistoryRecordAdapter
import com.github.tvbox.osc.ui.adapter.RecordEntry
import com.github.tvbox.osc.util.BottomNavHelper
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.Utils
import com.lxj.xpopup.XPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * 需求1：记录（历史 + 收藏）页面改造
 *
 * 1. 移除原「历史 / 收藏」Tab，改为单列表 + 分组标题（历史记录 / 收藏）
 * 2. 卡片 UI 与订阅管理页一致：左封面 + 中间标题信息 + 右侧删除按钮
 * 3. 右侧删除按钮与长按整卡删除两种方式共存
 * 4. 历史、收藏在同一次统一读取中加载（修复部分手机端只显示历史、不显示收藏的问题）
 * 5. 空数据时显示与订阅页一致的 LoadSir「暂无数据」占位
 */
class HistoryActivity : BaseVbActivity<ActivityHistoryBinding>() {

    override fun vbInflate(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        attachToRoot: Boolean,
    ) = ActivityHistoryBinding.inflate(inflater, parent, attachToRoot)

    /** 历史记录原始数据（分组渲染 + 删除时共用） */
    private val historyList = ArrayList<VodInfo>()

    /** 收藏原始数据（分组渲染 + 删除时共用） */
    private val collectList = ArrayList<VodCollect>()

    private lateinit var recordAdapter: HistoryRecordAdapter

    /** 首次进入由 init() 加载，后续 onResume 再刷新，避免重复加载 */
    private var firstResume = true

    override fun init() {
        initList()
        initClearAction()
        loadAllData()
        BottomNavHelper.attach(this, mBinding.bottomNav, R.id.navigation_history)
    }

    /** 列表初始化：单 RecyclerView + 单适配器（不再切换 adapter，规避真机 Tab 切换失效问题） */
    private fun initList() {
        setLoadSir(mBinding.mGridView)
        mBinding.mGridView.layoutManager = LinearLayoutManager(this)
        mBinding.mGridView.setHasFixedSize(false)
        recordAdapter = HistoryRecordAdapter(
            // 点击卡片：跳转详情/播放（附带原播放进度由详情页读取）
            onClick = { view, entry ->
                FastClickCheckUtil.check(view)
                openEntry(entry)
            },
            // 右侧删除按钮：逐条删除
            onDelete = { entry -> deleteEntry(entry) },
            // 长按整卡：保留原有长按逐条删除功能，与按钮删除共存
            onLongClick = { entry -> deleteEntry(entry) }
        )
        mBinding.mGridView.adapter = recordAdapter
    }

    /** 右上角刷子：一键清空（历史 + 收藏） */
    private fun initClearAction() {
        mBinding.ivDelete.setOnClickListener {
            val hasHistory = historyList.isNotEmpty()
            val hasCollect = collectList.isNotEmpty()
            if (!hasHistory && !hasCollect) return@setOnClickListener
            val tip = when {
                hasHistory && hasCollect -> "确定清空全部历史记录和收藏？"
                hasHistory -> "确定清空全部历史记录？"
                else -> "确定清空全部收藏？"
            }
            XPopup.Builder(this)
                .isDarkTheme(Utils.isDarkTheme())
                .asConfirm("提示", tip) {
                    clearAll(hasHistory, hasCollect)
                }.show()
        }
    }

    /**
     * 需求1 修复：历史、收藏统一在同一次 IO 读取中加载，
     * 并在主线程渲染，模拟器 / 手机端行为一致。
     */
    private fun loadAllData() {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                // 历史：保留原 playNote -> note 的转换逻辑
                val history = ArrayList(RoomDataManger.getAllVodRecord(100))
                history.forEach { vod ->
                    if (!vod.playNote.isNullOrEmpty()) vod.note = vod.playNote
                }
                // 收藏：与历史同一批读取，保证两端数据都能拿到
                val collect = ArrayList(RoomDataManger.getAllVodCollect())
                Pair(history, collect)
            }
            historyList.clear()
            historyList.addAll(data.first)
            collectList.clear()
            collectList.addAll(data.second)
            rebuildList()
        }
    }

    /** 按分组重建列表：历史记录分组 + 收藏分组，两组都为空时显示空占位 */
    private fun rebuildList() {
        val entries = ArrayList<RecordEntry>()
        if (historyList.isNotEmpty()) {
            entries.add(RecordEntry.Group("历史记录", historyList.size))
            historyList.forEach { entries.add(RecordEntry.History(it)) }
        }
        if (collectList.isNotEmpty()) {
            entries.add(RecordEntry.Group("收藏", collectList.size))
            collectList.forEach { entries.add(RecordEntry.Collect(it)) }
        }
        recordAdapter.submit(entries)
        refreshState()
    }

    /** 有数据 -> 展示长按提示；无数据 -> LoadSir「暂无数据」占位（与订阅页一致） */
    private fun refreshState() {
        if (historyList.isEmpty() && collectList.isEmpty()) {
            mBinding.topTip.visibility = android.view.View.GONE
            showEmpty()
        } else {
            mBinding.topTip.visibility = android.view.View.VISIBLE
            mBinding.topTip.text = "长按条目可逐条删除"
            showSuccess()
        }
    }

    /** 卡片点击：历史 -> 详情；收藏 -> 详情（源失效时回退快速搜索，保留原逻辑） */
    private fun openEntry(entry: RecordEntry) {
        when (entry) {
            is RecordEntry.History -> {
                val vod = entry.vod
                val bundle = Bundle().apply {
                    putString("id", vod.id)
                    putString("sourceKey", vod.sourceKey)
                }
                jumpActivity(DetailActivity::class.java, bundle)
            }

            is RecordEntry.Collect -> {
                val vod = entry.vod
                if (ApiConfig.get().getSource(vod.sourceKey) != null) {
                    val bundle = Bundle().apply {
                        putString("id", vod.vodId)
                        putString("sourceKey", vod.sourceKey)
                    }
                    jumpActivity(DetailActivity::class.java, bundle)
                } else {
                    // 收藏的源已被删除：走快速搜索（与原 Collect 适配器逻辑一致）
                    val intent = Intent(this, FastSearchActivity::class.java)
                    intent.putExtra("title", vod.name)
                    startActivity(intent)
                }
            }

            else -> Unit
        }
    }

    /** 单条删除：同步移除内存数据 + 异步删除数据库（右侧按钮 / 长按共用） */
    private fun deleteEntry(entry: RecordEntry) {
        when (entry) {
            is RecordEntry.History -> {
                val vod = entry.vod
                historyList.removeAll { it.sourceKey == vod.sourceKey && it.id == vod.id }
                RoomDataManger.deleteVodRecord(vod.sourceKey, vod)
                rebuildList()
            }

            is RecordEntry.Collect -> {
                val vod = entry.vod
                collectList.removeAll { it.id == vod.id }
                RoomDataManger.deleteVodCollect(vod.id)
                rebuildList()
            }

            else -> Unit
        }
    }

    /** 一键清空：历史 + 收藏（按当前是否有数据分别清理） */
    private fun clearAll(clearHistory: Boolean, clearCollect: Boolean) {
        showLoadingDialog()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (clearHistory) RoomDataManger.deleteVodRecordAll()
                if (clearCollect) RoomDataManger.deleteVodCollectAll()
            }
            if (clearHistory) historyList.clear()
            if (clearCollect) collectList.clear()
            rebuildList()
            dismissLoadingDialog()
        }
    }

    /** 详情页收藏状态变化时刷新列表（避免返回记录页后收藏分组不更新） */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onHistoryRefresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH) loadAllData()
    }

    /** 从播放页返回时刷新历史进度 */
    override fun onResume() {
        super.onResume()
        if (firstResume) {
            firstResume = false
        } else {
            loadAllData()
        }
    }
}
