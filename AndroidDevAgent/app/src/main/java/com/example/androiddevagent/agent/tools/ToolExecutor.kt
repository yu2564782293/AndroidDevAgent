package com.example.androiddevagent.agent.tools

import com.example.androiddevagent.agent.build.TermuxIntegration
import com.example.androiddevagent.agent.llm.ChatCompletionRequest
import com.example.androiddevagent.agent.memory.ProjectSummaryGenerator
import com.example.androiddevagent.agent.vcs.GitHubApiService
import com.example.androiddevagent.agent.skills.SkillManager
import com.example.androiddevagent.agent.vcs.GitIntegration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
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
    private val projectSummaryGenerator: ProjectSummaryGenerator,
    private val termuxIntegration: TermuxIntegration,
    private val githubApiService: GitHubApiService,
    private val skillManager: SkillManager
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
            "run_command" -> runCommand(args)
            "install_apk" -> installApk(args)
            "launch_app" -> launchApp(args)
            "git_clone" -> gitClone(args)
            "git_push" -> gitPush(args)
            "git_pull" -> gitPull(args)
            "git_branch" -> gitBranch(args)
            "ask_user" -> askUser(args)
            "glob" -> globFiles(args)
            "grep" -> grepContent(args)
            "todo_write" -> todoWrite(args)
            "github_read_file" -> githubReadFile(args)
            "github_write_file" -> githubWriteFile(args)
            "github_list_dir" -> githubListDir(args)
            "github_delete_file" -> githubDeleteFile(args)
            "github_branch" -> githubBranch(args)
            "github_repo_info" -> githubRepoInfo()
            "github_commits" -> githubCommits(args)
            "github_create_pr" -> githubCreatePR(args)
            "github_search_code" -> githubSearchCode(args)
            "skill_search" -> skillSearch(args)
            "skill_install" -> skillInstall(args)
            "skill_list" -> skillList()
            "skill_uninstall" -> skillUninstall(args)
            "skill_update" -> skillUpdate(args)
            "skill_config" -> skillConfig(args)
            else -> kotlinx.coroutines.runBlocking {
                skillManager.executeSkillTool(call.function.name, parseArgs(call.function.arguments), projectPath)
            }
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
        val replaceAll = args["replace_all"]?.toBoolean() ?: false
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

        if (!replaceAll && content.indexOf(oldText) != content.lastIndexOf(oldText)) {
            val occurrences = content.split(oldText).size - 1
            return ToolResult(
                "要替换的文本在 $path 中出现 $occurrences 次。请提供更多上下文使其唯一，或设置 replace_all=true 替换所有。",
                false
            )
        }

        val newContent = if (replaceAll) content.replace(oldText, newText) else content.replaceFirst(oldText, newText)
        file.writeText(newContent)

        val lintResult = quickLint(path, newContent)
        if (!lintResult.passed) {
            file.writeText(content)
            return ToolResult(
                "编辑导致语法问题，已自动回滚:\n${lintResult.errors.joinToString("\n")}\n请修复后重试。",
                false
            )
        }

        val occurrences = if (replaceAll) content.split(oldText).size - 1 else 1
        val lineCount = newContent.lines().size
        return ToolResult("编辑成功: $path (替换 $occurrences 处, $lineCount 行)", true)
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

        val gradlew = File(projectDir, "gradlew")
        val gradleBat = File(projectDir, "gradlew.bat")

        if (!gradlew.exists() && !gradleBat.exists()) {
            val hasGradleHome = System.getenv("GRADLE_HOME") != null || 
                File("/usr/local/bin/gradle").exists() ||
                File("/usr/bin/gradle").exists()
            if (hasGradleHome) {
                return tryGradleCommand(projectDir, task)
            }
            return ToolResult(
                "项目中未找到 Gradle 包装器 (gradlew)。\n" +
                "解决方案：\n" +
                "1. 在项目根目录运行: gradle wrapper\n" +
                "2. 或从已有项目复制 gradlew 和 gradle/wrapper/ 目录\n" +
                "3. 或通过 Termux 安装: pkg install gradle && gradle wrapper",
                false
            )
        }

        if (gradlew.exists() && !gradlew.canExecute()) {
            gradlew.setExecutable(true)
        }

        return tryTermuxOrLocal(projectDir, if (gradlew.exists()) "./gradlew" else "gradle", task)
    }

    private fun tryTermuxOrLocal(projectDir: File, gradleCmd: String, task: String): ToolResult {
        val termuxResult = termuxIntegration.executeLocalCommand(
            "$gradleCmd $task --stacktrace",
            projectPath,
            300000L
        )
        if (termuxResult.success) {
            val summary = extractBuildSummary(termuxResult.output, true)
            return ToolResult("构建成功: $task\n$summary", true)
        }

        val output = termuxResult.output
        if (output.contains("Permission denied") || output.contains("not executable")) {
            return try {
                termuxIntegration.executeLocalCommand("chmod +x gradlew && ./gradlew $task --stacktrace", projectPath, 300000L)
                val retryResult = termuxIntegration.executeLocalCommand("./gradlew $task --stacktrace", projectPath, 300000L)
                if (retryResult.success) {
                    val summary = extractBuildSummary(retryResult.output, true)
                    ToolResult("构建成功: $task\n$summary", true)
                } else {
                    val errorSummary = extractErrorSummary(retryResult.output)
                    ToolResult("构建失败: $task\n$errorSummary", false)
                }
            } catch (e: Exception) {
                ToolResult("构建执行错误: ${e.message}\n提示: 确保已安装 Termux 并授予存储权限", false)
            }
        }

        val errorSummary = extractErrorSummary(output)
        return ToolResult("构建失败: $task\n$errorSummary", false)
    }

    private fun tryGradleCommand(projectDir: File, task: String): ToolResult {
        return try {
            val result = termuxIntegration.executeLocalCommand("gradle $task --stacktrace", projectPath, 300000L)
            if (result.success) {
                val summary = extractBuildSummary(result.output, true)
                ToolResult("构建成功: $task\n$summary", true)
            } else {
                val errorSummary = extractErrorSummary(result.output)
                ToolResult("构建失败: $task\n$errorSummary", false)
            }
        } catch (e: Exception) {
            ToolResult("Gradle 命令不可用: ${e.message}", false)
        }
    }

    private fun runTests(args: Map<String, String>): ToolResult {
        val testClass = args["test_class"]
        val projectDir = File(projectPath)

        if (!projectDir.exists()) {
            return ToolResult("项目目录不存在: $projectPath", false)
        }

        val gradlew = File(projectDir, "gradlew")
        if (!gradlew.exists()) {
            return ToolResult("项目中未找到 Gradle 包装器 (gradlew)，无法运行测试", false)
        }
        if (!gradlew.canExecute()) {
            gradlew.setExecutable(true)
        }

        val task = if (testClass != null) {
            "./gradlew test --tests $testClass --stacktrace"
        } else {
            "./gradlew test --stacktrace"
        }

        return try {
            val result = termuxIntegration.executeLocalCommand(task, projectPath, 300000L)
            val testSummary = extractTestSummary(result.output)
            if (result.success) {
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

    private fun runCommand(args: Map<String, String>): ToolResult {
        val command = args["command"] ?: return ToolResult("缺少 'command' 参数", false)
        val workingDir = args["working_dir"] ?: projectPath
        val timeout = args["timeout"]?.toLongOrNull() ?: 120000L
        val result = termuxIntegration.executeLocalCommand(command, workingDir, timeout)
        return ToolResult(
            if (result.success) "命令执行成功 (退出码: ${result.exitCode})\n${result.output.take(2000)}"
            else "命令执行失败 (退出码: ${result.exitCode})\n${result.output.take(2000)}",
            result.success
        )
    }

    private fun installApk(args: Map<String, String>): ToolResult {
        val apkPath = args["apk_path"] ?: return ToolResult("缺少 'apk_path' 参数", false)
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            val searchResult = findLatestApk()
            if (searchResult != null) {
                return ToolResult("APK 路径不存在: $apkPath\n找到最新 APK: ${searchResult.absolutePath}\n请使用此路径重试。", false)
            }
            return ToolResult("APK 文件不存在: $apkPath", false)
        }
        return ToolResult("APK 安装请求已发送: $apkPath\n请在弹出的安装界面中确认安装。", true)
    }

    private fun launchApp(args: Map<String, String>): ToolResult {
        val packageName = args["package_name"] ?: return ToolResult("缺少 'package_name' 参数", false)
        return ToolResult("应用启动请求已发送: $packageName", true)
    }

    private fun findLatestApk(): File? {
        val buildDir = File(projectPath, "app/build/outputs/apk/debug")
        if (!buildDir.exists()) return null
        return buildDir.listFiles()?.filter { it.name.endsWith(".apk") }?.maxByOrNull { it.lastModified() }
    }

    private fun parseArgs(json: String): Map<String, String> {
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val raw: Map<String, Any> = gson.fromJson(json, type)
            raw.mapValues { it.value.toString() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun gitClone(args: Map<String, String>): ToolResult {
        val url = args["url"] ?: return ToolResult("缺少 'url' 参数", false)
        val directory = args["directory"] ?: return ToolResult("缺少 'directory' 参数", false)
        val result = gitIntegration.clone(url, directory)
        return if (result.success) {
            ToolResult("仓库克隆成功: $url → $directory\n${result.output.take(500)}", true)
        } else {
            ToolResult("仓库克隆失败: ${result.output}", false)
        }
    }

    private fun gitPush(args: Map<String, String>): ToolResult {
        val remote = args["remote"] ?: "origin"
        val branch = args["branch"] ?: ""
        val result = gitIntegration.push(remote, branch)
        return if (result.success) {
            ToolResult("推送成功: ${result.output.take(500)}", true)
        } else {
            ToolResult("推送失败: ${result.output}\n提示: 请在设置中配置 GitHub Token", false)
        }
    }

    private fun gitPull(args: Map<String, String>): ToolResult {
        val remote = args["remote"] ?: "origin"
        val branch = args["branch"] ?: ""
        val result = gitIntegration.pull(remote, branch)
        return if (result.success) {
            ToolResult("拉取成功: ${result.output.take(500)}", true)
        } else {
            ToolResult("拉取失败: ${result.output}", false)
        }
    }

    private fun gitBranch(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult("缺少 'action' 参数", false)
        val name = args["name"] ?: ""
        return when (action) {
            "list" -> {
                val result = gitIntegration.listBranches()
                val current = gitIntegration.getCurrentBranch()
                val currentBranch = if (current.success) current.output else "unknown"
                if (result.success) {
                    ToolResult("当前分支: $currentBranch\n${result.output}", true)
                } else {
                    ToolResult("分支列表获取失败: ${result.output}", false)
                }
            }
            "create" -> {
                if (name.isEmpty()) return ToolResult("创建分支需要 'name' 参数", false)
                val result = gitIntegration.createBranch(name)
                if (result.success) {
                    ToolResult("已创建并切换到分支: $name", true)
                } else {
                    ToolResult("创建分支失败: ${result.output}", false)
                }
            }
            "switch" -> {
                if (name.isEmpty()) return ToolResult("切换分支需要 'name' 参数", false)
                val result = gitIntegration.switchBranch(name)
                if (result.success) {
                    ToolResult("已切换到分支: $name", true)
                } else {
                    ToolResult("切换分支失败: ${result.output}", false)
                }
            }
            else -> ToolResult("未知分支操作: $action (支持: list, create, switch)", false)
        }
    }

    private fun askUser(args: Map<String, String>): ToolResult {
        val question = args["question"] ?: return ToolResult("缺少 'question' 参数", false)
        return ToolResult("[等待用户回复] 问题: $question\n注意: 当前版本暂不支持实时交互，请将回答直接告诉 Agent。", true)
    }

    private fun globFiles(args: Map<String, String>): ToolResult {
        val pattern = args["pattern"] ?: return ToolResult("缺少 'pattern' 参数", false)
        val searchPath = args["path"] ?: "."
        val dir = File(projectPath, searchPath)

        if (!dir.exists() || !dir.isDirectory) {
            return ToolResult("目录不存在: $searchPath", false)
        }

        val regex = globToRegex(pattern)
        val results = mutableListOf<String>()

        dir.walk().forEach { file ->
            if (!file.isFile) return@forEach
            if (file.absolutePath.contains("/build/")) return@forEach
            if (file.absolutePath.contains("/.gradle/")) return@forEach
            if (file.absolutePath.contains("/.idea/")) return@forEach
            if (file.absolutePath.contains("/.git/")) return@forEach

            val relativePath = file.relativeTo(File(projectPath)).path
            if (regex.matches(relativePath)) {
                results.add(relativePath)
                if (results.size >= 50) return@forEach
            }
        }

        return if (results.isEmpty()) {
            ToolResult("未找到匹配 '$pattern' 的文件", true)
        } else {
            ToolResult("找到 ${results.size} 个文件:\n${results.joinToString("\n")}", true)
        }
    }

    private fun grepContent(args: Map<String, String>): ToolResult {
        val pattern = args["pattern"] ?: return ToolResult("缺少 'pattern' 参数", false)
        val searchPath = args["path"] ?: "."
        val filePattern = args["file_pattern"] ?: ""
        val caseInsensitive = args["case_insensitive"]?.toBoolean() ?: false

        val dir = File(projectPath, searchPath)
        if (!dir.exists() || !dir.isDirectory) {
            return ToolResult("目录不存在: $searchPath", false)
        }

        val regex = try {
            if (caseInsensitive) Regex(pattern, RegexOption.IGNORE_CASE)
            else Regex(pattern)
        } catch (e: Exception) {
            return ToolResult("无效的正则表达式: ${e.message}", false)
        }

        val extensions = if (filePattern.isNotEmpty()) {
            listOf(filePattern)
        } else {
            listOf(".kt", ".java", ".xml", ".gradle", ".properties", ".kts", ".json", ".yaml", ".yml", ".toml")
        }

        val results = mutableListOf<String>()

        dir.walk().forEach { file ->
            if (!file.isFile) return@forEach
            if (file.absolutePath.contains("/build/")) return@forEach
            if (file.absolutePath.contains("/.gradle/")) return@forEach
            if (file.absolutePath.contains("/.idea/")) return@forEach
            if (file.absolutePath.contains("/.git/")) return@forEach
            if (extensions.none { ext -> file.name.endsWith(ext) }) return@forEach

            try {
                val lines = file.readLines()
                val relativePath = file.relativeTo(File(projectPath)).path
                for ((index, line) in lines.withIndex()) {
                    if (regex.containsMatchIn(line)) {
                        results.add("$relativePath:${index + 1}: ${line.trim().take(120)}")
                        if (results.size >= 30) break
                    }
                }
            } catch (_: Exception) {
            }
            if (results.size >= 30) return@forEach
        }

        return if (results.isEmpty()) {
            ToolResult("未找到匹配 '$pattern' 的内容", true)
        } else {
            ToolResult("找到 ${results.size} 处匹配:\n${results.joinToString("\n")}", true)
        }
    }

    private var currentTodos: List<Map<String, String>> = emptyList()

    private fun todoWrite(args: Map<String, String>): ToolResult {
        val todosJson = args["todos"] ?: return ToolResult("缺少 'todos' 参数", false)
        return try {
            val type = object : TypeToken<List<Map<String, String>>>() {}.type
            currentTodos = gson.fromJson(todosJson, type)
            val summary = currentTodos.mapIndexed { idx, todo ->
                val status = when (todo["status"]) {
                    "completed" -> "✅"
                    "in_progress" -> "🔄"
                    else -> "⬜"
                }
                "$status ${idx + 1}. ${todo["content"]}"
            }.joinToString("\n")
            ToolResult("任务列表已更新:\n$summary", true)
        } catch (e: Exception) {
            ToolResult("无效的 todos 格式: ${e.message}", false)
        }
    }

    private fun globToRegex(pattern: String): Regex {
        var regex = pattern
            .replace(".", "\\.")
            .replace("**/", "(__ANYDIR__/)?")
            .replace("**", "(__ANYDIR__/)?__ANY__")
            .replace("*", "[^/]*")
            .replace("?", "[^/]")
            .replace("(__ANYDIR__/)?", "(?:.*/)?")
            .replace("__ANY__", ".*")
        return Regex("^$regex$")
    }

    private fun githubReadFile(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult("缺少 'path' 参数", false)
        val branch = args["branch"] ?: ""
        if (!githubApiService.isConfigured()) {
            return ToolResult("未连接 GitHub 仓库，请先在首页连接仓库并确保已配置 GitHub Token", false)
        }
        return try {
            val result = kotlinx.coroutines.runBlocking {
                githubApiService.readFile(path, branch)
            }
            result.fold(
                onSuccess = { file ->
                    val lineCount = file.content.lines().size
                    val content = file.content.lines().mapIndexed { idx, line ->
                        "${idx + 1}→$line"
                    }.joinToString("\n")
                    ToolResult("GitHub 文件: $path ($lineCount 行, SHA: ${file.sha.take(7)})\n$content", true)
                },
                onFailure = { ToolResult("读取 GitHub 文件失败: ${it.message}", false) }
            )
        } catch (e: Exception) {
            ToolResult("GitHub API 错误: ${e.message}", false)
        }
    }

    private fun githubWriteFile(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult("缺少 'path' 参数", false)
        val content = args["content"] ?: return ToolResult("缺少 'content' 参数", false)
        val message = args["message"] ?: return ToolResult("缺少 'message' 参数", false)
        val branch = args["branch"] ?: ""
        if (!githubApiService.isConfigured()) {
            return ToolResult("未连接 GitHub 仓库，请先在首页连接仓库并确保已配置 GitHub Token", false)
        }
        return try {
            val result = kotlinx.coroutines.runBlocking {
                githubApiService.writeFile(path, content, message, branch)
            }
            result.fold(
                onSuccess = { commit ->
                    val lineCount = content.lines().size
                    ToolResult("已写入 GitHub 文件: $path ($lineCount 行)\n提交: ${commit.sha.take(7)}\n查看: ${commit.htmlUrl}", true)
                },
                onFailure = { ToolResult("写入 GitHub 文件失败: ${it.message}", false) }
            )
        } catch (e: Exception) {
            ToolResult("GitHub API 错误: ${e.message}", false)
        }
    }

    private fun githubListDir(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: ""
        val branch = args["branch"] ?: ""
        if (!githubApiService.isConfigured()) {
            return ToolResult("未连接 GitHub 仓库", false)
        }
        return try {
            val result = kotlinx.coroutines.runBlocking {
                githubApiService.listDirectory(path, branch)
            }
            result.fold(
                onSuccess = { entries ->
                    val listing = entries.map { entry ->
                        val icon = if (entry.type == "dir") "📁" else "📄"
                        val sizeStr = if (entry.type == "file") " (${entry.size}B)" else ""
                        "$icon ${entry.name}$sizeStr"
                    }.joinToString("\n")
                    val dirLabel = if (path.isEmpty()) "/" else path
                    ToolResult("GitHub 目录: $dirLabel (${entries.size} 项)\n$listing", true)
                },
                onFailure = { ToolResult("列出 GitHub 目录失败: ${it.message}", false) }
            )
        } catch (e: Exception) {
            ToolResult("GitHub API 错误: ${e.message}", false)
        }
    }

    private fun githubDeleteFile(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult("缺少 'path' 参数", false)
        val message = args["message"] ?: return ToolResult("缺少 'message' 参数", false)
        val branch = args["branch"] ?: ""
        if (!githubApiService.isConfigured()) {
            return ToolResult("未连接 GitHub 仓库", false)
        }
        return try {
            val result = kotlinx.coroutines.runBlocking {
                githubApiService.deleteFile(path, message, branch)
            }
            result.fold(
                onSuccess = { ToolResult("已从 GitHub 删除文件: $path\n提交: ${it.sha.take(7)}", true) },
                onFailure = { ToolResult("删除 GitHub 文件失败: ${it.message}", false) }
            )
        } catch (e: Exception) {
            ToolResult("GitHub API 错误: ${e.message}", false)
        }
    }

    private fun githubBranch(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult("缺少 'action' 参数", false)
        val name = args["name"] ?: ""
        val fromBranch = args["from_branch"] ?: ""
        if (!githubApiService.isConfigured()) {
            return ToolResult("未连接 GitHub 仓库", false)
        }
        return try {
            when (action) {
                "list" -> {
                    val result = kotlinx.coroutines.runBlocking { githubApiService.listBranches() }
                    result.fold(
                        onSuccess = { branches ->
                            val branchList = branches.map { b ->
                                val marker = if (b.isDefault) " (当前)" else ""
                                "  ${b.name}$marker"
                            }.joinToString("\n")
                            ToolResult("GitHub 分支列表:\n$branchList", true)
                        },
                        onFailure = { ToolResult("获取分支列表失败: ${it.message}", false) }
                    )
                }
                "create" -> {
                    if (name.isEmpty()) return ToolResult("创建分支需要 'name' 参数", false)
                    val result = kotlinx.coroutines.runBlocking { githubApiService.createBranch(name, fromBranch) }
                    result.fold(
                        onSuccess = { ToolResult(it, true) },
                        onFailure = { ToolResult("创建分支失败: ${it.message}", false) }
                    )
                }
                "switch" -> {
                    if (name.isEmpty()) return ToolResult("切换分支需要 'name' 参数", false)
                    val repo = githubApiService.getRepo()
                    if (repo != null) {
                        githubApiService.setRepo(repo.owner, repo.repo, name)
                        ToolResult("已切换到分支: $name", true)
                    } else {
                        ToolResult("未连接仓库", false)
                    }
                }
                else -> ToolResult("未知分支操作: $action (支持: list, create, switch)", false)
            }
        } catch (e: Exception) {
            ToolResult("GitHub API 错误: ${e.message}", false)
        }
    }

    private fun githubRepoInfo(): ToolResult {
        if (!githubApiService.isConfigured()) {
            return ToolResult("未连接 GitHub 仓库", false)
        }
        return try {
            val result = kotlinx.coroutines.runBlocking { githubApiService.getRepoInfo() }
            result.fold(
                onSuccess = { ToolResult("GitHub 仓库信息:\n$it", true) },
                onFailure = { ToolResult("获取仓库信息失败: ${it.message}", false) }
            )
        } catch (e: Exception) {
            ToolResult("GitHub API 错误: ${e.message}", false)
        }
    }

    private fun githubCommits(args: Map<String, String>): ToolResult {
        val count = args["count"]?.toIntOrNull() ?: 10
        if (!githubApiService.isConfigured()) {
            return ToolResult("未连接 GitHub 仓库", false)
        }
        return try {
            val result = kotlinx.coroutines.runBlocking { githubApiService.getRecentCommits(count) }
            result.fold(
                onSuccess = { ToolResult("GitHub 最近提交:\n$it", true) },
                onFailure = { ToolResult("获取提交记录失败: ${it.message}", false) }
            )
        } catch (e: Exception) {
            ToolResult("GitHub API 错误: ${e.message}", false)
        }
    }

    private fun githubCreatePR(args: Map<String, String>): ToolResult {
        val title = args["title"] ?: return ToolResult("缺少 'title' 参数", false)
        val body = args["body"] ?: ""
        val headBranch = args["head_branch"] ?: return ToolResult("缺少 'head_branch' 参数", false)
        val baseBranch = args["base_branch"] ?: ""
        if (!githubApiService.isConfigured()) {
            return ToolResult("未连接 GitHub 仓库", false)
        }
        return try {
            val result = kotlinx.coroutines.runBlocking {
                githubApiService.createPullRequest(title, body, headBranch, baseBranch)
            }
            result.fold(
                onSuccess = { pr ->
                    ToolResult("PR #${pr.number} 已创建: ${pr.title}\n状态: ${pr.state}\n${pr.headBranch} → ${pr.baseBranch}\n查看: ${pr.htmlUrl}", true)
                },
                onFailure = { ToolResult("创建 PR 失败: ${it.message}", false) }
            )
        } catch (e: Exception) {
            ToolResult("GitHub API 错误: ${e.message}", false)
        }
    }

    private fun githubSearchCode(args: Map<String, String>): ToolResult {
        val query = args["query"] ?: return ToolResult("缺少 'query' 参数", false)
        if (!githubApiService.isConfigured()) {
            return ToolResult("未连接 GitHub 仓库", false)
        }
        return try {
            val result = kotlinx.coroutines.runBlocking { githubApiService.searchCode(query) }
            result.fold(
                onSuccess = { ToolResult("GitHub 代码搜索 '$query':\n$it", true) },
                onFailure = { ToolResult("搜索失败: ${it.message}", false) }
            )
        } catch (e: Exception) {
            ToolResult("GitHub API 错误: ${e.message}", false)
        }
    }

    private fun skillSearch(args: Map<String, String>): ToolResult {
        val query = args["query"] ?: return ToolResult("缺少 'query' 参数", false)
        return try {
            val results = kotlinx.coroutines.runBlocking { skillManager.searchSkills(query) }
            if (results.isEmpty()) {
                ToolResult("未找到匹配 '$query' 的技能", true)
            } else {
                val listing = results.take(10).mapIndexed { idx, r ->
                    val installedTag = if (r.installed) " [已安装]" else ""
                    "${idx + 1}. ${r.icon} ${r.name} (${r.author}) ⭐${r.stars}$installedTag\n   ${r.description.take(80)}\n   来源: ${r.sourceUrl}"
                }.joinToString("\n\n")
                ToolResult("找到 ${results.size} 个技能:\n\n$listing", true)
            }
        } catch (e: Exception) {
            ToolResult("技能搜索失败: ${e.message}", false)
        }
    }

    private fun skillInstall(args: Map<String, String>): ToolResult {
        val source = args["source"] ?: "github"
        val repo = args["repo"] ?: return ToolResult("缺少 'repo' 参数", false)
        val branch = args["branch"] ?: "main"
        return try {
            val result = kotlinx.coroutines.runBlocking { skillManager.installSkill(source, repo, branch) }
            result.fold(
                onSuccess = { skill ->
                    val tools = skill.toolNames.joinToString(", ")
                    ToolResult("技能安装成功: ${skill.icon} ${skill.name} v${skill.version}\n描述: ${skill.description}\n提供工具: $tools\n风险等级: ${skill.riskLevel}", true)
                },
                onFailure = { ToolResult("技能安装失败: ${it.message}", false) }
            )
        } catch (e: Exception) {
            ToolResult("技能安装错误: ${e.message}", false)
        }
    }

    private fun skillList(): ToolResult {
        return try {
            val skills = skillManager.getInstalledSkills()
            if (skills.isEmpty()) {
                ToolResult("尚未安装任何技能。使用 skill_search 搜索可用技能。", true)
            } else {
                val listing = skills.mapIndexed { idx, s ->
                    val enabledTag = if (s.enabled) "✅" else "❌"
                    val tools = s.toolNames.joinToString(", ")
                    "$enabledTag ${s.icon} ${s.name} v${s.version} (${s.author})\n   ${s.description.take(60)}\n   工具: $tools"
                }.joinToString("\n\n")
                ToolResult("已安装 ${skills.size} 个技能:\n\n$listing", true)
            }
        } catch (e: Exception) {
            ToolResult("获取技能列表失败: ${e.message}", false)
        }
    }

    private fun skillUninstall(args: Map<String, String>): ToolResult {
        val skillId = args["skill_id"] ?: return ToolResult("缺少 'skill_id' 参数", false)
        return try {
            val result = kotlinx.coroutines.runBlocking { skillManager.uninstallSkill(skillId) }
            result.fold(
                onSuccess = { ToolResult("技能已卸载: $skillId", true) },
                onFailure = { ToolResult("卸载失败: ${it.message}", false) }
            )
        } catch (e: Exception) {
            ToolResult("卸载错误: ${e.message}", false)
        }
    }

    private fun skillUpdate(args: Map<String, String>): ToolResult {
        val skillId = args["skill_id"] ?: return ToolResult("缺少 'skill_id' 参数", false)
        return try {
            val result = kotlinx.coroutines.runBlocking { skillManager.updateSkill(skillId) }
            result.fold(
                onSuccess = { skill -> ToolResult("技能已更新: ${skill.name} v${skill.version}", true) },
                onFailure = { ToolResult("更新失败: ${it.message}", false) }
            )
        } catch (e: Exception) {
            ToolResult("更新错误: ${e.message}", false)
        }
    }

    private fun skillConfig(args: Map<String, String>): ToolResult {
        val skillId = args["skill_id"] ?: return ToolResult("缺少 'skill_id' 参数", false)
        val key = args["key"]
        val value = args["value"]
        return try {
            if (key != null && value != null) {
                val config = kotlinx.coroutines.runBlocking { skillManager.getSkillConfig(skillId) }.toMutableMap()
                config[key] = value
                kotlinx.coroutines.runBlocking { skillManager.saveSkillConfig(skillId, config) }
                ToolResult("技能配置已更新: $skillId.$key = $value", true)
            } else {
                val config = kotlinx.coroutines.runBlocking { skillManager.getSkillConfig(skillId) }
                if (config.isEmpty()) {
                    ToolResult("技能 $skillId 无配置项", true)
                } else {
                    val listing = config.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
                    ToolResult("技能 $skillId 配置:\n$listing", true)
                }
            }
        } catch (e: Exception) {
            ToolResult("配置操作失败: ${e.message}", false)
        }
    }
}
