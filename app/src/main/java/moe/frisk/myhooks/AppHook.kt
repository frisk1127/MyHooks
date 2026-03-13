package moe.frisk.myhooks

import de.robv.android.xposed.callbacks.XC_LoadPackage

interface AppHook {
    val key: String
    val targetPackages: Set<String>
    fun onPackageLoaded(lpparam: XC_LoadPackage.LoadPackageParam)
}
