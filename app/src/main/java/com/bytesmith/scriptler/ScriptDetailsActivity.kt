package com.bytesmith.scriptler

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bytesmith.scriptler.models.Script
import com.bytesmith.scriptler.models.ScriptLog
import com.bytesmith.scriptler.utils.DateUtils
import com.bytesmith.scriptler.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID

class ScriptDetailsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ScriptDetailsActivity"
        private const val COUNTDOWN_UPDATE_INTERVAL = 1000L // 1 second
    }

    private lateinit var toolbar: Toolbar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var logsWrapper: LinearLayout
    private lateinit var emptyLogsState: LinearLayout
    private lateinit var emptyLogsTextView: TextView
    private lateinit var buttonClearLogs: ImageButton
    private lateinit var nextRunCard: LinearLayout
    private lateinit var textNextRunTime: TextView
    private lateinit var textNextRunCountdown: TextView
    private lateinit var footerContainer: FrameLayout
    private lateinit var buttonRunNow: Button

    private var scriptId: String? = null
    private var currentScript: Script? = null
    private lateinit var scriptRepository: ScriptRepository

    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            currentScript?.let { script ->
                if (script.nextRun != null && script.nextRun!! > 0) {
                    textNextRunCountdown.text = DateUtils.formatCountdown(script.nextRun!!)
                }
            }
            countdownHandler.postDelayed(this, COUNTDOWN_UPDATE_INTERVAL)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before setContentView so the correct theme is used
        applyThemeFromSettings()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_details)

        // Get references to UI elements
        toolbar = findViewById(R.id.toolbar)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        logsWrapper = findViewById(R.id.logs_wrapper)
        emptyLogsState = findViewById(R.id.empty_logs_state)
        emptyLogsTextView = findViewById(R.id.empty_logs_text)
        buttonClearLogs = findViewById(R.id.button_clear_logs)
        nextRunCard = findViewById(R.id.next_run_card)
        textNextRunTime = findViewById(R.id.text_next_run_time)
        textNextRunCountdown = findViewById(R.id.text_next_run_countdown)
        footerContainer = findViewById(R.id.footer_container)
        buttonRunNow = findViewById(R.id.button_run_now)

        // Set up Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Get ScriptRepository instance
        scriptRepository = ScriptRepository.getInstance(this)

        // Get the script ID from the Intent
        if (intent.hasExtra("script_id")) {
            scriptId = intent.getStringExtra("script_id")
            Log.d(TAG, "Received script ID: $scriptId")
            loadScriptData(scriptId!!)
        } else {
            Toast.makeText(this, "Error: Script ID not provided", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "ScriptDetailsActivity started without a script_id extra.")
            finish()
            return
        }

        // Set up Refresh Listener
        swipeRefreshLayout.setOnRefreshListener { loadLogs() }

        // Set up Button Listeners
        buttonRunNow.setOnClickListener { runScript() }
        buttonClearLogs.setOnClickListener { showClearLogsConfirmation() }
    }

    override fun onResume() {
        super.onResume()
        scriptId?.let { loadScriptData(it) }
        // Start countdown timer
        countdownHandler.post(countdownRunnable)
    }

    override fun onPause() {
        super.onPause()
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadScriptData(id: String) {
        currentScript = scriptRepository.getScriptById(id)

        if (currentScript != null) {
            Log.d(TAG, "Script data loaded for ID: $id, Name: ${currentScript!!.name}")
            supportActionBar?.title = currentScript!!.name
            updateNextRunUI(currentScript!!.nextRun)
            loadLogs()
        } else {
            Toast.makeText(this, "Error: Script not found", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Script with ID $id not found in repository.")
            finish()
        }
        swipeRefreshLayout.isRefreshing = false
    }

    private fun loadLogs() {
        if (scriptId == null) {
            swipeRefreshLayout.isRefreshing = false
            return
        }

        Log.d(TAG, "Loading logs for script ID: $scriptId")
        lifecycleScope.launch {
            val logs = scriptRepository.getLogsForScript(scriptId!!)
            displayLogs(logs)
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun displayLogs(logs: List<ScriptLog>) {
        logsWrapper.removeAllViews()

        if (logs.isNotEmpty()) {
            Log.d(TAG, "Displaying ${logs.size} log entries.")
            emptyLogsState.visibility = View.GONE
            logsWrapper.visibility = View.VISIBLE
            buttonClearLogs.visibility = View.VISIBLE

            // Display in reverse order (most recent first)
            for (i in logs.size - 1 downTo 0) {
                val log = logs[i]
                val runNumber = logs.size - i

                val logEntryLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 8, 0, 8)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                // Status
                val statusTextView = TextView(this).apply {
                    text = log.status.uppercase()
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(
                        when (log.status.lowercase()) {
                            "success" -> getColor(R.color.success_color)
                            "error" -> getColor(R.color.error_color)
                            else -> getColor(R.color.text_secondary_color)
                        }
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                logEntryLayout.addView(statusTextView)

                // Time and Run Number
                val timeTextView = TextView(this).apply {
                    val formattedTime = if (log.timestamp > 0) DateUtils.formatDate(log.timestamp) else "Unknown Time"
                    text = "$formattedTime (Run #$runNumber)"
                    setTextColor(getColor(R.color.text_secondary_color))
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                logEntryLayout.addView(timeTextView)

                // Output
                val outputTextView = TextView(this).apply {
                    text = log.output.ifEmpty { "No output." }
                    setTextColor(getColor(R.color.text_color))
                    textSize = 14f
                    typeface = android.graphics.Typeface.MONOSPACE
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                logEntryLayout.addView(outputTextView)

                logsWrapper.addView(logEntryLayout)

                // Add separator between entries
                if (i > 0) {
                    val separator = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            resources.getDimensionPixelSize(R.dimen.log_separator_height)
                        )
                        setBackgroundColor(getColor(R.color.border_color))
                    }
                    logsWrapper.addView(separator)
                }
            }
        } else {
            Log.d(TAG, "No logs found to display for script ID: $scriptId")
            emptyLogsState.visibility = View.VISIBLE
            logsWrapper.visibility = View.GONE
            buttonClearLogs.visibility = View.GONE
            emptyLogsTextView.text = "No execution logs yet for '${currentScript?.name ?: scriptId}'"
        }
    }

    private fun updateNextRunUI(nextRunTimestamp: Long?) {
        if (nextRunTimestamp != null && nextRunTimestamp > 0) {
            Log.d(TAG, "Updating next run UI with timestamp: $nextRunTimestamp")
            nextRunCard.visibility = View.VISIBLE
            textNextRunTime.text = DateUtils.formatDate(nextRunTimestamp)
            textNextRunCountdown.text = DateUtils.formatCountdown(nextRunTimestamp)
        } else {
            Log.d(TAG, "No next run scheduled, hiding next run UI.")
            nextRunCard.visibility = View.GONE
        }
    }

    private fun runScript() {
        if (scriptId == null || currentScript == null) {
            Toast.makeText(this, "Cannot run script: details not loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val script = currentScript!!

        // For Python scripts, check imports before executing
        if (script.language == "python") {
            val scriptRunner = ScriptRunner(this)
            val checkResult = scriptRunner.checkImports(script)

            if (checkResult.missingPackages.isNotEmpty()) {
                // Show ModuleInstallDialog for missing packages
                val dialog = ModuleInstallDialog.newInstance(
                    packages = checkResult.missingPackages,
                    onAllInstalled = {
                        // Re-run after packages are installed
                        executeScriptDirectly(script)
                    },
                    onDismiss = {
                        // User declined — run anyway and let Python produce the error
                        executeScriptDirectly(script)
                    }
                )
                dialog.show(supportFragmentManager, "ModuleInstallDialog")
                return
            }
        }

        // No missing imports (or non-Python script) — execute directly
        executeScriptDirectly(script)
    }

    /**
     * Execute the script directly without import checking.
     * This is called either when no missing imports were found,
     * or after the user has handled the ModuleInstallDialog.
     */
    private fun executeScriptDirectly(script: Script) {
        Log.d(TAG, "Initiating run for script: ${script.name} (ID: $scriptId)")
        Toast.makeText(this, "Running script: ${script.name}...", Toast.LENGTH_SHORT).show()
    
        // Use ScriptRunner to execute the script
        val scriptRunner = ScriptRunner(this)
    
        lifecycleScope.launch {
            val result = scriptRunner.execute(script)
            
            // Get log count
            val logCount = scriptRepository.getLogCountForScript(script.id)
            
            // Add log entry
            val logEntry = ScriptLog(
                id = UUID.randomUUID().toString(),
                scriptId = script.id,
                timestamp = System.currentTimeMillis(),
                runNumber = logCount + 1,
                output = result.output,
                status = if (result.isError) "error" else "success",
                isError = result.isError
            )
            scriptRepository.addLogForScript(script.id, logEntry)
    
            // Update script lastRun
            val updatedScript = script.copy(lastRun = System.currentTimeMillis())
            scriptRepository.saveOrUpdateScript(updatedScript)
            currentScript = updatedScript
    
            // Send notification if enabled
            val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@ScriptDetailsActivity)
            val notificationsEnabled = preferences.getBoolean("notifications_enabled", false)
            if (notificationsEnabled) {
                NotificationUtils.sendNotification(
                    this@ScriptDetailsActivity,
                    "${script.name} Executed",
                    if (result.isError) "Error: ${result.output}" else result.output
                )
            }
    
            // Refresh logs
            loadLogs()
        }
    }

    private fun showClearLogsConfirmation() {
        if (scriptId == null) {
            Toast.makeText(this, "Error finding script logs", Toast.LENGTH_SHORT).show()
            return
        }
    
        lifecycleScope.launch {
            val logs = scriptRepository.getLogsForScript(scriptId!!)
            if (logs.isEmpty()) {
                Toast.makeText(this@ScriptDetailsActivity, "No logs to clear for this script", Toast.LENGTH_SHORT).show()
                return@launch
            }
    
            AlertDialog.Builder(this@ScriptDetailsActivity)
                .setTitle("Clear Logs")
                .setMessage("Are you sure you want to clear all execution logs for this script?")
                .setPositiveButton("Clear") { _, _ ->
                    scriptRepository.clearLogsForScript(scriptId!!)
                    Toast.makeText(this@ScriptDetailsActivity, "Logs cleared", Toast.LENGTH_SHORT).show()
                    loadLogs()
                }
                .setNegativeButton("Cancel", null)
                .show()
            }
        }
        
        private fun clearLogs() {
        if (scriptId == null) {
            Toast.makeText(this, "Error clearing logs", Toast.LENGTH_SHORT).show()
            return
        }

        scriptRepository.clearLogsForScript(scriptId!!)
        loadLogs()
        Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
    }

    // Apply theme from settings before setContentView
    private fun applyThemeFromSettings() {
        val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val isDarkTheme = preferences.getBoolean("dark_theme_enabled", true)
        if (isDarkTheme) {
            setTheme(R.style.Theme_Scriptler)
        } else {
            setTheme(R.style.Theme_Scriptler_Light)
        }
    }
}
