package com.bytesmith.scriptler.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytesmith.scriptler.models.Script
import com.bytesmith.scriptler.models.ScriptLog
import com.bytesmith.scriptler.ScriptRunner
import com.bytesmith.scriptler.ui.components.*
import com.bytesmith.scriptler.ui.theme.*
import com.bytesmith.scriptler.utils.DateUtils

// Import common states to avoid overload resolution ambiguity
import com.bytesmith.scriptler.ui.components.CommonLoadingState
import com.bytesmith.scriptler.ui.components.CommonErrorState as CommonErrorState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ScriptDetailsScreenCompose(
    @Suppress("UNUSED_PARAMETER") scriptId: String,
    script: Script? = null,
    logs: List<ScriptLog> = emptyList(),
    isLoading: Boolean = false,
    isLoadingLogs: Boolean = false,
    isExecuting: Boolean = false,
    error: String? = null,
    executionResult: com.bytesmith.scriptler.ScriptRunner.ExecutionResult? = null,
    onNavigateBack: () -> Unit = {},
    onRunScript: () -> Unit = {},
    onClearLogs: () -> Unit = {},
    onEditScript: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onClearError: () -> Unit = {},
    onClearExecutionResult: () -> Unit = {}
) {
    var nextRunCountdown by remember { mutableStateOf("") }

    // Countdown timer
    LaunchedEffect(script?.nextRun) {
        while (script?.nextRun != null) {
            val now = System.currentTimeMillis()
            val nextRun = script.nextRun ?: 0
            val remaining = nextRun - now

            if (remaining > 0) {
                nextRunCountdown = DateUtils.formatCountdown(nextRun)
            } else {
                nextRunCountdown = "Running now..."
            }

            delay(1000)
        }
    }

    val scrollState = rememberLazyListState()
    var showScrollToTop by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Show scroll-to-top button when scrolled down
    LaunchedEffect(scrollState.canScrollForward) {
        showScrollToTop = scrollState.firstVisibleItemIndex > 0
    }

    Scaffold(
        topBar = {
            ScriptlerTopAppBar(
                title = script?.name ?: "Script",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onNavigateBack,
                actions = {
                    if (!isLoading && !isExecuting) {
                        ScriptlerIconButton(
                            onClick = onRefresh,
                            containerColor = Color.Transparent,
                            contentColor = OnSurface
                        ) {
                            Icon(Icons.Default.History, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        containerColor = Surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CommonLoadingState("Loading script...")
                }
                error != null -> {
                    CommonErrorState(
                        message = error,
                        onRetry = onClearError
                    )
                }
                script == null -> {
                    EmptyState()
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(PaddingMedium, PaddingMedium, PaddingMedium, PaddingMedium),
                            verticalArrangement = Arrangement.spacedBy(PaddingMedium)
                        ) {
                        // Script Info Card
                        item {
                            ScriptInfoCard(
                                script = script,
                                isExecuting = isExecuting,
                                onRunScript = onRunScript,
                                onEditScript = onEditScript
                            )
                        }

                        // Execution Result Card
                        executionResult?.let { result ->
                            item {
                                ExecutionResultCard(
                                    result = result,
                                    onDismiss = onClearExecutionResult
                                )
                            }
                        }

                        // Next Execution Card
                        script.nextRun?.let { nextRun ->
                            if (nextRun > 0) {
                                item {
                                    NextRunCard(
                                        nextRunTime = DateUtils.formatDate(nextRun),
                                        countdown = nextRunCountdown
                                    )
                                }
                            }
                        }

                        // Execution Logs Section
                        item {
                            LogsSection(
                                logs = logs,
                                isLoadingLogs = isLoadingLogs,
                                onClearLogs = onClearLogs,
                                onRefreshLogs = onRefresh
                            )
                        }
                    }
                    }

                    // Scroll to top floating button
                    if (showScrollToTop) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(PaddingLarge),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            ScriptlerIconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        scrollState.animateScrollToItem(0)
                                    }
                                },
                                containerColor = Primary,
                                contentColor = OnPrimary
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Scroll to top")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NextRunCard(
    nextRunTime: String,
    countdown: String
) {
    ScriptlerCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PaddingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Next Run",
                    style = MaterialTheme.typography.labelLarge,
                    color = OnSurfaceVariant
                )

                Spacer(modifier = Modifier.height(PaddingSmall))

                Text(
                    text = nextRunTime,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = OnSurface
                )
            }

            Box(
                modifier = Modifier
                    .background(SurfaceContainerHighest)
                    .padding(horizontal = PaddingLarge, vertical = PaddingMedium)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Countdown",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )

                    Text(
                        text = countdown,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Primary
                    )
                }
            }
        }
    }
}

