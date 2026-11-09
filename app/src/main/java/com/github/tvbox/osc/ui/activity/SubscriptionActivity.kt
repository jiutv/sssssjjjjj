package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.angcyo.tablayout.DslTabLayout
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.bean.Source
import com.github.tvbox.osc.bean.Subscription
import com.github.tvbox.osc.databinding.ActivitySubscriptionBinding
import com.github.tvbox.osc.ui.adapter.SubscriptionAdapter
import com.github.tvbox.osc.ui.dialog.ChooseSourceDialog
import com.github.tvbox.osc.ui.dialog.SubsTipDialog
import com.github.tvbox.osc.ui.dialog.SubsciptionDialog
import com.github.tvbox.osc.ui.dialog.SubsciptionDialog.OnSubsciptionListener
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.Utils
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.lxj.xpopup.XPopup
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
import com.obsez.android.lib.filechooser.ChooserDialog
import com.orhanobut.hawk.Hawk
import org.apache.commons.lang3.StringUtils
import java.util.function.Consumer

class SubscriptionActivity : BaseVbActivity<ActivitySubscriptionBinding>() {

    private var mBeforeUrl = Hawk.get(HawkConfig.API_URL, "")
    private var mSelectedUrl = ""
    private var mSubscriptions: MutableList<Subscription> = Hawk.get(HawkConfig.SUBSCRIPTIONS, ArrayList())
    private var mSubscriptionAdapter = SubscriptionAdapter()
    private val mSources: MutableList<Source> = ArrayList()

    private var mBeforeLiveUrl = Hawk.get(HawkConfig.LIVE_URL, "")
    private var mSelectedLiveUrl = ""
    private var mLiveSubscriptions: MutableList<Subscription> = Hawk.get(HawkConfig.LIVE_SUBSCRIPTIONS, ArrayList())
    private val mLiveSubscriptionAdapter = SubscriptionAdapter()

    private var currentTab = 0

