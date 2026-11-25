package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.adapter.FastSearchAdapter;
import com.lxj.xpopup.core.CenterPopupView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全部站点结果：左侧站点列表 + 右侧该站点搜到的全部影片
 */
public class AllSitesResultDialog extends CenterPopupView {

    public interface OnVideoClick {
        void onClick(Movie.Video video);
    }

    private final LinkedHashMap<String, List<Movie.Video>> data;
    private final java.util.Map<String, Long> costs;
    private final OnVideoClick listener;

    private FastSearchAdapter adapter;
    private RecyclerView recyclerView;
    private LinearLayout siteContainer;
    private final List<String> siteKeys = new ArrayList<>();
    private final List<TextView> siteViews = new ArrayList<>();
    private int selected = 0;

    public AllSitesResultDialog(@NonNull @NotNull Context context,
                                LinkedHashMap<String, List<Movie.Video>> data,
                                java.util.Map<String, Long> costs,
                                OnVideoClick listener) {
        super(context);
        this.data = data;
        this.costs = costs;
        this.listener = listener;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_all_sites_result;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        final TextView tvTotal = findViewById(R.id.tvTotal);
        findViewById(R.id.ivClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        recyclerView = findViewById(R.id.rvAllSites);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FastSearchAdapter();
        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter baseAdapter, View view, int position) {
                if (position < 0 || position >= adapter.getData().size()) return;
                Movie.Video video = adapter.getData().get(position);
                dismiss();
                if (listener != null && video != null) listener.onClick(video);
            }
        });
        recyclerView.setAdapter(adapter);

        siteContainer = findViewById(R.id.llSites);
        int total = 0;
        for (Map.Entry<String, List<Movie.Video>> entry : data.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            siteKeys.add(entry.getKey());
            total += entry.getValue().size();
        }
        tvTotal.setText("共 " + total + " 部");

        siteContainer.removeAllViews();
        siteViews.clear();
        addSiteView("全部显示\n" + total + " 部", 0);
        for (int i = 0; i < siteKeys.size(); i++) {
            String key = siteKeys.get(i);
            SourceBean source = ApiConfig.get().getSource(key);
            String name = source == null || TextUtils.isEmpty(source.getName()) ? key : source.getName();
            List<Movie.Video> list = data.get(key);
            int size = list == null ? 0 : list.size();
            Long cost = costs == null ? null : costs.get(key);
            addSiteView(name + "\n" + size + " 部" + (cost == null ? "" : " · " + cost + "ms"), i + 1);
        }
        selectSite(0);
    }

    private void addSiteView(String label, final int index) {
        TextView tv = new TextView(getContext());
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(dp(6), dp(12), dp(6), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectSite(index);
            }
        });
        siteContainer.addView(tv);
        siteViews.add(tv);
    }

    /** 0 = 全部显示，其余对应 siteKeys 下标 + 1 */
    private void selectSite(int index) {
        if (index < 0 || index >= siteViews.size()) return;
        selected = index;
        for (int i = 0; i < siteViews.size(); i++) {
            TextView tv = siteViews.get(i);
            boolean on = i == selected;
            tv.setTextColor(on ? skinColor(R.attr.skin_text_primary) : skinColor(R.attr.skin_text_secondary));
            tv.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            if (on) {
                tv.setBackgroundResource(R.drawable.bg_small_round_gray);
            } else {
                tv.setBackground(null);
            }
        }
        List<Movie.Video> list = new ArrayList<>();
        if (index == 0) {
            for (String key : siteKeys) {
                List<Movie.Video> one = data.get(key);
                if (one != null) list.addAll(one);
            }
        } else {
            List<Movie.Video> one = data.get(siteKeys.get(index - 1));
            if (one != null) list.addAll(one);
        }
        adapter.setNewData(list);
        recyclerView.scrollToPosition(0);
    }

    private int skinColor(int attr) {
        TypedValue tv = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) {
                return ContextCompat.getColor(getContext(), tv.resourceId);
            }
            return tv.data;
        }
        return 0x88FFFFFF;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getContext().getResources().getDisplayMetrics());
    }
}
