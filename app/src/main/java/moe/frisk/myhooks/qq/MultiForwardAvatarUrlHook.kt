package moe.frisk.myhooks.qq

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import moe.frisk.myhooks.AppHook
import moe.frisk.myhooks.util.AndroidUi
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.WeakHashMap

object MultiForwardAvatarUrlHook : AppHook {

    override val key: String = "qq_multi_forward_avatar"
    override val targetPackages: Set<String> = setOf(
        "com.tencent.mobileqq",
        "com.tencent.tim",
    )

    private const val avatarComponentClassName =
        "com.tencent.mobileqq.aio.msglist.holder.component.avatar.AIOAvatarContentComponent"
    private const val multiForwardActivityClassName = "com.tencent.mobileqq.activity.MultiForwardActivity"
    private const val installedTag = "myhooks.multi_forward_avatar.installed"
    private val fromFaceUrlRegex = Regex("""(?:^|[,{ ])fromFaceUrl=([^,}]+)""")
    private val viewState = WeakHashMap<View, String>()
    @Volatile
    private var hooked = false

    override fun onPackageLoaded(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (hooked) {
            return
        }
        val componentClass = runCatching {
            XposedHelpers.findClass(avatarComponentClassName, lpparam.classLoader)
        }.getOrNull() ?: return
        hookAllCandidateMethods(componentClass)
        hooked = true
        XposedBridge.log("[MyHooks/$key] installed for ${lpparam.packageName}")
    }

    private fun hookAllCandidateMethods(componentClass: Class<*>) {
        componentClass.declaredMethods
            .filter { it.returnType == Void.TYPE && it.parameterTypes.isEmpty() }
            .forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        bindAvatarActions(param.thisObject)
                    }
                })
            }
    }

    private fun bindAvatarActions(component: Any) {
        val avatarContainer = findAvatarContainer(component) ?: return
        val context = avatarContainer.context ?: return
        if (context.javaClass.name != multiForwardActivityClassName) {
            return
        }
        val msgRecord = findMsgRecord(component) ?: return
        val state = "${msgRecord.senderUin}|${msgRecord.peerUin}|${extractFromFaceUrl(msgRecord)}"
        if (viewState[avatarContainer] == state) {
            return
        }
        viewState[avatarContainer] = state
        avatarContainer.setTag(installedTag)
        avatarContainer.setOnClickListener {
            val url = extractFromFaceUrl(msgRecord)
            AndroidUi.showDialog(
                context = context,
                title = "合并转发头像",
                message = buildDialogMessage(msgRecord, url),
                negativeText = if (url.isNullOrEmpty()) null else "保存头像",
                onNegative = if (url.isNullOrEmpty()) null else ({
                    saveAvatar(context, url, msgRecord.senderUin, msgRecord.peerUin)
                }),
                neutralText = if (url.isNullOrEmpty()) "复制详情" else "复制链接",
                onNeutral = {
                    val text = url ?: msgRecord.rawString
                    AndroidUi.copyText(context, "avatar-url", text)
                    AndroidUi.toast(context, "已复制")
                },
            )
        }
        avatarContainer.setOnLongClickListener {
            val url = extractFromFaceUrl(msgRecord)
            if (url.isNullOrEmpty()) {
                AndroidUi.toast(context, "未找到 fromFaceUrl")
            } else {
                saveAvatar(context, url, msgRecord.senderUin, msgRecord.peerUin)
            }
            true
        }
    }

    private fun findAvatarContainer(component: Any): ViewGroup? {
        component.javaClass.declaredFields.forEach { field ->
            runCatching {
                field.isAccessible = true
                val value = field.get(component) ?: return@runCatching
                val actual = runCatching { XposedHelpers.callMethod(value, "getValue") }.getOrNull() ?: value
                if (actual is ViewGroup) {
                    return actual
                }
            }
        }
        return null
    }

    private fun findMsgRecord(component: Any): QqMsgRecord? {
        component.javaClass.declaredFields.forEach { field ->
            runCatching {
                field.isAccessible = true
                val value = field.get(component) ?: return@runCatching
                val msgRecord = runCatching { XposedHelpers.callMethod(value, "getMsgRecord") }.getOrNull() ?: return@runCatching
                val senderUin = XposedHelpers.getObjectField(msgRecord, "senderUin") as? String ?: return@runCatching
                val peerUin = XposedHelpers.getObjectField(msgRecord, "peerUin") as? String ?: ""
                val chatType = (XposedHelpers.getIntField(msgRecord, "chatType"))
                return QqMsgRecord(senderUin, peerUin, chatType, msgRecord.toString())
            }
        }
        return null
    }

    private fun extractFromFaceUrl(msgRecord: QqMsgRecord): String? {
        val raw = fromFaceUrlRegex.find(msgRecord.rawString)?.groupValues?.getOrNull(1)?.trim()
        if (raw.isNullOrEmpty() || raw == "null") {
            return null
        }
        return raw
    }

    private fun buildDialogMessage(msgRecord: QqMsgRecord, url: String?): String {
        return buildString {
            append("senderUin: ").appendLine(msgRecord.senderUin)
            if (msgRecord.peerUin.isNotEmpty()) {
                append("peerUin: ").appendLine(msgRecord.peerUin)
            }
            append("chatType: ").appendLine(msgRecord.chatType)
            append("fromFaceUrl: ").append(url ?: "未找到")
        }
    }

    private fun saveAvatar(context: Context, url: String, senderUin: String, peerUin: String) {
        AndroidUi.toast(context, "开始下载头像")
        Thread {
            runCatching {
                val file = downloadAvatar(url, senderUin, peerUin)
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                AndroidUi.toast(context, "已保存: ${file.name}")
            }.onFailure {
                AndroidUi.toast(context, "保存失败: ${it.message ?: it.javaClass.simpleName}")
                XposedBridge.log("[MyHooks/$key] ${it.stackTraceToString()}")
            }
        }.start()
    }

    private fun downloadAvatar(url: String, senderUin: String, peerUin: String): File {
        val parent = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MyHooks/qq-avatar"
        )
        if (!parent.exists()) {
            parent.mkdirs()
        }
        val file = File(parent, "avatar_${peerUin.ifBlank { "0" }}_${senderUin}_${System.currentTimeMillis()}.jpg")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 MyHooks")
        }
        connection.inputStream.use { input ->
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val len = input.read(buffer)
                    if (len < 0) {
                        break
                    }
                    output.write(buffer, 0, len)
                }
            }
        }
        if (file.length() <= 0L) {
            file.delete()
            error("empty file")
        }
        return file
    }

    private data class QqMsgRecord(
        val senderUin: String,
        val peerUin: String,
        val chatType: Int,
        val rawString: String,
    )
}
