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
import com.github.tvbox.osc.picasso.RoundTransformation
import com.github.tvbox.osc.util.DefaultConfig
import com.squareup.picasso.Picasso

/**
 * 记录页（历史 / 收藏）卡片适配器
 *
 * 卡片 UI 与「订阅管理」页保持一致：左侧封面、中间标题信息、右侧独立删除按钮；
 * 支持点击跳转、右侧按钮删除、长按整卡删除（两种删除方式共存）。
 * 历史 / 收藏通过顶部 Tab 切换，由 Activity 传入当前 Tab 的数据。
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
        // 3 列海报卡片：封面按控件宽度等比计算（与 item_record_card.xml 的 2:3 比例一致）
        private const val COVER_RADIUS_DP = 16f
        private const val CARD_MARGIN_DP = 10f
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
            var year = 0
            var playNote: String? = null

            when (entry) {
                is RecordEntry.History -> {
                    name = entry.vod.name ?: ""
                    pic = entry.vod.pic
                    sourceKey = entry.vod.sourceKey
                    year = entry.vod.year
                    // 历史卡片保留播放进度标记（原 HistoryAdapter 的 playNote 逻辑）
                    playNote = entry.vod.playNote
                }

                is RecordEntry.Collect -> {
                    name = entry.vod.name ?: ""
                    pic = entry.vod.pic
                    sourceKey = entry.vod.sourceKey
                }
            }

            binding.tvName.text = if (TextUtils.isEmpty(name)) "未知影片" else name

            // 来源名兜底，避免源被删除后 ApiConfig.getSource() 返回 null 导致崩溃
            val sourceName = ApiConfig.get().getSource(sourceKey)?.name ?: "未知来源"
            binding.tvMeta.text = if (year > 0) "$sourceName · $year" else sourceName

            if (!TextUtils.isEmpty(playNote)) {
                binding.tvProgress.visibility = View.VISIBLE
                binding.tvProgress.text = "上次看到：$playNote"
            } else {
                binding.tvProgress.visibility = View.GONE
            }

            bindCover(pic)

            binding.root.setOnClickListener { onClick(it, entry) }
            binding.root.setOnLongClickListener {
                onLongClick(entry)
                true
            }
            binding.ivDel.setOnClickListener { onDelete(entry) }
        }

        /** 封面：Picasso 缓存 + 顶部圆角裁剪 + 失败占位图 */
        private fun bindCover(picUrl: String?) {
            val pic = picUrl ?: ""
            if (TextUtils.isEmpty(pic)) {
                binding.ivCover.setImageResource(R.drawable.img_loading_placeholder)
                return
            }
            // 3 列布局：按屏幕宽度算卡片宽度，封面高度按 2:3 计算，避免按原图尺寸解码
            val cardWidth = (ScreenUtils.getScreenWidth() - ConvertUtils.dp2px(CARD_MARGIN_DP)) / 3
            val cardHeight = cardWidth * 3 / 2
            Picasso.get()
                .load(DefaultConfig.checkReplaceProxy(pic))
                .placeholder(R.drawable.img_loading_placeholder)
                .error(R.drawable.img_loading_placeholder)
                .transform(
                    RoundTransformation("record_cover_${pic.hashCode()}")
                        .override(cardWidth, cardHeight)
                        .roundRadius(ConvertUtils.dp2px(COVER_RADIUS_DP), RoundTransformation.RoundType.TOP)
                )
                .into(binding.ivCover)
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
