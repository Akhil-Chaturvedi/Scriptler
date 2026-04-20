package com.bytesmith.scriptler

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bytesmith.scriptler.models.Script
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ScheduleManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ScheduleManager"

        @Volatile
        private var instance: ScheduleManager? = null

        fun getInstance(context: Context): ScheduleManager {
            return instance ?: synchronized(this) {
                instance ?: ScheduleManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val workManager: WorkManager = WorkManager.getInstance(context)

    /**
     * Schedule a script based on its schedule type.
     */
    fun scheduleScript(script: Script) {
        // Cancel any existing schedule first
        cancelSchedule(script.id)

        when (script.scheduleType) {
            "interval" -> scheduleInterval(script)
            "daily" -> scheduleDaily(script)
            "weekly" -> scheduleWeekly(script)
            "none" -> {
                Log.d(TAG, "No schedule for script: ${script.name}")
                updateScriptNextRun(script, null)
            }
        }
    }

    /**
     * Cancel a scheduled script by its ID.
     */
    fun cancelSchedule(scriptId: String) {
        workManager.cancelUniqueWork(scriptId)
        Log.d(TAG, "Cancelled schedule for script ID: $scriptId")
    }

    /**
     * Re-register all active schedules (e.g., after boot).
     */
    fun reRegisterAllSchedules() {
        val scriptRepository = ScriptRepository.getInstance(context)
        val scripts = scriptRepository.getAllScripts()

        for (script in scripts) {
            if (script.isActive && script.scheduleType != "none") {
                scheduleScript(script)
                Log.d(TAG, "Re-registered schedule for: ${script.name}")
            }
        }
    }

    private fun scheduleInterval(script: Script) {
        val intervalMinutes = try {
            script.scheduleValue.toLong()
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Invalid interval value: ${script.scheduleValue}", e)
            return
        }

        // WorkManager minimum periodic interval is 15 minutes
        val minIntervalMinutes = 15L
        val effectiveInterval = maxOf(intervalMinutes, minIntervalMinutes)

        val inputData = Data.Builder()
            .putString("script_id", script.id)
            .putString("script_name", script.name)
            .putString("script_language", script.language)
            .build()

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .setRequiresDeviceIdle(false)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ScriptExecutionWorker>(
            effectiveInterval, TimeUnit.MINUTES
        )
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            script.id,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        val nextRun = System.currentTimeMillis() + effectiveInterval * 60 * 1000
        updateScriptNextRun(script, nextRun)

        Log.d(TAG, "Scheduled interval script: ${script.name} every $effectiveInterval minutes")
    }

    private fun scheduleDaily(script: Script) {
        // Parse time from scheduleValue (e.g., "09:00")
        val (hour, minute) = parseTime(script.scheduleValue) ?: return

        val initialDelay = calculateDelayToNextOccurrence(hour, minute)

        val inputData = Data.Builder()
            .putString("script_id", script.id)
            .putString("script_name", script.name)
            .putString("script_language", script.language)
            .build()

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .setRequiresDeviceIdle(false)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        // Use a periodic work request with 24-hour interval
        val workRequest = PeriodicWorkRequestBuilder<ScriptExecutionWorker>(
            24, TimeUnit.HOURS
        )
            .setInputData(inputData)
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            script.id,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        val nextRun = System.currentTimeMillis() + initialDelay
        updateScriptNextRun(script, nextRun)

        Log.d(TAG, "Scheduled daily script: ${script.name} at $hour:$minute (delay: ${initialDelay / 60000}m)")
    }

    private fun scheduleWeekly(script: Script) {
        // Parse day and time from scheduleValue (e.g., "monday/09:00")
        val parts = script.scheduleValue.split("/")
        if (parts.size != 2) {
            Log.e(TAG, "Invalid weekly schedule format: ${script.scheduleValue}")
            return
        }

        val targetDay = dayOfWeekFromString(parts[0].trim())
        val (hour, minute) = parseTime(parts[1].trim()) ?: return

        val initialDelay = calculateDelayToNextDayAndTime(targetDay, hour, minute)

        val inputData = Data.Builder()
            .putString("script_id", script.id)
            .putString("script_name", script.name)
            .putString("script_language", script.language)
            .build()

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .setRequiresDeviceIdle(false)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        // Use a periodic work request with 7-day interval
        val workRequest = PeriodicWorkRequestBuilder<ScriptExecutionWorker>(
            7, TimeUnit.DAYS
        )
            .setInputData(inputData)
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            script.id,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        val nextRun = System.currentTimeMillis() + initialDelay
        updateScriptNextRun(script, nextRun)

        Log.d(TAG, "Scheduled weekly script: ${script.name} on ${parts[0]} at $hour:$minute")
    }

    private fun updateScriptNextRun(script: Script, nextRun: Long?) {
        val scriptRepository = ScriptRepository.getInstance(context)
        val updatedScript = script.copy(nextRun = nextRun)
        scriptRepository.saveOrUpdateScript(updatedScript)
    }

    /**
     * Calculate the delay in milliseconds until the next occurrence of the given hour:minute.
     */
    private fun calculateDelayToNextOccurrence(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the target time has already passed today, schedule for tomorrow
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }

    /**
     * Calculate the delay in milliseconds until the next occurrence of the given day of week and hour:minute.
     */
    private fun calculateDelayToNextDayAndTime(targetDay: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, targetDay)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the target time has already passed this week, schedule for next week
        if (target.before(now)) {
            target.add(Calendar.WEEK_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }

    private fun parseTime(timeStr: String): Pair<Int, Int>? {
        val parts = timeStr.split(":")
        if (parts.size != 2) {
            Log.e(TAG, "Invalid time format: $timeStr")
            return null
        }
        return try {
            val hour = parts[0].trim().toInt()
            val minute = parts[1].trim().toInt()
            if (hour in 0..23 && minute in 0..59) {
                Pair(hour, minute)
            } else {
                Log.e(TAG, "Time values out of range: $hour:$minute")
                null
            }
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Invalid time number format: $timeStr", e)
            null
        }
    }

    private fun dayOfWeekFromString(day: String): Int {
        return when (day.lowercase()) {
            "sunday" -> Calendar.SUNDAY
            "monday" -> Calendar.MONDAY
            "tuesday" -> Calendar.TUESDAY
            "wednesday" -> Calendar.WEDNESDAY
            "thursday" -> Calendar.THURSDAY
            "friday" -> Calendar.FRIDAY
            "saturday" -> Calendar.SATURDAY
            else -> Calendar.MONDAY
        }
    }

    /**
     * Calculate the next run timestamp for a script based on its schedule.
     */
    fun calculateNextRun(script: Script): Long? {
        return when (script.scheduleType) {
            "interval" -> {
                val intervalMinutes = try {
                    script.scheduleValue.toLong()
                } catch (e: NumberFormatException) {
                    return null
                }
                val effectiveInterval = maxOf(intervalMinutes, 15L)
                System.currentTimeMillis() + effectiveInterval * 60 * 1000
            }
            "daily" -> {
                val (hour, minute) = parseTime(script.scheduleValue) ?: return null
                val delay = calculateDelayToNextOccurrence(hour, minute)
                System.currentTimeMillis() + delay
            }
            "weekly" -> {
                val parts = script.scheduleValue.split("/")
                if (parts.size != 2) return null
                val targetDay = dayOfWeekFromString(parts[0].trim())
                val (hour, minute) = parseTime(parts[1].trim()) ?: return null
                val delay = calculateDelayToNextDayAndTime(targetDay, hour, minute)
                System.currentTimeMillis() + delay
            }
            else -> null
        }
    }
}
