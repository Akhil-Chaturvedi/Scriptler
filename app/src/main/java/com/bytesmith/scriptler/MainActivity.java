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
import androidx.documentfile.provider.DocumentFile;
import android.widget.EditText; // For create script dialog

import java.io.File; // Keep for now for parts of createScriptDialog, but ideally remove
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int SCRIPT_EDITOR_REQUEST_CODE = 101; // For starting ScriptEditorActivity
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

        // Load selected storage URI early
        StorageHelper.loadSelectedTreeUri(this);

        scriptsListView = findViewById(R.id.scriptsListView);
        createScriptButton = findViewById(R.id.createScriptButton);

        // Initialize the script adapter
        scriptAdapter = new ScriptAdapter(this, scripts); // Reverted constructor
        scriptsListView.setAdapter(scriptAdapter);

        // Load scripts from storage
        // And check storage access
        if (!StorageHelper.hasStorageAccess(this)) {
            Toast.makeText(this, "Please select a scripts directory via the menu.", Toast.LENGTH_LONG).show();
            createScriptButton.setEnabled(false);
        } else {
            createScriptButton.setEnabled(true);
            loadScripts();
        }
        
        createScriptButton.setOnClickListener(v -> {
            if (!StorageHelper.hasStorageAccess(this)) {
                Toast.makeText(this, "Please select a scripts directory first (Menu > Storage).", Toast.LENGTH_LONG).show();
                return;
            }
            showCreateScriptDialog();
        });

        // Handle item clicks for editing (if ScriptAdapter doesn't handle it all)
        scriptsListView.setOnItemClickListener((parent, view, position, id) -> {
            Script script = scripts.get(position);
            editScript(script);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh script list and storage access check on resume
        if (!StorageHelper.hasStorageAccess(this)) {
            Toast.makeText(this, "Please select a scripts directory via the menu.", Toast.LENGTH_LONG).show();
            createScriptButton.setEnabled(false);
            scripts.clear(); // Clear existing scripts if no storage access
            scriptAdapter.notifyDataSetChanged();
        } else {
            createScriptButton.setEnabled(true);
            loadScripts(); // Reload scripts in case of external changes or returning from editor
        }
    }

    private void loadScripts() {
        if (!StorageHelper.hasStorageAccess(this)) {
            // Toast.makeText(this, "Cannot load scripts: No storage directory selected.", Toast.LENGTH_SHORT).show();
            return;
        }
        scripts.clear();
        List<DocumentFile> scriptFiles = StorageHelper.listScriptFiles(this);
        if (scriptFiles.isEmpty()) {
            Toast.makeText(this, "No scripts found in the selected directory.", Toast.LENGTH_SHORT).show();
        } else {
            for (DocumentFile file : scriptFiles) {
                String fileName = file.getName();
                if (fileName != null) {
                    String scriptName = fileName;
                    String language = "Unknown"; // Default
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
                        scriptName = fileName.substring(0, dotIndex);
                        String extension = fileName.substring(dotIndex).toLowerCase();
                        if (extension.equals(".py")) {
                            language = "Python";
                        } else if (extension.equals(".js")) {
                            language = "JavaScript";
                        }
                    }
                    scripts.add(new Script(scriptName, language, file.getUri().toString()));
                }
            }
        }
        scriptAdapter.notifyDataSetChanged();
    }

