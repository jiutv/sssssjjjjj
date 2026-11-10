package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.ScreenUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.util.DanmuHelper;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class DanmuSettingDialog extends BaseDialog {

    private TextView openView;
    private TextView speedView;
    private TextView sizeView;
    private TextView alphaView;
    private TextView lineView;
    private TextView randomColorView;
    private String searchWord;

    public DanmuSettingDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_danmu_setting);
        initView();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(getWindow().getAttributes());
        lp.gravity = Gravity.CENTER;
        lp.width = (int) (ScreenUtils.getScreenWidth() * (ScreenUtils.isPortrait() ? 0.86f : 0.42f));
        getWindow().setAttributes(lp);
        getWindow().setWindowAnimations(R.style.DialogFadeAnimation);
    }

    private void initView() {
        openView = findViewById(R.id.danmu_open);
        speedView = findViewById(R.id.speed);
        sizeView = findViewById(R.id.size);
        alphaView = findViewById(R.id.alpha);
        lineView = findViewById(R.id.line);
        randomColorView = findViewById(R.id.randomColor);

        findViewById(R.id.sizeSub).setOnClickListener(v -> changeSize(-0.1f));
        findViewById(R.id.sizeAdd).setOnClickListener(v -> changeSize(0.1f));
        findViewById(R.id.speedSub).setOnClickListener(v -> changeSpeed(-0.1f));
        findViewById(R.id.speedAdd).setOnClickListener(v -> changeSpeed(0.1f));
        findViewById(R.id.alphaSub).setOnClickListener(v -> changeAlpha(-0.05f));
        findViewById(R.id.alphaAdd).setOnClickListener(v -> changeAlpha(0.05f));
        findViewById(R.id.lineSub).setOnClickListener(v -> changeLine(-1));
        findViewById(R.id.lineAdd).setOnClickListener(v -> changeLine(1));

        openView.setOnClickListener(v -> {
            DanmuHelper.setOpen(!DanmuHelper.isOpen());
            refreshValues();
            notifyChanged(true);
        });
        randomColorView.setOnClickListener(v -> {
            DanmuHelper.setRandomColor(!DanmuHelper.useRandomColor());
            refreshValues();
            notifyChanged(true);
        });

        findViewById(R.id.danmuSearch).setOnClickListener(v -> {
            SearchDanmuDialog dialog = new SearchDanmuDialog(getContext());
            dialog.setOnDanmuSelectedListener(danmu -> {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, danmu));
                dismiss();
            });
            if (!TextUtils.isEmpty(searchWord)) dialog.setSearchWord(searchWord);
            dialog.show();
        });

        findViewById(R.id.danmuReset).setOnClickListener(v -> {
            DanmuHelper.setOpen(true);
            DanmuHelper.setSpeed(1.5f);
            DanmuHelper.setSizeScale(0.8f);
            DanmuHelper.setAlpha(0.9f);
            DanmuHelper.setMaxLine(3);
            DanmuHelper.setRandomColor(false);
            refreshValues();
            notifyChanged(true);
        });

        refreshValues();
    }

    public void setSearchWord(String word) {
        this.searchWord = word;
    }

    public void refreshValues() {
        openView.setText(DanmuHelper.isOpen() ? "开" : "关");
        speedView.setText(String.format(Locale.getDefault(), "%.1f", DanmuHelper.getSpeed()));
        sizeView.setText(String.format(Locale.getDefault(), "%.1f", DanmuHelper.getSizeScale()));
        alphaView.setText((int) (DanmuHelper.getAlpha() * 100) + "%");
        lineView.setText(String.valueOf(DanmuHelper.getMaxLine()));
        randomColorView.setText(DanmuHelper.useRandomColor() ? "开" : "关");
    }

    private void changeSpeed(float delta) {
        DanmuHelper.setSpeed(Math.max(0.5f, Math.min(3.0f, DanmuHelper.getSpeed() + delta)));
        refreshValues();
        notifyChanged(true);
    }

    private void changeSize(float delta) {
        DanmuHelper.setSizeScale(Math.max(0.6f, Math.min(2.0f, DanmuHelper.getSizeScale() + delta)));
        refreshValues();
        notifyChanged(true);
    }

    private void changeAlpha(float delta) {
        DanmuHelper.setAlpha(Math.max(0.1f, Math.min(1.0f, DanmuHelper.getAlpha() + delta)));
        refreshValues();
        notifyChanged(true);
    }

    private void changeLine(int delta) {
        DanmuHelper.setMaxLine(Math.max(1, Math.min(10, DanmuHelper.getMaxLine() + delta)));
        refreshValues();
        notifyChanged(true);
    }

    private void notifyChanged(boolean reload) {
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, reload));
    }
}
