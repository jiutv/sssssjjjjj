package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.angcyo.tablayout.DslTabLayout
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.cache.VodCollect
import com.github.tvbox.osc.databinding.ActivityHistoryBinding
import com.github.tvbox.osc.ui.adapter.CollectAdapter
import com.github.tvbox.osc.ui.adapter.HistoryAdapter
import com.github.tvbox.osc.util.BottomNavHelper
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.Utils
import com.lxj.xpopup.XPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : BaseVbActivity<ActivityHistoryBinding>() {

    private val historyAdapter = HistoryAdapter()
    private val collectAdapter = CollectAdapter()
    private var currentTab = 0 // 0=历史 1=收藏

    override fun init() {
        initTabLayout()
        initGridView()
        loadAllData()
        BottomNavHelper.attach(this, findViewById(R.id.bottom_nav), R.id.navigation_history)
    }

    // ========== TabLayout ==========
    private fun initTabLayout() {
        val lp = DslTabLayout.LayoutParams(-2, -2)
        lp.topMargin = 15
        lp.bottomMargin = 15

        val tab0 = TextView(this)
        tab0.text = "历史"
        tab0.gravity = Gravity.CENTER
        tab0.setPadding(20, 10, 20, 10)
        mBinding.tabLayout.addView(tab0, lp)

        val tab1 = TextView(this)
        tab1.text = "收藏"
        tab1.gravity = Gravity.CENTER
        tab1.setPadding(20, 10, 20, 10)
        mBinding.tabLayout.addView(tab1, lp)

        mBinding.tabLayout.setCurrentItem(0, true, false)

        mBinding.tabLayout.configTabLayoutConfig {
            onSelectViewChange = { _, selectViewList, _, _ ->
                val selectedTab = (selectViewList.first() as? TextView)?.text?.toString()
                switchTab(if (selectedTab == "收藏") 1 else 0)
            }
        }

        mBinding.ivDelete.setOnClickListener {
            if (currentTab == 0) clearHistory() else clearCollect()
        }
    }

    // ========== 共用的 RecyclerView (GridLayoutManager 3列) ==========
    private fun initGridView() {
        setLoadSir(mBinding.mGridView)
        mBinding.mGridView.setHasFixedSize(true)
        mBinding.mGridView.layoutManager = GridLayoutManager(this, 3)

        // 历史 adapter 点击/长按 (原 HistoryActivity 逻辑)
        historyAdapter.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { _: BaseQuickAdapter<*, *>?, view: View?, position: Int ->
                FastClickCheckUtil.check(view)
                val vodInfo = historyAdapter.data[position]
                historyAdapter.remove(position)
                RoomDataManger.deleteVodRecord(vodInfo.sourceKey, vodInfo)
                if (currentTab == 0) refreshTopTip()
                true
            }
        historyAdapter.onItemClickListener =
            BaseQuickAdapter.OnItemClickListener { _: BaseQuickAdapter<*, *>?, view: View?, position: Int ->
                FastClickCheckUtil.check(view)
                val vodInfo = historyAdapter.data[position]
                val bundle = Bundle()
                bundle.putString("id", vodInfo.id)
                bundle.putString("sourceKey", vodInfo.sourceKey)
                jumpActivity(DetailActivity::class.java, bundle)
            }

        // 收藏 adapter 点击/长按 (原 CollectActivity 逻辑)
        collectAdapter.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { _: BaseQuickAdapter<*, *>?, _: View?, position: Int ->
                val vodInfo = collectAdapter.data[position]
                if (vodInfo != null) {
                    collectAdapter.remove(position)
                    RoomDataManger.deleteVodCollect(vodInfo.id)
                }
                if (currentTab == 1) refreshTopTip()
                true
            }
        collectAdapter.onItemClickListener =
            BaseQuickAdapter.OnItemClickListener { _: BaseQuickAdapter<*, *>?, view: View?, position: Int ->
                FastClickCheckUtil.check(view)
                val vodInfo = collectAdapter.data[position]
                if (vodInfo != null) {
                    if (ApiConfig.get().getSource(vodInfo.sourceKey) != null) {
                        val bundle = Bundle()
                        bundle.putString("id", vodInfo.vodId)
                        bundle.putString("sourceKey", vodInfo.sourceKey)
                        jumpActivity(DetailActivity::class.java, bundle)
                    } else {
                        val newIntent = Intent(mContext, FastSearchActivity::class.java)
                        newIntent.putExtra("title", vodInfo.name)
                        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(newIntent)
                    }
                }
            }
    }

    // ========== 加载两个数据源 ==========
    private fun loadAllData() {
        lifecycleScope.launch(Dispatchers.IO) {
            // 并行加载历史
            val allHistory = RoomDataManger.getAllVodRecord(100)
            val historyList: MutableList<VodInfo> = ArrayList()
            for (vodInfo in allHistory) {
                if (vodInfo.playNote != null && vodInfo.playNote.isNotEmpty()) vodInfo.note = vodInfo.playNote
                historyList.add(vodInfo)
            }
            historyAdapter.setNewData(historyList)

            // 并行加载收藏
            val allCollect = RoomDataManger.getAllVodCollect()
            val collectList: MutableList<VodCollect> = ArrayList()
            for (vodInfo in allCollect) collectList.add(vodInfo)
            collectAdapter.setNewData(collectList)

            withContext(Dispatchers.Main) {
                switchTab(0) // 默认显示历史
            }
        }
    }

    // ========== Tab 切换: 只有一个 Grid, 只有一个 LoadSir ==========
    private fun switchTab(index: Int) {
        currentTab = index
        if (index == 0) {
            mBinding.mGridView.adapter = historyAdapter as RecyclerView.Adapter<*>
        } else {
            mBinding.mGridView.adapter = collectAdapter as RecyclerView.Adapter<*>
        }
        refreshTopTip()
        refreshEmptyState()
    }

    private fun refreshTopTip() {
        val hasData = if (currentTab == 0) historyAdapter.data.isNotEmpty() else collectAdapter.data.isNotEmpty()
        mBinding.topTip.visibility = if (hasData) View.VISIBLE else View.GONE
        mBinding.topTip.text = if (currentTab == 0) "长按记录逐条删除" else "长按取消收藏"
    }

    private fun refreshEmptyState() {
        val hasData = if (currentTab == 0) historyAdapter.data.isNotEmpty() else collectAdapter.data.isNotEmpty()
        if (hasData) showSuccess() else showEmpty()
    }

    // ========== 清空 ==========
    private fun clearHistory() {
        XPopup.Builder(this)
            .isDarkTheme(Utils.isDarkTheme())
            .asConfirm("提示", "确定清空?") {
                showLoadingDialog()
                lifecycleScope.launch(Dispatchers.IO) {
                    RoomDataManger.deleteVodRecordAll()
                    withContext(Dispatchers.Main) {
                        dismissLoadingDialog()
                        historyAdapter.setNewData(ArrayList())
                        refreshTopTip()
                        refreshEmptyState()
                    }
                }
            }.show()
    }

    private fun clearCollect() {
        XPopup.Builder(this)
            .isDarkTheme(Utils.isDarkTheme())
            .asConfirm("提示", "确定清空?") {
                showLoadingDialog()
                lifecycleScope.launch(Dispatchers.IO) {
                    RoomDataManger.deleteVodCollectAll()
                    withContext(Dispatchers.Main) {
                        dismissLoadingDialog()
                        collectAdapter.setNewData(ArrayList())
                        refreshTopTip()
                        refreshEmptyState()
                    }
                }
            }.show()
    }
}
