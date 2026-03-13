package moe.frisk.myhooks.util

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object AndroidUi {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    fun toast(context: Context, text: String) {
        runOnMainThread {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    fun copyText(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun showDialog(
        context: Context,
        title: String,
        message: String,
        positiveText: String = "关闭",
        onPositive: (() -> Unit)? = null,
        negativeText: String? = null,
        onNegative: (() -> Unit)? = null,
        neutralText: String? = null,
        onNeutral: (() -> Unit)? = null,
    ) {
        runOnMainThread {
            val builder = AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveText) { _, _ -> onPositive?.invoke() }
            if (negativeText != null) {
                builder.setNegativeButton(negativeText) { _, _ -> onNegative?.invoke() }
            }
            if (neutralText != null) {
                builder.setNeutralButton(neutralText) { _, _ -> onNeutral?.invoke() }
            }
            builder.show()
        }
    }
}
