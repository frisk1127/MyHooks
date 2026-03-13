package moe.frisk.myhooks

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        HookRegistry.hooks
            .asSequence()
            .filter { lpparam.packageName in it.targetPackages }
            .forEach { hook ->
                runCatching {
                    hook.onPackageLoaded(lpparam)
                }.onFailure { error ->
                    XposedBridge.log("[MyHooks/${hook.key}] ${error.stackTraceToString()}")
                }
            }
    }
}
