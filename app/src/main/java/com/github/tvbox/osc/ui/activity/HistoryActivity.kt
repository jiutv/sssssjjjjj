package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.angcyo.tablayout.DslTabLayout
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.cache.VodCollect
import com.github.tvbox.osc.callback.EmptyCallback
import com.github.tvbox.osc.databinding.ActivityHistoryBinding
import com.github.tvbox.osc.ui.adapter.CollectAdapter
import com.github.tvbox.osc.ui.adapter.HistoryAdapter
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.Utils
import com.kingja.loadsir.callback.Callback
import com.kingja.loadsir.core.LoadService
import com.kingja.loadsir.core.LoadSir
import com.lxj.xpopup.XPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : BaseVbActivity<ActivityHistoryBinding>() {

    private var historyAdapter: HistoryAdapter? = null
    private val collectAdapter = CollectAdapter()
    private var collectLoadService: LoadService<*>? = null
    private var currentTab = 0 // 0=历史 1=收藏

    override fun init() {
        initTabLayout()
        initHistory()
        initCollect()
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
        mBinding.ivDelete.setOnClickListener {
            if (currentTab == 0) {
                clearHistory()
            } else {
                clearCollect()
            }
        }
        switchTab(0)
    }

    private fun switchTab(index: Int) {
        currentTab = index
        if (index == 0) {
            mBinding.mGridView.visibility = View.VISIBLE
            mBinding.collectContainer.visibility = View.GONE
            mBinding.topTip.visibility =
                if (historyAdapter != null && historyAdapter!!.data.isNotEmpty()) View.VISIBLE else View.GONE
        } else {
            mBinding.mGridView.visibility = View.GONE
            mBinding.collectContainer.visibility = View.VISIBLE
            mBinding.topTipCollect.visibility =
                if (collectAdapter.data.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ========== 历史 Tab (原有逻辑完整保留) ==========
    private fun initHistory() {
        setLoadSir(mBinding.mGridView)

        mBinding.mGridView.setHasFixedSize(true)
        mBinding.mGridView.setLayoutManager(GridLayoutManager(this, 3))
        historyAdapter = HistoryAdapter()
        mBinding.mGridView.setAdapter(historyAdapter)

        historyAdapter!!.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { _: BaseQuickAdapter<*, *>?, view: View?, position: Int ->
                FastClickCheckUtil.check(view)
                val vodInfo = historyAdapter!!.data[position]
                historyAdapter!!.remove(position)
                RoomDataManger.deleteVodRecord(vodInfo.sourceKey, vodInfo)
                if (historyAdapter!!.data.isEmpty()) {
                    mBinding.topTip.visibility = View.GONE
                }
                true
            }

        historyAdapter!!.onItemClickListener =
            BaseQuickAdapter.OnItemClickListener { _: BaseQuickAdapter<*, *>?, view: View?, position: Int ->
                FastClickCheckUtil.check(view)
                val vodInfo = historyAdapter!!.data[position]
                val bundle = Bundle()
                bundle.putString("id", vodInfo.id)
                bundle.putString("sourceKey", vodInfo.sourceKey)
                jumpActivity(DetailActivity::class.java, bundle)
            }

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
                    mBinding.topTip.visibility = View.VISIBLE
                } else {
                    showEmpty()
                }
            }
        }

        mBinding.tabLayout.configTabLayoutConfig {
            onSelectViewChange = { _, selectViewList, _, _ ->
                val selectedTab = (selectViewList.first() as? TextView)?.text?.toString()
                switchTab(if (selectedTab == "收藏") 1 else 0)
            }
        }
    }

    // ========== 清空历史 (原有逻辑) ==========
    private fun clearHistory() {
        XPopup.Builder(this)
            .isDarkTheme(Utils.isDarkTheme())
            .asConfirm("提示", "确定清空?") {
                showLoadingDialog()
                lifecycleScope.launch(Dispatchers.IO) {
                    RoomDataManger.deleteVodRecordAll()
                    withContext(Dispatchers.Main) {
                        dismissLoadingDialog()
                        historyAdapter!!.setNewData(ArrayList())
                        mBinding.topTip.visibility = View.GONE
                        showEmpty()
                    }
                }
            }.show()
    }

    // ========== 收藏 Tab (CollectActivity 逻辑完整照搬) ==========
    private fun initCollect() {
        mBinding.mGridViewCollect.setHasFixedSize(true)
        mBinding.mGridViewCollect.setLayoutManager(GridLayoutManager(this, 3))
        mBinding.mGridViewCollect.setAdapter(collectAdapter)

        // 收藏的 LoadSir —— 手动注册第二个
        collectLoadService = LoadSir.getDefault().register(
            mBinding.collectContainer,
            Callback.OnReloadListener { }
        )

        collectAdapter.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { adapter: BaseQuickAdapter<*, *>?, view: View?, position: Int ->
                val vodInfo = collectAdapter.data[position]
                if (vodInfo != null) {
                    collectAdapter.remove(position)
                    RoomDataManger.deleteVodCollect(vodInfo.id)
                }
                if (collectAdapter.data.isEmpty()) {
                    mBinding.topTipCollect.visibility = View.GONE
                }
                true
            }

        collectAdapter.onItemClickListener =
            BaseQuickAdapter.OnItemClickListener { adapter: BaseQuickAdapter<*, *>?, view: View?, position: Int ->
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

        lifecycleScope.launch(Dispatchers.IO) {
            val allVodRecord = RoomDataManger.getAllVodCollect()
            val vodInfoList: MutableList<VodCollect> = ArrayList()
            for (vodInfo in allVodRecord) {
                vodInfoList.add(vodInfo)
            }
            withContext(Dispatchers.Main) {
                collectAdapter.setNewData(vodInfoList)
                if (vodInfoList.isNotEmpty()) {
                    collectLoadService!!.showSuccess()
                    mBinding.topTipCollect.visibility = View.VISIBLE
                } else {
                    collectLoadService!!.showCallback(EmptyCallback::class.java)
                }
            }
        }
    }

    // ========== 清空收藏 (原有逻辑) ==========
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
                        mBinding.topTipCollect.visibility = View.GONE
                        collectLoadService?.showCallback(EmptyCallback::class.java)
                    }
                }
            }.show()
    }
}
