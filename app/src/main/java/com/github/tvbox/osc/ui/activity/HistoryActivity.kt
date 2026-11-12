package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
 * 记录（历史 + 收藏）页面
 *
 * 1. 顶部 Tab【历史｜收藏】左右并排（与订阅管理页同款样式），切换查看历史 / 收藏
 * 2. 卡片 UI 保持不变：左封面 + 中间标题信息 + 右侧删除按钮
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

    /** 0 = 历史，1 = 收藏 */
    private var currentTab = 0

    /** 历史记录原始数据 */
    private val historyList = ArrayList<VodInfo>()

    /** 收藏原始数据 */
    private val collectList = ArrayList<VodCollect>()

    private lateinit var recordAdapter: HistoryRecordAdapter

    /** 首次进入由 init() 加载，后续 onResume 再刷新，避免重复加载 */
    private var firstResume = true

    override fun init() {
        initList()
        initTabLayout()
        initClearAction()
        loadAllData()
        BottomNavHelper.attach(this, findViewById(R.id.bottom_nav), R.id.navigation_history)
    }

    /** 列表初始化：单 RecyclerView + 单适配器，Tab 切换只换数据 */
    private fun initList() {
        setLoadSir(mBinding.mGridView)
        mBinding.mGridView.layoutManager = LinearLayoutManager(this)
        mBinding.mGridView.setHasFixedSize(false)
        recordAdapter = HistoryRecordAdapter(
            onClick = { view, entry ->
                FastClickCheckUtil.check(view)
                openEntry(entry)
            },
            onDelete = { entry -> deleteEntry(entry) },
            onLongClick = { entry -> deleteEntry(entry) }
        )
        mBinding.mGridView.adapter = recordAdapter
    }

    // ========== 顶部 Tab（样式与订阅管理页一致） ==========
    private fun initTabLayout() {
        selectTab(0)
        mBinding.tabHistory.setOnClickListener { selectTab(0) }
        mBinding.tabCollect.setOnClickListener { selectTab(1) }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        val selectedTextColor = attrColor(R.attr.skin_accent)
        val unselectedTextColor = attrColor(R.attr.skin_text_secondary)
        if (index == 0) {
            mBinding.tabHistoryText.setTextColor(selectedTextColor)
            mBinding.tabHistoryText.setTypeface(null, android.graphics.Typeface.BOLD)
            mBinding.tabHistoryIndicator.alpha = 1f
            mBinding.tabCollectText.setTextColor(unselectedTextColor)
            mBinding.tabCollectText.setTypeface(null, android.graphics.Typeface.NORMAL)
            mBinding.tabCollectIndicator.alpha = 0f
        } else {
            mBinding.tabCollectText.setTextColor(selectedTextColor)
            mBinding.tabCollectText.setTypeface(null, android.graphics.Typeface.BOLD)
            mBinding.tabCollectIndicator.alpha = 1f
            mBinding.tabHistoryText.setTextColor(unselectedTextColor)
            mBinding.tabHistoryText.setTypeface(null, android.graphics.Typeface.NORMAL)
            mBinding.tabHistoryIndicator.alpha = 0f
        }
        rebuildList()
    }

    /** 读取当前皮肤主题里的颜色 token */
    private fun attrColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
    }

    /** 右上角刷子：清空当前 Tab 的数据（历史 / 收藏） */
    private fun initClearAction() {
        mBinding.ivDelete.setOnClickListener {
            val isHistory = currentTab == 0
            val hasData = if (isHistory) historyList.isNotEmpty() else collectList.isNotEmpty()
            if (!hasData) return@setOnClickListener
            val tip = if (isHistory) "确定清空全部历史记录？" else "确定清空全部收藏？"
            XPopup.Builder(this)
                .isDarkTheme(Utils.isDarkTheme())
                .asConfirm("提示", tip) {
                    clearCurrentTab(isHistory)
                }.show()
        }
    }

    /**
     * 历史、收藏统一在同一次 IO 读取中加载，并在主线程渲染，
     * 模拟器 / 手机端行为一致（修复手机端只显示历史不显示收藏）。
     */
    private fun loadAllData() {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                // 历史：保留原 playNote -> note 的转换逻辑；单个查询失败不影响另一个
                val history = try {
                    val list = ArrayList<VodInfo>(RoomDataManger.getAllVodRecord(100))
                    list.forEach { vod ->
                        if (!vod.playNote.isNullOrEmpty()) vod.note = vod.playNote
                    }
                    list
                } catch (t: Throwable) {
                    ArrayList<VodInfo>()
                }
                val collect = try {
                    ArrayList<VodCollect>(RoomDataManger.getAllVodCollect())
                } catch (t: Throwable) {
                    ArrayList<VodCollect>()
                }
                Pair(history, collect)
            }
            historyList.clear()
            historyList.addAll(data.first)
            collectList.clear()
            collectList.addAll(data.second)
            rebuildList()
        }
    }

    /** 只渲染当前 Tab 的卡片；当前 Tab 无数据时显示空占位 */
    private fun rebuildList() {
        val entries = ArrayList<RecordEntry>()
        if (currentTab == 0) {
            historyList.forEach { entries.add(RecordEntry.History(it)) }
        } else {
            collectList.forEach { entries.add(RecordEntry.Collect(it)) }
        }
        recordAdapter.submit(entries)
        refreshState()
    }

    /** 有数据 -> 展示长按提示；无数据 -> LoadSir「暂无数据」占位（与订阅页一致） */
    private fun refreshState() {
        val hasData = if (currentTab == 0) historyList.isNotEmpty() else collectList.isNotEmpty()
        if (!hasData) {
            mBinding.topTip.visibility = View.GONE
            showEmpty()
        } else {
            mBinding.topTip.visibility = View.VISIBLE
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
        }
    }

    /** 单条删除：同步移除内存数据 + 异步删除数据库（右侧按钮 / 长按共用） */
    private fun deleteEntry(entry: RecordEntry) {
        when (entry) {
            is RecordEntry.History -> {
                val vod = entry.vod
                historyList.removeAll { it.sourceKey == vod.sourceKey && it.id == vod.id }
                RoomDataManger.deleteVodRecord(vod.sourceKey, vod)
            }

            is RecordEntry.Collect -> {
                val vod = entry.vod
                collectList.removeAll { it.id == vod.id }
                RoomDataManger.deleteVodCollect(vod.id)
            }
        }
        rebuildList()
    }

    /** 清空当前 Tab：isHistory=true 清历史，false 清收藏 */
    private fun clearCurrentTab(isHistory: Boolean) {
        showLoadingDialog()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (isHistory) {
                    RoomDataManger.deleteVodRecordAll()
                } else {
                    RoomDataManger.deleteVodCollectAll()
                }
            }
            if (isHistory) historyList.clear() else collectList.clear()
            rebuildList()
            dismissLoadingDialog()
        }
    }

    /** 详情页收藏状态变化时刷新列表 */
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