@Composable
fun ScriptInfoCard(
    script: Script,
    isExecuting: Boolean = false,
    onRunScript: () -> Unit = {},
    onEditScript: () -> Unit = {}
) {
    ScriptlerCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PaddingMedium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = script.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = OnSurface
                    )

                    Spacer(modifier = Modifier.height(PaddingSmall))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(PaddingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Language Badge
                        Box(
                            modifier = Modifier
                                .background(
                                    when (script.language) {
                                        "javascript" -> Color(0xFFF7DF1E)
                                        "python" -> Color(0xFF3776AB)
                                        else -> Primary
                                    }.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = PaddingSmall, vertical = PaddingSmall)
                        ) {
                            Text(
                                text = script.getLanguageBadge(),
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurface
                            )
                        }

                        // Schedule Badge
                        if (script.scheduleType != "none") {
                            Box(
                                modifier = Modifier
                                    .background(
                                        Primary.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = PaddingSmall, vertical = PaddingSmall)
                            ) {
                                Text(
                                    text = script.getScheduleDisplayText(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Primary
                                )
                            }
                        }
                    }
                }

                ScriptlerIconButton(
                    onClick = onEditScript,
                    containerColor = SurfaceContainerHighest,
                    contentColor = OnSurface
                ) {
                    Icon(Icons.Default.Code, contentDescription = "Edit script")
                }
            }

            Spacer(modifier = Modifier.height(PaddingSmall))

            ScriptlerButton(
                onClick = onRunScript,
                enabled = !isExecuting,
                modifier = Modifier.fillMaxWidth(),
                content = { Text(if (isExecuting) "Running..." else "Run Now") }
            )
        }
    }
}

