package moe.frisk.myhooks.qq;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import moe.frisk.myhooks.AppHook;
import moe.frisk.myhooks.dexkit.DexKitHost;
import moe.frisk.myhooks.dexkit.DexKitMethodLocator;
import moe.frisk.myhooks.util.AndroidUi;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

public class MultiForwardAvatarUrlHook implements AppHook {

    private static final String[] AVATAR_COMPONENT_CLASSES = new String[]{
        "com.tencent.mobileqq.aio.msglist.holder.component.avatar.AIOAvatarContentComponent",
        "com.tencent.mobileqq.aio.msg.holder.component.avatar.AIOAvatarContentComponent"
    };
    private static final String[] GALLERY_ACTIVITY_CLASSES = new String[]{
        "com.tencent.richframework.gallery.QQGalleryActivity",
        "com.tencent.mobileqq.richmediabrowser.AIOGalleryActivity",
        "com.tencent.mobileqq.activity.aio.photo.AIOGalleryActivity"
    };
    private static final String[] LAYER_INIT_BEAN_CLASSES = new String[]{
        "com.tencent.richframework.gallery.bean.RFWLayerInitBean"
    };
    private static final String[] LAYER_ITEM_MEDIA_INFO_CLASSES = new String[]{
        "com.tencent.richframework.gallery.bean.RFWLayerItemMediaInfo"
    };
    private static final String[] LAYER_PIC_INFO_CLASSES = new String[]{
        "com.tencent.richframework.gallery.bean.RFWLayerPicInfo"
    };
    private static final String[] PIC_INFO_CLASSES = new String[]{
        "com.tencent.richframework.gallery.bean.RFWLayerPicInfo$RFWPicInfo"
    };
    private static final String[] TRANS_ANIM_BEAN_CLASSES = new String[]{
        "com.tencent.richframework.gallery.anim.RFWTransAnimBean"
    };
    private static final String[] SOURCE_RECT_CLASSES = new String[]{
        "com.tencent.richframework.gallery.anim.RFWTransAnimBean$SourceRect"
    };
    private static final String MULTI_FORWARD_ACTIVITY =
        "com.tencent.mobileqq.activity.MultiForwardActivity";
    private static final Pattern FROM_FACE_URL =
        Pattern.compile("(?:^|[,{ ])fromFaceUrl=([^,}]+)");

    private static volatile boolean sHooked = false;
    private static final WeakHashMap<View, String> sViewState = new WeakHashMap<>();
    private static final WeakHashMap<View, View.OnLongClickListener> sInstalledLongClick = new WeakHashMap<>();

    @Override
    public String getKey() {
        return "qq_multi_forward_avatar";
    }

    @Override
    public String getTitle() {
        return "QQ 多选转发头像 URL 提取";
    }

