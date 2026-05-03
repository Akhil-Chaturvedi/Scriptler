package com.bytesmith.scriptler.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bytesmith.scriptler.ui.theme.*

data class CreateScriptDialogState(
    val scriptName: String = "",
    val language: String = "javascript",
    val isValid: Boolean = false
)

data class RenameScriptDialogState(
    val scriptId: String = "",
    val scriptName: String = "",
    val isValid: Boolean = false
)

@Composable
fun LanguageOption(
    @Suppress("UNUSED_PARAMETER") language: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        Primary.copy(alpha = 0.2f)
    } else {
        Color.Transparent
    }

    val borderColor = if (selected) {
        Primary
    } else {
        OutlineVariant
    }

    val textColor = if (selected) {
        OnPrimary
    } else {
        OnSurface
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.height(48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor
            )
        }
    }
}

@Composable
fun ScheduleOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            Primary.copy(alpha = 0.15f)
        } else {
            Color.Transparent
        },
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) OnPrimary else OnSurfaceVariant,
            modifier = Modifier.padding(PaddingSmall)
        )
    }
}

@Composable
fun LanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PaddingSmall)
    ) {
        LanguageOption(
            language = "javascript",
            label = "JavaScript",
            selected = selectedLanguage == "javascript",
            onClick = { onLanguageSelected("javascript") }
        )

        LanguageOption(
            language = "python",
            label = "Python",
            selected = selectedLanguage == "python",
            onClick = { onLanguageSelected("python") }
        )
    }
}

@Composable
fun CreateScriptDialogCompose(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var state by remember { mutableStateOf(CreateScriptDialogState()) }

    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            ScriptlerCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(PaddingLarge)
                ) {
                    // Title
                    Text(
                        text = "Create New Script",
                        style = MaterialTheme.typography.displaySmall,
                        color = OnSurface,
                        modifier = Modifier.padding(bottom = PaddingMedium)
                    )

                    Spacer(modifier = Modifier.height(PaddingMedium))

                    // Script Name
                    Text(
                        text = "Script Name",
                        style = MaterialTheme.typography.labelLarge,
                        color = OnSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(PaddingSmall))

                    ScriptlerTextField(
                        value = state.scriptName,
                        onValueChange = { state = state.copy(scriptName = it) },
                        placeholder = "Enter script name",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(PaddingMedium))

                    // Language
                    Text(
                        text = "Language",
                        style = MaterialTheme.typography.labelLarge,
                        color = OnSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(PaddingSmall))

                    LanguageSelector(
                        selectedLanguage = state.language,
                        onLanguageSelected = { state = state.copy(language = it) }
                    )

                    Spacer(modifier = Modifier.height(PaddingExtraLarge))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PaddingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScriptlerTextButton(
                            onClick = onDismiss
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        ScriptlerButton(
                            onClick = {
                                if (state.scriptName.isNotBlank()) {
                                    onConfirm(state.scriptName, state.language)
                                    state = CreateScriptDialogState()
                                }
                            },
                            enabled = state.scriptName.isNotBlank()
                        ) {
                            Text(
                                text = "Create",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenameScriptDialogCompose(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var state by remember { mutableStateOf(RenameScriptDialogState()) }

    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            ScriptlerCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(PaddingLarge)
                ) {
                    // Title
                    Text(
                        text = "Rename Script",
                        style = MaterialTheme.typography.displaySmall,
                        color = OnSurface,
                        modifier = Modifier.padding(bottom = PaddingMedium)
                    )

                    Spacer(modifier = Modifier.height(PaddingMedium))

                    // Script Name
                    Text(
                        text = "New Name",
                        style = MaterialTheme.typography.labelLarge,
                        color = OnSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(PaddingSmall))

                    ScriptlerTextField(
                        value = state.scriptName,
                        onValueChange = { state = state.copy(scriptName = it) },
                        placeholder = "Enter new script name",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(PaddingExtraLarge))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PaddingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScriptlerTextButton(
                            onClick = onDismiss
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        ScriptlerButton(
                            onClick = {
                                if (state.scriptName.isNotBlank()) {
                                    onConfirm(state.scriptName)
                                    state = RenameScriptDialogState()
                                }
                            },
                            enabled = state.scriptName.isNotBlank()
                        ) {
                            Text(
                                text = "Rename",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleDialogCompose(
    visible: Boolean,
    scheduleType: String,
    scheduleValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf(scheduleType) }
    var selectedValue by remember { mutableStateOf(scheduleValue) }

    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            ScriptlerCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(PaddingLarge)
                ) {
                    // Title
                    Text(
                        text = "Schedule Script",
                        style = MaterialTheme.typography.displaySmall,
                        color = OnSurface,
                        modifier = Modifier.padding(bottom = PaddingMedium)
                    )

                    Spacer(modifier = Modifier.height(PaddingMedium))

                    // Schedule Type
                    Text(
                        text = "Frequency",
                        style = MaterialTheme.typography.labelLarge,
                        color = OnSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(PaddingSmall))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PaddingSmall)
                    ) {
                        ScheduleOption(
                            label = "Once",
                            selected = selectedType == "once",
                            onClick = { selectedType = "once" }
                        )
                        ScheduleOption(
                            label = "Daily",
                            selected = selectedType == "daily",
                            onClick = { selectedType = "daily" }
                        )
                        ScheduleOption(
                            label = "Hourly",
                            selected = selectedType == "hourly",
                            onClick = { selectedType = "hourly" }
                        )
                    }

                    Spacer(modifier = Modifier.height(PaddingMedium))

                    // Schedule Value
                    Text(
                        text = "Value",
                        style = MaterialTheme.typography.labelLarge,
                        color = OnSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(PaddingSmall))

                    ScriptlerTextField(
                        value = selectedValue,
                        onValueChange = { selectedValue = it },
                        placeholder = when (selectedType) {
                            "once" -> "e.g., 12:00"
                            "daily" -> "e.g., 09:00"
                            "hourly" -> "e.g., 00:00"
                            else -> ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(PaddingExtraLarge))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PaddingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScriptlerTextButton(
                            onClick = onDismiss
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        ScriptlerButton(
                            onClick = { onConfirm(selectedType, selectedValue) },
                            enabled = selectedValue.isNotBlank()
                        ) {
                            Text(
                                text = "Set Schedule",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
