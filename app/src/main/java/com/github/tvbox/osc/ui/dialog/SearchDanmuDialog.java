package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.KeyboardUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.bean.DanmuSearchResult;
import com.github.tvbox.osc.ui.adapter.SearchDanmuAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SearchDanmuDialog extends BaseDialog {

    private final Context mContext;
    private TvRecyclerView mGridView;
    private SearchDanmuAdapter searchAdapter;
    private TextView searchBtn;
    private EditText searchEt;
    private ProgressBar loadingBar;
    private OnDanmuSelectedListener listener;
    private String searchWord = "";

    public interface OnDanmuSelectedListener {
        void onDanmuSelected(String danmu);
    }

    public SearchDanmuDialog(@NonNull @NotNull Context context) {
        super(context);
        mContext = context;
        if (context instanceof Activity) setOwnerActivity((Activity) context);
        setContentView(R.layout.dialog_search_danmu);
        initView();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(getWindow().getAttributes());
        lp.gravity = ScreenUtils.isPortrait() ? Gravity.CENTER : Gravity.TOP | Gravity.START | Gravity.END;
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        getWindow().setAttributes(lp);
        getWindow().setWindowAnimations(R.style.DialogFadeAnimation);
    }

    private void initView() {
        loadingBar = findViewById(R.id.loadingBar);
        mGridView = findViewById(R.id.mGridView);
        searchEt = findViewById(R.id.input);
        searchBtn = findViewById(R.id.inputSubmit);
        searchAdapter = new SearchDanmuAdapter();
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                DanmuSearchResult result = searchAdapter.getData().get(position);
                loadingBar.setVisibility(View.VISIBLE);
                mGridView.setVisibility(View.GONE);
                DanmakuApi.loadSearchResult(result, new DanmakuApi.SearchResultCallback() {
                    @Override
                    public void onSuccess(String danmu) {
                        loadingBar.setVisibility(View.GONE);
                        if (listener != null) listener.onDanmuSelected(danmu);
                        dismiss();
                    }

                    @Override
                    public void onError(String message) {
                        loadingBar.setVisibility(View.GONE);
                        mGridView.setVisibility(View.VISIBLE);
                        Toast.makeText(getContext(), "弹幕加载失败：" + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        searchEt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(searchEt.getText().toString().trim());
                return true;
            }
            return false;
        });
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                search(searchEt.getText().toString().trim());
            }
        });
        searchAdapter.setNewData(new ArrayList<>());
    }

    public void setSearchWord(String wd) {
        if (TextUtils.isEmpty(wd)) return;
        wd = wd.replaceAll("(?:（|\\(|\\[|【|\\.mp4|\\.mkv|\\.avi|\\.MP4|\\.MKV|\\.AVI)", "");
        wd = wd.replaceAll("(?:：|\\:|）|\\)|\\]|】|\\.)", " ");
        int len = wd.length();
        int finalLen = Math.min(len, 36);
        wd = wd.substring(0, finalLen).trim();
        searchEt.setText(wd);
        searchEt.setSelection(wd.length());
        searchEt.requestFocus();
    }

    public void search(String wd) {
        KeyboardUtils.hideSoftInput(getWindow());
        searchAdapter.setNewData(new ArrayList<>());
        if (TextUtils.isEmpty(wd)) {
            Toast.makeText(getContext(), "输入内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        searchWord = wd;
        loadingBar.setVisibility(View.VISIBLE);
        mGridView.setVisibility(View.GONE);
        DanmakuApi.searchList(wd, "", new DanmakuApi.SearchListCallback() {
            @Override
            public void onSuccess(List<DanmuSearchResult> results) {
                loadingBar.setVisibility(View.GONE);
                mGridView.setVisibility(View.VISIBLE);
                if (results == null || results.isEmpty()) {
                    Toast.makeText(getContext(), "未查询到匹配弹幕", Toast.LENGTH_SHORT).show();
                    return;
                }
                searchAdapter.setNewData(results);
                mGridView.requestFocus();
            }

            @Override
            public void onError(String message) {
                loadingBar.setVisibility(View.GONE);
                mGridView.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), "弹幕搜索失败：" + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void setOnDanmuSelectedListener(OnDanmuSelectedListener listener) {
        this.listener = listener;
    }
}
