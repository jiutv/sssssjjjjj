package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import org.jupnp.model.meta.Device;

/**
 * TODO: 用 jupnp 的 RegistryListener 重写设备发现回调
 * 原来 implements OnDeviceRegistryListener (DLNA-Cast) 已不可用
 */
public class CastDevicesAdapter extends BaseQuickAdapter<Device, BaseViewHolder> {

    public CastDevicesAdapter() {
        super(R.layout.item_title);
    }

    @Override
    protected void convert(BaseViewHolder helper, Device item) {
        helper.setText(R.id.title, item.getDetails().getFriendlyName());
    }

    // TODO: jupnp 版设备发现回调
    /*
    public void onDeviceAdded(Device<?, ?, ?> device) { addData(device); }
    public void onDeviceUpdated(Device<?, ?, ?> device) { }
    public void onDeviceRemoved(Device<?, ?, ?> device) { ... }
    */
}
