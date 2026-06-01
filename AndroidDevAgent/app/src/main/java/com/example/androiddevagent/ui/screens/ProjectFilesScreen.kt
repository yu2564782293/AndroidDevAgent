package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val size: Long,
    val children: List<FileNode> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectFilesScreen(
    viewModel: ProjectFilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("项目文件", style = MaterialTheme.typography.titleMedium)
                        if (uiState.projectPath.isNotEmpty()) {
                            Text(
                                uiState.projectPath.substringAfterLast("/"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.projectPath.isEmpty()) {
                        TextButton(onClick = { viewModel.selectProject() }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("打开")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.projectPath.isEmpty()) {
            EmptyProjectState(onSelectProject = { viewModel.selectProject() })
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchFiles(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text("搜索文件...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        items(uiState.files) { node ->
                            FileNodeItem(
                                node = node,
                                depth = 0,
                                onClick = {
                                    if (node.isDirectory) {
                                        viewModel.toggleDirectory(node.path)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyProjectState(onSelectProject: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.FolderOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "未选择项目",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "请选择一个 Android 项目目录以浏览文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onSelectProject) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("选择项目")
            }
        }
    }
}

@Composable
private fun FileNodeItem(
    node: FileNode,
    depth: Int,
    onClick: () -> Unit
) {
    val icon = when {
        node.isDirectory -> Icons.Filled.Folder
        node.name.endsWith(".kt") -> Icons.Filled.Code
        node.name.endsWith(".java") -> Icons.Filled.Code
        node.name.endsWith(".xml") -> Icons.Filled.DataObject
        node.name.endsWith(".gradle") || node.name.endsWith(".kts") -> Icons.Filled.Settings
        node.name.endsWith(".png") || node.name.endsWith(".jpg") || node.name.endsWith(".webp") -> Icons.Filled.Image
        else -> Icons.Filled.Description
    }

    val iconColor = when {
        node.isDirectory -> MaterialTheme.colorScheme.primary
        node.name.endsWith(".kt") || node.name.endsWith(".java") -> MaterialTheme.colorScheme.tertiary
        node.name.endsWith(".xml") -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val timeFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINESE)
    val timeStr = timeFormat.format(Date(node.lastModified))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(start = (12 + depth * 20).dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = iconColor)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    node.name,
                    style = if (node.isDirectory) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!node.isDirectory) {
                    Text(
                        "${formatFileSize(node.size)} · $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            if (node.isDirectory) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> "${size / (1024 * 1024)} MB"
    }
}
