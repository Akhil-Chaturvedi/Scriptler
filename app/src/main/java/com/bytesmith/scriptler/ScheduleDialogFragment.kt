package com.bytesmith.scriptler

import android.app.AlertDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.DialogFragment

/**
 * Dialog for selecting a script schedule type and value.
 *
 * Offers four options:
 * - None: No scheduling
 * - Interval: Run every N minutes (minimum 15 due to WorkManager constraints)
 * - Daily: Run at a specific time every day
 * - Weekly: Run on a specific day of week at a specific time
 *
 * Returns the schedule as a display string that ScriptEditorActivity can parse,
 * matching the existing format: "Every 15m", "Daily at 09:00", "Monday at 14:30", or "No schedule"
 */
class ScheduleDialogFragment : DialogFragment() {

    interface ScheduleDialogListener {
        fun onScheduleSelected(scheduleDisplay: String, scheduleType: String, scheduleValue: String)
    }

    private var listener: ScheduleDialogListener? = null
    private var currentScheduleType: String = "none"
    private var currentScheduleValue: String = ""

    // Views
    private lateinit var spinnerScheduleType: Spinner
    private lateinit var intervalContainer: LinearLayout
    private lateinit var numberPickerInterval: NumberPicker
    private lateinit var dailyContainer: LinearLayout
    private lateinit var textDailyTime: TextView
    private lateinit var weeklyContainer: LinearLayout
    private lateinit var spinnerDayOfWeek: Spinner
    private lateinit var textWeeklyTime: TextView

    // Selected values
    private var dailyHour: Int = 9
    private var dailyMinute: Int = 0
    private var weeklyHour: Int = 9
    private var weeklyMinute: Int = 0

    companion object {
        fun newInstance(
            currentType: String = "none",
            currentValue: String = "",
            listener: ScheduleDialogListener
        ): ScheduleDialogFragment {
            val dialog = ScheduleDialogFragment()
            dialog.currentScheduleType = currentType
            dialog.currentScheduleValue = currentValue
            dialog.listener = listener
            return dialog
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        // Title
        val title = TextView(requireContext()).apply {
            text = "Set Schedule"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.text_color))
        }
        container.addView(title)

