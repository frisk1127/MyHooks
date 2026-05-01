package moe.frisk.myhooks;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

public interface AppHook {

    String getKey();

    String getTitle();

    String getDescription();

    String[] getTargetPackages();

    void onPackageLoaded(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
