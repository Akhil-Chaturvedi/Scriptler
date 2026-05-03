package com.bytesmith.scriptler.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bytesmith.scriptler.ui.theme.*

@Composable
fun ScheduleButton(
    scheduleType: String,
    scheduleValue: String,
    modifier: Modifier = Modifier
) {
    ScriptlerTextButton(
        onClick = { /* Handled by modifier */ },
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = PaddingMedium, vertical = PaddingSmall)
        ) {
            Text(
                text = when (scheduleType) {
                    "none" -> "No Schedule"
                    "interval" -> "Every ${scheduleValue}"
                    "daily" -> "Daily at ${scheduleValue}"
                    "weekly" -> "Weekly on ${scheduleValue}"
                    else -> scheduleType
                },
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface
            )
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Schedule",
                tint = Primary
            )
        }
    }
}
