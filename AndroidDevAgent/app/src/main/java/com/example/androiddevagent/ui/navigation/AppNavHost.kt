package com.example.androiddevagent.ui.navigation

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.androiddevagent.ui.screens.AgentChatScreen
import com.example.androiddevagent.ui.screens.ArchitectureScreen
import com.example.androiddevagent.ui.screens.CodeEditorScreen
import com.example.androiddevagent.ui.screens.CodeExplanationScreen
import com.example.androiddevagent.ui.screens.DebugScreen
import com.example.androiddevagent.ui.screens.HistoryScreen
import com.example.androiddevagent.ui.screens.MemoryScreen
import com.example.androiddevagent.ui.screens.NewProjectScreen
import com.example.androiddevagent.ui.screens.ProjectFilesScreen
import com.example.androiddevagent.ui.screens.ProjectListScreen
import com.example.androiddevagent.ui.screens.SettingsScreen
import com.example.androiddevagent.ui.screens.SkillScreen
import com.example.androiddevagent.ui.screens.TaskHistoryScreen

@Composable
fun AppNavHost(navController: NavHostController, drawerState: DrawerState? = null) {
    NavHost(
        navController = navController,
        startDestination = Screen.AgentChat.route
    ) {
        composable(Screen.AgentChat.route) {
            AgentChatScreen(drawerState = drawerState)
        }
        composable(Screen.CodeExplanation.route) {
            CodeExplanationScreen()
        }
        composable(Screen.Debug.route) {
            DebugScreen()
        }
        composable(Screen.Architecture.route) {
            ArchitectureScreen()
        }
        composable(Screen.ConversationHistory.route) {
            HistoryScreen()
        }
        composable(Screen.ProjectFiles.route) {
            ProjectFilesScreen(
                onFileClick = { filePath ->
                    val encoded = java.net.URLEncoder.encode(filePath, "UTF-8")
                    navController.navigate("code_editor/$encoded")
                }
            )
        }
        composable(Screen.Skills.route) {
            SkillScreen()
        }
        composable(Screen.TaskHistory.route) {
            TaskHistoryScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(
            route = Screen.CodeEditor.route,
            arguments = listOf(
                navArgument("path") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
            val filePath = java.net.URLDecoder.decode(encodedPath, "UTF-8")
            CodeEditorScreen(
                filePath = filePath,
                navController = navController
            )
        }
        composable(Screen.ProjectList.route) {
            ProjectListScreen(navController = navController)
        }
        composable(Screen.NewProject.route) {
            NewProjectScreen()
        }
        composable(Screen.Memory.route) {
            MemoryScreen()
        }
    }
}
