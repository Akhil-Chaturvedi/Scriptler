package com.bytesmith.scriptler

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import com.bytesmith.scriptler.models.Script
import com.bytesmith.scriptler.utils.FileUtils
import java.util.UUID
import java.util.concurrent.TimeUnit

class ScriptEditorActivity : AppCompatActivity(), ScheduleDialogFragment.ScheduleDialogListener {

    companion object {
        private const val TAG = "ScriptEditorActivity"
        private const val NEW_JS_TEMPLATE = "// Write your JavaScript code here\n\nfunction main() {\n console.log(\"Hello from Scriptler!\");\n return \"Script executed successfully\";\n}\n\nmain();"
        private const val NEW_PY_TEMPLATE = "# Write your Python code here\n\ndef main():\n print(\"Hello from Scriptler!\")\n\nif __name__ == \"__main__\":\n main()"
        private const val AUTO_SAVE_INTERVAL_MS = 30_000L // 30 seconds
    }

    private lateinit var nameInput: EditText
    private lateinit var languageSpinner: Spinner
    private lateinit var editor: CustomEditor
    private lateinit var scheduleInput: EditText
    private lateinit var saveButtonBottom: Button

    private lateinit var toolbar: Toolbar
    private lateinit var toolbarTitle: TextView
    private lateinit var buttonCancel: ImageButton
    private lateinit var buttonSaveToolbar: ImageButton

    private var originalCode = ""
    private var hasChanges = false
    private var script: Script? = null
    private var currentScheduleType: String = "none"
    private var currentScheduleValue: String = ""

    private lateinit var scriptRepository: ScriptRepository

