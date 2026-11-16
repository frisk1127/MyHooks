package de.robv.android.xposed;
import java.lang.reflect.*;
import java.util.*;
public final class XposedHelpers {
    public static Class<?> findClass(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new RuntimeException("Missing field " + cls.getName() + "#" + name);
    }
    public static Method findMethodExact(Class<?> cls, String name, Object... parameters) {
        try {
            Class<?>[] types = Arrays.copyOf(parameters, parameters.length, Class[].class);
            Method m = cls.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static XC_MethodHook.Unhook findAndHookMethod(
        Class<?> cls, String name, Object... args) {
        return XposedBridge.hookMethod(
            findMethodExact(cls, name, Arrays.copyOf(args, args.length - 1)),
            (XC_MethodHook) args[args.length - 1]);
    }
    public static Object getObjectField(Object obj, String name) {
        try {
            return findField(obj.getClass(), name).get(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static boolean getBooleanField(Object obj, String name) {
        return (Boolean) getObjectField(obj, name);
    }
    public static void setBooleanField(Object obj, String name, boolean value) {
        try {
            findField(obj.getClass(), name).setBoolean(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static Object callMethod(Object obj, String name, Object... args) {
        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == args.length) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(obj, args);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        throw new RuntimeException("Missing method " + name);
    }
}
