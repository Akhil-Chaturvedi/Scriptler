package com.bytesmith.scriptler;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
// Corrected import for ScriptExecutor
import com.bytesmith.scriptler.ScriptExecutor; 
import java.io.File;

public class ScriptWorker extends Worker {
    private static final String TAG = "ScriptWorker";

    public ScriptWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            String scriptPath = getInputData().getString("scriptPath");
            if (scriptPath == null) {
                Log.e(TAG, "Script path is null");
                return Result.failure();
            }

            File scriptFile = new File(scriptPath);
            if (!scriptFile.exists()) {
                Log.e(TAG, "Script file does not exist: " + scriptPath);
                return Result.failure();
            }

            String scriptExtension = getFileExtension(scriptPath);
            
            // Instantiate ScriptExecutor to call its non-static methods
            ScriptExecutor executor = new ScriptExecutor(getApplicationContext(), true); // Specify true for background execution

            if ("py".equalsIgnoreCase(scriptExtension)) {
                executor.executePythonScript(getApplicationContext(), scriptPath, null);
            } else if ("js".equalsIgnoreCase(scriptExtension)) {
                executor.executeJavaScriptScript(getApplicationContext(), scriptPath, null);
            } else {
                Log.e(TAG, "Unsupported script type: " + scriptExtension);
                return Result.failure();
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Script execution error: " + e.getMessage());
            return Result.failure();
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) return "";
        return fileName.substring(lastDotIndex + 1);
    }
} 