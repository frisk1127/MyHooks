package moe.frisk.myhooks.securitycenter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import moe.frisk.myhooks.AppHook;

public class AutoFastChargeHook implements AppHook {

    private static final String TARGET_PACKAGE = "com.miui.securitycenter";
    private static final String LOG_TAG = "MyHooksFastCharge";
    private static final String ACTION_TURN_ON_FAST_CHARGE =
        "com.miui.powercenter.action.TURN_ON_FAST_CHARGE";
    private static final String POWER_SAVE_SERVICE =
        "com.miui.powercenter.provider.PowerSaveService";
    private static final long TRIGGER_DEBOUNCE_MS = 5000L;

    private long lastTriggerElapsed;
    private int lastPlugType = -1;

    @Override
    public String getKey() {
        return "auto_fast_charge";
    }

    @Override
    public String getTitle() {
        return "快充通知自动启用";
    }

    @Override
    public String getDescription() {
        return "手机管家检测到快充加速通知触发条件时，自动执行“立即使用”。";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void onPackageLoaded(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        Class<?> notifierClass = XposedHelpers.findClassIfExists("td.c", lpparam.classLoader);
        if (notifierClass == null) {
            log("td.c not found.");
            return;
        }

        XposedHelpers.findAndHookMethod(
            notifierClass,
            "b",
            Context.class,
            int.class,
            int.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Context context = (Context) param.args[0];
                    int watt = (Integer) param.args[1];
                    int plugType = (Integer) param.args[2];
                    if (context == null || shouldSkip(plugType)) {
                        return;
                    }

                    triggerFastCharge(context, plugType);
                    log("Auto TURN_ON_FAST_CHARGE, watt=" + watt + ", plugType=" + plugType);
                }
            }
        );

        log("Hook installed.");
    }

    private boolean shouldSkip(int plugType) {
        long now = SystemClock.elapsedRealtime();
        if (plugType == lastPlugType && now - lastTriggerElapsed < TRIGGER_DEBOUNCE_MS) {
            return true;
        }
        lastPlugType = plugType;
        lastTriggerElapsed = now;
        return false;
    }

    private void triggerFastCharge(Context context, int plugType) {
        Intent intent = new Intent(ACTION_TURN_ON_FAST_CHARGE);
        intent.setComponent(new ComponentName(TARGET_PACKAGE, POWER_SAVE_SERVICE));
        intent.putExtra("plugType", plugType);
        context.startService(intent);
    }

    private static void log(String message) {
        XposedBridge.log("[" + LOG_TAG + "] " + message);
        Log.i(LOG_TAG, message);
    }
}
