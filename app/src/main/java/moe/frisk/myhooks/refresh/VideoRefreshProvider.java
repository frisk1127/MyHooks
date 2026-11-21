package moe.frisk.myhooks.refresh;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

public final class VideoRefreshProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID && uid != Process.myUid()) throw new SecurityException();
        if (!"read".equals(method)) throw new IllegalArgumentException(method);
        return VideoRefreshConfig.snapshot(getContext());
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String order) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
}
