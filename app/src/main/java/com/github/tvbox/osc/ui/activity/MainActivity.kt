package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Process
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.constant.IntentKey
import com.github.tvbox.osc.databinding.ActivityMainBinding
import com.github.tvbox.osc.ui.fragment.CombinedFragment
import com.github.tvbox.osc.ui.fragment.GridFragment
import com.github.tvbox.osc.ui.fragment.HomeFragment
import com.github.tvbox.osc.ui.fragment.MyFragment
import kotlin.system.exitProcess

class MainActivity : BaseVbActivity<ActivityMainBinding>() {

    // 首页、历史&收藏、我的（直播 tab 直接跳转 LiveActivity）
    var fragments = listOf(HomeFragment(), CombinedFragment(), MyFragment())
    var useCacheConfig = false
    private var exitTime = 0L

    override fun init() {

        useCacheConfig = intent.extras?.getBoolean(IntentKey.CACHE_CONFIG_CHANGED, false)?:false

        mBinding.vp.adapter = object : FragmentPagerAdapter(supportFragmentManager) {
            override fun getItem(position: Int): Fragment {
                return fragments[position]
            }

            override fun getCount(): Int {
                return fragments.size
            }
        }

        mBinding.bottomNav.setOnNavigationItemSelectedListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.navigation_live -> {
                    // 直播 tab 直接跳转 LiveActivity，不进 ViewPager
                    startActivity(Intent(this, LiveActivity::class.java))
                    false  // 不高亮选中
                }
                else -> {
                    // orderInCategory: 首页=0, 历史&收藏=2, 我的=3
                    // 但 ViewPager 只有 3 个页面（0,1,2），需要映射
                    val position = when (menuItem.itemId) {
                        R.id.navigation_home -> 0
                        R.id.navigation_combined -> 1
                        R.id.navigation_dashboard -> 2
                        else -> 0
                    }
                    mBinding.vp.setCurrentItem(position, false)
                    true
                }
            }
        }
        mBinding.vp.addOnPageChangeListener(object : SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                // 映射：ViewPager 0→首页, 1→历史&收藏, 2→我的
                val navId = when (position) {
                    0 -> R.id.navigation_home
                    1 -> R.id.navigation_combined
                    2 -> R.id.navigation_dashboard
                    else -> R.id.navigation_home
                }
                mBinding.bottomNav.menu.findItem(navId).setChecked(true)
            }
        })
    }

    override fun onBackPressed() {
        // 非首页 tab 先切回首页
        if (mBinding.vp.currentItem != 0) {
            mBinding.vp.currentItem = 0
            return
        }
        val homeFragment = fragments[0] as HomeFragment
        if (!homeFragment.isAdded) { // 资源不足销毁重建时未挂载到activity时getChildFragmentManager会崩溃
            confirmExit()
            return
        }
        val childFragments = homeFragment.allFragments
        if (childFragments.isEmpty()) { //加载中(没有tab)
            confirmExit()
            return
        }
        val fragment: Fragment = childFragments[homeFragment.tabIndex]
        if (fragment is GridFragment) { // 首页数据源动态加载的tab
            if (!fragment.restoreView()) { // 有回退的view,先回退(AList等文件夹列表),没有可回退的,返到主页tab
                if (!homeFragment.scrollToFirstTab()) {
                    confirmExit()
                }
            }
        } else {
            confirmExit()
        }
    }

    private fun confirmExit() {
        if (System.currentTimeMillis() - exitTime > 2000) {
            ToastUtils.showShort("再按一次退出程序")
            exitTime = System.currentTimeMillis()
            ActivityUtils.finishAllActivities(true)
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }
    }
}
