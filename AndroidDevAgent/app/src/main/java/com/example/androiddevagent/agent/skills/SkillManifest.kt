package com.example.androiddevagent.agent.skills

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

data class SkillManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val author: String = "",
    val category: String = "general",
    val icon: String = "🔧",
    val tags: List<String> = emptyList(),
    val source: SkillSource? = null,
    val permissions: List<String> = emptyList(),
    val tools: List<SkillToolDef> = emptyList(),
    val knowledge: String = "",
    val runtime: SkillRuntimeDef? = null,
    val config: SkillConfig? = null,
    val security: SkillSecurity? = null
)

data class SkillSource(
    val type: String = "github",
    val repo: String = "",
    val branch: String = "main"
)

data class SkillToolDef(
    val name: String,
    val description: String,
    val parameters: SkillParameters
)

data class SkillParameters(
    val type: String = "object",
    val properties: Map<String, SkillPropertyDef> = emptyMap(),
    val required: List<String> = emptyList()
)

data class SkillPropertyDef(
    val type: String,
    val description: String = ""
)

data class SkillRuntimeDef(
    val type: String = "script",
    val entry: String = "main.kts",
    val language: String = "kotlin-script",
    val dependencies: List<String> = emptyList()
)

data class SkillConfig(
    val properties: Map<String, SkillConfigProperty> = emptyMap()
)

data class SkillConfigProperty(
    val type: String = "string",
    val default: String = "",
    val description: String = ""
)

data class SkillSecurity(
    val riskLevel: String = "low",
    val autoApprove: Boolean = true,
    val networkAccess: Boolean = false,
    val fileAccess: String = "none",
    @SerializedName("max_execution_time_ms")
    val maxExecutionTimeMs: Long = 30000
)

data class SkillSearchResult(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val stars: Int = 0,
    val downloads: Int = 0,
    val category: String,
    val tags: List<String>,
    val sourceUrl: String,
    val installed: Boolean = false,
    val icon: String = "🔧"
)

object SkillManifestParser {
    private val gson = Gson()

    fun parse(json: String): Result<SkillManifest> {
        return try {
            val manifest = gson.fromJson(json, SkillManifest::class.java)
            if (manifest.id.isBlank()) {
                Result.failure(IllegalArgumentException("技能 ID 不能为空"))
            } else {
                Result.success(manifest)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun toJson(manifest: SkillManifest): String {
        return gson.toJson(manifest)
    }

    fun parseConfigDefaults(config: SkillConfig?): Map<String, Any> {
        if (config == null) return emptyMap()
        return config.properties.mapValues { (_, prop) ->
            when (prop.type) {
                "integer" -> prop.default.toIntOrNull() ?: 0
                "boolean" -> prop.default.toBooleanStrictOrNull() ?: false
                else -> prop.default
            }
        }
    }
}
