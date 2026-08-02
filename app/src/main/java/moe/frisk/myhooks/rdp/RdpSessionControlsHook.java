package moe.frisk.myhooks.rdp;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import moe.frisk.myhooks.AppHook;

public final class RdpSessionControlsHook implements AppHook {

    private static final String TARGET_PACKAGE = "com.microsoft.rdc.androidx";
    private static final String LOG_TAG = "MyHooksRdp";
    private static final String B_BAR_CLASS = "com.microsoft.a3rdc.desktop.view.BBar";
    private static final String B_BAR_PRESENTER_CLASS =
        "com.microsoft.a3rdc.ui.presenter.session.BBarPresenter";
    private static final String LEGACY_SESSION_ACTIVITY_CLASS =
        "com.microsoft.a3rdc.ui.activities.LegacySessionActivity";

    private static final String FIELD_BAR = "myhooks_rdp_bar";
    private static final String FIELD_REVEAL_UNTIL = "myhooks_rdp_reveal_until";
    private static final String FIELD_HIDE_RUNNABLE = "myhooks_rdp_hide_runnable";
    private static final String FIELD_LAST_GESTURE = "myhooks_rdp_last_gesture";
    private static final long REVEAL_DURATION_MS = 10000L;
    private static final long GESTURE_DEBOUNCE_MS = 800L;

    private Class<?> bBarClass;
    private Class<?> sessionActivityClass;

    @Override
    public String getKey() {
        return "rdp_session_controls";
    }

    @Override
    public String getTitle() {
        return "RDP 会话控件增强";
    }

