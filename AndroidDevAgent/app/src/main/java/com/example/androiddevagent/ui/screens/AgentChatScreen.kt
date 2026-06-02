package com.example.androiddevagent.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.agent.events.AgentEvent
import com.example.androiddevagent.agent.vcs.GitHubUserRepo
import com.example.androiddevagent.agent.vcs.GitHubUserInfo
import com.example.androiddevagent.ui.theme.DerekGradientEnd
import com.example.androiddevagent.ui.theme.DerekGradientStart
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    viewModel: AgentChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    var showCloneDialog by remember { mutableStateOf(false) }
    var showGitHubDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val path = getRealPathFromUri(context, it)
            if (path != null) {
                viewModel.setProjectPath(path)
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val path = getFilePathFromUri(context, it)
            if (path != null) {
                inputText = if (inputText.isBlank()) "[图片: $path]" else "$inputText\n[图片: $path]"
            } else {
                inputText = if (inputText.isBlank()) "[图片: ${it.lastPathSegment}]" else "$inputText\n[图片: ${it.lastPathSegment}]"
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val path = getFilePathFromUri(context, it)
            if (path != null) {
                val file = java.io.File(path)
                if (file.exists() && file.length() < 50000) {
                    try {
                        val content = file.readText().take(2000)
                        inputText = if (inputText.isBlank()) "[文件: $path]\n$content" else "$inputText\n[文件: $path]\n$content"
                    } catch (_: Exception) {
                        inputText = if (inputText.isBlank()) "[文件: $path]" else "$inputText\n[文件: $path]"
                    }
                } else {
                    inputText = if (inputText.isBlank()) "[文件: $path]" else "$inputText\n[文件: $path]"
                }
            } else {
                inputText = if (inputText.isBlank()) "[文件: ${it.lastPathSegment}]" else "$inputText\n[文件: ${it.lastPathSegment}]"
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spoken = results[0]
                inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
            }
        }
    }

    if (showCloneDialog) {
        CloneRepoDialog(
            onDismiss = { showCloneDialog = false },
            onClone = { url, directory ->
                viewModel.cloneRepo(url, directory)
                showCloneDialog = false
            },
            defaultDirectory = "/sdcard/Projects"
        )
    }

    if (showGitHubDialog) {
        ConnectGitHubDialog(
            onDismiss = { showGitHubDialog = false },
            onSelectRepo = { viewModel.selectGitHubRepo(it); showGitHubDialog = false },
            onDisconnect = {
                viewModel.disconnectGitHubRepo()
            },
            onLoadRepos = { viewModel.loadGitHubRepos() },
            currentRepo = uiState.githubRepo,
            isConnected = uiState.githubConnected,
            userInfo = uiState.githubUserInfo,
            repoList = uiState.githubRepoList,
            isLoading = uiState.githubLoading,
            error = uiState.githubError
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(DerekGradientStart, DerekGradientEnd),
                                        start = Offset.Zero,
                                        end = Offset(32f, 32f)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "DEREK AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.projectPath.isNotEmpty()) {
                                Text(
                                    text = uiState.projectPath.substringAfterLast("/"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (uiState.isRunning) {
                        FilledTonalButton(
                            onClick = { viewModel.stopAgent() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("停止", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多", modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (uiState.projectPath.isNotEmpty() && !uiState.isRunning) {
                                DropdownMenuItem(
                                    text = { Text("构建项目") },
                                    onClick = {
                                        viewModel.triggerBuild()
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("克隆仓库") },
                                onClick = {
                                    showCloneDialog = true
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (uiState.githubConnected) "GitHub: ${uiState.githubRepo}" else "连接 GitHub 仓库") },
                                onClick = {
                                    showGitHubDialog = true
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            if (uiState.projectPath.isNotEmpty() && !uiState.isRunning) {
                                DropdownMenuItem(
                                    text = { Text("Git 推送") },
                                    onClick = {
                                        viewModel.gitPush()
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Git 拉取") },
                                    onClick = {
                                        viewModel.gitPull()
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Git 状态") },
                                    onClick = {
                                        viewModel.refreshGitStatus()
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Source, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                            if (uiState.events.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("分享报告") },
                                    onClick = {
                                        viewModel.shareReport(context)
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("清除聊天") },
                                    onClick = {
                                        viewModel.clearCurrentChat()
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("新会话") },
                                onClick = {
                                    viewModel.startNewSession()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.gitStatus.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Source,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            uiState.gitStatus.take(100),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.refreshGitStatus() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            if (uiState.events.isEmpty() && !uiState.isRunning) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyChatContent(
                        projectPath = uiState.projectPath,
                        onProjectClick = { folderPicker.launch(null) },
                        onCloneClick = { showCloneDialog = true },
                        onGitHubClick = { showGitHubDialog = true },
                        githubConnected = uiState.githubConnected,
                        githubRepo = uiState.githubRepo,
                        onSuggestionClick = { suggestion ->
                            inputText = suggestion
                        },
                        onBuildClick = { viewModel.triggerBuild() },
                        onInstallApkClick = { viewModel.triggerInstallApk() },
                        onRunTestsClick = { viewModel.triggerRunTests() },
                        onGitPushClick = { viewModel.gitPush() },
                        onGitPullClick = { viewModel.gitPull() }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    reverseLayout = true
                ) {
                    if (uiState.isRunning) {
                        item { ThinkingIndicator() }
                    }
                    items(uiState.events.reversed()) { event ->
                        EventBubble(event)
                    }
                }
            }

            if (uiState.awaitingConfirmation != null) {
                ConfirmationBar(
                    event = uiState.awaitingConfirmation!!,
                    onConfirm = { viewModel.confirmAction() },
                    onDeny = { viewModel.denyAction() }
                )
            }

            EnhancedInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendTask(inputText)
                        inputText = ""
                    }
                },
                enabled = !uiState.isRunning,
                onAttachFile = { filePicker.launch(arrayOf("*/*")) },
                onAttachImage = { imagePicker.launch("image/*") },
                onVoiceInput = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasPermission) {
                        inputText = if (inputText.isBlank()) "请先授予麦克风权限，然后在设置中开启语音输入" else inputText
                    } else {
                        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出您的任务指令")
                        }
                        if (speechIntent.resolveActivity(context.packageManager) != null) {
                            speechLauncher.launch(speechIntent)
                        } else {
                            inputText = if (inputText.isBlank()) "您的设备不支持语音输入，请安装语音识别应用" else inputText
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun CloneRepoDialog(
    onDismiss: () -> Unit,
    onClone: (url: String, directory: String) -> Unit,
    defaultDirectory: String
) {
    var repoUrl by remember { mutableStateOf("") }
    var targetDir by remember { mutableStateOf(defaultDirectory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("克隆云端仓库")
            }
        },
        text = {
            Column {
                Text(
                    "输入 Git 仓库地址，DEREK 将自动克隆到本地",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    label = { Text("仓库地址") },
                    placeholder = { Text("https://github.com/user/repo.git") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetDir,
                    onValueChange = { targetDir = it },
                    label = { Text("目标目录") },
                    placeholder = { Text(defaultDirectory) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "提示: 在设置中配置 GitHub Token 后可推送代码",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onClone(repoUrl, targetDir) },
                enabled = repoUrl.isNotBlank() && targetDir.isNotBlank()
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("克隆")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ConnectGitHubDialog(
    onDismiss: () -> Unit,
    onSelectRepo: (GitHubUserRepo) -> Unit,
    onDisconnect: () -> Unit,
    onLoadRepos: () -> Unit,
    currentRepo: String,
    isConnected: Boolean,
    userInfo: GitHubUserInfo?,
    repoList: List<GitHubUserRepo>,
    isLoading: Boolean,
    error: String
) {
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!isConnected && repoList.isEmpty() && !isLoading && error.isEmpty()) {
            onLoadRepos()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("选择 GitHub 仓库")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isConnected) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("已连接: $currentRepo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (userInfo != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${userInfo.name} 的仓库",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (repoList.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("搜索仓库") },
                        placeholder = { Text("输入仓库名称筛选...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else if (error.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            if (error.contains("Token")) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("请在设置中配置 GitHub Token", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                } else if (repoList.isNotEmpty()) {
                    val filtered = if (searchQuery.isBlank()) repoList else repoList.filter {
                        it.fullName.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true)
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered) { repo ->
                            RepoItem(
                                repo = repo,
                                isSelected = repo.fullName == currentRepo,
                                onClick = { onSelectRepo(repo) }
                            )
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Text("没有匹配的仓库", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (!isConnected) {
                    Text(
                        "配置 GitHub Token 后，你的仓库将自动显示在此处",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (isConnected) {
                    TextButton(onClick = onDisconnect) {
                        Text("断开", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (!isLoading && repoList.isEmpty() && error.isNotEmpty()) {
                    TextButton(onClick = onLoadRepos) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重试")
                    }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun RepoItem(
    repo: GitHubUserRepo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        repo.fullName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (repo.isPrivate) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (repo.description.isNotEmpty()) {
                    Text(
                        repo.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    if (repo.language != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                repo.language,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        "更新于 ${repo.updatedAt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isSelected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun getRealPathFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        if (docId.startsWith("primary:")) {
            val relativePath = docId.substringAfter("primary:")
            if (relativePath.isEmpty()) "/sdcard" else "/sdcard/$relativePath"
        } else {
            null
        }
    } catch (e: Exception) {
        try {
            val docId = android.provider.DocumentsContract.getDocumentId(uri)
            if (docId.startsWith("primary:")) {
                val relativePath = docId.substringAfter("primary:")
                if (relativePath.isEmpty()) "/sdcard" else "/sdcard/$relativePath"
            } else {
                null
            }
        } catch (e2: Exception) {
            null
        }
    }
}

private fun getFilePathFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        val docId = android.provider.DocumentsContract.getDocumentId(uri)
        if (docId.startsWith("primary:")) {
            val relativePath = docId.substringAfter("primary:")
            if (relativePath.isEmpty()) "/sdcard" else "/sdcard/$relativePath"
        } else {
            null
        }
    } catch (_: Exception) {
        uri.path
    }
}

@Composable
private fun EmptyChatContent(
    projectPath: String,
    onProjectClick: () -> Unit,
    onCloneClick: () -> Unit,
    onGitHubClick: () -> Unit,
    githubConnected: Boolean,
    githubRepo: String,
    onSuggestionClick: (String) -> Unit,
    onBuildClick: () -> Unit,
    onInstallApkClick: () -> Unit,
    onRunTestsClick: () -> Unit,
    onGitPushClick: () -> Unit,
    onGitPullClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(DerekGradientStart, DerekGradientEnd),
                        start = Offset.Zero,
                        end = Offset(80f, 80f)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "DEREK AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "你的 AI 编程助手，自主完成开发任务",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (githubConnected) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "已连接: $githubRepo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (projectPath.isEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickActionCard(
                    icon = Icons.Filled.FolderOpen,
                    label = "选择项目",
                    onClick = onProjectClick,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    icon = Icons.Filled.CloudDownload,
                    label = "克隆仓库",
                    onClick = onCloneClick,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickActionCard(
                    icon = Icons.Filled.Cloud,
                    label = "GitHub",
                    onClick = onGitHubClick,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    icon = Icons.Filled.Build,
                    label = "构建项目",
                    onClick = onBuildClick,
                    modifier = Modifier.weight(1f),
                    enabled = false
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        projectPath.substringAfterLast("/"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onProjectClick) {
                        Text("切换", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickActionCard(icon = Icons.Filled.Build, label = "构建", onClick = onBuildClick, modifier = Modifier.weight(1f))
                QuickActionCard(icon = Icons.Filled.Download, label = "安装", onClick = onInstallApkClick, modifier = Modifier.weight(1f))
                QuickActionCard(icon = Icons.Filled.DoneAll, label = "测试", onClick = onRunTestsClick, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickActionCard(icon = Icons.Filled.CloudUpload, label = "推送", onClick = onGitPushClick, modifier = Modifier.weight(1f))
                QuickActionCard(icon = Icons.Filled.CloudDownload, label = "拉取", onClick = onGitPullClick, modifier = Modifier.weight(1f))
                QuickActionCard(icon = Icons.Filled.Cloud, label = "GitHub", onClick = onGitHubClick, modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "试试说：",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = listOf(
                "添加一个登录界面" to Icons.Filled.Login,
                "修复构建错误" to Icons.Filled.Build,
                "添加深色模式" to Icons.Filled.DarkMode,
                "克隆 GitHub 仓库" to Icons.Filled.CloudDownload
            )
            items(suggestions) { (text, icon) ->
                SuggestionChip(
                    onClick = { onSuggestionClick(text) },
                    label = { Text(text, style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        onClick = if (enabled) onClick else { {} },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun EventBubble(event: AgentEvent) {
    when (event) {
        is AgentEvent.UserMessage -> MessageBubble(content = event.content, isUser = true)
        is AgentEvent.AssistantThought -> MessageBubble(content = event.content, isUser = false)
        is AgentEvent.ToolCallEvent -> ToolCallGroupBubble(name = event.name, args = event.args, result = null, success = true)
        is AgentEvent.ToolResultEvent -> ToolResultOnlyBubble(output = event.output, success = event.success)
        is AgentEvent.TaskCompleteEvent -> TaskCompleteBubble(event.summary, event.filesChanged)
        is AgentEvent.BuildResultEvent -> BuildResultBubble(event.success, event.output)
        is AgentEvent.AutoFixEvent -> AutoFixBubble(event.attempt, event.maxAttempts, event.errorSummary)
        is AgentEvent.LintResultEvent -> LintResultBubble(event.path, event.passed, event.errors)
        is AgentEvent.StuckDetectedEvent -> WarningBubble(event.reason)
        is AgentEvent.ErrorEvent -> ErrorBubble(event.message)
        else -> {}
    }
}

@Composable
private fun MessageBubble(content: String, isUser: Boolean) {
    val bgColor by animateColorAsState(
        if (isUser) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.secondaryContainer,
        label = "bg"
    )
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSecondaryContainer
    val avatarColor = if (isUser) MaterialTheme.colorScheme.primary
    else DerekGradientEnd

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(DerekGradientStart, DerekGradientEnd),
                            start = Offset.Zero,
                            end = Offset(28f, 28f)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                RenderMarkdownText(content, textColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(System.currentTimeMillis()),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = textColor.copy(alpha = 0.5f)
                )
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun RenderMarkdownText(text: String, baseColor: Color) {
    val annotated = buildAnnotatedString {
        val lines = text.split("\n")
        var inCodeBlock = false
        for (line in lines) {
            if (line.trim().startsWith("```")) {
                inCodeBlock = !inCodeBlock
                if (inCodeBlock) {
                    val lang = line.trim().removePrefix("```").trim()
                    if (lang.isNotEmpty()) {
                        withStyle(SpanStyle(fontSize = 10.sp, color = baseColor.copy(alpha = 0.5f))) {
                            append("$lang\n")
                        }
                    }
                }
                continue
            }
            if (inCodeBlock) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)) {
                    append(line)
                    append("\n")
                }
                continue
            }
            if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                withStyle(SpanStyle(color = baseColor)) { append("• ") }
                appendLine(line.trim().removePrefix("- ").removePrefix("* "))
                continue
            }
            if (line.trim().startsWith("# ")) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = baseColor)) {
                    appendLine(line.trim().removePrefix("# "))
                }
                continue
            }
            val boldRegex = Regex("""\*\*(.+?)\*\*""")
            var remaining = line
            while (boldRegex.containsMatchIn(remaining)) {
                val match = boldRegex.find(remaining)!!
                withStyle(SpanStyle(color = baseColor)) { append(remaining.substring(0, match.range.first)) }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) { append(match.groupValues[1]) }
                remaining = remaining.substring(match.range.last + 1)
            }
            if (remaining.isNotEmpty()) {
                withStyle(SpanStyle(color = baseColor)) { append(remaining) }
            }
            append("\n")
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ToolCallGroupBubble(name: String, args: Map<String, String>, result: String?, success: Boolean) {
    var expanded by remember { mutableStateOf(false) }

    val displayName = getToolDisplayName(name)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Icon(
                    Icons.Filled.Build,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.weight(1f))
                val argsPreview = args.entries.firstOrNull()?.let { "${it.key}=${it.value.take(20)}" } ?: ""
                if (argsPreview.isNotBlank() && !expanded) {
                    Text(
                        argsPreview,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                args.entries.forEach { (key, value) ->
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text(
                            "$key: ",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolResultOnlyBubble(output: String, success: Boolean) {
    var expanded by remember { mutableStateOf(false) }

    val icon = if (success) Icons.Filled.CheckCircle else Icons.Filled.Error
    val color = if (success) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.errorContainer

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (success) "执行成功" else "执行失败",
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.weight(1f))
                if (!expanded) {
                    Text(
                        output.take(60),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1E1E)
                ) {
                    Text(
                        text = output,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFD4D4D4)
                        ),
                        maxLines = 20,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

private fun getToolDisplayName(name: String): String {
    return when (name) {
        "read_file" -> "读取文件"
        "write_file" -> "写入文件"
        "edit_file" -> "编辑文件"
        "list_files" -> "列出文件"
        "glob" -> "搜索文件"
        "grep" -> "搜索内容"
        "delete_file" -> "删除文件"
        "gradle_build" -> "Gradle 构建"
        "run_tests" -> "运行测试"
        "read_logcat" -> "读取日志"
        "lint_check" -> "语法检查"
        "search_code" -> "搜索代码"
        "analyze_project" -> "分析项目"
        "find_usages" -> "查找引用"
        "git_commit" -> "Git 提交"
        "git_diff" -> "Git 差异"
        "git_revert" -> "Git 回退"
        "git_clone" -> "Git 克隆"
        "git_push" -> "Git 推送"
        "git_pull" -> "Git 拉取"
        "git_branch" -> "Git 分支"
        "run_command" -> "执行命令"
        "install_apk" -> "安装 APK"
        "launch_app" -> "启动应用"
        "ask_user" -> "询问用户"
        "todo_write" -> "任务清单"
        "github_read_file" -> "GitHub 读取"
        "github_write_file" -> "GitHub 写入"
        "github_list_dir" -> "GitHub 目录"
        "github_delete_file" -> "GitHub 删除"
        "github_branch" -> "GitHub 分支"
        "github_repo_info" -> "GitHub 仓库信息"
        "github_commits" -> "GitHub 提交"
        "github_create_pr" -> "GitHub PR"
        "github_search_code" -> "GitHub 搜索"
        else -> name
    }
}

@Composable
private fun ThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse1"
    )
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse2"
    )
    val pulse3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut, delayMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(DerekGradientStart, DerekGradientEnd),
                        start = Offset.Zero,
                        end = Offset(28f, 28f)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = pulse1))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = pulse2))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = pulse3))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "DEREK 正在思考...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun TaskCompleteBubble(summary: String, filesChanged: List<String>) {
    var expanded by remember { mutableStateOf(filesChanged.size > 3) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("任务完成", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall)
            if (filesChanged.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = !expanded }
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${filesChanged.size} 个文件已修改",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (expanded) {
                    filesChanged.forEach { file ->
                        Row(modifier = Modifier.padding(start = 8.dp, vertical = 1.dp)) {
                            Text(
                                "• ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                file,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildResultBubble(success: Boolean, output: String) {
    var expanded by remember { mutableStateOf(false) }
    val icon = if (success) Icons.Filled.CheckCircle else Icons.Filled.Build
    val color = if (success) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.errorContainer

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (success) "构建成功" else "构建失败",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1E1E)
                ) {
                    Text(
                        text = output.take(800),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFD4D4D4)
                        ),
                        maxLines = 15,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoFixBubble(attempt: Int, maxAttempts: Int, errorSummary: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "自动修复 第 $attempt/$maxAttempts 次",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { attempt.toFloat() / maxAttempts.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun LintResultBubble(path: String, passed: Boolean, errors: List<String>) {
    val color = if (passed) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.errorContainer

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (passed) Icons.Filled.CheckCircle else Icons.Filled.BugReport,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "语法检查: $path — ${if (passed) "通过" else "${errors.size} 个问题"}",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun WarningBubble(reason: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(reason, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ErrorBubble(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Error, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ConfirmationBar(
    event: AgentEvent.AwaitingConfirmationEvent,
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("确认操作: ${event.name}", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                event.args.entries.take(3).joinToString("\n") { "${it.key}: ${it.value.take(50)}" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDeny) { Text("拒绝") }
                Button(onClick = onConfirm) { Text("确认") }
            }
        }
    }
}

@Composable
private fun EnhancedInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    onAttachFile: () -> Unit,
    onAttachImage: () -> Unit,
    onVoiceInput: () -> Unit = {}
) {
    var showAttachMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Box {
                    IconButton(
                        onClick = { showAttachMenu = !showAttachMenu },
                        enabled = enabled,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Filled.AddCircle,
                            contentDescription = "附件",
                            modifier = Modifier.size(24.dp),
                            tint = if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("图片") },
                            onClick = { onAttachImage(); showAttachMenu = false },
                            leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("文件") },
                            onClick = { onAttachFile(); showAttachMenu = false },
                            leadingIcon = { Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("语音") },
                            onClick = { onVoiceInput(); showAttachMenu = false },
                            leadingIcon = { Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "描述一个任务...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    maxLines = 4,
                    enabled = enabled,
                    shape = RoundedCornerShape(24.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(4.dp))
                FloatingActionButton(
                    onClick = { if (enabled && text.isNotBlank()) onSend() },
                    modifier = Modifier
                        .size(40.dp)
                        .alpha(if (enabled && text.isNotBlank()) 1f else 0.38f),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "发送", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
