package com.bytesmith.scriptler;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.chaquo.python.PyException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ScriptExecutor {
    private static final String TAG = "ScriptExecutor";

    public static void executePythonScript(Context context, String scriptPath, String[] args) {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context));
        }

        Python python = Python.getInstance();
        try {
            String scriptContent = readFile(scriptPath);
            python.getModule("sys").callAttr("path").add(context.getFilesDir().getPath());
            python.getModule("sys").callAttr("path").add(new File(scriptPath).getParent());

            // Add any required Python modules
            // Example: addPythonModule(context, "requests");

            python.getModule("builtins").callAttr("exec", scriptContent);

            // Log successful execution
            Log.d(TAG, "Python script executed successfully: " + scriptPath);
            // Implement logging and notifications here
        } catch (PyException e) {
            Log.e(TAG, "Python script error: " + e.getMessage());
            // Show error notification
            showNotification(context, "Script Error", "Python script failed: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "Error reading script file: " + e.getMessage());
            showNotification(context, "File Error", "Could not read script file: " + e.getMessage());
        }
    }

    public static void executeJavaScriptScript(Context context, String scriptPath, String[] args) {
        try {
            javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = manager.getEngineByName("JavaScript");
            String scriptContent = readFile(scriptPath);
            engine.eval(scriptContent);

            // Log successful execution
            Log.d(TAG, "JavaScript script executed successfully: " + scriptPath);
            // Implement logging and notifications here
        } catch (Exception e) {
            Log.e(TAG, "JavaScript script error: " + e.getMessage());
            // Show error notification
            showNotification(context, "Script Error", "JavaScript script failed: " + e.getMessage());
        }
    }

    private static String readFile(String path) throws IOException {
        return new String(Files.readAllBytes(new File(path).toPath()));
    }

    private static void addPythonModule(Context context, String moduleName) {
        try {
            Python python = Python.getInstance();
            python.getPackageIndex().ensureModule(moduleName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add Python module: " + moduleName + " - " + e.getMessage());
        }
    }

    private static void showNotification(Context context, String title, String message) {
        // Implement notification logic here
        Toast.makeText(context, title + ": " + message, Toast.LENGTH_LONG).show();
    }
} 