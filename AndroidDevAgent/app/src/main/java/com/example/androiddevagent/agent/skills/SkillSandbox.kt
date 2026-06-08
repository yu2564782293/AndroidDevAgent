package com.example.androiddevagent.agent.skills

import com.example.androiddevagent.data.SkillEntity
import java.io.File

data class SandboxConstraints(
    val networkAllowed: Boolean = false,
    val networkWhitelist: List<String> = emptyList(),
    val fileAccessRoot: String = "",
    val fileAccessMode: String = "none",
    val maxExecutionTimeMs: Long = 30000,
    val maxOutputLength: Int = 5000,
    val maxMemoryMb: Int = 64,
    val allowedCommands: List<String> = emptyList(),
    val deniedCommands: List<String> = listOf("rm -rf /", "format", "del /s", "mkfs")
)

object SkillSandbox {

    fun buildConstraints(skill: SkillEntity, projectPath: String): SandboxConstraints {
        return SandboxConstraints(
            networkAllowed = skill.networkAccess,
            networkWhitelist = emptyList(),
            fileAccessRoot = projectPath,
            fileAccessMode = skill.fileAccess,
            maxExecutionTimeMs = 30000,
            maxOutputLength = 5000,
            maxMemoryMb = 64
        )
    }

    fun validateScriptSafety(script: String, constraints: SandboxConstraints): List<String> {
        val warnings = mutableListOf<String>()
        val lowerScript = script.lowercase()

        for (denied in constraints.deniedCommands) {
            if (lowerScript.contains(denied.lowercase())) {
                warnings.add("脚本包含危险命令: $denied")
            }
        }

        if (!constraints.networkAllowed) {
            val networkPatterns = listOf("http://", "https://", "ftp://", "socket", "urlconnection", "okhttp", "retrofit")
            for (pattern in networkPatterns) {
                if (lowerScript.contains(pattern)) {
                    warnings.add("脚本尝试网络访问但未声明网络权限: 发现 $pattern")
                }
            }
        }

        if (constraints.fileAccessMode == "none") {
            val filePatterns = listOf("file(", "fileinputstream", "fileoutputstream", "bufferedreader(file", "writetext", "readtext", "readlines", ".mkdirs()")
            for (pattern in filePatterns) {
                if (lowerScript.contains(pattern)) {
                    warnings.add("脚本尝试文件访问但未声明文件权限: 发现 $pattern")
                }
            }
        }

        val reflectionPatterns = listOf("class.forname", "getdeclaredmethod", "invokemethod", "runtime.exec", "processbuilder")
        for (pattern in reflectionPatterns) {
            if (lowerScript.contains(pattern)) {
                warnings.add("脚本使用反射/进程调用: 发现 $pattern")
            }
        }

        return warnings
    }

    fun sanitizeOutput(output: String, maxLength: Int): String {
        val sanitized = output.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
        return if (sanitized.length > maxLength) {
            sanitized.substring(0, maxLength) + "\n... (输出已截断，共 ${sanitized.length} 字符)"
        } else {
            sanitized
        }
    }

    fun isPathAllowed(path: String, root: String, mode: String): Boolean {
        if (mode == "none") return false
        val canonicalPath = try { File(path).canonicalPath } catch (_: Exception) { path }
        val canonicalRoot = try { File(root).canonicalPath } catch (_: Exception) { root }
        val skillDataRoot = "/sdcard/DerekAI/skills"
        return when (mode) {
            "read_only" -> canonicalPath.startsWith(canonicalRoot) || canonicalPath.startsWith(skillDataRoot)
            "read_write" -> canonicalPath.startsWith(canonicalRoot) || canonicalPath.startsWith(skillDataRoot)
            else -> false
        }
    }
}
