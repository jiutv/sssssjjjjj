package com.github.tvbox.osc.ui.activity

import android.content.Intent
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
import kotlin.system.exitProcess

class MainActivity : BaseVbActivity<ActivityMainBinding>() {

    var fragments = listOf(HomeFragment(), MyFragment())
    private val POS_HOME = 0
    private val POS_MY = 4    // 我的：底部第 5 个位置
    private var exitTime = 0L

    override fun init() {

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
                R.id.navigation_favorite -> {
                    startActivity(Intent(this, CollectActivity::class.java))
                    false
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
    }

    override fun onBackPressed() {
        if (mBinding.vp.currentItem == 1) {
            mBinding.bottomNav.selectedItemId = R.id.navigation_home
            mBinding.vp.currentItem = 0
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
