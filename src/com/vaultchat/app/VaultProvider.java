package com.vaultchat.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;

/** Serves decrypted files to the system viewer, one item at a time, read-only. */
public class VaultProvider extends ContentProvider {

    public static final String AUTHORITY = "com.vaultchat.app.files";

    public static Uri uriFor(String id) {
        return Uri.parse("content://" + AUTHORITY + "/" + id);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        try {
            String id = uri.getLastPathSegment();
            Vault v = Vault.get(getContext());
            if (!v.unlocked()) throw new FileNotFoundException("locked");
            File f = v.decryptToCache(id);
            if (f == null) throw new FileNotFoundException("no item");
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new FileNotFoundException(e.toString());
        }
    }

    @Override
    public String getType(Uri uri) {
        try {
            Vault v = Vault.get(getContext());
            JSONObject o = v.find(uri.getLastPathSegment());
            if (o != null) return o.optString("mime", "application/octet-stream");
        } catch (Exception ignored) {
        }
        return "application/octet-stream";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
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
