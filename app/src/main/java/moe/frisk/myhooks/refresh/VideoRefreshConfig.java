package moe.frisk.myhooks.refresh;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import moe.frisk.myhooks.HookPreferences;

public final class VideoRefreshConfig {
    public static final String KEY = "video_refresh";
    static final Uri URI = Uri.parse("content://moe.frisk.myhooks.video_refresh/selection");
    static final String PACKAGES = "packages";
    private static final Set<String> DEFAULT = Collections.singleton("tv.danmaku.bili");

    private VideoRefreshConfig() {}

    private static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext().getSharedPreferences(KEY, Context.MODE_PRIVATE);
    }

    public static synchronized boolean enabled(Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs.contains("enabled")) return prefs.getBoolean("enabled", true);
        // Preserve the switch from the earlier Bilibili-only version.
        if (!context.getSystemService(UserManager.class).isUserUnlocked()) return false;
        boolean value = context.getSharedPreferences(HookPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("bili_player_high_refresh_rate", true);
        prefs.edit().putBoolean("enabled", value).commit();
        return value;
    }

    public static synchronized void setEnabled(Context context, boolean enabled) {
        if (prefs(context).edit().putBoolean("enabled", enabled).commit()) notifyHosts(context);
    }

    static Set<String> packages(Context context) {
        return new HashSet<>(prefs(context).getStringSet(PACKAGES, DEFAULT));
    }

    public static int selectedCount(Context context) {
        return prefs(context).getStringSet(PACKAGES, DEFAULT).size();
    }

    static boolean save(Context context, Set<String> packages) {
        boolean saved = prefs(context).edit().putStringSet(PACKAGES, new HashSet<>(packages)).commit();
        if (saved) notifyHosts(context);
        return saved;
    }

    static Bundle snapshot(Context context) {
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(PACKAGES, enabled(context)
            ? new ArrayList<>(packages(context)) : new ArrayList<>());
        return bundle;
    }

    private static void notifyHosts(Context context) {
        context.getContentResolver().notifyChange(URI, null, 0);
    }
}
