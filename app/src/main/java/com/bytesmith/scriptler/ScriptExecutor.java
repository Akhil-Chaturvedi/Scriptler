package com.bytesmith.scriptler;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.chaquo.python.PyException;

import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;
import com.google.android.play.core.splitinstall.SplitInstallRequest;
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;
// Required for tasks if using them for listeners
import com.google.android.play.core.tasks.OnSuccessListener;
import com.google.android.play.core.tasks.OnFailureListener;

// Imports for Notifications
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.Manifest;
import android.content.pm.PackageManager;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.atomic.AtomicInteger;


public class ScriptExecutor {
    private static final String TAG = "ScriptExecutor";
    private static final String NOTIFICATION_CHANNEL_ID = "ScriptlerChannel";
    private static AtomicInteger notificationIdCounter = new AtomicInteger(0); // For unique notification IDs

    private SplitInstallManager splitInstallManager;
    private PendingScriptExecution pendingScript;
    private boolean isBackgroundExecution = false;

    private static class PendingScriptExecution {
        String scriptPath;
        String scriptContent;
        String[] args;
        Context context;
        // boolean isBackground; // This was in the diff, but isBackgroundExecution of ScriptExecutor instance is used

        PendingScriptExecution(Context context, String scriptPath, String scriptContent, String[] args /*, boolean isBackground (removed) */) {
            this.context = context;
            this.scriptPath = scriptPath;
            this.scriptContent = scriptContent;
            this.args = args;
            // this.isBackground = isBackground; // Removed
        }
    }

    public ScriptExecutor(Context context, boolean isBackground) {
        this.splitInstallManager = SplitInstallManagerFactory.create(context.getApplicationContext());
        this.isBackgroundExecution = isBackground;
        createNotificationChannel(context.getApplicationContext()); // Create channel on instantiation
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Scriptler Notifications";
            String description = "Notifications for Scriptler script executions and module downloads";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void showCustomNotification(Context context, String title, String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot show notification.");
                // Optionally, inform the user via a Toast if it's a foreground operation context
                if (!this.isBackgroundExecution) { // Or check context type if more granular control needed
                    Toast.makeText(context, "Notification permission needed to show script status.", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Placeholder icon
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(notificationIdCounter.getAndIncrement(), builder.build());
    }


    private Set<String> extractImportedModules(String scriptContent) {
        Set<String> modules = new HashSet<>();
        Pattern pattern = Pattern.compile(
            "^\s*(?:import\s+([\w][\w.]*)|from\s+([\w][\w.]*)\s+import)", 
            Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(scriptContent);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                modules.add(matcher.group(1).split("\\.")[0]);
            } else if (matcher.group(2) != null) {
                modules.add(matcher.group(2).split("\\.")[0]);
            }
        }
        return modules;
    }

    public void executePythonScript(Context context, String scriptPath, String[] args) {
        String scriptContent;
        try {
            scriptContent = readFile(scriptPath);
        } catch (IOException e) {
            Log.e(TAG, "Error reading script file: " + e.getMessage());
            showNotification(context, "File Error", "Could not read script file: " + e.getMessage());
            return;
        }

        Set<String> requiredScriptModules = extractImportedModules(scriptContent);
        if (requiredScriptModules.isEmpty()) {
            Log.d(TAG, "No specific Python modules detected for dynamic download. Proceeding with execution.");
            actuallyExecutePythonScript(context, scriptPath, scriptContent, args);
            return;
        }

        Log.d(TAG, "Script requires modules: " + requiredScriptModules);

        Set<String> installedModules = splitInstallManager.getInstalledModules();
        Log.d(TAG, "Currently installed feature modules: " + installedModules);

        List<String> modulesToInstall = new ArrayList<>();
        for (String scriptModule : requiredScriptModules) {
            // Convention: Python module 'requests' -> DFM 'feature_requests'
            // Python module 'bs4' (BeautifulSoup) -> DFM 'feature_bs4' (example)
            // Python module 'schedule' -> DFM 'feature_schedule' (example)
            // This mapping needs to be robust or defined elsewhere.
            String featureModuleName = "feature_" + scriptModule.toLowerCase(); 
            if (!installedModules.contains(featureModuleName)) {
                // TODO: Add a check against a predefined list of known DFMs for this app
                // to avoid trying to download arbitrary names.
                // For now, any 'feature_*' can be requested if not installed.
                modulesToInstall.add(featureModuleName);
            }
        }

        if (modulesToInstall.isEmpty()) {
            Log.d(TAG, "All required modules are already installed.");
            actuallyExecutePythonScript(context, scriptPath, scriptContent, args);
            return;
        }

        Log.d(TAG, "Requesting installation for modules: " + modulesToInstall);
        if (!this.isBackgroundExecution) { // Use instance's flag
            Toast.makeText(context, "Downloading required modules: " + modulesToInstall, Toast.LENGTH_LONG).show();
        }

        SplitInstallRequest.Builder requestBuilder = SplitInstallRequest.newBuilder();
        for (String moduleName : modulesToInstall) {
            requestBuilder.addModule(moduleName);
        }
        SplitInstallRequest request = requestBuilder.build();

        this.pendingScript = new PendingScriptExecution(context, scriptPath, scriptContent, args /*, this.isBackgroundExecution (removed) */);

        splitInstallManager.registerListener(listener);
        splitInstallManager.startInstall(request)
            .addOnSuccessListener(sessionId -> Log.d(TAG, "Module installation session started with ID: " + sessionId))
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to start module installation: " + e.getMessage(), e);
                showToastOrNotificationForError(context, "Installation Failed", "Could not start download: " + e.getMessage(), this.isBackgroundExecution);
                if (this.pendingScript != null) { 
                     this.pendingScript = null;
                }
            });
    }

