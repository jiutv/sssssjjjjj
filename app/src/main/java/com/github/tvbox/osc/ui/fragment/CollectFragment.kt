package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseVbFragment
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.cache.VodCollect
import com.github.tvbox.osc.databinding.FragmentCollectBinding
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.adapter.CollectAdapter
import com.github.tvbox.osc.util.FastClickCheckUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CollectFragment : BaseVbFragment<FragmentCollectBinding>() {

    private val collectAdapter = CollectAdapter()

    override fun init() {
        setLoadSir(mBinding.mGridView)
        mBinding.mGridView.setHasFixedSize(true)
        mBinding.mGridView.layoutManager = GridLayoutManager(context, 3)
        mBinding.mGridView.adapter = collectAdapter

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

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allVodRecord = RoomDataManger.getAllVodCollect()
            val vodInfoList: MutableList<VodCollect> = ArrayList()
            for (vodInfo in allVodRecord) {
                vodInfoList.add(vodInfo)
            }
            withContext(Dispatchers.Main) {
                collectAdapter.setNewData(vodInfoList)
                if (vodInfoList.isNotEmpty()) {
                    showSuccess()
                } else {
                    showEmpty()
                }
            }
        }
    }
}
