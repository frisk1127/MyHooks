package moe.frisk.myhooks.dexkit;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.result.MethodData;

public final class DexKitHost {

    private static final WeakHashMap<ClassLoader, DexKitBridge> sBridgeCache = new WeakHashMap<>();
    private static final Map<String, Method> sMethodCache = new ConcurrentHashMap<>();
    private static volatile boolean sNativeLoaded = false;

    private DexKitHost() {
    }

    public static Method requireMethod(ClassLoader loader, String scopeKey, DexKitMethodLocator locator) throws Exception {
        String cacheKey = scopeKey + "#" + locator.getCacheKey();
        Method cached = sMethodCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        DexKitBridge bridge = getBridge(loader);
        MethodData methodData = locator.find(bridge);
        if (methodData == null) {
            throw new NoSuchMethodException("DexKit locate returned null: " + locator.getCacheKey());
        }
        Method method = methodData.getMethodInstance(loader);
        method.setAccessible(true);
        sMethodCache.put(cacheKey, method);
        return method;
    }

    public static void clearMethodCache() {
        sMethodCache.clear();
    }

    private static DexKitBridge getBridge(ClassLoader loader) {
        ensureNativeLoaded();
        synchronized (sBridgeCache) {
            DexKitBridge bridge = sBridgeCache.get(loader);
            if (bridge != null && bridge.isValid()) {
                return bridge;
            }
            bridge = DexKitBridge.create(loader, false);
            bridge.setThreadNum(1);
            sBridgeCache.put(loader, bridge);
            return bridge;
        }
    }

    private static void ensureNativeLoaded() {
        if (sNativeLoaded) {
            return;
        }
        synchronized (DexKitHost.class) {
            if (sNativeLoaded) {
                return;
            }
            System.loadLibrary("dexkit");
            sNativeLoaded = true;
        }
    }
}
