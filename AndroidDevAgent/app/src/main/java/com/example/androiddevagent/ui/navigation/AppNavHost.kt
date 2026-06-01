package com.example.androiddevagent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.androiddevagent.ui.screens.*

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCodeGeneration = { navController.navigate(Screen.CodeGeneration.route) },
                onNavigateToCodeExplanation = { navController.navigate(Screen.CodeExplanation.route) },
                onNavigateToDebugging = { navController.navigate(Screen.Debugging.route) },
                onNavigateToArchitecture = { navController.navigate(Screen.Architecture.route) }
            )
        }
        composable(Screen.CodeGeneration.route) {
            CodeGenerationScreen()
        }
        composable(Screen.CodeExplanation.route) {
            CodeExplanationScreen()
        }
        composable(Screen.Debugging.route) {
            DebuggingScreen()
        }
        composable(Screen.Architecture.route) {
            ArchitectureScreen()
        }
    }
}
