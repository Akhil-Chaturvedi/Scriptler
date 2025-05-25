package com.bytesmith.scriptler;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
// import android.widget.Button; // Replaced with FAB
import com.google.android.material.floatingactionbutton.FloatingActionButton; // Added for FAB
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
    // private Button createScriptButton; // Replaced with FAB
    private FloatingActionButton fabCreateScript; // Added for FAB
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
        // createScriptButton = findViewById(R.id.createScriptButton); // Replaced with FAB
        fabCreateScript = findViewById(R.id.fab_create_script); // Added for FAB

        // Initialize the script adapter
        scriptAdapter = new ScriptAdapter(this, scripts); // Reverted constructor
        scriptsListView.setAdapter(scriptAdapter);

        // Load scripts from storage
        // And check storage access
        if (!StorageHelper.hasStorageAccess(this)) {
            Toast.makeText(this, "Please select a scripts directory via the menu.", Toast.LENGTH_LONG).show();
            // createScriptButton.setEnabled(false); // Replaced with FAB
            fabCreateScript.setEnabled(false); // Added for FAB
        } else {
            // createScriptButton.setEnabled(true); // Replaced with FAB
            fabCreateScript.setEnabled(true); // Added for FAB
            loadScripts();
        }
        
        // createScriptButton.setOnClickListener(v -> { // Replaced with FAB
        fabCreateScript.setOnClickListener(v -> { // Added for FAB
            if (!StorageHelper.hasStorageAccess(this)) {
                Toast.makeText(this, "Please select a scripts directory first (Menu > Storage).", Toast.LENGTH_LONG).show();
                return;
            }
            showCreateScriptDialog();
        });

        // Handle item clicks to open ScriptConsoleActivity
        scriptsListView.setOnItemClickListener((parent, view, position, id) -> {
            Script script = scripts.get(position);
            if (script == null || script.getPath() == null) {
                Toast.makeText(this, "Invalid script data.", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri scriptFileUri = Uri.parse(script.getPath());
            DocumentFile scriptFileDoc = DocumentFile.fromSingleUri(this, scriptFileUri);

            if (scriptFileDoc == null || !scriptFileDoc.exists()) {
                Toast.makeText(this, "Script file not found.", Toast.LENGTH_SHORT).show();
                return;
            }

            DocumentFile scriptDirDoc = scriptFileDoc.getParentFile();
            if (scriptDirDoc == null || !scriptDirDoc.isDirectory()) {
                Toast.makeText(this, "Script directory not found.", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(this, ScriptConsoleActivity.class);
            intent.putExtra("SCRIPT_NAME", script.getName());
            intent.putExtra("SCRIPT_DIRECTORY_URI", scriptDirDoc.getUri().toString());
            intent.putExtra("SCRIPT_FILE_URI", script.getPath()); // script.getPath() is the file URI string
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh script list and storage access check on resume
        if (!StorageHelper.hasStorageAccess(this)) {
            Toast.makeText(this, "Please select a scripts directory via the menu.", Toast.LENGTH_LONG).show();
            // createScriptButton.setEnabled(false); // Replaced with FAB
            fabCreateScript.setEnabled(false); // Added for FAB
            scripts.clear(); // Clear existing scripts if no storage access
            scriptAdapter.notifyDataSetChanged();
        } else {
            // createScriptButton.setEnabled(true); // Replaced with FAB
            fabCreateScript.setEnabled(true); // Added for FAB
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
            // The "No scripts found" Toast was here and has been removed as per requirements.
        } else {
            for (DocumentFile scriptFile : scriptFiles) {
                String fullFileName = scriptFile.getName(); // e.g., MyScript.py
                DocumentFile scriptDir = scriptFile.getParentFile(); // e.g., MyScript directory
                if (fullFileName != null && scriptDir != null) {
                    String scriptName = scriptDir.getName(); // Should be "MyScript"
                    String language = "Unknown";
                    String extension = "";

                    int dotIndex = fullFileName.lastIndexOf('.');
                    if (dotIndex > 0 && dotIndex < fullFileName.length() - 1) {
                        // We expect scriptName from directory to match fileName without extension
                        // String fileNameWithoutExtension = fullFileName.substring(0, dotIndex);
                        // if (!scriptName.equals(fileNameWithoutExtension)) {
                        //     Log.w("LoadScripts", "Mismatch between dir name ("+scriptName+") and file name ("+fileNameWithoutExtension+")");
                        //     // Optionally skip this script or handle error
                        // }
                        extension = fullFileName.substring(dotIndex).toLowerCase();
                        if (extension.equals(".py")) {
                            language = "Python";
                        } else if (extension.equals(".js")) {
                            language = "JavaScript";
                        }
                    }
                    // Ensure the script name (directory name) and file name (without extension) match
                    if (scriptName != null && fullFileName.startsWith(scriptName) && fullFileName.endsWith(extension)) {
                         scripts.add(new Script(scriptName, language, scriptFile.getUri().toString()));
                    } else {
                        // Log an error or warning if the structure is not as expected
                        // For example, if scriptFile is MyOtherScript.py inside MyScript/
                        android.util.Log.w("MainActivity", "Script file " + fullFileName + " does not match parent directory name " + scriptName);
                    }

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

            // String fileNameWithExtension = scriptNameInput + extension; // Not needed directly for new method

            DocumentFile newScriptFile = StorageHelper.createScriptDirectoryAndFile(MainActivity.this, scriptNameInput, extension);
            if (newScriptFile != null && newScriptFile.exists()) {
                Toast.makeText(MainActivity.this, "Script created: " + scriptNameInput, Toast.LENGTH_LONG).show();
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
        if (script == null || script.getPath() == null) {
            Toast.makeText(this, "Invalid script data for deletion.", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri scriptFileUri = Uri.parse(script.getPath());
        DocumentFile scriptFile = DocumentFile.fromSingleUri(this, scriptFileUri);

        // It's possible that fromSingleUri doesn't give full SAF capabilities for deletion of parent.
        // A more robust way, if script.getName() and script.getLanguage() are reliable:
        // String extension = "Python".equals(script.getLanguage()) ? ".py" : ".js";
        // DocumentFile actualScriptFile = StorageHelper.findScriptFile(this, script.getName(), extension);
        // However, StorageHelper.deleteScriptFile is designed to take the script *file* DocumentFile.

        if (scriptFile != null && scriptFile.exists()) {
            new AlertDialog.Builder(this)
                .setTitle("Delete Script")
                .setMessage("Are you sure you want to delete the script '" + script.getName() + "' and its directory?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (StorageHelper.deleteScriptFile(scriptFile)) {
                        Toast.makeText(MainActivity.this, "Script '" + script.getName() + "' deleted.", Toast.LENGTH_SHORT).show();
                        scripts.remove(script);
                        scriptAdapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(MainActivity.this, "Failed to delete script '" + script.getName() + "'.", Toast.LENGTH_SHORT).show();
                        // Consider reloading scripts here if deletion state is uncertain
                        loadScripts(); 
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            Toast.makeText(this, "Script file not found for deletion: " + script.getName(), Toast.LENGTH_LONG).show();
            // Fallback: try removing from list if file is already gone
            if (scripts.remove(script)) {
                scriptAdapter.notifyDataSetChanged();
            } else {
                // If it wasn't in the list, maybe it was already deleted or list is out of sync.
                loadScripts();
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
            if (itemId == R.id.action_run_now) {
                runScript(script);
                return true;
            } else if (itemId == R.id.action_edit_script) { // Assuming R.id.action_edit_script
                editScript(script);
                return true;
            } else if (itemId == R.id.action_change_schedule) {
                // Toast.makeText(MainActivity.this, "Change Schedule for " + script.getName() + " clicked. UI to be implemented.", Toast.LENGTH_LONG).show();
                showConfigureScheduleDialog(script);
                return true;
            } else if (itemId == R.id.action_rename_script) {
                showRenameScriptDialog(script);
                return true;
            } else if (itemId == R.id.action_pause_schedule) {
                androidx.work.WorkManager.getInstance(MainActivity.this).cancelAllWorkByTag(script.getPath());
                Toast.makeText(MainActivity.this, "Scheduled runs for '" + script.getName() + "' paused.", Toast.LENGTH_SHORT).show();
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

    private void showConfigureScheduleDialog(final Script script) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_configure_schedule, null);
        builder.setView(dialogView);
        builder.setTitle("Configure Schedule for: " + script.getName());

        RadioGroup scheduleTypeRadioGroup = dialogView.findViewById(R.id.scheduleTypeRadioGroup);
        LinearLayout oneTimeInputLayout = dialogView.findViewById(R.id.oneTimeInputLayout);
        DatePicker oneTimeDatePicker = dialogView.findViewById(R.id.oneTimeDatePicker);
        TimePicker oneTimeTimePicker = dialogView.findViewById(R.id.oneTimeTimePicker);
        oneTimeTimePicker.setIs24HourView(true);

        LinearLayout intervalInputLayout = dialogView.findViewById(R.id.intervalInputLayout);
        EditText intervalValueEditText = dialogView.findViewById(R.id.intervalValueEditText);
        Spinner intervalUnitSpinner = dialogView.findViewById(R.id.intervalUnitSpinner);
        // Populate Spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{"Minutes", "Hours"});
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        intervalUnitSpinner.setAdapter(spinnerAdapter);


        LinearLayout fixedTimeDailyInputLayout = dialogView.findViewById(R.id.fixedTimeDailyInputLayout);
        TimePicker fixedTimeDailyTimePicker = dialogView.findViewById(R.id.fixedTimeDailyTimePicker);
        fixedTimeDailyTimePicker.setIs24HourView(true);

        LinearLayout alternateDaysInputLayout = dialogView.findViewById(R.id.alternateDaysInputLayout);
        TimePicker alternateDaysTimePicker = dialogView.findViewById(R.id.alternateDaysTimePicker);
        alternateDaysTimePicker.setIs24HourView(true);

        LinearLayout everyNDaysInputLayout = dialogView.findViewById(R.id.everyNDaysInputLayout);
        TimePicker everyNDaysTimePicker = dialogView.findViewById(R.id.everyNDaysTimePicker);
        everyNDaysTimePicker.setIs24HourView(true);
        EditText nDaysEditText = dialogView.findViewById(R.id.nDaysEditText);

        final View[] visibleLayouts = {oneTimeInputLayout, intervalInputLayout, fixedTimeDailyInputLayout, alternateDaysInputLayout, everyNDaysInputLayout};

        scheduleTypeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            for (View layout : visibleLayouts) {
                layout.setVisibility(View.GONE);
            }
            if (checkedId == R.id.radio_one_time) {
                oneTimeInputLayout.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.radio_interval) {
                intervalInputLayout.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.radio_fixed_time_daily) {
                fixedTimeDailyInputLayout.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.radio_alternate_days) {
                alternateDaysInputLayout.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.radio_every_n_days) {
                everyNDaysInputLayout.setVisibility(View.VISIBLE);
            }
        });
        // Set a default selection and trigger visibility
        scheduleTypeRadioGroup.check(R.id.radio_one_time); 
        oneTimeInputLayout.setVisibility(View.VISIBLE);


        builder.setPositiveButton("Save", (dialog, which) -> {
            androidx.work.WorkManager.getInstance(this).cancelAllWorkByTag(script.getPath());
            int selectedTypeId = scheduleTypeRadioGroup.getCheckedRadioButtonId();

            if (selectedTypeId == R.id.radio_one_time) {
                int year = oneTimeDatePicker.getYear();
                int month = oneTimeDatePicker.getMonth(); // 0-indexed
                int day = oneTimeDatePicker.getDayOfMonth();
                int hour = oneTimeTimePicker.getHour();
                int minute = oneTimeTimePicker.getMinute();
                ScriptScheduler.scheduleOneTimeSpecific(this, script.getPath(), year, month, day, hour, minute);
            } else if (selectedTypeId == R.id.radio_interval) {
                String intervalValueStr = intervalValueEditText.getText().toString();
                if (!intervalValueStr.isEmpty()) {
                    long intervalValue = Long.parseLong(intervalValueStr);
                    String unit = intervalUnitSpinner.getSelectedItem().toString();
                    long intervalMillis = 0;
                    if ("Minutes".equals(unit)) {
                        intervalMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(intervalValue);
                    } else if ("Hours".equals(unit)) {
                        intervalMillis = java.util.concurrent.TimeUnit.HOURS.toMillis(intervalValue);
                    }
                    // Existing scheduleScript takes interval in minutes. Adjust if needed.
                    // For simplicity, let's assume scheduleScript is for periodic, true.
                    // scheduleScript(Context context, String scriptPath, long intervalMinutes, boolean isPeriodic)
                    if (intervalMillis > 0) {
                         long intervalMinutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(intervalMillis);
                         if (intervalMinutes == 0 && intervalMillis > 0) intervalMinutes = 1; // Minimum 1 minute if some millis provided
                         ScriptScheduler.scheduleScript(this, script.getPath(), intervalMinutes, true); // isPeriodic = true
                    } else {
                        Toast.makeText(this, "Invalid interval value.", Toast.LENGTH_SHORT).show();
                        return; // Don't dismiss dialog
                    }
                } else {
                     Toast.makeText(this, "Interval value cannot be empty.", Toast.LENGTH_SHORT).show();
                     return; // Don't dismiss dialog
                }
            } else if (selectedTypeId == R.id.radio_fixed_time_daily) {
                int hour = fixedTimeDailyTimePicker.getHour();
                int minute = fixedTimeDailyTimePicker.getMinute();
                // This requires calculating initial delay until hour:minute today, or tomorrow if past.
                // For now, using existing scheduleScriptAtFixedTime which might need adjustment or a new method.
                // Let's assume scheduleScriptAtFixedTime is actually meant for this use case (daily at fixed time)
                // and it correctly calculates the delay to the *next* occurrence.
                // The current scheduleScriptAtFixedTime takes a full start time (long).
                // We need a method that takes hour/minute and schedules daily.
                // Let's create a conceptual one or use an existing one if it fits.
                // Workaround: Use scheduleEveryNDays with N=1
                ScriptScheduler.scheduleEveryNDays(this, script.getPath(), 1, hour, minute);

            } else if (selectedTypeId == R.id.radio_alternate_days) {
                int hour = alternateDaysTimePicker.getHour();
                int minute = alternateDaysTimePicker.getMinute();
                ScriptScheduler.scheduleAlternateDays(this, script.getPath(), hour, minute);
            } else if (selectedTypeId == R.id.radio_every_n_days) {
                String nDaysStr = nDaysEditText.getText().toString();
                 if (!nDaysStr.isEmpty()) {
                    int nDays = Integer.parseInt(nDaysStr);
                    if (nDays > 0) {
                        int hour = everyNDaysTimePicker.getHour();
                        int minute = everyNDaysTimePicker.getMinute();
                        ScriptScheduler.scheduleEveryNDays(this, script.getPath(), nDays, hour, minute);
                    } else {
                        Toast.makeText(this, "N must be greater than 0.", Toast.LENGTH_SHORT).show();
                        return; // Don't dismiss dialog
                    }
                } else {
                    Toast.makeText(this, "N days cannot be empty.", Toast.LENGTH_SHORT).show();
                    return; // Don't dismiss dialog
                }
            }
            Toast.makeText(this, "Schedule updated for " + script.getName(), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private void showRenameScriptDialog(final Script script) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Script: " + script.getName());

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(script.getName()); // Pre-fill with current name
        input.setHint("New script name");
        builder.setView(input);

        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newScriptNameFromDialog = input.getText().toString().trim();
            if (newScriptNameFromDialog.isEmpty()) {
                Toast.makeText(MainActivity.this, "Script name cannot be empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newScriptNameFromDialog.matches("[a-zA-Z0-9_]+")) {
                Toast.makeText(MainActivity.this, "Script name can only contain letters, numbers, and underscores.", Toast.LENGTH_LONG).show();
                return;
            }
            if (newScriptNameFromDialog.equals(script.getName())) {
                Toast.makeText(MainActivity.this, "New name is the same as the old name.", Toast.LENGTH_SHORT).show();
                return;
            }

            String oldScriptPath = script.getPath();
            String oldScriptFileNameWithExtension = new File(oldScriptPath).getName();
            
            DocumentFile scriptFileDoc = DocumentFile.fromSingleUri(this, Uri.parse(oldScriptPath));
            if (scriptFileDoc == null || !scriptFileDoc.exists()) {
                 Toast.makeText(MainActivity.this, "Original script file not found.", Toast.LENGTH_SHORT).show();
                 return;
            }
            DocumentFile scriptDirDoc = scriptFileDoc.getParentFile();
            if (scriptDirDoc == null || !scriptDirDoc.exists() || !scriptDirDoc.isDirectory()) {
                 Toast.makeText(MainActivity.this, "Original script directory not found.", Toast.LENGTH_SHORT).show();
                 return;
            }
            String currentScriptDirUriString = scriptDirDoc.getUri().toString();

            String newDirUriString = StorageHelper.renameScript(this, currentScriptDirUriString, oldScriptFileNameWithExtension, newScriptNameFromDialog);

            if (newDirUriString != null) {
                // Cancel old WorkManager tasks
                androidx.work.WorkManager.getInstance(this).cancelAllWorkByTag(oldScriptPath);

                // Update the Script object
                script.setName(newScriptNameFromDialog);
                String extension = "";
                int dotIndex = oldScriptFileNameWithExtension.lastIndexOf('.');
                if (dotIndex > 0) {
                    extension = oldScriptFileNameWithExtension.substring(dotIndex);
                }
                String newFullScriptPath = Uri.parse(newDirUriString).buildUpon().appendPath(newScriptNameFromDialog + extension).build().toString();
                script.setPath(newFullScriptPath);
                
                scriptAdapter.notifyDataSetChanged();
                Toast.makeText(MainActivity.this, "Script renamed to " + newScriptNameFromDialog, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Failed to rename script.", Toast.LENGTH_LONG).show();
                // Consider reloading scripts from storage to reflect actual state if partial rename occurred
                loadScripts(); 
            }
            // Dialog will be dismissed automatically
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
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
                // createScriptButton.setEnabled(true); // Replaced with FAB
                fabCreateScript.setEnabled(true); // Added for FAB
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