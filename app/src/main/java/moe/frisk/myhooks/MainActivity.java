package moe.frisk.myhooks;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

import moe.frisk.myhooks.refresh.VideoAppsActivity;
import moe.frisk.myhooks.refresh.VideoRefreshConfig;
import moe.frisk.myhooks.ui.ModuleActivity;

public class MainActivity extends ModuleActivity {
    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;
    private TextView refreshSummary;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        HookPreferences.ensurePrefsReadable(this);
        LinearLayout page = column();
        page.addView(header(), MATCH, WRAP);
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1));
        LinearLayout list = column();
        list.setPadding(dp(16), 0, dp(16), dp(24));
        scroll.addView(list);
        List<AppHook> system = new ArrayList<>();
        List<AppHook> apps = new ArrayList<>();
        for (AppHook hook : HookRegistry.HOOKS) {
            (hook.isSystemHook() ? system : apps).add(hook);
        }
        addGroup(list, R.string.group_system, system);
        addGroup(list, R.string.group_apps, apps);
        showPage(page, false);
    }

    @Override protected void onResume() {
        super.onResume();
        if (refreshSummary != null) {
            refreshSummary.setText(getString(R.string.refresh_manage_summary, VideoRefreshConfig.selectedCount(this)));
        }
    }

    private LinearLayout header() {
        LinearLayout header = row();
        header.setPaddingRelative(dp(20), dp(14), dp(16), dp(10));
        ImageView logo = new ImageView(this);
        logo.setBackground(surface(com.google.android.material.R.attr.colorSurfaceContainer, 12));
        logo.setClipToOutline(true);
        logo.setImageResource(R.drawable.ic_launcher);
        header.addView(logo, dp(44), dp(44));
        LinearLayout labels = column();
        labels.setPaddingRelative(dp(12), 0, dp(8), 0);
        header.addView(labels, new LinearLayout.LayoutParams(0, WRAP, 1));
        labels.addView(label(getString(R.string.app_name), 27, true));
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) { }
        labels.addView(label(version, 13, false));

        boolean active = isModuleActivated();
        String status = getString(active ? R.string.xposed_activated : R.string.xposed_unactivated);
        String details = getString(R.string.status_version, status, version);
        TextView badge = label(status, 13, true);
        badge.setPadding(dp(12), dp(6), dp(12), dp(6));
        badge.setBackground(surface(active ? com.google.android.material.R.attr.colorPrimaryContainer
            : com.google.android.material.R.attr.colorSurfaceContainerHigh, 20));
        badge.setTextColor(color(active ? com.google.android.material.R.attr.colorOnPrimaryContainer
            : com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout info = row();
        info.setGravity(Gravity.CENTER);
        info.setMinimumHeight(dp(48));
        info.setContentDescription(getString(R.string.module_status_label) + " · " + status);
        info.addView(badge);
        ripple(info);
        info.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.module_status_label).setMessage(details)
            .setPositiveButton(android.R.string.ok, null).show());
        header.addView(info, WRAP, WRAP);
        return header;
    }

    private void addGroup(LinearLayout list, int titleId, List<AppHook> hooks) {
        if (hooks.isEmpty()) return;
        LinearLayout heading = row();
        heading.setPaddingRelative(dp(6), dp(16), dp(8), dp(8));
        TextView title = label(getString(titleId), 15, true);
        title.setTextColor(color(com.google.android.material.R.attr.colorPrimary));
        heading.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1));
        heading.addView(label(Integer.toString(hooks.size()), 13, false));
        list.addView(heading, MATCH, WRAP);

        LinearLayout group = column();
        group.setBackground(surface(com.google.android.material.R.attr.colorSurfaceContainer, 20));
        group.setClipToOutline(true);
        list.addView(group, MATCH, WRAP);
        for (int i = 0; i < hooks.size(); i++) {
            if (i > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(color(com.google.android.material.R.attr.colorOutlineVariant));
                divider.setAlpha(0.45f);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, 1);
                p.setMarginStart(dp(62));
                p.setMarginEnd(dp(14));
                group.addView(divider, p);
            }
            group.addView(hookRow(hooks.get(i)), MATCH, WRAP);
        }
    }

    private LinearLayout hookRow(AppHook hook) {
        boolean refresh = VideoRefreshConfig.KEY.equals(hook.getKey());
        LinearLayout row = row();
        row.setPaddingRelative(dp(14), dp(12), dp(10), dp(12));
        row.setMinimumHeight(dp(76));
        ripple(row);
        ImageView icon = new ImageView(this);
        if (refresh || "stock_noto_emoji".equals(hook.getKey())) {
            icon.setImageResource(refresh ? R.drawable.ic_refresh_display : R.drawable.ic_emoji);
            icon.setPadding(dp(7), dp(7), dp(7), dp(7));
            icon.setColorFilter(color(com.google.android.material.R.attr.colorOnPrimaryContainer));
            icon.setBackground(surface(com.google.android.material.R.attr.colorPrimaryContainer, 12));
        } else {
            icon.setImageDrawable(targetIcon(hook));
        }
        row.addView(icon, dp(36), dp(36));
        LinearLayout labels = column();
        labels.setPaddingRelative(dp(12), 0, dp(6), 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView title = label(hook.getTitle(), 16, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        TextView description = label(hook.getDescription(), 13, false);
        description.setMaxLines(2);
        description.setEllipsize(TextUtils.TruncateAt.END);
        description.setPadding(0, dp(3), 0, 0);
        labels.addView(description);
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setMinHeight(dp(48));
        toggle.setContentDescription(hook.getTitle());
        toggle.setChecked(HookPreferences.isHookEnabled(this, hook));
        toggle.setOnCheckedChangeListener((button, checked) -> HookPreferences.setHookEnabled(this, hook, checked));
        row.addView(toggle, WRAP, WRAP);
        if (refresh) {
            refreshSummary = description;
            description.setTextColor(color(com.google.android.material.R.attr.colorPrimary));
            row.setOnClickListener(v -> startActivity(new Intent(this, VideoAppsActivity.class)));
        } else {
            row.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setTitle(hook.getTitle())
                .setMessage(hook.getDescription() + "\n\n" + getString(R.string.target_packages_label)
                    + "\n" + TextUtils.join("\n", hook.getTargetPackages()) + "\n\n"
                    + getString(hook.targetsSystemFramework() ? R.string.hook_reboot_note : R.string.hook_effect_note))
                .setPositiveButton(android.R.string.ok, null).show());
        }
        return row;
    }

    private Drawable targetIcon(AppHook hook) {
        PackageManager pm = getPackageManager();
        for (String name : hook.getTargetPackages()) {
            try {
                return pm.getApplicationIcon(name);
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return pm.getDefaultActivityIcon();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView label(String value, int size, boolean emphasized) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color(emphasized ? com.google.android.material.R.attr.colorOnSurface
            : com.google.android.material.R.attr.colorOnSurfaceVariant));
        if (emphasized) text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return text;
    }

    private void ripple(View view) {
        TypedValue value = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            view.setBackgroundResource(value.resourceId);
        }
        view.setFocusable(true);
    }

    public static boolean isModuleActivated() {
        return false;
    }
}
