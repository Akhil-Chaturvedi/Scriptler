package com.bytesmith.scriptler;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText; // Import EditText
import android.widget.ScrollView; // Keep ScrollView if EditText is inside it
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.bytesmith.scriptler.R;
import com.bytesmith.scriptler.StorageHelper;

// Removed: import java.io.File; (No longer needed)
// Removed: import android.widget.LinearLayout; (No longer needed for codeContainer)


public class ScriptEditorActivity extends AppCompatActivity {
    private static final String TAG = "ScriptEditorActivity";
    // private ScrollView scrollView; // Keep if activity_script_editor.xml uses it around codeEditText
    private EditText codeEditText; // Changed from LinearLayout to EditText
    private String scriptName;
    private String scriptFileUriString;
    private DocumentFile scriptFile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_editor);

        // scrollView = findViewById(R.id.codeScrollView); // Assuming codeEditText is inside this
        codeEditText = findViewById(R.id.codeEditText); // Assuming this ID for the new EditText
        Button saveButton = findViewById(R.id.saveButton);
        Button closeButton = findViewById(R.id.closeButton);

        scriptName = getIntent().getStringExtra("scriptName");
        scriptFileUriString = getIntent().getStringExtra("scriptUri");

        if (scriptName == null || scriptFileUriString == null) {
            Toast.makeText(this, "Error: Script details not provided.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Script name or URI is null. Name: " + scriptName + ", URI: " + scriptFileUriString);
            finish();
            return;
        }

        setTitle("Edit: " + scriptName);

        Uri fileUri = Uri.parse(scriptFileUriString);
        scriptFile = DocumentFile.fromSingleUri(this, fileUri);

        if (scriptFile == null || !scriptFile.exists()) {
            Toast.makeText(this, "Error: Script file not found or accessible.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Script file DocumentFile is null or does not exist for URI: " + scriptFileUriString);
            finish();
            return;
        }

        loadScriptContent();

        saveButton.setOnClickListener(v -> saveScript());
        closeButton.setOnClickListener(v -> {
            // TODO: Consider prompting if there are unsaved changes before calling onBackPressed()
            onBackPressed();
        });
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
        } else {
            Toast.makeText(this, "Failed to load script content. File might be empty or an error occurred.", Toast.LENGTH_LONG).show();
            codeEditText.setText(""); // Set to empty if content is null (error or empty file)
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
        // Note: readScriptContent in StorageHelper appends a newline if the file ends with one.
        // EditText.getText().toString() will represent the text as displayed, including user's final newline if they typed one.
        // This behavior is generally fine.

        boolean success = StorageHelper.saveScriptContent(this, scriptFile, scriptContentString);

        if (success) {
            Toast.makeText(this, scriptName + " saved successfully.", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK); // Indicate success to MainActivity if it needs to know
            finish();
        } else {
            Toast.makeText(this, "Failed to save " + scriptName + ". Check logs.", Toast.LENGTH_LONG).show();
        }
    }
} 