package de.robv.android.xposed;
import java.lang.reflect.Member;
import java.util.HashMap;
import java.util.Map;
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
    }
    protected void afterHookedMethod(MethodHookParam p) throws Throwable {
    }
    public void before(MethodHookParam p) throws Throwable {
        beforeHookedMethod(p);
    }
    public void after(MethodHookParam p) throws Throwable {
        afterHookedMethod(p);
    }
    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public Member method;
        public boolean returned;
        public Throwable error;
        private Object result;
        private final Map<String, Object> extras = new HashMap<>();
        public void setResult(Object value) {
            result = value;
            returned = true;
            error = null;
        }
        public Object getResult() {
            return result;
        }
        public boolean hasThrowable() {
            return error != null;
        }
        public void setObjectExtra(String key, Object value) {
            extras.put(key, value);
        }
        public Object getObjectExtra(String key) {
            return extras.get(key);
        }
    }
    public class Unhook {
        private final Member member;
        public Unhook(Member member) {
            this.member = member;
        }
        public void unhook() {
            XposedBridge.hooks.remove(member);
        }
    }
}
