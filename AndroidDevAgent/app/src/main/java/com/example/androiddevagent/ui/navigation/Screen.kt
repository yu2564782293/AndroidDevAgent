package com.example.androiddevagent.ui.navigation

sealed class Screen(val route: String) {
    object AgentChat : Screen("agent_chat")
    object ProjectFiles : Screen("project_files")
    object TaskHistory : Screen("task_history")
    object Settings : Screen("settings")
    object CodeEditor : Screen("code_editor/{path}")
    object ProjectList : Screen("project_list")
}
