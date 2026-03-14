package moe.frisk.myhooks.util;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public final class AndroidUi {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private AndroidUi() {
    }

    public static void runOnMainThread(Runnable block) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block.run();
        } else {
            MAIN_HANDLER.post(block);
        }
    }

    public static void toast(final Context context, final String text) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void copyText(Context context, String label, String text) {
        ClipboardManager clipboard =
            (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    public static void showDialog(Context context, String title, String message) {
        showDialog(context, title, message, "关闭", null, null, null, null, null);
    }

    public static void showDialog(final Context context,
                                  final String title,
                                  final String message,
                                  final String positiveText,
                                  final Runnable onPositive,
                                  final String negativeText,
                                  final Runnable onNegative,
                                  final String neutralText,
                                  final Runnable onNeutral) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(positiveText, new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            if (onPositive != null) {
                                onPositive.run();
                            }
                        }
                    });
                if (negativeText != null) {
                    builder.setNegativeButton(negativeText, new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            if (onNegative != null) {
                                onNegative.run();
                            }
                        }
                    });
                }
                if (neutralText != null) {
                    builder.setNeutralButton(neutralText, new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            if (onNeutral != null) {
                                onNeutral.run();
                            }
                        }
                    });
                }
                builder.show();
            }
        });
    }
}
