package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.adapter.FastSearchAdapter;
import com.github.tvbox.osc.ui.adapter.SiteHeaderAdapter;
import com.lxj.xpopup.core.CenterPopupView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全部站点结果：按站点分组，展示每个站点搜到的所有影片
 */
public class AllSitesResultDialog extends CenterPopupView {

    public interface OnVideoClick {
        void onClick(Movie.Video video);
    }

    private final LinkedHashMap<String, List<Movie.Video>> data;
    private final OnVideoClick listener;

    public AllSitesResultDialog(@NonNull @NotNull Context context,
                                LinkedHashMap<String, List<Movie.Video>> data,
                                OnVideoClick listener) {
        super(context);
        this.data = data;
        this.listener = listener;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_all_sites_result;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        TextView tvTotal = findViewById(R.id.tvTotal);
        findViewById(R.id.ivClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        RecyclerView rv = findViewById(R.id.rvAllSites);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        List<RecyclerView.Adapter<? extends RecyclerView.ViewHolder>> adapters = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, List<Movie.Video>> entry : data.entrySet()) {
            List<Movie.Video> videos = entry.getValue();
            if (videos == null || videos.isEmpty()) continue;
            SourceBean source = ApiConfig.get().getSource(entry.getKey());
            String name = source == null || source.getName() == null ? entry.getKey() : source.getName();
            SiteHeaderAdapter header = new SiteHeaderAdapter();
            header.setNewData(Collections.singletonList(name + " · " + videos.size() + " 部"));
            adapters.add(header);

            FastSearchAdapter items = new FastSearchAdapter();
            items.setNewData(new ArrayList<>(videos));
            items.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                    Movie.Video video = items.getData().get(position);
                    dismiss();
                    if (listener != null && video != null) listener.onClick(video);
                }
            });
            adapters.add(items);
            total += videos.size();
        }
        tvTotal.setText("共 " + total + " 部");
        rv.setAdapter(new ConcatAdapter(adapters));
    }
}
