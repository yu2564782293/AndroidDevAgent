package com.example.androiddevagent.agent.vcs

import android.util.Base64
import com.example.androiddevagent.data.SecureStorage
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class GitHubRepoInfo(
    val owner: String,
    val repo: String,
    val branch: String = "main"
)

data class GitHubFileContent(
    val path: String,
    val content: String,
    val sha: String,
    val size: Int
)

data class GitHubDirEntry(
    val name: String,
    val path: String,
    val type: String,
    val size: Int
)

data class GitHubCommitResult(
    val sha: String,
    val htmlUrl: String
)

data class GitHubBranchInfo(
    val name: String,
    val isDefault: Boolean
)

data class GitHubPRInfo(
    val number: Int,
    val title: String,
    val state: String,
    val htmlUrl: String,
    val headBranch: String,
    val baseBranch: String
)

@Singleton
class GitHubApiService @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var currentRepo: GitHubRepoInfo? = null

    fun setRepo(owner: String, repo: String, branch: String = "main") {
        currentRepo = GitHubRepoInfo(owner, repo, branch)
    }

    fun getRepo(): GitHubRepoInfo? = currentRepo

    fun isConfigured(): Boolean {
        return secureStorage.getGitToken("github").isNotEmpty() && currentRepo != null
    }

    private fun getToken(): String = secureStorage.getGitToken("github")

    private fun apiUrl(path: String): String {
        return "https://api.github.com$path"
    }

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "token ${getToken()}")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        val response = client.newCall(request).execute()
        response.body?.string() ?: ""
    }

    private suspend fun httpPut(url: String, jsonBody: String): Pair<Int, String> {
        return withContext(Dispatchers.IO) {
            try {
                val body = jsonBody.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "token ${getToken()}")
                    .header("Accept", "application/vnd.github.v3+json")
                    .put(body)
                    .build()
                val response = client.newCall(request).execute()
                Pair(response.code, response.body?.string() ?: "")
            } catch (e: Exception) {
                Pair(-1, e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun httpDelete(url: String, jsonBody: String): Pair<Int, String> {
        return withContext(Dispatchers.IO) {
            try {
                val body = jsonBody.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "token ${getToken()}")
                    .header("Accept", "application/vnd.github.v3+json")
                    .delete(body)
                    .build()
                val response = client.newCall(request).execute()
                Pair(response.code, response.body?.string() ?: "")
            } catch (e: Exception) {
                Pair(-1, e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun httpPost(url: String, jsonBody: String): Pair<Int, String> {
        return withContext(Dispatchers.IO) {
            try {
                val body = jsonBody.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "token ${getToken()}")
                    .header("Accept", "application/vnd.github.v3+json")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                Pair(response.code, response.body?.string() ?: "")
            } catch (e: Exception) {
                Pair(-1, e.message ?: "Unknown error")
            }
        }
    }

    suspend fun readFile(path: String, branch: String = ""): Result<GitHubFileContent> = try {
        val repo = currentRepo ?: return Result.failure(Exception("未连接仓库"))
        val b = branch.ifEmpty { repo.branch }
        val url = apiUrl("/repos/${repo.owner}/${repo.repo}/contents/$path?ref=$b")
        val json = httpGet(url)
        val response = gson.fromJson(json, GitHubContentResponse::class.java)
        if (response.content == null) {
            return Result.failure(Exception("文件不存在或为目录: $path"))
        }
        val decoded = decodeBase64(response.content)
        Result.success(GitHubFileContent(
            path = response.path,
            content = decoded,
            sha = response.sha,
            size = response.size ?: 0
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun listDirectory(path: String = "", branch: String = ""): Result<List<GitHubDirEntry>> = try {
        val repo = currentRepo ?: return Result.failure(Exception("未连接仓库"))
        val b = branch.ifEmpty { repo.branch }
        val pathPart = if (path.isEmpty()) "" else "/$path"
        val url = apiUrl("/repos/${repo.owner}/${repo.repo}/contents$pathPart?ref=$b")
        val json = httpGet(url)
        val items = gson.fromJson(json, Array<GitHubContentResponse>::class.java)
        Result.success(items.map { item ->
            GitHubDirEntry(
                name = item.name,
                path = item.path,
                type = item.type,
                size = item.size ?: 0
            )
        })
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun writeFile(
        path: String,
        content: String,
        message: String,
        branch: String = "",
        sha: String? = null
    ): Result<GitHubCommitResult> = try {
        val repo = currentRepo ?: return Result.failure(Exception("未连接仓库"))
        val b = branch.ifEmpty { repo.branch }
        val url = apiUrl("/repos/${repo.owner}/${repo.repo}/contents/$path")

        val existingSha = sha ?: try {
            val existing = readFile(path, b).getOrNull()
            existing?.sha
        } catch (_: Exception) {
            null
        }

        val encodedContent = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)

        val bodyMap = mutableMapOf(
            "message" to message,
            "content" to encodedContent,
            "branch" to b
        )
        if (existingSha != null) {
            bodyMap["sha"] = existingSha
        }

        val (code, responseJson) = httpPut(url, gson.toJson(bodyMap))
        if (code in 200..299) {
            val commitResponse = gson.fromJson(responseJson, GitHubPutContentResponse::class.java)
            Result.success(GitHubCommitResult(
                sha = commitResponse.commit?.sha ?: "",
                htmlUrl = commitResponse.commit?.htmlUrl ?: ""
            ))
        } else {
            val errorMsg = try {
                gson.fromJson(responseJson, GitHubErrorResponse::class.java).message
            } catch (_: Exception) {
                responseJson.take(200)
            }
            Result.failure(Exception("写入失败 ($code): $errorMsg"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteFile(
        path: String,
        message: String,
        branch: String = ""
    ): Result<GitHubCommitResult> = try {
        val repo = currentRepo ?: return Result.failure(Exception("未连接仓库"))
        val b = branch.ifEmpty { repo.branch }
        val existing = readFile(path, b).getOrNull()
            ?: return Result.failure(Exception("文件不存在: $path"))

        val url = apiUrl("/repos/${repo.owner}/${repo.repo}/contents/$path")
        val bodyMap = mapOf(
            "message" to message,
            "sha" to existing.sha,
            "branch" to b
        )

        val (code, responseJson) = httpDelete(url, gson.toJson(bodyMap))
        if (code in 200..299) {
            val commitResponse = gson.fromJson(responseJson, GitHubCommitResponse::class.java)
            Result.success(GitHubCommitResult(
                sha = commitResponse.sha ?: "",
                htmlUrl = commitResponse.htmlUrl ?: ""
            ))
        } else {
            Result.failure(Exception("删除失败 ($code)"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun listBranches(): Result<List<GitHubBranchInfo>> = try {
        val repo = currentRepo ?: return Result.failure(Exception("未连接仓库"))
        val url = apiUrl("/repos/${repo.owner}/${repo.repo}/branches")
        val json = httpGet(url)
        val branches = gson.fromJson(json, Array<GitHubBranchResponse>::class.java)
        Result.success(branches.map {
            GitHubBranchInfo(
                name = it.name,
                isDefault = it.name == repo.branch
            )
        })
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun createBranch(branchName: String, fromBranch: String = ""): Result<String> = try {
        val repo = currentRepo ?: return Result.failure(Exception("未连接仓库"))
        val sourceBranch = fromBranch.ifEmpty { repo.branch }

        val refUrl = apiUrl("/repos/${repo.owner}/${repo.repo}/git/ref/heads/$sourceBranch")
        val refJson = httpGet(refUrl)
        val refResponse = gson.fromJson(refJson, GitHubRefResponse::class.java)
        val sha = refResponse.objectData?.sha
            ?: return Result.failure(Exception("无法获取 $sourceBranch 的 SHA"))

        val createUrl = apiUrl("/repos/${repo.owner}/${repo.repo}/git/refs")
        val bodyMap = mapOf(
            "ref" to "refs/heads/$branchName",
            "sha" to sha
        )
        val (code, _) = httpPost(createUrl, gson.toJson(bodyMap))
        if (code in 200..299) {
            currentRepo = repo.copy(branch = branchName)
            Result.success("分支 $branchName 创建成功，基于 $sourceBranch")
        } else {
            Result.failure(Exception("创建分支失败 ($code)"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getRepoInfo(): Result<String> = try {
        val repo = currentRepo ?: return Result.failure(Exception("未连接仓库"))
        val url = apiUrl("/repos/${repo.owner}/${repo.repo}")
        val json = httpGet(url)
        val info = gson.fromJson(json, GitHubRepoResponse::class.java)
        Result.success(buildString {
            append("仓库: ${info.fullName}\n")
            append("描述: ${info.description ?: "无"}\n")
            append("默认分支: ${info.defaultBranch}\n")
            append("星标: ${info.stargazersCount} | Fork: ${info.forksCount}\n")
            append("语言: ${info.language ?: "未知"}\n")
            append("私有: ${if (info.private) "是" else "否"}\n")
            append("URL: ${info.htmlUrl}")
        })
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getRecentCommits(count: Int = 10): Result<String> = try {
        val repo = currentRepo ?: return Result.failure(Exception("未连接仓库"))
        val url = apiUrl("/repos/${repo.owner}/${repo.repo}/commits?per_page=$count&sha=${repo.branch}")
        val json = httpGet(url)
        val commits = gson.fromJson(json, Array<GitHubCommitItem>::class.java)
        Result.success(commits.mapIndexed { idx, c ->
            val shortSha = c.sha?.take(7) ?: "?"
            val msg = c.commit?.message?.lines()?.first()?.take(72) ?: ""
            val author = c.commit?.author?.name ?: ""
            val date = c.commit?.author?.date?.take(10) ?: ""
            "${idx + 1}. $shortSha $msg ($author, $date)"
        }.joinToString("\n"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun createPullRequest(
        title: String,
        body: String = "",
        headBranch: String,
        baseBranch: String = ""
    ): Result<GitHubPRInfo> = try {
        val repo = currentRepo ?: return Result.failure(Exception("未连接仓库"))
        val base = baseBranch.ifEmpty { repo.branch }
        val url = apiUrl("/repos/${repo.owner}/${repo.repo}/pulls")
        val bodyMap = mapOf(
            "title" to title,
            "body" to body,
            "head" to headBranch,
            "base" to base
        )
        val (code, responseJson) = httpPost(url, gson.toJson(bodyMap))
        if (code in 200..299) {
            val pr = gson.fromJson(responseJson, GitHubPRResponse::class.java)
            Result.success(GitHubPRInfo(
                number = pr.number ?: 0,
                title = pr.title ?: "",
                state = pr.state ?: "",
                htmlUrl = pr.htmlUrl ?: "",
                headBranch = headBranch,
                baseBranch = base
            ))
        } else {
            Result.failure(Exception("创建 PR 失败 ($code)"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun searchCode(query: String): Result<String> = try {
        val repo = currentRepo ?: return Result.failure(Exception("未连接仓库"))
        val encodedQuery = java.net.URLEncoder.encode("$query repo:${repo.owner}/${repo.repo}", "UTF-8")
        val url = apiUrl("/search/code?q=$encodedQuery&per_page=20")
        val json = httpGet(url)
        val response = gson.fromJson(json, GitHubSearchResponse::class.java)
        if (response.items.isNullOrEmpty()) {
            Result.success("未找到匹配的代码")
        } else {
            Result.success(response.items.map { item ->
                "${item.path} (${item.name})"
            }.joinToString("\n"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun decodeBase64(encoded: String): String {
        val cleaned = encoded.replace("\n", "").replace("\r", "")
        return String(Base64.decode(cleaned, Base64.NO_WRAP), Charsets.UTF_8)
    }

    private data class GitHubContentResponse(
        val name: String = "",
        val path: String = "",
        val sha: String = "",
        val size: Int? = 0,
        val type: String = "",
        val content: String? = null,
        val encoding: String? = null
    )

    private data class GitHubPutContentResponse(
        val content: GitHubContentResponse? = null,
        val commit: GitHubCommitData? = null
    )

    private data class GitHubCommitData(
        val sha: String = "",
        val htmlUrl: String = ""
    )

    private data class GitHubCommitResponse(
        val sha: String? = "",
        val htmlUrl: String? = ""
    )

    private data class GitHubErrorResponse(
        val message: String = ""
    )

    private data class GitHubBranchResponse(
        val name: String = ""
    )

    private data class GitHubRefResponse(
        @SerializedName("object") val objectData: GitHubRefObject? = null
    )

    private data class GitHubRefObject(
        val sha: String = ""
    )

    private data class GitHubRepoResponse(
        @SerializedName("full_name") val fullName: String = "",
        val description: String? = "",
        @SerializedName("default_branch") val defaultBranch: String = "main",
        @SerializedName("stargazers_count") val stargazersCount: Int = 0,
        @SerializedName("forks_count") val forksCount: Int = 0,
        val language: String? = "",
        val private: Boolean = false,
        @SerializedName("html_url") val htmlUrl: String = ""
    )

    private data class GitHubCommitItem(
        val sha: String? = "",
        val commit: GitHubCommitDetail? = null
    )

    private data class GitHubCommitDetail(
        val message: String? = "",
        val author: GitHubCommitAuthor? = null
    )

    private data class GitHubCommitAuthor(
        val name: String? = "",
        val date: String? = ""
    )

    private data class GitHubPRResponse(
        val number: Int? = 0,
        val title: String? = "",
        val state: String? = "",
        @SerializedName("html_url") val htmlUrl: String? = ""
    )

    private data class GitHubSearchResponse(
        val total_count: Int = 0,
        val items: List<GitHubSearchItem>? = null
    )

    private data class GitHubSearchItem(
        val name: String = "",
        val path: String = ""
    )
}
