package com.example.androiddevagent.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.agent.events.AgentEvent
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    onNavigateToSettings: () -> Unit = {},
    viewModel: AgentChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SmartToy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Android Dev Agent")
                    }
                },
                actions = {
                    if (uiState.isRunning) {
                        IconButton(onClick = { viewModel.stopAgent() }) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop")
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.projectPath.isNotEmpty()) {
                ProjectBar(uiState.projectPath)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = false
            ) {
                items(uiState.events) { event ->
                    EventBubble(event)
                }

                if (uiState.isRunning) {
                    item {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Agent is working...", style = MaterialTheme.typography.bodySmall)
                        }
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

            InputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendTask(inputText)
                        inputText = ""
                    }
                },
                enabled = !uiState.isRunning
            )
        }
    }
}

@Composable
private fun ProjectBar(projectPath: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = projectPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EventBubble(event: AgentEvent) {
    when (event) {
        is AgentEvent.UserMessage -> {
            MessageBubble(
                content = event.content,
                isUser = true,
                icon = Icons.Filled.Person
            )
        }
        is AgentEvent.AssistantThought -> {
            MessageBubble(
                content = event.content,
                isUser = false,
                icon = Icons.Filled.SmartToy
            )
        }
        is AgentEvent.ToolCallEvent -> {
            ToolCallBubble(event.name, event.args)
        }
        is AgentEvent.ToolResultEvent -> {
            ToolResultBubble(event.output, event.success)
        }
        is AgentEvent.TaskCompleteEvent -> {
            TaskCompleteBubble(event.summary, event.filesChanged)
        }
        is AgentEvent.BuildResultEvent -> {
            BuildResultBubble(event.success, event.output)
        }
        is AgentEvent.AutoFixEvent -> {
            AutoFixBubble(event.attempt, event.maxAttempts, event.errorSummary)
        }
        is AgentEvent.LintResultEvent -> {
            LintResultBubble(event.path, event.passed, event.errors)
        }
        is AgentEvent.StuckDetectedEvent -> {
            WarningBubble(event.reason)
        }
        is AgentEvent.ErrorEvent -> {
            ErrorBubble(event.message)
        }
        else -> {}
    }
}

@Composable
private fun MessageBubble(content: String, isUser: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector) {
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
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = textColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(content, style = MaterialTheme.typography.bodySmall, color = textColor)
            }
        }
    }
}

@Composable
private fun ToolCallBubble(name: String, args: Map<String, String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Build,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                val argsPreview = args.entries.take(3).joinToString(" ") { "${it.key}=${it.value.take(30)}" }
                if (argsPreview.isNotBlank()) {
                    Text(
                        text = argsPreview,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolResultBubble(output: String, success: Boolean) {
    val icon = if (success) Icons.Filled.CheckCircle else Icons.Filled.Error
    val color = if (success) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.errorContainer

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (success) "Result" else "Error",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = output.take(500),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 10
            )
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
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Task Complete", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall)
            if (filesChanged.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Files changed: ${filesChanged.joinToString(", ")}", style = MaterialTheme.typography.labelSmall)
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
                    if (success) "Build Succeeded" else "Build Failed",
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
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Auto-fix attempt $attempt/$maxAttempts",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorSummary.take(200),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 5,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
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
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (passed) Icons.Filled.CheckCircle else Icons.Filled.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Lint: $path — ${if (passed) "Passed" else "${errors.size} issue(s)"}",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (!passed && errors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errors.take(5).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 5
                )
            }
        }
    }
}

@Composable
private fun WarningBubble(reason: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Confirm action: ${event.name}", style = MaterialTheme.typography.titleSmall)
            Text(event.args.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDeny) { Text("Deny") }
                Button(onClick = onConfirm) { Text("Confirm") }
            }
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Enter your task...") },
                maxLines = 4,
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = { if (enabled && text.isNotBlank()) onSend() },
                modifier = Modifier.alpha(if (enabled && text.isNotBlank()) 1f else 0.38f)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