    @Override
    public String getDescription() {
        return "默认隐藏会话悬浮栏；三指轻触可临时呼出，长按音量减可直接切换原生键盘。";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void onPackageLoaded(XC_LoadPackage.LoadPackageParam lpparam) {
        bBarClass = XposedHelpers.findClassIfExists(B_BAR_CLASS, lpparam.classLoader);
        Class<?> presenterClass = XposedHelpers.findClassIfExists(
            B_BAR_PRESENTER_CLASS,
            lpparam.classLoader
        );
        sessionActivityClass = XposedHelpers.findClassIfExists(
            LEGACY_SESSION_ACTIVITY_CLASS,
            lpparam.classLoader
        );
        if (bBarClass == null || presenterClass == null || sessionActivityClass == null) {
            log("Unsupported RDP version: required session classes were not found.");
            return;
        }

        hookBarBinding(presenterClass);
        hookBarVisibility();
        hookRevealGesture();
        hookKeyboardShortcut();
        hookKeyboardButton();
        log("Session controls hooks installed.");
    }

    private void hookBarBinding(Class<?> presenterClass) {
        XposedBridge.hookAllMethods(presenterClass, "g", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args.length < 1 || !bBarClass.isInstance(param.args[0])) {
                    return;
                }
                View bar = (View) param.args[0];
                Activity activity = findActivity(bar.getContext());
                if (activity != null && sessionActivityClass.isInstance(activity)) {
                    XposedHelpers.setAdditionalInstanceField(activity, FIELD_BAR, bar);
                }
                hideBar(bar);
                log("Session toolbar bound and hidden.");
            }
        });
    }

    private void hookBarVisibility() {
        XposedHelpers.findAndHookMethod(
            View.class,
            "setVisibility",
            int.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!bBarClass.isInstance(param.thisObject)) {
                        return;
                    }
                    int visibility = (Integer) param.args[0];
                    if (visibility == View.VISIBLE && !isTemporarilyRevealed((View) param.thisObject)) {
                        param.args[0] = View.GONE;
                    }
                }
            }
        );
    }

    private void hookRevealGesture() {
        XposedHelpers.findAndHookMethod(
            Activity.class,
            "dispatchTouchEvent",
            MotionEvent.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!sessionActivityClass.isInstance(param.thisObject)) {
                        return;
                    }
                    MotionEvent event = (MotionEvent) param.args[0];
                    if (event.getActionMasked() != MotionEvent.ACTION_POINTER_DOWN
                        || event.getPointerCount() != 3) {
                        return;
                    }
                    Activity activity = (Activity) param.thisObject;
                    long now = SystemClock.elapsedRealtime();
                    Object lastValue = XposedHelpers.getAdditionalInstanceField(
                        activity,
                        FIELD_LAST_GESTURE
                    );
                    long last = lastValue instanceof Long ? (Long) lastValue : 0L;
                    if (now - last < GESTURE_DEBOUNCE_MS) {
                        return;
                    }
                    XposedHelpers.setAdditionalInstanceField(activity, FIELD_LAST_GESTURE, now);
                    toggleBar(activity);
                }
            }
        );
    }

    private void hookKeyboardShortcut() {
        XposedHelpers.findAndHookMethod(
            sessionActivityClass,
            "onKeyDown",
            int.class,
            KeyEvent.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    int keyCode = (Integer) param.args[0];
                    KeyEvent event = (KeyEvent) param.args[1];
                    if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN || event.getRepeatCount() == 0) {
                        return;
                    }
                    if (event.getRepeatCount() == 1) {
                        toggleKeyboard((Activity) param.thisObject);
                    }
                    param.setResult(true);
                }
            }
        );
    }

    private void hookKeyboardButton() {
        XposedHelpers.findAndHookMethod(
            View.class,
            "performClick",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    View clickedView = (View) param.thisObject;
                    if (!isNamedView(clickedView, "bbar_keyboard")) {
                        return;
                    }
                    Activity activity = findActivity(clickedView.getContext());
                    if (activity == null || !sessionActivityClass.isInstance(activity)) {
                        return;
                    }
                    View auxKeyboard = findView(activity, "aux_keyboard");
                    if (auxKeyboard != null && auxKeyboard.getVisibility() == View.VISIBLE) {
                        requestIme(activity, 50L, false);
                        requestIme(activity, 250L, false);
                        requestIme(activity, 650L, true);
                    }
                }
            }
        );
    }

    private void toggleBar(Activity activity) {
        Object value = XposedHelpers.getAdditionalInstanceField(activity, FIELD_BAR);
        if (!(value instanceof View)) {
            log("Toolbar gesture ignored: toolbar is not bound yet.");
            return;
        }
        View bar = (View) value;
        if (bar.getVisibility() == View.VISIBLE && bar.isShown()) {
            hideBar(bar);
            log("Session toolbar hidden by gesture.");
            return;
        }

        long revealUntil = SystemClock.elapsedRealtime() + REVEAL_DURATION_MS;
        XposedHelpers.setAdditionalInstanceField(bar, FIELD_REVEAL_UNTIL, revealUntil);
        bar.setVisibility(View.VISIBLE);
        bar.bringToFront();
        try {
            XposedHelpers.callMethod(bar, "g");
        } catch (Throwable ignored) {
        }
        scheduleHide(bar);
        log("Session toolbar revealed for 10 seconds.");
    }

    private void toggleKeyboard(Activity activity) {
        Object value = XposedHelpers.getAdditionalInstanceField(activity, FIELD_BAR);
        if (!(value instanceof View)) {
            log("Keyboard shortcut ignored: toolbar is not bound yet.");
            return;
        }
        View bar = (View) value;
        View keyboardButton = findView(bar, "bbar_keyboard");
        if (keyboardButton == null || !keyboardButton.performClick()) {
            log("Keyboard shortcut failed: native keyboard button was not found.");
            return;
        }
        hideBar(bar);
        log("Native keyboard toggled by volume shortcut.");
    }

    @SuppressWarnings("deprecation")
    private void requestIme(final Activity activity, long delayMs, final boolean force) {
        activity.getWindow().getDecorView().postDelayed(new Runnable() {
            @Override
            public void run() {
                View input = findView(activity, "textInput");
                View auxKeyboard = findView(activity, "aux_keyboard");
                if (input == null || auxKeyboard == null
                    || auxKeyboard.getVisibility() != View.VISIBLE) {
                    return;
                }
                if (Build.VERSION.SDK_INT >= 30 && input.getRootWindowInsets() != null
                    && input.getRootWindowInsets().isVisible(WindowInsets.Type.ime())) {
                    return;
                }
                input.setFocusableInTouchMode(true);
                input.requestFocusFromTouch();
                InputMethodManager inputMethodManager =
                    (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (inputMethodManager != null) {
                    inputMethodManager.restartInput(input);
                    inputMethodManager.showSoftInput(
                        input,
                        force ? InputMethodManager.SHOW_FORCED : InputMethodManager.SHOW_IMPLICIT
                    );
                }
                if (Build.VERSION.SDK_INT >= 30
                    && activity.getWindow().getInsetsController() != null) {
                    activity.getWindow().getInsetsController().show(WindowInsets.Type.ime());
                }
            }
        }, delayMs);
    }

    private void scheduleHide(final View bar) {
        Object oldValue = XposedHelpers.getAdditionalInstanceField(bar, FIELD_HIDE_RUNNABLE);
        if (oldValue instanceof Runnable) {
            bar.removeCallbacks((Runnable) oldValue);
        }
        Runnable hideRunnable = new Runnable() {
            @Override
            public void run() {
                hideBar(bar);
                log("Session toolbar auto-hidden.");
            }
        };
        XposedHelpers.setAdditionalInstanceField(bar, FIELD_HIDE_RUNNABLE, hideRunnable);
        bar.postDelayed(hideRunnable, REVEAL_DURATION_MS);
    }

    private boolean isTemporarilyRevealed(View bar) {
        Object value = XposedHelpers.getAdditionalInstanceField(bar, FIELD_REVEAL_UNTIL);
        return value instanceof Long && SystemClock.elapsedRealtime() < (Long) value;
    }

    private View findView(Activity activity, String resourceName) {
        int id = activity.getResources().getIdentifier(resourceName, "id", TARGET_PACKAGE);
        return id == 0 ? null : activity.findViewById(id);
    }

    private View findView(View root, String resourceName) {
        int id = root.getResources().getIdentifier(resourceName, "id", TARGET_PACKAGE);
        return id == 0 ? null : root.findViewById(id);
    }

    private boolean isNamedView(View view, String resourceName) {
        int id = view.getResources().getIdentifier(resourceName, "id", TARGET_PACKAGE);
        return id != 0 && view.getId() == id;
    }

    private void hideBar(View bar) {
        Object hideRunnable = XposedHelpers.getAdditionalInstanceField(bar, FIELD_HIDE_RUNNABLE);
        if (hideRunnable instanceof Runnable) {
            bar.removeCallbacks((Runnable) hideRunnable);
            XposedHelpers.removeAdditionalInstanceField(bar, FIELD_HIDE_RUNNABLE);
        }
        XposedHelpers.setAdditionalInstanceField(bar, FIELD_REVEAL_UNTIL, 0L);
        bar.setVisibility(View.GONE);
    }

    private Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current) {
                break;
            }
            current = base;
        }
        return null;
    }

    private static void log(String message) {
        XposedBridge.log("[" + LOG_TAG + "] " + message);
        Log.i(LOG_TAG, message);
    }
}
