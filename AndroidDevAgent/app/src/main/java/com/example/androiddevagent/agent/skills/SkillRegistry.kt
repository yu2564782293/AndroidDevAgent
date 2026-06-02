package com.example.androiddevagent.agent.skills

import com.example.androiddevagent.data.SkillDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

class SkillRegistry(
    private val skillDao: SkillDao
) {

    suspend fun searchGitHub(query: String): List<SkillSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode("$query topic:derek-skill", "UTF-8")
                val url = "https://api.github.com/search/repositories?q=$encoded&sort=stars&per_page=20"
                val json = downloadText(url) ?: return@withContext emptyList()

                val gson = Gson()
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val response: Map<String, Any> = gson.fromJson(json, type)

                val items = response["items"] as? List<Map<String, Any>> ?: return@withContext emptyList()
                val installedIds = skillDao.getAll().map { it.id }.toSet()

                items.mapNotNull { item ->
                    val fullName = item["full_name"] as? String ?: return@mapNotNull null
                    val name = item["name"] as? String ?: fullName
                    val desc = item["description"] as? String ?: ""
                    val stars = (item["stargazers_count"] as? Number)?.toInt() ?: 0
                    val htmlUrl = item["html_url"] as? String ?: ""

                    SkillSearchResult(
                        id = fullName,
                        name = name,
                        description = desc,
                        author = fullName.substringBefore("/"),
                        stars = stars,
                        category = "community",
                        tags = emptyList(),
                        sourceUrl = htmlUrl,
                        installed = fullName in installedIds
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun searchMarketplace(query: String): List<SkillSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://skills.derek-ai.com/api/v1/search?q=$encoded"
                val json = downloadText(url) ?: return@withContext emptyList()

                val gson = Gson()
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val items: List<Map<String, Any>> = gson.fromJson(json, type)

                val installedIds = skillDao.getAll().map { it.id }.toSet()

                items.mapNotNull { item ->
                    SkillSearchResult(
                        id = item["id"] as? String ?: return@mapNotNull null,
                        name = item["name"] as? String ?: "",
                        description = item["description"] as? String ?: "",
                        author = item["author"] as? String ?: "",
                        stars = (item["stars"] as? Number)?.toInt() ?: 0,
                        downloads = (item["downloads"] as? Number)?.toInt() ?: 0,
                        category = item["category"] as? String ?: "general",
                        tags = (item["tags"] as? List<String>) ?: emptyList(),
                        sourceUrl = item["source_url"] as? String ?: "",
                        installed = (item["id"] as? String)?.let { it in installedIds } ?: false
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun searchForTask(taskDescription: String): List<SkillSearchResult> {
        val keywords = extractKeywords(taskDescription)
        val allResults = mutableListOf<SkillSearchResult>()

        for (keyword in keywords.take(3)) {
            allResults.addAll(searchGitHub(keyword))
        }

        return allResults.distinctBy { it.id }.sortedByDescending { it.stars }.take(10)
    }

    suspend fun getRecommended(): List<SkillSearchResult> {
        val officialSkills = listOf(
            SkillSearchResult(
                id = "derek-skills/web-scraper",
                name = "网页抓取",
                description = "抓取网页内容，提取结构化数据，支持 CSS 选择器",
                author = "derek-skills",
                stars = 128,
                category = "data",
                tags = listOf("web", "scrape", "html"),
                sourceUrl = "https://github.com/derek-skills/web-scraper",
                installed = false,
                icon = "🌐"
            ),
            SkillSearchResult(
                id = "derek-skills/api-tester",
                name = "API 测试",
                description = "REST API 测试和调试工具，支持 GET/POST/PUT/DELETE",
                author = "derek-skills",
                stars = 96,
                category = "dev",
                tags = listOf("api", "rest", "http"),
                sourceUrl = "https://github.com/derek-skills/api-tester",
                installed = false,
                icon = "🔗"
            ),
            SkillSearchResult(
                id = "derek-skills/code-reviewer",
                name = "代码审查",
                description = "AI 驱动的代码审查与改进建议",
                author = "derek-skills",
                stars = 85,
                category = "ai",
                tags = listOf("code", "review", "quality"),
                sourceUrl = "https://github.com/derek-skills/code-reviewer",
                installed = false,
                icon = "🤖"
            ),
            SkillSearchResult(
                id = "derek-skills/security-scanner",
                name = "安全扫描",
                description = "扫描代码中的安全漏洞和敏感信息泄露",
                author = "derek-skills",
                stars = 72,
                category = "security",
                tags = listOf("security", "vulnerability", "scan"),
                sourceUrl = "https://github.com/derek-skills/security-scanner",
                installed = false,
                icon = "🔒"
            ),
            SkillSearchResult(
                id = "derek-skills/apk-optimizer",
                name = "APK 优化",
                description = "APK 体积优化、混淆配置和性能分析",
                author = "derek-skills",
                stars = 64,
                category = "android",
                tags = listOf("apk", "optimize", "proguard"),
                sourceUrl = "https://github.com/derek-skills/apk-optimizer",
                installed = false,
                icon = "📱"
            ),
            SkillSearchResult(
                id = "derek-skills/db-explorer",
                name = "数据库探索",
                description = "浏览和查询 SQLite 数据库，可视化数据表",
                author = "derek-skills",
                stars = 58,
                category = "data",
                tags = listOf("database", "sqlite", "query"),
                sourceUrl = "https://github.com/derek-skills/db-explorer",
                installed = false,
                icon = "📊"
            )
        )

        val installedIds = skillDao.getAll().map { it.id }.toSet()
        return officialSkills.map { it.copy(installed = it.id in installedIds) }
    }

    private fun extractKeywords(task: String): List<String> {
        val keywordMap = mapOf(
            "web" to listOf("web", "html", "scrape", "crawl", "网页", "抓取", "爬虫"),
            "api" to listOf("api", "rest", "http", "request", "接口"),
            "database" to listOf("database", "sql", "sqlite", "数据库", "查询"),
            "security" to listOf("security", "vulnerability", "scan", "安全", "漏洞"),
            "code" to listOf("code", "review", "quality", "代码", "审查", "质量"),
            "test" to listOf("test", "testing", "unit", "测试"),
            "deploy" to listOf("deploy", "release", "publish", "发布", "部署"),
            "optimize" to listOf("optimize", "performance", "优化", "性能"),
            "translate" to listOf("translate", "i18n", "翻译", "国际化"),
            "document" to listOf("document", "doc", "readme", "文档")
        )

        val lowerTask = task.lowercase()
        return keywordMap.entries
            .filter { (_, keywords) -> keywords.any { lowerTask.contains(it) } }
            .map { it.key }
            .ifEmpty { listOf("general") }
    }

    private fun downloadText(url: String): String? {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.getInputStream().bufferedReader().readText()
        } catch (e: Exception) {
            null
        }
    }
}
