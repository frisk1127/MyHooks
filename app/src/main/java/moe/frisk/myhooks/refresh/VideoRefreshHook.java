package moe.frisk.myhooks.refresh;

import android.content.Context;
import android.view.Display;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import moe.frisk.myhooks.AppHook;

public final class VideoRefreshHook implements AppHook {
    static final String POWERKEEPER = "com.miui.powerkeeper";
    private static final Pattern TARGET_60 = Pattern.compile("(^|[,\"])TargetFPS:60(?=[,\"]|$)");

    @Override public String getKey() { return VideoRefreshConfig.KEY; }
    @Override public boolean isSystemHook() { return true; }
    @Override public boolean shouldInstallInHost() { return true; }
    @Override public String getTitle() { return "应用高刷与画质增强"; }
    @Override public String getDescription() { return "选择应用，解除 PowerKeeper 的应用限帧，并让支持双芯增强的播放页请求 120 Hz。"; }
    @Override public String[] getTargetPackages() { return new String[]{"android", POWERKEEPER}; }

    @Override public void onPackageLoaded(XC_LoadPackage.LoadPackageParam lp) {
        VideoSelection selection = new VideoSelection();
        if (POWERKEEPER.equals(lp.processName)) {
            PowerKeeperRefresh.install(lp.classLoader, selection);
        } else if ("android".equals(lp.packageName)
                && ("android".equals(lp.processName) || "system_server".equals(lp.processName))) {
            try {
                hookVideoCommand(lp.classLoader, selection);
            } catch (Throwable e) {
                XposedBridge.log("[MyHooks/video_refresh] Dual DPU: " + e);
            }
            hookWindowPolicy(lp.classLoader, selection);
        }
    }

    private static void hookVideoCommand(ClassLoader loader, VideoSelection selection) {
        Class<?> dual = XposedHelpers.findClass("com.android.server.ui.display.DisplayDualDpuHandler", loader);
        Field instance = XposedHelpers.findField(dual, "mInstance");
        Field foreground = XposedHelpers.findField(dual, "mCurPackage");
        XposedHelpers.findAndHookConstructor(dual, Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                selection.start((Context) p.args[0], null);
            }
        });
        XposedHelpers.findAndHookMethod("com.android.server.ui.utils.ServiceManagerUtil", loader,
            "notifyFrameMaster", int.class, int.class, int.class, String.class, String.class,
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) throws IllegalAccessException {
                    // Only rewrite an existing video-MEMC enable command at the stock 120 Hz setting.
                    if ((int) p.args[0] != 2 || (int) p.args[1] != 1 || (int) p.args[2] != 120) return;
                    Object handler = instance.get(null);
                    if (handler == null || !selection.packages().contains(foreground.get(handler))) return;
                    if (selection.userLimit() < 119.9f) return;
                    String cmd = (String) p.args[3];
                    if (cmd != null) p.args[3] = TARGET_60.matcher(cmd).replaceFirst("$1TargetFPS:120");
                }
            });
    }

    private static void hookWindowPolicy(ClassLoader loader, VideoSelection selection) {
        Class<?> policy = XposedHelpers.findClass("com.android.server.wm.RefreshRatePolicy", loader);
        Class<?> win = XposedHelpers.findClass("com.android.server.wm.WindowState", loader);
        Field info = XposedHelpers.findField(policy, "mDisplayInfo");
        Field attrs = XposedHelpers.findField(win, "mAttrs");
        Field modes = XposedHelpers.findField(info.getType(), "supportedModes");
        XposedBridge.hookAllConstructors(policy, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Context context = (Context) XposedHelpers.getObjectField(p.args[0], "mContext");
                selection.start(context, null);
            }
        });
        XposedHelpers.findAndHookMethod(policy, "getPreferredMinRefreshRate", win, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) throws IllegalAccessException {
                Object w = p.args[0];
                WindowManager.LayoutParams a = (WindowManager.LayoutParams) attrs.get(w);
                if (a.type != WindowManager.LayoutParams.TYPE_BASE_APPLICATION
                    || !selection.packages().contains(a.packageName)
                    || !(boolean) XposedHelpers.callMethod(w, "isFocused")
                    || (boolean) XposedHelpers.callMethod(w, "inMultiWindowMode")
                    || (boolean) XposedHelpers.callMethod(w, "isAnimationRunningSelfOrParent")
                    || (boolean) XposedHelpers.callMethod(w, "isInsetsAnimationRunning")) return;
                float limit = selection.userLimit();
                Object display = info.get(p.thisObject);
                Display.Mode current = (Display.Mode) XposedHelpers.callMethod(display, "getMode");
                float original = (float) p.getResult();
                float best = original;
                for (Display.Mode mode : (Display.Mode[]) modes.get(display)) {
                    float rate = mode.getRefreshRate();
                    if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                        && mode.getPhysicalHeight() == current.getPhysicalHeight()
                        && rate > 60.1f && rate <= limit + 0.1f) best = Math.max(best, rate);
                }
                if (best > original) p.setResult(best);
            }
        });
    }
}
