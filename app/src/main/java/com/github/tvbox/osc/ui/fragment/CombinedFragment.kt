package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseVbFragment
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.cache.VodCollect
import com.github.tvbox.osc.databinding.FragmentCombinedBinding
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.adapter.CollectAdapter
import com.github.tvbox.osc.ui.adapter.HistoryAdapter
import com.github.tvbox.osc.util.FastClickCheckUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CombinedFragment : BaseVbFragment<FragmentCombinedBinding>() {

    private val historyAdapter = HistoryAdapter()
    private val collectAdapter = CollectAdapter()
    private var currentTab = 0 // 0=历史, 1=收藏

    override fun init() {
        // RecyclerView
        mBinding.mGridView.layoutManager = GridLayoutManager(context, 3)
        mBinding.mGridView.adapter = historyAdapter

        // Tab 切换
        mBinding.tabHistory.setOnClickListener { switchTab(0) }
        mBinding.tabCollect.setOnClickListener { switchTab(1) }

        // 历史
        historyAdapter.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { _, view, position ->
                FastClickCheckUtil.check(view)
                val vodInfo = historyAdapter.data[position]
                historyAdapter.remove(position)
                RoomDataManger.deleteVodRecord(vodInfo.sourceKey, vodInfo)
                true
            }
        historyAdapter.onItemClickListener =
            BaseQuickAdapter.OnItemClickListener { _, view, position ->
                FastClickCheckUtil.check(view)
                val vodInfo = historyAdapter.data[position]
                val bundle = Bundle()
                bundle.putString("id", vodInfo.id)
                bundle.putString("sourceKey", vodInfo.sourceKey)
                jumpActivity(DetailActivity::class.java, bundle)
            }

        // 收藏
        collectAdapter.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { _, _, position ->
                val vodInfo = collectAdapter.data[position]
                if (vodInfo != null) {
                    collectAdapter.remove(position)
                    RoomDataManger.deleteVodCollect(vodInfo.id)
                }
                true
            }
        collectAdapter.onItemClickListener =
            BaseQuickAdapter.OnItemClickListener { _, view, position ->
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
                        newIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(newIntent)
                    }
                }
            }
    }

    private fun switchTab(index: Int) {
        if (index == currentTab) return
        currentTab = index
        val primary = ContextCompat.getColor(mContext, R.color.colorPrimary)
        val gray = ContextCompat.getColor(mContext, R.color.disable_gray)
        if (index == 0) {
            mBinding.tabHistory.setTextColor(primary)
            mBinding.tabCollect.setTextColor(gray)
            mBinding.mGridView.adapter = historyAdapter
        } else {
            mBinding.tabHistory.setTextColor(gray)
            mBinding.tabCollect.setTextColor(primary)
            mBinding.mGridView.adapter = collectAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allVodRecord = RoomDataManger.getAllVodRecord(100)
            val vodInfoList: MutableList<VodInfo> = ArrayList()
            for (vodInfo in allVodRecord) {
                if (vodInfo.playNote != null && vodInfo.playNote.isNotEmpty()) vodInfo.note =
                    vodInfo.playNote
                vodInfoList.add(vodInfo)
            }
            val allCollect = RoomDataManger.getAllVodCollect()
            val collectList: MutableList<VodCollect> = ArrayList()
            for (item in allCollect) {
                collectList.add(item)
            }
            withContext(Dispatchers.Main) {
                historyAdapter.setNewData(vodInfoList)
                collectAdapter.setNewData(collectList)
                mBinding.mGridView.adapter = if (currentTab == 0) historyAdapter else collectAdapter
            }
        }
    }
}
