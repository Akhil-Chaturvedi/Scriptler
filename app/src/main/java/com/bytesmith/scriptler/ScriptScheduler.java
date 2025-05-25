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
                    .addTag(scriptPath) // Added tag
                    .build();

            WorkManager.getInstance(context).enqueue(periodicWorkRequest);
        } else {
            OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(ScriptWorker.class)
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .addTag(scriptPath) // Added tag
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
                .setInputData(new Data.Builder().putString("scriptPath", scriptPath).build()) // Ensure input data is set
                .addTag(scriptPath) // Added tag
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

    public static void scheduleOneTimeSpecific(Context context, String scriptPath, int year, int month, int day, int hour, int minute) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(year, month, day, hour, minute, 0);
        long startTime = calendar.getTimeInMillis();
        long delay = startTime - System.currentTimeMillis();

        if (delay <= 0) {
            Log.e(TAG, "Invalid start time (in the past): " + startTime);
            // Optionally, show a Toast or provide feedback to the user
            return;
        }

        Data inputData = new Data.Builder().putString("scriptPath", scriptPath).build();
        OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(ScriptWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(scriptPath)
                .build();

        WorkManager.getInstance(context).enqueue(oneTimeWorkRequest);
        Log.i(TAG, "Scheduled one-time specific for " + scriptPath + " at " + calendar.getTime().toString());
    }


    public static void scheduleAlternateDays(Context context, String scriptPath, int hour, int minute) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar nextRunTime = java.util.Calendar.getInstance();
        nextRunTime.set(java.util.Calendar.HOUR_OF_DAY, hour);
        nextRunTime.set(java.util.Calendar.MINUTE, minute);
        nextRunTime.set(java.util.Calendar.SECOND, 0);
        nextRunTime.set(java.util.Calendar.MILLISECOND, 0);

        if (now.after(nextRunTime)) { // If current time is past today's schedule time
            nextRunTime.add(java.util.Calendar.DAY_OF_YEAR, 1); // Schedule for tomorrow
        }
        // If we want strictly "alternate" from *now*, and today's time has passed, it should be day after tomorrow.
        // However, the prompt says "if it's before, then tomorrow", which means next available slot.
        // The current logic schedules for the next possible time slot (today or tomorrow).
        // To ensure it's *at least* tomorrow for the first run of an "alternate days" cycle if today's time has passed:
        // if (now.get(java.util.Calendar.HOUR_OF_DAY) > hour || 
        //    (now.get(java.util.Calendar.HOUR_OF_DAY) == hour && now.get(java.util.Calendar.MINUTE) >= minute)) {
        //     nextRunTime.add(java.util.Calendar.DAY_OF_YEAR, 1); 
        // }
        // The simpler logic above is fine per simplification notes.

        long initialDelay = nextRunTime.getTimeInMillis() - now.getTimeInMillis();
        if (initialDelay < 0) { // Should not happen if logic above is correct
            initialDelay = 0;
        }
        
        Data inputData = new Data.Builder().putString("scriptPath", scriptPath).build();
        PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(
                ScriptWorker.class,
                2, TimeUnit.DAYS) // 48 hours
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(scriptPath)
                .build();
        
        WorkManager.getInstance(context).enqueue(periodicWorkRequest);
        Log.i(TAG, "Scheduled alternate days for " + scriptPath + ", first run at " + nextRunTime.getTime().toString());
    }

    public static void scheduleEveryNDays(Context context, String scriptPath, int nDays, int hour, int minute) {
        if (nDays <= 0) {
            Log.e(TAG, "Invalid N days: " + nDays);
            return;
        }
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar nextRunTime = java.util.Calendar.getInstance();
        nextRunTime.set(java.util.Calendar.HOUR_OF_DAY, hour);
        nextRunTime.set(java.util.Calendar.MINUTE, minute);
        nextRunTime.set(java.util.Calendar.SECOND, 0);
        nextRunTime.set(java.util.Calendar.MILLISECOND, 0);

        if (now.after(nextRunTime)) { // If current time is past today's schedule time
            nextRunTime.add(java.util.Calendar.DAY_OF_YEAR, 1); // Schedule for tomorrow
        }
        // Similar to alternate days, this schedules for the next available slot (today or tomorrow).

        long initialDelay = nextRunTime.getTimeInMillis() - now.getTimeInMillis();
         if (initialDelay < 0) { 
            initialDelay = 0;
        }

        Data inputData = new Data.Builder().putString("scriptPath", scriptPath).build();
        PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(
                ScriptWorker.class,
                nDays, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(scriptPath)
                .build();
        
        WorkManager.getInstance(context).enqueue(periodicWorkRequest);
        Log.i(TAG, "Scheduled every " + nDays + " days for " + scriptPath + ", first run at " + nextRunTime.getTime().toString());
    }
}