    private SplitInstallStateUpdatedListener listener = state -> {
        if (pendingScript == null || state.sessionId() == 0) {
            return;
        }
        
        Log.d(TAG, "SplitInstallState: status=" + state.status() + ", error=" + state.errorCode() + ", modules=" + state.moduleNames());
        String scriptNameForNotification = new File(pendingScript.scriptPath).getName();

        if (state.status() == SplitInstallSessionStatus.INSTALLED) {
            Log.d(TAG, "Modules installed successfully: " + state.moduleNames() + ". Restarting Python interpreter.");
            if (this.isBackgroundExecution) { // Check instance's flag
                showCustomNotification(pendingScript.context, "Modules Installed", "Modules for " + scriptNameForNotification + " installed.");
            } else {
                Toast.makeText(pendingScript.context, "Modules installed. Initializing...", Toast.LENGTH_SHORT).show();
            }

            if (Python.isStarted()) {
                Python.stop();
            }
            Python.start(new AndroidPlatform(pendingScript.context.getApplicationContext()));
            Log.d(TAG, "Python interpreter restarted.");

            actuallyExecutePythonScript(pendingScript.context, pendingScript.scriptPath, pendingScript.scriptContent, pendingScript.args);
            this.pendingScript = null;
            splitInstallManager.unregisterListener(this.listener);
        } else if (state.status() == SplitInstallSessionStatus.FAILED) {
            Log.e(TAG, "Module installation failed. Error code: " + state.errorCode() + " for modules " + state.moduleNames());
            showToastOrNotificationForError(pendingScript.context, "Installation Failed", "Error " + state.errorCode() + " for " + state.moduleNames(), this.isBackgroundExecution);
            this.pendingScript = null;
            splitInstallManager.unregisterListener(this.listener);
        } else if (state.status() == SplitInstallSessionStatus.CANCELED) {
            Log.d(TAG, "Module installation canceled for modules " + state.moduleNames());
            showToastOrNotificationForError(pendingScript.context, "Installation Canceled", "Download for " + state.moduleNames() + " was canceled.", this.isBackgroundExecution);
            this.pendingScript = null;
            splitInstallManager.unregisterListener(this.listener);
        } else {
            // Other states: PENDING, DOWNLOADING, INSTALLING, REQUIRES_USER_CONFIRMATION, CANCELING
            Log.d(TAG, "Module installation status: " + state.status() + " for " + state.moduleNames());
            // Update progress bar here based on state.bytesDownloaded() and state.totalBytesToDownload()
            if (state.status() == SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION) {
                // For simplicity, not handling this explicitly with startConfirmationDialogForResult.
                // The user might get a notification from Play Store to confirm.
                Log.w(TAG, "Module installation requires user confirmation: " + state.moduleNames());
            }
        }
    };

