package com.example.androiddevagent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.agent.skills.SkillSearchResult
import com.example.androiddevagent.data.SkillEntity
import com.example.androiddevagent.ui.theme.DerekGradientEnd
import com.example.androiddevagent.ui.theme.DerekGradientStart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillScreen(
    viewModel: SkillViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showInstallDialog by remember { mutableStateOf(false) }
    var installSource by remember { mutableStateOf("github") }
    var installRepo by remember { mutableStateOf("") }
    var installBranch by remember { mutableStateOf("main") }

    LaunchedEffect(uiState.success, uiState.error) {
        if (uiState.success.isNotEmpty() || uiState.error.isNotEmpty()) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("技能市场") },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { showInstallDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "手动安装")
                    }
                    IconButton(onClick = { viewModel.loadSkills() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
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
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchSkills(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索技能...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.searchSkills("")
                            }) {
                                Icon(Icons.Filled.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
            }

            if (uiState.error.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(uiState.error, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (uiState.success.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(uiState.success, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (searchQuery.isNotEmpty() && uiState.searchResults.isNotEmpty()) {
                    item {
                        Text("搜索结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    items(uiState.searchResults) { skill ->
                        SkillSearchResultCard(
                            skill = skill,
                            isInstalling = uiState.isInstalling && uiState.installingSkillId == skill.id,
                            onInstall = {
                                val source = if (skill.sourceUrl.contains("github.com")) "github" else "url"
                                val repo = if (source == "github") skill.sourceUrl.removePrefix("https://github.com/") else skill.sourceUrl
                                viewModel.installSkill(source, repo)
                            }
                        )
                    }
                } else if (searchQuery.isNotEmpty() && !uiState.isSearching && uiState.searchResults.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("未找到匹配 '$searchQuery' 的技能", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (searchQuery.isEmpty()) {
                    if (uiState.recommendedSkills.isNotEmpty()) {
                        item {
                            Text("推荐技能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(uiState.recommendedSkills) { skill ->
                                    RecommendedSkillCard(
                                        skill = skill,
                                        isInstalling = uiState.isInstalling && uiState.installingSkillId == skill.id,
                                        onInstall = {
                                            val source = if (skill.sourceUrl.contains("github.com")) "github" else "url"
                                            val repo = if (source == "github") skill.sourceUrl.removePrefix("https://github.com/") else skill.sourceUrl
                                            viewModel.installSkill(source, repo)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.installedSkills.isNotEmpty()) {
                        item {
                            Text("已安装 (${uiState.installedSkills.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                        }
                        items(uiState.installedSkills) { skill ->
                            InstalledSkillCard(
                                skill = skill,
                                onToggle = { viewModel.toggleSkill(skill.id, it) },
                                onUninstall = { viewModel.uninstallSkill(skill.id) },
                                onUpdate = { viewModel.updateSkill(skill.id) }
                            )
                        }
                    } else {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Extension, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("尚未安装任何技能", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("从推荐技能中选择安装，或手动输入技能地址", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInstallDialog) {
        AlertDialog(
            onDismissRequest = { showInstallDialog = false },
            title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("手动安装技能") } },
            text = {
                Column {
                    Text("输入技能来源地址，DEREK 将自动下载安装", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = installSource == "github", onClick = { installSource = "github" }, label = { Text("GitHub") })
                        FilterChip(selected = installSource == "url", onClick = { installSource = "url" }, label = { Text("URL") })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = installRepo,
                        onValueChange = { installRepo = it },
                        label = { Text(if (installSource == "github") "仓库路径" else "技能 URL") },
                        placeholder = { Text(if (installSource == "github") "derek-skills/web-scraper" else "https://example.com/skill.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (installSource == "github") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = installBranch,
                            onValueChange = { installBranch = it },
                            label = { Text("分支") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.installSkill(installSource, installRepo, installBranch)
                        showInstallDialog = false
                        installRepo = ""
                    },
                    enabled = installRepo.isNotBlank()
                ) { Text("安装") }
            },
            dismissButton = { TextButton(onClick = { showInstallDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun RecommendedSkillCard(
    skill: SkillSearchResult,
    isInstalling: Boolean,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(skill.icon, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(skill.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(skill.description.take(40), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            if (skill.installed) {
                Text("已安装", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            } else {
                FilledTonalButton(
                    onClick = onInstall,
                    enabled = !isInstalling,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    if (isInstalling) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.dp)
                    } else {
                        Text("安装", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillSearchResultCard(
    skill: SkillSearchResult,
    isInstalling: Boolean,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(skill.icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(skill.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("⭐${skill.stars}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(skill.description.take(80), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(skill.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
            }
            if (skill.installed) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "已安装", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            } else {
                FilledTonalButton(onClick = onInstall, enabled = !isInstalling, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    if (isInstalling) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.dp)
                    } else {
                        Text("安装", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledSkillCard(
    skill: SkillEntity,
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit
) {
    var showActions by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (skill.enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showActions = !showActions }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(skill.icon, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(skill.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("v${skill.version}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    }
                    Text(skill.description.take(60), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row {
                        skill.toolNames.take(3).forEach { tool ->
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.padding(end = 4.dp)) {
                                Text(tool, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        if (skill.toolNames.size > 3) {
                            Text("+${skill.toolNames.size - 3}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Switch(checked = skill.enabled, onCheckedChange = onToggle, modifier = Modifier.size(36.dp))
            }
            AnimatedVisibility(visible = showActions) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onUpdate, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("更新", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onUninstall,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("卸载", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
