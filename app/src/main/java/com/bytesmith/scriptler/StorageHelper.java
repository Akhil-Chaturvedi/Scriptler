package com.bytesmith.scriptler;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import androidx.documentfile.provider.DocumentFile;

public class StorageHelper {
    private static Uri selectedUri;

    public static void openStoragePicker(Context context) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        context.startActivityForResult(intent, 100);
    }

    public static void requestStoragePermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Uri uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FScriptler");
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
                context.startActivityForResult(intent, 100);
            }
        } else {
            // Handle older versions if needed
        }
    }

    public static void setSelectedUri(Uri uri) {
        selectedUri = uri;
    }

    public static String getScriptsDirectory(Context context) {
        if (selectedUri != null) {
            DocumentFile rootDir = DocumentFile.fromTreeUri(context, selectedUri);
            if (rootDir != null && rootDir.exists()) {
                return rootDir.getUri().getPath();
            }
        }
        // Fallback to default directory
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getPath() + "/Scriptler";
    }

    public static boolean hasStorageAccess() {
        return selectedUri != null;
    }
} 