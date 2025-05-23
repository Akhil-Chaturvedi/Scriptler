package com.bytesmith.scriptler;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.appcompat.app.AppCompatActivity;
import com.bytesmith.scriptler.R;
import com.bytesmith.scriptler.helpers.StorageHelper;
import java.io.File;

public class ScriptEditorActivity extends AppCompatActivity {
    private ScrollView scrollView;
    private LinearLayout codeContainer;
    private String scriptName;
    private String language;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_editor);

        scrollView = findViewById(R.id.codeScrollView);
        codeContainer = findViewById(R.id.codeContainer);
        Button saveButton = findViewById(R.id.saveButton);
        Button closeButton = findViewById(R.id.closeButton);

        scriptName = getIntent().getStringExtra("scriptName");
        language = getIntent().getStringExtra("language");

        setTitle("Edit " + scriptName);

        // Initialize the code editor with sample content
        initializeCodeEditor();

        saveButton.setOnClickListener(v -> saveScript());
        closeButton.setOnClickListener(v -> {
            onBackPressed();
        });
    }

    private void initializeCodeEditor() {
        // Sample code lines - replace with actual script content loading
        String[] codeLines = {
                "print('Hello, Scriptler!')",
                "import requests",
                "response = requests.get('https://api.example.com/data')",
                "print(response.status_code)"
        };

        for (int i = 0; i < codeLines.length; i++) {
            addCodeLine(codeLines[i]);
        }
    }

    private void addCodeLine(String lineContent) {
        View codeLineView = getLayoutInflater().inflate(R.layout.item_code_line, null);
        codeContainer.addView(codeLineView);

        // Set the code text
        LineInput lineInput = codeLineView.findViewById(R.id.codeLineInput);
        lineInput.setText(lineContent);

        // Set up the copy button
        Button copyButton = codeLineView.findViewById(R.id.copyButton);
        final int lineIndex = codeContainer.indexOfChild(codeLineView);
        copyButton.setOnClickListener(v -> {
            copyLine(lineIndex);
            scrollDown();
        });
    }

    private void copyLine(int lineIndex) {
        LineInput lineInput = codeContainer.getChildAt(lineIndex).findViewById(R.id.codeLineInput);
        String lineText = lineInput.getText().toString();
        // Copy to clipboard
        // Implement clipboard logic here
    }

    private void scrollDown() {
        int scrollAmount = scrollView.getChildAt(0).getHeight() - scrollView.getHeight();
        scrollView.smoothScrollBy(0, scrollAmount);
    }

    private void saveScript() {
        StringBuilder scriptContent = new StringBuilder();
        for (int i = 0; i < codeContainer.getChildCount(); i++) {
            LineInput lineInput = codeContainer.getChildAt(i).findViewById(R.id.codeLineInput);
            scriptContent.append(lineInput.getText().toString()).append("\n");
        }

        // Save the script to the selected directory
        String scriptsDir = StorageHelper.getScriptsDirectory(this);
        File scriptDir = new File(scriptsDir, scriptName);
        if (!scriptDir.exists()) {
            scriptDir.mkdirs();
        }

        File scriptFile = new File(scriptDir, scriptName + (language.equals("Python") ? ".py" : ".js"));
        // Write scriptContent to scriptFile
        // Implement file writing logic here

        finish();
    }
} 