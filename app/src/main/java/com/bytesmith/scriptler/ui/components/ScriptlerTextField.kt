package com.bytesmith.scriptler.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bytesmith.scriptler.ui.theme.*

@Composable
fun ScriptlerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    @Suppress("UNUSED_PARAMETER") imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    readOnly: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        label = if (label != null) { { Text(label) } } else null,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        keyboardActions = KeyboardActions(
            onDone = {
                onImeAction?.invoke()
                defaultKeyboardAction(ImeAction.Done)
            }
        ),
        readOnly = readOnly,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Primary,
            unfocusedIndicatorColor = OutlineVariant,
            focusedContainerColor = SurfaceContainerHigh,
            unfocusedContainerColor = SurfaceContainerHigh,
            focusedTextColor = OnSurface,
            unfocusedTextColor = OnSurface,
            cursorColor = Primary
        ),
        shape = RoundedCornerShape(8.dp),
        trailingIcon = trailingIcon
    )
}

@Composable
fun ScriptlerMonoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    singleLine: Boolean = true,
    @Suppress("UNUSED_PARAMETER") imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    readOnly: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        label = if (label != null) { { Text(label) } } else null,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        keyboardActions = KeyboardActions(
            onDone = {
                onImeAction?.invoke()
                defaultKeyboardAction(ImeAction.Done)
            }
        ),
        readOnly = readOnly,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Primary,
            unfocusedIndicatorColor = OutlineVariant,
            focusedContainerColor = SurfaceContainerHigh,
            unfocusedContainerColor = SurfaceContainerHigh,
            focusedTextColor = OnSurface,
            unfocusedTextColor = OnSurface,
            cursorColor = Primary
        ),
        shape = RoundedCornerShape(8.dp)
    )
}
