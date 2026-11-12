package com.github.tvbox.osc.ui.adapter

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.ConvertUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.VodCollect
import com.github.tvbox.osc.databinding.ItemRecordCardBinding
import com.github.tvbox.osc.databinding.ItemRecordGroupBinding
import com.github.tvbox.osc.picasso.RoundTransformation
import com.github.tvbox.osc.util.DefaultConfig
import com.squareup.picasso.Picasso

/**
 * 需求1：记录页（历史 + 收藏）多类型列表适配器
 *
 * 列表结构：分组标题（历史记录 / 收藏） + 卡片条目
 * 卡片 UI 与「订阅管理」页保持一致：左侧封面、中间标题信息、右侧独立删除按钮
 * 支持点击跳转、右侧按钮删除、长按整卡删除（两种删除方式共存）
 */
class HistoryRecordAdapter(
    /** 点击卡片（跳转播放/详情） */
    private val onClick: (View, RecordEntry) -> Unit,
    /** 点击右侧删除按钮 */
    private val onDelete: (RecordEntry) -> Unit,
    /** 长按卡片删除（保留原长按逐条删除功能） */
    private val onLongClick: (RecordEntry) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_GROUP = 0
        private const val TYPE_CARD = 1

        // 封面尺寸（与 item_record_card.xml 保持一致）
        private const val COVER_WIDTH_DP = 62f
        private const val COVER_HEIGHT_DP = 86f
        private const val COVER_RADIUS_DP = 8f
    }

    private val items = ArrayList<RecordEntry>()

    /** 提交分组 + 卡片数据（由 Activity 统一构建，避免分组索引错乱） */
    fun submit(list: List<RecordEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is RecordEntry.Group -> TYPE_GROUP
        else -> TYPE_CARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GROUP) {
            GroupHolder(ItemRecordGroupBinding.inflate(inflater, parent, false))
        } else {
            CardHolder(ItemRecordCardBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val entry = items[position]
        when (holder) {
            is GroupHolder -> holder.bind(entry as RecordEntry.Group)
            is CardHolder -> holder.bind(entry)
        }
    }

    /** 分组标题 */
    private class GroupHolder(private val binding: ItemRecordGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(group: RecordEntry.Group) {
            binding.tvGroupTitle.text = group.title
            binding.tvGroupCount.text = "共 ${group.count} 条"
        }
    }

    /** 历史 / 收藏卡片 */
    private inner class CardHolder(private val binding: ItemRecordCardBinding) :
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
                    // 需求1：历史卡片保留播放进度标记（原 HistoryAdapter 的 playNote 逻辑）
                    playNote = entry.vod.playNote
                }

                is RecordEntry.Collect -> {
                    name = entry.vod.name ?: ""
                    pic = entry.vod.pic
                    sourceKey = entry.vod.sourceKey
                }

                else -> return
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

        /** 封面：Picasso 缓存 + 圆角裁剪 + 失败占位图 */
        private fun bindCover(picUrl: String?) {
            val pic = picUrl ?: ""
            if (TextUtils.isEmpty(pic)) {
                binding.ivCover.setImageResource(R.drawable.img_loading_placeholder)
                return
            }
            val width = ConvertUtils.dp2px(COVER_WIDTH_DP)
            val height = ConvertUtils.dp2px(COVER_HEIGHT_DP)
            Picasso.get()
                .load(DefaultConfig.checkReplaceProxy(pic))
                .placeholder(R.drawable.img_loading_placeholder)
                .error(R.drawable.img_loading_placeholder)
                .transform(
                    RoundTransformation("record_cover_${pic.hashCode()}")
                        .override(width, height)
                        .roundRadius(ConvertUtils.dp2px(COVER_RADIUS_DP), RoundTransformation.RoundType.ALL)
                )
                .into(binding.ivCover)
        }
    }
}

/**
 * 需求1：记录页条目模型
 * Group   分组标题（历史记录 / 收藏）
 * History 历史记录卡片
 * Collect 收藏卡片
 */
sealed class RecordEntry {
    data class Group(val title: String, val count: Int) : RecordEntry()
    data class History(val vod: VodInfo) : RecordEntry()
    data class Collect(val vod: VodCollect) : RecordEntry()
}
