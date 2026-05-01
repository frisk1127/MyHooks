package moe.frisk.myhooks.systemui;

import android.content.ContentResolver;
import android.provider.Settings;
import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import moe.frisk.myhooks.AppHook;

public class HyperOsNavBarHook implements AppHook {

    private static final String TARGET_PACKAGE = "com.android.systemui";

    @Override
    public String getKey() {
        return "hyperos_navbar_fix";
    }

    @Override
    public String getTitle() {
        return "HyperOS 隐藏手势线保留旋转建议";
    }

    @Override
    public String getDescription() {
        return "在开启系统原生“隐藏手势提示线”时，让旋转建议按钮依然能够正常弹出。";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void onPackageLoaded(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[MyHooks/HyperOS] SystemUI Hook: Settings Deception + Alpha Zero Mode.");

        // 1. 设置欺骗：让 SystemUI 认为“隐藏手势线”开关是关着的
        hookSettings(lpparam);

        // 2. 暴力透明：不论什么 View，只要是小白条相关的，全部透明化
        hookViews(lpparam);
        
        // 3. 辅助：强制开启旋转按钮权限
        hookRotationLogic(lpparam);
    }

    private void hookSettings(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XC_MethodHook settingsHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String name = (String) param.args[1];
                    // MIUI/HyperOS 隐藏手势线的关键设置项
                    if ("hide_gesture_line".equals(name) || "force_fsg_nav_bar".equals(name)) {
                        param.setResult(0); // 返回 0，代表“未隐藏”
                    }
                }
            };

            XposedHelpers.findAndHookMethod(Settings.Global.class, "getInt", ContentResolver.class, String.class, int.class, settingsHook);
            XposedHelpers.findAndHookMethod(Settings.System.class, "getInt", ContentResolver.class, String.class, int.class, settingsHook);
            XposedHelpers.findAndHookMethod(Settings.Secure.class, "getInt", ContentResolver.class, String.class, int.class, settingsHook);
            
            XposedBridge.log("[MyHooks/HyperOS] Settings deception hooks applied.");
        } catch (Throwable e) {
            XposedBridge.log("[MyHooks/HyperOS] Settings hook failed: " + e.getMessage());
        }
    }

    private void hookViews(XC_LoadPackage.LoadPackageParam lpparam) {
        // 针对所有可能的类进行 Alpha 0 处理
        String[] viewClasses = {
            "com.android.systemui.navigationbar.gestural.NavigationHandle",
            "com.android.systemui.navigationbar.gestural.MiuiGestureLineView",
            "com.miui.systemui.navigation.GestureLineView",
            "com.android.systemui.navigationbar.buttons.NearestTouchFrame"
        };

        for (String className : viewClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (clazz == null) continue;

                // Hook setVisibility 或 onMeasure 等高频触发点，确保 Alpha 始终为 0
                XposedHelpers.findAndHookMethod(clazz, "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View v = (View) param.thisObject;
                        v.setAlpha(0f);
                        XposedBridge.log("[MyHooks/HyperOS] Forced Alpha 0 on View: " + param.thisObject.getClass().getName());
                    }
                });
            } catch (Throwable ignored) {}
        }
    }

    private void hookRotationLogic(XC_LoadPackage.LoadPackageParam lpparam) {
        // 既然我们已经欺骗了 Settings，这里的 Hook 主要是为了双重保险
        String controllerClass = "com.android.systemui.shared.rotation.RotationButtonController";
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(controllerClass, lpparam.classLoader);
            if (clazz != null) {
                XposedHelpers.findAndHookMethod(clazz, "isFloatingRotationButtonAllowed", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(true);
                    }
                });
            }
        } catch (Throwable ignored) {}
    }
}
