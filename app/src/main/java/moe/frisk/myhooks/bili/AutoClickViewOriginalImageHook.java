package moe.frisk.myhooks.bili;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import moe.frisk.myhooks.AppHook;

public class AutoClickViewOriginalImageHook implements AppHook {

    private static final String TARGET_PACKAGE = "tv.danmaku.bili";
    private static final String DIALOG_CLASS = "androidx.activity.ComponentDialog";
    private static final String TARGET_TEXT = "查看原图";

    private static volatile boolean sHooked = false;
    private static final WeakHashMap<Dialog, Boolean> sWatchingDialogs = new WeakHashMap<>();
    private static final WeakHashMap<Dialog, View> sLastClickedTarget = new WeakHashMap<>();

    @Override
    public String getKey() {
        return "bili_auto_click_view_original";
    }

    @Override
    public String getTitle() {
        return "B 站自动点查看原图";
    }

    @Override
    public String getDescription() {
        return "在哔哩哔哩图片弹窗出现时，自动点击“查看原图”。";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
            TARGET_PACKAGE
        };
    }

    @Override
    public void onPackageLoaded(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (sHooked) {
            return;
        }
        final Class<?> dialogClass = XposedHelpers.findClassIfExists(DIALOG_CLASS, lpparam.classLoader);
        if (dialogClass == null) {
            return;
        }
        Method onStart = dialogClass.getDeclaredMethod("onStart");
        XposedBridge.hookMethod(onStart, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Dialog)) {
                    return;
                }
                startWatching((Dialog) param.thisObject);
            }
        });
        sHooked = true;
    }

    private void startWatching(final Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        final View decorView = window.getDecorView();
        if (decorView == null) {
            return;
        }
        if (Boolean.TRUE.equals(sWatchingDialogs.get(dialog))) {
            scanAndClick(dialog);
            return;
        }
        sWatchingDialogs.put(dialog, Boolean.TRUE);
        decorView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (dialog.isShowing()) {
                    scanAndClick(dialog);
                } else {
                    sWatchingDialogs.remove(dialog);
                    sLastClickedTarget.remove(dialog);
                }
            }
        });
        scanAndClick(dialog);
    }

    private void scanAndClick(final Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null || !dialog.isShowing()) {
            sWatchingDialogs.remove(dialog);
            sLastClickedTarget.remove(dialog);
            return;
        }
        final View decorView = window.getDecorView();
        if (decorView == null) {
            sWatchingDialogs.remove(dialog);
            sLastClickedTarget.remove(dialog);
            return;
        }
        decorView.post(new Runnable() {
            @Override
            public void run() {
                if (!dialog.isShowing()) {
                    sWatchingDialogs.remove(dialog);
                    sLastClickedTarget.remove(dialog);
                    return;
                }
                View target = findTargetView(decorView);
                if (target == null) {
                    sLastClickedTarget.remove(dialog);
                    return;
                }
                View lastTarget = sLastClickedTarget.get(dialog);
                if (lastTarget == target) {
                    return;
                }
                sLastClickedTarget.put(dialog, target);
                clickView(target);
            }
        });
    }

    private void clickView(View target) {
        View clickTarget = findClickableTarget(target);
        try {
            boolean clicked = clickTarget.performClick();
            if (!clicked) {
                clicked = clickTarget.callOnClick();
            }
            if (!clicked && clickTarget != target) {
                clicked = target.performClick();
                if (!clicked) {
                    target.callOnClick();
                }
            }
        } catch (Throwable e) {
            XposedBridge.log("[MyHooks/" + getKey() + "] "
                + android.util.Log.getStackTraceString(e));
        }
    }

    private View findTargetView(View root) {
        if (!root.isShown()) {
            return null;
        }
        if (isTargetTextView(root)) {
            return root;
        }
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTargetView(group.getChildAt(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean isTargetTextView(View view) {
        if (!(view instanceof TextView)) {
            return false;
        }
        CharSequence text = ((TextView) view).getText();
        if (text == null) {
            return false;
        }
        String value = text.toString().trim();
        return value.equals(TARGET_TEXT)
            || value.startsWith(TARGET_TEXT + " ")
            || value.startsWith(TARGET_TEXT + "(")
            || value.startsWith(TARGET_TEXT + "（")
            || value.contains(TARGET_TEXT);
    }

    private View findClickableTarget(View target) {
        View current = target;
        while (current != null) {
            if (current.isClickable() && current.isEnabled()) {
                return current;
            }
            Object parent = current.getParent();
            if (!(parent instanceof View)) {
                break;
            }
            current = (View) parent;
        }
        return target;
    }
}
