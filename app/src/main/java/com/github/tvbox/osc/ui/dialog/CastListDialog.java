package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.CastVideo;
import com.github.tvbox.osc.ui.adapter.CastDevicesAdapter;
import com.lxj.xpopup.core.CenterPopupView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class CastListDialog extends CenterPopupView {

    private final CastVideo castVideo;
    private CastDevicesAdapter adapter;

    public CastListDialog(@NonNull @NotNull Context context, CastVideo castVideo) {
        super(context);
        this.castVideo = castVideo;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_cast;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        // TODO: 用 jupnp 重写 DLNA 发现和投屏逻辑
        // 原来的 DLNACastManager (com.android.cast.dlna.dmc) 在移除 DLNA-Cast 依赖后不可用
        findViewById(R.id.btn_cancel).setOnClickListener(view -> {
            dismiss();
        });
        // 搜索按钮暂时无效
        findViewById(R.id.btn_confirm).setOnClickListener(view -> {
            adapter.setNewData(new ArrayList<>());
        });
        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CastDevicesAdapter();
        rv.setAdapter(adapter);

        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                // TODO: 投屏功能暂不可用，等 jupnp 重写
            }
        });
    }
}
