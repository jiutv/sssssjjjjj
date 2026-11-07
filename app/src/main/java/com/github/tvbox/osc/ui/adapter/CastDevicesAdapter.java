package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import org.jupnp.model.meta.Device;

import java.util.List;

/**
 * DLNA-Cast v2.0.0 移除了 OnDeviceRegistryListener
 * 暂时不实现设备监听接口，待 cling 重写
 */
public class CastDevicesAdapter extends BaseQuickAdapter<Device, BaseViewHolder> {

    public CastDevicesAdapter() {
        super(R.layout.item_title);
    }

    @Override
    protected void convert(BaseViewHolder helper, Device item) {
        if (item != null && item.getDetails() != null) {
            helper.setText(R.id.title, item.getDetails().getFriendlyName());
        }
    }

    public void onDeviceAdded(Device device) {
        addData(device);
    }

    public void onDeviceUpdated(Device device) {
    }

    public void onDeviceRemoved(Device device) {
        List<Device> data = getData();
        if (data.contains(device)) {
            data.remove(device);
            notifyDataSetChanged();
        }
    }
}
