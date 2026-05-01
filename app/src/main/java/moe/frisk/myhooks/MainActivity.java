package moe.frisk.myhooks;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private int colorBackground;
    private int colorSurfaceContainer;
    private int colorSurfaceContainerHigh;
    private int colorOnSurface;
    private int colorOnSurfaceVariant;
    private int colorPrimary;
    private int colorPrimaryContainer;
    private int colorOnPrimaryContainer;
    private int colorSecondaryContainer;
    private int colorOnSecondaryContainer;
    private int colorTertiaryContainer;
    private int colorOnTertiaryContainer;
    private int colorOutline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadColors();
        applySystemBars();
        HookPreferences.ensurePrefsReadable(this);
        setContentView(createContentView());
    }

    public static boolean isModuleActivated() {
        return false;
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(colorBackground);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(createHeader());
        root.addView(createStatusPanel());
        root.addView(createHookSectionHeader());

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        for (int i = 0; i < HookRegistry.HOOKS.length; i++) {
            list.addView(createHookCard(HookRegistry.HOOKS[i], i));
        }
        return scrollView;
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, dp(8), 0, dp(14));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(colorOnSurface);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        header.addView(title);

        TextView summary = new TextView(this);
        summary.setText(R.string.hook_list_summary);
        summary.setTextColor(colorOnSurfaceVariant);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        summary.setPadding(0, dp(10), 0, 0);
        summary.setLineSpacing(0, 1.12f);
        header.addView(summary);

        return header;
    }

    private View createStatusPanel() {
        final boolean activated = isModuleActivated();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(roundRect(
            activated ? colorPrimaryContainer : colorTertiaryContainer,
            dp(24)
        ));

        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelParams.setMargins(0, dp(8), 0, dp(18));
        panel.setLayoutParams(panelParams);

        TextView marker = new TextView(this);
        marker.setGravity(Gravity.CENTER);
        marker.setText(activated ? "ON" : "OFF");
        marker.setTextColor(activated ? colorOnPrimaryContainer : colorOnTertiaryContainer);
        marker.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        marker.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        marker.setBackground(roundRect(colorBackground, dp(18)));
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(56), dp(36));
        markerParams.setMargins(0, 0, dp(14), 0);
        panel.addView(marker, markerParams);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        panel.addView(textColumn, new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ));

        TextView label = new TextView(this);
        label.setText(R.string.module_status_label);
        label.setTextColor(activated ? colorOnPrimaryContainer : colorOnTertiaryContainer);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textColumn.addView(label);

        TextView value = new TextView(this);
        value.setText(activated ? R.string.xposed_activated : R.string.xposed_unactivated);
        value.setTextColor(activated ? colorOnPrimaryContainer : colorOnTertiaryContainer);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setPadding(0, dp(4), 0, 0);
        textColumn.addView(value);

        return panel;
    }

    private View createHookSectionHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(10));

        TextView title = new TextView(this);
        title.setText(R.string.hook_list_title);
        title.setTextColor(colorOnSurface);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ));

        TextView count = new TextView(this);
        count.setText(getString(R.string.hook_count_format, HookRegistry.HOOKS.length));
        count.setTextColor(colorOnSecondaryContainer);
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        count.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        count.setPadding(dp(12), dp(7), dp(12), dp(7));
        count.setBackground(roundRect(colorSecondaryContainer, dp(16)));
        row.addView(count);

        return row;
    }

    private View createHookCard(final AppHook hook, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(roundRect(colorSurfaceContainer, dp(22)));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, index == 0 ? 0 : dp(10), 0, 0);
        card.setLayoutParams(cardParams);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top);

        TextView title = new TextView(this);
        title.setText(hook.getTitle());
        title.setTextColor(colorOnSurface);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLineSpacing(0, 1.08f);
        top.addView(title, new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ));

        final Switch toggle = new Switch(this);
        toggle.setChecked(HookPreferences.isHookEnabled(this, hook));
        toggle.setContentDescription(hook.getTitle());
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                HookPreferences.setHookEnabled(MainActivity.this, hook, isChecked);
            }
        });
        top.addView(toggle);

        TextView desc = new TextView(this);
        desc.setText(hook.getDescription());
        desc.setTextColor(colorOnSurfaceVariant);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        desc.setLineSpacing(0, 1.12f);
        desc.setPadding(0, dp(10), 0, 0);
        card.addView(desc);

        TextView targets = new TextView(this);
        targets.setText(buildTargetSummary(hook));
        targets.setTextColor(colorOnSurfaceVariant);
        targets.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        targets.setPadding(0, dp(12), 0, 0);
        card.addView(targets);

        TextView info = new TextView(this);
        info.setText(R.string.hook_more_info);
        info.setTextColor(colorPrimary);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        info.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.setPadding(0, dp(12), 0, 0);
        info.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHookInfo(hook);
            }
        });
        card.addView(info);

        return card;
    }

    private void showHookInfo(AppHook hook) {
        new AlertDialog.Builder(this)
            .setTitle(hook.getTitle())
            .setMessage(buildHookInfoMessage(hook))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private String buildHookInfoMessage(AppHook hook) {
        StringBuilder sb = new StringBuilder();
        sb.append(hook.getDescription()).append("\n\n");
        sb.append(getString(R.string.target_packages_label)).append("\n");
        String[] targets = hook.getTargetPackages();
        if (targets != null) {
            for (int i = 0; i < targets.length; i++) {
                sb.append("- ").append(targets[i]).append('\n');
            }
        }
        sb.append('\n').append(getString(R.string.hook_effect_note));
        return sb.toString();
    }

    private String buildTargetSummary(AppHook hook) {
        String[] targets = hook.getTargetPackages();
        if (targets == null || targets.length == 0) {
            return getString(R.string.target_packages_label) + ": -";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.target_packages_label)).append(": ");
        for (int i = 0; i < targets.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(targets[i]);
        }
        return sb.toString();
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void loadColors() {
        colorBackground = color(R.color.md3_background);
        colorSurfaceContainer = color(R.color.md3_surface_container);
        colorSurfaceContainerHigh = color(R.color.md3_surface_container_high);
        colorOnSurface = color(R.color.md3_on_surface);
        colorOnSurfaceVariant = color(R.color.md3_on_surface_variant);
        colorPrimary = color(R.color.md3_primary);
        colorPrimaryContainer = color(R.color.md3_primary_container);
        colorOnPrimaryContainer = color(R.color.md3_on_primary_container);
        colorSecondaryContainer = color(R.color.md3_secondary_container);
        colorOnSecondaryContainer = color(R.color.md3_on_secondary_container);
        colorTertiaryContainer = color(R.color.md3_tertiary_container);
        colorOnTertiaryContainer = color(R.color.md3_on_tertiary_container);
        colorOutline = color(R.color.md3_outline);

        if (colorSurfaceContainerHigh == colorOutline) {
            colorSurfaceContainerHigh = colorSurfaceContainer;
        }
    }

    private void applySystemBars() {
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(colorBackground);
            getWindow().setNavigationBarColor(colorBackground);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            );
        } else if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private int color(int resId) {
        if (Build.VERSION.SDK_INT >= 23) {
            return getColor(resId);
        }
        return getResources().getColor(resId);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            getResources().getDisplayMetrics()
        ));
    }
}
