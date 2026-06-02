package com.example.androiddevagent.ui.screens

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.agent.events.AgentEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    viewModel: AgentChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "开发助手",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (uiState.projectPath.isNotEmpty()) {
                            Text(
                                text = uiState.projectPath.substringAfterLast("/"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.isRunning) {
                        FilledTonalButton(
                            onClick = { viewModel.stopAgent() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("停止", style = MaterialTheme.typography.labelMedium)
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
            if (uiState.events.isEmpty() && !uiState.isRunning) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyChatContent(
                        projectPath = uiState.projectPath,
                        onProjectClick = { folderPicker.launch(null) },
                        onSuggestionClick = { suggestion ->
                            inputText = suggestion
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    reverseLayout = true
                ) {
                    items(uiState.events.reversed()) { event ->
                        EventBubble(event)
                    }
                }
            }

            if (uiState.isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
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
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "说出您的任务指令")
                    }
                    speechLauncher.launch(intent)
                }
            )
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
    onSuggestionClick: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Filled.Android,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "安卓开发助手",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "描述一个任务，助手将自主完成它",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (projectPath.isEmpty()) {
            Button(onClick = onProjectClick) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("选择项目目录")
            }
        } else {
            AssistChip(
                onClick = onProjectClick,
                label = { Text(projectPath.substringAfterLast("/")) },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("试试说：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        SuggestionChip(onClick = { onSuggestionClick("添加一个登录界面") }, label = { Text("添加一个登录界面") })
        Spacer(modifier = Modifier.height(4.dp))
        SuggestionChip(onClick = { onSuggestionClick("修复构建错误") }, label = { Text("修复构建错误") })
        Spacer(modifier = Modifier.height(4.dp))
        SuggestionChip(onClick = { onSuggestionClick("添加深色模式支持") }, label = { Text("添加深色模式支持") })
    }
}

@Composable
private fun EventBubble(event: AgentEvent) {
    when (event) {
        is AgentEvent.UserMessage -> MessageBubble(content = event.content, isUser = true)
        is AgentEvent.AssistantThought -> MessageBubble(content = event.content, isUser = false)
        is AgentEvent.ToolCallEvent -> ToolCallBubble(event.name, event.args)
        is AgentEvent.ToolResultEvent -> ToolResultBubble(event.output, event.success)
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    if (isUser) Icons.Filled.Person else Icons.Filled.Android,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = textColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    content,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun ToolCallBubble(name: String, args: Map<String, String>) {
    var expanded by remember { mutableStateOf(false) }

    val displayName = when (name) {
        "read_file" -> "读取文件"
        "write_file" -> "写入文件"
        "edit_file" -> "编辑文件"
        "list_files" -> "列出文件"
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
        "ask_user" -> "询问用户"
        else -> name
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded }
            ) {
                Icon(
                    Icons.Filled.Build,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.weight(1f))
                val argsPreview = args.entries.take(2).joinToString(" ") { "${it.key}=${it.value.take(20)}" }
                if (argsPreview.isNotBlank() && !expanded) {
                    Text(
                        text = argsPreview,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
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
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
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
private fun ToolResultBubble(output: String, success: Boolean) {
    var expanded by remember { mutableStateOf(false) }

    val icon = if (success) Icons.Filled.CheckCircle else Icons.Filled.Error
    val color = if (success) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.errorContainer

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded }
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (success) "结果" else "错误",
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.weight(1f))
                if (!expanded) {
                    Text(
                        output.take(80),
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
                Text(
                    text = output,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 20
                )
            }
        }
    }
}

@Composable
private fun TaskCompleteBubble(summary: String, filesChanged: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("任务完成", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall)
            if (filesChanged.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${filesChanged.size} 个文件已修改",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                filesChanged.take(5).forEach { file ->
                    Text(
                        "  $file",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                if (filesChanged.size > 5) {
                    Text(
                        "  及其他 ${filesChanged.size - 5} 个文件",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildResultBubble(success: Boolean, output: String) {
    val icon = if (success) Icons.Filled.CheckCircle else Icons.Filled.Build
    val color = if (success) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.errorContainer

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (success) "构建成功" else "构建失败",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = output.take(400),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 8
            )
        }
    }
}

@Composable
private fun AutoFixBubble(attempt: Int, maxAttempts: Int, errorSummary: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "自动修复 第 $attempt/$maxAttempts 次",
                style = MaterialTheme.typography.labelMedium
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
        shape = RoundedCornerShape(8.dp),
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
        shape = RoundedCornerShape(8.dp)
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
        shape = RoundedCornerShape(8.dp)
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onVoiceInput,
                enabled = enabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "语音输入",
                    modifier = Modifier.size(20.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onAttachImage,
                enabled = enabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = "添加图片",
                    modifier = Modifier.size(20.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onAttachFile,
                enabled = enabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = "添加文件",
                    modifier = Modifier.size(20.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("描述一个任务...", style = MaterialTheme.typography.bodySmall) },
                maxLines = 4,
                enabled = enabled,
                shape = RoundedCornerShape(20.dp),
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
