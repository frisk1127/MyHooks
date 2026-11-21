package moe.frisk.myhooks.refresh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;

final class VideoSelection {
    private volatile Set<String> selected = Collections.emptySet();
    private Context context;
    private Handler worker;
    private Runnable changed;
    private boolean observing;
    private boolean listeningForUnlock;
    private volatile float limit;
    private long retryMillis = 1000;
    private final Runnable refresh = this::read;

    Set<String> packages() { return selected; }
    float userLimit() { return limit; }

    synchronized void start(Context host, Runnable onChanged) {
        if (worker != null) return;
        context = host;
        changed = onChanged;
        HandlerThread thread = new HandlerThread("MyHooks-VideoRefresh");
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(refresh);
    }

    private void registerObservers() {
        if (!listeningForUnlock) {
            BroadcastReceiver unlock = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) { schedule(); }
            };
            IntentFilter filter = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(unlock, filter, null, worker, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(unlock, filter, null, worker);
            }
            listeningForUnlock = true;
        }
        if (!observing) {
            ContentObserver observer = new ContentObserver(worker) {
                @Override public void onChange(boolean selfChange) { schedule(); }
            };
            try {
                context.getContentResolver().registerContentObserver(VideoRefreshConfig.URI, false, observer);
                context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor("user_refresh_rate"), false, observer);
                context.getContentResolver().registerContentObserver(Settings.System.getUriFor("peak_refresh_rate"), false, observer);
                observing = true;
            } catch (RuntimeException e) {
                context.getContentResolver().unregisterContentObserver(observer);
                throw e;
            }
        }
    }

    private void schedule() {
        worker.removeCallbacks(refresh);
        worker.post(refresh);
    }

    private void read() {
        try {
            registerObservers();
            float rate = Settings.Secure.getFloat(context.getContentResolver(), "user_refresh_rate", 0);
            if (rate <= 0) rate = Settings.System.getFloat(context.getContentResolver(), "peak_refresh_rate", 120);
            limit = Math.min(rate, 120);
            Bundle data = context.getContentResolver().call(VideoRefreshConfig.URI, "read", null, null);
            ArrayList<String> list = data == null ? null : data.getStringArrayList(VideoRefreshConfig.PACKAGES);
            if (list == null) throw new IllegalStateException("Missing refresh selection");
            Set<String> next = Collections.unmodifiableSet(new HashSet<>(list));
            if (!next.equals(selected)) {
                selected = next;
                if (changed != null) changed.run();
            }
            retryMillis = 1000;
        } catch (RuntimeException e) {
            if (retryMillis == 1000) XposedBridge.log("[MyHooks/video_refresh] " + e);
            worker.removeCallbacks(refresh);
            worker.postDelayed(refresh, retryMillis);
            retryMillis = Math.min(60000, retryMillis * 2);
        }
    }

}
