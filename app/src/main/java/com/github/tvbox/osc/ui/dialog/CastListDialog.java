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

import org.jupnp.model.meta.Device;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * DLNA-Cast v2.0.0 移除了 DLNACastManager
 * 投屏功能待用 jupnp 重写，暂时 UI 留着但核心逻辑空实现
 */
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
        // TODO: 用 jupnp 重写 DLNA 发现和投屏
        findViewById(R.id.btn_cancel).setOnClickListener(view -> dismiss());
        findViewById(R.id.btn_confirm).setOnClickListener(view -> {
            adapter.setNewData(new ArrayList<>());
            // TODO: jupnp search
        });
        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CastDevicesAdapter();
        rv.setAdapter(adapter);

        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                Device item = (Device) adapter.getItem(position);
                if (item != null) {
                    // TODO: jupnp cast
                }
            }
        });
    }

    @Override
    protected void onDismiss() {
        super.onDismiss();
        // TODO: jupnp cleanup
    }
}
