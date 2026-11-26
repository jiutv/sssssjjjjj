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
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
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
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class FastSearchActivity : BaseVbActivity<ActivityFastSearchBinding>(), TextWatcher {

    override fun vbInflate(inflater: android.view.LayoutInflater, parent: android.view.ViewGroup?, attachToRoot: Boolean) = ActivityFastSearchBinding.inflate(inflater, parent, attachToRoot)

    companion object {
        /** 单源超时：每个源独立计时，到点只淘汰这一个源，不影响其他源继续搜索 */
        private const val SEARCH_TIMEOUT_MS = 30_000L
        /** 联想词防抖间隔 */
        private const val SUGGEST_DEBOUNCE_MS = 280L
        /** 热门搜索兜底词：接口失效时保证有内容 */
        private val FALLBACK_HOT_WORDS = listOf(
            "庆余年", "狂飙", "繁花", "长相思", "莲花楼", "三体", "漫长的季节", "苍兰诀"
        )
        /** 热搜数据源：豆瓣年度热门（参考 AVBox 的实现） */
        private const val HOT_URL =
            "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range="
        private const val HOT_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        private const val HOT_CACHE_JSON = "fast_search_hot_json"
        private const val HOT_CACHE_DAY = "fast_search_hot_day"

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
    /** 每个源各自的超时定时器，新一轮搜索/页面销毁时统一移除 */
    private val sourceTimeoutRunnables = ArrayList<Runnable>()
    /** 剧名归一化后 -> 各源结果，用于同剧去重合并 */
    private val groupedVideos = LinkedHashMap<String, MutableList<Movie.Video>>()
    /** 本次搜索的站点顺序，「全部站点结果」按这个顺序分组 */
    private val siteOrder = ArrayList<String>()
    /** 每个源搜索的开始时间 / 实际耗时(ms)，用于按响应速度排序站点 */
    private val sourceStart = HashMap<String, Long>()
    private val sourceCost = HashMap<String, Long>()

    override fun init() {
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        initView()
        initData()
        //历史搜索
        initHistorySearch()
        // 热门搜索
        loadHotWords()
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
        // 右上角按钮改为「左侧站点栏」开关，不新开界面
        mBinding.ivAllSites.alpha = 0.45f
        mBinding.ivAllSites.setOnClickListener { toggleSiteBar() }
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
        // 三列竖滑
        mBinding.mGridView.setLayoutManager(GridLayoutManager(this, 3))
        mBinding.mGridView.adapter = searchAdapter
        searchAdapter.setOnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            openVideo(searchAdapter.data[position])
        }
        mBinding.mGridViewFilter.setLayoutManager(GridLayoutManager(this, 3))

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
    /**
     * 热搜榜：豆瓣年度热门 + 当天缓存 + 内置兜底（逻辑参考 AVBox）
     */
    private fun loadHotWords() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val cachedDay = Hawk.get<String>(HOT_CACHE_DAY, "")
        val cachedJson = Hawk.get<String>(HOT_CACHE_JSON, "")
        if (cachedDay == today && !cachedJson.isNullOrEmpty()) {
            val cachedWords = parseHotTitles(cachedJson)
            if (cachedWords.isNotEmpty()) {
                applyHotRank(cachedWords)
                return
            }
        }
        // 接口返回前先展示兜底榜，保证永远有内容
        applyHotRank(FALLBACK_HOT_WORDS)
        val year = Calendar.getInstance().get(Calendar.YEAR)
        OkGo.get<String>(HOT_URL + "$year,$year")
            .headers("User-Agent", HOT_UA)
            .headers("Referer", "https://movie.douban.com/")
            .execute(object : AbsCallback<String?>() {
                override fun onSuccess(response: com.lzy.okgo.model.Response<String?>) {
                    val body = response.body() ?: return
                    val words = parseHotTitles(body)
                    if (words.isNotEmpty()) {
                        Hawk.put(HOT_CACHE_JSON, body)
                        Hawk.put(HOT_CACHE_DAY, today)
                        applyHotRank(words)
                    }
                }

                @Throws(Throwable::class)
                override fun convertResponse(response: Response): String {
                    return response.body()!!.string()
                }
            })
    }

    private fun parseHotTitles(json: String?): List<String> = try {
        val arr = JSONObject(json ?: "").optJSONArray("data") ?: return emptyList()
        (0 until minOf(arr.length(), 20)).mapNotNull {
            arr.optJSONObject(it)?.optString("title")?.takeIf { t -> t.isNotBlank() }
        }
    } catch (_: Throwable) {
        emptyList()
    }

    /** 热搜榜：两列、带名次，前三名用 accent 色 */
    private fun applyHotRank(words: List<String>) {
        val container = mBinding.llHotRank
        container.removeAllViews()
        val density = resources.displayMetrics.density
        val accent = resolveSkinColor(R.attr.skin_accent)
        val primary = resolveSkinColor(R.attr.skin_text_primary)
        val muted = resolveSkinColor(R.attr.skin_text_secondary)
        fun dp(v: Int) = (v * density + 0.5f).toInt()
        words.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            pair.forEachIndexed { colIndex, title ->
                val rank = rowIndex * 2 + colIndex + 1
                val item = LinearLayout(this)
                item.orientation = LinearLayout.HORIZONTAL
                item.gravity = Gravity.CENTER_VERTICAL
                item.setPadding(dp(4), dp(8), dp(4), dp(8))
                item.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                val numView = TextView(this)
                numView.text = rank.toString()
                numView.textSize = 14f
                numView.gravity = Gravity.CENTER
                numView.setTextColor(if (rank <= 3) accent else muted)
                numView.layoutParams = LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.WRAP_CONTENT)

                val nameView = TextView(this)
                nameView.text = title
                nameView.textSize = 14f
                nameView.maxLines = 1
                nameView.ellipsize = TextUtils.TruncateAt.END
                nameView.setTextColor(primary)
                val nameLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                nameLp.leftMargin = dp(8)
                nameView.layoutParams = nameLp

                item.addView(numView)
                item.addView(nameView)
                item.setOnClickListener { search(title) }
                row.addView(item)
            }
            if (pair.size == 1) {
                val spacer = View(this)
                spacer.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                row.addView(spacer)
            }
            container.addView(row)
        }
    }

    private fun resolveSkinColor(attr: Int): Int {
        val value = android.util.TypedValue()
        return if (theme.resolveAttribute(attr, value, true)) {
            androidx.core.content.ContextCompat.getColor(this, value.resourceId)
        } else {
            0x88FFFFFF.toInt()
        }
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
        siteOrder.clear()
        sourceStart.clear()
        sourceCost.clear()
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
            siteOrder.add(bean.key)
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
        // 每个源独立计时：到点只淘汰这一个源，其他源继续搜索，不再整页一刀切
        clearSourceTimeouts()
        val token = searchToken
        for (key: String in siteKey) {
            sourceStart[key] = System.currentTimeMillis()
            val timeout = Runnable { markSourceFinished(key, token) }
            sourceTimeoutRunnables.add(timeout)
            mainHandler.postDelayed(timeout, SEARCH_TIMEOUT_MS)
            searchExecutorService!!.execute {
                try {
                    sourceViewModel.getSearch(key, searchTitle, searchToken.toString())
                } catch (_: Exception) {
                    // 提交失败也要回包，否则这个源只能干等超时
                    mainHandler.post { markSourceFinished(key, token) }
                }
            }
        }
    }

    /** 单个源到点仍未返回：只淘汰该源；批次已切换则忽略 */
    private fun markSourceFinished(key: String, token: Int) {
        if (token != searchToken) return
        val start = sourceStart[key]
        if (start != null) sourceCost[key] = System.currentTimeMillis() - start
        if (!pendingSources.remove(key)) return
        searchDone++
        updateSearchProgress()
        if (pendingSources.isEmpty()) finishSearch()
    }

    private fun clearSourceTimeouts() {
        for (runnable in sourceTimeoutRunnables) {
            mainHandler.removeCallbacks(runnable)
        }
        sourceTimeoutRunnables.clear()
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

        // 计数：这个源已经回来了（空结果也算），顺便记录响应耗时
        val sourceKey = absXml?.sourceKey
        if (sourceKey != null) {
            if (pendingSources.remove(sourceKey)) searchDone++
            if (!sourceCost.containsKey(sourceKey)) {
                val start = sourceStart[sourceKey]
                if (start != null) sourceCost[sourceKey] = System.currentTimeMillis() - start
            }
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
            rebuildResultList()
            showSuccess()
            if (!isFilterMode) mBinding.mGridView.visibility = View.VISIBLE
        }

        updateSearchProgress()
        if (pendingSources.isEmpty()) {
            finishSearch()
        }
    }

    /** 展示全部搜到的影片（不做同名合并），按匹配度排序 */
    private fun rebuildResultList() {
        val rows = ArrayList<Movie.Video>()
        for (key in siteOrder) {
            resultVods[key]?.let { rows.addAll(it) }
        }
        for ((key, list) in resultVods) {
            if (!siteOrder.contains(key)) rows.addAll(list)
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

    /**
     * 标题归一化：仅用于点开播放页时把同一部剧的多个源带过去，不再用于列表合并。
     * 去括号备注、站点名前缀（如「酷酷看·嫁金钗」）、清晰度/语言/版本、集数标记，再去空白与标点。
     */
    private fun normalizeTitle(name: String?): String {
        if (name.isNullOrEmpty()) return ""
        var title = name
            .replace(Regex("[（(\\[【][^）)\\]】]*[）)\\]】]"), "")
            .trim()
        title = stripSitePrefix(title)
        title = title
            .replace(Regex("(?i)(2160p|1080p|720p|4k|8k|hdr|hd|蓝光|超清|高清|国语|粤语|中字|双语|完整版|未删减|tv版|剧版|电影版|电视剧)"), "")
            .replace(Regex("第\\s*\\d+\\s*[集话期]"), "")
            .replace(Regex("(全|共|更新至|已更新至|更新|连载至)\\s*\\d+\\s*[集话期]"), "")
            .replace(Regex("(完结|已完结|全集)"), "")
            .trim()
        val cleaned = title
            .replace(Regex("[\\s\\p{Z}\\p{P}\\p{S}]"), "")
            .lowercase()
        // 全是噪音时退回原名的清理结果，避免把所有结果并成一条
        if (cleaned.isEmpty()) {
            return name.replace(Regex("[\\s\\p{Z}\\p{P}\\p{S}]"), "").lowercase()
        }
        return cleaned
    }

    /** 去掉片名前的站点名/品牌前缀，如「酷酷看·嫁金钗」→「嫁金钗」 */
    private fun stripSitePrefix(title: String): String {
        val m = Regex("^(.{1,12}?)\\s*[·|｜\\-—_:：]\\s*(.+)$").find(title) ?: return title
        val prefix = m.groupValues[1].replace(Regex("[^\\u4e00-\\u9fa5A-Za-z0-9]"), "")
        if (prefix.isEmpty()) return title
        val rest = m.groupValues[2].trim()
        if (rest.isEmpty()) return title
        // 1) 前缀就是已知站点名
        for (site in spNames.keys) {
            val s = site.replace(Regex("[^\\u4e00-\\u9fa5A-Za-z0-9]"), "")
            if (s.isNotEmpty() && s.equals(prefix, true)) return rest
        }
        // 2) 未知品牌前缀：剩余部分和搜索词高度吻合时才去掉（如「酷酷看·嫁金钗」）
        val query = searchTitle?.trim().orEmpty()
        if (prefix.length <= 6 && query.isNotEmpty()) {
            val q = query.replace(Regex("[\\s\\p{Z}\\p{P}\\p{S}]"), "").lowercase()
            val r = rest.replace(Regex("[\\s\\p{Z}\\p{P}\\p{S}]"), "").lowercase()
            if (q.isNotEmpty() && (r == q || r.startsWith(q) || q.startsWith(r))) return rest
        }
        return title
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

    /** 点结果：直接进播放页，这个剧在所有源的结果一起带过去，在播放页里切换 */
    private fun openVideo(video: Movie.Video) {
        val picked = ArrayList<Movie.Video>()
        picked.add(video)
        val seen = HashSet<String>()
        seen.add(video.sourceKey ?: "")
        groupedVideos[normalizeTitle(video.name)]?.forEach { v ->
            val key = v.sourceKey ?: ""
            if (seen.add(key)) picked.add(v)
        }
        // 点击的站点优先播放；同片其余站点按搜索响应速度排在后面
        val clickedKey = video.sourceKey ?: ""
        val rest = picked.filter { (it.sourceKey ?: "") != clickedKey }
            .sortedBy { sourceCost[it.sourceKey] ?: Long.MAX_VALUE }
        picked.clear()
        picked.add(video)
        picked.addAll(rest)
        jumpToDetail(picked)
    }

    private fun jumpToDetail(videos: List<Movie.Video>) {
        if (videos.isEmpty()) return
        try {
            if (searchExecutorService != null) {
                pauseRunnable = searchExecutorService!!.shutdownNow()
                searchExecutorService = null
                JsLoader.stopAll()
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        val first = videos[0]
        val bundle = Bundle()
        bundle.putString("id", first.id)
        bundle.putString("sourceKey", first.sourceKey)
        if (videos.size > 1) {
            val keys = ArrayList<String>()
            val ids = ArrayList<String>()
            val notes = ArrayList<String>()
            val costs = LongArray(videos.size)
            for ((i, v) in videos.withIndex()) {
                keys.add(v.sourceKey ?: "")
                ids.add(v.id ?: "")
                notes.add(v.note ?: "")
                costs[i] = sourceCost[v.sourceKey] ?: -1L
            }
            bundle.putStringArrayList("searchSourceKeys", keys)
            bundle.putStringArrayList("searchSourceIds", ids)
            bundle.putStringArrayList("searchSourceNotes", notes)
            bundle.putLongArray("searchSourceCosts", costs)
        }
        jumpActivity(DetailActivity::class.java, bundle)
    }

    /** 右上角按钮：左侧站点栏开关（不新开界面） */
    private fun toggleSiteBar() {
        val show = mBinding.llWord.visibility != View.VISIBLE
        mBinding.llWord.visibility = if (show) View.VISIBLE else View.GONE
        mBinding.ivAllSites.alpha = if (show) 1f else 0.45f
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
        clearSourceTimeouts()
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
        clearSourceTimeouts()
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