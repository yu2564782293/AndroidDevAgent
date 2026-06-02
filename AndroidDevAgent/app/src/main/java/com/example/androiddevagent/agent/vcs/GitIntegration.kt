package com.example.androiddevagent.agent.vcs

import com.example.androiddevagent.data.SecureStorage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class GitResult(
    val success: Boolean,
    val output: String
)

@Singleton
class GitIntegration @Inject constructor(
    private val secureStorage: SecureStorage
) {

    private var projectPath: String = ""

    fun setProjectPath(path: String) {
        projectPath = path
    }

    fun isGitRepo(): Boolean {
        if (projectPath.isEmpty()) return false
        val gitDir = File(projectPath, ".git")
        return gitDir.exists() && gitDir.isDirectory
    }

    fun init(): GitResult {
        return executeGit("init")
    }

    fun autoCommit(message: String): GitResult {
        if (!isGitRepo()) {
            val initResult = init()
            if (!initResult.success) return initResult
        }

        val addResult = executeGit("add", "-A")
        if (!addResult.success) return addResult

        val statusResult = executeGit("status", "--porcelain")
        if (statusResult.output.isBlank()) {
            return GitResult(true, "No changes to commit")
        }

        return executeGit("commit", "-m", message)
    }

    fun getDiff(): GitResult {
        if (!isGitRepo()) return GitResult(false, "Not a git repository")
        return executeGit("diff", "HEAD")
    }

    fun getDiffStat(): GitResult {
        if (!isGitRepo()) return GitResult(false, "Not a git repository")
        return executeGit("diff", "--stat", "HEAD")
    }

    fun revertLastCommit(): GitResult {
        if (!isGitRepo()) return GitResult(false, "Not a git repository")
        return executeGit("reset", "--soft", "HEAD~1")
    }

    fun getLog(count: Int = 10): GitResult {
        if (!isGitRepo()) return GitResult(false, "Not a git repository")
        return executeGit("log", "--oneline", "-$count")
    }

    fun getStatus(): GitResult {
        if (!isGitRepo()) return GitResult(false, "Not a git repository")
        return executeGit("status", "--short")
    }

    fun clone(url: String, directory: String): GitResult {
        val targetDir = File(directory)
        if (targetDir.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
            return GitResult(false, "目标目录不为空: $directory")
        }
        val authUrl = authenticateUrl(url)
        return executeGitInDir(targetDir.parentFile ?: File("/sdcard"), "clone", authUrl, directory)
    }

    fun push(remote: String = "origin", branch: String = ""): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        configureAuth()
        return if (branch.isNotEmpty()) {
            executeGit("push", remote, branch)
        } else {
            executeGit("push", remote)
        }
    }

    fun pull(remote: String = "origin", branch: String = ""): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        configureAuth()
        return if (branch.isNotEmpty()) {
            executeGit("pull", remote, branch)
        } else {
            executeGit("pull", remote)
        }
    }

    fun fetch(remote: String = "origin"): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        configureAuth()
        return executeGit("fetch", remote)
    }

    fun createBranch(name: String): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        return executeGit("checkout", "-b", name)
    }

    fun switchBranch(name: String): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        return executeGit("checkout", name)
    }

    fun listBranches(): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        return executeGit("branch", "-a")
    }

    fun getCurrentBranch(): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        return executeGit("rev-parse", "--abbrev-ref", "HEAD")
    }

    fun addRemote(name: String, url: String): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        val authUrl = authenticateUrl(url)
        val existing = executeGit("remote")
        if (existing.output.lines().contains(name)) {
            return executeGit("remote", "set-url", name, authUrl)
        }
        return executeGit("remote", "add", name, authUrl)
    }

    fun getRemotes(): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        return executeGit("remote", "-v")
    }

    private fun configureAuth() {
        val token = secureStorage.getGitToken("github")
        if (token.isNotEmpty()) {
            try {
                executeGit("config", "credential.helper", "store")
            } catch (_: Exception) {
            }
        }
    }

    private fun authenticateUrl(url: String): String {
        val token = secureStorage.getGitToken("github")
        if (token.isEmpty()) return url
        if (url.startsWith("https://github.com/")) {
            return url.replace("https://github.com/", "https://$token@github.com/")
        }
        return url
    }

    private fun executeGit(vararg args: String): GitResult {
        if (projectPath.isEmpty()) {
            return GitResult(false, "未设置项目路径")
        }

        val projectDir = File(projectPath)
        if (!projectDir.exists()) {
            return GitResult(false, "项目目录不存在: $projectPath")
        }

        return executeGitInDir(projectDir, *args)
    }

    private fun executeGitInDir(dir: File, vararg args: String): GitResult {
        return try {
            val process = ProcessBuilder()
                .command(listOf("git") + args.toList())
                .directory(dir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            if (process.exitValue() == 0) {
                GitResult(true, output.trim())
            } else {
                GitResult(false, output.trim())
            }
        } catch (e: Exception) {
            GitResult(false, "Git 错误: ${e.message}")
        }
    }
}
