package com.example.androiddevagent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.androiddevagent.ui.screens.AgentChatScreen
import com.example.androiddevagent.ui.screens.ProjectFilesScreen
import com.example.androiddevagent.ui.screens.TaskHistoryScreen
import com.example.androiddevagent.ui.screens.SettingsScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.AgentChat.route
    ) {
        composable(Screen.AgentChat.route) {
            AgentChatScreen()
        }
        composable(Screen.ProjectFiles.route) {
            ProjectFilesScreen()
        }
        composable(Screen.TaskHistory.route) {
            TaskHistoryScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
