package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.agent.memory.MemoryCategory
import com.example.androiddevagent.agent.memory.MemoryViewModel
import com.example.androiddevagent.agent.memory.SmartMemoryEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智能记忆", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = { viewModel.showAddDialog() }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加记忆")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索栏
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.searchMemories(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )

            // 类别筛选条
            CategoryFilterChips(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = { viewModel.selectCategory(it) }
            )

            // 记忆列表
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredMemories.isEmpty()) {
                EmptyMemoryState()
            } else {
                val groupedMemories = uiState.filteredMemories.groupBy { it.category }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groupedMemories.forEach { (category, memories) ->
                        val categoryEnum = MemoryCategory.fromName(category)
                        item(key = "header_$category") {
                            CategoryHeader(category = categoryEnum, count = memories.size)
                        }
                        items(memories, key = { it.id }) { memory ->
                            MemoryCard(
                                memory = memory,
                                category = categoryEnum,
                                onDelete = { viewModel.deleteMemory(memory.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // 添加记忆对话框
    if (uiState.isAdding) {
        AddMemoryDialog(
            content = uiState.newMemoryContent,
            onContentChange = { viewModel.updateNewMemoryContent(it) },
            category = uiState.newMemoryCategory,
            onCategoryChange = { viewModel.updateNewMemoryCategory(it) },
            importance = uiState.newMemoryImportance,
            onImportanceChange = { viewModel.updateNewMemoryImportance(it) },
            onConfirm = { viewModel.addMemory() },
            onDismiss = { viewModel.hideAddDialog() }
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("搜索记忆...", style = MaterialTheme.typography.bodySmall) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Clear, contentDescription = "清除", modifier = Modifier.size(16.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun CategoryFilterChips(
    selectedCategory: MemoryCategory?,
    onCategorySelected: (MemoryCategory?) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = if (selectedCategory == null) 0 else selectedCategory.ordinal + 1,
        modifier = Modifier.fillMaxWidth(),
        edgePadding = 12.dp,
        divider = {}
    ) {
        // 全部
        Tab(
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            text = { Text("全部", style = MaterialTheme.typography.labelSmall) },
            icon = { Icon(Icons.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        // 各类别
        MemoryCategory.entries.forEach { category ->
            Tab(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                text = { Text(category.displayName, style = MaterialTheme.typography.labelSmall) },
                icon = {
                    Icon(
                        categoryIcon(category),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun CategoryHeader(category: MemoryCategory, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            categoryIcon(category),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = categoryColor(category)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            category.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = categoryColor(category),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "$count 条",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MemoryCard(
    memory: SmartMemoryEntity,
    category: MemoryCategory,
    onDelete: () -> Unit
) {
    val timeFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINESE)
    val timeStr = timeFormat.format(Date(memory.createdAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // 类别徽章
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = categoryColor(category).copy(alpha = 0.15f),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        category.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor(category),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // 内容预览
                Text(
                    memory.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // 删除按钮
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "删除",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 标签
            if (memory.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    memory.tags.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (memory.tags.size > 3) {
                        Text(
                            "+${memory.tags.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 底部信息行
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 时间
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // 重要性指示器
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (memory.importance >= 0.7f) Icons.Filled.Star
                        else if (memory.importance >= 0.4f) Icons.Filled.StarHalf
                        else Icons.Filled.StarOutline,
                        contentDescription = "重要性",
                        modifier = Modifier.size(12.dp),
                        tint = importanceColor(memory.importance)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        String.format("%.0f%%", memory.importance * 100),
                        style = MaterialTheme.typography.labelSmall,
                        color = importanceColor(memory.importance)
                    )
                }

                // 访问次数
                if (memory.accessCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "${memory.accessCount}次",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMemoryState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "暂无记忆",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "AI 助手会在交互中自动学习并存储记忆",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "您也可以点击 + 手动添加",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemoryDialog(
    content: String,
    onContentChange: (String) -> Unit,
    category: MemoryCategory,
    onCategoryChange: (MemoryCategory) -> Unit,
    importance: Float,
    onImportanceChange: (Float) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加记忆", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 内容输入
                OutlinedTextField(
                    value = content,
                    onValueChange = onContentChange,
                    label = { Text("记忆内容") },
                    placeholder = { Text("输入要记住的内容...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodySmall
                )

                // 类别选择
                Text("类别", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MemoryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { onCategoryChange(cat) },
                            label = { Text(cat.displayName, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    categoryIcon(cat),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }

                // 重要性滑块
                Text("重要性: ${String.format("%.0f%%", importance * 100)}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = importance,
                    onValueChange = onImportanceChange,
                    valueRange = 0f..1f,
                    steps = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = content.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun categoryIcon(category: MemoryCategory) = when (category) {
    MemoryCategory.PREFERENCE -> Icons.Filled.Favorite
    MemoryCategory.FACT -> Icons.Filled.Info
    MemoryCategory.INSTRUCTION -> Icons.Filled.Assignment
    MemoryCategory.CONTEXT -> Icons.Filled.MyLocation
    MemoryCategory.ERROR_SOLUTION -> Icons.Filled.Build
}

@Composable
private fun categoryColor(category: MemoryCategory) = when (category) {
    MemoryCategory.PREFERENCE -> MaterialTheme.colorScheme.tertiary
    MemoryCategory.FACT -> MaterialTheme.colorScheme.primary
    MemoryCategory.INSTRUCTION -> MaterialTheme.colorScheme.secondary
    MemoryCategory.CONTEXT -> MaterialTheme.colorScheme.primaryContainer
    MemoryCategory.ERROR_SOLUTION -> MaterialTheme.colorScheme.error
}

private fun importanceColor(importance: Float) = when {
    importance >= 0.7f -> androidx.compose.ui.graphics.Color(0xFFFF9800)
    importance >= 0.4f -> androidx.compose.ui.graphics.Color(0xFF66BB6A)
    else -> androidx.compose.ui.graphics.Color(0xFFBDBDBD)
}
