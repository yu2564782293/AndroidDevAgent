package com.example.androiddevagent.ui.navigation

sealed class Screen(val route: String) {
    object AgentChat : Screen("agent_chat")
    object Settings : Screen("settings")
}
