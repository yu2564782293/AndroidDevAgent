package com.example.androiddevagent.agent.memory

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ProjectSummary(
    val structure: String,
    val keyFiles: List<FileSummary>,
    val gradleDependencies: String,
    val manifestInfo: String
)

data class FileSummary(
    val path: String,
    val summary: String,
    val lineCount: Int
)

@Singleton
class ProjectSummaryGenerator @Inject constructor() {

    fun generate(projectPath: String): ProjectSummary {
        val projectDir = File(projectPath)
        if (!projectDir.exists()) {
            return ProjectSummary("Project not found", emptyList(), "", "")
        }

        val structure = generateDirectoryTree(projectDir, maxDepth = 3)
        val keyFiles = analyzeKeyFiles(projectDir)
        val gradleDeps = parseGradleDependencies(projectDir)
        val manifestInfo = parseManifest(projectDir)

        return ProjectSummary(structure, keyFiles, gradleDeps, manifestInfo)
    }

    private fun generateDirectoryTree(dir: File, maxDepth: Int, currentDepth: Int = 0): String {
        if (currentDepth >= maxDepth) return ""
        val indent = "  ".repeat(currentDepth)
        val sb = StringBuilder()
        val files = dir.listFiles()?.sortedBy { !it.isDirectory } ?: return ""
        for (file in files) {
            if (file.name.startsWith(".") && file.name != ".github") continue
            if (file.name in listOf("build", ".gradle", ".idea", "node_modules", ".git")) continue
            sb.append("$indent${if (file.isDirectory) "📁" else "📄"} ${file.name}\n")
            if (file.isDirectory && currentDepth < maxDepth - 1) {
                sb.append(generateDirectoryTree(file, maxDepth, currentDepth + 1))
            }
        }
        return sb.toString()
    }

    private fun analyzeKeyFiles(projectDir: File): List<FileSummary> {
        val summaries = mutableListOf<FileSummary>()
        val keyPatterns = listOf(
            "build.gradle" to "Gradle build configuration",
            "settings.gradle" to "Gradle settings",
            "AndroidManifest.xml" to "Android manifest",
            "proguard-rules.pro" to "ProGuard configuration"
        )

        for ((pattern, defaultSummary) in keyPatterns) {
            findFiles(projectDir, pattern).forEach { file ->
                summaries.add(FileSummary(
                    path = file.relativeTo(projectDir).path,
                    summary = defaultSummary,
                    lineCount = file.readLines().size
                ))
            }
        }

        findFiles(projectDir, ".kt").take(20).forEach { file ->
            val content = file.readText()
            val summary = generateKotlinSummary(content)
            summaries.add(FileSummary(
                path = file.relativeTo(projectDir).path,
                summary = summary,
                lineCount = content.lines().size
            ))
        }

        findFiles(projectDir, ".java").take(10).forEach { file ->
            val content = file.readText()
            val summary = generateJavaSummary(content)
            summaries.add(FileSummary(
                path = file.relativeTo(projectDir).path,
                summary = summary,
                lineCount = content.lines().size
            ))
        }

        return summaries
    }

    private fun generateKotlinSummary(content: String): String {
        val classes = Regex("(?:data )?class (\\w+)").findAll(content).map { it.groupValues[1] }.toList()
        val functions = Regex("fun (\\w+)").findAll(content).map { it.groupValues[1] }.toList()
        val composables = Regex("@Composable\\s+fun (\\w+)").findAll(content).map { it.groupValues[1] }.toList()

        val parts = mutableListOf<String>()
        if (classes.isNotEmpty()) parts.add("classes: ${classes.take(5).joinToString()}")
        if (composables.isNotEmpty()) parts.add("@Composable: ${composables.take(5).joinToString()}")
        else if (functions.isNotEmpty()) parts.add("functions: ${functions.take(5).joinToString()}")

        return if (parts.isEmpty()) "Kotlin source file" else parts.joinToString("; ")
    }

    private fun generateJavaSummary(content: String): String {
        val classes = Regex("(?:public )?class (\\w+)").findAll(content).map { it.groupValues[1] }.toList()
        val methods = Regex("(?:public|private|protected) \\w+ (\\w+)\\(").findAll(content).map { it.groupValues[1] }.toList()

        val parts = mutableListOf<String>()
        if (classes.isNotEmpty()) parts.add("classes: ${classes.take(5).joinToString()}")
        if (methods.isNotEmpty()) parts.add("methods: ${methods.take(5).joinToString()}")

        return if (parts.isEmpty()) "Java source file" else parts.joinToString("; ")
    }

    private fun parseGradleDependencies(projectDir: File): String {
        val buildFile = findFiles(projectDir, "build.gradle").firstOrNull() ?: return ""
        val content = buildFile.readText()
        val deps = Regex("implementation ['\"]([^'\"]+)['\"]").findAll(content)
            .map { it.groupValues[1] }.toList()
        return if (deps.isEmpty()) "" else deps.joinToString("\n")
    }

    private fun parseManifest(projectDir: File): String {
        val manifestFile = findFiles(projectDir, "AndroidManifest.xml").firstOrNull() ?: return ""
        val content = manifestFile.readText()

        val package_ = Regex("package=\"([^\"]+)\"").find(content)?.groupValues?.get(1) ?: ""
        val activities = Regex("<activity[^>]*android:name=\"([^\"]+)\"").findAll(content)
            .map { it.groupValues[1] }.toList()
        val permissions = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"").findAll(content)
            .map { it.groupValues[1] }.toList()

        val sb = StringBuilder()
        sb.append("Package: $package_\n")
        if (activities.isNotEmpty()) sb.append("Activities: ${activities.joinToString()}\n")
        if (permissions.isNotEmpty()) sb.append("Permissions: ${permissions.joinToString()}\n")

        return sb.toString()
    }

    private fun findFiles(dir: File, suffix: String): List<File> {
        val result = mutableListOf<File>()
        dir.walk().forEach { file ->
            if (file.isFile && file.name.endsWith(suffix)) {
                if (!file.absolutePath.contains("/build/") &&
                    !file.absolutePath.contains("/.gradle/") &&
                    !file.absolutePath.contains("/.idea/")) {
                    result.add(file)
                }
            }
        }
        return result
    }
}
