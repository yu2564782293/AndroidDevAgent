package com.example.androiddevagent.agent.skills

import android.content.Context
import com.example.androiddevagent.data.SkillDao
import com.example.androiddevagent.data.SkillEntity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class PublishResult(
    val success: Boolean,
    val message: String,
    val publishUrl: String = ""
)

class SkillPublisher(
    private val context: Context,
    private val skillDao: SkillDao
) {

    suspend fun publishToMarketplace(skillId: String): PublishResult {
        return withContext(Dispatchers.IO) {
            try {
                val skill = skillDao.getById(skillId)
                    ?: return@withContext PublishResult(false, "技能未找到: $skillId")

                val manifest = SkillManifestParser.parse(skill.manifestJson)
                    .getOrElse { return@withContext PublishResult(false, "清单解析失败: ${it.message}") }

                val payload = Gson().toJson(mapOf(
                    "id" to manifest.id,
                    "name" to manifest.name,
                    "version" to manifest.version,
                    "description" to manifest.description,
                    "author" to manifest.author,
                    "category" to manifest.category,
                    "icon" to manifest.icon,
                    "tags" to manifest.tags,
                    "tools" to manifest.tools.map { mapOf(
                        "name" to it.name,
                        "description" to it.description
                    )},
                    "runtime_type" to skill.runtimeType,
                    "risk_level" to skill.riskLevel,
                    "source_url" to skill.sourceUrl
                ))

                val url = URL("https://skills.derek-ai.com/api/v1/publish")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                connection.outputStream.write(payload.toByteArray())
                connection.outputStream.flush()

                val responseCode = connection.responseCode
                if (responseCode == 200 || responseCode == 201) {
                    val response = connection.inputStream.bufferedReader().readText()
                    PublishResult(true, "发布成功！", "https://skills.derek-ai.com/skills/${manifest.id}")
                } else {
                    val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    PublishResult(false, "发布失败 ($responseCode): $error")
                }
            } catch (e: Exception) {
                PublishResult(false, "发布失败: ${e.message}")
            }
        }
    }

    suspend fun exportSkillPackage(skillId: String, outputDir: String): PublishResult {
        return withContext(Dispatchers.IO) {
            try {
                val skill = skillDao.getById(skillId)
                    ?: return@withContext PublishResult(false, "技能未找到: $skillId")

                val exportDir = File(outputDir, skillId.replace("/", "_"))
                exportDir.mkdirs()

                File(exportDir, "skill.json").writeText(skill.manifestJson)

                val skillDir = File("/sdcard/DerekAI/skills", skillId.replace("/", "_"))
                if (skillDir.exists()) {
                    skillDir.copyRecursively(File(exportDir, "scripts"), overwrite = true)
                }

                val readme = buildString {
                    appendLine("# ${skill.name}")
                    appendLine()
                    appendLine(skill.description)
                    appendLine()
                    appendLine("**版本**: ${skill.version}")
                    appendLine("**作者**: ${skill.author}")
                    appendLine("**分类**: ${skill.category}")
                    appendLine()
                    appendLine("## 提供的工具")
                    for (tool in skill.toolNames) {
                        appendLine("- `$tool`")
                    }
                    appendLine()
                    appendLine("## 安装")
                    appendLine("在 DEREK AI 中使用 `skill_install(source=\"local\", repo=\"${exportDir.absolutePath}\")` 安装此技能。")
                }
                File(exportDir, "README.md").writeText(readme)

                PublishResult(true, "技能包已导出到: ${exportDir.absolutePath}", exportDir.absolutePath)
            } catch (e: Exception) {
                PublishResult(false, "导出失败: ${e.message}")
            }
        }
    }
}
