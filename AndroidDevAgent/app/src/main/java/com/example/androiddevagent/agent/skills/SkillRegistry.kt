package com.example.androiddevagent.agent.skills

import com.example.androiddevagent.data.SkillDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class SkillRegistry(
    private val skillDao: SkillDao
) {

    // ─── 全 GitHub 平台搜索 ───────────────────────────────────────

    /**
     * 直接在 GitHub 全平台搜索：不限制 topic、不限制标签。
     * 使用多路并行搜索策略，合并去重后按 stars 排序。
     *
     * 策略：
     *  1. 按名称+描述搜索 (权重高)
     *  2. 按 README 内容搜索 (覆盖面广)
     *  3. 搜索含有 skill.json / derek-skill.json 的仓库 (精准匹配)
     */
    suspend fun searchGitHub(query: String): List<SkillSearchResult> {
        val installedIds = withContext(Dispatchers.IO) { skillDao.getAll().map { it.id }.toSet() }

        return coroutineScope {
            val deferredName = async { searchGitHubBy("$query in:name,description", installedIds) }
            val deferredReadme = async { searchGitHubBy("$query in:readme", installedIds) }
            val deferredManifest = async { searchGitHubSkillFiles(query, installedIds) }

            val all = (deferredName.await() + deferredReadme.await() + deferredManifest.await())
                .distinctBy { it.id }
                .sortedByDescending { it.stars }
                .take(30)

            all
        }
    }

    /**
     * 底层 GitHub Repository Search API 调用。
     * 支持 GitHub 标准搜索语法: "keyword in:field" 等。
     */
    private suspend fun searchGitHubBy(searchQuery: String, installedIds: Set<String>): List<SkillSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(searchQuery, "UTF-8")
                val url = "https://api.github.com/search/repositories?q=$encoded&sort=stars&order=desc&per_page=15"
                val json = downloadText(url) ?: return@withContext emptyList()

                parseGitHubSearchResponse(json, installedIds)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 搜索包含技能描述文件 (skill.json / derek-skill.json) 的仓库。
     * 使用 GitHub Code Search API 查找文件内容。
     */
    private suspend fun searchGitHubSkillFiles(query: String, installedIds: Set<String>): List<SkillSearchResult> {
        return withContext(Dispatchers.IO) {
            // 并行搜索两种技能文件格式
            val repoNames = mutableSetOf<String>()

            listOf("derek-skill.json", "skill.json").forEach { filename ->
                try {
                    val encodedQuery = java.net.URLEncoder.encode("$query filename:$filename", "UTF-8")
                    val url = "https://api.github.com/search/code?q=$encodedQuery&per_page=10"
                    // Code search 可能需要认证，无 token 限速更严(10/min)，失败不阻塞
                    val json = downloadText(url) ?: return@forEach
                    val gson = Gson()
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val response: Map<String, Any> = gson.fromJson(json, type)
                    val items = response["items"] as? List<Map<String, Any>> ?: return@forEach
                    items.mapNotNull { item ->
                        val repo = item["repository"] as? Map<String, Any> ?: return@mapNotNull null
                        repo["full_name"] as? String
                    }.forEach { repoNames.add(it) }
                } catch (_: Exception) {
                    // Code search 降级，不影响主流程
                }
            }

            if (repoNames.isEmpty()) return@withContext emptyList()

            // 按仓库名批量获取仓库信息
            repoNames.mapNotNull { fullName ->
                try {
                    val encoded = java.net.URLEncoder.encode(fullName, "UTF-8")
                    val url = "https://api.github.com/search/repositories?q=repo:$encoded&per_page=1"
                    val json = downloadText(url) ?: return@mapNotNull null
                    val results = parseGitHubSearchResponse(json, installedIds)
                    results.firstOrNull()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    private fun parseGitHubSearchResponse(json: String, installedIds: Set<String>): List<SkillSearchResult> {
        val gson = Gson()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val response: Map<String, Any> = gson.fromJson(json, type)
        val items = response["items"] as? List<Map<String, Any>> ?: return emptyList()

        return items.mapNotNull { item ->
            val fullName = item["full_name"] as? String ?: return@mapNotNull null
            val name = item["name"] as? String ?: fullName
            val desc = item["description"] as? String ?: ""
            val stars = (item["stargazers_count"] as? Number)?.toInt() ?: 0
            val htmlUrl = item["html_url"] as? String ?: ""
            val language = item["language"] as? String ?: ""
            val topics = (item["topics"] as? List<String>) ?: emptyList()
            val updatedAt = item["updated_at"] as? String ?: ""

            SkillSearchResult(
                id = fullName,
                name = name,
                description = desc,
                author = fullName.substringBefore("/"),
                stars = stars,
                category = inferCategory(name, desc, topics, language),
                tags = topics.ifEmpty { listOf(language.lowercase()) },
                sourceUrl = htmlUrl,
                installed = fullName in installedIds
            )
        }
    }

    // ─── 多平台搜索 ───────────────────────────────────────────────

    /**
     * GitCode 搜索 (gitcode.com, 国内代码托管平台)
     */
    suspend fun searchGitCode(query: String): List<SkillSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://gitcode.com/api/v4/projects?search=$encoded&order_by=stars&sort=desc&per_page=20"
                val json = downloadText(url) ?: return@withContext emptyList()

                val gson = Gson()
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val items: List<Map<String, Any>> = gson.fromJson(json, type)
                val installedIds = skillDao.getAll().map { it.id }.toSet()

                items.mapNotNull { item ->
                    val path = item["path_with_namespace"] as? String ?: return@mapNotNull null
                    val name = item["name"] as? String ?: path
                    val desc = item["description"] as? String ?: ""
                    val stars = (item["star_count"] as? Number)?.toInt() ?: 0
                    val webUrl = item["web_url"] as? String ?: ""
                    val namespace = item["namespace"] as? Map<String, Any>
                    val author = namespace?.get("name") as? String ?: path.substringBefore("/")

                    SkillSearchResult(
                        id = "gitcode:$path",
                        name = name,
                        description = desc,
                        author = author,
                        stars = stars,
                        category = "community",
                        tags = emptyList(),
                        sourceUrl = webUrl,
                        installed = "gitcode:$path" in installedIds
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * GitLab 搜索 (gitlab.com)
     */
    suspend fun searchGitLab(query: String): List<SkillSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://gitlab.com/api/v4/projects?search=$encoded&order_by=stars&sort=desc&per_page=15"
                val json = downloadText(url) ?: return@withContext emptyList()

                val gson = Gson()
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val items: List<Map<String, Any>> = gson.fromJson(json, type)
                val installedIds = skillDao.getAll().map { it.id }.toSet()

                items.mapNotNull { item ->
                    val path = item["path_with_namespace"] as? String ?: return@mapNotNull null
                    val name = item["name"] as? String ?: path
                    val desc = item["description"] as? String ?: ""
                    val stars = (item["star_count"] as? Number)?.toInt() ?: 0
                    val webUrl = item["web_url"] as? String ?: ""
                    val namespace = item["namespace"] as? Map<String, Any>
                    val author = namespace?.get("name") as? String ?: path.substringBefore("/")

                    SkillSearchResult(
                        id = "gitlab:$path",
                        name = name,
                        description = desc,
                        author = author,
                        stars = stars,
                        category = "community",
                        tags = emptyList(),
                        sourceUrl = webUrl,
                        installed = "gitlab:$path" in installedIds
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // ─── 统一搜索入口 ─────────────────────────────────────────────

    /**
     * 全平台并行搜索，合并去重。
     */
    suspend fun searchAllPlatforms(query: String): List<SkillSearchResult> {
        return coroutineScope {
            val github = async { searchGitHub(query) }
            val gitcode = async { searchGitCode(query) }
            val gitlab = async { searchGitLab(query) }

            (github.await() + gitcode.await() + gitlab.await())
                .distinctBy { it.sourceUrl }
                .sortedByDescending { it.stars }
                .take(30)
        }
    }

    // ─── 市场搜索 (保留原逻辑) ─────────────────────────────────────

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

    // ─── 任务驱动搜索 ─────────────────────────────────────────────

    /**
     * 根据用户任务描述，智能提取关键词并多策略搜索。
     * 返回最佳匹配的技能仓库。
     */
    suspend fun searchForTask(taskDescription: String): List<SkillSearchResult> {
        val keywords = extractKeywordsV2(taskDescription)
        val allResults = mutableListOf<SkillSearchResult>()

        // 策略 1: 用提取的关键词直接搜索
        for (keyword in keywords.take(3)) {
            allResults.addAll(searchGitHub(keyword))
        }

        // 策略 2: 组合关键词 + 技能相关词搜索
        if (keywords.size >= 2) {
            val combined = keywords.take(2).joinToString(" ")
            allResults.addAll(searchGitHub(combined))
        }

        // 策略 3: 用原始任务描述的部分内容搜索（取前 15 个有意义字符）
        val shortDesc = taskDescription.take(80)
            .replace(Regex("[，。！？；：、\\n\\r]"), " ")
            .trim()
        if (shortDesc.length > 5) {
            allResults.addAll(searchGitHub(shortDesc))
        }

        return allResults.distinctBy { it.id }.sortedByDescending { it.stars }.take(15)
    }

    // ─── 推荐 (动态获取) ──────────────────────────────────────────

    /**
     * 动态从 GitHub 获取热门技能仓库作为推荐，而非硬编码。
     *
     * 搜索策略：
     *  1. 按"技能/工具/自动化"类关键词搜索高星仓库
     *  2. 搜索 Android 开发相关热门工具
     *  3. 搜索通用开发者工具
     *
     * 如果没有网络结果，降级返回基础推荐。
     */
    suspend fun getRecommended(): List<SkillSearchResult> {
        val installedIds = withContext(Dispatchers.IO) { skillDao.getAll().map { it.id }.toSet() }

        return coroutineScope {
            val queries = listOf(
                "developer tool automation stars:>50",
                "android dev tool stars:>30",
                "code assistant skill agent stars:>20"
            )

            val deferreds = queries.map { q ->
                async { searchGitHubBy(q, installedIds) }
            }

            val all = deferreds.flatMap { it.await() }
                .distinctBy { it.id }
                .sortedByDescending { it.stars }
                .take(12)

            all.ifEmpty {
                // 降级：返回基础硬编码推荐
                getFallbackRecommended(installedIds)
            }
        }
    }

    private fun getFallbackRecommended(installedIds: Set<String>): List<SkillSearchResult> {
        return listOf(
            SkillSearchResult(
                id = "android/nowinandroid", name = "Now in Android",
                description = "Google 官方 Android 开发最佳实践示例应用",
                author = "android", stars = 17000, category = "android",
                tags = listOf("android", "compose", "sample"),
                sourceUrl = "https://github.com/android/nowinandroid",
                installed = "android/nowinandroid" in installedIds
            ),
            SkillSearchResult(
                id = "google/tsunami-security-scanner", name = "Tsunami 安全扫描",
                description = "通用网络安全扫描器，可扩展插件系统",
                author = "google", stars = 8300, category = "security",
                tags = listOf("security", "scanner", "network"),
                sourceUrl = "https://github.com/google/tsunami-security-scanner",
                installed = "google/tsunami-security-scanner" in installedIds
            ),
            SkillSearchResult(
                id = "curl/curl", name = "curl",
                description = "命令行工具和库，支持多种协议传输数据",
                author = "curl", stars = 37000, category = "network",
                tags = listOf("http", "network", "cli"),
                sourceUrl = "https://github.com/curl/curl",
                installed = "curl/curl" in installedIds
            ),
            SkillSearchResult(
                id = "ossrs/srs", name = "SRS 流媒体服务器",
                description = "简单高效的实时视频服务器，支持 RTMP/WebRTC/HLS",
                author = "ossrs", stars = 26000, category = "media",
                tags = listOf("streaming", "webrtc", "live"),
                sourceUrl = "https://github.com/ossrs/srs",
                installed = "ossrs/srs" in installedIds
            )
        )
    }

    // ─── 关键词提取 (v2 增强版) ────────────────────────────────────

    private fun extractKeywordsV2(task: String): List<String> {
        val lowerTask = task.lowercase()

        // 按优先级排序的关键词映射
        val keywordMap = mapOf(
            "android" to listOf("android", "安卓", "kotlin", "java", "jetpack", "compose", "apk"),
            "web" to listOf("web", "html", "css", "scrape", "crawl", "爬虫", "网页", "抓取", "browser", "frontend", "前端"),
            "api" to listOf("api", "rest", "http", "request", "接口", "graphql", "endpoint"),
            "database" to listOf("database", "sql", "sqlite", "数据库", "查询", "mysql", "postgres", "mongodb", "redis"),
            "security" to listOf("security", "安全", "漏洞", "vulnerability", "scan", "扫描", "encrypt", "加密", "auth", "渗透"),
            "code" to listOf("code", "review", "审查", "quality", "质量", "refactor", "重构", "linter", "format"),
            "test" to listOf("test", "测试", "unit", "单元", "integration", "e2e", "mock"),
            "deploy" to listOf("deploy", "发布", "部署", "release", "publish", "ci", "cd", "devops", "docker", "kubernetes"),
            "optimize" to listOf("optimize", "优化", "性能", "performance", "memory", "内存", "speed", "加速"),
            "translate" to listOf("translate", "翻译", "国际化", "i18n", "localization"),
            "document" to listOf("document", "文档", "readme", "doc", "generate", "生成"),
            "ai" to listOf("ai", "llm", "gpt", "人工智能", "模型", "machine learning", "深度学习", "nlp", "chatbot"),
            "network" to listOf("network", "网络", "tcp", "udp", "socket", "proxy", "代理"),
            "image" to listOf("image", "图片", "photo", "照片", "compress", "压缩", "resize", "裁剪"),
            "video" to listOf("video", "视频", "media", "媒体", "stream", "直播", "transcode", "转码"),
            "audio" to listOf("audio", "音频", "sound", "语音", "speech", "tts", "stt"),
            "file" to listOf("file", "文件", "io", "读写", "storage", "存储", "upload", "下载"),
            "monitor" to listOf("monitor", "监控", "log", "日志", "alert", "告警", "metric", "trace"),
            "backup" to listOf("backup", "备份", "restore", "恢复", "sync", "同步"),
            "build" to listOf("build", "构建", "compile", "编译", "gradle", "maven", "make"),
            "git" to listOf("git", "version", "版本", "commit", "branch", "merge", "diff"),
            "mobile" to listOf("mobile", "移动", "ios", "swift", "flutter", "react native", "小程序"),
            "cli" to listOf("cli", "terminal", "终端", "command", "shell", "bash", "script", "脚本"),
            "data" to listOf("data", "数据", "分析", "analytics", "visualization", "可视化", "etl", "pipeline")
        )

        val matched = keywordMap.entries
            .filter { (_, kws) -> kws.any { lowerTask.contains(it) } }
            .sortedByDescending { (_, kws) ->
                // 关键词匹配越多、越精确，分数越高
                kws.count { lowerTask.contains(it) }
            }
            .map { it.key }

        return matched.ifEmpty {
            // 没有匹配到关键词时，提取任务中较长的词作为搜索关键词
            task.split(Regex("[\\s，。！？；：、]+"))
                .filter { it.length >= 2 }
                .take(5)
                .ifEmpty { listOf("developer tool") }
        }
    }

    // 保留旧版兼容 (ToolExecutor 中 skill_search 工具可能使用)
    @Suppress("unused")
    private fun extractKeywords(task: String): List<String> = extractKeywordsV2(task)

    // ─── 工具方法 ──────────────────────────────────────────────────

    private fun inferCategory(name: String, desc: String, topics: List<String>, language: String): String {
        val text = "$name $desc ${topics.joinToString(" ")}".lowercase()
        return when {
            text.contains("android") || text.contains("kotlin") -> "android"
            text.contains("security") || text.contains("vulnerability") || text.contains("auth") -> "security"
            text.contains("api") || text.contains("rest") || text.contains("http") -> "api"
            text.contains("database") || text.contains("sql") || text.contains("sqlite") -> "database"
            text.contains("web") || text.contains("html") || text.contains("frontend") -> "web"
            text.contains("ai") || text.contains("llm") || text.contains("machine learning") -> "ai"
            text.contains("test") || text.contains("testing") -> "test"
            text.contains("deploy") || text.contains("devops") || text.contains("ci") -> "deploy"
            text.contains("monitor") || text.contains("log") -> "monitor"
            text.contains("cli") || text.contains("terminal") || text.contains("shell") -> "cli"
            text.contains("media") || text.contains("video") || text.contains("image") -> "media"
            text.contains("data") || text.contains("analytics") -> "data"
            else -> "dev"
        }
    }

    private fun downloadText(url: String): String? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "DEREK-AI-SkillRegistry")
            connection.getInputStream().bufferedReader().readText()
        } catch (e: Exception) {
            null
        }
    }
}