    override fun init() {

        // ========== TabLayout ==========
        initTabLayout()

        // ========== 配置订阅 (原有逻辑不变) ==========
        mBinding.rv.setAdapter(mSubscriptionAdapter)
        mBinding.rv.layoutManager = LinearLayoutManager(this)
        mSubscriptions.forEach(Consumer { item: Subscription ->
            if (item.isChecked) {
                mSelectedUrl = item.url
            }
        })

        mSubscriptionAdapter.setNewData(mSubscriptions)
        mBinding.ivUseTip.setOnClickListener {
            XPopup.Builder(this)
                .asCustom(SubsTipDialog(this))
                .show()
        }

        // ========== 直播订阅 ==========
        mBinding.rvLive.setAdapter(mLiveSubscriptionAdapter)
        mBinding.rvLive.layoutManager = LinearLayoutManager(this)
        mLiveSubscriptions.forEach(Consumer { item: Subscription ->
            if (item.isChecked) {
                mSelectedLiveUrl = item.url
            }
        })
        // 如果没有选中项但有数据,默认选中第一个的url作为当前直播源
        if (TextUtils.isEmpty(mSelectedLiveUrl) && mLiveSubscriptions.isNotEmpty()) {
            mSelectedLiveUrl = mLiveSubscriptions[0].url
        }
        mLiveSubscriptionAdapter.setNewData(mLiveSubscriptions)

        // 右上角加号 —— 根据当前 tab 做不同事
        mBinding.titleBar.rightView.setOnClickListener {
            if (currentTab == 0) {
                // 配置订阅: 添加点播订阅源 (原有逻辑)
                addVodSubscription()
            } else {
                // 直播订阅: 添加直播源
                addLiveSubscription()
            }
        }

        // ========== 配置订阅列表点击/删除/长按 (原有逻辑不变) ==========
        mSubscriptionAdapter.setOnItemChildClickListener { _: BaseQuickAdapter<*, *>?, view: View, position: Int ->
            LogUtils.d("删除订阅")
            if (view.id == R.id.iv_del) {
                if (mSubscriptions.get(position).isChecked) {
                    ToastUtils.showShort("不能删除当前使用的订阅")
                    return@setOnItemChildClickListener
                }
                XPopup.Builder(this@SubscriptionActivity)
                    .asConfirm("删除订阅", "确定删除订阅吗？") {
                        mSubscriptions.removeAt(position)
                        mSubscriptionAdapter.notifyDataSetChanged()
                    }.show()
            }
        }

        mSubscriptionAdapter.setOnItemClickListener { _: BaseQuickAdapter<*, *>?, _: View?, position: Int ->
            val clicked = mSubscriptions[position]
            if (clicked.isDisabled) {
                clicked.setDisabled(false)
            }
            for (i in mSubscriptions.indices) {
                val subscription = mSubscriptions[i]
                if (i == position) {
                    subscription.setChecked(true)
                    mSelectedUrl = subscription.url
                } else {
                    subscription.setChecked(false)
                }
            }
            mSubscriptionAdapter.notifyDataSetChanged()
        }

        mSubscriptionAdapter.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { _: BaseQuickAdapter<*, *>?, view: View, position: Int ->
                val item = mSubscriptions[position]
                showLongClickMenu(item, position, isLive = false, anchorView = view.findViewById(R.id.tv_name))
                true
            }

        // ========== 直播订阅列表点击/删除/长按 ==========
        mLiveSubscriptionAdapter.setOnItemChildClickListener { _: BaseQuickAdapter<*, *>?, view: View, position: Int ->
            if (view.id == R.id.iv_del) {
                if (mLiveSubscriptions[position].isChecked) {
                    ToastUtils.showShort("不能删除当前使用的直播源")
                    return@setOnItemChildClickListener
                }
                XPopup.Builder(this@SubscriptionActivity)
                    .asConfirm("删除直播源", "确定删除该直播源吗？") {
                        mLiveSubscriptions.removeAt(position)
                        mLiveSubscriptionAdapter.notifyDataSetChanged()
                    }.show()
            }
        }

        mLiveSubscriptionAdapter.setOnItemClickListener { _: BaseQuickAdapter<*, *>?, _: View?, position: Int ->
            val clicked = mLiveSubscriptions[position]
            if (clicked.isDisabled) {
                clicked.setDisabled(false)
            }
            for (i in mLiveSubscriptions.indices) {
                val subscription = mLiveSubscriptions[i]
                if (i == position) {
                    subscription.setChecked(true)
                    mSelectedLiveUrl = subscription.url
                } else {
                    subscription.setChecked(false)
                }
            }
            mLiveSubscriptionAdapter.notifyDataSetChanged()
        }

        mLiveSubscriptionAdapter.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { _: BaseQuickAdapter<*, *>?, view: View, position: Int ->
                val item = mLiveSubscriptions[position]
                showLongClickMenu(item, position, isLive = true, anchorView = view.findViewById(R.id.tv_name))
                true
            }

        // ========== 自动从点播订阅源解析直播源 ==========
        tryLoadLiveFromVodSource()
    }

    // ========== Tab 初始化 ==========
    private fun initTabLayout() {
        selectTab(0)
        mBinding.tabVod.setOnClickListener { selectTab(0) }
        mBinding.tabLive.setOnClickListener { selectTab(1) }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        val selectedTextColor = resources.getColor(R.color.title_fouse_n, null)
        val unselectedTextColor = resources.getColor(R.color.gray_darker, null)
        if (index == 0) {
            // 配置订阅
            mBinding.tabVodText.setTextColor(selectedTextColor)
            mBinding.tabVodText.setTypeface(null, android.graphics.Typeface.BOLD)
            mBinding.tabVodIndicator.alpha = 1f
            mBinding.tabLiveText.setTextColor(unselectedTextColor)
            mBinding.tabLiveText.setTypeface(null, android.graphics.Typeface.NORMAL)
            mBinding.tabLiveIndicator.alpha = 0f
            mBinding.rv.visibility = View.VISIBLE
            mBinding.rvLive.visibility = View.GONE
            mBinding.ivUseTip.visibility = View.VISIBLE
        } else {
            // 直播订阅
            mBinding.tabLiveText.setTextColor(selectedTextColor)
            mBinding.tabLiveText.setTypeface(null, android.graphics.Typeface.BOLD)
            mBinding.tabLiveIndicator.alpha = 1f
            mBinding.tabVodText.setTextColor(unselectedTextColor)
            mBinding.tabVodText.setTypeface(null, android.graphics.Typeface.NORMAL)
            mBinding.tabVodIndicator.alpha = 0f
            mBinding.rv.visibility = View.GONE
            mBinding.rvLive.visibility = View.VISIBLE
            mBinding.ivUseTip.visibility = View.GONE
        }
    }

