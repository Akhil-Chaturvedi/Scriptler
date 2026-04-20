package com.bytesmith.scriptler

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bytesmith.scriptler.models.Script
import com.bytesmith.scriptler.utils.DateUtils

class ScriptAdapter(
    private var scripts: List<Script>,
    private val onScriptClick: (Script) -> Unit,
    private val onPlayPauseClick: (Script) -> Unit,
    private val onEditClick: (Script) -> Unit,
    private val onRenameClick: (Script) -> Unit,
    private val onDeleteClick: (Script) -> Unit,
    private val onScheduleClick: (Script) -> Unit
) : RecyclerView.Adapter<ScriptAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.script_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val script = scripts[position]

        holder.scriptName.text = script.name

        // Language badge
        holder.languageBadge.text = script.getLanguageBadge()
        val badgeColor = when (script.language) {
            "python" -> holder.itemView.context.getColor(R.color.primary_color)
            else -> holder.itemView.context.getColor(R.color.success_color)
        }
        holder.languageBadge.setTextColor(badgeColor)

        // Schedule info
        holder.scheduleText.text = script.getScheduleDisplayText()

        // Last run
        holder.lastRunText.text = if (script.lastRun > 0) {
            "Last: ${DateUtils.formatRelativeTime(script.lastRun)}"
        } else {
            "Last: Never"
        }

        // Play/Pause button
        val playPauseIcon = if (script.isActive && script.scheduleType != "none") {
            R.drawable.ic_bell
        } else {
            R.drawable.ic_code
        }
        holder.playPauseButton.setImageResource(playPauseIcon)
        holder.playPauseButton.setOnClickListener {
            onPlayPauseClick(script)
        }

        // Click on the card opens details
        holder.itemView.setOnClickListener {
            onScriptClick(script)
        }

        // Overflow menu
        holder.overflowButton.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.script_overflow_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit -> {
                        onEditClick(script)
                        true
                    }
                    R.id.action_rename -> {
                        onRenameClick(script)
                        true
                    }
                    R.id.action_schedule -> {
                        onScheduleClick(script)
                        true
                    }
                    R.id.action_delete -> {
                        onDeleteClick(script)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = scripts.size

    fun updateScripts(newScripts: List<Script>) {
        scripts = newScripts
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val scriptName: TextView = itemView.findViewById(R.id.script_name)
        val languageBadge: TextView = itemView.findViewById(R.id.language_badge)
        val scheduleText: TextView = itemView.findViewById(R.id.schedule_text)
        val lastRunText: TextView = itemView.findViewById(R.id.last_run)
        val playPauseButton: ImageButton = itemView.findViewById(R.id.play_pause_button)
        val overflowButton: ImageButton = itemView.findViewById(R.id.overflow_button)
    }
}
