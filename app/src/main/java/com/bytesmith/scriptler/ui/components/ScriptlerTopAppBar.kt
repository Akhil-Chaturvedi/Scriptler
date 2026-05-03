package com.bytesmith.scriptler.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bytesmith.scriptler.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptlerTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface
            )
        },
        modifier = modifier,
        navigationIcon = if (navigationIcon != null && onNavigationClick != null) {
            {
                IconButton(onClick = onNavigationClick) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = "Back",
                        tint = OnSurface
                    )
                }
            }
        } else {
            {}
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = OnSurface,
            navigationIconContentColor = OnSurface,
            actionIconContentColor = OnSurface
        ),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptlerCenteredTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    leftAction: @Composable () -> Unit = {},
    rightAction: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface
            )
        },
        modifier = modifier,
        navigationIcon = {
            leftAction()
        },
        actions = {
            rightAction()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = OnSurface,
            navigationIconContentColor = OnSurface,
            actionIconContentColor = OnSurface
        ),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}