    @Override
    public String getDescription() {
        return "在 QQ 多选转发界面长按头像，提取并显示头像的原始 URL。";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
            "com.tencent.mobileqq",
            "com.tencent.tim"
        };
    }

    @Override
    public void onPackageLoaded(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sHooked) {
            return;
        }
        Class<?> componentClass;
        try {
            componentClass = findFirstClass(lpparam.classLoader, AVATAR_COMPONENT_CLASSES);
        } catch (Throwable e) {
            return;
        }
        if (!hookByDexKit(lpparam, componentClass)) {
            hookAllCandidateMethods(componentClass);
        }
        sHooked = true;
    }

    private boolean hookByDexKit(final XC_LoadPackage.LoadPackageParam lpparam, Class<?> componentClass) {
        try {
            Method method = DexKitHost.requireMethod(lpparam.classLoader, lpparam.packageName + ":" + getKey(),
                new DexKitMethodLocator() {
                    @Override
                    public String getCacheKey() {
                        return "avatar_set_listener";
                    }

                    @Override
                    public MethodData find(DexKitBridge bridge) throws Exception {
                        for (String candidate : AVATAR_COMPONENT_CLASSES) {
                            MethodData data = findAvatarListenerMethod(bridge, candidate);
                            if (data != null) {
                                return data;
                            }
                        }
                        return null;
                    }
                });
            if (!componentClass.isAssignableFrom(method.getDeclaringClass())) {
                return false;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    bindAvatarActions(param.thisObject);
                }
            });
            return true;
        } catch (Throwable e) {
            XposedBridge.log("[MyHooks/" + getKey() + "] DexKit fallback: " + e.getClass().getSimpleName()
                + ": " + String.valueOf(e.getMessage()));
            return false;
        }
    }

    private MethodData findAvatarListenerMethod(DexKitBridge bridge, String declaredClass) {
        try {
            MethodMatcher matcher = MethodMatcher.create()
                .declaredClass(declaredClass)
                .returnType("void")
                .paramTypes()
                .addInvoke("setOnClickListener");
            MethodDataList result = bridge.findMethod(FindMethod.create().matcher(matcher));
            if (result != null && !result.isEmpty()) {
                return result.get(0);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void hookAllCandidateMethods(Class<?> componentClass) {
        for (Method method : componentClass.getDeclaredMethods()) {
            if (method.getReturnType() == Void.TYPE && method.getParameterTypes().length == 0) {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        bindAvatarActions(param.thisObject);
                    }
                });
            }
        }
    }

    private void bindAvatarActions(Object component) {
        final ViewGroup avatarContainer = findAvatarContainer(component);
        if (avatarContainer == null) {
            return;
        }
        final Context context = avatarContainer.getContext();
        if (context == null || !MULTI_FORWARD_ACTIVITY.equals(context.getClass().getName())) {
            return;
        }
        final QqMsgRecord msgRecord = findMsgRecord(component);
        if (msgRecord == null) {
            return;
        }
        final String url = extractFromFaceUrl(msgRecord);
        String state = msgRecord.senderUin + "|" + msgRecord.peerUin + "|" + url;
        String oldState = sViewState.get(avatarContainer);
        if (state.equals(oldState)) {
            return;
        }
        sViewState.put(avatarContainer, state);
        installLongClick(avatarContainer, new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String currentUrl = extractFromFaceUrl(msgRecord);
                if (currentUrl == null || currentUrl.length() == 0) {
                    AndroidUi.toast(context, "未找到 fromFaceUrl");
                } else {
                    previewAvatar(context, avatarContainer, currentUrl, msgRecord);
                }
                return true;
            }
        });
    }

    private void installLongClick(View view, View.OnLongClickListener listener) {
        View.OnLongClickListener existing = getExistingLongClickListener(view);
        View.OnLongClickListener wrapped = buildChainedLongClickListener(view, existing, listener);
        sInstalledLongClick.put(view, wrapped);
        view.setOnLongClickListener(wrapped);
    }

    private View.OnLongClickListener buildChainedLongClickListener(final View view,
                                                                   final View.OnLongClickListener existing,
                                                                   final View.OnLongClickListener ours) {
        if (existing == null || existing == sInstalledLongClick.get(view)) {
            return ours;
        }
        return new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View target) {
                boolean consumed = false;
                try {
                    consumed = existing.onLongClick(target);
                } catch (Throwable e) {
                    XposedBridge.log("[MyHooks/" + getKey() + "] existing long click failed: "
                        + android.util.Log.getStackTraceString(e));
                }
                if (consumed) {
                    return true;
                }
                return ours.onLongClick(target);
            }
        };
    }

    private View.OnLongClickListener getExistingLongClickListener(View view) {
        try {
            Method getListenerInfo = View.class.getDeclaredMethod("getListenerInfo");
            getListenerInfo.setAccessible(true);
            Object listenerInfo = getListenerInfo.invoke(view);
            if (listenerInfo == null) {
                return null;
            }
            Field field = listenerInfo.getClass().getDeclaredField("mOnLongClickListener");
            field.setAccessible(true);
            Object value = field.get(listenerInfo);
            if (value instanceof View.OnLongClickListener) {
                return (View.OnLongClickListener) value;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private ViewGroup findAvatarContainer(Object component) {
        Field[] fields = component.getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(component);
                if (value == null) {
                    continue;
                }
                Object actual = unwrapLazyValue(value);
                if (actual instanceof ViewGroup) {
                    return (ViewGroup) actual;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private Object unwrapLazyValue(Object value) {
        try {
            return XposedHelpers.callMethod(value, "getValue");
        } catch (Throwable ignored) {
            return value;
        }
    }

    private QqMsgRecord findMsgRecord(Object component) {
        Field[] fields = component.getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                if (!isLikelyAioMsgItem(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(component);
                QqMsgRecord record = resolveQqMsgRecord(value);
                if (record != null) {
                    return record;
                }
            } catch (Throwable ignored) {
            }
        }
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(component);
                if (value == null) {
                    continue;
                }
                QqMsgRecord record = resolveQqMsgRecord(value);
                if (record != null) {
                    return record;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private QqMsgRecord resolveQqMsgRecord(Object value) {
        if (value == null) {
            return null;
        }
        Object msgRecord = null;
        if (value.getClass().getName().contains("MsgRecord")) {
            msgRecord = value;
        } else {
            try {
                msgRecord = XposedHelpers.callMethod(value, "getMsgRecord");
            } catch (Throwable ignored) {
            }
        }
        if (msgRecord == null) {
            return null;
        }
        try {
            Object senderObj = XposedHelpers.getObjectField(msgRecord, "senderUin");
            Object peerObj = XposedHelpers.getObjectField(msgRecord, "peerUin");
            int chatType = XposedHelpers.getIntField(msgRecord, "chatType");
            return new QqMsgRecord(
                senderObj == null ? "" : String.valueOf(senderObj),
                peerObj == null ? "" : String.valueOf(peerObj),
                chatType,
                msgRecord.toString()
            );
        } catch (Throwable e) {
            return null;
        }
    }

    private String extractFromFaceUrl(QqMsgRecord msgRecord) {
        Matcher matcher = FROM_FACE_URL.matcher(msgRecord.rawString);
        if (!matcher.find()) {
            return null;
        }
        String url = matcher.group(1);
        if (url == null) {
            return null;
        }
        url = url.trim();
        if (url.length() == 0 || "null".equals(url)) {
            return null;
        }
        return url;
    }

    private void previewAvatar(final Context context, final View avatarView, final String url, final QqMsgRecord msgRecord) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final File file = downloadAvatar(context, url, msgRecord.senderUin, msgRecord.peerUin, true);
                    MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);
                    openImagePreview(context, avatarView, file, msgRecord);
                } catch (Throwable e) {
                    AndroidUi.toast(context, "头像预览失败: " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
                    XposedBridge.log("[MyHooks/" + getKey() + "] " + android.util.Log.getStackTraceString(e));
                }
            }
        }).start();
    }

    private void openImagePreview(final Context context, final View avatarView, final File file, final QqMsgRecord msgRecord) {
        AndroidUi.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                Uri uri = buildPreviewUri(context, file);
                boolean grantRead = !"file".equals(uri.getScheme());
                if (tryOpenNativeGallery(context, avatarView, file, msgRecord)) {
                    return;
                }
                try {
                    Intent fallback = new Intent(Intent.ACTION_VIEW);
                    fallback.setDataAndType(uri, "image/*");
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (grantRead) {
                        fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                    context.startActivity(fallback);
                } catch (Throwable inner) {
                    AndroidUi.toast(context, "没有可用的图片预览器: " + String.valueOf(inner.getMessage()));
                    XposedBridge.log("[MyHooks/" + getKey() + "] " + android.util.Log.getStackTraceString(inner));
                }
            }
        });
    }

    private boolean tryOpenNativeGallery(Context context, View avatarView, File file, QqMsgRecord msgRecord) {
        try {
            Intent intent = buildNativeGalleryIntent(context, avatarView, file, msgRecord);
            context.startActivity(intent);
            return true;
        } catch (Throwable e) {
            XposedBridge.log("[MyHooks/" + getKey() + "] native gallery failed: "
                + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()) + "\n"
                + android.util.Log.getStackTraceString(e));
            return false;
        }
    }

    private Intent buildNativeGalleryIntent(Context context, View avatarView, File file, QqMsgRecord msgRecord) throws Exception {
        ClassLoader loader = context.getClassLoader();
        Object picInfo = createPicInfo(loader, file);
        Object layerPicInfo = createLayerPicInfo(loader, picInfo);
        Object mediaInfo = createLayerItemMediaInfo(loader, layerPicInfo, file);
        Object initBean = createLayerInitBean(loader, mediaInfo, layerPicInfo, avatarView);
        Rect rect = getViewRectOnScreen(avatarView);
        String galleryActivity = findFirstClassName(loader, GALLERY_ACTIVITY_CLASSES);

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(context.getPackageName(), galleryActivity));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("KEY_THUMBNAL_BOUND", rect);
        intent.putExtra("PhotoConst.INIT_ACTIVITY_PACKAGE_NAME", context.getPackageName());
        intent.putExtra("PhotoConst.INIT_ACTIVITY_CLASS_NAME", MULTI_FORWARD_ACTIVITY);
        intent.putExtra("key_init_bean", (android.os.Parcelable) initBean);
        intent.putExtra("MSG_RECORD_STORE_KEY", "1");
        intent.putExtra("extra.GROUP_CODE", safePeer(msgRecord));
        intent.putExtra("preAct", "MultiForwardActivity");
        intent.putExtra("leftViewText", "返回");
        intent.putExtra("preAct_elapsedRealtime", android.os.SystemClock.elapsedRealtime());
        intent.putExtra("preAct_time", System.currentTimeMillis());
        intent.putExtra("fling_action_key", 2);
        intent.putExtra("fling_code_key", file.getAbsolutePath().hashCode());
        intent.putExtra("extra.ENTER_NEW_GALLERY", true);
        intent.putExtra("extra.AIO_CURRENT_PANEL_STATE", 0);
        intent.putExtra("uintype", msgRecord.chatType);
        intent.putExtra("uin", safePeer(msgRecord));
        intent.putExtra("extra.GROUP_UIN", safePeer(msgRecord));
        intent.putExtra("forward_source_uin_type", msgRecord.chatType);
        intent.putExtra("public_fragment_class", "com.tencent.qqnt.aio.gallery.NTAIOLayerFragment");
        intent.putExtra("key_allow_forward_photo_preview_edit", true);
        intent.putExtra("extra.EXTRA_FORWARD_TO_QZONE_SRC", 2);
        intent.putExtra("extra.EXTRA_ENTRANCE", 1);
        intent.putExtra("extra.IS_FROM_MULTI_MSG", true);
        intent.putExtra("is_one_item", true);
        intent.putExtra("extra.IS_SAVING_FILE", false);
        intent.putExtra("extra.MOBILE_QQ_PROCESS_ID", android.os.Process.myPid());
        intent.putExtra("extra.CAN_FORWARD_TO_GROUP_ALBUM", false);
        intent.putExtra("is_ReplyMsg_From_Same_Session", true);
        intent.putExtra("extra.IS_REPLY_SRC_MSG_EXIST", false);
        return intent;
    }

    private String safePeer(QqMsgRecord msgRecord) {
        if (msgRecord.peerUin == null || msgRecord.peerUin.length() == 0) {
            return msgRecord.senderUin == null ? "0" : msgRecord.senderUin;
        }
        return msgRecord.peerUin;
    }

    private Object createLayerInitBean(ClassLoader loader, Object mediaInfo, Object layerPicInfo, View avatarView) throws Exception {
        Class<?> initClz = findFirstClass(loader, LAYER_INIT_BEAN_CLASSES);
        Object bean = newInstanceBestEffort(initClz);
        setField(bean, "enterPos", 0);
        setField(bean, "mTransAnimBeanCreatorId", 0);
        ArrayList<Object> list = new ArrayList<>();
        list.add(mediaInfo);
        setField(bean, "richMediaDataList", list);
        setField(bean, "transitionBean", createTransitionBean(loader, layerPicInfo, avatarView));
        return bean;
    }

    private Object createLayerItemMediaInfo(ClassLoader loader, Object layerPicInfo, File file) throws Exception {
        Class<?> clz = findFirstClass(loader, LAYER_ITEM_MEDIA_INFO_CLASSES);
        Object bean = newInstanceBestEffort(clz);
        setField(bean, "_mediaId", file.getName() + "_0");
        setField(bean, "extraData", null);
        setField(bean, "invalid", false);
        setField(bean, "layerPicInfo", layerPicInfo);
        setField(bean, "layerVideoInfo", null);
        return bean;
    }

    private Object createLayerPicInfo(ClassLoader loader, Object picInfo) throws Exception {
        Class<?> clz = findFirstClass(loader, LAYER_PIC_INFO_CLASSES);
        Object bean = newInstanceBestEffort(clz);
        setField(bean, "_currentPicInfo", picInfo);
        setField(bean, "bigPicInfo", picInfo);
        setField(bean, "downloadPicInfo", null);
        setField(bean, "originPicInfo", picInfo);
        setField(bean, "picId", null);
        setField(bean, "smallPicInfo", picInfo);
        return bean;
    }

    private Object createPicInfo(ClassLoader loader, File file) throws Exception {
        Class<?> clz = findFirstClass(loader, PIC_INFO_CLASSES);
        Object bean = newInstanceBestEffort(clz);
        int[] size = readImageSize(file);
        setField(bean, "height", size[1]);
        setField(bean, "localPath", file.getAbsolutePath());
        setField(bean, "size", file.length());
        setField(bean, "url", file.getAbsolutePath());
        setField(bean, "width", size[0]);
        return bean;
    }

    private Object createTransitionBean(ClassLoader loader, Object layerPicInfo, View avatarView) throws Exception {
        Class<?> clz = findFirstClass(loader, TRANS_ANIM_BEAN_CLASSES);
        Object bean = newInstanceBestEffort(clz);
        Object sourceRect = createSourceRect(loader, getViewRectOnScreen(avatarView));
        setField(bean, "fadeCoverTimeMs", 0);
        setField(bean, "imageRect", sourceRect);
        setField(bean, "isBackTransition", true);
        setField(bean, "isCarvedAnimOpen", false);
        setField(bean, "layerPicInfo", layerPicInfo);
        setField(bean, "layoutRect", sourceRect);
        setField(bean, "scaleType", android.widget.ImageView.ScaleType.CENTER_CROP);
        setField(bean, "transitionDelayTimeMs", 0);
        setField(bean, "transitionDuration", 150);
        setField(bean, "uUid", UUID.randomUUID());
        return bean;
    }

    private Object createSourceRect(ClassLoader loader, Rect rect) throws Exception {
        Class<?> clz = findFirstClass(loader, SOURCE_RECT_CLASSES);
        try {
            return XposedHelpers.newInstance(clz, rect.left, rect.top, rect.right, rect.bottom);
        } catch (Throwable ignored) {
        }
        Object bean = newInstanceBestEffort(clz);
        setField(bean, "left", rect.left);
        setField(bean, "top", rect.top);
        setField(bean, "right", rect.right);
        setField(bean, "bottom", rect.bottom);
        return bean;
    }

    private Object newInstanceBestEffort(Class<?> clz) throws Exception {
        try {
            return XposedHelpers.newInstance(clz);
        } catch (Throwable ignored) {
        }
        Constructor<?>[] constructors = clz.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            try {
                constructor.setAccessible(true);
                Class<?>[] types = constructor.getParameterTypes();
                Object[] args = new Object[types.length];
                for (int i = 0; i < types.length; i++) {
                    args[i] = defaultValue(types[i]);
                }
                return constructor.newInstance(args);
            } catch (Throwable ignored) {
            }
        }
        throw new NoSuchMethodException("No usable constructor for " + clz.getName());
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == Boolean.TYPE) {
            return false;
        }
        if (type == Character.TYPE) {
            return Character.valueOf('\0');
        }
        if (type == Byte.TYPE) {
            return Byte.valueOf((byte) 0);
        }
        if (type == Short.TYPE) {
            return Short.valueOf((short) 0);
        }
        if (type == Integer.TYPE) {
            return Integer.valueOf(0);
        }
        if (type == Long.TYPE) {
            return Long.valueOf(0L);
        }
        if (type == Float.TYPE) {
            return Float.valueOf(0f);
        }
        if (type == Double.TYPE) {
            return Double.valueOf(0d);
        }
        return null;
    }

    private void setField(Object obj, String name, Object value) {
        try {
            XposedHelpers.setObjectField(obj, name, value);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Field field = XposedHelpers.findField(obj.getClass(), name);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Throwable e) {
            throw new IllegalStateException("setField failed: " + obj.getClass().getName()
                + "." + name + " -> " + String.valueOf(e.getMessage()), e);
        }
    }

    private Rect getViewRectOnScreen(View view) {
        if (view == null) {
            return new Rect(0, 0, 1, 1);
        }
        int[] location = new int[2];
        try {
            view.getLocationOnScreen(location);
            return new Rect(location[0], location[1], location[0] + Math.max(1, view.getWidth()), location[1] + Math.max(1, view.getHeight()));
        } catch (Throwable ignored) {
            return new Rect(0, 0, Math.max(1, view.getWidth()), Math.max(1, view.getHeight()));
        }
    }

    private int[] readImageSize(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        int width = options.outWidth > 0 ? options.outWidth : 640;
        int height = options.outHeight > 0 ? options.outHeight : 640;
        return new int[]{width, height};
    }

    private Uri buildPreviewUri(Context context, File file) {
        String[] candidates = new String[]{
            "androidx.core.content.FileProvider",
            "android.support.v4.content.FileProvider"
        };
        for (String className : candidates) {
            try {
                Class<?> fileProvider = context.getClassLoader().loadClass(className);
                return (Uri) XposedHelpers.callStaticMethod(
                    fileProvider,
                    "getUriForFile",
                    context,
                    context.getPackageName() + ".fileprovider",
                    file
                );
            } catch (Throwable ignored) {
            }
        }
        return Uri.fromFile(file);
    }

    private File downloadAvatar(Context context, String url, String senderUin, String peerUin, boolean previewOnly) throws Exception {
        File parent;
        if (previewOnly) {
            parent = new File(context.getCacheDir(), "myhooks/avatar-preview");
        } else {
            parent = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MyHooks/qq-avatar"
            );
        }
        if (!parent.exists()) {
            if (!parent.mkdirs() && !parent.exists()) {
                throw new java.io.FileNotFoundException("mkdirs failed: " + parent.getAbsolutePath());
            }
        }
        String peer = peerUin == null || peerUin.length() == 0 ? "0" : peerUin;
        String sender = senderUin == null || senderUin.length() == 0 ? "unknown" : senderUin;
        File file = new File(parent, "avatar_" + peer + "_" + sender + "_" + System.currentTimeMillis() + ".jpg");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 MyHooks");
        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = connection.getInputStream();
            output = new FileOutputStream(file);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) >= 0) {
                output.write(buffer, 0, len);
            }
            output.flush();
        } finally {
            if (input != null) {
                input.close();
            }
            if (output != null) {
                output.close();
            }
            connection.disconnect();
        }
        if (!file.isFile() || file.length() <= 0) {
            file.delete();
            throw new IllegalStateException("empty file");
        }
        return file;
    }

    private boolean isLikelyAioMsgItem(Class<?> type) {
        if (type == null) {
            return false;
        }
        String name = type.getName();
        return "com.tencent.mobileqq.aio.msg.AIOMsgItem".equals(name)
            || name.endsWith(".AIOMsgItem")
            || name.contains(".aio.msg.") && name.endsWith("MsgItem");
    }

    private Class<?> findFirstClass(ClassLoader loader, String[] candidates) throws ClassNotFoundException {
        String className = findFirstClassName(loader, candidates);
        return XposedHelpers.findClass(className, loader);
    }

    private String findFirstClassName(ClassLoader loader, String[] candidates) throws ClassNotFoundException {
        Throwable lastError = null;
        for (String candidate : candidates) {
            try {
                XposedHelpers.findClass(candidate, loader);
                return candidate;
            } catch (Throwable e) {
                lastError = e;
            }
        }
        throw new ClassNotFoundException("No matching class in candidates", lastError);
    }

    private static class QqMsgRecord {
        final String senderUin;
        final String peerUin;
        final int chatType;
        final String rawString;

        QqMsgRecord(String senderUin, String peerUin, int chatType, String rawString) {
            this.senderUin = senderUin;
            this.peerUin = peerUin;
            this.chatType = chatType;
            this.rawString = rawString;
        }
    }
}
