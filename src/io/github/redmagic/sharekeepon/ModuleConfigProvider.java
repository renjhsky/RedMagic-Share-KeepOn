package io.github.redmagic.sharekeepon;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Only a non-sensitive enabled bit is exposed; all remote writes are rejected. */
public final class ModuleConfigProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"get".equals(method)) throw new SecurityException("Read-only configuration");
        Bundle result = new Bundle(); result.putBoolean("enabled", ModuleConfig.enabled(getContext())); return result;
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.sharekeepon.config"; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new SecurityException("Read-only configuration"); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new SecurityException("Read-only configuration"); }
    @Override public int delete(Uri u, String s, String[] a) { throw new SecurityException("Read-only configuration"); }
}