    // Auto-save
    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = object : Runnable {
        override fun run() {
            if (hasChanges && isAutoSaveEnabled()) {
                autoSave()
            }
            autoSaveHandler.postDelayed(this, AUTO_SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before setContentView so the correct theme is used
        applyThemeFromSettings()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_editor)

        // Get references to UI elements
        nameInput = findViewById(R.id.name_input)
        languageSpinner = findViewById(R.id.language_spinner)
        scheduleInput = findViewById(R.id.schedule_input)
        saveButtonBottom = findViewById(R.id.save_button)

        // Initialize CustomEditor
        editor = CustomEditor(this)
        val editorContainer = findViewById<ViewGroup>(R.id.editor_container)
        editorContainer.addView(editor)

        // Toolbar
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = toolbar.findViewById(R.id.toolbar_title)
        buttonCancel = toolbar.findViewById(R.id.button_cancel)
        buttonSaveToolbar = toolbar.findViewById(R.id.button_save_toolbar)

        // Get ScriptRepository instance
        scriptRepository = ScriptRepository.getInstance(this)

        // Set up Language Spinner Adapter
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("javascript", "python"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter

        // Make schedule input read-only — clicking it opens the schedule dialog
        scheduleInput.isFocusable = false
        scheduleInput.isClickable = true
        scheduleInput.setOnClickListener { showScheduleDialog() }
        scheduleInput.setHint("Tap to set schedule")

        // Load Script Data (if editing) or set up for New Script
        val scriptId = intent.getStringExtra("script_id")
        if (scriptId != null) {
            // Editing existing script
            Log.d(TAG, "Loading existing script with ID: $scriptId")
            script = scriptRepository.getScriptById(scriptId)

            if (script != null) {
                Log.d(TAG, "Script found: ${script!!.name}")
                toolbarTitle.text = "Edit ${script!!.name}"
                nameInput.setText(script!!.name)
                val languagePosition = adapter.getPosition(script!!.language)
                if (languagePosition >= 0) {
                    languageSpinner.setSelection(languagePosition)
                }
                originalCode = FileUtils.readScript(script!!.name, script!!.language)
                editor.setText(originalCode)
                currentScheduleType = script!!.scheduleType
                currentScheduleValue = script!!.scheduleValue
                scheduleInput.setText(script!!.getScheduleDisplayText())
                nameInput.isEnabled = false
                hasChanges = false
            } else {
                Toast.makeText(this, "Error: Script not found", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Script with ID $scriptId not found in repository for editing.")
                finish()
                return
            }
        } else {
            // Creating new script
            Log.d(TAG, "Setting up for new script creation.")
            toolbarTitle.text = "New Script"
            nameInput.setText("")
            languageSpinner.setSelection(0) // JavaScript default
            editor.setText(NEW_JS_TEMPLATE)
            originalCode = NEW_JS_TEMPLATE
            scheduleInput.setText("")
            currentScheduleType = "none"
            currentScheduleValue = ""
            nameInput.isEnabled = true
            hasChanges = false
            script = Script(id = UUID.randomUUID().toString(), language = "javascript")
        }

        // Apply font size from settings
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val fontSize = preferences.getInt("editor_font_size", 14)
        editor.setFontSize(fontSize)

        // Add TextWatcher to track changes
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkIfChangesExist()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Set up button listeners
        buttonSaveToolbar.setOnClickListener { saveAndSchedule() }
        buttonCancel.setOnClickListener { handleCancel() }

        // Hide the original bottom save button
        saveButtonBottom.visibility = View.GONE

        // Start auto-save if enabled in settings
        if (isAutoSaveEnabled()) {
            autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_INTERVAL_MS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
    }

    // Re-read font size when returning from Settings in case it was changed
    override fun onResume() {
        super.onResume()
        // Re-apply font size in case it was changed in Settings
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val fontSize = preferences.getInt("editor_font_size", 14)
        editor.setFontSize(fontSize)
    }

    // --- Schedule Dialog ---

    private fun showScheduleDialog() {
        val dialog = ScheduleDialogFragment.newInstance(
            currentType = currentScheduleType,
            currentValue = currentScheduleValue,
            listener = this
        )
        dialog.show(supportFragmentManager, "ScheduleDialog")
    }

    override fun onScheduleSelected(scheduleDisplay: String, scheduleType: String, scheduleValue: String) {
        currentScheduleType = scheduleType
        currentScheduleValue = scheduleValue
        scheduleInput.setText(scheduleDisplay)
        hasChanges = true
        Log.d(TAG, "Schedule set: type=$scheduleType, value=$scheduleValue, display=$scheduleDisplay")
    }

    // --- Auto-Save ---

    private fun isAutoSaveEnabled(): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        return preferences.getBoolean("auto_save_enabled", true)
    }

    private fun autoSave() {
        if (script == null || script!!.id.isEmpty()) return

        val name = nameInput.text.toString().trim()
        val language = languageSpinner.selectedItem.toString()
        val code = editor.getText()

        if (name.isEmpty() || code.isEmpty()) return

        FileUtils.createScriptFolder(name)
        FileUtils.saveScript(name, code, language)
        originalCode = code
        hasChanges = false
        Log.d(TAG, "Auto-saved script: $name")
        Toast.makeText(this, "Auto-saved", Toast.LENGTH_SHORT).show()
    }

    // --- Theme Propagation ---

    private fun applyThemeFromSettings() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val isDarkTheme = preferences.getBoolean("dark_theme_enabled", true)
        if (isDarkTheme) {
            setTheme(R.style.Theme_Scriptler)
        } else {
            setTheme(R.style.Theme_Scriptler_Light)
        }
    }

    // --- Existing methods ---

    private fun checkIfChangesExist() {
        hasChanges = editor.getText() != originalCode
        Log.d(TAG, "Checking for changes. Has changes: $hasChanges")
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        if (hasChanges) {
            showUnsavedChangesDialog()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun handleCancel() {
        if (hasChanges) {
            showUnsavedChangesDialog()
        } else {
            finish()
        }
    }

    private fun showUnsavedChangesDialog() {
        Log.d(TAG, "Showing unsaved changes dialog.")
        AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Are you sure you want to exit without saving?")
            .setPositiveButton("Exit Without Saving") { _, _ ->
                hasChanges = false
                finish()
            }
            .setNegativeButton("Continue Editing") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun saveAndSchedule() {
        val name = nameInput.text.toString().trim()
        val language = languageSpinner.selectedItem.toString()
        val code = editor.getText()

        // Validation
        if (name.isEmpty()) {
            Toast.makeText(this, "Script name is required", Toast.LENGTH_SHORT).show()
            return
        }
        if (code.isEmpty()) {
            Toast.makeText(this, "Script code cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (script == null || script!!.id.isEmpty()) {
            Toast.makeText(this, "Error: Script object not initialized correctly", Toast.LENGTH_SHORT).show()
            return
        }

        // Save code file
        FileUtils.createScriptFolder(name)
        FileUtils.saveScript(name, code, language)
        Log.d(TAG, "Script code file saved for: $name")

        // Use schedule from dialog (already stored in currentScheduleType/currentScheduleValue)
        val scheduleType = currentScheduleType
        val scheduleValue = currentScheduleValue

        // Update script object
        val updatedScript = script!!.copy(
            name = name,
            language = language,
            scheduleType = scheduleType,
            scheduleValue = scheduleValue
        )

        // Schedule or cancel WorkManager
        val scheduleManager = ScheduleManager.getInstance(this)
        if (scheduleType != "none" && updatedScript.isActive) {
            scheduleManager.scheduleScript(updatedScript)
        } else {
            scheduleManager.cancelSchedule(updatedScript.id)
        }

        // Save script metadata
        scriptRepository.saveOrUpdateScript(updatedScript)
        Log.d(TAG, "Script metadata saved/updated in repository for ID: ${updatedScript.id}")

        // Reset changes tracking
        originalCode = code
        hasChanges = false

        Toast.makeText(this, "Script saved successfully", Toast.LENGTH_SHORT).show()

        // Navigation after saving
        val startedForEditing = intent.hasExtra("script_id")
        if (!startedForEditing) {
            Log.d(TAG, "New script saved, navigating to details for ID: ${updatedScript.id}")
            val intent = Intent(this, ScriptDetailsActivity::class.java)
            intent.putExtra("script_id", updatedScript.id)
            startActivity(intent)
        }
        finish()
    }
}
