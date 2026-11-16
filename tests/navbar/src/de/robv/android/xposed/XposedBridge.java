package de.robv.android.xposed;
import java.lang.reflect.*;
import java.util.*;
public final class XposedBridge {
    public static final Map<Member, XC_MethodHook> hooks = new LinkedHashMap<>();
    public static XC_MethodHook.Unhook hookMethod(Member method, XC_MethodHook hook) {
        hooks.put(method, hook);
        return hook.new Unhook(method);
    }
    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> cls, XC_MethodHook hook) {
        Set<XC_MethodHook.Unhook> result = new HashSet<>();
        for (Constructor<?> c : cls.getDeclaredConstructors()) result.add(hookMethod(c, hook));
        return result;
    }
    public static void log(String text) {
        System.out.println(text);
    }
}
