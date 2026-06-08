package com.example.androiddevagent.models

/**
 * 支持的编程语言枚举
 */
enum class ProgrammingLanguage(val displayName: String, val extension: String) {
    KOTLIN("Kotlin", "kt"),
    JAVA("Java", "java"),
    XML("XML", "xml"),
    GRADLE("Gradle", "gradle"),
    PYTHON("Python", "py"),
    JAVASCRIPT("JavaScript", "js")
}

/**
 * 项目类型枚举
 */
enum class ProjectType {
    ANDROID_APP,
    LIBRARY,
    PLUGIN,
    GAME
}

/**
 * 代码生成请求
 */
data class CodeGenerationRequest(
    val description: String,
    val language: ProgrammingLanguage,
    val requirements: List<String> = emptyList(),
    val projectType: ProjectType = ProjectType.ANDROID_APP
)

/**
 * 代码生成结果
 */
data class CodeGenerationResult(
    val code: String,
    val language: ProgrammingLanguage,
    val explanation: String,
    val requirements: List<String>
)

/**
 * 代码解释结果
 */
data class CodeExplanationResult(
    val originalCode: String,
    val language: ProgrammingLanguage,
    val explanation: String,
    val suggestions: List<String>
)

/**
 * 调试结果
 */
data class DebugResult(
    val errorDescription: String,
    val suggestedSolution: String,
    val confidence: Double,
    val alternativeSolutions: List<String>
)

/**
 * 架构设计结果
 */
data class ArchitectureResult(
    val projectDescription: String,
    val suggestedArchitecture: String,
    val components: List<String>,
    val patterns: List<String>,
    val bestPractices: List<String>
)

/**
 * 编译测试结果
 */
data class CompileTestResult(
    val originalCode: String,
    val language: ProgrammingLanguage,
    val compileResult: CompileResult,
    val testResult: TestResult,
    val success: Boolean
)

/**
 * 语法检查结果
 */
data class SyntaxCheckResult(
    val isValid: Boolean,
    val errorMessage: String?
)

/**
 * 编译结果
 */
data class CompileResult(
    val success: Boolean,
    val compiledCode: String?,
    val errorMessage: String?
)

/**
 * 测试结果
 */
data class TestResult(
    val success: Boolean,
    val testOutput: String,
    val failedTests: List<String>
)