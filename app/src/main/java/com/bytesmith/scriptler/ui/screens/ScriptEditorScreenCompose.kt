package com.bytesmith.scriptler.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytesmith.scriptler.models.Script
import com.bytesmith.scriptler.ui.components.*
import com.bytesmith.scriptler.ui.theme.*
import com.bytesmith.scriptler.ui.components.CreateScriptDialogCompose
import com.bytesmith.scriptler.ui.components.ScheduleDialogCompose

// Import common states to avoid overload resolution ambiguity
import com.bytesmith.scriptler.ui.components.CommonLoadingState
import com.bytesmith.scriptler.ui.components.CommonErrorState as CommonErrorState

@Composable
fun ScriptEditorScreenCompose(
    scriptName: String,
    language: String,
    code: String,
    scheduleType: String,
    scheduleValue: String,
    isLoading: Boolean = false,
    isSaving: Boolean = false,
    error: String? = null,
    saveSuccess: Boolean = false,
    onUpdateScriptName: (String) -> Unit = {},
    onUpdateLanguage: (String) -> Unit = {},
    onUpdateCode: (String) -> Unit = {},
    onUpdateScheduleType: (String) -> Unit = {},
    onUpdateScheduleValue: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onCancel: () -> Unit = {},
    onClearError: () -> Unit = {},
    onClearSaveSuccess: () -> Unit = {}
) {
    var showScheduleDialog by remember { mutableStateOf(false) }
    val hasChanges = code.isNotEmpty() || scriptName.isNotEmpty()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            ScriptlerCenteredTopAppBar(
                title = if (scriptName.isEmpty()) "New Script" else "Edit $scriptName",
                leftAction = {
                    ScriptlerIconButton(
                        onClick = onCancel,
                        containerColor = Color.Transparent,
                        contentColor = OnSurface
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                rightAction = {
                    ScriptlerIconButton(
                        onClick = onSave,
                        containerColor = Primary.copy(alpha = 0.1f),
                        contentColor = OnPrimary,
                        enabled = hasChanges && !isSaving && !isLoading
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = OnPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                }
            )
        },
        containerColor = Surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(PaddingMedium)
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
                else -> {
                    // 1. Metadata Section (Name, Language, Schedule)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PaddingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScriptlerTextField(
                            value = scriptName,
                            onValueChange = onUpdateScriptName,
                            placeholder = "Script Name",
                            modifier = Modifier.weight(1f)
                        )

                        LanguageSelector(
                            selectedLanguage = language,
                            onLanguageSelected = onUpdateLanguage
                        )

                        ScheduleButton(
                            scheduleType = scheduleType,
                            scheduleValue = scheduleValue,
                            modifier = Modifier.clickable { showScheduleDialog = true }
                        )
                    }

                    // 2. Code Editor Section
                    ScriptlerCard(modifier = Modifier.fillMaxWidth()) {
                        CodeEditor(
                            code = code,
                            onCodeChange = onUpdateCode,
                            language = language,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(450.dp) 
                        )
                    }

                    // 3. Save Success Message
                    if (saveSuccess) {
                        LaunchedEffect(saveSuccess) {
                            kotlinx.coroutines.delay(2000)
                            onClearSaveSuccess()
                        }
                        
                        ScriptlerCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Success.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(PaddingMedium),
                                horizontalArrangement = Arrangement.spacedBy(PaddingSmall),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Success
                                )
                                Text(
                                    text = "Script saved successfully!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Success
                                )
                            }
                        }
                    }
                }
            }

            // Schedule Dialog
            if (showScheduleDialog) {
                ScheduleDialogCompose(
                    visible = true,
                    scheduleType = scheduleType,
                    scheduleValue = scheduleValue,
                    onDismiss = { showScheduleDialog = false },
                    onConfirm = { type, value ->
                        onUpdateScheduleType(type)
                        onUpdateScheduleValue(value)
                        showScheduleDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun LanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PaddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LanguageOption(
            label = "JavaScript",
            selected = selectedLanguage.lowercase() == "javascript",
            onClick = { onLanguageSelected("javascript") }
        )

        LanguageOption(
            label = "Python",
            selected = selectedLanguage.lowercase() == "python",
            onClick = { onLanguageSelected("python") }
        )
    }
}

@Composable
fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) Primary.copy(alpha = 0.2f) else Color.Transparent
    val borderColor = if (selected) Primary else OutlineVariant
    val textColor = if (selected) OnPrimary else OnSurface

    Surface(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        onClick = onClick
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = PaddingMedium)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor
            )
        }
    }
}