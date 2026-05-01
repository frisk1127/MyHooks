package moe.frisk.myhooks;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

import de.robv.android.xposed.XSharedPreferences;

public final class HookPreferences {

    public static final String MODULE_PACKAGE = "moe.frisk.myhooks";
    public static final String PREFS_NAME = "hook_prefs";

    private HookPreferences() {
    }

    public static boolean isHookEnabled(Context context, AppHook hook) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(hook.getKey(), true);
    }

    public static void setHookEnabled(Context context, AppHook hook, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(hook.getKey(), enabled).apply();
        ensurePrefsReadable(context);
    }

    public static boolean isHookEnabledInHost(AppHook hook) {
        XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
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
