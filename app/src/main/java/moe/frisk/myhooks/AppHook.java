package moe.frisk.myhooks;

import java.util.Set;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

public interface AppHook {

    String getKey();

    Set<String> getTargetPackages();

    void onPackageLoaded(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
