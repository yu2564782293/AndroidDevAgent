package com.example.androiddevagent.agent.tools

import com.example.androiddevagent.agent.llm.ChatCompletionRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import javax.inject.Inject

data class ToolResult(
    val output: String,
    val success: Boolean
)

class ToolExecutor @Inject constructor() {

    private var projectPath: String = ""
    private val gson = Gson()

    fun setProjectPath(path: String) {
        projectPath = path
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
            else -> ToolResult("Unknown tool: ${call.function.name}", false)
        }
    }

    private fun readFile(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult("Missing 'path' parameter", false)
        val file = File(projectPath, path)

        if (!file.exists()) {
            return ToolResult("File not found: $path", false)
        }
        if (!file.isFile) {
            return ToolResult("Not a file: $path", false)
        }

        val lines = file.readLines()
        val startLine = (args["start_line"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val endLine = (args["end_line"]?.toIntOrNull() ?: (startLine + 99)).coerceAtMost(lines.size)

        val content = lines.subList(startLine - 1, endLine).mapIndexed { idx, line ->
            "${startLine + idx}→$line"
        }.joinToString("\n")

        return ToolResult(
            "File: $path (lines $startLine-$endLine of ${lines.size})\n$content",
            true
        )
    }

    private fun writeFile(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult("Missing 'path' parameter", false)
        val content = args["content"] ?: return ToolResult("Missing 'content' parameter", false)
        val file = File(projectPath, path)

        file.parentFile?.mkdirs()
        file.writeText(content)

        val lineCount = content.lines().size
        return ToolResult("Written successfully: $path ($lineCount lines)", true)
    }

    private fun editFile(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult("Missing 'path' parameter", false)
        val oldText = args["old_text"] ?: return ToolResult("Missing 'old_text' parameter", false)
        val newText = args["new_text"] ?: return ToolResult("Missing 'new_text' parameter", false)
        val file = File(projectPath, path)

        if (!file.exists()) {
            return ToolResult("File not found: $path", false)
        }

        val content = file.readText()
        if (!content.contains(oldText)) {
            val preview = content.take(500)
            return ToolResult(
                "old_text not found in $path. Make sure the text matches exactly.\nFile preview:\n$preview",
                false
            )
        }

        if (content.indexOf(oldText) != content.lastIndexOf(oldText)) {
            return ToolResult(
                "old_text appears multiple times in $path. Please provide more context to make it unique.",
                false
            )
        }

        val newContent = content.replace(oldText, newText)
        file.writeText(newContent)

        val lintResult = quickLint(path, newContent)
        if (!lintResult.passed) {
            file.writeText(content)
            return ToolResult(
                "Edit caused syntax issues, auto-reverted:\n${lintResult.errors.joinToString("\n")}\nPlease fix and retry.",
                false
            )
        }

        val lineCount = newContent.lines().size
        return ToolResult("Edited successfully: $path ($lineCount lines)", true)
    }

    private fun listFiles(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: "."
        val maxDepth = args["max_depth"]?.toIntOrNull() ?: 3
        val dir = File(projectPath, path)

        if (!dir.exists() || !dir.isDirectory) {
            return ToolResult("Directory not found: $path", false)
        }

        val tree = buildTree(dir, maxDepth, 0)
        return ToolResult("Directory: $path\n$tree", true)
    }

    private fun deleteFile(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult("Missing 'path' parameter", false)
        val file = File(projectPath, path)

        if (!file.exists()) {
            return ToolResult("File not found: $path", false)
        }

        val deleted = file.delete()
        return if (deleted) {
            ToolResult("Deleted: $path", true)
        } else {
            ToolResult("Failed to delete: $path", false)
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
                    errors.add("Unbalanced braces: $openBraces open, $closeBraces close")
                }
                val openParens = content.count { it == '(' }
                val closeParens = content.count { it == ')' }
                if (openParens != closeParens) {
                    errors.add("Unbalanced parentheses: $openParens open, $closeParens close")
                }
            }
            "xml" -> {
                val openTags = Regex("<([a-zA-Z][a-zA-Z0-9]*)[^/]*>").findAll(content).count()
                val closeTags = Regex("</[a-zA-Z][a-zA-Z0-9]*>").findAll(content).count()
                val selfClosing = Regex("<[a-zA-Z][a-zA-Z0-9]*[^>]*/>").findAll(content).count()
                if (openTags - selfClosing != closeTags) {
                    errors.add("Unbalanced XML tags: $openTags open, $closeTags close, $selfClosing self-closing")
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
