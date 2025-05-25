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
    
    public static DocumentFile findScriptFile(Context context, String scriptName, String extension) {
        DocumentFile rootDir = getScriptsDirectory(context);
        if (rootDir == null) {
            Log.e(TAG, "Root scripts directory not found.");
            return null;
        }
        DocumentFile scriptDir = rootDir.findFile(scriptName);
        if (scriptDir == null || !scriptDir.exists() || !scriptDir.isDirectory()) {
            Log.e(TAG, "Script directory not found for: " + scriptName);
            return null;
        }
        String fileNameWithExtension = scriptName + extension;
        DocumentFile scriptFile = scriptDir.findFile(fileNameWithExtension);
        if (scriptFile == null || !scriptFile.exists() || !scriptFile.isFile()) {
            Log.e(TAG, "Script file not found: " + fileNameWithExtension + " in " + scriptName);
            return null;
        }
        return scriptFile;
    }

    public static List<DocumentFile> listScriptFiles(Context context) {
        List<DocumentFile> scriptFiles = new ArrayList<>();
        DocumentFile rootDir = getScriptsDirectory(context);

        if (rootDir != null && rootDir.exists() && rootDir.isDirectory()) {
            for (DocumentFile dir : rootDir.listFiles()) {
                if (dir.isDirectory()) {
                    String dirName = dir.getName();
                    if (dirName != null) {
                        for (String ext : ALLOWED_SCRIPT_EXTENSIONS) {
                            String scriptFileName = dirName + ext;
                            DocumentFile scriptFile = dir.findFile(scriptFileName);
                            if (scriptFile != null && scriptFile.exists() && scriptFile.isFile()) {
                                scriptFiles.add(scriptFile);
                                break; // Found script in this directory, move to next directory
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
            DocumentFile scriptDir = scriptFile.getParentFile();
            if (scriptDir != null && scriptDir.exists() && scriptDir.isDirectory()) {
                // Attempt to delete the directory. This should also delete its contents.
                if (scriptDir.delete()) {
                    Log.i(TAG, "Successfully deleted script directory: " + scriptDir.getUri());
                    return true;
                } else {
                    // Fallback: if directory deletion fails, try deleting individual files
                    boolean fileDeleted = scriptFile.delete();
                    if (fileDeleted) {
                         Log.w(TAG, "Deleted script file but failed to delete parent directory: " + scriptDir.getUri() + ". Manual cleanup might be needed.");
                         // Try deleting directory again if it's now empty
                         DocumentFile[] filesInDir = scriptDir.listFiles();
                         if (filesInDir == null || filesInDir.length == 0) {
                             if (scriptDir.delete()) {
                                 Log.i(TAG, "Successfully deleted parent directory after deleting the script file: " + scriptDir.getUri());
                             } else {
                                 Log.w(TAG, "Still failed to delete parent directory even after it was supposedly empty: " + scriptDir.getUri());
                             }
                         }
                         return true; // Consider it a partial success if file is gone
                    } else {
                        Log.e(TAG, "Failed to delete script file and its directory: " + scriptDir.getUri());
                        return false;
                    }
                }
            } else {
                Log.w(TAG, "Parent directory of script file not found or is not a directory. Attempting to delete file directly.");
                // If parent directory is null, it might be a file in the root (old structure)
                // Or the passed DocumentFile is the directory itself
                if (scriptFile.isDirectory()) { // If scriptFile is actually the directory
                    Log.i(TAG, "Attempting to delete scriptFile as a directory: " + scriptFile.getUri());
                    return scriptFile.delete();
                } else if (scriptFile.exists()) { // If it's a file, try deleting it directly
                    Log.i(TAG, "Attempting to delete scriptFile as a file: " + scriptFile.getUri());
                    return scriptFile.delete();
                } else {
                    Log.w(TAG, "Script file does not exist, cannot delete: " + scriptFile.getUri());
                    return false; // File doesn't exist, so deletion is "successful" in a way, or indicate not found
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception deleting file or directory: " + scriptFile.getUri(), e);
        }
        return false;
    }

    public static DocumentFile createScriptDirectoryAndFile(Context context, String scriptName, String extension) {
        DocumentFile rootDir = getScriptsDirectory(context);
        if (rootDir == null || !rootDir.canWrite()) {
            Log.e(TAG, "Scripts directory is not accessible or not writable for creating script: " + scriptName);
            return null;
        }

        // Create a directory for the script
        DocumentFile scriptDir = rootDir.findFile(scriptName);
        if (scriptDir == null || !scriptDir.exists()) {
            scriptDir = rootDir.createDirectory(scriptName);
            if (scriptDir == null) {
                Log.e(TAG, "Failed to create directory for script: " + scriptName);
                return null;
            }
        } else if (!scriptDir.isDirectory()) {
            Log.e(TAG, "A file with the script name already exists and is not a directory: " + scriptName);
            return null;
        }

        String fileNameWithExtension = scriptName + extension;
        // Check if file already exists in the script directory
        DocumentFile scriptFile = scriptDir.findFile(fileNameWithExtension);
        if (scriptFile != null && scriptFile.exists()) {
            Log.w(TAG, "Script file already exists: " + fileNameWithExtension + " in " + scriptName);
            return scriptFile; // Or handle as an error
        }
        
        String mimeType = "text/plain"; // Default MIME type
        if (extension.equals(".py")) {
            mimeType = "application/x-python";
        } else if (extension.equals(".js")) {
            mimeType = "application/javascript";
        }

        try {
            return scriptDir.createFile(mimeType, fileNameWithExtension);
        } catch (Exception e) {
            Log.e(TAG, "Error creating script file: " + fileNameWithExtension + " in " + scriptName, e);
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

    public static void appendToLog(Context context, String scriptDirectoryUriString, String logMessage) {
        if (scriptDirectoryUriString == null || scriptDirectoryUriString.isEmpty()) {
            Log.e(TAG, "Script directory URI string is null or empty. Cannot write log.");
            return;
        }

        Uri scriptDirUri = Uri.parse(scriptDirectoryUriString);
        DocumentFile scriptDir = DocumentFile.fromTreeUri(context, scriptDirUri); // Use fromTreeUri if it's a tree URI

        if (scriptDir == null || !scriptDir.exists() || !scriptDir.isDirectory()) {
             // Fallback: if scriptDirectoryUriString is actually a single document URI (less ideal)
            scriptDir = DocumentFile.fromSingleUri(context, scriptDirUri);
            if (scriptDir != null && scriptDir.isFile()) { // If it was a file URI, get parent
                scriptDir = scriptDir.getParentFile();
            }
            if (scriptDir == null || !scriptDir.exists() || !scriptDir.isDirectory()) {
                Log.e(TAG, "Script directory not found or is not a directory: " + scriptDirectoryUriString);
                return;
            }
        }

        if (!scriptDir.canWrite()) {
            Log.e(TAG, "Cannot write to script directory: " + scriptDirectoryUriString);
            return;
        }

        DocumentFile logFile = scriptDir.findFile("execution.log");
        if (logFile == null || !logFile.exists()) {
            logFile = scriptDir.createFile("text/plain", "execution.log");
            if (logFile == null) {
                Log.e(TAG, "Failed to create execution.log in " + scriptDirectoryUriString);
                return;
            }
        } else if (!logFile.isFile()) {
            Log.e(TAG, "execution.log exists but is not a file in " + scriptDirectoryUriString);
            return;
        }

        try (OutputStream outputStream = context.getContentResolver().openOutputStream(logFile.getUri(), "wa"); // "wa" for append
             OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
            writer.write(logMessage + "\n"); // Ensure new line for each message
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Log file not found for appending: " + logFile.getUri(), e);
        } catch (IOException e) {
            Log.e(TAG, "Error appending to log file: " + logFile.getUri(), e);
        }
    }

    public static String readLogFile(Context context, String scriptDirectoryUriString) {
        if (scriptDirectoryUriString == null || scriptDirectoryUriString.isEmpty()) {
            Log.e(TAG, "Script directory URI string is null or empty. Cannot read log file.");
            return "Error: Script directory URI not provided.";
        }

        Uri scriptDirUri = Uri.parse(scriptDirectoryUriString);
        DocumentFile scriptDir = DocumentFile.fromTreeUri(context, scriptDirUri); // Assuming it's a tree URI for the directory

        if (scriptDir == null || !scriptDir.exists() || !scriptDir.isDirectory()) {
            // Fallback for cases where fromTreeUri might not be appropriate or fails
            scriptDir = DocumentFile.fromSingleUri(context, scriptDirUri);
             if (scriptDir != null && scriptDir.isFile()) { // If it was a file URI, get parent
                scriptDir = scriptDir.getParentFile();
            }
            if (scriptDir == null || !scriptDir.exists() || !scriptDir.isDirectory()) {
                 Log.e(TAG, "Script directory not found or is not a directory: " + scriptDirectoryUriString);
                return "Error: Script directory not accessible: " + scriptDirectoryUriString;
            }
        }

        DocumentFile logFile = scriptDir.findFile("execution.log");
        if (logFile == null || !logFile.exists() || !logFile.isFile()) {
            Log.w(TAG, "execution.log not found in " + scriptDirectoryUriString);
            return "No logs found for this script.";
        }

        if (!logFile.canRead()) {
            Log.e(TAG, "Cannot read log file: " + logFile.getUri());
            return "Error: Cannot read log file.";
        }

        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(logFile.getUri());
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Log file not found during read: " + logFile.getUri(), e);
            return "Error: Log file disappeared during read.";
        } catch (IOException e) {
            Log.e(TAG, "Error reading log file content: " + logFile.getUri(), e);
            return "Error: Could not read log file content.";
        }
        return stringBuilder.toString();
    }

    public static String renameScript(Context context, String scriptDirectoryUriString, String oldScriptFileNameWithExtension, String newScriptNameWithoutExtension) {
        if (scriptDirectoryUriString == null || oldScriptFileNameWithExtension == null || newScriptNameWithoutExtension == null ||
            scriptDirectoryUriString.isEmpty() || oldScriptFileNameWithExtension.isEmpty() || newScriptNameWithoutExtension.isEmpty()) {
            Log.e(TAG, "Invalid parameters for renaming script.");
            return null;
        }

        Uri scriptDirUri = Uri.parse(scriptDirectoryUriString);
        DocumentFile scriptDir = DocumentFile.fromTreeUri(context, scriptDirUri);

        if (scriptDir == null || !scriptDir.exists() || !scriptDir.isDirectory()) {
             // Try fromSingleUri if fromTreeUri fails or if the URI is not a tree URI
            scriptDir = DocumentFile.fromSingleUri(context, scriptDirUri);
            if (scriptDir == null || !scriptDir.exists() || !scriptDir.isDirectory()) {
                Log.e(TAG, "Script directory not found or is not a directory: " + scriptDirectoryUriString);
                return null;
            }
        }

        if (!scriptDir.canWrite()) {
            Log.e(TAG, "Cannot write to script directory (permissions issue?): " + scriptDirectoryUriString);
            return null;
        }
        
        // Determine extension
        String extension = "";
        int dotIndex = oldScriptFileNameWithExtension.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < oldScriptFileNameWithExtension.length() - 1) {
            extension = oldScriptFileNameWithExtension.substring(dotIndex);
        } else {
            Log.e(TAG, "Could not determine extension for old script file: " + oldScriptFileNameWithExtension);
            return null; // Cannot proceed without extension
        }

        // Step 1: Rename the directory
        DocumentFile newScriptDir = scriptDir.renameTo(newScriptNameWithoutExtension);
        if (newScriptDir == null || !newScriptDir.exists()) {
            Log.e(TAG, "Failed to rename directory from " + scriptDir.getName() + " to " + newScriptNameWithoutExtension);
            return null;
        }
        Log.i(TAG, "Directory renamed successfully to: " + newScriptDir.getUri());

        // Step 2: Rename the script file within the new directory
        DocumentFile oldScriptFileInNewDir = newScriptDir.findFile(oldScriptFileNameWithExtension);
        if (oldScriptFileInNewDir == null || !oldScriptFileInNewDir.exists()) {
            Log.e(TAG, "Old script file " + oldScriptFileNameWithExtension + " not found in newly renamed directory " + newScriptDir.getName());
            // Attempt to rename directory back as a cleanup, though this might also fail
            if (!newScriptDir.renameTo(scriptDir.getName())) { // scriptDir.getName() is the old name
                 Log.w(TAG, "Critical error: Failed to find old script file in new directory AND failed to rename directory back.");
            }
            return null;
        }

        String newScriptFileNameWithExtension = newScriptNameWithoutExtension + extension;
        if (oldScriptFileInNewDir.renameTo(newScriptFileNameWithExtension)) {
            Log.i(TAG, "Script file renamed successfully to: " + newScriptFileNameWithExtension);
            return newScriptDir.getUri().toString(); // Success
        } else {
            Log.e(TAG, "Failed to rename script file from " + oldScriptFileNameWithExtension + " to " + newScriptFileNameWithExtension + " in dir " + newScriptDir.getName());
            // Attempt to rename directory back
             if (!newScriptDir.renameTo(scriptDir.getName())) {
                 Log.w(TAG, "Critical error: Failed to rename script file AND failed to rename directory back.");
             }
            return null;
        }
    }
}