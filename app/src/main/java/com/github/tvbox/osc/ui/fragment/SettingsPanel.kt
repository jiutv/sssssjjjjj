package com.github.tvbox.osc.ui.fragment

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.DiffUtil
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.IJKCode
import com.github.tvbox.osc.constant.IntentKey
import com.github.tvbox.osc.databinding.ViewSettingsContentBinding
import com.github.tvbox.osc.ui.activity.MainActivity
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter.SelectDialogInterface
import com.github.tvbox.osc.ui.dialog.BackupDialog
import com.github.tvbox.osc.ui.dialog.DanmuSettingDialog
import com.github.tvbox.osc.ui.dialog.SelectDialog
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.FileUtils
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.OkGoHelper
import com.github.tvbox.osc.util.PlayerHelper
import com.github.tvbox.osc.util.Utils
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.lxj.xpopup.XPopup
import com.orhanobut.hawk.Hawk
import okhttp3.HttpUrl
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.File

/**
 * 设置项面板：内嵌在「我的」页，负责所有设置行的点击与状态刷新。
 */
class SettingsPanel(
    private val activity: Activity,
    private val binding: ViewSettingsContentBinding
) {

    fun init() {
        binding.tvMediaCodec.text = Hawk.get(HawkConfig.IJK_CODEC, "")
        binding.tvDns.text = OkGoHelper.dnsHttpsList[Hawk.get(HawkConfig.DOH_URL, 0)]
        binding.tvHomeRec.text = getHomeRecName(Hawk.get(HawkConfig.HOME_REC, 0))
        binding.tvScaleType.text = PlayerHelper.getScaleName(Hawk.get(HawkConfig.PLAY_SCALE, 0))
        binding.tvPlay.text = PlayerHelper.getPlayerName(Hawk.get(HawkConfig.PLAY_TYPE, 0))
        binding.tvRenderType.text = PlayerHelper.getRenderName(Hawk.get(HawkConfig.PLAY_RENDER, 0))

        binding.tvDanmuState.text =
            if (Hawk.get(HawkConfig.DANMU_OPEN, true)) "已开启" else "已关闭"
        binding.llDanmuSetting.setOnClickListener {
            val dialog = DanmuSettingDialog(activity)
            dialog.setSearchEnabled(false)
            dialog.setOnDismissListener {
                binding.tvDanmuState.text =
                    if (Hawk.get(HawkConfig.DANMU_OPEN, true)) "已开启" else "已关闭"
            }
            dialog.show()
        }

        binding.switchPrivateBrowsing.isChecked = Hawk.get(HawkConfig.PRIVATE_BROWSING, false)
        binding.llPrivateBrowsing.setOnClickListener { _: View? ->
            val newConfig = !Hawk.get(HawkConfig.PRIVATE_BROWSING, false)
            binding.switchPrivateBrowsing.isChecked = newConfig
            Hawk.put(HawkConfig.PRIVATE_BROWSING, newConfig)
        }

        val defaultBgPlayTypePos = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0)
        val bgPlayTypes = ArrayList<String>()
        bgPlayTypes.add("关闭")
        bgPlayTypes.add("开启")
        bgPlayTypes.add("画中画")
        binding.tvBackgroundPlayType.text = bgPlayTypes[defaultBgPlayTypePos]
        binding.llBackgroundPlay.setOnClickListener { view: View? ->
            FastClickCheckUtil.check(view)
            val dialog = SelectDialog<String>(activity)
            dialog.setTip("请选择")
            dialog.setAdapter(object : SelectDialogInterface<String?> {
                override fun click(value: String?, pos: Int) {
                    binding.tvBackgroundPlayType.text = value
                    Hawk.put(HawkConfig.BACKGROUND_PLAY_TYPE, pos)
                }

                override fun getDisplay(name: String?): String {
                    return name ?: ""
                }
            }, SelectDialogAdapter.stringDiff, bgPlayTypes, defaultBgPlayTypePos)
            dialog.show()
        }

        // 画中画比例：0跟随视频 1 16:9 2 4:3 3 1:1（比例越方，PiP 窗口宽度越小）
        val pipRatioPos = Hawk.get(HawkConfig.PIP_RATIO, 0)
        val pipRatios = arrayOf("跟随视频", "16:9", "4:3", "1:1")
        binding.tvPipRatio.text = pipRatios[pipRatioPos]
        binding.llPipRatio.setOnClickListener { view: View? ->
            FastClickCheckUtil.check(view)
            val dialog = SelectDialog<String>(activity)
            dialog.setTip("请选择")
            dialog.setAdapter(object : SelectDialogInterface<String?> {
                override fun click(value: String?, pos: Int) {
                    binding.tvPipRatio.text = value
                    Hawk.put(HawkConfig.PIP_RATIO, pos)
                }

                override fun getDisplay(name: String?): String {
                    return name ?: ""
                }
            }, SelectDialogAdapter.stringDiff, pipRatios.toList(), pipRatioPos)
            dialog.show()
        }

        binding.tvSpeed.text = Hawk.get(HawkConfig.VIDEO_SPEED, 2.0f).toString()
        binding.llPressSpeed.setOnClickListener {
            val types = ArrayList<String>()
            types.add("2.0")
            types.add("3.0")
            types.add("4.0")
            types.add("5.0")
            types.add("6.0")
            types.add("8.0")
            types.add("10.0")
            val defaultPos = types.indexOf(Hawk.get(HawkConfig.VIDEO_SPEED, 2.0f).toString())
            val dialog = SelectDialog<String>(activity)
            dialog.setTip("请选择")
            dialog.setAdapter(object : SelectDialogInterface<String?> {
                override fun click(value: String?, pos: Int) {
                    Hawk.put(HawkConfig.VIDEO_SPEED, value?.toFloat())
                    binding.tvSpeed.text = value
                }

                override fun getDisplay(name: String?): String {
                    return name ?: ""
                }
            }, SelectDialogAdapter.stringDiff, types, defaultPos)
            dialog.show()
        }

        binding.llBackup.setOnClickListener { v: View? ->
            FastClickCheckUtil.check(v)
            if (XXPermissions.isGranted(activity, Permission.MANAGE_EXTERNAL_STORAGE)) {
                BackupDialog(activity).show()
            } else {
                XXPermissions.with(activity)
                    .permission(Permission.MANAGE_EXTERNAL_STORAGE)
                    .request(object : OnPermissionCallback {
                        override fun onGranted(permissions: List<String>, all: Boolean) {
                            if (all) {
                                BackupDialog(activity).show()
                            }
                        }

                        override fun onDenied(permissions: List<String>, never: Boolean) {
                            if (never) {
                                ToastUtils.showLong("获取存储权限失败,请在系统设置中开启")
                                XXPermissions.startPermissionActivity(activity, permissions)
                            } else {
                                ToastUtils.showShort("获取存储权限失败")
                            }
                        }
                    })
            }
        }

        binding.llDns.setOnClickListener { v: View? ->
            FastClickCheckUtil.check(v)
            val dohUrl = Hawk.get(HawkConfig.DOH_URL, 0)
            val dialog = SelectDialog<String>(activity)
            dialog.setTip("请选择安全DNS")
            dialog.setAdapter(object : SelectDialogInterface<String?> {
                override fun click(value: String?, pos: Int) {
                    binding.tvDns.text = OkGoHelper.dnsHttpsList[pos]
                    Hawk.put(HawkConfig.DOH_URL, pos)
                    val url = OkGoHelper.getDohUrl(pos)
                    OkGoHelper.dnsOverHttps.setUrl(if (url.isEmpty()) null else HttpUrl.get(url))
                    IjkMediaPlayer.toggleDotPort(pos > 0)
                    restartMain()
                }

                override fun getDisplay(name: String?): String {
                    return name ?: ""
                }
            }, SelectDialogAdapter.stringDiff, OkGoHelper.dnsHttpsList, dohUrl)
            dialog.show()
        }

        binding.llMediaCodec.setOnClickListener { v: View? ->
            val ijkCodes = ApiConfig.get().ijkCodes
            if (ijkCodes == null || ijkCodes.size == 0) return@setOnClickListener
            FastClickCheckUtil.check(v)
            var defaultPos = 0
            val ijkSel = Hawk.get(HawkConfig.IJK_CODEC, "")
            for (j in ijkCodes.indices) {
                if (ijkSel == ijkCodes[j].name) {
                    defaultPos = j
                    break
                }
            }
            val dialog = SelectDialog<IJKCode>(activity)
            dialog.setTip("请选择IJK解码")
            dialog.setAdapter(object : SelectDialogInterface<IJKCode?> {
                override fun click(value: IJKCode?, pos: Int) {
                    value?.selected(true)
                    binding.tvMediaCodec.text = value?.name
                }

                override fun getDisplay(code: IJKCode?): String {
                    return code?.name ?: ""
                }
            }, object : DiffUtil.ItemCallback<IJKCode>() {
                override fun areItemsTheSame(oldItem: IJKCode, newItem: IJKCode): Boolean {
                    return oldItem === newItem
                }

                override fun areContentsTheSame(oldItem: IJKCode, newItem: IJKCode): Boolean {
                    return oldItem.name.contentEquals(newItem.name)
                }
            }, ijkCodes, defaultPos)
            dialog.show()
        }

        binding.llScale.setOnClickListener { v: View? ->
            FastClickCheckUtil.check(v)
            val defaultPos = Hawk.get(HawkConfig.PLAY_SCALE, 0)
            val scales = ArrayList<Int>()
            scales.add(0)
            scales.add(1)
            scales.add(2)
            scales.add(3)
            scales.add(4)
            scales.add(5)
            val dialog = SelectDialog<Int>(activity)
            dialog.setTip("请选择画面缩放")
            dialog.setAdapter(object : SelectDialogInterface<Int?> {
                override fun click(value: Int?, pos: Int) {
                    Hawk.put(HawkConfig.PLAY_SCALE, value)
                    binding.tvScaleType.text = value?.let { PlayerHelper.getScaleName(it) }
                }

                override fun getDisplay(value: Int?): String {
                    return PlayerHelper.getScaleName(value ?: 0)
                }
            }, object : DiffUtil.ItemCallback<Int>() {
                override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }
            }, scales, defaultPos)
            dialog.show()
        }

        binding.llPlay.setOnClickListener { v: View? ->
            FastClickCheckUtil.check(v)
            val playerType = Hawk.get(HawkConfig.PLAY_TYPE, 0)
            var defaultPos = 0
            val players = PlayerHelper.getExistPlayerTypes()
            val renders = ArrayList<Int>()
            for (p in players.indices) {
                renders.add(p)
                if (players[p] == playerType) {
                    defaultPos = p
                }
            }
            val dialog = SelectDialog<Int>(activity)
            dialog.setTip("请选择默认播放器")
            dialog.setAdapter(object : SelectDialogInterface<Int?> {
                override fun click(value: Int?, pos: Int) {
                    val thisPlayerType = players[pos]
                    Hawk.put(HawkConfig.PLAY_TYPE, thisPlayerType)
                    binding.tvPlay.text = PlayerHelper.getPlayerName(thisPlayerType)
                    PlayerHelper.init()
                }

                override fun getDisplay(value: Int?): String {
                    return PlayerHelper.getPlayerName(players[value ?: 0])
                }
            }, object : DiffUtil.ItemCallback<Int>() {
                override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }
            }, renders, defaultPos)
            dialog.show()
        }

        binding.llRender.setOnClickListener { v: View? ->
            FastClickCheckUtil.check(v)
            val defaultPos = Hawk.get(HawkConfig.PLAY_RENDER, 0)
            val renders = ArrayList<Int>()
            renders.add(0)
            renders.add(1)
            val dialog = SelectDialog<Int>(activity)
            dialog.setTip("请选择默认渲染方式")
            dialog.setAdapter(object : SelectDialogInterface<Int?> {
                override fun click(value: Int?, pos: Int) {
                    Hawk.put(HawkConfig.PLAY_RENDER, value)
                    binding.tvRenderType.text = PlayerHelper.getRenderName(value ?: 0)
                    PlayerHelper.init()
                }

                override fun getDisplay(value: Int?): String {
                    return PlayerHelper.getRenderName(value ?: 0)
                }
            }, object : DiffUtil.ItemCallback<Int>() {
                override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }
            }, renders, defaultPos)
            dialog.show()
        }

        binding.llHomeRec.setOnClickListener { v: View? ->
            FastClickCheckUtil.check(v)
            val defaultPos = Hawk.get(HawkConfig.HOME_REC, 0)
            val types = ArrayList<Int>()
            types.add(0)
            types.add(1)
            types.add(2)
            val dialog = SelectDialog<Int>(activity)
            dialog.setTip("主页内容显示")
            dialog.setAdapter(object : SelectDialogInterface<Int?> {
                override fun click(value: Int?, pos: Int) {
                    Hawk.put(HawkConfig.HOME_REC, value)
                    binding.tvHomeRec.text = getHomeRecName(value ?: 0)
                    restartMain()
                }

                override fun getDisplay(value: Int?): String {
                    return getHomeRecName(value ?: 0)
                }
            }, object : DiffUtil.ItemCallback<Int>() {
                override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }
            }, types, defaultPos)
            dialog.show()
        }

        binding.llClearCache.setOnClickListener { view: View ->
            XPopup.Builder(activity)
                .isDarkTheme(Utils.isDarkTheme())
                .asConfirm("提示", "确定清空吗？") { onClickClearCache(view) }.show()
        }

        val oldTheme = currentThemeIndexOf()
        val themes = arrayOf(
            "跟随系统", "浅色", "深色",
            "黑金凤雅", "青黑科技", "蓝紫沉浸", "暖橙怀旧", "液态玻璃", "樱花粉",
            "雾蓝", "森林绿", "奶油"
        )
        binding.tvTheme.text = themes[oldTheme]
        binding.llTheme.setOnClickListener(View.OnClickListener { view: View? ->
            FastClickCheckUtil.check(view)
            val types = ArrayList<Int>()
            for (i in themes.indices) types.add(i)
            val dialog = SelectDialog<Int>(activity)
            dialog.setTip("请选择")
            dialog.setAdapter(object : SelectDialogInterface<Int?> {
                override fun click(value: Int?, pos: Int) {
                    val v = value ?: 0
                    when (v) {
                        0, 1, 2 -> {
                            Hawk.put(HawkConfig.THEME_TAG, v)
                            Hawk.put(HawkConfig.SKIN_TAG, 0)
                        }
                        in 3..11 -> {
                            Hawk.put(HawkConfig.SKIN_TAG, v - 2)
                        }
                    }
                    binding.tvTheme.text = themes[v]
                    dialog.dismiss()
                }

                override fun getDisplay(value: Int?): String {
                    return themes[value ?: 0]
                }
            }, object : DiffUtil.ItemCallback<Int>() {
                override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
                    return oldItem == newItem
                }
            }, types, oldTheme)
            dialog.setOnDismissListener { _: DialogInterface? ->
                if (oldTheme != currentThemeIndexOf()) {
                    Utils.initTheme()
                    restartMain()
                }
            }
            dialog.show()
        })

        binding.switchVideoPurify.isChecked = Hawk.get(HawkConfig.VIDEO_PURIFY, true)
        binding.llVideoPurify.setOnClickListener { v: View? ->
            FastClickCheckUtil.check(v)
            val newConfig = !Hawk.get(HawkConfig.VIDEO_PURIFY, true)
            binding.switchVideoPurify.isChecked = newConfig
            Hawk.put(HawkConfig.VIDEO_PURIFY, newConfig)
        }
        binding.switchIjkCachePlay.isChecked = Hawk.get(HawkConfig.IJK_CACHE_PLAY, false)
        binding.llIjkCachePlay.setOnClickListener { v: View? ->
            FastClickCheckUtil.check(v)
            val newConfig = !Hawk.get(HawkConfig.IJK_CACHE_PLAY, false)
            binding.switchIjkCachePlay.isChecked = newConfig
            Hawk.put(HawkConfig.IJK_CACHE_PLAY, newConfig)
        }
    }

    /** 首页内容 / DNS / 主题变更后重启主界面，使配置立即生效 */
    private fun restartMain() {
        Utils.initTheme()
        val intent = Intent(activity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra(IntentKey.CACHE_CONFIG_CHANGED, true)
        activity.startActivity(intent)
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    /**
     * 主题颜色当前选中项的索引：
     * 0跟随系统 1浅色 2深色 3黑金凤雅 4青黑科技 5蓝紫沉浸 6暖橙怀旧 7液态玻璃 8樱花粉
     * 9雾蓝 10森林绿 11奶油
     */
    private fun currentThemeIndexOf(): Int {
        return when (Utils.getSkinTag()) {
            1 -> 3
            2 -> 4
            3 -> 5
            4 -> 6
            5 -> 7
            6 -> 8
            7 -> 9
            8 -> 10
            9 -> 11
            else -> Hawk.get<Int>(HawkConfig.THEME_TAG, 0)
        }
    }

    private fun onClickClearCache(v: View) {
        FastClickCheckUtil.check(v)
        val cachePath = FileUtils.getCachePath()
        val cacheDir = File(cachePath)
        if (!cacheDir.exists()) return
        Thread {
            try {
                FileUtils.cleanDirectory(cacheDir)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
        ToastUtils.showLong("缓存已清空")
    }

    private fun getHomeRecName(type: Int): String {
        return when (type) {
            0 -> "豆瓣热播"
            1 -> "站点推荐"
            else -> "关闭"
        }
    }
}
