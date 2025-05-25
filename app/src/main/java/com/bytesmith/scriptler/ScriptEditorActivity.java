package com.bytesmith.scriptler;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
// import android.widget.Button; // Removed
import android.widget.EditText; // Import EditText
// import android.widget.ScrollView; // Keep ScrollView if EditText is inside it - No longer used directly
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;

import com.bytesmith.scriptler.R;
import com.bytesmith.scriptler.StorageHelper;

// Removed: import java.io.File; (No longer needed)
// Removed: import android.widget.LinearLayout; (No longer needed for codeContainer)


public class ScriptEditorActivity extends AppCompatActivity {
    private static final String TAG = "ScriptEditorActivity";
    // private ScrollView scrollView; // No longer directly used
    private EditText codeEditText;
    private String scriptName;
    private String scriptFileUriString;
    private DocumentFile scriptFile;
    private boolean hasUnsavedChanges = false;
    private Toolbar toolbar;

    private android.os.Handler autoSaveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable autoSaveRunnable;
    private static final long AUTO_SAVE_DELAY_MS = 2000; // 2 seconds


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_editor);

        toolbar = findViewById(R.id.editor_toolbar);
        setSupportActionBar(toolbar);

        codeEditText = findViewById(R.id.codeEditText);
        // Removed: Button saveButton = findViewById(R.id.saveButton);
        // Removed: Button closeButton = findViewById(R.id.closeButton);

        scriptName = getIntent().getStringExtra("scriptName");
        scriptFileUriString = getIntent().getStringExtra("scriptUri");

        if (scriptName == null || scriptFileUriString == null) {
            Toast.makeText(this, "Error: Script details not provided.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Script name or URI is null. Name: " + scriptName + ", URI: " + scriptFileUriString);
            finish();
            return;
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Edit: " + scriptName);
        }


        Uri fileUri = Uri.parse(scriptFileUriString);
        scriptFile = DocumentFile.fromSingleUri(this, fileUri);

        if (scriptFile == null || !scriptFile.exists()) {
            Toast.makeText(this, "Error: Script file not found or accessible.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Script file DocumentFile is null or does not exist for URI: " + scriptFileUriString);
            finish();
            return;
        }

        loadScriptContent();
        
        codeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hasUnsavedChanges = true;
            }

            @Override
            public void afterTextChanged(Editable s) {
                hasUnsavedChanges = true; // Already here, but good to confirm
                autoSaveHandler.removeCallbacks(autoSaveRunnable);
                autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY_MS);
            }
        });

        autoSaveRunnable = () -> {
            if (hasUnsavedChanges) { // Check if there are changes to save
                saveScript(); // This method should NOT finish the activity
                // Optionally, show a brief toast:
                Toast.makeText(ScriptEditorActivity.this, "Auto-saved", Toast.LENGTH_SHORT).show();
            }
        };

        // Removed: saveButton.setOnClickListener(v -> saveScript());
        // Removed: closeButton.setOnClickListener(v -> onBackPressed());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.script_editor_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_save_script) {
            saveScript();
            return true;
        } else if (itemId == R.id.action_exit_editor) {
            onBackPressed(); // Will trigger confirmation if unsaved changes
            return true;
        } else if (itemId == R.id.action_copy_all) {
            copyAllScriptContent();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void copyAllScriptContent() {
        String scriptContent = codeEditText.getText().toString();
        if (scriptContent.isEmpty()) {
            Toast.makeText(this, "Nothing to copy.", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("ScriptContent", scriptContent);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Script content copied to clipboard.", Toast.LENGTH_SHORT).show();
    }

    private void loadScriptContent() {
        if (scriptFile == null || !scriptFile.canRead()) {
            Toast.makeText(this, "Error: Cannot read script file.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Cannot read script file: " + (scriptFile != null ? scriptFile.getUri().toString() : "null DocumentFile"));
            codeEditText.setText(""); // Clear editor on error
            return;
        }

        String content = StorageHelper.readScriptContent(this, scriptFile);
        if (content != null) {
            codeEditText.setText(content);
            hasUnsavedChanges = false; // Content loaded, no changes yet
        } else {
            Toast.makeText(this, "Failed to load script content. File might be empty or an error occurred.", Toast.LENGTH_LONG).show();
            codeEditText.setText(""); // Set to empty if content is null (error or empty file)
            hasUnsavedChanges = false; // Still no changes from user perspective
        }
    }

    // Removed addCodeLine method
    // Removed copyLine method
    // Removed scrollDown method

    private void saveScript() {
        if (scriptFile == null || !scriptFile.canWrite()) {
            Toast.makeText(this, "Error: Cannot write to script file. Check permissions.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Cannot write to script file (null, or !canWrite()): " + (scriptFile != null ? scriptFile.getUri().toString() : "null DocumentFile"));
            return;
        }

        String scriptContentString = codeEditText.getText().toString();
        boolean success = StorageHelper.saveScriptContent(this, scriptFile, scriptContentString);

        if (success) {
            Toast.makeText(this, scriptName + " saved successfully.", Toast.LENGTH_SHORT).show();
            hasUnsavedChanges = false; // Content saved
            // setResult(RESULT_OK); // Keep if MainActivity needs to refresh
            // finish(); // Don't finish automatically, let user decide via Exit or back press
        } else {
            Toast.makeText(this, "Failed to save " + scriptName + ". Check logs.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (hasUnsavedChanges) {
            new AlertDialog.Builder(this)
                    .setTitle("Unsaved Changes")
                    .setMessage("Exit without saving?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        finish(); // Close the activity
                    })
                    .setNegativeButton("No", null) // Dismiss dialog, do nothing
                    .show();
        } else {
            super.onBackPressed(); // Exit normally
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoSaveHandler != null && autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
        }
    }
}