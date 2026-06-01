package com.example.androiddevagent.agent.tools

import com.example.androiddevagent.agent.llm.ChatCompletionRequest
import com.example.androiddevagent.agent.memory.ProjectSummaryGenerator
import com.example.androiddevagent.agent.vcs.GitIntegration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ToolResult(
    val output: String,
    val success: Boolean
)

@Singleton
class ToolExecutor @Inject constructor(
    private val gitIntegration: GitIntegration,
    private val projectSummaryGenerator: ProjectSummaryGenerator
) {

    private var projectPath: String = ""
    private val gson = Gson()

    fun setProjectPath(path: String) {
        projectPath = path
        gitIntegration.setProjectPath(path)
    }

    fun getProjectPath(): String = projectPath

    fun execute(call: ChatCompletionRequest.ToolCall): ToolResult {
        val args = parseArgs(call.function.arguments)
        return when (call.function.name) {
            "read_file" -> readFile(args)
            "write_file" -> writeFile(args)
            "edit_file" -> editFile(args)
            "list_files" -> listFiles(args)
            "delete_file" -> deleteFile(args)
            "gradle_build" -> gradleBuild(args)
            "run_tests" -> runTests(args)
            "read_logcat" -> readLogcat(args)
            "lint_check" -> lintCheck(args)
            "search_code" -> searchCode(args)
            "analyze_project" -> analyzeProject()
            "find_usages" -> findUsages(args)
            "git_commit" -> gitCommit(args)
            "git_diff" -> gitDiff(args)
            "git_revert" -> gitRevert()
            else -> ToolResult("未知工具: ${call.function.name}", false)
        }
    }

    private fun readFile(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult("缺少 'path' 参数", false)
        val file = File(projectPath, path)

        if (!file.exists()) {
            return ToolResult("文件不存在: $path", false)
        }
        if (!file.isFile) {
            return ToolResult("不是文件: $path", false)
        }

        val lines = file.readLines()
        val startLine = (args["start_line"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val endLine = (args["end_line"]?.toIntOrNull() ?: (startLine + 99)).coerceAtMost(lines.size)

        val content = lines.subList(startLine - 1, endLine).mapIndexed { idx, line ->
            "${startLine + idx}→$line"
        }.joinToString("\n")

        return ToolResult(
            "文件: $path (第 $startLine-$endLine 行，共 ${lines.size} 行)\n$content",
            true
        )
    }

    private fun writeFile(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult("缺少 'path' 参数", false)
        val content = args["content"] ?: return ToolResult("缺少 'content' 参数", false)
        val file = File(projectPath, path)

        file.parentFile?.mkdirs()
        file.writeText(content)

        val lineCount = content.lines().size
        return ToolResult("写入成功: $path ($lineCount 行)", true)
    }

    private fun editFile(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult("缺少 'path' 参数", false)
        val oldText = args["old_text"] ?: return ToolResult("缺少 'old_text' 参数", false)
        val newText = args["new_text"] ?: return ToolResult("缺少 'new_text' 参数", false)
        val file = File(projectPath, path)

        if (!file.exists()) {
            return ToolResult("文件不存在: $path", false)
        }

        val content = file.readText()
        if (!content.contains(oldText)) {
            val preview = content.take(500)
            return ToolResult(
                "在 $path 中未找到要替换的文本，请确保文本完全匹配。\n文件预览:\n$preview",
                false
            )
        }

        if (content.indexOf(oldText) != content.lastIndexOf(oldText)) {
            return ToolResult(
                "要替换的文本在 $path 中出现多次，请提供更多上下文使其唯一。",
                false
            )
        }

        val newContent = content.replace(oldText, newText)
        file.writeText(newContent)

        val lintResult = quickLint(path, newContent)
        if (!lintResult.passed) {
            file.writeText(content)
            return ToolResult(
                "编辑导致语法问题，已自动回滚:\n${lintResult.errors.joinToString("\n")}\n请修复后重试。",
                false
            )
        }

        val lineCount = newContent.lines().size
        return ToolResult("编辑成功: $path ($lineCount 行)", true)
    }

    private fun listFiles(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: "."
        val maxDepth = args["max_depth"]?.toIntOrNull() ?: 3
        val dir = File(projectPath, path)

        if (!dir.exists() || !dir.isDirectory) {
            return ToolResult("目录不存在: $path", false)
        }

        val tree = buildTree(dir, maxDepth, 0)
        return ToolResult("目录: $path\n$tree", true)
    }

    private fun deleteFile(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult("缺少 'path' 参数", false)
        val file = File(projectPath, path)

        if (!file.exists()) {
            return ToolResult("文件不存在: $path", false)
        }

        val deleted = file.delete()
        return if (deleted) {
            ToolResult("已删除: $path", true)
        } else {
            ToolResult("删除失败: $path", false)
        }
    }

    private fun gradleBuild(args: Map<String, String>): ToolResult {
        val task = args["task"] ?: "assembleDebug"
        val projectDir = File(projectPath)

        if (!projectDir.exists()) {
            return ToolResult("项目目录不存在: $projectPath", false)
        }

        val gradlew = File(projectDir, if (File(projectDir, "gradlew").exists()) "gradlew" else "gradle")
        if (!gradlew.exists()) {
            return ToolResult("项目中未找到 Gradle 包装器", false)
        }

        return try {
            val process = ProcessBuilder()
                .command(gradlew.absolutePath, task)
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            if (process.exitValue() == 0) {
                val summary = extractBuildSummary(output, true)
                ToolResult("构建成功: $task\n$summary", true)
            } else {
                val errorSummary = extractErrorSummary(output)
                ToolResult("构建失败: $task\n$errorSummary", false)
            }
        } catch (e: Exception) {
            ToolResult("构建执行错误: ${e.message}", false)
        }
    }

    private fun runTests(args: Map<String, String>): ToolResult {
        val testClass = args["test_class"]
        val projectDir = File(projectPath)

        if (!projectDir.exists()) {
            return ToolResult("项目目录不存在: $projectPath", false)
        }

        val gradlew = File(projectDir, if (File(projectDir, "gradlew").exists()) "gradlew" else "gradle")
        if (!gradlew.exists()) {
            return ToolResult("项目中未找到 Gradle 包装器", false)
        }

        val task = if (testClass != null) {
            "test --tests $testClass"
        } else {
            "test"
        }

        return try {
            val process = ProcessBuilder()
                .command(gradlew.absolutePath, *task.split(" ").toTypedArray())
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            val testSummary = extractTestSummary(output)
            if (process.exitValue() == 0) {
                ToolResult("测试通过\n$testSummary", true)
            } else {
                ToolResult("测试失败\n$testSummary", false)
            }
        } catch (e: Exception) {
            ToolResult("测试执行错误: ${e.message}", false)
        }
    }

    private fun readLogcat(args: Map<String, String>): ToolResult {
        val filter = args["filter"] ?: ""
        val lines = args["lines"]?.toIntOrNull() ?: 50

        return try {
            val command = mutableListOf("logcat", "-d", "-t", lines.toString())
            if (filter.isNotEmpty()) {
                command.addAll(listOf("-s", filter))
            }

            val process = ProcessBuilder()
                .command(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            if (output.isBlank()) {
                ToolResult("暂无 Logcat 输出", true)
            } else {
                val filtered = if (filter.isNotEmpty()) {
                    output.lines().filter { it.contains(filter, ignoreCase = true) }.take(lines)
                } else {
                    output.lines().take(lines)
                }
                ToolResult("Logcat (最近 $lines 行):\n${filtered.joinToString("\n")}", true)
            }
        } catch (e: Exception) {
            ToolResult("Logcat 读取错误: ${e.message}。注意: logcat 需要连接设备或模拟器。", false)
        }
    }

    private fun lintCheck(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult("缺少 'path' 参数", false)
        val file = File(projectPath, path)

        if (!file.exists()) {
            return ToolResult("文件不存在: $path", false)
        }
        if (!file.isFile) {
            return ToolResult("不是文件: $path", false)
        }

        val content = file.readText()
        val lintResult = quickLint(path, content)

        return if (lintResult.passed) {
            ToolResult("语法检查通过: $path", true)
        } else {
            val errorsText = lintResult.errors.joinToString("\n") { "  - $it" }
            ToolResult("$path 语法检查发现问题:\n$errorsText", false)
        }
    }

    private fun extractErrorSummary(output: String): String {
        val errorLines = output.lines().filter { line ->
            line.contains("error:", ignoreCase = true) ||
            line.contains("ERROR", ignoreCase = true) ||
            line.startsWith("e:")
        }

        if (errorLines.isEmpty()) {
            val lastLines = output.lines().takeLast(20)
            return "未找到具体错误行。最后输出:\n${lastLines.joinToString("\n")}"
        }

        val distinctErrors = errorLines.distinctBy { it.trim() }.take(10)
        return distinctErrors.joinToString("\n")
    }

    private fun extractBuildSummary(output: String, success: Boolean): String {
        val summaryLines = output.lines().filter { line ->
            line.contains("BUILD") ||
            line.contains("Task :") && line.contains("seconds")
        }.takeLast(5)
        return summaryLines.joinToString("\n")
    }

    private fun extractTestSummary(output: String): String {
        val summaryLines = output.lines().filter { line ->
            line.contains("tests completed") ||
            line.contains("tests passed") ||
            line.contains("tests failed") ||
            line.contains("Test ") && line.contains("PASSED") ||
            line.contains("Test ") && line.contains("FAILED")
        }.takeLast(10)
        return if (summaryLines.isEmpty()) {
            output.lines().takeLast(15).joinToString("\n")
        } else {
            summaryLines.joinToString("\n")
        }
    }

    private fun searchCode(args: Map<String, String>): ToolResult {
        val query = args["query"] ?: return ToolResult("缺少 'query' 参数", false)
        val filePattern = args["file_pattern"] ?: ""
        val projectDir = File(projectPath)

        if (!projectDir.exists()) {
            return ToolResult("项目目录不存在: $projectPath", false)
        }

        val results = mutableListOf<String>()
        val extensions = if (filePattern.isNotEmpty()) {
            listOf(filePattern)
        } else {
            listOf(".kt", ".java", ".xml", ".gradle", ".properties", ".kts")
        }

        projectDir.walk().forEach { file ->
            if (!file.isFile) return@forEach
            if (file.absolutePath.contains("/build/")) return@forEach
            if (file.absolutePath.contains("/.gradle/")) return@forEach
            if (file.absolutePath.contains("/.idea/")) return@forEach
            if (extensions.none { ext -> file.name.endsWith(ext) }) return@forEach

            try {
                val lines = file.readLines()
                val relativePath = file.relativeTo(projectDir).path
                for ((index, line) in lines.withIndex()) {
                    if (line.contains(query, ignoreCase = true)) {
                        results.add("$relativePath:${index + 1}: ${line.trim().take(100)}")
                        if (results.size >= 30) break
                    }
                }
            } catch (_: Exception) {
            }
            if (results.size >= 30) return@forEach
        }

        return if (results.isEmpty()) {
            ToolResult("未找到匹配: $query", true)
        } else {
            ToolResult("找到 ${results.size} 处匹配 '$query':\n${results.joinToString("\n")}", true)
        }
    }

    private fun analyzeProject(): ToolResult {
        if (projectPath.isEmpty()) {
            return ToolResult("未设置项目路径", false)
        }

        return try {
            val summary = projectSummaryGenerator.generate(projectPath)
            val sb = StringBuilder()
            sb.append("项目分析\n")
            sb.append("结构:\n${summary.structure}\n")
            if (summary.keyFiles.isNotEmpty()) {
                sb.append("关键文件:\n")
                summary.keyFiles.take(20).forEach { f ->
                    sb.append("- ${f.path}: ${f.summary} (${f.lineCount} 行)\n")
                }
            }
            if (summary.gradleDependencies.isNotEmpty()) {
                sb.append("依赖项:\n${summary.gradleDependencies}\n")
            }
            if (summary.manifestInfo.isNotEmpty()) {
                sb.append("清单文件:\n${summary.manifestInfo}\n")
            }
            ToolResult(sb.toString(), true)
        } catch (e: Exception) {
            ToolResult("项目分析失败: ${e.message}", false)
        }
    }

    private fun findUsages(args: Map<String, String>): ToolResult {
        val symbol = args["symbol"] ?: return ToolResult("缺少 'symbol' 参数", false)
        val searchPath = args["path"] ?: "."
        val projectDir = File(projectPath, searchPath)

        if (!projectDir.exists()) {
            return ToolResult("目录不存在: $searchPath", false)
        }

        val results = mutableListOf<String>()
        val wordBoundaryPattern = Regex("\\b${Regex.escape(symbol)}\\b")

        projectDir.walk().forEach { file ->
            if (!file.isFile) return@forEach
            if (file.absolutePath.contains("/build/")) return@forEach
            if (file.absolutePath.contains("/.gradle/")) return@forEach
            if (file.absolutePath.contains("/.idea/")) return@forEach
            val ext = file.extension
            if (ext !in listOf("kt", "java", "xml", "gradle", "kts")) return@forEach

            try {
                val lines = file.readLines()
                val relativePath = file.relativeTo(File(projectPath)).path
                for ((index, line) in lines.withIndex()) {
                    if (wordBoundaryPattern.containsMatchIn(line)) {
                        val isDeclaration = line.contains("fun $symbol") ||
                                line.contains("class $symbol") ||
                                line.contains("object $symbol") ||
                                line.contains("val $symbol") ||
                                line.contains("var $symbol") ||
                                line.contains("interface $symbol")
                        val tag = if (isDeclaration) "声明" else "引用"
                        results.add("[$tag] $relativePath:${index + 1}: ${line.trim().take(100)}")
                        if (results.size >= 30) break
                    }
                }
            } catch (_: Exception) {
            }
            if (results.size >= 30) return@forEach
        }

        return if (results.isEmpty()) {
            ToolResult("未找到符号引用: $symbol", true)
        } else {
            ToolResult("找到 ${results.size} 处 '$symbol' 的引用:\n${results.joinToString("\n")}", true)
        }
    }

    private fun gitCommit(args: Map<String, String>): ToolResult {
        val message = args["message"] ?: return ToolResult("缺少 'message' 参数", false)
        val result = gitIntegration.autoCommit(message)
        return if (result.success) {
            ToolResult("已提交: ${result.output.take(200)}", true)
        } else {
            ToolResult("Git 提交失败: ${result.output}", false)
        }
    }

    private fun gitDiff(args: Map<String, String>): ToolResult {
        val stat = args["stat"]?.toBoolean() ?: true
        val result = if (stat) gitIntegration.getDiffStat() else gitIntegration.getDiff()
        return if (result.success) {
            ToolResult("Git 差异:\n${result.output.take(1000)}", true)
        } else {
            ToolResult("Git 差异获取失败: ${result.output}", false)
        }
    }

    private fun gitRevert(): ToolResult {
        val result = gitIntegration.revertLastCommit()
        return if (result.success) {
            ToolResult("已回退上次提交: ${result.output.take(200)}", true)
        } else {
            ToolResult("Git 回退失败: ${result.output}", false)
        }
    }

    private fun buildTree(dir: File, maxDepth: Int, currentDepth: Int): String {
        if (currentDepth >= maxDepth) return ""
        val indent = "  ".repeat(currentDepth)
        val sb = StringBuilder()
        val files = dir.listFiles()?.sortedBy { !it.isDirectory } ?: return ""
        for (file in files) {
            if (file.name.startsWith(".") && file.name != ".github") continue
            if (file.name in listOf("build", ".gradle", ".idea", "node_modules")) continue
            sb.append("$indent${if (file.isDirectory) "📁" else "📄"} ${file.name}\n")
            if (file.isDirectory && currentDepth < maxDepth - 1) {
                sb.append(buildTree(file, maxDepth, currentDepth + 1))
            }
        }
        return sb.toString()
    }

    private fun quickLint(path: String, content: String): LintResult {
        val errors = mutableListOf<String>()
        val ext = path.substringAfterLast('.', "")

        when (ext) {
            "kt", "java" -> {
                val openBraces = content.count { it == '{' }
                val closeBraces = content.count { it == '}' }
                if (openBraces != closeBraces) {
                    errors.add("大括号不匹配: $openBraces 个开, $closeBraces 个闭")
                }
                val openParens = content.count { it == '(' }
                val closeParens = content.count { it == ')' }
                if (openParens != closeParens) {
                    errors.add("圆括号不匹配: $openParens 个开, $closeParens 个闭")
                }
            }
            "xml" -> {
                val openTags = Regex("<([a-zA-Z][a-zA-Z0-9]*)[^/]*>").findAll(content).count()
                val closeTags = Regex("</[a-zA-Z][a-zA-Z0-9]*>").findAll(content).count()
                val selfClosing = Regex("<[a-zA-Z][a-zA-Z0-9]*[^>]*/>").findAll(content).count()
                if (openTags - selfClosing != closeTags) {
                    errors.add("XML 标签不匹配: $openTags 个开标签, $closeTags 个闭标签, $selfClosing 个自闭合")
                }
            }
        }

        return LintResult(errors, errors.isEmpty())
    }

    private data class LintResult(val errors: List<String>, val passed: Boolean)

    private fun parseArgs(json: String): Map<String, String> {
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val raw: Map<String, Any> = gson.fromJson(json, type)
            raw.mapValues { it.value.toString() }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
