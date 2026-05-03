package com.bytesmith.scriptler.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bytesmith.scriptler.models.Script
import com.bytesmith.scriptler.ui.components.*
import com.bytesmith.scriptler.ui.theme.*

// Import common states to avoid overload resolution ambiguity
import com.bytesmith.scriptler.ui.components.CommonLoadingState
import com.bytesmith.scriptler.ui.components.CommonErrorState as CommonErrorState
import com.bytesmith.scriptler.ui.components.CreateScriptDialogCompose

@Composable
fun ScriptsListScreenCompose(
    scripts: List<Script> = emptyList(),
    isLoading: Boolean = false,
    isCreating: Boolean = false,
    isDeleting: Boolean = false,
    isRenaming: Boolean = false,
    error: String? = null,
    onCreateScript: (String, String) -> Unit = { _, _ -> },
    onScriptClick: (Script) -> Unit = {},
    onScriptToggle: (Script) -> Unit = {},
    onScriptEdit: (Script) -> Unit = {},
    onScriptDelete: (Script) -> Unit = {},
    onScriptRename: (Script, String) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    onNavigateToPackages: () -> Unit = {},
    onClearError: () -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var scriptToRename by remember { mutableStateOf<Script?>(null) }
    Scaffold(
        topBar = {
            ScriptlerTopAppBar(
                title = "Scriptler",
                navigationIcon = null,
                onNavigationClick = null,
                actions = {
                    ScriptlerIconButton(
                        onClick = onNavigateToPackages,
                        containerColor = Color.Transparent,
                        contentColor = OnSurface
                    ) {
                        Icon(Icons.Default.Inventory, contentDescription = "Packages")
                    }
                    ScriptlerIconButton(
                        onClick = onNavigateToSettings,
                        containerColor = Color.Transparent,
                        contentColor = OnSurface
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        containerColor = Surface,
        floatingActionButton = {
            ScriptlerFloatingActionButton(
                onClick = { showCreateDialog = true },
                enabled = !isCreating && !isDeleting && !isRenaming
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CommonLoadingState("Loading scripts...")
                }
                error != null -> {
                    CommonErrorState(
                        message = error,
                        onRetry = onClearError
                    )
                }
                scripts.isEmpty() -> {
                    EmptyState(
                        onCreateScript = { showCreateDialog = true }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(PaddingMedium, PaddingMedium, PaddingLarge, PaddingMedium),
                        verticalArrangement = Arrangement.spacedBy(PaddingSmall)
                    ) {
                        items(scripts) { script ->
                            ScriptCard(
                                script = script,
                                isDeleting = isDeleting,
                                isRenaming = isRenaming,
                                onClick = { onScriptClick(script) },
                                onToggle = { onScriptToggle(script) },
                                onEdit = { onScriptEdit(script) },
                                onDelete = { onScriptDelete(script) },
                                onRename = {
                                    scriptToRename = script
                                    showRenameDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // Create Script Dialog
            if (showCreateDialog) {
                CreateScriptDialogCompose(
                    visible = true,
                    onDismiss = { showCreateDialog = false },
                    onConfirm = { name, language ->
                        onCreateScript(name, language)
                        showCreateDialog = false
                    }
                )
            }

            // Rename Script Dialog
            if (showRenameDialog && scriptToRename != null) {
                RenameScriptDialogCompose(
                    visible = true,
                    onDismiss = { showRenameDialog = false },
                    onConfirm = { newName ->
                        onScriptRename(scriptToRename!!, newName)
                        showRenameDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun ScriptCard(
    script: Script,
    isDeleting: Boolean = false,
    isRenaming: Boolean = false,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    val isActive = script.isActive && script.scheduleType != "none"
    var showMenu by remember { mutableStateOf(false) }

    val languageColor = when (script.language) {
        "javascript" -> Color(0xFFF7DF1E)
        "python" -> Color(0xFF3776AB)
        else -> Primary
    }

    val languageBadge = when (script.language) {
        "javascript" -> "JS"
        "python" -> "PY"
        else -> script.language.take(2).uppercase()
    }

    val cardBackgroundColor = if (isActive) {
        Primary.copy(alpha = 0.08f)
    } else {
        Surface
    }

    val gutterColor = if (isActive) {
        Primary
    } else {
        Color.Transparent
    }

    val textColor = if (isActive) {
        OnSurface
    } else {
        OnSurfaceVariant
    }

    val statusColor = OnSurfaceVariant

    ScriptlerCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                enabled = !isDeleting && !isRenaming
            ),
        backgroundColor = cardBackgroundColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gutter indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(80.dp)
                    .background(gutterColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(PaddingMedium)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = script.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(PaddingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Language badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = languageColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = languageBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = languageColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Toggle active button
                        ScriptlerIconButton(
                            onClick = onToggle,
                            containerColor = if (isActive) {
                                Success.copy(alpha = 0.15f)
                            } else {
                                OnSurfaceVariant.copy(alpha = 0.1f)
                            },
                            contentColor = if (isActive) Success else OnSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isActive) "Stop script" else "Run script",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Overflow menu
                        Box {
                            ScriptlerIconButton(
                                onClick = { showMenu = true },
                                containerColor = Color.Transparent,
                                contentColor = OnSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        showMenu = false
                                        onEdit()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        showMenu = false
                                        onRename()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = Error
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(PaddingSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PaddingMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Schedule info
                    if (script.scheduleType != "none") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = script.scheduleType,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                        }
                    }

                    // Last run
                    if (script.lastRun > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = formatRelativeTime(script.lastRun),
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor
                            )
                        }
                    }
                }

                if (isActive) {
                    Spacer(modifier = Modifier.height(PaddingSmall))

                    // Status indicator
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Success, CircleShape)
                        )
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = Success
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    onCreateScript: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(PaddingLarge)
        ) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = "No scripts",
                tint = OnSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(PaddingMedium))

            Text(
                text = "No scripts yet",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface
            )

            Spacer(modifier = Modifier.height(PaddingSmall))

            Text(
                text = "Create your first script to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(PaddingLarge))

            ScriptlerButton(
                onClick = onCreateScript
            ) {
                Text(
                    text = "Create Script",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ScriptlerFloatingActionButton(
    onClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(
                        Primary,
                        PrimaryLight
                    )
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Create script",
            tint = OnPrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}

// Helper function for relative time formatting
private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> "${diff / 86400_000}d ago"
    }
}
