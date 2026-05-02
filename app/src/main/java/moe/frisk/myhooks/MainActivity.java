package moe.frisk.myhooks;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.WindowCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * MainActivity designed strictly following Material Design 3 (MD3) Expressive specifications.
 * Uses MD3 tokens for color, typography, and shape.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        applySystemBars();
        HookPreferences.ensurePrefsReadable(this);
        setContentView(createContentView());
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        // Token: md.sys.color.surface
        scrollView.setBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurface));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), 0, dp(16), dp(48));
        scrollView.addView(root, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 1. Large Top App Bar - Role: Headline Large (32sp)
        root.addView(createLargeTopAppBar());

        // 2. Status Widget - Role: primary-container / error-container
        root.addView(createStatusHero());

        // 3. Section Header - Role: Label Large (Emphasized)
        root.addView(createSectionHeader(getString(R.string.hook_list_title)));

        // 4. Hook Group - Role: surface-container (Shape: Extra Large 28dp)
        root.addView(createHookListGroup());

        root.addView(createFooter());

        return scrollView;
    }

    private View createLargeTopAppBar() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(12), dp(64), dp(12), dp(28));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        // Token: md.sys.color.on-surface
        title.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
        // Token: md.sys.typescale.headline-large (32sp)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLetterSpacing(-0.02f); // Emphasized feel
        header.addView(title);

        TextView summary = new TextView(this);
        summary.setText(R.string.hook_list_summary);
        // Token: md.sys.color.on-surface-variant
        summary.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        // Token: md.sys.typescale.body-medium (14sp)
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        summary.setPadding(0, dp(16), 0, 0);
        summary.setLineSpacing(0, 1.4f);
        header.addView(summary);

        return header;
    }

    private View createStatusHero() {
        final boolean activated = isModuleActivated();
        MaterialCardView card = new MaterialCardView(this);
        // Tokens: primary-container / error-container
        card.setCardBackgroundColor(themeColor(activated ? com.google.android.material.R.attr.colorPrimaryContainer : com.google.android.material.R.attr.colorErrorContainer));
        // Token: md.sys.shape.corner.extra-large (28dp)
        card.setRadius(dp(28));
        card.setCardElevation(0);
        card.setStrokeWidth(0);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(32));
        card.setLayoutParams(params);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(24), dp(28), dp(24));
        card.addView(content);

        // Tokens: on-primary-container / on-error-container
        int textColor = themeColor(activated ? com.google.android.material.R.attr.colorOnPrimaryContainer : com.google.android.material.R.attr.colorOnErrorContainer);

        TextView label = new TextView(this);
        label.setText(R.string.module_status_label);
        label.setTextColor(textColor);
        // Token: md.sys.typescale.label-large (14sp, emphasized)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setAlpha(0.7f);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setAllCaps(true);
        label.setLetterSpacing(0.08f);
        content.addView(label);

        TextView statusText = new TextView(this);
        statusText.setText(activated ? R.string.xposed_activated : R.string.xposed_unactivated);
        statusText.setTextColor(textColor);
        // Token: md.sys.typescale.headline-medium (28sp)
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        statusText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statusText.setPadding(0, dp(6), 0, 0);
        content.addView(statusText);

        return card;
    }

    private View createSectionHeader(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        // Token: md.sys.color.primary
        title.setTextColor(themeColor(com.google.android.material.R.attr.colorPrimary));
        // Token: md.sys.typescale.label-large (14sp)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(12), 0, dp(12), dp(16));
        title.setAllCaps(true);
        title.setLetterSpacing(0.15f);
        return title;
    }

    private View createHookListGroup() {
        MaterialCardView group = new MaterialCardView(this);
        // Token: md.sys.color.surface-container
        group.setCardBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainer));
        // Token: md.sys.shape.corner.extra-large (28dp)
        group.setRadius(dp(28));
        group.setCardElevation(0);
        group.setStrokeWidth(dp(1));
        // Token: md.sys.color.outline-variant
        group.setStrokeColor(themeColor(com.google.android.material.R.attr.colorOutlineVariant));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        group.addView(container);

        AppHook[] hooks = HookRegistry.HOOKS;
        for (int i = 0; i < hooks.length; i++) {
            container.addView(createHookItem(hooks[i]));
            if (i < hooks.length - 1) {
                MaterialDivider divider = new MaterialDivider(this);
                divider.setDividerColor(themeColor(com.google.android.material.R.attr.colorOutlineVariant));
                // Align with text inset
                divider.setDividerInsetStart(dp(24));
                divider.setDividerInsetEnd(dp(24));
                container.addView(divider);
            }
        }

        return group;
    }

    private View createHookItem(final AppHook hook) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(24), dp(20), dp(24), dp(20));
        item.setClickable(true);
        item.setFocusable(true);
        
        // MD3 Ripple effect
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        item.setBackgroundResource(outValue.resourceId);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        item.addView(top);

        TextView title = new TextView(this);
        title.setText(hook.getTitle());
        title.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
        // Token: md.sys.typescale.title-large (20sp) - Reduced for better multi-line
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLineSpacing(0, 1.15f);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(HookPreferences.isHookEnabled(this, hook));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            HookPreferences.setHookEnabled(MainActivity.this, hook, isChecked);
        });
        // Ensure switch has proper MD3 spacing
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        toggleParams.setMarginStart(dp(16));
        toggleParams.gravity = Gravity.TOP; // Align to top when title wraps
        top.addView(toggle, toggleParams);

        TextView desc = new TextView(this);
        desc.setText(hook.getDescription());
        desc.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        // Token: md.sys.typescale.body-medium (14sp)
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        desc.setPadding(0, dp(10), 0, 0);
        desc.setLineSpacing(0, 1.35f);
        item.addView(desc);

        item.setOnClickListener(v -> showHookInfo(hook));

        return item;
    }

    private void showHookInfo(AppHook hook) {
        // MaterialAlertDialogBuilder strictly follows MD3 dialog tokens
        new MaterialAlertDialogBuilder(this)
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
            for (String target : targets) {
                sb.append("• ").append(target).append("\n");
            }
        }
        sb.append("\n").append(getString(R.string.hook_effect_note));
        return sb.toString();
    }

    private View createFooter() {
        TextView footer = new TextView(this);
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            footer.setText("Version " + versionName);
        } catch (Exception e) {
            footer.setText("MyHooks");
        }
        footer.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        // Token: md.sys.typescale.label-small (11sp)
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        footer.setAlpha(0.5f);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(64), 0, dp(16));
        return footer;
    }

    private void applySystemBars() {
        // Use modern WindowCompat for edge-to-edge system bars configuration
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        if (Build.VERSION.SDK_INT >= 21) {
            // Set bars transparent to allow content to flow under or color matching
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }
    }

    public static boolean isModuleActivated() {
        return false;
    }

    private int themeColor(int attrResId) {
        return MaterialColors.getColor(this, attrResId, 0);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            getResources().getDisplayMetrics()
        ));
    }
}
