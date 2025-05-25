package com.bytesmith.scriptler;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;
import android.net.Uri;

public class ScriptConsoleActivity extends AppCompatActivity {

    private static final String TAG = "ScriptConsoleActivity";

    private Toolbar toolbar;
    private TextView consoleOutputTextView;
    private TextView nextRunTextView;
    private TextView timeLeftTextView;

    private String scriptName;
    private String scriptDirectoryUriString;
    private String scriptFileUriString; // Added for WorkManager tag

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_console);

        toolbar = findViewById(R.id.console_toolbar);
        setSupportActionBar(toolbar);

        consoleOutputTextView = findViewById(R.id.consoleOutputTextView);
        nextRunTextView = findViewById(R.id.nextRunTextView);
        timeLeftTextView = findViewById(R.id.timeLeftTextView);

        scriptName = getIntent().getStringExtra("SCRIPT_NAME");
        scriptDirectoryUriString = getIntent().getStringExtra("SCRIPT_DIRECTORY_URI");
        scriptFileUriString = getIntent().getStringExtra("SCRIPT_FILE_URI"); // Added

        if (scriptName == null || scriptDirectoryUriString == null || scriptFileUriString == null) {
            Toast.makeText(this, "Error: Script details not provided.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Script name, directory URI, or file URI is null. Name: " + scriptName + 
                                 ", Dir URI: " + scriptDirectoryUriString + 
                                 ", File URI: " + scriptFileUriString);
            finish();
            return;
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Console: " + scriptName);
        }

        loadAndDisplayLogs();
        loadAndDisplayScheduleInfo();
    }

    private void loadAndDisplayScheduleInfo() {
        if (scriptFileUriString == null) {
            nextRunTextView.setText("Next run: N/A (Error)");
            timeLeftTextView.setText("Time left: N/A (Error)");
            return;
        }

        androidx.work.WorkManager workManager = androidx.work.WorkManager.getInstance(getApplicationContext());
        androidx.lifecycle.LiveData<java.util.List<androidx.work.WorkInfo>> workInfosLiveData = 
            workManager.getWorkInfosByTagLiveData(scriptFileUriString);

        workInfosLiveData.observe(this, workInfos -> {
            if (workInfos == null || workInfos.isEmpty()) {
                nextRunTextView.setText("Next run: N/A");
                timeLeftTextView.setText("Time left: N/A");
                return;
            }

            androidx.work.WorkInfo relevantWorkInfo = null;
            for (androidx.work.WorkInfo workInfo : workInfos) {
                // Prioritize ENQUEUED or RUNNING states
                if (workInfo.getState() == androidx.work.WorkInfo.State.ENQUEUED || 
                    workInfo.getState() == androidx.work.WorkInfo.State.RUNNING) {
                    relevantWorkInfo = workInfo;
                    break;
                }
            }
            
            // If no enqueued or running, pick the first one to see if it's a completed periodic task or other states.
            if (relevantWorkInfo == null && !workInfos.isEmpty()) {
                relevantWorkInfo = workInfos.get(0); 
            }

            if (relevantWorkInfo != null) {
                if (relevantWorkInfo.getState() == androidx.work.WorkInfo.State.ENQUEUED) {
                    // For ENQUEUED, it's hard to get exact next run time for periodic work without more complex queries.
                    // Workaround: Check if progress data contains next run time (if ScriptWorker sets it)
                    // For now, just show "Scheduled".
                    // A better approach would be to query WorkSpec for initialDelay and periodStartTime if available.
                    nextRunTextView.setText("Next run: Scheduled");
                    timeLeftTextView.setText("Time left: Pending calculation"); 
                } else if (relevantWorkInfo.getState() == androidx.work.WorkInfo.State.RUNNING) {
                    nextRunTextView.setText("Next run: Running now");
                    timeLeftTextView.setText("Time left: N/A");
                } else if (relevantWorkInfo.getState() == androidx.work.WorkInfo.State.SUCCEEDED ||
                           relevantWorkInfo.getState() == androidx.work.WorkInfo.State.FAILED ||
                           relevantWorkInfo.getState() == androidx.work.WorkInfo.State.CANCELLED ||
                           relevantWorkInfo.getState() == androidx.work.WorkInfo.State.BLOCKED) {
                    // If it's succeeded/failed/cancelled, it's likely not scheduled for a future run unless it's periodic
                    // and this is an old instance. WorkManager might keep old WorkInfo for periodic tasks.
                    // We need to be careful not to misinterpret an old completed run as "Not scheduled" if a new one is ENQUEUED.
                    // The loop above prioritizes ENQUEUED/RUNNING, so this should be okay for terminal states of non-repeating work.
                    nextRunTextView.setText("Next run: Not scheduled");
                    timeLeftTextView.setText("Time left: N/A");
                } else {
                    nextRunTextView.setText("Next run: N/A (State: " + relevantWorkInfo.getState() + ")");
                    timeLeftTextView.setText("Time left: N/A");
                }
            } else {
                nextRunTextView.setText("Next run: N/A");
                timeLeftTextView.setText("Time left: N/A");
            }
        });
    }

    private void loadAndDisplayLogs() {
        if (scriptDirectoryUriString == null) {
            consoleOutputTextView.setText("Error: Script directory URI not available.");
            return;
        }
        String logContent = StorageHelper.readLogFile(this, scriptDirectoryUriString);
        if (logContent != null && !logContent.isEmpty()) {
            consoleOutputTextView.setText(logContent);
        } else {
            consoleOutputTextView.setText("No logs available or an error occurred while reading logs.");
        }
    }
}
