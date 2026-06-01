package com.example.androiddevagent.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object CodeGeneration : Screen("code_generation")
    object CodeExplanation : Screen("code_explanation")
    object Debugging : Screen("debugging")
    object Architecture : Screen("architecture")
}
