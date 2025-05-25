package com.bytesmith.scriptler;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bytesmith.scriptler.R;
import com.bytesmith.scriptler.Script;
import com.bytesmith.scriptler.MainActivity;

import java.util.List;

public class ScriptAdapter extends ArrayAdapter<Script> {
    private final Context context;
    private final List<Script> scripts;

    public ScriptAdapter(Context context, List<Script> scripts) {
        super(context, R.layout.item_script, scripts);
        this.context = context;
        this.scripts = scripts;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_script, parent, false);
        }

        Script script = getItem(position);
        if (script != null) {
            TextView scriptNameTextView = convertView.findViewById(R.id.scriptNameTextView);
            TextView scriptLanguageTextView = convertView.findViewById(R.id.scriptLanguageTextView);
            Button runButton = convertView.findViewById(R.id.runButton);
            Button overflowButton = convertView.findViewById(R.id.overflowButton);

            scriptNameTextView.setText(script.getName());
            // scriptLanguageTextView.setText(script.getLanguage()); // Original line

            // Set "PY" or "JS" for language
            String language = script.getLanguage();
            if ("Python".equalsIgnoreCase(language)) {
                scriptLanguageTextView.setText("PY");
            } else if ("JavaScript".equalsIgnoreCase(language)) {
                scriptLanguageTextView.setText("JS");
            } else {
                scriptLanguageTextView.setText(language); // Fallback to full name if not matched
            }

            runButton.setOnClickListener(v -> {
                // Handle run button click
                ((MainActivity) context).runScript(script);
            });

            overflowButton.setOnClickListener(v -> {
                // Handle overflow button click
                ((MainActivity) context).showScriptOptions(script, v); // Pass the anchor view (overflowButton itself)
            });
        }

        return convertView;
    }
} 