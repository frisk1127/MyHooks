package moe.frisk.myhooks.refresh;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.ArraySet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class PowerKeeperRefresh {
    private PowerKeeperRefresh() {}

    static void install(ClassLoader loader, VideoSelection selection) {
        Class<?> policy = XposedHelpers.findClass("com.miui.powerkeeper.statemachine.DisplayFrameSetting", loader);
        filterAfter(policy, "parseTopDefineAppFromDb", selection, "mTopDefineApps", "mCustomApps");
        filterAfter(policy, "parseTopSmartAppFromDb", selection, "mTopSmartApps");
        filterAfter(policy, "parseTopVideoAppFromDb", selection, "mVideoApps", "mCustomApps");
        filterAfter(policy, "parseDisplayFpsAppFromDb", selection, "mDisplayFpsApps");
        XposedHelpers.findAndHookMethod(policy, "updateCustomDataBase", String.class,
            fields(selection, "mCustomApps"));
        XposedBridge.hookAllMethods(policy, "onPkgExistentChanged", fields(selection,
            "mTopDefineApps", "mTopSmartApps", "mVideoApps", "mDisplayFpsApps", "mCustomApps"));

        XposedHelpers.findAndHookMethod(policy, "getTopAppsFps", String.class, boolean.class, boolean.class,
            new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    remove60((Map<?, ?>) p.getResult(), selection.packages());
                }
            });
        XposedHelpers.findAndHookMethod(policy, "getTopVideoPkg", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                String value = (String) p.getResult();
                Set<String> selected = selection.packages();
                if (value == null || value.isEmpty() || selected.isEmpty()) return;
                ArrayList<String> kept = new ArrayList<>();
                for (String pkg : value.split(",", -1)) {
                    if (!selected.contains(pkg)) kept.add(pkg);
                }
                p.setResult(android.text.TextUtils.join(",", kept));
            }
        });
        XposedHelpers.findAndHookConstructor(policy, Context.class, Looper.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Handler handler = (Handler) p.thisObject;
                selection.start((Context) p.args[0], () -> {
                    // Use PowerKeeper's own reload messages to restore deselected packages from current stock data.
                    handler.sendEmptyMessage(4);   // fps_group
                    handler.sendEmptyMessage(5);   // fps_top_video_pkg
                    handler.sendEmptyMessage(25);  // fps_smart_group
                    handler.sendEmptyMessage(35);  // display_fps_group
                    handler.post(() -> {
                        try {
                            XposedHelpers.callMethod(handler, "setFpsList");
                            Object fg = XposedHelpers.getObjectField(handler, "mCurrentFgInfo");
                            if (fg != null) XposedHelpers.callMethod(handler, "onForegroundChanged", fg);
                        } catch (Throwable e) {
                            XposedBridge.log("[MyHooks/video_refresh] PowerKeeper reload: " + e);
                        }
                    });
                });
            }
        });
    }

    private static void filterAfter(Class<?> policy, String method, VideoSelection selection, String... names) {
        XposedHelpers.findAndHookMethod(policy, method, String.class, ArraySet.class, fields(selection, names));
    }

    private static XC_MethodHook fields(VideoSelection selection, String... names) {
        return new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Set<String> selected = selection.packages();
                if (selected.isEmpty() || p.hasThrowable()) return;
                for (String name : names) {
                    Object value = XposedHelpers.getObjectField(p.thisObject, name);
                    if (value instanceof Map) remove60((Map<?, ?>) value, selected);
                    else if (value instanceof Collection) ((Collection<?>) value).removeAll(selected);
                }
            }
        };
    }

    private static void remove60(Map<?, ?> fps, Set<String> selected) {
        if (fps == null) return;
        for (String pkg : selected) {
            if (Integer.valueOf(60).equals(fps.get(pkg))) fps.remove(pkg);
        }
    }
}