@Composable
fun ExecutionResultCard(
    result: ScriptRunner.ExecutionResult,
    onDismiss: () -> Unit = {}
) {
    ScriptlerCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (result.isError) Error.copy(alpha = 0.1f) else Success.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PaddingSmall),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (result.isError) Icons.Default.Error else Icons.Default.CheckCircle,
                    contentDescription = if (result.isError) "Error" else "Success",
                    tint = if (result.isError) Error else Success
                )

                Text(
                    text = if (result.isError) "Execution Failed" else "Execution Successful",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result.isError) Error else Success
                )
            }

            ScriptlerIconButton(
                onClick = onDismiss,
                containerColor = Color.Transparent,
                contentColor = OnSurfaceVariant
            ) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }

        Spacer(modifier = Modifier.height(PaddingMedium))

        Text(
            text = result.output,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = OnSurface,
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerHighest, RoundedCornerShape(8.dp))
                .padding(PaddingMedium)
        )
    }
}

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PaddingMedium)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "No script",
                tint = OnSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "Script not found",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface
            )

            Text(
                text = "The script you're looking for doesn't exist",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
fun LogsSection(
    logs: List<ScriptLog>,
    isLoadingLogs: Boolean = false,
    onClearLogs: () -> Unit,
    onRefreshLogs: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Activity Logs",
                style = MaterialTheme.typography.headlineMedium,
                color = OnSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(PaddingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (logs.isNotEmpty()) {
                    Text(
                        text = "${logs.size} Entries",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(PaddingSmall))

                    ScriptlerIconButton(
                        onClick = onClearLogs,
                        containerColor = Color.Transparent,
                        contentColor = Error
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                    }
                }

                ScriptlerIconButton(
                    onClick = onRefreshLogs,
                    containerColor = Color.Transparent,
                    contentColor = OnSurfaceVariant,
                    enabled = !isLoadingLogs
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "Refresh logs",
                        modifier = Modifier.alpha(if (isLoadingLogs) 0.5f else 1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(PaddingSmall))

        when {
            isLoadingLogs -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = PaddingLarge),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            logs.isEmpty() -> {
                EmptyLogsState()
            }
            else -> {
                LogsList(logs = logs)
            }
        }
    }
}

@Composable
fun LogsList(logs: List<ScriptLog>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerHigh)
            .border(1.dp, OutlineVariant.copy(alpha = 0.15f))
    ) {
        // Log Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerHighest)
                .padding(horizontal = PaddingMedium, vertical = PaddingSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Success)
                        .clip(CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Warning)
                        .clip(CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Primary)
                        .clip(CircleShape)
                )
            }

            Text(
                text = "System.out",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = OnSurfaceVariant,
                modifier = Modifier.padding(start = PaddingMedium)
            )
        }

        // Log entries
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingMedium)
        ) {
            logs.forEachIndexed { index, log ->
                val isMostRecent = index == logs.size - 1

                LogEntry(
                    log = log,
                    isMostRecent = isMostRecent,
                    showSeparator = index > 0
                )
            }
        }
    }
}

@Composable
fun LogEntry(
    log: ScriptLog,
    isMostRecent: Boolean,
    showSeparator: Boolean
) {
    val statusColor = when (log.status.lowercase()) {
        "success" -> Success
        "error" -> Error
        else -> Primary
    }

    val statusIcon: ImageVector = when (log.status.lowercase()) {
        "success" -> Icons.Default.CheckCircle
        "error" -> Icons.Default.Error
        else -> Icons.Default.Info
    }

    val gutterColor = if (isMostRecent) {
        Primary
    } else {
        SurfaceContainerHighest
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isMostRecent) {
                    SurfaceContainerHigh.copy(alpha = 0.5f)
                } else {
                    Color.Transparent
                }
            )
    ) {
        // Gutter indicator
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(gutterColor)
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PaddingMedium, top = PaddingMedium, bottom = PaddingMedium, end = 0.dp)
        ) {
            // Status and timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PaddingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = log.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }

                Text(
                    text = "${DateUtils.formatDate(log.timestamp)} (Run #${log.runNumber})",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }

            // Output
            Text(
                text = log.output,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = OnSurface
            )

            // Error details if present
            if (log.isError) {
                Spacer(modifier = Modifier.height(PaddingSmall))

                Text(
                    text = "Error occurred during execution",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = Error
                )
            }
        }

        if (showSeparator) {
            Spacer(modifier = Modifier.height(PaddingSmall))

            Text(
                text = "-----------------------------------",
                style = MaterialTheme.typography.bodySmall,
                color = OutlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(horizontal = PaddingMedium)
            )
        }
    }
}

@Composable
fun EmptyLogsState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(PaddingMedium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceContainerHigh)
                .border(1.dp, OutlineVariant.copy(alpha = 0.15f))
                .padding(PaddingExtraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "No logs",
                tint = OnSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.height(PaddingMedium))

            Text(
                text = "No execution logs yet",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface
            )
        }
    }
}

@Composable
fun RunButton(onRunScript: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PaddingLarge, vertical = PaddingMedium)
    ) {
        ScriptlerButton(
            onClick = onRunScript,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = PaddingMedium)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Run",
                    tint = Color(0xFF0A2E10)
                )

                Spacer(modifier = Modifier.width(PaddingSmall))

                Text(
                    text = "Run Now",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF0A2E10)
                )
            }
        }
    }
}