    private void actuallyExecutePythonScript(Context context, String scriptPath, String scriptContent, String[] args) {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context.getApplicationContext())); // Use application context for Chaquopy
        }
        Python python = Python.getInstance();
        try {
            python.getModule("sys").get("path").callAttr("append", context.getFilesDir().getPath());
            String parentDir = new File(scriptPath).getParent();
            if (parentDir != null) {
                 python.getModule("sys").get("path").callAttr("append", parentDir);
            }
            
            python.getModule("builtins").callAttr("exec", scriptContent);
            Log.d(TAG, "Python script executed successfully: " + scriptPath);
            if (!this.isBackgroundExecution) {
                Toast.makeText(context, "Script executed: " + new File(scriptPath).getName(), Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "Background Mode: Script executed. Showing success notification for " + scriptPath);
                showCustomNotification(context, "Script Executed", "Script " + new File(scriptPath).getName() + " finished successfully.");
            }
        } catch (PyException e) {
            Log.e(TAG, "Python script error: " + e.getMessage(), e);
            showToastOrNotificationForError(context, "Script Error", "Python script failed: " + e.getMessage(), this.isBackgroundExecution);
        }
    }


    public void executeJavaScriptScript(Context context, String scriptPath, String[] args) {
        try {
            javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = manager.getEngineByName("JavaScript");
            String scriptContent = readFile(scriptPath); // Assuming readFile is still appropriate here
            engine.eval(scriptContent);
            Log.d(TAG, "JavaScript script executed successfully: " + scriptPath);
            if (!this.isBackgroundExecution) {
                 Toast.makeText(context, "JS Script executed: " + new File(scriptPath).getName(), Toast.LENGTH_SHORT).show();
            } else {
                showCustomNotification(context, "JS Script Executed", "Script " + new File(scriptPath).getName() + " finished successfully.");
            }
        } catch (Exception e) {
            Log.e(TAG, "JavaScript script error: " + e.getMessage(), e);
            showToastOrNotificationForError(context, "Script Error", "JavaScript script failed: " + e.getMessage(), this.isBackgroundExecution);
        }
    }

    private String readFile(String path) throws IOException {
        // Ensure path is not null or empty
        if (path == null || path.isEmpty()) {
            throw new IOException("Script path is null or empty.");
        }
        File scriptFile = new File(path);
        if (!scriptFile.exists()) {
            throw new IOException("Script file does not exist: " + path);
        }
        if (!scriptFile.canRead()) {
            throw new IOException("Script file cannot be read: " + path);
        }
        return new String(Files.readAllBytes(scriptFile.toPath()));
    }

    // This method might not be needed if dynamic features handle module availability.
    // Keeping it for now if direct Chaquopy module management is ever mixed.
    private void addPythonModule(Context context, String moduleName) {
        try {
            Python python = Python.getInstance();
            python.getPackageIndex().ensureModule(moduleName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add Python module: " + moduleName + " - " + e.getMessage());
        }
    }

    // Renamed to showToastOrNotificationForError and logic updated
    private void showToastOrNotificationForError(Context context, String title, String message, boolean isBackground) {
        if (!isBackground) {
            Toast.makeText(context, title + ": " + message, Toast.LENGTH_LONG).show();
        } else {
            // For background errors, use a notification
            showCustomNotification(context, title, message);
        }
    }
} 