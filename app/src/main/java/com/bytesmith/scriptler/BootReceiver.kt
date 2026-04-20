package com.bytesmith.scriptler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that re-registers all script schedules after device reboot.
 * WorkManager should automatically re-enqueue periodic work, but this provides
 * a safety measure to ensure schedules are properly restored.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.d(TAG, "Boot completed received, re-registering schedules...")
            try {
                val scheduleManager = ScheduleManager.getInstance(context)
                scheduleManager.reRegisterAllSchedules()
                Log.d(TAG, "Schedules re-registered successfully after boot.")
            } catch (e: Exception) {
                Log.e(TAG, "Error re-registering schedules after boot", e)
            }
        }
    }
}
