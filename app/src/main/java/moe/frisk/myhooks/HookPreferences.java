package moe.frisk.myhooks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import moe.frisk.myhooks.refresh.VideoRefreshConfig;

import de.robv.android.xposed.XSharedPreferences;

public final class HookPreferences {

    public static final String MODULE_PACKAGE = "moe.frisk.myhooks";
    public static final String PREFS_NAME = "hook_prefs";
    private static final String FRAMEWORK_PREFS = "framework_hooks";

    private HookPreferences() {
    }

    public static boolean isHookEnabled(Context context, AppHook hook) {
        if (VideoRefreshConfig.KEY.equals(hook.getKey())) return VideoRefreshConfig.enabled(context);
        return preferences(context, hook).getBoolean(hook.getKey(), true);
    }

    public static void setHookEnabled(Context context, AppHook hook, boolean enabled) {
        if (VideoRefreshConfig.KEY.equals(hook.getKey())) {
            VideoRefreshConfig.setEnabled(context, enabled);
            return;
        }
        SharedPreferences.Editor editor = preferences(context, hook).edit().putBoolean(hook.getKey(), enabled);
        if (hook.targetsSystemFramework()) {
            if (editor.commit()) {
                File data = context.createDeviceProtectedStorageContext().getDataDir();
                File dir = new File(data, "shared_prefs");
                data.setExecutable(true, false);
                dir.setExecutable(true, false);
                new File(dir, FRAMEWORK_PREFS + ".xml").setReadable(true, false);
            }
            return;
        }
        editor.apply();
        ensurePrefsReadable(context);
    }

    private static SharedPreferences preferences(Context context, AppHook hook) {
        if (hook.targetsSystemFramework()) {
            // system_server reads its switches before the first user unlock.
            return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(FRAMEWORK_PREFS, Context.MODE_PRIVATE);
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isHookEnabledInHost(AppHook hook) {
        XSharedPreferences prefs = hook.targetsSystemFramework()
            ? new XSharedPreferences(new File(Environment.getDataDirectory(),
                "user_de/0/" + MODULE_PACKAGE + "/shared_prefs/" + FRAMEWORK_PREFS + ".xml"))
            : new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
        prefs.reload();
        return prefs.getBoolean(hook.getKey(), true);
    }

    public static void ensurePrefsReadable(Context context) {
        try {
            File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, PREFS_NAME + ".xml");
            if (prefsDir.exists()) {
                prefsDir.setReadable(true, false);
                prefsDir.setExecutable(true, false);
            }
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
            }
        } catch (Throwable ignored) {
        }
    }
}
