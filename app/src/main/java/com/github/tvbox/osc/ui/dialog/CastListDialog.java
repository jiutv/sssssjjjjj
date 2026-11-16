package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.blankj.utilcode.util.ToastUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.CastVideo;
import com.github.tvbox.osc.ui.adapter.CastDevicesAdapter;
import com.github.tvbox.osc.util.cast.DlnaCastManager;
import com.lxj.xpopup.core.CenterPopupView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * 投屏弹窗：打开即搜索局域网 DLNA 设备，点击设备开始投屏
 */
public class CastListDialog extends CenterPopupView {

    private final CastVideo castVideo;
    private CastDevicesAdapter adapter;
    private TextView titleView;
    private boolean dismissed = false;

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
        titleView = findViewById(R.id.title);
        findViewById(R.id.btn_cancel).setOnClickListener(view -> dismiss());

        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CastDevicesAdapter();
        rv.setAdapter(adapter);

        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter baseAdapter, View view, int position) {
                DlnaCastManager.Device device = adapter.getItem(position);
                if (device == null) return;
                ToastUtils.showShort("正在投屏到 " + device.name);
                DlnaCastManager.get().cast(device, castVideo.getUrl(), castVideo.getName(),
                        (success, message) -> {
                            ToastUtils.showShort(message);
                            if (success) dismiss();
                        });
            }
        });

        findViewById(R.id.btn_confirm).setOnClickListener(view -> startSearch());
        startSearch();
    }

    private void startSearch() {
        adapter.setNewData(new ArrayList<>());
        if (titleView != null) titleView.setText("投屏 · 搜索中…");
        DlnaCastManager.get().search(4000, new DlnaCastManager.DiscoveryListener() {
            @Override
            public void onDeviceFound(DlnaCastManager.Device device) {
                if (dismissed) return;
                adapter.addData(device);
            }

            @Override
            public void onSearchFinished() {
                if (dismissed) return;
                int count = adapter.getItemCount();
                if (titleView != null) {
                    titleView.setText(count > 0 ? "投屏（找到 " + count + " 台设备）" : "投屏");
                }
                if (count == 0) {
                    ToastUtils.showShort("未找到设备，请确认电视与手机在同一局域网并已开启投屏");
                }
            }

            @Override
            public void onError(String message) {
                if (dismissed) return;
                ToastUtils.showShort("搜索失败：" + message);
            }
        });
    }

    @Override
    protected void onDismiss() {
        dismissed = true;
        DlnaCastManager.get().cancel();
        super.onDismiss();
    }
}
