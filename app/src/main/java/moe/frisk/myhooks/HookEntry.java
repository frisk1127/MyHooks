package moe.frisk.myhooks;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        for (AppHook hook : HookRegistry.HOOKS) {
            if (!isTargetPackage(hook, lpparam.packageName)) {
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
}
