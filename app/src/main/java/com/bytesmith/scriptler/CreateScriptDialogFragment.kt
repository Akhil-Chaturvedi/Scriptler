package com.bytesmith.scriptler

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.DialogFragment

class CreateScriptDialogFragment : DialogFragment() {

    interface CreateScriptDialogListener {
        fun onScriptCreateClick(scriptName: String, scriptLanguage: String)
    }

    private var listener: CreateScriptDialogListener? = null

    private lateinit var editTextScriptName: EditText
    private lateinit var languageSpinner: Spinner
    private lateinit var buttonCancel: Button
    private lateinit var buttonCreate: Button

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        listener = try {
            parentFragment as? CreateScriptDialogListener
                ?: throw ClassCastException("Parent fragment must implement CreateScriptDialogListener")
        } catch (e: ClassCastException) {
            throw ClassCastException(
                (parentFragment?.toString() ?: "null") + " must implement CreateScriptDialogListener"
            )
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_create_script, null)

        editTextScriptName = view.findViewById(R.id.edit_text_script_name)
        buttonCancel = view.findViewById(R.id.button_cancel)
        buttonCreate = view.findViewById(R.id.button_create)

        // Set up language spinner
        languageSpinner = view.findViewById(R.id.language_spinner)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("JavaScript", "Python")
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter

        builder.setView(view)

        buttonCancel.setOnClickListener {
            dialog?.cancel()
        }

        buttonCreate.setOnClickListener {
            val scriptName = editTextScriptName.text.toString().trim()
            val scriptLanguage = languageSpinner.selectedItem.toString().lowercase()

            if (scriptName.isEmpty()) {
                Toast.makeText(context, "Script name cannot be empty", Toast.LENGTH_SHORT).show()
            } else if (scriptName.contains(" ") || scriptName.contains("/")) {
                Toast.makeText(context, "Script name cannot contain spaces or slashes", Toast.LENGTH_SHORT).show()
            } else {
                listener?.onScriptCreateClick(scriptName, scriptLanguage)
                dialog?.dismiss()
            }
        }

        return builder.create()
    }
}
