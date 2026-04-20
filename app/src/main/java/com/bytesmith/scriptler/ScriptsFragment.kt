package com.bytesmith.scriptler

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bytesmith.scriptler.models.Script
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ScriptsFragment : Fragment(), CreateScriptDialogFragment.CreateScriptDialogListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var scriptAdapter: ScriptAdapter
    private lateinit var fab: FloatingActionButton
    private lateinit var headerTitle: TextView
    private lateinit var settingsButton: ImageButton
    private lateinit var scriptRepository: ScriptRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_scripts, container, false)

        scriptRepository = ScriptRepository.getInstance(requireContext())

        // Set up header
        headerTitle = view.findViewById(R.id.header_title)
        settingsButton = view.findViewById(R.id.settings_button)
        settingsButton.setOnClickListener {
            // Navigate to settings tab
            val activity = activity as? MainActivity
            activity?.navigateToSettings()
        }

        // Set up RecyclerView
        recyclerView = view.findViewById(R.id.scripts_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        scriptAdapter = ScriptAdapter(
            scripts = scriptRepository.getAllScripts(),
            onScriptClick = { script -> openScriptDetails(script) },
            onPlayPauseClick = { script -> toggleScriptActive(script) },
            onEditClick = { script -> openScriptEditor(script) },
            onRenameClick = { script -> showRenameDialog(script) },
            onDeleteClick = { script -> showDeleteDialog(script) },
            onScheduleClick = { script -> openScriptEditor(script) }
        )
        recyclerView.adapter = scriptAdapter

        // Set up FAB
        fab = view.findViewById(R.id.add_script_fab)
        fab.setOnClickListener {
            showCreateScriptDialog()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        refreshScriptsList()
    }

    private fun refreshScriptsList() {
        val scripts = scriptRepository.getAllScripts()
        scriptAdapter.updateScripts(scripts)
    }

    private fun showCreateScriptDialog() {
        val dialog = CreateScriptDialogFragment()
        dialog.show(parentFragmentManager, "CreateScriptDialog")
    }

    override fun onScriptCreateClick(scriptName: String, scriptLanguage: String) {
        val script = scriptRepository.createNewScript(scriptName, scriptLanguage)
        refreshScriptsList()
        // Open the editor for the newly created script
        openScriptEditor(script)
    }

    private fun openScriptDetails(script: Script) {
        val intent = Intent(requireContext(), ScriptDetailsActivity::class.java)
        intent.putExtra("script_id", script.id)
        startActivity(intent)
    }

    private fun openScriptEditor(script: Script) {
        val intent = Intent(requireContext(), ScriptEditorActivity::class.java)
        intent.putExtra("script_id", script.id)
        startActivity(intent)
    }

    private fun toggleScriptActive(script: Script) {
        val updatedScript = script.copy(isActive = !script.isActive)
        scriptRepository.saveOrUpdateScript(updatedScript)

        // Cancel or re-register schedule
        if (updatedScript.isActive) {
            ScheduleManager.getInstance(requireContext()).scheduleScript(updatedScript)
        } else {
            ScheduleManager.getInstance(requireContext()).cancelSchedule(updatedScript.id)
        }

        refreshScriptsList()
    }

    private fun showRenameDialog(script: Script) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        val input = android.widget.EditText(requireContext())
        input.setText(script.name)
        input.hint = "New script name"
        val container = android.widget.FrameLayout(requireContext())
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(48, 0, 48, 0)
        input.layoutParams = params
        container.addView(input)

        builder.setTitle("Rename Script")
            .setView(container)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != script.name) {
                    scriptRepository.renameScript(script.id, newName)
                    refreshScriptsList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteDialog(script: Script) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Script")
            .setMessage("Are you sure you want to delete '${script.name}'? This will remove the script folder and all logs.")
            .setPositiveButton("Delete") { _, _ ->
                ScheduleManager.getInstance(requireContext()).cancelSchedule(script.id)
                scriptRepository.deleteScript(script.id)
                refreshScriptsList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
