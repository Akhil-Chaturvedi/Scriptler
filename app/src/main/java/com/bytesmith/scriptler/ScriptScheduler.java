package com.bytesmith.scriptler;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.bytesmith.scriptler.workers.ScriptWorker;

import java.util.concurrent.TimeUnit;

public class ScriptScheduler {
    private static final String TAG = "ScriptScheduler";

    public static void scheduleScript(Context context, String scriptPath, long intervalMinutes, boolean isPeriodic) {
        if (intervalMinutes <= 0) {
            Log.e(TAG, "Invalid interval: " + intervalMinutes);
            return;
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        Data inputData = new Data.Builder()
                .putString("scriptPath", scriptPath)
                .build();

        if (isPeriodic) {
            PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(
                    ScriptWorker.class,
                    intervalMinutes,
                    TimeUnit.MINUTES
            )
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .build();

            WorkManager.getInstance(context).enqueue(periodicWorkRequest);
        } else {
            OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(ScriptWorker.class)
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .build();

            WorkManager.getInstance(context).enqueue(oneTimeWorkRequest);
        }
    }

    public static void scheduleScriptAtFixedTime(Context context, String scriptPath, long startTime) {
        long delay = startTime - System.currentTimeMillis();
        if (delay <= 0) {
            Log.e(TAG, "Invalid start time: " + startTime);
            return;
        }

        OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(ScriptWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context).enqueue(oneTimeWorkRequest);
    }

    public static void cancelAllScripts(Context context) {
        WorkManager.getInstance(context).cancelAllWork();
    }

    public static void cancelScheduledScript(Context context, String scriptPath) {
        // Implement script cancellation logic
        WorkManager.getInstance(context).cancelAllWorkByTag(scriptPath);
    }
} 