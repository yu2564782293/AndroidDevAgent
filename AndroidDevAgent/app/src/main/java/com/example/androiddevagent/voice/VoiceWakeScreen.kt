package com.example.androiddevagent.voice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 语音唤醒设置页面
 * 提供唤醒词注册、服务启停、参数配置等功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceWakeScreen(
    viewModel: VoiceWakeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var wakeWordInput by remember { mutableStateOf(uiState.wakeWordName) }

    // 同步唤醒词名称
    LaunchedEffect(uiState.wakeWordName) {
        wakeWordInput = uiState.wakeWordName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音唤醒") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 服务状态卡片
            ServiceStatusCard(
                isRunning = uiState.isServiceRunning,
                isDetecting = uiState.isDetecting,
                onStart = { viewModel.startWakeService() },
                onStop = { viewModel.stopWakeService() }
            )

            Divider()

            // 唤醒词名称设置
            Text("唤醒词设置", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = wakeWordInput,
                onValueChange = {
                    wakeWordInput = it
                    viewModel.saveWakeWordName(it)
                },
                label = { Text("唤醒词名称") },
                placeholder = { Text("例如：小助手") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isServiceRunning && !uiState.isEnrolling
            )

            Text(
                "设置唤醒词名称（仅用于显示），实际唤醒词需要通过录音注册",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider()

            // 注册区域
            Text("唤醒词注册", style = MaterialTheme.typography.titleMedium)

            Text(
                "请在一个安静的环境中，清晰地说出您想要的唤醒词，重复3次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (uiState.isEnrolled && !uiState.isEnrolling) {
                // 已注册状态
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "已注册唤醒词: ${uiState.wakeWordName.ifBlank { "未命名" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "可以启动服务开始监听",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.startEnrollment() },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isServiceRunning
                    ) {
                        Text("重新注册")
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearEnrollment() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        enabled = !uiState.isServiceRunning
                    ) {
                        Text("删除唤醒词")
                    }
                }
            } else if (uiState.isEnrolling) {
                // 注册中状态
                EnrollmentCard(
                    progress = uiState.enrollmentProgress,
                    totalSteps = 3,
                    stepText = uiState.enrollmentStepText,
                    onCancel = { viewModel.cancelEnrollment() }
                )
            } else {
                // 未注册状态
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "尚未注册唤醒词",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "点击下方按钮开始注册，您需要说出唤醒词3次以完成注册",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { viewModel.startEnrollment() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始注册")
                        }
                    }
                }
            }

            Divider()

            // VAD 模式信息
            Text("技术信息", style = MaterialTheme.typography.titleMedium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    InfoRow("VAD 模式", if (uiState.vadMode == "onnx") "Silero ONNX" else "能量检测（回退）")
                    InfoRow("特征提取", "MFCC (13维)")
                    InfoRow("匹配算法", "DTW 动态时间规整")
                    InfoRow("采样率", "16000 Hz")
                    InfoRow("注册要求", "说3次唤醒词")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 服务状态卡片
 */
@Composable
private fun ServiceStatusCard(
    isRunning: Boolean,
    isDetecting: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDetecting) MaterialTheme.colorScheme.tertiaryContainer
            else if (isRunning) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isRunning) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = null,
                    tint = if (isRunning) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        when {
                            isDetecting -> "检测到唤醒词！"
                            isRunning -> "正在监听唤醒词..."
                            else -> "服务未启动"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        when {
                            isDetecting -> "已触发唤醒"
                            isRunning -> "后台持续监听中"
                            else -> "点击启动开始监听"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = if (isRunning) onStop else onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = if (isRunning) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Icon(
                    if (isRunning) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isRunning) "停止监听" else "启动监听")
            }
        }
    }
}

/**
 * 注册进度卡片
 */
@Composable
private fun EnrollmentCard(
    progress: Int,
    totalSteps: Int,
    stepText: String,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "注册中",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Text(
                stepText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            // 进度条
            LinearProgressIndicator(
                progress = { progress.toFloat() / totalSteps },
                modifier = Modifier.fillMaxWidth(),
            )

            // 进度指示点
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 1..totalSteps) {
                    val isCompleted = i <= progress
                    val isCurrent = i == progress + 1

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = when {
                            isCompleted -> MaterialTheme.colorScheme.primary
                            isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "$i",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCompleted || isCurrent)
                                    MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (i < totalSteps) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("取消注册")
            }
        }
    }
}

/**
 * 信息行
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
