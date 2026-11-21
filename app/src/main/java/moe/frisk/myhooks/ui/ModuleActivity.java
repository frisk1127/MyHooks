package moe.frisk.myhooks.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

public abstract class ModuleActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        boolean light = ColorUtils.calculateLuminance(color(com.google.android.material.R.attr.colorSurface)) > 0.5;
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(light);
        bars.setAppearanceLightNavigationBars(light);
    }

    protected void showPage(View root, boolean accountForKeyboard) {
        root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
        int types = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
        final int insetTypes = accountForKeyboard ? types | WindowInsetsCompat.Type.ime() : types;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safe = insets.getInsets(insetTypes);
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        setContentView(root);
        ViewCompat.requestApplyInsets(root);
    }

    protected int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    protected int color(int attribute) {
        return MaterialColors.getColor(this, attribute, getClass().getSimpleName());
    }

    protected GradientDrawable surface(int colorAttribute, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color(colorAttribute));
        shape.setCornerRadius(dp(radius));
        return shape;
    }
}
