package com.bytesmith.scriptler.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Script(
    val id: String = "",
    val name: String = "",
    val language: String = "javascript", // "python" or "javascript"
    val scheduleType: String = "none", // "none", "interval", "daily", "weekly"
    val scheduleValue: String = "", // e.g., "15" for interval minutes, "09:00" for daily, "monday/09:00" for weekly
    var lastRun: Long = 0L,
    var nextRun: Long? = null,
    var isActive: Boolean = true
) : Serializable {

    fun getScheduleDisplayText(): String {
        return when (scheduleType) {
            "interval" -> "Every ${scheduleValue}m"
            "daily" -> "Daily at $scheduleValue"
            "weekly" -> {
                val parts = scheduleValue.split("/")
                if (parts.size == 2) "${parts[0].replaceFirstChar { it.uppercase() }} at ${parts[1]}" else scheduleValue
            }
            else -> "No schedule"
        }
    }

    fun getFileExtension(): String = when (language) {
        "python" -> "py"
        "javascript" -> "js"
        else -> "txt"
    }

    fun getLanguageBadge(): String = when (language) {
        "python" -> "PY"
        "javascript" -> "JS"
        else -> "??"
    }
}
