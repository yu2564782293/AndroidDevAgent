package com.example.androiddevagent.agent.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.androiddevagent.agent.events.AgentEvent
import com.example.androiddevagent.agent.vcs.GitIntegration
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareManager @Inject constructor(
    private val gitIntegration: GitIntegration
) {

    fun exportTaskReport(events: List<AgentEvent>, taskDescription: String): File {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "task_report_${dateFormat.format(Date())}.md"
        val file = File.createTempFile("report", ".md")

        val sb = StringBuilder()
        sb.append("# 任务报告\n\n")
        sb.append("**任务**: $taskDescription\n")
        sb.append("**时间**: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")

        sb.append("## 执行过程\n\n")
        events.forEachIndexed { index, event ->
            when (event) {
                is AgentEvent.UserMessage -> sb.append("### 👤 用户\n${event.content}\n\n")
                is AgentEvent.AssistantThought -> sb.append("### 🤖 思考\n${event.content}\n\n")
                is AgentEvent.ToolCallEvent -> sb.append("### 🔧 工具调用: ${event.name}\n```\n${event.args.entries.joinToString("\n") { "${it.key}: ${it.value}" }}\n```\n\n")
                is AgentEvent.ToolResultEvent -> sb.append("### 📋 结果 (${if (event.success) "✅" else "❌"})\n```\n${event.output.take(500)}\n```\n\n")
                is AgentEvent.BuildResultEvent -> sb.append("### 🔨 构建 ${if (event.success) "✅ 成功" else "❌ 失败"}\n```\n${event.output.take(300)}\n```\n\n")
                is AgentEvent.TaskCompleteEvent -> sb.append("### ✅ 任务完成\n${event.summary}\n\n修改文件: ${event.filesChanged.joinToString(", ")}\n\n")
                is AgentEvent.ErrorEvent -> sb.append("### ❌ 错误\n${event.message}\n\n")
                is AgentEvent.StuckDetectedEvent -> sb.append("### ⚠️ 陷入循环\n${event.reason}\n\n")
                is AgentEvent.AutoFixEvent -> sb.append("### 🔄 自动修复 (第 ${event.attempt}/${event.maxAttempts} 次)\n${event.errorSummary}\n\n")
                else -> {}
            }
        }

        file.writeText(sb.toString())
        return file
    }

    fun exportDiff(): File? {
        val diffResult = gitIntegration.getDiff()
        if (!diffResult.success || diffResult.output.isBlank()) return null

        val file = File.createTempFile("diff", ".diff")
        file.writeText(diffResult.output)
        return file
    }

    fun exportProjectZip(projectPath: String): File? {
        val projectDir = File(projectPath)
        if (!projectDir.exists()) return null

        val zipFile = File.createTempFile("project", ".zip")
        ZipOutputStream(zipFile.outputStream()).use { zipOut ->
            projectDir.walk().forEach { file ->
                if (file.isDirectory) return@forEach
                if (file.absolutePath.contains("/build/")) return@forEach
                if (file.absolutePath.contains("/.gradle/")) return@forEach
                if (file.absolutePath.contains("/.idea/")) return@forEach
                if (file.name.startsWith(".") && file.name != ".gitignore") return@forEach

                val entry = ZipEntry(file.relativeTo(projectDir).path)
                zipOut.putNextEntry(entry)
                try {
                    zipOut.write(file.readBytes())
                } catch (_: Exception) {
                }
                zipOut.closeEntry()
            }
        }
        return zipFile
    }

    fun shareFile(context: Context, file: File, mimeType: String = "text/plain") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享"))
    }
}
