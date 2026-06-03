package com.example.androiddevagent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.example.androiddevagent.ui.screens.CodeGenerationScreen
import com.example.androiddevagent.ui.screens.HomeScreen
import com.example.androiddevagent.ui.screens.SimpleFeatureScreen
import com.example.androiddevagent.ui.theme.AndroidDevAgentTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidDevAgentTheme {
                AndroidDevAgentApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidDevAgentApp() {
    var currentDestination by remember { mutableStateOf(AppDestination.Home) }
    val destinations = AppDestination.entries

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentDestination.title) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination == destination,
                            onClick = { currentDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.title
                                )
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            val contentModifier = Modifier.padding(innerPadding)
            when (currentDestination) {
                AppDestination.Home -> HomeScreen(
                    modifier = contentModifier,
                    onNavigateToCodeGeneration = { currentDestination = AppDestination.CodeGeneration },
                    onNavigateToCodeExplanation = { currentDestination = AppDestination.CodeExplanation },
                    onNavigateToDebugging = { currentDestination = AppDestination.Debugging },
                    onNavigateToArchitecture = { currentDestination = AppDestination.Architecture }
                )
                AppDestination.CodeGeneration -> CodeGenerationScreen(modifier = contentModifier)
                AppDestination.CodeExplanation -> SimpleFeatureScreen(
                    modifier = contentModifier,
                    title = "代码解释",
                    description = "粘贴代码后，Android Dev Agent 将帮助你梳理实现逻辑、关键 API 与潜在风险。",
                    actionText = "开始分析"
                )
                AppDestination.Debugging -> SimpleFeatureScreen(
                    modifier = contentModifier,
                    title = "调试助手",
                    description = "输入报错信息、Logcat 或复现步骤，快速定位问题并获得修复建议。",
                    actionText = "提交问题"
                )
                AppDestination.Architecture -> SimpleFeatureScreen(
                    modifier = contentModifier,
                    title = "架构设计",
                    description = "描述业务目标和技术约束，获取模块划分、数据流和工程结构建议。",
                    actionText = "生成方案"
                )
            }
        }
    }
}

enum class AppDestination(
    val title: String,
    val label: String,
    val icon: ImageVector
) {
    Home("Android Dev Agent", "首页", Icons.Filled.Home),
    CodeGeneration("智能代码生成", "生成", Icons.Filled.Code),
    CodeExplanation("代码解释", "解释", Icons.Filled.MenuBook),
    Debugging("调试助手", "调试", Icons.Filled.BugReport),
    Architecture("架构设计", "架构", Icons.Filled.AccountTree)
}

@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    AndroidDevAgentTheme {
        AndroidDevAgentApp()
    }
}
