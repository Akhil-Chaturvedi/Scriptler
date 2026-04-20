package com.bytesmith.scriptler.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtils {

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayTimeFormat = SimpleDateFormat("EEEE HH:mm", Locale.getDefault())

    /**
     * Format a timestamp into a readable date/time string.
     * e.g., "2024-01-15 14:30"
     */
    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "N/A"
        return dateTimeFormat.format(Date(timestamp))
    }

    /**
     * Format a timestamp into a relative time string.
     * e.g., "in 5 minutes", "2 hours ago", "in 3 days"
     */
    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0) return "N/A"

        val now = System.currentTimeMillis()
        val diff = timestamp - now
        val absDiff = Math.abs(diff)
        val isFuture = diff > 0

        val prefix = if (isFuture) "in " else ""
        val suffix = if (!isFuture) " ago" else ""

        return when {
            absDiff < TimeUnit.MINUTES.toMillis(1) -> "just now"
            absDiff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(absDiff)
                "${prefix}${minutes} minute${if (minutes != 1L) "s" else ""}${suffix}"
            }
            absDiff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(absDiff)
                "${prefix}${hours} hour${if (hours != 1L) "s" else ""}${suffix}"
            }
            absDiff < TimeUnit.DAYS.toMillis(7) -> {
                val days = TimeUnit.MILLISECONDS.toDays(absDiff)
                "${prefix}${days} day${if (days != 1L) "s" else ""}${suffix}"
            }
            absDiff < TimeUnit.DAYS.toMillis(30) -> {
                val weeks = TimeUnit.MILLISECONDS.toDays(absDiff) / 7
                "${prefix}${weeks} week${if (weeks != 1L) "s" else ""}${suffix}"
            }
            else -> formatDate(timestamp)
        }
    }

    /**
     * Format a countdown from now to a future timestamp.
     * e.g., "05:30:12" (HH:MM:SS) or "2d 05:30:12" if more than a day
     */
    fun formatCountdown(targetTimestamp: Long): String {
        if (targetTimestamp <= 0) return "N/A"

        val now = System.currentTimeMillis()
        val remaining = targetTimestamp - now

        if (remaining <= 0) return "Now"

        val days = TimeUnit.MILLISECONDS.toDays(remaining)
        val hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60

        val timePart = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)

        return if (days > 0) {
            "${days}d $timePart"
        } else {
            timePart
        }
    }

    /**
     * Format a timestamp as time only.
     * e.g., "14:30"
     */
    fun formatTime(timestamp: Long): String {
        if (timestamp <= 0) return "N/A"
        return timeFormat.format(Date(timestamp))
    }

    /**
     * Format a timestamp as day + time.
     * e.g., "Monday 14:30"
     */
    fun formatDayTime(timestamp: Long): String {
        if (timestamp <= 0) return "N/A"
        return dayTimeFormat.format(Date(timestamp))
    }
}
