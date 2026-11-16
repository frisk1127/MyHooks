package moe.frisk.myhooks.systemui;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class HyperOs4NavBarHook {
    private static final String NAVIGATION_BAR = "com.android.systemui.navigationbar.views.NavigationBar";
    private static final String INJECTOR = "com.android.systemui.statusbar.phone.NavigationModeControllerInjector";
    private static final String RESTORE_INJECTOR = "myhooks.restoreGestureLineInjector";
    private static final String RESTORE_MESSAGE = "myhooks.restoreNavigationMessage";

    private final List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
    private volatile boolean hideGestureLine;
    private Context context;
    private Object controller;
    private boolean insetStateLogged;
    private boolean insetErrorLogged;

    void install(ClassLoader loader) throws Throwable {
        Class<?> navigationBar = XposedHelpers.findClass(NAVIGATION_BAR, loader);
        Class<?> navigationController = XposedHelpers.findClass(
            "com.android.systemui.navigationbar.NavigationBarControllerImpl", loader);
        Class<?> injector = XposedHelpers.findClass(INJECTOR, loader);
        Class<?> handler = XposedHelpers.findClass(INJECTOR + "$mHandler$1", loader);
        Class<?> handle = XposedHelpers.findClass(
            "com.android.systemui.navigationbar.gestural.NavigationHandle", loader);
        Class<?> windowManager = XposedHelpers.findClass("android.view.WindowManagerImpl", loader);

        // Resolve critical members before installing any hooks in SystemUI.
        XposedHelpers.findField(injector, "mHideGestureLine");
        XposedHelpers.findField(injector, "mIsFsgMode");
        XposedHelpers.findField(navigationController, "mNavigationModeControllerInjector");
        XposedHelpers.findField(navigationController, "mNavigationBars");
        XposedHelpers.findField(navigationController, "mContext");
        XposedHelpers.findField(injector, "context");
        XposedHelpers.findField(navigationBar, "mView");
        XposedHelpers.findField(handler, "this$0");
        XposedHelpers.findField(WindowManager.LayoutParams.class, "providedInsets");
        XposedHelpers.findMethodExact(navigationBar, "repositionNavigationBar", int.class);
        Method create = findCreateNavigationBar(navigationController);
        Method layout = XposedHelpers.findMethodExact(navigationBar, "getBarLayoutParamsForRotation", int.class);
        Method message = XposedHelpers.findMethodExact(handler, "handleMessage", Message.class);
        Method draw = XposedHelpers.findMethodExact(handle, "onDraw", Canvas.class);

        try {
            hooks.add(XposedBridge.hookMethod(create, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Display display = (Display) param.args[0];
                    if (display == null || display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                        return;
                    }
                    observeSettings((Context) XposedHelpers.getObjectField(param.thisObject, "mContext"));
                    readSettings();
                    Object mode = XposedHelpers.getObjectField(param.thisObject, "mNavigationModeControllerInjector");
                    if (hideGestureLine && XposedHelpers.getBooleanField(mode, "mIsFsgMode")
                        && XposedHelpers.getBooleanField(mode, "mHideGestureLine")) {
                        XposedHelpers.setBooleanField(mode, "mHideGestureLine", false);
                        param.setObjectExtra(RESTORE_INJECTOR, mode);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object mode = param.getObjectExtra(RESTORE_INJECTOR);
                    if (mode != null) {
                        XposedHelpers.setBooleanField(mode, "mHideGestureLine", true);
                    }
                }
            }));
            hooks.add(XposedBridge.hookMethod(message, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object mode = XposedHelpers.getObjectField(param.thisObject, "this$0");
                    observeSettings((Context) XposedHelpers.getObjectField(mode, "context"));
                    readSettings();
                    Message msg = (Message) param.args[0];
                    if (msg != null && msg.what == 1 && hideGestureLine
                        && XposedHelpers.getBooleanField(mode, "mIsFsgMode")) {
                        // Only replace the gesture-setting removal; configuration rebuilds still work.
                        param.setObjectExtra(RESTORE_MESSAGE, msg.what);
                        msg.what = 0;
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object what = param.getObjectExtra(RESTORE_MESSAGE);
                    if (what != null) {
                        ((Message) param.args[0]).what = (Integer) what;
                    }
                    if (!param.hasThrowable()) {
                        refreshNavigationBar();
                    }
                }
            }));
            hooks.add(XposedBridge.hookMethod(layout, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (hideGestureLine && param.getResult() instanceof WindowManager.LayoutParams) {
                        WindowManager.LayoutParams params = (WindowManager.LayoutParams) param.getResult();
                        if (isDefaultNavigationBar(params)) {
                            clearVisualInsets(params);
                        }
                    }
                }
            }));
            XC_MethodHook windowHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (hideGestureLine && param.args[1] instanceof WindowManager.LayoutParams) {
                        WindowManager.LayoutParams params = (WindowManager.LayoutParams) param.args[1];
                        if (isDefaultNavigationBar(params)) {
                            clearVisualInsets(params);
                        }
                    }
                }
            };
            hooks.add(XposedHelpers.findAndHookMethod(windowManager, "addView",
                View.class, ViewGroup.LayoutParams.class, windowHook));
            hooks.add(XposedHelpers.findAndHookMethod(windowManager, "updateViewLayout",
                View.class, ViewGroup.LayoutParams.class, windowHook));
            hooks.add(XposedBridge.hookMethod(draw, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    Display display = view.getDisplay();
                    if (hideGestureLine && display != null
                        && display.getDisplayId() == Display.DEFAULT_DISPLAY) {
                        param.setResult(null);
                    }
                }
            }));
            hooks.addAll(XposedBridge.hookAllConstructors(injector, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) {
                        observeSettings((Context) XposedHelpers.getObjectField(param.thisObject, "context"));
                    }
                }
            }));
            hooks.addAll(XposedBridge.hookAllConstructors(navigationController, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) {
                        controller = param.thisObject;
                        observeSettings((Context) XposedHelpers.getObjectField(controller, "mContext"));
                    }
                }
            }));
            log("HyperOS 4 independent gesture-line hiding installed; native rotation policy preserved.");
        } catch (Throwable error) {
            for (XC_MethodHook.Unhook hook : hooks) {
                hook.unhook();
            }
            hooks.clear();
            throw error;
        }
    }

    private Method findCreateNavigationBar(Class<?> clazz) throws NoSuchMethodException {
        for (Method method : clazz.getDeclaredMethods()) {
            Class<?>[] args = method.getParameterTypes();
            if ("createNavigationBar".equals(method.getName()) && args.length == 3
                && args[0] == Display.class && method.getReturnType() == void.class) {
                return method;
            }
        }
        throw new NoSuchMethodException(clazz.getName() + "#createNavigationBar(Display, ...)");
    }

    private void observeSettings(Context hostContext) {
        if (context != null) {
            return;
        }
        context = hostContext;
        readSettings();
        ContentResolver resolver = context.getContentResolver();
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                readSettings();
                refreshNavigationBar();
            }
        };
        resolver.registerContentObserver(Settings.Global.getUriFor("hide_gesture_line"), false, observer);
        resolver.registerContentObserver(Settings.Global.getUriFor("force_fsg_nav_bar"), false, observer);
        log("Module gesture-line hiding active=" + hideGestureLine);
    }

    private void readSettings() {
        if (context == null) {
            return;
        }
        ContentResolver resolver = context.getContentResolver();
        // The launcher's native hide setting also changes its quick-switch gesture region.
        // Hide the handle independently so that setting can remain off.
        boolean hidden = Settings.Global.getInt(resolver, "force_fsg_nav_bar", 0) == 1;
        if (hideGestureLine != hidden) {
            hideGestureLine = hidden;
            log("Module gesture-line hiding active=" + hidden);
        }
    }

    private void refreshNavigationBar() {
        if (controller == null) {
            return;
        }
        try {
            SparseArray<?> bars = (SparseArray<?>) XposedHelpers.getObjectField(controller, "mNavigationBars");
            Object bar = bars.get(Display.DEFAULT_DISPLAY);
            if (bar == null) {
                return;
            }
            View view = (View) XposedHelpers.getObjectField(bar, "mView");
            if (view == null || !view.isAttachedToWindow() || view.getDisplay() == null) {
                return;
            }
            // Rebuild from system resources to restore the original insets in button navigation.
            XposedHelpers.callMethod(bar, "repositionNavigationBar", view.getDisplay().getRotation());
            int id = view.getResources().getIdentifier("home_handle", "id", "com.android.systemui");
            View handle = id == 0 ? null : view.findViewById(id);
            if (handle != null) {
                handle.invalidate();
            }
        } catch (Throwable error) {
            log("NavigationBar refresh failed: " + error);
        }
    }

    private boolean isDefaultNavigationBar(WindowManager.LayoutParams params) {
        CharSequence title = params.getTitle();
        return params.type == 2019 && title != null && "NavigationBar0".contentEquals(title);
    }

    private void clearVisualInsets(WindowManager.LayoutParams params) {
        try {
            clearVisualProviders(params);
            Object rotations = XposedHelpers.getObjectField(params, "paramsForRotation");
            if (rotations instanceof WindowManager.LayoutParams[]) {
                for (WindowManager.LayoutParams rotated : (WindowManager.LayoutParams[]) rotations) {
                    if (rotated != null) {
                        clearVisualProviders(rotated);
                    }
                }
            }
        } catch (Throwable error) {
            if (!insetErrorLogged) {
                insetErrorLogged = true;
                log("NavigationBar inset update failed: " + error);
            }
        }
    }

    private void clearVisualProviders(WindowManager.LayoutParams params) {
        Object providers = XposedHelpers.getObjectField(params, "providedInsets");
        if (providers == null) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < Array.getLength(providers); i++) {
            Object provider = Array.get(providers, i);
            if (provider == null) {
                continue;
            }
            int type = (Integer) XposedHelpers.callMethod(provider, "getType");
            if (type == WindowInsets.Type.navigationBars() || type == WindowInsets.Type.tappableElement()) {
                // Preserve provider identities and gesture insets across WindowManager relayouts.
                XposedHelpers.callMethod(provider, "setInsetsSize", Insets.NONE);
                changed = true;
            }
        }
        if (changed && !insetStateLogged) {
            insetStateLogged = true;
            log("NavigationBar visual insets cleared; gesture inset providers retained.");
        }
    }

    private void log(String message) {
        XposedBridge.log("[MyHooks/HyperOS4] " + message);
        Log.i("MyHooksHyperOS", message);
    }
}
