package moe.frisk.myhooks.framework;

import android.util.Base64;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import moe.frisk.myhooks.AppHook;

public final class StockEmojiHook implements AppHook {
    private static final String FONT_NAME = "NotoColorEmoji";
    // fs-verity SHA-256 of Google's 3.052 update shipped to this device.
    private static final byte[] UPDATE_DIGEST =
        Base64.decode("fTraf4gh83y05adY1bLQL64z1nJb+/PzkYqR5UPKrRA=", Base64.DEFAULT);

    @Override
    public String getKey() {
        return "stock_noto_emoji";
    }

    @Override
    public boolean isSystemHook() {
        return true;
    }

    @Override
    public String getTitle() {
        return "恢复出厂 2D Emoji";
    }

    @Override
    public String getDescription() {
        return "跳过 Google Noto Emoji 3.052 的这次 3D 更新，使用系统自带的表情字体。";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"android"};
    }

    @Override
    public void onPackageLoaded(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)
                || !("android".equals(lpparam.processName) || "system_server".equals(lpparam.processName))) {
            return;
        }
        Class<?> verity = XposedHelpers.findClass("com.android.internal.security.VerityUtils", lpparam.classLoader);
        Method measure = XposedHelpers.findMethodExact(verity, "getFsverityDigest", String.class);
        XposedHelpers.findAndHookMethod("android.graphics.fonts.SystemFonts", lpparam.classLoader,
            "getSystemFontConfigInternal", String.class, String.class, String.class, String.class,
            Map.class, long.class, int.class, new XC_MethodHook() {
                private final AtomicBoolean reported = new AtomicBoolean();

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Map<?, ?> updates = (Map<?, ?>) param.args[4];
                    Object candidate = updates == null ? null : updates.get(FONT_NAME);
                    if (!(candidate instanceof File)) {
                        return;
                    }
                    File stock = new File((String) param.args[1], FONT_NAME + ".ttf");
                    if (!stock.isFile()) {
                        return;
                    }
                    byte[] digest = (byte[]) measure.invoke(null, ((File) candidate).getAbsolutePath());
                    if (Arrays.equals(UPDATE_DIGEST, digest)) {
                        Map<?, ?> filtered = new HashMap<>(updates);
                        filtered.remove(FONT_NAME);
                        param.args[4] = filtered;
                        if (reported.compareAndSet(false, true)) {
                            XposedBridge.log("[MyHooks/stock_noto_emoji] Skipped 3.052 in favor of " + stock);
                        }
                    }
                }
            });
    }
}
