package moe.frisk.myhooks;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

public interface AppHook {

    String getKey();

    default boolean isSystemHook() {
        return false;
    }

    String getTitle();

    String getDescription();

    String[] getTargetPackages();

    void onPackageLoaded(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
