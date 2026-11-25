package com.github.tvbox.osc.ui.activity

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.angcyo.tablayout.DslTabLayout
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.catvod.crawler.JsLoader
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.bean.AbsXml
import com.github.tvbox.osc.bean.DoubanSuggestBean
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.databinding.ActivityFastSearchBinding
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.event.ServerEvent
import com.github.tvbox.osc.ui.adapter.FastSearchAdapter
import com.github.tvbox.osc.ui.dialog.DoubanSuggestDialog
import com.github.tvbox.osc.ui.dialog.SearchCheckboxDialog
import com.github.tvbox.osc.ui.dialog.SearchSuggestionsDialog
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.SearchHelper
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BasePopupView
import com.lxj.xpopup.interfaces.SimpleCallback
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.callback.StringCallback
import com.orhanobut.hawk.Hawk
import com.zhy.view.flowlayout.FlowLayout
import com.zhy.view.flowlayout.TagAdapter
import okhttp3.Response
import java.net.URLEncoder
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class FastSearchActivity : BaseVbActivity<ActivityFastSearchBinding>(), TextWatcher {

    override fun vbInflate(inflater: android.view.LayoutInflater, parent: android.view.ViewGroup?, attachToRoot: Boolean) = ActivityFastSearchBinding.inflate(inflater, parent, attachToRoot)

    companion object {
        /** 单源超时：超过这个时间仍未返回，就当作无响应，避免整页一直等 */
        private const val SEARCH_TIMEOUT_MS = 12_000L
        /** 联想词防抖间隔 */
        private const val SUGGEST_DEBOUNCE_MS = 280L

        private var mCheckSources: HashMap<String, String>? = null
        fun setCheckedSourcesForSearch(checkedSources: HashMap<String, String>?) {
            mCheckSources = checkedSources
        }
    }

    private lateinit var sourceViewModel : SourceViewModel
    private var searchAdapter = FastSearchAdapter()
    private var searchAdapterFilter = FastSearchAdapter()
    private var searchTitle: String? = ""
    private var spNames = HashMap<String, String>()
    private var isFilterMode = false
    private var searchFilterKey: String? = "" // 过滤的key
    private var resultVods = HashMap<String, MutableList<Movie.Video>>()
    private var pauseRunnable: MutableList<Runnable>? = null
    private var mSearchSuggestionsDialog: SearchSuggestionsDialog? = null

    /** 主线程定时器：联想防抖 + 搜索超时兜底 */
    private val mainHandler = Handler(Looper.getMainLooper())
    private var suggestRunnable: Runnable? = null
    private val suggestSeq = AtomicInteger(0)
    /** 搜索批次号：每次新搜索递增，迟到的旧结果直接丢弃 */
    private var searchToken = 0
    /** 本次搜索还没有返回的源 */
    private val pendingSources = HashSet<String>()
    private var searchTotal = 0
    private var searchDone = 0
    private var searchFinished = true
    private var timeoutRunnable: Runnable? = null
    /** 剧名归一化后 -> 各源结果，用于同剧去重合并 */
    private val groupedVideos = LinkedHashMap<String, MutableList<Movie.Video>>()

    override fun init() {
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        initView()
        // 同一个剧名合并了多个源时，列表项显示「· N源」
        val sourceCounter = FastSearchAdapter.SourceCounter { video ->
            groupedVideos[normalizeTitle(video.name)]?.size ?: 0
        }
        searchAdapter.setSourceCounter(sourceCounter)
        searchAdapterFilter.setSourceCounter(sourceCounter)
        initData()
        //历史搜索
        initHistorySearch()
        // 热门搜索
        hotWords
    }

    override fun onResume() {
        super.onResume()
        if (pauseRunnable != null && pauseRunnable!!.size > 0) {
            searchExecutorService = Executors.newFixedThreadPool(10)
            allRunCount.set(pauseRunnable!!.size)
            for (runnable: Runnable? in pauseRunnable!!) {
                searchExecutorService!!.execute(runnable)
            }
            pauseRunnable!!.clear()
            pauseRunnable = null
        }
    }

    private fun initView() {
        mBinding.etSearch.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(mBinding.etSearch.text.toString())
                return@setOnEditorActionListener true
            }
            false
        }
        mBinding.etSearch.addTextChangedListener(this)
        mBinding.ivFilter.setOnClickListener { filterSearchSource() }
        mBinding.ivBack.setOnClickListener { finish() }
        mBinding.ivSearch.setOnClickListener {
            search(mBinding.etSearch.text.toString())
        }
        mBinding.tabLayout.configTabLayoutConfig {
            onSelectViewChange  = { _, selectViewList, _, _ ->
                    val tvItem: TextView = selectViewList.first() as TextView
                    filterResult(tvItem.text.toString())
                }
        }
        mBinding.mGridView.setHasFixedSize(true)
        mBinding.mGridView.setLayoutManager(LinearLayoutManager(this))
        mBinding.mGridView.adapter = searchAdapter
        searchAdapter.setOnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            openVideo(searchAdapter.data[position])
        }
        mBinding.mGridViewFilter.setLayoutManager(LinearLayoutManager(this))

        mBinding.mGridViewFilter.adapter = searchAdapterFilter
        searchAdapterFilter.setOnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            val video = searchAdapterFilter.data[position]
            if (video != null) openVideo(video)
        }

        searchAdapter.setOnItemLongClickListener { _, _, position ->
            val video = searchAdapter.data[position]
            getDoubanSuggest(video.name)
            true
        }
        searchAdapterFilter.setOnItemLongClickListener { _, _, position ->
            val video = searchAdapterFilter.data[position]
            getDoubanSuggest(video.name)
            true
        }

        setLoadSir(mBinding.llLayout)
    }

    /**
     * 指定搜索源(过滤)
     */
    private fun filterSearchSource() {
        val allSourceBean = ApiConfig.get().sourceBeanList
        if (allSourceBean.isNotEmpty()) {
            val searchAbleSource: MutableList<SourceBean> = ArrayList()
            for (sourceBean: SourceBean in allSourceBean) {
                if (sourceBean.isSearchable) {
                    searchAbleSource.add(sourceBean)
                }
            }
            val mSearchCheckboxDialog = SearchCheckboxDialog(this@FastSearchActivity, searchAbleSource, mCheckSources)
            mSearchCheckboxDialog.show()
        }

    }

    private fun filterResult(spName: String) {
        if (spName === "全部显示") {
            mBinding.mGridView.visibility = View.VISIBLE
            mBinding.mGridViewFilter.visibility = View.GONE
            return
        }
        mBinding.mGridView.visibility = View.GONE
        mBinding.mGridViewFilter.visibility = View.VISIBLE
        val key = spNames[spName]
        if (key.isNullOrEmpty()) return
        if (searchFilterKey === key) return
        searchFilterKey = key
        // 该源可能还没返回，不能强解包
        val list: List<Movie.Video> = resultVods[key] ?: ArrayList()
        searchAdapterFilter.setNewData(ArrayList(list))
    }

    private fun initData() {
        mCheckSources = SearchHelper.getSourcesForSearch()
        if (intent != null && intent.hasExtra("title")) {
            val title = intent.getStringExtra("title")
            if (!TextUtils.isEmpty(title)) {
                showLoading()
                search(title)
            }
        }
    }

    private fun hideHotAndHistorySearch(isHide: Boolean) {
        if (isHide) {
            mBinding.llSearchSuggest.visibility = View.GONE
            mBinding.llSearchResult.visibility = View.VISIBLE
        } else {
            mBinding.llSearchSuggest.visibility = View.VISIBLE
            mBinding.llSearchResult.visibility = View.GONE
        }
    }

    private fun initHistorySearch() {
        val mSearchHistory: List<String> = Hawk.get(HawkConfig.HISTORY_SEARCH, ArrayList())
        mBinding.llHistory.visibility = if (mSearchHistory.isNotEmpty()) View.VISIBLE else View.GONE
        mBinding.flHistory.adapter = object : TagAdapter<String?>(mSearchHistory) {
            override fun getView(parent: FlowLayout, position: Int, s: String?): View {
                val tv: TextView = LayoutInflater.from(this@FastSearchActivity).inflate(
                    R.layout.item_search_word_hot,
                    mBinding.flHistory, false
                ) as TextView
                tv.text = s
                return tv
            }
        }
        mBinding.flHistory.setOnTagClickListener { _: View?, position: Int, _: FlowLayout? ->
            search(mSearchHistory[position])
            true
        }
        findViewById<View>(R.id.iv_clear_history).setOnClickListener { view: View ->
            Hawk.put(HawkConfig.HISTORY_SEARCH, ArrayList<Any>())
            //FlowLayout及其adapter貌似没有清空数据的api,简单粗暴重置
            view.postDelayed({ initHistorySearch() }, 300)
        }
    }

    /**
     * 热门搜索
     */
    private val hotWords: Unit
        get() {
            // 加载热词
            OkGo.get<String>("https://node.video.qq.com/x/api/hot_search")
                .params("channdlId", "0")
                .params("_", System.currentTimeMillis())
                .execute(object : AbsCallback<String?>() {
                    override fun onSuccess(response: com.lzy.okgo.model.Response<String?>) {
                        try {
                            val hots = ArrayList<String>()
                            val itemList =
                                JsonParser.parseString(response.body()).asJsonObject["data"].asJsonObject["mapResult"].asJsonObject["0"].asJsonObject["listInfo"].asJsonArray
                            //                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonArray();
                            for (ele: JsonElement in itemList) {
                                val obj = ele as JsonObject
                                hots.add(obj["title"].asString.trim { it <= ' ' }
                                    .replace("<|>|《|》|-".toRegex(), "").split(" ".toRegex())
                                    .dropLastWhile { it.isEmpty() }
                                    .toTypedArray()[0])
                            }
                            mBinding.flHot.adapter = object : TagAdapter<String?>(hots as List<String?>?) {
                                override fun getView(
                                    parent: FlowLayout,
                                    position: Int,
                                    s: String?
                                ): View {
                                    val tv: TextView =
                                        LayoutInflater.from(this@FastSearchActivity).inflate(
                                            R.layout.item_search_word_hot,
                                            mBinding.flHot, false
                                        ) as TextView
                                    tv.text = s
                                    return tv
                                }
                            }
                            mBinding.flHot.setOnTagClickListener { _: View?, position: Int, _: FlowLayout? ->
                                search(hots.get(position))
                                true
                            }
                        } catch (th: Throwable) {
                            th.printStackTrace()
                        }
                    }

                    @Throws(Throwable::class)
                    override fun convertResponse(response: Response): String {
                        return response.body()!!.string()
                    }
                })
        }

    /**
     * 联想搜索
     */
    private fun getSuggest(text: String) {
        // 同一时刻只允许一个联想请求，避免「庆余年」三个字发出三个请求、旧结果覆盖新结果
        val seq = suggestSeq.incrementAndGet()
        OkGo.getInstance().cancelTag("suggest")
        val url = "https://suggest.video.iqiyi.com/?if=mobile&key=" + URLEncoder.encode(text, "UTF-8")
        OkGo.get<String>(url)
            .tag("suggest")
            .execute(object : AbsCallback<String?>() {
                override fun onSuccess(response: com.lzy.okgo.model.Response<String?>) {
                    if (seq != suggestSeq.get()) return // 已有更新的联想请求，丢弃本次结果
                    val titles: MutableList<String> = ArrayList()
                    try {
                        val json = JsonParser.parseString(response.body()).asJsonObject
                        val datas = json["data"].asJsonArray
                        for (data: JsonElement in datas) {
                            val item = data as JsonObject
                            titles.add(item["name"].asString.trim { it <= ' ' })
                        }
                    } catch (th: Throwable) {
                        LogUtils.d(th.toString())
                    }
                    if (titles.isNotEmpty()) {
                        showSuggestDialog(titles)
                    }
                }

                @Throws(Throwable::class)
                override fun convertResponse(response: Response): String {
                    return response.body()!!.string()
                }
            })
    }

    private fun showSuggestDialog(list: List<String>) {
        if (mSearchSuggestionsDialog == null) {
            mSearchSuggestionsDialog =
                SearchSuggestionsDialog(this@FastSearchActivity, list
                ) { _, text ->
                    LogUtils.d("搜索:$text")
                    mSearchSuggestionsDialog!!.dismissWith { search(text) }
                }
            XPopup.Builder(this@FastSearchActivity)
                .atView(mBinding.etSearch)
                .notDismissWhenTouchInView(mBinding.etSearch)
                .isViewMode(true) //开启View实现
                .isRequestFocus(false) //不强制焦点
                .setPopupCallback(object : SimpleCallback() {
                    override fun onDismiss(popupView: BasePopupView) { // 弹窗关闭了就置空对象,下次重新new
                        super.onDismiss(popupView)
                        mSearchSuggestionsDialog = null
                    }
                })
                .asCustom(mSearchSuggestionsDialog)
                .show()
        } else { // 不为空说明弹窗为打开状态(关闭就置空了).直接刷新数据
            mSearchSuggestionsDialog!!.updateSuggestions(list)
        }
    }

    private fun saveSearchHistory(searchWord: String?) {
        if (!searchWord.isNullOrEmpty()) {
            val history = Hawk.get(HawkConfig.HISTORY_SEARCH, ArrayList<String?>())
            if (!history.contains(searchWord)) {
                history.add(0, searchWord)
            } else {
                history.remove(searchWord)
                history.add(0, searchWord)
            }
            if (history.size > 30) {
                history.removeAt(30)
            }
            Hawk.put(HawkConfig.HISTORY_SEARCH, history)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun server(event: ServerEvent) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            val title = event.obj as String
            showLoading()
            search(title)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    override fun refresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(if (event.obj == null) null else event.obj as AbsXml)
            } catch (e: Exception) {
                searchData(null)
            }
        }
    }

    private fun search(title: String?) {
        if (title.isNullOrEmpty()) {
            ToastUtils.showShort("请输入搜索内容")
            return
        }

        //先移除监听,避免重新设置要搜索的文字触发搜索建议并弹窗
        mBinding.etSearch.removeTextChangedListener(this)
        mBinding.etSearch.setText(title)
        mBinding.etSearch.setSelection(title.length)
        mBinding.etSearch.addTextChangedListener(this)
        if (mSearchSuggestionsDialog != null && mSearchSuggestionsDialog!!.isShow) {
            mSearchSuggestionsDialog!!.dismiss()
        }
        if (!Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) { //无痕浏览不存搜索历史
            saveSearchHistory(title)
        }
        hideHotAndHistorySearch(true)
        KeyboardUtils.hideSoftInput(this)
        cancel()
        // 取消可能还在路上的联想请求，避免弹窗又冒出来
        suggestRunnable?.let { mainHandler.removeCallbacks(it) }
        suggestSeq.incrementAndGet()
        OkGo.getInstance().cancelTag("suggest")
        searchToken++ // 新批次：旧结果一律丢弃
        showLoading()
        searchTitle = title
        //fenci();
        mBinding.mGridView.visibility = View.INVISIBLE
        mBinding.mGridViewFilter.visibility = View.GONE
        searchAdapter!!.setNewData(ArrayList())
        searchAdapterFilter!!.setNewData(ArrayList())
        resultVods.clear()
        groupedVideos.clear()
        searchFilterKey = ""
        isFilterMode = false
        spNames.clear()
        mBinding.tabLayout.removeAllViews()
        searchResult()
    }

    private var searchExecutorService: ExecutorService? = null
    private val allRunCount = AtomicInteger(0)
    private fun getSiteTextView(text: String): TextView {
        val textView = TextView(this)
        textView.text = text
        textView.gravity = Gravity.CENTER
        val params = DslTabLayout.LayoutParams(-2, -2)
        params.topMargin = 20
        params.bottomMargin = 20
        textView.setPadding(20, 10, 20, 10)
        textView.layoutParams = params
        return textView
    }

    private fun searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService!!.shutdownNow()
                searchExecutorService = null
                JsLoader.stopAll()
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        } finally {
            searchAdapter.setNewData(ArrayList())
            searchAdapterFilter.setNewData(ArrayList())
            allRunCount.set(0)
        }
        // 新一轮搜索：重置统计
        groupedVideos.clear()
        pendingSources.clear()
        searchDone = 0
        searchTotal = 0
        searchFinished = false
        searchExecutorService = Executors.newFixedThreadPool(10)
        val searchRequestList: MutableList<SourceBean> = ArrayList()
        searchRequestList.addAll(ApiConfig.get().sourceBeanList)
        val home = ApiConfig.get().homeSourceBean
        searchRequestList.remove(home)
        searchRequestList.add(0, home)
        val siteKey = ArrayList<String>()
        mBinding.tabLayout.addView(getSiteTextView("全部显示"))
        mBinding.tabLayout.setCurrentItem(0, true, false)
        for (bean: SourceBean in searchRequestList) {
            if (!bean.isSearchable) {
                continue
            }
            if (mCheckSources != null && !mCheckSources!!.containsKey(bean.key)) {
                continue
            }
            siteKey.add(bean.key)
            spNames[bean.name] = bean.key
            allRunCount.incrementAndGet()
        }
        pendingSources.addAll(siteKey)
        searchTotal = siteKey.size
        if (siteKey.isEmpty()) {
            finishSearch()
            return
        }
        updateSearchProgress()
        // 超时兜底：到点强制收尾，不让用户无限等
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = Runnable { finishSearch() }
        mainHandler.postDelayed(timeoutRunnable!!, SEARCH_TIMEOUT_MS)
        for (key: String in siteKey) {
            searchExecutorService!!.execute {
                try {
                    sourceViewModel.getSearch(key, searchTitle, searchToken.toString())
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * 添加到最后面并返回最后一个key
     * @param key
     * @return
     */
    private fun addWordAdapterIfNeed(key: String): String {
        try {
            var name = ""
            for (n: String in spNames.keys) {
                if ((spNames[n] == key)) {
                    name = n
                }
            }
            if ((name == "")) return key
            for (i in 0 until mBinding.tabLayout.childCount) {
                val item = mBinding.tabLayout.getChildAt(i) as TextView
                if ((name == item.text.toString())) {
                    return key
                }
            }
            mBinding.tabLayout.addView(getSiteTextView(name))
            return key
        } catch (e: Exception) {
            return key
        }
    }

    private fun matchSearchResult(name: String, searchTitle: String?): Boolean {
        var searchTitle = searchTitle
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(searchTitle)) return false
        searchTitle = searchTitle!!.trim { it <= ' ' }
        val arr = searchTitle.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var matchNum = 0
        for (one: String in arr) {
            if (name.contains(one)) matchNum++
        }
        return if (matchNum == arr.size) true else false
    }

    private fun searchData(absXml: AbsXml?) {
        // 上一轮搜索迟到的结果，直接丢弃
        val token = absXml?.searchToken
        if (!token.isNullOrEmpty() && token != searchToken.toString()) return

        // 计数：这个源已经回来了（空结果也算）
        val sourceKey = absXml?.sourceKey
        if (sourceKey != null && pendingSources.remove(sourceKey)) {
            searchDone++
        }

        var added = false
        val list = absXml?.movie?.videoList
        if (list != null && list.isNotEmpty()) {
            for (video: Movie.Video in list) {
                if (!matchSearchResult(video.name, searchTitle)) continue
                added = true
                resultVods.getOrPut(video.sourceKey) { ArrayList() }.add(video)
                groupedVideos.getOrPut(normalizeTitle(video.name)) { ArrayList() }.add(video)
                addWordAdapterIfNeed(video.sourceKey)
            }
        }
        if (added) {
            rebuildGroupedList()
            showSuccess()
            if (!isFilterMode) mBinding.mGridView.visibility = View.VISIBLE
        }

        updateSearchProgress()
        if (pendingSources.isEmpty()) {
            finishSearch()
        }
    }

    /** 按剧名归一化合并后重建列表，并按匹配度排序 */
    private fun rebuildGroupedList() {
        val rows = ArrayList<Movie.Video>()
        for (group in groupedVideos.values) {
            if (group.isEmpty()) continue
            rows.add(pickRepresentative(group))
        }
        rows.sortWith(
            compareByDescending<Movie.Video> { scoreName(it.name) }
                .thenBy { it.name?.length ?: 0 }
        )
        searchAdapter.setNewData(rows)
        if (isFilterMode) {
            val key = searchFilterKey
            val list: List<Movie.Video> = if (key.isNullOrEmpty()) emptyList() else (resultVods[key] ?: emptyList())
            searchAdapterFilter.setNewData(ArrayList(list))
        }
    }

    /** 同剧多源时选一个最有代表性的（优先名称完全一致，其次名称最短） */
    private fun pickRepresentative(group: MutableList<Movie.Video>): Movie.Video {
        val title = searchTitle?.trim()
        if (!title.isNullOrEmpty()) {
            for (v in group) if (v.name?.trim() == title) return v
        }
        var best = group[0]
        for (v in group) {
            val n = v.name ?: continue
            if (n.length < (best.name?.length ?: Int.MAX_VALUE)) best = v
        }
        return best
    }

    /** 去重用的标题归一化：去括号备注、去空白与标点 */
    private fun normalizeTitle(name: String?): String {
        if (name.isNullOrEmpty()) return ""
        return name
            .replace(Regex("[（(\\[【][^）)\\]】]*[）)\\]】]"), "")
            .replace(Regex("[\\s\\p{Z}\\p{P}\\p{S}]"), "")
            .lowercase()
    }

    /** 相关度：完全一致 > 开头匹配 > 包含 > 分词全中 */
    private fun scoreName(name: String?): Int {
        val n = name?.trim() ?: return 0
        val t = searchTitle?.trim() ?: return 0
        if (t.isEmpty()) return 0
        return when {
            n == t -> 100
            n.startsWith(t) -> 80
            n.contains(t) -> 60
            matchSearchResult(n, t) -> 40
            else -> 0
        }
    }

    /** 点结果：同剧只有一个源直接进详情，多个源弹窗让用户选 */
    private fun openVideo(video: Movie.Video) {
        val group = groupedVideos[normalizeTitle(video.name)]
        if (group == null || group.size <= 1) {
            jumpToDetail(video)
            return
        }
        val names = group.map { v ->
            val site = ApiConfig.get().getSource(v.sourceKey)?.name ?: v.sourceKey
            if (v.note.isNullOrEmpty()) site else site + " · " + v.note
        }.toTypedArray()
        XPopup.Builder(this)
            .isDarkTheme(com.github.tvbox.osc.util.Utils.isDarkTheme())
            .asCenterList("选择播放源（共 " + group.size + " 个）", names, null) { position, _ ->
                if (position in group.indices) jumpToDetail(group[position])
            }
            .show()
    }

    private fun jumpToDetail(video: Movie.Video) {
        try {
            if (searchExecutorService != null) {
                pauseRunnable = searchExecutorService!!.shutdownNow()
                searchExecutorService = null
                JsLoader.stopAll()
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        val bundle = Bundle()
        bundle.putString("id", video.id)
        bundle.putString("sourceKey", video.sourceKey)
        jumpActivity(DetailActivity::class.java, bundle)
    }

    private fun updateSearchProgress() {
        if (searchTotal <= 0) {
            mBinding.tvSearchProgress.visibility = View.GONE
            return
        }
        mBinding.tvSearchProgress.visibility = View.VISIBLE
        mBinding.tvSearchProgress.text = if (searchFinished) {
            if (pendingSources.isEmpty()) "已搜索 " + searchTotal + " 个源"
            else "已返回 " + searchDone + "/" + searchTotal + " 个源 · " + pendingSources.size + " 个无响应"
        } else {
            "搜索中 " + searchDone + "/" + searchTotal + " 个源"
        }
    }

    private fun finishSearch() {
        if (searchFinished) return
        searchFinished = true
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        if (searchAdapter.data.isEmpty()) {
            showEmpty()
        } else {
            showSuccess()
            if (!isFilterMode) mBinding.mGridView.visibility = View.VISIBLE
        }
        cancel()
        updateSearchProgress()
        if (pendingSources.isNotEmpty()) {
            ToastUtils.showShort(pendingSources.size.toString() + " 个源无响应")
        }
    }

    private fun cancel() {
        OkGo.getInstance().cancelTag("search")
    }

    override fun onDestroy() {
        super.onDestroy()
        suggestRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        OkGo.getInstance().cancelTag("suggest")
        cancel()
        try {
            if (searchExecutorService != null) {
                searchExecutorService!!.shutdownNow()
                searchExecutorService = null
                JsLoader.load()
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
    override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
    override fun afterTextChanged(editable: Editable) {
        val text = editable.toString()
        suggestRunnable?.let { mainHandler.removeCallbacks(it) }
        if (TextUtils.isEmpty(text)) {
            suggestSeq.incrementAndGet()
            OkGo.getInstance().cancelTag("suggest")
            mSearchSuggestionsDialog?.dismiss()
            hideHotAndHistorySearch(false)
        } else {
            // 停止输入 280ms 后才发联想请求，避免每敲一个字都打网络
            val task = Runnable { getSuggest(text) }
            suggestRunnable = task
            mainHandler.postDelayed(task, SUGGEST_DEBOUNCE_MS)
        }
    }

    private fun getDoubanSuggest(text: String) {
        OkGo.get<String>("https://movie.douban.com/j/subject_suggest?q="+text.trim())
            .execute(object : StringCallback(){
                override fun onSuccess(response: com.lzy.okgo.model.Response<String>?) {
                    val list = GsonUtils.fromJson<List<DoubanSuggestBean>>(
                        response?.body(),
                        object : TypeToken<List<DoubanSuggestBean>>() {}.type
                    )

                    //暂时只保留第一个,分数查询接口有限制
                    val filterList = list.filter {
                        it.title == text
                    }
                    if (filterList.isEmpty()){
                        ToastUtils.showShort("暂无评分信息")
                        return
                    }

                    XPopup.Builder(this@FastSearchActivity)
                        .maxHeight(ScreenUtils.getScreenHeight() - (ScreenUtils.getScreenHeight() / 4))
                        .asCustom(DoubanSuggestDialog(this@FastSearchActivity,filterList.subList(0,1)))
                        .show()
                }
            })
    }
}