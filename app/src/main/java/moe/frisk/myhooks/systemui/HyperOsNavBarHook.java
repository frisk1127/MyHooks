package moe.frisk.myhooks.systemui;

import android.content.ContentResolver;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import moe.frisk.myhooks.AppHook;

public class HyperOsNavBarHook implements AppHook {

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final int TYPE_NAVIGATION_BAR = 2019;
    private static final String LOG_TAG = "MyHooksHyperOS";
    private static final ThreadLocal<Integer> ROTATION_SCOPE_DEPTH = new ThreadLocal<Integer>();
    private static final ThreadLocal<Object> CREATE_NAV_BAR_RESTORE_TARGET = new ThreadLocal<Object>();

    @Override
    public String getKey() {
        return "hyperos_navbar_fix";
    }

    @Override
    public String getTitle() {
        return "HyperOS 隐藏手势线保留旋转建议";
    }

    @Override
    public String getDescription() {
        return "在开启系统原生“隐藏手势提示线”时，让旋转建议按钮依然能够正常弹出。";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEMUI_PACKAGE};
    }

    @Override
    public void onPackageLoaded(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEMUI_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        log("SystemUI Hook: native gesture-line hiding preserved.");

        hookScopedGestureLineReads(lpparam);
        hookKeepNavigationBarForRotation(lpparam);
        hookNavigationBarControllerRemoval(lpparam);
        hookSystemUiNavigationBarWindow(lpparam);
        hookNavigationBarLayoutInsets(lpparam);
        hookGestureLineViews(lpparam);
        hookRotationLogic(lpparam);
        hookNavigationRotationScopes(lpparam);
    }

    private void hookKeepNavigationBarForRotation(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.phone.NavigationModeControllerInjector$mHandler$1",
                lpparam.classLoader
            );
            if (clazz == null) {
                return;
            }
            XposedHelpers.findAndHookMethod(clazz, "handleMessage", Message.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Message message = (Message) param.args[0];
                    if (message != null && message.what == 1) {
                        message.what = 0;
                    }
                }
            });
            log("NavigationModeController handler removal converted to keep-alive mode.");
        } catch (Throwable e) {
            log("NavigationModeController handler hook failed: " + e.getMessage());
        }
    }

    private void hookSystemUiNavigationBarWindow(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists("android.view.WindowManagerImpl", lpparam.classLoader);
            if (clazz == null) {
                return;
            }
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null || param.args.length < 2) {
                        return;
                    }
                    if (param.args[1] instanceof WindowManager.LayoutParams) {
                        WindowManager.LayoutParams params = (WindowManager.LayoutParams) param.args[1];
                        if (isNavigationBarWindow(params)) {
                            stripNavigationBarInsets(params);
                            log("Stripped NavigationBar window insets.");
                        }
                    }
                }
            };
            XposedHelpers.findAndHookMethod(clazz, "addView", View.class, ViewGroup.LayoutParams.class, hook);
            XposedHelpers.findAndHookMethod(clazz, "updateViewLayout", View.class, ViewGroup.LayoutParams.class, hook);
            log("SystemUI NavigationBar window inset hook applied.");
        } catch (Throwable e) {
            log("SystemUI NavigationBar window hook failed: " + e.getMessage());
        }
    }

    private void hookNavigationBarControllerRemoval(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(
                "com.android.systemui.navigationbar.NavigationBarControllerImpl",
                lpparam.classLoader
            );
            if (clazz == null) {
                log("NavigationBarControllerImpl not found.");
                return;
            }
            XposedBridge.hookAllMethods(clazz, "removeNavigationBar", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isDefaultDisplayArg(param)) {
                        log("Blocked NavigationBarControllerImpl#removeNavigationBar(0).");
                        param.setResult(null);
                    }
                }
            });
            XposedBridge.hookAllMethods(clazz, "addDefaultNavigationBar", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    log("NavigationBarControllerImpl#addDefaultNavigationBar finished.");
                }
            });
            XposedBridge.hookAllMethods(clazz, "createNavigationBar", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object injector = XposedHelpers.getObjectField(param.thisObject, "mNavigationModeControllerInjector");
                    if (injector != null && getBooleanField(injector, "mIsFsgMode") && getBooleanField(injector, "mHideGestureLine")) {
                        setBooleanField(injector, "mHideGestureLine", false);
                        CREATE_NAV_BAR_RESTORE_TARGET.set(injector);
                        log("Temporarily disabled mHideGestureLine for createNavigationBar().");
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object injector = CREATE_NAV_BAR_RESTORE_TARGET.get();
                    if (injector != null) {
                        setBooleanField(injector, "mHideGestureLine", true);
                        CREATE_NAV_BAR_RESTORE_TARGET.remove();
                        log("Restored mHideGestureLine after createNavigationBar().");
                    }
                }
            });
            log("NavigationBarControllerImpl create/remove hooks applied.");
        } catch (Throwable e) {
            log("NavigationBarControllerImpl removal hook failed: " + e.getMessage());
        }
    }

    private void hookNavigationBarLayoutInsets(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists("com.android.systemui.navigationbar.NavigationBar", lpparam.classLoader);
            if (clazz == null) {
                return;
            }
            XposedHelpers.findAndHookMethod(clazz, "getBarLayoutParamsForRotation", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object result = param.getResult();
                    if (result instanceof WindowManager.LayoutParams) {
                        stripNavigationBarInsets((WindowManager.LayoutParams) result);
                    }
                }
            });
            hookMethod(clazz, "repositionNavigationBar", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    stripNavigationBarFrameInsets(param.thisObject);
                }
            });
            hookMethod(clazz, "onViewAttached", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    stripNavigationBarFrameInsets(param.thisObject);
                    hideHomeHandle(param.thisObject);
                }
            });
            log("NavigationBar insets strip hooks applied.");
        } catch (Throwable e) {
            log("NavigationBar insets hook failed: " + e.getMessage());
        }
    }

    private void hookGestureLineViews(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] viewClasses = {
            "com.android.systemui.navigationbar.gestural.NavigationHandle",
            "com.android.systemui.navigationbar.gestural.MiuiGestureLineView",
            "com.miui.systemui.navigation.GestureLineView"
        };

        for (int i = 0; i < viewClasses.length; i++) {
            try {
                Class<?> clazz = XposedHelpers.findClassIfExists(viewClasses[i], lpparam.classLoader);
                if (clazz == null) {
                    continue;
                }
                XposedBridge.hookAllConstructors(clazz, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        hideView(param.thisObject);
                    }
                });
                hookMethod(clazz, "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        hideView(param.thisObject);
                    }
                });
                log("Gesture line visual hook applied: " + viewClasses[i]);
            } catch (Throwable ignored) {}
        }
    }

    private void hookScopedGestureLineReads(XC_LoadPackage.LoadPackageParam lpparam) {
        final XC_MethodHook intHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (isInRotationScope() && isHideGestureLineKey(param)) {
                    param.setResult(0);
                }
            }
        };
        final XC_MethodHook stringHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (isInRotationScope() && isHideGestureLineKey(param)) {
                    param.setResult("0");
                }
            }
        };

        hookSettingsClass(Settings.Global.class, intHook, stringHook);
        hookSettingsClass(Settings.System.class, intHook, stringHook);
        hookSettingsClass(Settings.Secure.class, intHook, stringHook);
        hookScopedMiuiGestureLineState(lpparam);
        log("Scoped hide_gesture_line hooks applied.");
    }

    private void hookSettingsClass(Class<?> settingsClass, XC_MethodHook intHook, XC_MethodHook stringHook) {
        hookMethod(settingsClass, "getInt", ContentResolver.class, String.class, intHook);
        hookMethod(settingsClass, "getInt", ContentResolver.class, String.class, int.class, intHook);
        hookMethod(settingsClass, "getString", ContentResolver.class, String.class, stringHook);
    }

    private void hookScopedMiuiGestureLineState(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists("android.util.MiuiMultiWindowUtils", lpparam.classLoader);
            if (clazz == null) {
                return;
            }
            XposedBridge.hookAllMethods(clazz, "isHideGestureLine", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isInRotationScope()) {
                        param.setResult(false);
                    }
                }
            });
        } catch (Throwable e) {
            log("Scoped Miui gesture state hook failed: " + e.getMessage());
        }
    }

    private boolean isHideGestureLineKey(XC_MethodHook.MethodHookParam param) {
        return param.args != null
            && param.args.length > 1
            && "hide_gesture_line".equals(param.args[1]);
    }

    private void hookRotationLogic(XC_LoadPackage.LoadPackageParam lpparam) {
        String controllerClass = "com.android.systemui.shared.rotation.RotationButtonController";
        try {
            final Class<?> clazz = XposedHelpers.findClassIfExists(controllerClass, lpparam.classLoader);
            if (clazz != null) {
                XposedBridge.hookAllConstructors(clazz, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        normalizeRotationControllerState(param.thisObject);
                    }
                });

                hookAllBooleanMethods(clazz, "isFloatingRotationButtonAllowed", true);
                hookAllBooleanMethods(clazz, "canShowRotationButton", true);
                hookAllBooleanMethods(clazz, "isRotationButtonVisible", true);
                hookAllBooleanMethods(clazz, "isNavBarShowing", true);
                hookAllBooleanMethods(clazz, "isNavigationBarShowing", true);

                hookRotationScopedMethods(clazz, true, true);
                log("RotationButtonController hooks applied.");
            }
        } catch (Throwable e) {
            log("Rotation logic hook failed: " + e.getMessage());
        }
    }

    private void hookNavigationRotationScopes(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] classNames = {
            "com.android.systemui.navigationbar.NavigationBar",
            "com.android.systemui.navigationbar.NavigationBarView",
            "com.android.systemui.navigationbar.MiuiNavigationBarView",
            "com.miui.systemui.navigationbar.NavigationBar",
            "com.miui.systemui.navigationbar.NavigationBarView",
            "com.miui.systemui.navigationbar.MiuiNavigationBarView"
        };

        for (int i = 0; i < classNames.length; i++) {
            try {
                Class<?> clazz = XposedHelpers.findClassIfExists(classNames[i], lpparam.classLoader);
                if (clazz == null) {
                    continue;
                }
                hookRotationScopedMethods(clazz, false, false);
                log("Navigation rotation scope hooks applied: " + classNames[i]);
            } catch (Throwable ignored) {}
        }
    }

    private void hookRotationScopedMethods(final Class<?> clazz, final boolean normalizeState, boolean includeStateKeywords) {
        Method[] methods = clazz.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            final Method method = methods[i];
            String name = method.getName();
            if (!containsRotationScopeKeyword(name, includeStateKeywords)) {
                continue;
            }
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        enterRotationScope();
                        if (normalizeState) {
                            normalizeRotationControllerState(param.thisObject);
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (normalizeState) {
                            normalizeRotationControllerState(param.thisObject);
                        }
                        exitRotationScope();
                    }
                });
            } catch (Throwable ignored) {}
        }
    }

    private void hookAllBooleanMethods(Class<?> clazz, String methodName, final boolean result) {
        try {
            XposedBridge.hookAllMethods(clazz, methodName, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    enterRotationScope();
                    normalizeRotationControllerState(param.thisObject);
                    param.setResult(result);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    exitRotationScope();
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hookMethod(Class<?> clazz, String methodName, Object... args) {
        try {
            XposedHelpers.findAndHookMethod(clazz, methodName, args);
        } catch (Throwable ignored) {}
    }

    private void stripNavigationBarFrameInsets(Object navigationBar) {
        try {
            Object frame = XposedHelpers.getObjectField(navigationBar, "mFrame");
            Object windowManager = XposedHelpers.getObjectField(navigationBar, "mWindowManager");
            if (!(frame instanceof View) || !(windowManager instanceof WindowManager)) {
                return;
            }
            View frameView = (View) frame;
            Object layoutParams = frameView.getLayoutParams();
            if (layoutParams instanceof WindowManager.LayoutParams) {
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) layoutParams;
                stripNavigationBarInsets(params);
                ((WindowManager) windowManager).updateViewLayout(frameView, params);
            }
        } catch (Throwable ignored) {}
    }

    private void stripNavigationBarInsets(WindowManager.LayoutParams params) {
        try {
            params.setFitInsetsTypes(0);
            params.setFitInsetsSides(0);
            params.setFitInsetsIgnoringVisibility(false);
            clearObjectField(params, "providedInsets");
            Object paramsForRotation = XposedHelpers.getObjectField(params, "paramsForRotation");
            if (paramsForRotation instanceof WindowManager.LayoutParams[]) {
                WindowManager.LayoutParams[] rotationArray = (WindowManager.LayoutParams[]) paramsForRotation;
                for (int i = 0; i < rotationArray.length; i++) {
                    WindowManager.LayoutParams rotationParams = rotationArray[i];
                    if (rotationParams != null) {
                        rotationParams.setFitInsetsTypes(0);
                        rotationParams.setFitInsetsSides(0);
                        rotationParams.setFitInsetsIgnoringVisibility(false);
                        clearObjectField(rotationParams, "providedInsets");
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private boolean isNavigationBarWindow(WindowManager.LayoutParams params) {
        CharSequence title = params.getTitle();
        return title != null
            && "NavigationBar0".contentEquals(title)
            && params.type == TYPE_NAVIGATION_BAR;
    }

    private void clearObjectField(Object object, String fieldName) {
        try {
            Field field = findField(object.getClass(), fieldName);
            field.setAccessible(true);
            field.set(object, null);
        } catch (Throwable ignored) {}
    }

    private void hideHomeHandle(Object navigationBar) {
        try {
            Object view = XposedHelpers.getObjectField(navigationBar, "mView");
            if (!(view instanceof View)) {
                return;
            }
            int id = ((View) view).getResources().getIdentifier("home_handle", "id", "com.android.systemui");
            if (id == 0) {
                return;
            }
            View homeHandle = ((View) view).findViewById(id);
            hideView(homeHandle);
        } catch (Throwable ignored) {}
    }

    private void hideView(Object object) {
        if (object instanceof View) {
            View view = (View) object;
            view.setAlpha(0f);
            view.setVisibility(View.INVISIBLE);
        }
    }

    private boolean isDefaultDisplayArg(XC_MethodHook.MethodHookParam param) {
        return param.args != null
            && param.args.length > 0
            && param.args[0] instanceof Integer
            && ((Integer) param.args[0]).intValue() == 0;
    }

    private boolean getBooleanField(Object object, String fieldName) {
        try {
            Field field = findField(object.getClass(), fieldName);
            field.setAccessible(true);
            return field.getBoolean(object);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void setBooleanField(Object object, String fieldName, boolean value) {
        try {
            Field field = findField(object.getClass(), fieldName);
            field.setAccessible(true);
            field.setBoolean(object, value);
        } catch (Throwable ignored) {}
    }

    private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private boolean containsHiddenKeyword(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("hide")
            || lower.contains("hidden")
            || lower.contains("gestureline");
    }

    private boolean containsGestureLineOrNavBarKeyword(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return containsHiddenKeyword(name)
            || lower.contains("navbar")
            || lower.contains("navigationbar")
            || lower.contains("gestureline");
    }

    private boolean containsRotationScopeKeyword(String name, boolean includeStateKeywords) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("rotation")
            || lower.contains("rotate")
            || lower.contains("orientation")
            || (includeStateKeywords && containsGestureLineOrNavBarKeyword(name));
    }

    private boolean shouldForceBooleanFieldTrue(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return (lower.contains("navbar") || lower.contains("navigationbar"))
            && (lower.contains("show") || lower.contains("visible"));
    }

    private void normalizeRotationControllerState(Object object) {
        if (object == null) {
            return;
        }
        Class<?> clazz = object.getClass();
        while (clazz != null) {
            Field[] fields = clazz.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (field.getType() != boolean.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (containsHiddenKeyword(field.getName())) {
                        field.setBoolean(object, false);
                    } else if (shouldForceBooleanFieldTrue(field.getName())) {
                        field.setBoolean(object, true);
                    }
                } catch (Throwable ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
    }

    private void enterRotationScope() {
        Integer depth = ROTATION_SCOPE_DEPTH.get();
        ROTATION_SCOPE_DEPTH.set(Integer.valueOf(depth == null ? 1 : depth.intValue() + 1));
    }

    private void exitRotationScope() {
        Integer depth = ROTATION_SCOPE_DEPTH.get();
        if (depth == null || depth.intValue() <= 1) {
            ROTATION_SCOPE_DEPTH.remove();
        } else {
            ROTATION_SCOPE_DEPTH.set(Integer.valueOf(depth.intValue() - 1));
        }
    }

    private boolean isInRotationScope() {
        Integer depth = ROTATION_SCOPE_DEPTH.get();
        return depth != null && depth.intValue() > 0;
    }

    private void log(String message) {
        String text = "[MyHooks/HyperOS] " + message;
        XposedBridge.log(text);
        Log.i(LOG_TAG, message);
    }
}
