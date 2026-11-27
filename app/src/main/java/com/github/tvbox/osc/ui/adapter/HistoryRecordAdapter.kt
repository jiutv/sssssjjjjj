package com.github.tvbox.osc.ui.adapter

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.ConvertUtils
import com.blankj.utilcode.util.ScreenUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.VodCollect
import com.github.tvbox.osc.databinding.ItemRecordCardBinding
import com.github.tvbox.osc.util.DefaultConfig
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记录页（历史 / 收藏）卡片适配器
 *
 * 流式海报卡片：封面 + 左上角来源角标 + 左下角「上次观看」+ 下方标题 / 相对时间。
 * 支持点击跳转、右上角按钮删除、长按整卡删除（两种删除方式共存）。
 */
class HistoryRecordAdapter(
    /** 点击卡片（跳转详情/播放） */
    private val onClick: (View, RecordEntry) -> Unit,
    /** 点击右侧删除按钮 */
    private val onDelete: (RecordEntry) -> Unit,
    /** 长按卡片删除（保留原长按逐条删除功能） */
    private val onLongClick: (RecordEntry) -> Unit,
) : RecyclerView.Adapter<HistoryRecordAdapter.CardHolder>() {

    companion object {
        // 3 列海报：RecyclerView 左右各 10dp padding + 每格卡片左右各 5dp margin
        private const val RV_HORIZONTAL_PADDING_DP = 10f
        private const val CARD_MARGIN_DP = 5f
    }

    private val items = ArrayList<RecordEntry>()

    /** 提交当前 Tab 的卡片数据 */
    fun submit(list: List<RecordEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder {
        val binding = ItemRecordCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CardHolder(binding)
    }

    override fun onBindViewHolder(holder: CardHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class CardHolder(private val binding: ItemRecordCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: RecordEntry) {
            val name: String
            val pic: String?
            val sourceKey: String?
            var playNote: String? = null
            var updateTime = 0L

            when (entry) {
                is RecordEntry.History -> {
                    name = entry.vod.name ?: ""
                    pic = entry.vod.pic
                    sourceKey = entry.vod.sourceKey
                    // 历史卡片保留播放进度标记（原 HistoryAdapter 的 playNote 逻辑）
                    playNote = entry.vod.playNote
                    updateTime = entry.vod.historyTime
                }

                is RecordEntry.Collect -> {
                    name = entry.vod.name ?: ""
                    pic = entry.vod.pic
                    sourceKey = entry.vod.sourceKey
                    updateTime = entry.vod.updateTime
                }
            }

            binding.tvName.text = if (TextUtils.isEmpty(name)) "未知影片" else name

            // 来源名兜底，避免源被删除后 ApiConfig.getSource() 返回 null 导致崩溃
            val sourceName = ApiConfig.get().getSource(sourceKey)?.name ?: "未知来源"
            binding.tvSource.text = sourceName

            if (!TextUtils.isEmpty(playNote)) {
                binding.llProgress.visibility = View.VISIBLE
                binding.tvProgress.text = "上次观看 $playNote"
            } else {
                binding.llProgress.visibility = View.GONE
            }

            val timeText = formatRelativeTime(updateTime)
            binding.tvTime.text = timeText
            binding.tvTime.visibility = if (TextUtils.isEmpty(timeText)) View.GONE else View.VISIBLE

            bindCover(pic)

            binding.root.setOnClickListener { onClick(it, entry) }
            binding.root.setOnLongClickListener {
                onLongClick(entry)
                true
            }
            binding.ivDel.setOnClickListener { onDelete(entry) }
        }

        /** 封面：按卡片实际宽度 2:3 解码，避免加载原图；圆角由 poster_box 的 outline 裁剪 */
        private fun bindCover(picUrl: String?) {
            val pic = picUrl ?: ""
            if (TextUtils.isEmpty(pic)) {
                binding.ivCover.setImageResource(R.drawable.img_loading_placeholder)
                return
            }
            val rvPadding = ConvertUtils.dp2px(RV_HORIZONTAL_PADDING_DP) * 2
            val cellWidth = (ScreenUtils.getScreenWidth() - rvPadding) / 3
            val coverWidth = cellWidth - ConvertUtils.dp2px(CARD_MARGIN_DP) * 2
            val coverHeight = coverWidth * 3 / 2
            Picasso.get()
                .load(DefaultConfig.checkReplaceProxy(pic))
                .placeholder(R.drawable.img_loading_placeholder)
                .error(R.drawable.img_loading_placeholder)
                .resize(coverWidth.coerceAtLeast(1), coverHeight.coerceAtLeast(1))
                .centerCrop()
                .into(binding.ivCover)
        }
    }

    /** 时间：刚刚 / N分钟前 / N小时前 / N天前，超过 30 天显示日期 */
    private fun formatRelativeTime(time: Long): String {
        if (time <= 0L) return ""
        val diff = System.currentTimeMillis() - time
        if (diff <= 0L) return "刚刚"
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour
        return when {
            diff < minute -> "刚刚"
            diff < hour -> "${diff / minute}分钟前"
            diff < day -> "${diff / hour}小时前"
            diff < 30 * day -> "${diff / day}天前"
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))
        }
    }
}

/**
 * 记录页条目模型
 * History 历史记录卡片
 * Collect 收藏卡片
 */
sealed class RecordEntry {
    data class History(val vod: VodInfo) : RecordEntry()
    data class Collect(val vod: VodCollect) : RecordEntry()
}