    // ========== 从当前点播订阅源自动提取直播源 ==========
    private fun tryLoadLiveFromVodSource() {
        if (TextUtils.isEmpty(mSelectedUrl)) return
        val apiUrl = mSelectedUrl
        val configUrl: String = when {
            apiUrl.startsWith("clan") -> apiUrl
            apiUrl.startsWith("http") -> apiUrl
            else -> "http://$apiUrl"
        }

        OkGo.get<String>(configUrl)
            .tag("load_live_from_vod")
            .execute(object : AbsCallback<String?>() {
                override fun convertResponse(response: okhttp3.Response): String? {
                    return response.body()?.string()
                }

                override fun onSuccess(response: Response<String?>) {
                    try {
                        val json = JsonParser.parseString(response.body()).asJsonObject
                        if (!json.has("lives")) return

                        val livesArray = json.getAsJsonArray("lives")
                        if (livesArray == null || livesArray.size() == 0) return

                        // 从 lives 数组里提取所有直播 URL
                        val liveUrls = mutableListOf<String>()
                        for (element in livesArray) {
                            try {
                                val livesObj = element.asJsonObject
                                // 格式1: proxy://...?ext=xxx (ext 是 base64 或 http/clan URL)
                                if (livesObj.has("proxy")) {
                                    val proxyUrl = livesObj.get("proxy").asString
                                    val index = proxyUrl.indexOf("proxy://")
                                    if (index != -1) {
                                        val endIndex = proxyUrl.lastIndexOf("\"")
                                        val url = if (endIndex != -1) proxyUrl.substring(index, endIndex) else proxyUrl.substring(index)
                                        val extUrl = Uri.parse(url).getQueryParameter("ext")
                                        if (!extUrl.isNullOrEmpty()) {
                                            if (extUrl.startsWith("http") || extUrl.startsWith("clan://")) {
                                                liveUrls.add(extUrl)
                                            } else {
                                                // base64 编码的 URL
                                                try {
                                                    val decoded = android.util.Base64.decode(extUrl, android.util.Base64.DEFAULT or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                                                        .toString(Charsets.UTF_8)
                                                    liveUrls.add(decoded)
                                                } catch (_: Throwable) {}
                                            }
                                        }
                                    }
                                }
                                // 格式2: FengMi type=0, 直接 url 字段
                                if (livesObj.has("type") && livesObj.get("type").asString == "0") {
                                    val url = livesObj.get("url").asString
                                    if (url.startsWith("http")) liveUrls.add(url)
                                }
                                // 格式3: 直接 lives 数组里就是 channels (FongMi 格式)
                                if (!livesObj.has("type") && livesObj.has("channels")) {
                                    // 这种是内置直播，不需要外部 URL
                                }
                            } catch (_: Throwable) {}
                        }

                        // 把解析到的直播 URL 加到列表里(已存在则跳过)
                        for (liveUrl in liveUrls) {
                            val exists = mLiveSubscriptions.any { it.url == liveUrl }
                            if (!exists) {
                                val name = "直播源: ${mLiveSubscriptions.size + 1}"
                                val sub = Subscription(name, liveUrl)
                                mLiveSubscriptions.add(sub)
                            }
                        }

                        // 自动选中第一个(如果当前没选中的)
                        if (TextUtils.isEmpty(mSelectedLiveUrl) && mLiveSubscriptions.isNotEmpty()) {
                            mLiveSubscriptions.firstOrNull { !it.isDisabled }?.let {
                                it.setChecked(true)
                                mSelectedLiveUrl = it.url
                            }
                        }
                        mLiveSubscriptionAdapter.setNewData(mLiveSubscriptions)
                    } catch (_: Throwable) {
                        // 解析失败不影响现有列表
                    }
                }

                override fun onError(response: Response<String?>) {
                    // 网络失败不影响，保留用户已添加的直播源
                }
            })
    }

    // ========== 添加点播订阅 (原有逻辑) ==========
    private fun addVodSubscription() {
        XPopup.Builder(this)
            .autoFocusEditText(false)
            .asCustom(
                SubsciptionDialog(
                    this,
                    "订阅: " + (mSubscriptions.size + 1),
                    object : OnSubsciptionListener {
                        override fun onConfirm(
                            name: String,
                            url: String,
                            checked: Boolean
                        ) {
                            for (item in mSubscriptions) {
                                if (item.url == url) {
                                    ToastUtils.showLong("订阅地址与" + item.name + "相同")
                                    return
                                }
                            }
                            addSubscription(name, url, checked)
                        }

                        override fun chooseLocal(checked: Boolean) {
                            if (!XXPermissions.isGranted(
                                    mContext,
                                    Permission.MANAGE_EXTERNAL_STORAGE
                                )
                            ) {
                                showPermissionTipPopup(checked)
                            } else {
                                pickFile(checked)
                            }
                        }
                    })
            ).show()
    }

    // ========== 添加直播源 ==========
    private fun addLiveSubscription() {
        XPopup.Builder(this)
            .autoFocusEditText(false)
            .asCustom(
                SubsciptionDialog(
                    this,
                    "直播源: " + (mLiveSubscriptions.size + 1),
                    object : OnSubsciptionListener {
                        override fun onConfirm(
                            name: String,
                            url: String,
                            checked: Boolean
                        ) {
                            for (item in mLiveSubscriptions) {
                                if (item.url == url) {
                                    ToastUtils.showLong("直播源地址与" + item.name + "相同")
                                    return
                                }
                            }
                            addLiveSubscription2List(name, url, checked)
                        }

                        override fun chooseLocal(checked: Boolean) {
                            if (!XXPermissions.isGranted(
                                    mContext,
                                    Permission.MANAGE_EXTERNAL_STORAGE
                                )
                            ) {
                                showLivePermissionTipPopup(checked)
                            } else {
                                pickLiveFile(checked)
                            }
                        }
                    })
            ).show()
    }

    private fun addLiveSubscription2List(name: String, url: String, checkNewest: Boolean) {
        if (checkNewest) {
            for (subscription in mLiveSubscriptions) {
                if (subscription.isChecked) {
                    subscription.setChecked(false)
                }
            }
            mSelectedLiveUrl = url
            mLiveSubscriptions.add(Subscription(name, url).setChecked(true))
        } else {
            mLiveSubscriptions.add(Subscription(name, url).setChecked(false))
        }
        mLiveSubscriptionAdapter.setNewData(mLiveSubscriptions)
    }

    // ========== 直播源本地文件导入 ==========
    private fun showLivePermissionTipPopup(checked: Boolean) {
        XPopup.Builder(this@SubscriptionActivity)
            .isDarkTheme(Utils.isDarkTheme())
            .asConfirm("提示", "这将访问您设备文件的读取权限") {
                XXPermissions.with(this)
                    .permission(Permission.MANAGE_EXTERNAL_STORAGE)
                    .request(object : OnPermissionCallback {
                        override fun onGranted(permissions: List<String>, all: Boolean) {
                            if (all) {
                                pickLiveFile(checked)
                            } else {
                                ToastUtils.showLong("部分权限未正常授予,请授权")
                            }
                        }

                        override fun onDenied(permissions: List<String>, never: Boolean) {
                            if (never) {
                                ToastUtils.showLong("读写文件权限被永久拒绝，请手动授权")
                                XXPermissions.startPermissionActivity(
                                    this@SubscriptionActivity,
                                    permissions
                                )
                            } else {
                                ToastUtils.showShort("获取权限失败")
                                showLivePermissionTipPopup(checked)
                            }
                        }
                    })
            }.show()
    }

    private fun pickLiveFile(checked: Boolean) {
        ChooserDialog(this@SubscriptionActivity, R.style.FileChooser)
            .withFilter(false, false, "txt", "json")
            .withStartFile(
                if (TextUtils.isEmpty(Hawk.get("before_live_selected_path"))) "/storage/emulated/0/Download" else Hawk.get(
                    "before_live_selected_path"
                )
            )
            .withChosenListener(ChooserDialog.Result { _, pathFile ->
                Hawk.put("before_live_selected_path", pathFile.parent)
                val clanPath =
                    pathFile.absolutePath.replace("/storage/emulated/0", "clan://localhost")
                for (item in mLiveSubscriptions) {
                    if (item.url == clanPath) {
                        ToastUtils.showLong("直播源地址与" + item.name + "相同")
                        return@Result
                    }
                }
                addLiveSubscription2List(pathFile.name, clanPath, checked)
            })
            .build()
            .show()
    }

    // ========== 长按菜单 (配置订阅 / 直播源 通用) ==========
    private fun showLongClickMenu(
        item: Subscription,
        position: Int,
        isLive: Boolean,
        anchorView: View
    ) {
        val adapter = if (isLive) mLiveSubscriptionAdapter else mSubscriptionAdapter
        val list = if (isLive) mLiveSubscriptions else mSubscriptions

        val menuItems = arrayOf(
            if (item.isTop) "取消置顶" else "置顶",
            "重命名",
            "复制地址",
            if (item.isDisabled) "启用源" else "停用源"
        )

        XPopup.Builder(this)
            .atView(anchorView)
            .hasShadowBg(false)
            .asAttachList(menuItems, null) { index: Int, _: String? ->
                when (index) {
                    0 -> {
                        item.isTop = !item.isTop
                        list[position] = item
                        adapter.setNewData(list)
                    }
                    1 -> {
                        XPopup.Builder(this)
                            .asInputConfirm(
                                "更改为",
                                "",
                                item.name,
                                "新的名称",
                                { text ->
                                    if (!TextUtils.isEmpty(text)) {
                                        if (text.trim { it <= ' ' }.length > 8) {
                                            ToastUtils.showShort("不要过长,不方便记忆")
                                        } else {
                                            item.name = text.trim { it <= ' ' }
                                            adapter.notifyItemChanged(position)
                                        }
                                    }
                                },
                                null,
                                R.layout.dialog_input
                            ).show()
                    }
                    2 -> {
                        ClipboardUtils.copyText(list[position].url)
                        ToastUtils.showLong("已复制")
                    }
                    3 -> {
                        // 停用/启用
                        if (!item.isDisabled && item.isChecked) {
                            ToastUtils.showShort("请先切换到其他订阅再停用")
                            return@asAttachList
                        }
                        item.setDisabled(!item.isDisabled)
                        if (item.isDisabled) {
                            item.setChecked(false)
                        }
                        list[position] = item
                        adapter.setNewData(list)
                    }
                }
            }.show()
    }

    // ========== 配置订阅原有方法 (完整保留) ==========

    private fun showPermissionTipPopup(checked: Boolean) {
        XPopup.Builder(this@SubscriptionActivity)
            .isDarkTheme(Utils.isDarkTheme())
            .asConfirm("提示", "这将访问您设备文件的读取权限") {
                XXPermissions.with(this)
                    .permission(Permission.MANAGE_EXTERNAL_STORAGE)
                    .request(object : OnPermissionCallback {
                        override fun onGranted(permissions: List<String>, all: Boolean) {
                            if (all) {
                                pickFile(checked)
                            } else {
                                ToastUtils.showLong("部分权限未正常授予,请授权")
                            }
                        }

                        override fun onDenied(permissions: List<String>, never: Boolean) {
                            if (never) {
                                ToastUtils.showLong("读写文件权限被永久拒绝，请手动授权")
                                XXPermissions.startPermissionActivity(
                                    this@SubscriptionActivity,
                                    permissions
                                )
                            } else {
                                ToastUtils.showShort("获取权限失败")
                                showPermissionTipPopup(checked)
                            }
                        }
                    })
            }.show()
    }

    private fun pickFile(checked: Boolean) {
        ChooserDialog(this@SubscriptionActivity, R.style.FileChooser)
            .withFilter(false, false, "txt", "json")
            .withStartFile(
                if (TextUtils.isEmpty(Hawk.get("before_selected_path"))) "/storage/emulated/0/Download" else Hawk.get(
                    "before_selected_path"
                )
            )
            .withChosenListener(ChooserDialog.Result { _, pathFile ->
                Hawk.put("before_selected_path", pathFile.parent)
                val clanPath =
                    pathFile.absolutePath.replace("/storage/emulated/0", "clan://localhost")
                for (item in mSubscriptions) {
                    if (item.url == clanPath) {
                        ToastUtils.showLong("订阅地址与" + item.name + "相同")
                        return@Result
                    }
                }
                addSubscription(pathFile.name, clanPath, checked)
            })
            .build()
            .show()
    }

    private fun addSubscription(name: String, url: String, checked: Boolean) {
        if (url.startsWith("clan://")) {
            addSub2List(name, url, checked)
            mSubscriptionAdapter.setNewData(mSubscriptions)
        } else if (url.startsWith("http")) {
            showLoadingDialog()
            OkGo.get<String>(url)
                .tag("get_subscription")
                .execute(object : AbsCallback<String?>() {
                    override fun onSuccess(response: Response<String?>) {
                        dismissLoadingDialog()
                        try {
                            val json = JsonParser.parseString(response.body()).asJsonObject
                            val urls = json["urls"]
                            val storeHouse = json["storeHouse"]
                            if (urls != null && urls.isJsonArray) {
                                if (checked) {
                                    ToastUtils.showLong("多条线路请主动选择")
                                }
                                val urlList = urls.asJsonArray
                                if (urlList != null && urlList.size() > 0 && urlList[0].isJsonObject
                                    && urlList[0].asJsonObject.has("url")
                                    && urlList[0].asJsonObject.has("name")
                                ) {
                                    for (i in 0 until urlList.size()) {
                                        val obj = urlList[i] as JsonObject
                                        val name = obj["name"].asString.trim { it <= ' ' }
                                            .replace("<|>|《|》|-".toRegex(), "")
                                        val url = obj["url"].asString.trim { it <= ' ' }
                                        mSubscriptions.add(Subscription(name, url))
                                    }
                                }
                            } else if (storeHouse != null && storeHouse.isJsonArray) {
                                val storeHouseList = storeHouse.asJsonArray
                                if (storeHouseList != null && storeHouseList.size() > 0 && storeHouseList[0].isJsonObject
                                    && storeHouseList[0].asJsonObject.has("sourceName")
                                    && storeHouseList[0].asJsonObject.has("sourceUrl")
                                ) {
                                    mSources.clear()
                                    for (i in 0 until storeHouseList.size()) {
                                        val obj = storeHouseList[i] as JsonObject
                                        val name = obj["sourceName"].asString.trim { it <= ' ' }
                                            .replace("<|>|《|》|-".toRegex(), "")
                                        val url = obj["sourceUrl"].asString.trim { it <= ' ' }
                                        mSources.add(Source(name, url))
                                    }
                                    XPopup.Builder(this@SubscriptionActivity)
                                        .asCustom(
                                            ChooseSourceDialog(
                                                this@SubscriptionActivity,
                                                mSources
                                            ) { position: Int, _: String? ->
                                                addSubscription(
                                                    mSources[position].sourceName,
                                                    mSources[position].sourceUrl,
                                                    checked
                                                )
                                            })
                                        .show()
                                }
                            } else {
                                addSub2List(name, url, checked)
                            }
                        } catch (th: Throwable) {
                            addSub2List(name, url, checked)
                        }
                        mSubscriptionAdapter.setNewData(mSubscriptions)
                    }

                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        return response.body()!!.string()
                    }

                    override fun onError(response: Response<String?>) {
                        super.onError(response)
                        dismissLoadingDialog()
                        ToastUtils.showLong("订阅失败,请检查地址或网络状态")
                    }
                })
        } else {
            ToastUtils.showShort("订阅格式不正确")
        }
    }

