package com.bytesmith.scriptler;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.bytesmith.scriptler.R;
import com.bytesmith.scriptler.ScriptAdapter;
import com.bytesmith.scriptler.StorageHelper;
import com.bytesmith.scriptler.Script;
import com.bytesmith.scriptler.ScriptExecutor;
import com.bytesmith.scriptler.ScriptScheduler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ListView scriptsListView;
    private Button createScriptButton;
    private ScriptAdapter scriptAdapter;
    private List<Script> scripts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle("Scriptler");
        }

        scriptsListView = findViewById(R.id.scriptsListView);
        createScriptButton = findViewById(R.id.createScriptButton);

        // Initialize the storage helper
        StorageHelper.openStoragePicker(this);

        // Initialize the script adapter
        scriptAdapter = new ScriptAdapter(this, scripts);
        scriptsListView.setAdapter(scriptAdapter);

        // Load scripts from storage
        loadScripts();

        createScriptButton.setOnClickListener(v -> {
            // Show create script dialog
            showCreateScriptDialog();
        });
    }

    private void loadScripts() {
        // Implement script loading from storage
        // For now, adding a sample script
        scripts.add(new Script("SampleScript", "Python", "/storage/emulated/0/Documents/Scriptler/SampleScript/SampleScript.py"));
        scriptAdapter.notifyDataSetChanged();
    }

    private void showCreateScriptDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_script, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.show();

        Button createButton = dialogView.findViewById(R.id.createButton);
        createButton.setOnClickListener(v -> {
            // Get input values
            // Implement script creation logic
            String scriptName = "NewScript";
            String language = "Python";

            // Create new script directory
            String scriptsDir = StorageHelper.getScriptsDirectory(MainActivity.this);
            File scriptDir = new File(scriptsDir, scriptName);
            if (!scriptDir.exists()) {
                scriptDir.mkdirs();
            }

            // Create main script file
            File scriptFile = new File(scriptDir, scriptName + (language.equals("Python") ? ".py" : ".js"));
            try {
                if (!scriptFile.exists()) {
                    scriptFile.createNewFile();
                }

                // Add script to list
                scripts.add(new Script(scriptName, language, scriptFile.getAbsolutePath()));
                scriptAdapter.notifyDataSetChanged();

                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Error creating script: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    public void runScript(Script script) {
        if (script == null) return;

        // Implement script execution
        String scriptPath = script.getPath();
        if (scriptPath == null || scriptPath.isEmpty()) {
            Toast.makeText(this, "Invalid script path", Toast.LENGTH_LONG).show();
            return;
        }

        if ("Python".equals(script.getLanguage())) {
            ScriptExecutor.executePythonScript(this, scriptPath, null);
        } else if ("JavaScript".equals(script.getLanguage())) {
            ScriptExecutor.executeJavaScriptScript(this, scriptPath, null);
        }

        // Update last run time
        script.setLastRunTime("Just now"); // Replace with actual timestamp
        scriptAdapter.notifyDataSetChanged();
    }

    public void showScriptOptions(Script script) {
        if (script == null) return;

        // Implement script options menu
        // For now, just show a toast
        Toast.makeText(this, "Script options: " + script.getName(), Toast.LENGTH_LONG).show();
    }

    private void scheduleScript(Script script, boolean isPeriodic) {
        if (script == null) return;

        String scriptPath = script.getPath();
        if (scriptPath == null || scriptPath.isEmpty()) {
            Toast.makeText(this, "Invalid script path", Toast.LENGTH_LONG).show();
            return;
        }

        // Schedule the script using WorkManager
        ScriptScheduler.scheduleScript(this, scriptPath, 15, isPeriodic); // 15-minute interval

        // Update next run time
        script.setNextRunTime("In 15 minutes"); // Replace with actual calculated time
        scriptAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                // Open settings
                return true;
            case R.id.action_storage:
                // Open storage picker
                StorageHelper.openStoragePicker(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            Uri treeUri = data.getData();
            StorageHelper.setSelectedUri(treeUri);
            // Grant storage access
            grantUriPermission(getPackageName(), treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            // Load scripts again
            loadScripts();
        }
    }
} 