package com.bytesmith.scriptler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bytesmith.scriptler.ui.theme.*

@Composable
fun ScriptlerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = SurfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(backgroundColor)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    Column(
        modifier = cardModifier.padding(PaddingMedium),
        content = content
    )
}

@Composable
fun ScriptlerCardWithGutter(
    modifier: Modifier = Modifier,
    gutterColor: Color = Primary,
    backgroundColor: Color = SurfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
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
                .padding(start = PaddingMedium, top = PaddingMedium, bottom = PaddingMedium, end = 0.dp),
            content = content
        )
    }
}

@Composable
fun ScriptlerSettingItem(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    description: String? = null,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
    val itemModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(SurfaceContainerHigh)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    Row(
        modifier = itemModifier.padding(PaddingMedium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PaddingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface
                )
                description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }
        }
        
        trailing()
    }
}
