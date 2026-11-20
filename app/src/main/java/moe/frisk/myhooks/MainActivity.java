package moe.frisk.myhooks;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        HookPreferences.ensurePrefsReadable(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean light = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            != Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(light);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(light);

        LinearLayout page = column();
        page.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
        ViewCompat.setOnApplyWindowInsetsListener(page, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        page.addView(header(), MATCH, WRAP);
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1));
        LinearLayout list = column();
        list.setPadding(0, dp(8), 0, dp(24));
        scroll.addView(list);
        List<AppHook> system = new ArrayList<>();
        List<AppHook> apps = new ArrayList<>();
        for (AppHook hook : HookRegistry.HOOKS) {
            (hook.isSystemHook() ? system : apps).add(hook);
        }
        addGroup(list, R.string.group_system, system);
        addGroup(list, R.string.group_apps, apps);
        setContentView(page);
    }

    private LinearLayout header() {
        LinearLayout row = row();
        row.setPadding(dp(16), dp(12), dp(8), dp(12));
        row.setBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainer));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        row.addView(logo, dp(40), dp(40));
        LinearLayout labels = column();
        labels.setPadding(dp(12), 0, 0, 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, WRAP, 1));
        labels.addView(label(getString(R.string.app_name), 24, true));
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) { }
        String status = getString(isModuleActivated() ? R.string.xposed_activated : R.string.xposed_unactivated);
        TextView subtitle = label(getString(R.string.status_version, status, version), 14, false);
        labels.addView(subtitle);
        ImageButton info = new ImageButton(this);
        info.setImageResource(R.drawable.ic_module_info);
        info.setColorFilter(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        info.setContentDescription(getString(R.string.module_status_label));
        ripple(info);
        info.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name).setMessage(subtitle.getText())
            .setPositiveButton(android.R.string.ok, null).show());
        row.addView(info, dp(48), dp(48));
        return row;
    }

    private void addGroup(LinearLayout list, int titleId, List<AppHook> hooks) {
        if (hooks.isEmpty()) return;
        LinearLayout heading = row();
        heading.setPadding(dp(20), dp(14), dp(20), dp(6));
        TextView title = label(getString(titleId), 16, true);
        title.setTextColor(color(com.google.android.material.R.attr.colorPrimary));
        heading.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1));
        heading.addView(label(Integer.toString(hooks.size()), 14, false));
        list.addView(heading, MATCH, WRAP);
        for (int i = 0; i < hooks.size(); i++) {
            if (i > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(color(com.google.android.material.R.attr.colorOutlineVariant));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, dp(1));
                p.setMarginStart(dp(76));
                list.addView(divider, p);
            }
            list.addView(hookRow(hooks.get(i)), MATCH, WRAP);
        }
    }

    private LinearLayout hookRow(AppHook hook) {
        LinearLayout row = row();
        row.setPadding(dp(16), dp(12), dp(12), dp(12));
        ripple(row);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(targetIcon(hook));
        row.addView(icon, dp(48), dp(48));
        LinearLayout labels = column();
        labels.setPadding(dp(12), 0, dp(8), 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView title = label(hook.getTitle(), 18, true);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        TextView description = label(hook.getDescription(), 14, false);
        description.setPadding(0, dp(4), 0, 0);
        labels.addView(description);
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setContentDescription(hook.getTitle());
        toggle.setChecked(HookPreferences.isHookEnabled(this, hook));
        toggle.setOnCheckedChangeListener((button, checked) -> HookPreferences.setHookEnabled(this, hook, checked));
        row.addView(toggle, WRAP, WRAP);
        row.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setTitle(hook.getTitle())
            .setMessage(hook.getDescription() + "\n\n" + getString(R.string.target_packages_label)
                + "\n" + TextUtils.join("\n", hook.getTargetPackages()) + "\n\n" + getString(R.string.hook_effect_note))
            .setPositiveButton(android.R.string.ok, null).show());
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

    private TextView label(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color(bold ? com.google.android.material.R.attr.colorOnSurface
            : com.google.android.material.R.attr.colorOnSurfaceVariant));
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private void ripple(View view) {
        TypedValue value = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            view.setBackgroundResource(value.resourceId);
        }
        view.setFocusable(true);
    }

    private int color(int attr) {
        return MaterialColors.getColor(this, attr, 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static boolean isModuleActivated() {
        return false;
    }
}
