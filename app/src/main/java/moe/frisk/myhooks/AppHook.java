package moe.frisk.myhooks;

import java.util.Arrays;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

public interface AppHook {

    String getKey();

    default boolean isSystemHook() {
        return false;
    }

    String getTitle();

    String getDescription();

    String[] getTargetPackages();

    default boolean targetsSystemFramework() {
        return Arrays.asList(getTargetPackages()).contains("android");
    }

    void onPackageLoaded(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
