package com.pipa.penkit;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Exposes quick-key config to the system_server hook. */
public final class ConfigProvider extends ContentProvider {

    public static final String AUTHORITY = "com.pipa.penkit.config";
    private static final String PREFS = "penkit_prefs";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        Context ctx = getContext();
        if (ctx == null) {
            return out;
        }
        if ("enabled".equals(method)) {
            out.putBoolean("enabled", ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean("relay_enabled", true));
            return out;
        }
        if (!"target".equals(method)) {
            return out;
        }
        int slot = 1;
        try {
            slot = Integer.parseInt(arg == null ? "1" : arg);
        } catch (NumberFormatException ignored) {
        }
        String key = slot == 1 ? "target_single" : slot == 2 ? "target_double" : "target_long";
        String value = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "");
        out.putString("target", value == null ? "" : value);
        return out;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
