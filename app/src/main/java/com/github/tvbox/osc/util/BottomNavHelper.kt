package com.github.tvbox.osc.util

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.github.tvbox.osc.R
import com.github.tvbox.osc.ui.activity.HistoryActivity
import com.github.tvbox.osc.ui.activity.LiveActivity
import com.github.tvbox.osc.ui.activity.MainActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * 底部导航复用：首页(MainActivity) / 直播 / 记录 / 我的
 * 让二级页面也能一直显示同一个底部导航，点击时在页面之间切换。
 */
object BottomNavHelper {

    const val EXTRA_OPEN_MY = "open_my_tab"

    fun attach(activity: Activity, nav: BottomNavigationView, currentId: Int) {
        val accent = attrColor(activity, R.attr.skin_accent)
        val muted = attrColor(activity, R.attr.skin_text_secondary)
        val colors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, muted)
        )
        nav.itemIconTintList = colors
        nav.itemTextColor = colors
        nav.selectedItemId = currentId
        nav.setOnNavigationItemSelectedListener { item ->
            if (item.itemId == currentId) {
                return@setOnNavigationItemSelectedListener true
            }
            when (item.itemId) {
                R.id.navigation_home -> {
                    toMain(activity, false)
                    true
                }
                R.id.navigation_my -> {
                    toMain(activity, true)
                    true
                }
                R.id.navigation_live -> {
                    activity.startActivity(Intent(activity, LiveActivity::class.java))
                    activity.finish()
                    true
                }
                R.id.navigation_history -> {
                    activity.startActivity(Intent(activity, HistoryActivity::class.java))
                    activity.finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun toMain(activity: Activity, openMy: Boolean) {
        val intent = Intent(activity, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (openMy) intent.putExtra(EXTRA_OPEN_MY, true)
        activity.startActivity(intent)
        activity.finish()
    }

    private fun attrColor(activity: Activity, attr: Int): Int {
        val tv = TypedValue()
        activity.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(activity, tv.resourceId) else tv.data
    }
}