import android.widget.ArrayAdapter; // For Spinner
import android.widget.Spinner;     // For Spinner
import androidx.appcompat.widget.PopupMenu; // For script options

    private void showCreateScriptDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_script, null);
        EditText scriptNameEditText = dialogView.findViewById(R.id.scriptNameEditText); 
        Spinner languageSpinner = dialogView.findViewById(R.id.languageSpinner);

        // Setup Spinner
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.script_languages, android.R.layout.simple_spinner_item); // Assuming R.array.script_languages exists
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(spinnerAdapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(dialogView);
        builder.setTitle("Create New Script");

        builder.setPositiveButton("Create", (dialog, which) -> {
            String scriptNameInput = scriptNameEditText.getText().toString().trim();
            if (scriptNameInput.isEmpty()) {
                Toast.makeText(MainActivity.this, "Script name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!scriptNameInput.matches("[a-zA-Z0-9_]+")) {
                 Toast.makeText(MainActivity.this, "Script name can only contain letters, numbers, and underscores.", Toast.LENGTH_LONG).show();
                return;
            }

            String selectedLanguage = languageSpinner.getSelectedItem().toString();
            String language;
            String extension;

            if ("Python".equalsIgnoreCase(selectedLanguage)) {
                language = "Python";
                extension = ".py";
            } else if ("JavaScript".equalsIgnoreCase(selectedLanguage)) {
                language = "JavaScript";
                extension = ".js";
            } else {
                Toast.makeText(MainActivity.this, "Invalid language selected", Toast.LENGTH_SHORT).show();
                return;
            }

            String fileNameWithExtension = scriptNameInput + extension;

            DocumentFile newScriptFile = StorageHelper.createScriptFile(MainActivity.this, fileNameWithExtension);
            if (newScriptFile != null && newScriptFile.exists()) {
                Toast.makeText(MainActivity.this, "Script created: " + newScriptFile.getName(), Toast.LENGTH_LONG).show();
                scripts.add(new Script(scriptNameInput, language, newScriptFile.getUri().toString()));
                scriptAdapter.notifyDataSetChanged();
                // Optionally, open the new script in ScriptEditorActivity
                // editScript(new Script(scriptNameInput, language, newScriptFile.getUri().toString()));
            } else {
                Toast.makeText(MainActivity.this, "Error creating script. Check logs or storage permissions.", Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    public void editScript(Script script) { // Made public to be called from adapter if needed, or keep private if only from options menu
        Intent intent = new Intent(this, ScriptEditorActivity.class);
        intent.putExtra("scriptUri", script.getPath()); // path is the URI string
        intent.putExtra("scriptName", script.getName());
        startActivityForResult(intent, SCRIPT_EDITOR_REQUEST_CODE);
    }

    private void deleteScript(Script script) {
        DocumentFile scriptFile = StorageHelper.findFile(this, script.getName() + (script.getLanguage().equals("Python") ? ".py" : ".js")); // Reconstruct filename
        if (scriptFile == null) {
             scriptFile = DocumentFile.fromSingleUri(this, Uri.parse(script.getPath()));
        }


        if (scriptFile != null && scriptFile.exists()) {
            new AlertDialog.Builder(this)
                .setTitle("Delete Script")
                .setMessage("Are you sure you want to delete '" + script.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (StorageHelper.deleteScriptFile(scriptFile)) {
                        Toast.makeText(MainActivity.this, "Script '" + script.getName() + "' deleted.", Toast.LENGTH_SHORT).show();
                        scripts.remove(script);
                        scriptAdapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(MainActivity.this, "Failed to delete script '" + script.getName() + "'.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            Toast.makeText(this, "Script file not found for deletion: " + script.getPath(), Toast.LENGTH_LONG).show();
             // Fallback: try removing from list if file is already gone
            if (!scripts.remove(script)) {
                // If it wasn't in the list, maybe it was already deleted.
                // To be safe, reload all scripts.
                loadScripts();
            } else {
                scriptAdapter.notifyDataSetChanged();
            }
        }
    }


    public void runScript(Script script) {
        if (script == null) return;

        // Implement script execution
        String scriptPath = script.getPath();
        if (scriptPath == null || scriptPath.isEmpty()) {
            Toast.makeText(this, "Invalid script path", Toast.LENGTH_LONG).show();
            return;
        }

        // Instantiate ScriptExecutor to call its non-static methods
        ScriptExecutor executor = new ScriptExecutor(this, false); // Specify false for foreground execution
        if ("Python".equals(script.getLanguage())) {
            executor.executePythonScript(this, scriptPath, null);
        } else if ("JavaScript".equals(script.getLanguage())) {
            executor.executeJavaScriptScript(this, scriptPath, null);
        }

        // Update last run time
        script.setLastRunTime("Just now"); // Replace with actual timestamp
        scriptAdapter.notifyDataSetChanged();
    }

    public void showScriptOptions(Script script, View anchorView) { // Added anchorView parameter
        if (script == null || anchorView == null) return;

        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.script_options_menu, popup.getMenu()); // Assuming R.menu.script_options_menu exists

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_edit_script) { // Assuming R.id.action_edit_script
                editScript(script);
                return true;
            } else if (itemId == R.id.action_delete_script) { // Assuming R.id.action_delete_script
                // Confirmation is already handled in deleteScript method
                deleteScript(script);
                return true;
            }
            return false;
        });
        popup.show();
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
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                StorageHelper.setSelectedTreeUri(this, treeUri);
                // Persisted permissions are now handled by setSelectedTreeUri
                createScriptButton.setEnabled(true); // Enable button after getting permission
                loadScripts(); // Reload scripts from the newly selected directory
                Toast.makeText(this, "Storage directory selected.", Toast.LENGTH_SHORT).show();
            } else {
                 Toast.makeText(this, "Failed to get storage directory URI.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == SCRIPT_EDITOR_REQUEST_CODE) {
            // No specific result needed from ScriptEditorActivity for now,
            // but onResume will call loadScripts() to refresh if anything changed.
            // If ScriptEditorActivity could rename files, we'd need a more specific result.
        }
    }
} 