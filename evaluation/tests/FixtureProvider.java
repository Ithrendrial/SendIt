package com.example.sendit.lightweight;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

// Runs in the test APK's own process, which does not include the target APK's Kotlin runtime.
public class FixtureProvider extends ContentProvider {
    private static final String TARGET = "com.example.sendit.lightweight";
    private static final String AUTHORITY = TARGET + ".test.fixtures";
    private File directory() { return new File(getContext().getExternalFilesDir(null), "fixtures"); }

    @Override public boolean onCreate() {
        directory().mkdirs();
        try {
            for (String name : getContext().getAssets().list("evaluation")) {
                try (InputStream input = getContext().getAssets().open("evaluation/" + name);
                     FileOutputStream output = new FileOutputStream(new File(directory(), name))) {
                    FileUtils.copy(input, output);
                }
            }
        } catch (IOException failure) { throw new IllegalStateException("Cannot load test videos", failure); }
        return true;
    }

    // Only the evaluation app may request a fixture grant. This avoids automating the
    // system picker, which is preparation outside the measured submission workflow.
    @Override public Bundle call(String method, String id, Bundle extras) {
        try {
            if (!method.equals("video") || Binder.getCallingUid() !=
                getContext().getPackageManager().getApplicationInfo(TARGET, 0).uid) throw new SecurityException();
            source(id);
            Uri uri = new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(id).build();
            long identity = Binder.clearCallingIdentity();
            try {
                getContext().grantUriPermission(TARGET, uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            } finally { Binder.restoreCallingIdentity(identity); }
            Bundle result = new Bundle();
            result.putParcelable("uri", uri);
            return result;
        } catch (PackageManager.NameNotFoundException | FileNotFoundException failure) {
            throw new IllegalArgumentException("Unknown evaluation fixture", failure);
        }
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        checkCaller();
        try {
            File file = source(uri.getLastPathSegment());
            MatrixCursor rows = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });
            rows.addRow(new Object[] { file.getName(), file.length() });
            return rows;
        } catch (FileNotFoundException failure) { throw new IllegalArgumentException(failure); }
    }
    @Override public String getType(Uri uri) { return "video/mp4"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        checkCaller();
        if (!mode.equals("r")) throw new FileNotFoundException("Test videos are read only");
        return ParcelFileDescriptor.open(source(uri.getLastPathSegment()), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    private void checkCaller() {
        try {
            if (Binder.getCallingUid() != getContext().getPackageManager().getApplicationInfo(TARGET, 0).uid)
                throw new SecurityException("Only the evaluation app may read fixtures");
        } catch (PackageManager.NameNotFoundException failure) { throw new SecurityException(failure); }
    }
    private File source(String id) throws FileNotFoundException {
        if (!id.matches("sendit-eval-[A-Za-z0-9_.-]+\\.mp4")) throw new FileNotFoundException(id);
        File file = new File(directory(), id);
        if (!file.isFile()) throw new FileNotFoundException(id);
        return file;
    }
}
