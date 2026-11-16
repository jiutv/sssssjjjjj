package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Process
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.constant.IntentKey
import com.github.tvbox.osc.databinding.ActivityMainBinding
import com.github.tvbox.osc.ui.fragment.GridFragment
import com.github.tvbox.osc.ui.fragment.HomeFragment
import com.github.tvbox.osc.ui.fragment.MyFragment
import com.github.tvbox.osc.util.BottomNavHelper
import kotlin.system.exitProcess

class MainActivity : BaseVbActivity<ActivityMainBinding>() {
    override fun vbInflate(inflater: android.view.LayoutInflater, parent: android.view.ViewGroup?, attachToRoot: Boolean) = ActivityMainBinding.inflate(inflater, parent, attachToRoot)


    var fragments = listOf(HomeFragment(), MyFragment())
    var useCacheConfig = false
    private var exitTime = 0L
    // 记录当前是否处于「平板横屏」形态，旋转时据此决定是否重建以切换布局
    private var lastTabletLandscape = false

    override fun init() {
        useCacheConfig = intent.extras?.getBoolean(IntentKey.CACHE_CONFIG_CHANGED, false)?:false

        applySkinToNav()
        lastTabletLandscape = isTabletLandscape(resources.configuration)
        setupTabletNav()

        // 默认选中首页
        mBinding.bottomNav.selectedItemId = R.id.navigation_home

        mBinding.vp.adapter = object : FragmentPagerAdapter(supportFragmentManager) {
            override fun getItem(position: Int): Fragment {
                return fragments[position]
            }

            override fun getCount(): Int {
                return fragments.size
            }
        }
        mBinding.vp.offscreenPageLimit = fragments.size

        mBinding.bottomNav.setOnNavigationItemSelectedListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    mBinding.vp.currentItem = 0
                    true
                }
                R.id.navigation_live -> {
                    startActivity(Intent(this, LiveActivity::class.java))
                    false   // 不切换选中（保持之前的高亮）
                }
                R.id.navigation_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    false
                }
                R.id.navigation_my -> {
                    mBinding.vp.currentItem = 1
                    true
                }
                else -> false
            }
        }

        if (intent.getBooleanExtra(BottomNavHelper.EXTRA_OPEN_MY, false)) {
            mBinding.bottomNav.selectedItemId = R.id.navigation_my
            mBinding.vp.currentItem = 1
            selectSideNav(3)
        }
    }

    /**
     * 平板横屏（sw>=600dp 且横屏）才启用左侧侧边导航；
     * 导航项与原底部导航完全一致：首页 / 直播 / 记录 / 我的。
     * 手机/竖屏布局中不存在这些 id，findViewById 返回 null，直接跳过。
     */
    private fun setupTabletNav() {
        findViewById<android.view.View>(R.id.side_nav_home)?.setOnClickListener {
            mBinding.bottomNav.selectedItemId = R.id.navigation_home
            mBinding.vp.currentItem = 0
            selectSideNav(0)
        }
        findViewById<android.view.View>(R.id.side_nav_live)?.setOnClickListener {
            startActivity(Intent(this, LiveActivity::class.java))
        }
        findViewById<android.view.View>(R.id.side_nav_history)?.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<android.view.View>(R.id.side_nav_my)?.setOnClickListener {
            mBinding.bottomNav.selectedItemId = R.id.navigation_my
            mBinding.vp.currentItem = 1
            selectSideNav(3)
        }
    }

    /** 侧边导航选中态：0 首页 / 1 直播 / 2 记录 / 3 我的（直播、记录为跳转页，不改变选中） */
    private fun selectSideNav(index: Int) {
        val bars = intArrayOf(
            R.id.side_nav_home_bar, R.id.side_nav_live_bar,
            R.id.side_nav_history_bar, R.id.side_nav_my_bar
        )
        val texts = intArrayOf(
            R.id.side_nav_home_text, R.id.side_nav_live_text,
            R.id.side_nav_history_text, R.id.side_nav_my_text
        )
        val accent = attrColor(R.attr.skin_accent)
        val muted = attrColor(R.attr.skin_text_secondary)
        bars.indices.forEach { i ->
            val on = i == index
            findViewById<android.view.View>(bars[i])?.setBackgroundColor(
                if (on) accent else android.graphics.Color.TRANSPARENT
            )
            (findViewById<android.view.View>(texts[i]) as? android.widget.TextView)
                ?.setTextColor(if (on) accent else muted)
        }
    }

    private fun isTabletLandscape(config: Configuration): Boolean {
        return config.smallestScreenWidthDp >= 600 &&
                config.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    /** 旋转到/离开平板横屏形态时重建界面，实时切换 layout / layout-sw600dp-land */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val now = isTabletLandscape(newConfig)
        if (now != lastTabletLandscape && !isFinishing) {
            lastTabletLandscape = now
            recreate()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(BottomNavHelper.EXTRA_OPEN_MY, false)) {
            mBinding.bottomNav.selectedItemId = R.id.navigation_my
            mBinding.vp.currentItem = 1
            selectSideNav(3)
        }
    }

    override fun onBackPressed() {
        if (mBinding.vp.currentItem == 1) {
            mBinding.bottomNav.selectedItemId = R.id.navigation_home
            mBinding.vp.currentItem = 0
            selectSideNav(0)
            return
        }
        val homeFragment = fragments[0] as HomeFragment
        if (!homeFragment.isAdded) {
            confirmExit()
            return
        }
        val childFragments = homeFragment.allFragments
        if (childFragments.isEmpty()) {
            confirmExit()
            return
        }
        val fragment: Fragment = childFragments[homeFragment.tabIndex]
        if (fragment is GridFragment) {
            if (!fragment.restoreView()) {
                if (!homeFragment.scrollToFirstTab()) {
                    confirmExit()
                }
            }
        } else {
            confirmExit()
        }
    }

    /** 底部导航跟随皮肤主色 */
    private fun applySkinToNav() {
        val accent = attrColor(R.attr.skin_accent)
        val muted = attrColor(R.attr.skin_text_secondary)
        val colors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, muted)
        )
        mBinding.bottomNav.itemIconTintList = colors
        mBinding.bottomNav.itemTextColor = colors
    }

    private fun attrColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) androidx.core.content.ContextCompat.getColor(this, tv.resourceId) else tv.data
    }

    private fun confirmExit() {
        if (System.currentTimeMillis() - exitTime > 2000) {
            ToastUtils.showShort("再按一次退出程序")
            exitTime = System.currentTimeMillis()
            exitProcess(0)
        } else {
            ActivityUtils.finishAllActivities(true)
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }
    }
}
