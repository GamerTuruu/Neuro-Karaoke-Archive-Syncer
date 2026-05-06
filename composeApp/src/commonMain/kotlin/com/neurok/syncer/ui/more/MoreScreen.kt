package com.neurok.syncer.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
fun MoreScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    Column(modifier.fillMaxSize()) {
        // Header
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Column {
                    Text("Neuro Karaoke Archive Syncer", style = MaterialTheme.typography.titleLarge)
                    Text("v1.0.0", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalDivider()

        MoreTile(
            icon = { Icon(Icons.Filled.Settings, null) },
            title = "Settings",
            subtitle = "Folder, schedule, API keys, advanced",
            onClick = onNavigateToSettings,
        )

        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

        MoreTile(
            icon = { Icon(Icons.Filled.Code, null) },
            title = "GitHub",
            subtitle = "View the source code and report issues",
            onClick = { uriHandler.openUri("https://github.com/GamerTuruu/Neuro-Karaoke-Archive-Syncer") },
        )

        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

        MoreTile(
            icon = { Icon(Icons.Filled.Info, null) },
            title = "Metadata Repository",
            subtitle = "Nyss777/Neuro-Karaoke-Archive-Metadata on GitHub",
            onClick = { uriHandler.openUri("https://github.com/Nyss777/Neuro-Karaoke-Archive-Metadata") },
        )

        HorizontalDivider()
    }
}

@Composable
private fun MoreTile(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