        // Schedule type spinner
        spinnerScheduleType = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                listOf("No schedule", "Interval", "Daily", "Weekly")
            )
        }
        container.addView(spinnerScheduleType)

        // --- Interval container ---
        intervalContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
            visibility = View.GONE
        }

        val intervalLabel = TextView(requireContext()).apply {
            text = "Every "
            textSize = 16f
            setTextColor(requireContext().getColor(R.color.text_color))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        intervalContainer.addView(intervalLabel)

        numberPickerInterval = NumberPicker(requireContext()).apply {
            minValue = 15
            maxValue = 1440
            value = 15
            wrapSelectorWheel = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        intervalContainer.addView(numberPickerInterval)

        val intervalSuffix = TextView(requireContext()).apply {
            text = " minutes"
            textSize = 16f
            setTextColor(requireContext().getColor(R.color.text_color))
        }
        intervalContainer.addView(intervalSuffix)

        container.addView(intervalContainer)

        // --- Daily container ---
        dailyContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
            visibility = View.GONE
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val dailyLabel = TextView(requireContext()).apply {
            text = "At: "
            textSize = 16f
            setTextColor(requireContext().getColor(R.color.text_color))
        }
        dailyContainer.addView(dailyLabel)

        textDailyTime = TextView(requireContext()).apply {
            text = "09:00"
            textSize = 16f
            setTextColor(requireContext().getColor(R.color.primary_color))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(16, 8, 16, 8)
            setOnClickListener { showDailyTimePicker() }
        }
        dailyContainer.addView(textDailyTime)

        container.addView(dailyContainer)

        // --- Weekly container ---
        weeklyContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
            visibility = View.GONE
        }

        val dayLabel = TextView(requireContext()).apply {
            text = "Day of week:"
            textSize = 14f
            setTextColor(requireContext().getColor(R.color.text_secondary_color))
        }
        weeklyContainer.addView(dayLabel)

        spinnerDayOfWeek = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            )
        }
        weeklyContainer.addView(spinnerDayOfWeek)

        val weeklyTimeLabel = TextView(requireContext()).apply {
            text = "At:"
            textSize = 14f
            setTextColor(requireContext().getColor(R.color.text_secondary_color))
            setPadding(0, 8, 0, 0)
        }
        weeklyContainer.addView(weeklyTimeLabel)

        textWeeklyTime = TextView(requireContext()).apply {
            text = "09:00"
            textSize = 16f
            setTextColor(requireContext().getColor(R.color.primary_color))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(16, 8, 16, 8)
            setOnClickListener { showWeeklyTimePicker() }
        }
        weeklyContainer.addView(textWeeklyTime)

        container.addView(weeklyContainer)

        // Set up spinner listener
        spinnerScheduleType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                intervalContainer.visibility = View.GONE
                dailyContainer.visibility = View.GONE
                weeklyContainer.visibility = View.GONE

                when (position) {
                    1 -> intervalContainer.visibility = View.VISIBLE
                    2 -> dailyContainer.visibility = View.VISIBLE
                    3 -> weeklyContainer.visibility = View.VISIBLE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Pre-fill from current schedule
        prefillFromCurrentSchedule()

        builder.setView(container)
        builder.setPositiveButton("Set Schedule") { _, _ ->
            val result = when (spinnerScheduleType.selectedItemPosition) {
                1 -> {
                    val minutes = numberPickerInterval.value
                    Triple("Every ${minutes}m", "interval", minutes.toString())
                }
                2 -> {
                    val timeStr = String.format("%02d:%02d", dailyHour, dailyMinute)
                    Triple("Daily at $timeStr", "daily", timeStr)
                }
                3 -> {
                    val day = spinnerDayOfWeek.selectedItem.toString()
                    val timeStr = String.format("%02d:%02d", weeklyHour, weeklyMinute)
                    Triple("$day at $timeStr", "weekly", "${day.lowercase()}/$timeStr")
                }
                else -> Triple("No schedule", "none", "")
            }

            listener?.onScheduleSelected(result.first, result.second, result.third)
        }
        builder.setNegativeButton("Cancel", null)

        return builder.create()
    }

    private fun prefillFromCurrentSchedule() {
        when (currentScheduleType) {
            "interval" -> {
                spinnerScheduleType.setSelection(1)
                val minutes = try { currentScheduleValue.toLong().toInt() } catch (e: Exception) { 15 }
                numberPickerInterval.value = maxOf(15, minutes)
            }
            "daily" -> {
                spinnerScheduleType.setSelection(2)
                val parts = currentScheduleValue.split(":")
                if (parts.size == 2) {
                    dailyHour = try { parts[0].trim().toInt() } catch (e: Exception) { 9 }
                    dailyMinute = try { parts[1].trim().toInt() } catch (e: Exception) { 0 }
                }
                textDailyTime.text = String.format("%02d:%02d", dailyHour, dailyMinute)
            }
            "weekly" -> {
                spinnerScheduleType.setSelection(3)
                val parts = currentScheduleValue.split("/")
                if (parts.size == 2) {
                    // Set day spinner
                    val dayName = parts[0].trim().replaceFirstChar { it.uppercase() }
                    val dayAdapter = spinnerDayOfWeek.adapter
                    for (i in 0 until dayAdapter.count) {
                        if (dayAdapter.getItem(i).toString().equals(dayName, ignoreCase = true)) {
                            spinnerDayOfWeek.setSelection(i)
                            break
                        }
                    }
                    // Set time
                    val timeParts = parts[1].trim().split(":")
                    if (timeParts.size == 2) {
                        weeklyHour = try { timeParts[0].trim().toInt() } catch (e: Exception) { 9 }
                        weeklyMinute = try { timeParts[1].trim().toInt() } catch (e: Exception) { 0 }
                    }
                }
                textWeeklyTime.text = String.format("%02d:%02d", weeklyHour, weeklyMinute)
            }
            else -> {
                spinnerScheduleType.setSelection(0)
            }
        }
    }

    private fun showDailyTimePicker() {
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                dailyHour = hour
                dailyMinute = minute
                textDailyTime.text = String.format("%02d:%02d", hour, minute)
            },
            dailyHour,
            dailyMinute,
            true
        ).show()
    }

    private fun showWeeklyTimePicker() {
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                weeklyHour = hour
                weeklyMinute = minute
                textWeeklyTime.text = String.format("%02d:%02d", hour, minute)
            },
            weeklyHour,
            weeklyMinute,
            true
        ).show()
    }
}
