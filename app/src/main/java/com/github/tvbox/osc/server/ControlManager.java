package com.github.tvbox.osc.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.receiver.SearchReceiver;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * @author pj567
 * @date :2021/1/4
 * @description:
 *
 * P0 fix: mContext 改成 static WeakReference，init() 统一存 Application Context 避免泄漏 Activity。
 */
public class ControlManager {
    private static ControlManager instance;
    private RemoteServer mServer = null;
    private static volatile java.lang.ref.WeakReference<Context> sContextRef;

    private ControlManager() {

    }

    public static ControlManager get() {
        if (instance == null) {
            synchronized (ControlManager.class) {
                if (instance == null) {
                    instance = new ControlManager();
                }
            }
        }
        return instance;
    }

    /**
     * 传入的 Context 会被换成 Application Context 再存，避免 Activity 泄漏。
     */
    public static void init(Context context) {
        sContextRef = new java.lang.ref.WeakReference<>(context.getApplicationContext());
    }

    private Context getContext() {
        Context ctx = sContextRef == null ? null : sContextRef.get();
        if (ctx == null) {
            // 兜底：RemoteServer 可能在 init() 之前就被调用，退回 Application
            throw new IllegalStateException("ControlManager.init(context) must be called before use");
        }
        return ctx;
    }

    public String getAddress(boolean local) {
        return local ? mServer.getLoadAddress() : mServer.getServerAddress();
    }

    public void startServer() {
        if (mServer != null) {
            return;
        }
        Context ctx = getContext();
        do {
            mServer = new RemoteServer(RemoteServer.serverPort, ctx);
            mServer.setDataReceiver(new DataReceiver() {
                @Override
                public void onTextReceived(String text) {
                    if (!TextUtils.isEmpty(text)) {
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putString("title", text);
                        intent.setAction(SearchReceiver.action);
                        intent.setPackage(ctx.getPackageName());
                        intent.setComponent(new ComponentName(ctx, SearchReceiver.class));
                        intent.putExtras(bundle);
                        ctx.sendBroadcast(intent);
                    }
                }

                @Override
                public void onApiReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_API_URL_CHANGE, url));
                }

                @Override
                public void onPushReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PUSH_URL, url));
                }
            });
            try {
                mServer.start();
                IjkMediaPlayer.setDotPort(Hawk.get(HawkConfig.DOH_URL, 0) > 0, RemoteServer.serverPort);
                break;
            } catch (IOException ex) {
                RemoteServer.serverPort++;
                mServer.stop();
            }
        } while (RemoteServer.serverPort < 9999);
    }

    public void stopServer() {
        if (mServer != null && mServer.isStarting()) {
            mServer.stop();
        }
    }
}
