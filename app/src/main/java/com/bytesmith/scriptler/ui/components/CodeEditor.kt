package com.bytesmith.scriptler.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytesmith.scriptler.ui.theme.*

@Composable
fun CodeEditor(
    code: String,
    onCodeChange: (String) -> Unit,
    language: String = "javascript",
    modifier: Modifier = Modifier
) {
    val textColor = when (language) {
        "javascript" -> Color(0xFFF7DF1E)
        "python" -> Color(0xFF3776AB)
        else -> Primary
    }

    BasicTextField(
        value = code,
        onValueChange = onCodeChange,
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = textColor
        )
    )
}
