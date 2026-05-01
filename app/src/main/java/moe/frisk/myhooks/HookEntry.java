package moe.frisk.myhooks;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (HookPreferences.MODULE_PACKAGE.equals(lpparam.packageName)) {
            hookSelf(lpparam);
            return;
        }

        for (AppHook hook : HookRegistry.HOOKS) {
            if (!isTargetPackage(hook, lpparam.packageName)) {
                continue;
            }
            if (!HookPreferences.isHookEnabledInHost(hook)) {
                continue;
            }
            try {
                hook.onPackageLoaded(lpparam);
            } catch (Throwable error) {
                XposedBridge.log("[MyHooks/" + hook.getKey() + "] "
                    + android.util.Log.getStackTraceString(error));
            }
        }
    }

    private boolean isTargetPackage(AppHook hook, String packageName) {
        String[] targets = hook.getTargetPackages();
        if (targets == null) {
            return false;
        }
        for (String target : targets) {
            if (target.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private void hookSelf(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                MainActivity.class.getName(),
                lpparam.classLoader,
                "isModuleActivated",
                XC_MethodReplacement.returnConstant(true)
            );
        } catch (Throwable error) {
            XposedBridge.log("[MyHooks/self] " + android.util.Log.getStackTraceString(error));
        }
    }
}
