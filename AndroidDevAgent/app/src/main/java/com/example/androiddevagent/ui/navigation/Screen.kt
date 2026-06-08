package com.example.androiddevagent.ui.navigation

sealed class Screen(val route: String) {
    object AgentChat : Screen("agent_chat")
    object CodeExplanation : Screen("code_explanation")
    object Debug : Screen("debug")
    object Architecture : Screen("architecture")
    object ConversationHistory : Screen("conversation_history")
    object ProjectFiles : Screen("project_files")
    object Skills : Screen("skills")
    object TaskHistory : Screen("task_history")
    object Settings : Screen("settings")
    object CodeEditor : Screen("code_editor/{path}")
    object ProjectList : Screen("project_list")
    object NewProject : Screen("new_project")
    object Memory : Screen("memory")
}
