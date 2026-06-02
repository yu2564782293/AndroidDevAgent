package com.example.androiddevagent.agent.memory

import java.io.File
import java.io.FileNotFoundException
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
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return ProjectSummary("项目目录不存在", emptyList(), "", "")
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
        val files = try {
            dir.listFiles()?.sortedBy { !it.isDirectory }
        } catch (e: SecurityException) {
            return "${indent}⚠️ 无权限访问\n"
        } ?: return ""
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
            "build.gradle" to "Gradle 构建配置",
            "settings.gradle" to "Gradle 设置",
            "AndroidManifest.xml" to "Android 清单文件",
            "proguard-rules.pro" to "ProGuard 配置"
        )

        for ((pattern, defaultSummary) in keyPatterns) {
            findFiles(projectDir, pattern).forEach { file ->
                try {
                    summaries.add(FileSummary(
                        path = file.relativeTo(projectDir).path,
                        summary = defaultSummary,
                        lineCount = file.readLines().size
                    ))
                } catch (_: Exception) {
                }
            }
        }

        findFiles(projectDir, ".kt").take(20).forEach { file ->
            try {
                val content = file.readText()
                val summary = generateKotlinSummary(content)
                summaries.add(FileSummary(
                    path = file.relativeTo(projectDir).path,
                    summary = summary,
                    lineCount = content.lines().size
                ))
            } catch (_: Exception) {
            }
        }

        findFiles(projectDir, ".java").take(10).forEach { file ->
            try {
                val content = file.readText()
                val summary = generateJavaSummary(content)
                summaries.add(FileSummary(
                    path = file.relativeTo(projectDir).path,
                    summary = summary,
                    lineCount = content.lines().size
                ))
            } catch (_: Exception) {
            }
        }

        return summaries
    }

    private fun generateKotlinSummary(content: String): String {
        val classes = Regex("(?:data |sealed |abstract |open |enum )?class (\\w+)(?:\\([^)]*\\))?(?:\\s*:\\s*[^{]+)?").findAll(content)
            .map { it.groupValues[1] }.toList()
        val interfaces = Regex("interface (\\w+)").findAll(content).map { it.groupValues[1] }.toList()
        val objects = Regex("object (\\w+)").findAll(content).map { it.groupValues[1] }.toList()
        val composables = Regex("@Composable\\s+(?:private\\s+)?fun (\\w+)\\(([^)]*)\\)").findAll(content)
            .map { "${it.groupValues[1]}(${it.groupValues[2].take(40)})" }.toList()
        val functions = Regex("(?:public |private |internal |suspend )?fun (\\w+)\\(([^)]*)\\)").findAll(content)
            .map { "${it.groupValues[1]}(${it.groupValues[2].take(40)})" }.toList()
        val composableNames = Regex("@Composable\\s+(?:private\\s+)?fun (\\w+)").findAll(content)
            .map { it.groupValues[1] }.toList()

        val parts = mutableListOf<String>()
        if (classes.isNotEmpty()) parts.add("class: ${classes.take(5).joinToString()}")
        if (interfaces.isNotEmpty()) parts.add("interface: ${interfaces.take(3).joinToString()}")
        if (objects.isNotEmpty()) parts.add("object: ${objects.take(3).joinToString()}")
        if (composables.isNotEmpty()) parts.add("@Composable: ${composables.take(5).joinToString()}")
        else if (functions.isNotEmpty()) {
            val nonComposable = functions.filter { f ->
                composableNames.none { f.startsWith(it) }
            }
            if (nonComposable.isNotEmpty()) parts.add("fun: ${nonComposable.take(5).joinToString()}")
        }

        return if (parts.isEmpty()) "Kotlin source" else parts.joinToString("; ")
    }

    private fun generateJavaSummary(content: String): String {
        val classes = Regex("(?:public |abstract )?class (\\w+)(?:<[^>]+>)?(?:\\s+extends\\s+\\w+)?(?:\\s+implements\\s+[^{]+)?").findAll(content)
            .map { it.groupValues[1] }.toList()
        val interfaces = Regex("(?:public )?interface (\\w+)").findAll(content).map { it.groupValues[1] }.toList()
        val methods = Regex("(?:public|private|protected)\\s+(?:static\\s+)?(?:\\w+(?:<[^>]+>)?)\\s+(\\w+)\\(([^)]*)\\)").findAll(content)
            .map { "${it.groupValues[1]}(${it.groupValues[2].take(40)})" }.toList()

        val parts = mutableListOf<String>()
        if (classes.isNotEmpty()) parts.add("class: ${classes.take(5).joinToString()}")
        if (interfaces.isNotEmpty()) parts.add("interface: ${interfaces.take(3).joinToString()}")
        if (methods.isNotEmpty()) parts.add("method: ${methods.take(5).joinToString()}")

        return if (parts.isEmpty()) "Java source" else parts.joinToString("; ")
    }

    private fun parseGradleDependencies(projectDir: File): String {
        val buildFile = findFiles(projectDir, "build.gradle").firstOrNull() ?: return ""
        return try {
            val content = buildFile.readText()
            val deps = Regex("implementation ['\"]([^'\"]+)['\"]").findAll(content)
                .map { it.groupValues[1] }.toList()
            if (deps.isEmpty()) "" else deps.joinToString("\n")
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseManifest(projectDir: File): String {
        val manifestFile = findFiles(projectDir, "AndroidManifest.xml").firstOrNull() ?: return ""
        return try {
            val content = manifestFile.readText()

            val package_ = Regex("package=\"([^\"]+)\"").find(content)?.groupValues?.get(1) ?: ""
            val activities = Regex("<activity[^>]*android:name=\"([^\"]+)\"").findAll(content)
                .map { it.groupValues[1] }.toList()
            val permissions = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"").findAll(content)
                .map { it.groupValues[1] }.toList()

            val sb = StringBuilder()
            sb.append("包名: $package_\n")
            if (activities.isNotEmpty()) sb.append("Activity: ${activities.joinToString()}\n")
            if (permissions.isNotEmpty()) sb.append("权限: ${permissions.joinToString()}\n")

            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun findFiles(dir: File, suffix: String): List<File> {
        val result = mutableListOf<File>()
        try {
            dir.walk().forEach { file ->
                if (file.isFile && file.name.endsWith(suffix)) {
                    if (!file.absolutePath.contains("/build/") &&
                        !file.absolutePath.contains("/.gradle/") &&
                        !file.absolutePath.contains("/.idea/")) {
                        result.add(file)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }
}
