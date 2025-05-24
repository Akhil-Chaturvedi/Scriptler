package com.bytesmith.scriptler;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StorageHelper {
    private static final String TAG = "StorageHelper";
    private static Uri selectedTreeUri; // Renamed for clarity, stores the URI of the selected directory tree

    // Allowed script extensions
    private static final List<String> ALLOWED_SCRIPT_EXTENSIONS = Arrays.asList(".py", ".js");

    public static void openStoragePicker(Context context) {
        // This method is called from MainActivity's onActivityResult, so it should be an Activity context
        ((android.app.Activity) context).startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 100);
    }

    // Call this from onActivityResult in your Activity
    public static void setSelectedTreeUri(Context context, Uri uri) {
        selectedTreeUri = uri;
        // Persist access permissions
        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        context.getContentResolver().takePersistableUriPermission(uri, takeFlags);

        // Store the URI string in shared preferences for persistence across app restarts
        context.getSharedPreferences("StoragePrefs", Context.MODE_PRIVATE)
                .edit()
                .putString("selectedTreeUri", uri.toString())
                .apply();
    }

    // Call this in your Application's onCreate or MainActivity's onCreate
    public static void loadSelectedTreeUri(Context context) {
        String uriString = context.getSharedPreferences("StoragePrefs", Context.MODE_PRIVATE)
                .getString("selectedTreeUri", null);
        if (uriString != null) {
            selectedTreeUri = Uri.parse(uriString);
            // Check if we still have permission
            if (!hasPersistedUriPermissions(context, selectedTreeUri)) {
                Log.w(TAG, "Permissions for URI " + selectedTreeUri + " were lost. Clearing stored URI.");
                selectedTreeUri = null; // Clear if permissions are lost
                context.getSharedPreferences("StoragePrefs", Context.MODE_PRIVATE)
                       .edit()
                       .remove("selectedTreeUri")
                       .apply();
            }
        }
    }
    
    private static boolean hasPersistedUriPermissions(Context context, Uri uri) {
        List<UriPermission> persistedUriPermissions = context.getContentResolver().getPersistedUriPermissions();
        for (UriPermission permission : persistedUriPermissions) {
            if (permission.getUri().equals(uri) && permission.isReadPermission() && permission.isWritePermission()) {
                return true;
            }
        }
        return false;
    }

    public static DocumentFile getScriptsDirectory(Context context) {
        if (selectedTreeUri == null) {
            loadSelectedTreeUri(context); // Try to load if not already set
            if (selectedTreeUri == null) {
                 Log.e(TAG, "Script directory URI not selected or permission lost.");
                return null; // Or prompt user to select directory again
            }
        }
        return DocumentFile.fromTreeUri(context, selectedTreeUri);
    }
    
    public static DocumentFile findFile(Context context, String fileName) {
        DocumentFile baseDir = getScriptsDirectory(context);
        if (baseDir == null) {
            return null;
        }
        return baseDir.findFile(fileName);
    }

    public static List<DocumentFile> listScriptFiles(Context context) {
        List<DocumentFile> scriptFiles = new ArrayList<>();
        DocumentFile rootDir = getScriptsDirectory(context);

        if (rootDir != null && rootDir.exists() && rootDir.isDirectory()) {
            for (DocumentFile file : rootDir.listFiles()) {
                if (file.isFile()) {
                    String fileName = file.getName();
                    if (fileName != null) {
                        String lowerCaseFileName = fileName.toLowerCase();
                        for (String ext : ALLOWED_SCRIPT_EXTENSIONS) {
                            if (lowerCaseFileName.endsWith(ext)) {
                                scriptFiles.add(file);
                                break;
                            }
                        }
                    }
                }
            }
        } else {
            Log.e(TAG, "Scripts directory is not accessible or not a directory.");
        }
        return scriptFiles;
    }

    public static String readScriptContent(Context context, DocumentFile scriptFile) {
        if (scriptFile == null || !scriptFile.canRead()) {
            Log.e(TAG, "Cannot read script file or file is null.");
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(scriptFile.getUri());
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found: " + scriptFile.getUri(), e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Error reading script content: " + scriptFile.getUri(), e);
            return null;
        }
        return stringBuilder.toString();
    }

    public static boolean saveScriptContent(Context context, DocumentFile scriptFile, String content) {
        if (scriptFile == null || !scriptFile.canWrite()) {
             Log.e(TAG, "Cannot write to script file or file is null/not writable.");
            return false;
        }
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(scriptFile.getUri());
             OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
            writer.write(content);
            return true;
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found for writing: " + scriptFile.getUri(), e);
            return false;
        } catch (IOException e) {
            Log.e(TAG, "Error writing script content: " + scriptFile.getUri(), e);
            return false;
        }
    }

    public static boolean deleteScriptFile(DocumentFile scriptFile) {
        if (scriptFile == null) {
            Log.e(TAG, "Cannot delete null script file.");
            return false;
        }
        try {
            if (scriptFile.exists()) {
                return scriptFile.delete();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception deleting file: " + scriptFile.getUri(), e);
        }
        return false;
    }

    public static DocumentFile createScriptFile(Context context, String fileNameWithExtension) {
        DocumentFile rootDir = getScriptsDirectory(context);
        if (rootDir == null || !rootDir.canWrite()) {
            Log.e(TAG, "Scripts directory is not accessible or not writable for creating file: " + fileNameWithExtension);
            return null;
        }

        // Check if file already exists
        DocumentFile existingFile = rootDir.findFile(fileNameWithExtension);
        if (existingFile != null && existingFile.exists()) {
            Log.w(TAG, "Script file already exists: " + fileNameWithExtension);
            return existingFile; // Or handle as an error, depending on desired behavior
        }
        
        String mimeType = "text/plain"; // Default MIME type
        if (fileNameWithExtension.endsWith(".py")) {
            mimeType = "application/x-python"; // More specific, though "text/plain" usually works
        } else if (fileNameWithExtension.endsWith(".js")) {
            mimeType = "application/javascript"; // More specific
        }

        try {
            return rootDir.createFile(mimeType, fileNameWithExtension);
        } catch (Exception e) {
            Log.e(TAG, "Error creating script file: " + fileNameWithExtension, e);
        }
        return null;
    }
    
    // This method is no longer directly used for getting directory path for scripts,
    // but kept for reference or other potential uses.
    // It's important that primary script operations use DocumentFile from getScriptsDirectory(context).
    public static String getLegacyScriptsDirectoryPath(Context context) {
        if (selectedTreeUri != null) {
            DocumentFile rootDir = DocumentFile.fromTreeUri(context, selectedTreeUri);
            if (rootDir != null && rootDir.exists()) {
                // This returns a path that might not be directly usable for java.io.File
                // if it's a content URI. Prefer using DocumentFile directly.
                return rootDir.getUri().toString(); 
            }
        }
        // Fallback to default directory - THIS IS PROBLEMATIC WITH SAF
        // This path is likely not accessible directly for write operations on modern Android
        // without specific permissions or using MediaStore, which is not what SAF is about.
        Log.w(TAG, "Falling back to legacy public directory path. This is likely not reliable for SAF.");
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getPath() + "/Scriptler";
    }

    public static boolean hasStorageAccess(Context context) {
        if (selectedTreeUri == null) {
            loadSelectedTreeUri(context);
        }
        return selectedTreeUri != null && hasPersistedUriPermissions(context, selectedTreeUri);
    }
} 