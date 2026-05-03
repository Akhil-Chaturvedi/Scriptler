package com.bytesmith.scriptler.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bytesmith.scriptler.ui.components.*
import com.bytesmith.scriptler.ui.theme.*

// Import common states to avoid overload resolution ambiguity
import com.bytesmith.scriptler.ui.components.CommonLoadingState
import com.bytesmith.scriptler.ui.components.CommonErrorState

data class PackageInfo(
    val name: String,
    val version: String,
    val isNative: Boolean,
    val isRuntime: Boolean = false
)

@Composable
fun PackageManagerScreenCompose(
    prebundledPackages: List<PackageInfo> = emptyList(),
    runtimePackages: List<PackageInfo> = emptyList(),
    searchResults: List<PackageInfo> = emptyList(),
    @Suppress("UNUSED_PARAMETER") packageCount: Int = 0,
    packageSize: Long = 0L,
    isLoading: Boolean = false,
    isSearching: Boolean = false,
    isInstalling: Boolean = false,
    isUninstalling: Boolean = false,
    error: String? = null,
    onSearch: (String) -> Unit = {},
    onInstallPackage: (String) -> Unit = {},
    onUninstallPackage: (String) -> Unit = {},
    onNavigateToScripts: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onNavigateToSettings: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onClearError: () -> Unit = {},
    onClearSearchResults: () -> Unit = {}
) {
    var searchText by remember { mutableStateOf("") }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    Scaffold(
        topBar = {
            ScriptlerTopAppBar(
                title = "Package Manager",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onNavigateToScripts,
                actions = {
                    if (!isLoading && !isSearching && !isInstalling && !isUninstalling) {
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                isLoading -> {
                    CommonLoadingState("Loading packages...")
                }
                error != null -> {
                    CommonErrorState(
                        message = error,
                        onRetry = onClearError
                    )
                }
                else -> {
                    // Single LazyColumn handles all scrolling for the entire screen
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(PaddingMedium),
                        verticalArrangement = Arrangement.spacedBy(PaddingMedium)
                    ) {
                        // Title and Storage Info
                        item {
                            Column {
                                Text(
                                    text = "Package Manager",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = OnSurface
                                )
                                Spacer(modifier = Modifier.height(PaddingSmall))
                                Text(
                                    text = "Runtime packages: ${formatFileSize(packageSize)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant
                                )
                            }
                        }

                        // Search Bar
                        item {
                            ScriptlerTextField(
                                value = searchText,
                                onValueChange = {
                                    searchText = it
                                    if (it.isEmpty()) onClearSearchResults()
                                },
                                placeholder = "Search packages...",
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    if (isSearching) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        ScriptlerIconButton(
                                            onClick = { onSearch(searchText) },
                                            containerColor = Primary,
                                            contentColor = OnPrimary
                                        ) {
                                            Icon(Icons.Default.Search, contentDescription = "Search")
                                        }
                                    }
                                }
                            )
                        }

                        // Results Logic
                        if (searchResults.isNotEmpty()) {
                            item { SectionHeader("SEARCH RESULTS") }
                            items(searchResults) { pkg ->
                                PackageCard(
                                    name = pkg.name,
                                    version = pkg.version,
                                    isNative = pkg.isNative,
                                    isRuntime = pkg.isRuntime,
                                    isProcessing = isInstalling,
                                    onInstall = { onInstallPackage(pkg.name) }
                                )
                            }
                        } else {
                            if (prebundledPackages.isNotEmpty()) {
                                item { SectionHeader("PRE-BUNDLED") }
                                items(prebundledPackages) { pkg ->
                                    PackageCard(
                                        name = pkg.name,
                                        version = pkg.version,
                                        isNative = pkg.isNative,
                                        isRuntime = pkg.isRuntime
                                    )
                                }
                            }

                            if (runtimePackages.isNotEmpty()) {
                                item { SectionHeader("INSTALLED AT RUNTIME") }
                                items(runtimePackages) { pkg ->
                                    PackageCard(
                                        name = pkg.name,
                                        version = pkg.version,
                                        isNative = pkg.isNative,
                                        isRuntime = pkg.isRuntime,
                                        isProcessing = isUninstalling,
                                        onUninstall = { onUninstallPackage(pkg.name) }
                                    )
                                }
                            }

                            if (prebundledPackages.isEmpty() && runtimePackages.isEmpty()) {
                                item { EmptyPackageState() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = OnSurfaceVariant,
        modifier = Modifier.padding(top = PaddingSmall, bottom = 4.dp)
    )
}

@Composable
fun PackageCard(
    name: String,
    @Suppress("UNUSED_PARAMETER") version: String,
    isNative: Boolean,
    isRuntime: Boolean,
    isProcessing: Boolean = false,
    onInstall: (() -> Unit)? = null,
    onUninstall: (() -> Unit)? = null
) {
    val cardColor = if (isRuntime) SurfaceContainerHigh.copy(alpha = 0.8f) else SurfaceContainerHigh
    val badgeColor = if (isNative) Error else Primary
    val badgeText = when {
        isNative -> "Native"
        isRuntime -> "Runtime"
        else -> "Pure"
    }
    val badgeBackgroundColor = when {
        isNative -> ErrorContainer.copy(alpha = 0.2f)
        isRuntime -> Primary.copy(alpha = 0.2f)
        else -> SurfaceContainerHighest
    }

    ScriptlerCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300, easing = FastOutSlowInEasing)),
        backgroundColor = cardColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(PaddingSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(PaddingMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Surface(shape = RoundedCornerShape(4.dp), color = badgeBackgroundColor) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Buttons based on available actions
            when {
                onInstall != null -> {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        ScriptlerTextButton(onClick = onInstall) { Text("Install") }
                    }
                }
                onUninstall != null -> {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        ScriptlerIconButton(onClick = onUninstall, contentColor = Error) {
                            Icon(Icons.Default.Delete, contentDescription = "Uninstall")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyPackageState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = PaddingExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(PaddingMedium))
        Text("No packages found", style = MaterialTheme.typography.titleMedium, color = OnSurface)
        Text("Search above to install packages from PyPI", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
    }
}

@Composable
fun PackageManagerErrorState(message: String, onRetry: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PaddingMedium),
            modifier = Modifier.padding(PaddingLarge)
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = Error, modifier = Modifier.size(64.dp))
            Text("Oops! Something went wrong", style = MaterialTheme.typography.titleLarge, color = OnSurface)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
            ScriptlerButton(onClick = onRetry) { Text("Try Again") }
        }
    }
}