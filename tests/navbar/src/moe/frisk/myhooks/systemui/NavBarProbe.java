package moe.frisk.myhooks.systemui;
import android.graphics.Insets;
import android.os.Binder;
import android.os.Message;
import android.view.WindowInsets;
import android.view.WindowManager;
import dalvik.system.PathClassLoader;
import de.robv.android.xposed.*;
import java.lang.reflect.*;
import java.util.*;

public final class NavBarProbe {
    static int checks;
    static void check(boolean ok, String name) {
        if (!ok)
            throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }
    static XC_MethodHook hook(String cls, String method) {
        for (Map.Entry<Member, XC_MethodHook> entry : XposedBridge.hooks.entrySet()) {
            if (entry.getKey().getDeclaringClass().getName().equals(cls)
                && entry.getKey().getName().equals(method))
                return entry.getValue();
        }
        throw new AssertionError("Missing hook " + cls + "#" + method);
    }
    static XC_MethodHook.MethodHookParam parameter(Object self, Object... args) {
        XC_MethodHook.MethodHookParam p = new XC_MethodHook.MethodHookParam();
        p.thisObject = self;
        p.args = args;
        return p;
    }
    static WindowManager.LayoutParams layout(String title) throws Exception {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        p.type = 2019;
        p.height = 60;
        p.width = -1;
        p.flags = 123456;
        p.setTitle(title);
        Class<?> cls = Class.forName("android.view.InsetsFrameProvider");
        Object providers = Array.newInstance(cls, 5);
        int[] types = {WindowInsets.Type.navigationBars(), WindowInsets.Type.tappableElement(),
            WindowInsets.Type.mandatorySystemGestures(), WindowInsets.Type.systemGestures(),
            WindowInsets.Type.systemGestures()};
        for (int i = 0; i < 5; i++) {
            Object provider = cls.getConstructor(Object.class, int.class, int.class)
                                  .newInstance(new Binder(), i == 4 ? 1 : 0, types[i]);
            cls.getMethod("setInsetsSize", Insets.class)
                .invoke(provider, Insets.of(i + 1, 0, 0, 60 + i));
            Array.set(providers, i, provider);
        }
        XposedHelpers.findField(p.getClass(), "providedInsets").set(p, providers);
        return p;
    }
    static Insets size(Object providers, int index) {
        return (Insets) XposedHelpers.callMethod(Array.get(providers, index), "getInsetsSize");
    }
    static Object providers(WindowManager.LayoutParams p) {
        return XposedHelpers.getObjectField(p, "providedInsets");
    }
    static void assertInsets(WindowManager.LayoutParams p, Object original) {
        check(providers(p) == original && Array.getLength(original) == 5,
            "provider identities/count preserved");
        check(size(original, 0).equals(Insets.NONE) && size(original, 1).equals(Insets.NONE),
            "visual insets zero");
        for (int i = 2; i < 5; i++)
            check(size(original, i).equals(Insets.of(i + 1, 0, 0, 60 + i)),
                "gesture provider " + i + " unchanged");
        check(p.height == 60 && p.width == -1 && p.flags == 123456,
            "window geometry and flags unchanged");
    }
    public static final class Mode {
        public boolean mHideGestureLine;
    }
    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }
    static void run() throws Throwable {
        String libraries = "/system_ext/framework/extphonelib.jar:/system_ext/framework/"
                           + "Miui-WindowManager-Shell.jar:"
            + "/system_ext/framework/androidx.window.extensions.jar:/system_ext/framework/"
              + "androidx.window.sidecar.jar:"
            + "/system_ext/app/miuisystem/miuisystem.apk:/system_ext/priv-app/MiuiSystemUI/"
              + "MiuiSystemUI.apk";
        ClassLoader loader = new PathClassLoader(libraries, NavBarProbe.class.getClassLoader());
        HyperOs4NavBarHook implementation = new HyperOs4NavBarHook();
        implementation.install(loader);
        check(XposedBridge.hooks.size() >= 8, "all critical hooks resolve against actual SystemUI");
        for (Member m : XposedBridge.hooks.keySet()) {
            check(m.getDeclaringClass() != android.view.View.class,
                "no global View hook: " + m.getName());
            check(!m.getName().equals("removeNavigationBar"),
                "configuration teardown remains native");
            check(!m.getDeclaringClass().getName().contains("RotationButton"),
                "rotation policy remains native");
        }
        XC_MethodHook window = hook("android.view.WindowManagerImpl", "updateViewLayout");
        WindowManager.LayoutParams normal = layout("NavigationBar0");
        Object normalProviders = providers(normal);
        window.before(parameter(null, null, normal));
        check(size(normalProviders, 0).bottom == 60,
            "inactive hiding leaves button-navigation inset intact");
        XposedHelpers.setBooleanField(implementation, "hideGestureLine", true);
        WindowManager.LayoutParams otherDisplay = layout("NavigationBar1");
        window.before(parameter(null, null, otherDisplay));
        check(size(providers(otherDisplay), 0).bottom == 60, "external display untouched");
        WindowManager.LayoutParams floating = layout("FloatingRotationButton");
        floating.type = 2024;
        window.before(parameter(null, null, floating));
        check(size(providers(floating), 0).bottom == 60, "rotation suggestion window untouched");
        WindowManager.LayoutParams main = layout("NavigationBar0");
        WindowManager.LayoutParams[] rotations = new WindowManager.LayoutParams[4];
        Object[] originalRotations = new Object[4];
        for (int i = 0; i < 4; i++) {
            rotations[i] = layout("NavigationBar0");
            originalRotations[i] = providers(rotations[i]);
        }
        XposedHelpers.findField(main.getClass(), "paramsForRotation").set(main, rotations);
        Object original = providers(main);
        window.before(parameter(null, null, main));
        assertInsets(main, original);
        for (int i = 0; i < 4; i++) assertInsets(rotations[i], originalRotations[i]);
        window.before(parameter(null, null, main));
        assertInsets(main, original);
        XposedHelpers.setBooleanField(implementation, "hideGestureLine", false);
        WindowManager.LayoutParams restored = layout("NavigationBar0");
        window.before(parameter(null, null, restored));
        check(size(providers(restored), 0).bottom == 60,
            "new system layout restores original inset after disabling");
        XC_MethodHook create =
            hook("com.android.systemui.navigationbar.NavigationBarControllerImpl",
                "createNavigationBar");
        Mode mode = new Mode();
        XC_MethodHook.MethodHookParam creation = parameter(null);
        creation.setObjectExtra("myhooks.restoreGestureLineInjector", mode);
        creation.error = new RuntimeException("simulated creation failure");
        create.after(creation);
        check(mode.mHideGestureLine, "creation failure restores native hide flag");
        XC_MethodHook handler =
            hook("com.android.systemui.statusbar.phone.NavigationModeControllerInjector$mHandler$1",
                "handleMessage");
        Message msg = Message.obtain();
        msg.what = 0;
        XC_MethodHook.MethodHookParam dispatch = parameter(null, msg);
        dispatch.setObjectExtra("myhooks.restoreNavigationMessage", 1);
        dispatch.error = new RuntimeException("simulated dispatch failure");
        handler.after(dispatch);
        check(msg.what == 1, "dispatch failure restores original message");
        System.out.println("PASS total=" + checks
            + "; real Android framework, recording Xposed shim (no live hooks)");
    }
}
