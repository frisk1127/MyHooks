package moe.frisk.myhooks.refresh;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import moe.frisk.myhooks.R;

final class AppRows extends BaseAdapter {
    static final class Entry {
        final String label, pkg, searchable;
        final ApplicationInfo info;

        Entry(String label, String pkg, ApplicationInfo info) {
            this.label = label;
            this.pkg = pkg;
            this.info = info;
            searchable = (label + "\n" + pkg).toLowerCase(Locale.ROOT);
        }
    }

    private final Activity activity;
    private final List<Entry> entries;
    private final Set<String> selected;
    private final ExecutorService workers;
    private final LruCache<String, Bitmap> icons = new LruCache<>(80);

    AppRows(Activity activity, List<Entry> entries, Set<String> selected, ExecutorService workers) {
        this.activity = activity;
        this.entries = entries;
        this.selected = selected;
        this.workers = workers;
    }

    @Override public int getCount() { return entries.size(); }
    @Override public Entry getItem(int position) { return entries.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View recycled, ViewGroup parent) {
        View row = recycled;
        Holder h;
        if (row == null) {
            row = activity.getLayoutInflater().inflate(R.layout.row_video_app, parent, false);
            h = new Holder(row, (ListView) parent);
            row.setTag(h);
        } else {
            h = (Holder) row.getTag();
        }
        Entry entry = getItem(position);
        h.label.setText(entry.label);
        h.pkg.setText(entry.info == null ? activity.getString(R.string.refresh_not_installed) : entry.pkg);
        h.check.setChecked(selected.contains(entry.pkg));
        row.setContentDescription(entry.label + ", " + h.pkg.getText());
        if (!entry.pkg.equals(h.bound)) bindIcon(h, entry);
        return row;
    }

    private void bindIcon(Holder h, Entry entry) {
        if (h.pending != null) h.pending.cancel(false);
        h.bound = entry.pkg;
        Bitmap cached = icons.get(entry.pkg);
        if (cached != null) {
            h.icon.setImageBitmap(cached);
            return;
        }
        h.icon.setImageResource(android.R.drawable.sym_def_app_icon);
        if (entry.info == null || workers.isShutdown()) return;
        int size = h.icon.getLayoutParams().width;
        h.pending = workers.submit(() -> {
            Drawable icon = entry.info.loadIcon(activity.getPackageManager());
            if (icon == null) return;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            icon = icon.mutate();
            icon.setBounds(0, 0, size, size);
            icon.draw(new Canvas(bitmap));
            icons.put(entry.pkg, bitmap);
            activity.runOnUiThread(() -> {
                if (entry.pkg.equals(h.bound) && !activity.isDestroyed()) h.icon.setImageBitmap(bitmap);
            });
        });
    }

    private static final class Holder {
        final ImageView icon;
        final TextView label, pkg;
        final CheckBox check;
        String bound;
        Future<?> pending;

        Holder(View row, ListView list) {
            icon = row.findViewById(R.id.app_icon);
            label = row.findViewById(R.id.app_label);
            pkg = row.findViewById(R.id.app_package);
            check = row.findViewById(R.id.app_checked);
            row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(CheckBox.class.getName());
                    info.setCheckable(true);
                    info.setChecked(check.isChecked());
                    info.setClickable(true);
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
                }
                @Override public boolean performAccessibilityAction(View host, int action, Bundle args) {
                    if (action != AccessibilityNodeInfo.ACTION_CLICK) return super.performAccessibilityAction(host, action, args);
                    int position = list.getPositionForView(host);
                    return position >= 0 && list.performItemClick(host, position, list.getItemIdAtPosition(position));
                }
            });
        }
    }
}
