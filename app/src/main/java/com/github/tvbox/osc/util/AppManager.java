package com.github.tvbox.osc.util;

import android.app.Activity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 *
 * P0 fix: 栈里只存 Activity 的弱引用，避免异常路径下 Activity 销毁但 Stack 仍持有导致泄漏。
 * 所有取值入口统一过滤 null / isFinishing / isDestroyed。
 */
public class AppManager {
    private static Stack<WeakReference<Activity>> activityStack;

    private AppManager() {
    }

    private static class SingleHolder {
        private static AppManager instance = new AppManager();
    }

    public static AppManager getInstance() {
        return SingleHolder.instance;
    }

    /**
     * 添加Activity到堆栈
     */
    public void addActivity(Activity activity) {
        if (activityStack == null) {
            activityStack = new Stack<>();
        }
        // 先把已失效的清理掉
        pruneDeadActivities();
        activityStack.add(new WeakReference<>(activity));
    }

    /** 清理掉弱引用已失效 / Activity 已 finish 的条目 */
    private void pruneDeadActivities() {
        if (activityStack == null) return;
        Iterator<WeakReference<Activity>> it = activityStack.iterator();
        while (it.hasNext()) {
            WeakReference<Activity> ref = it.next();
            Activity a = ref.get();
            if (a == null || a.isFinishing() || isActivityDestroyed(a)) {
                it.remove();
            }
        }
    }

    private static boolean isActivityDestroyed(Activity activity) {
        try {
            return activity.isDestroyed();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 是否有activity
     */
    public boolean isActivity() {
        pruneDeadActivities();
        return activityStack != null && !activityStack.isEmpty();
    }

    /**
     * 获取当前Activity（堆栈中最后一个压入的）
     */
    public Activity currentActivity() {
        pruneDeadActivities();
        if (activityStack == null || activityStack.isEmpty()) return null;
        WeakReference<Activity> ref = activityStack.lastElement();
        return ref == null ? null : ref.get();
    }

    /**
     * 结束当前Activity（堆栈中最后一个压入的）
     */
    public void finishActivity() {
        Activity activity = currentActivity();
        if (activity != null && !activity.isFinishing()) {
            activity.finish();
        }
    }

    public void finishActivity(Activity activity) {
        if (activityStack == null) return;
        Iterator<WeakReference<Activity>> it = activityStack.iterator();
        while (it.hasNext()) {
            Activity a = it.next().get();
            if (a == activity) {
                it.remove();
                break;
            }
        }
    }


    /**
     * 结束指定类名的Activity
     */
    public void finishActivity(Class<?> cls) {
        if (activityStack == null) return;
        Iterator<WeakReference<Activity>> it = activityStack.iterator();
        while (it.hasNext()) {
            WeakReference<Activity> ref = it.next();
            Activity a = ref.get();
            if (a != null && a.getClass().equals(cls)) {
                if (!a.isFinishing()) {
                    a.finish();
                }
                it.remove();
                break;
            }
        }
    }

    public void backActivity(Class<?> cls) {
        if (activityStack == null) return;
        while (!activityStack.empty()) {
            WeakReference<Activity> ref = activityStack.pop();
            Activity a = ref == null ? null : ref.get();
            if (a != null && a.getClass().equals(cls)) {
                activityStack.push(ref);
                break;
            } else if (a != null && !a.isFinishing()) {
                a.finish();
            }
        }
    }

    /**
     * 结束所有Activity
     */
    public void finishAllActivity() {
        if (activityStack != null && activityStack.size() > 0) {
            for (int i = 0, size = activityStack.size(); i < size; i++) {
                WeakReference<Activity> ref = activityStack.get(i);
                Activity activity = ref == null ? null : ref.get();
                if (activity != null && !activity.isFinishing()) {
                    activity.finish();
                }
            }
            activityStack.clear();
        }
    }

    /**
     * 获取指定的Activity
     */
    public Activity getActivity(Class<?> cls) {
        pruneDeadActivities();
        if (activityStack == null) return null;
        for (WeakReference<Activity> ref : activityStack) {
            Activity a = ref.get();
            if (a != null && a.getClass().equals(cls)) {
                return a;
            }
        }
        return null;
    }

    public void appExit(int code) {
        try {
            finishAllActivity();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(code);
        } catch (Exception e) {
            if (activityStack != null) activityStack.clear();
            e.printStackTrace();
        }
    }
}
