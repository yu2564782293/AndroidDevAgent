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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.androiddevagent.ui.screens.ArchitectureScreen
import com.example.androiddevagent.ui.screens.CodeGenerationScreen
import com.example.androiddevagent.ui.screens.CodeExplanationScreen
import com.example.androiddevagent.ui.screens.DebugScreen
import com.example.androiddevagent.ui.screens.HomeScreen
import com.example.androiddevagent.ui.screens.SettingsScreen
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
fun AndroidDevAgentApp(
    navController: NavHostController = rememberNavController()
) {
    val destinations = AppDestination.entries
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = destinations.firstOrNull { destination ->
        navBackStackEntry?.destination?.hierarchy?.any { it.route == destination.route } == true
    } ?: AppDestination.Home

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
                            onClick = {
                                navController.navigateToTopLevelDestination(destination)
                            },
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
            NavHost(
                navController = navController,
                startDestination = AppDestination.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(AppDestination.Home.route) {
                    HomeScreen(
                        onNavigateToCodeGeneration = {
                            navController.navigateToTopLevelDestination(AppDestination.CodeGeneration)
                        },
                        onNavigateToCodeExplanation = {
                            navController.navigateToTopLevelDestination(AppDestination.CodeExplanation)
                        },
                        onNavigateToDebugging = {
                            navController.navigateToTopLevelDestination(AppDestination.Debugging)
                        },
                        onNavigateToArchitecture = {
                            navController.navigateToTopLevelDestination(AppDestination.Architecture)
                        }
                    )
                }
                composable(AppDestination.CodeGeneration.route) {
                    CodeGenerationScreen()
                }
                composable(AppDestination.CodeExplanation.route) {
                    CodeExplanationScreen()
                }
                composable(AppDestination.Debugging.route) {
                    DebugScreen()
                }
                composable(AppDestination.Architecture.route) {
                    ArchitectureScreen()
                }
                composable(AppDestination.Settings.route) {
                    SettingsScreen()
                }
            }
        }
    }
}

private fun NavHostController.navigateToTopLevelDestination(destination: AppDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

enum class AppDestination(
    val route: String,
    val title: String,
    val label: String,
    val icon: ImageVector
) {
    Home("home", "Android Dev Agent", "首页", Icons.Filled.Home),
    CodeGeneration("code_generation", "智能代码生成", "生成", Icons.Filled.Code),
    CodeExplanation("code_explanation", "代码解释", "解释", Icons.Filled.MenuBook),
    Debugging("debugging", "调试助手", "调试", Icons.Filled.BugReport),
    Architecture("architecture", "架构设计", "架构", Icons.Filled.AccountTree),
    Settings("settings", "设置", "设置", Icons.Filled.Settings)
}

@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    AndroidDevAgentTheme {
        AndroidDevAgentApp()
    }
}