    private fun addSub2List(name: String, url: String, checkNewest: Boolean) {
        if (checkNewest) {
            for (subscription in mSubscriptions) {
                if (subscription.isChecked) {
                    subscription.setChecked(false)
                }
            }
            mSelectedUrl = url
            mSubscriptions.add(Subscription(name, url).setChecked(true))
        } else {
            mSubscriptions.add(Subscription(name, url).setChecked(false))
        }
    }

    override fun onPause() {
        super.onPause()
        Hawk.put(HawkConfig.API_URL, mSelectedUrl)
        Hawk.put<List<Subscription>?>(HawkConfig.SUBSCRIPTIONS, mSubscriptions)
        Hawk.put(HawkConfig.LIVE_URL, mSelectedLiveUrl)
        Hawk.put<List<Subscription>?>(HawkConfig.LIVE_SUBSCRIPTIONS, mLiveSubscriptions)
    }

    override fun finish() {
        val vodChanged = !TextUtils.isEmpty(mSelectedUrl) && mBeforeUrl != mSelectedUrl
        val liveChanged = !TextUtils.isEmpty(mSelectedLiveUrl) && mBeforeLiveUrl != mSelectedLiveUrl
        if (vodChanged || liveChanged) {
            val intent = Intent(this, MainActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        OkGo.getInstance().cancelTag("get_subscription")
    }
}
