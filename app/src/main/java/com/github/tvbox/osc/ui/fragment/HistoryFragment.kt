package com.github.tvbox.osc.ui.fragment

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.base.BaseVbFragment
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.databinding.FragmentHistoryBinding
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.adapter.HistoryAdapter
import com.github.tvbox.osc.util.FastClickCheckUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryFragment : BaseVbFragment<FragmentHistoryBinding>() {

    private var historyAdapter: HistoryAdapter? = null

    override fun init() {
        setLoadSir(mBinding.mGridView)
        mBinding.mGridView.setHasFixedSize(true)
        mBinding.mGridView.layoutManager = GridLayoutManager(context, 3)
        historyAdapter = HistoryAdapter()
        mBinding.mGridView.adapter = historyAdapter

        historyAdapter!!.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { _, view, position ->
                FastClickCheckUtil.check(view)
                val vodInfo = historyAdapter!!.data[position]
                historyAdapter!!.remove(position)
                RoomDataManger.deleteVodRecord(vodInfo.sourceKey, vodInfo)
                true
            }

        historyAdapter!!.onItemClickListener =
            BaseQuickAdapter.OnItemClickListener { _, view, position ->
                FastClickCheckUtil.check(view)
                val vodInfo = historyAdapter!!.data[position]
                val bundle = Bundle()
                bundle.putString("id", vodInfo.id)
                bundle.putString("sourceKey", vodInfo.sourceKey)
                jumpActivity(DetailActivity::class.java, bundle)
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
            withContext(Dispatchers.Main) {
                historyAdapter!!.setNewData(vodInfoList)
                if (vodInfoList.isNotEmpty()) {
                    showSuccess()
                } else {
                    showEmpty()
                }
            }
        }
    }
}
