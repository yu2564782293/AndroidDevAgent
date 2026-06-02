package com.example.androiddevagent.agent.vcs

import com.example.androiddevagent.agent.build.TermuxIntegration
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
    private val secureStorage: SecureStorage,
    private val termuxIntegration: TermuxIntegration
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
        return executeGit("git init")
    }

    fun autoCommit(message: String): GitResult {
        if (!isGitRepo()) {
            val initResult = init()
            if (!initResult.success) return initResult
        }

        val addResult = executeGit("git add -A")
        if (!addResult.success) return addResult

        val statusResult = executeGit("git status --porcelain")
        if (statusResult.output.isBlank()) {
            return GitResult(true, "No changes to commit")
        }

        val escapedMessage = message.replace("\"", "\\\"").replace("'", "'\\''")
        return executeGit("git commit -m '$escapedMessage'")
    }

    fun getDiff(): GitResult {
        if (!isGitRepo()) return GitResult(false, "Not a git repository")
        return executeGit("git diff HEAD")
    }

    fun getDiffStat(): GitResult {
        if (!isGitRepo()) return GitResult(false, "Not a git repository")
        return executeGit("git diff --stat HEAD")
    }

    fun revertLastCommit(): GitResult {
        if (!isGitRepo()) return GitResult(false, "Not a git repository")
        return executeGit("git reset --soft HEAD~1")
    }

    fun getLog(count: Int = 10): GitResult {
        if (!isGitRepo()) return GitResult(false, "Not a git repository")
        return executeGit("git log --oneline -$count")
    }

    fun getStatus(): GitResult {
        if (!isGitRepo()) return GitResult(false, "Not a git repository")
        return executeGit("git status --short")
    }

    fun clone(url: String, directory: String): GitResult {
        val targetDir = File(directory)
        if (targetDir.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
            return GitResult(false, "目标目录不为空: $directory")
        }
        val authUrl = authenticateUrl(url)
        targetDir.parentFile?.mkdirs()
        val parentDir = targetDir.parentFile?.absolutePath ?: "/sdcard"
        val dirName = targetDir.name
        return executeGitInDir(parentDir, "git clone $authUrl $dirName")
    }

    fun push(remote: String = "origin", branch: String = ""): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        configureAuth()
        val branchArg = if (branch.isNotEmpty()) " $branch" else ""
        return executeGit("git push $remote$branchArg")
    }

    fun pull(remote: String = "origin", branch: String = ""): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        configureAuth()
        val branchArg = if (branch.isNotEmpty()) " $branch" else ""
        return executeGit("git pull $remote$branchArg")
    }

    fun fetch(remote: String = "origin"): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        configureAuth()
        return executeGit("git fetch $remote")
    }

    fun createBranch(name: String): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        return executeGit("git checkout -b $name")
    }

    fun switchBranch(name: String): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        return executeGit("git checkout $name")
    }

    fun listBranches(): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        return executeGit("git branch -a")
    }

    fun getCurrentBranch(): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        return executeGit("git rev-parse --abbrev-ref HEAD")
    }

    fun addRemote(name: String, url: String): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        val authUrl = authenticateUrl(url)
        val existing = executeGit("git remote")
        if (existing.output.lines().contains(name)) {
            return executeGit("git remote set-url $name $authUrl")
        }
        return executeGit("git remote add $name $authUrl")
    }

    fun getRemotes(): GitResult {
        if (!isGitRepo()) return GitResult(false, "不是 Git 仓库")
        return executeGit("git remote -v")
    }

    private fun configureAuth() {
        val token = secureStorage.getGitToken("github")
        if (token.isNotEmpty()) {
            try {
                executeGit("git config credential.helper store")
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

    private fun executeGit(command: String): GitResult {
        if (projectPath.isEmpty()) {
            return GitResult(false, "未设置项目路径")
        }

        val projectDir = File(projectPath)
        if (!projectDir.exists()) {
            return GitResult(false, "项目目录不存在: $projectPath")
        }

        return executeGitInDir(projectPath, command)
    }

    private fun executeGitInDir(workingDir: String, command: String): GitResult {
        val termuxResult = termuxIntegration.executeLocalCommand(
            command,
            workingDir,
            120000L
        )

        if (termuxResult.success) {
            return GitResult(true, termuxResult.output)
        }

        val output = termuxResult.output
        if (output.contains("bash:") && output.contains("not found")) {
            return tryProcessBuilder(workingDir, command)
        }

        return GitResult(false, output)
    }

    private fun tryProcessBuilder(workingDir: String, command: String): GitResult {
        return try {
            val parts = command.split(" ")
            val process = ProcessBuilder()
                .command(parts)
                .directory(File(workingDir))
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
            GitResult(false, "Git 不可用: ${e.message}\n提示: 请安装 Termux 并在其中运行 pkg install git")
        }
    }
}
