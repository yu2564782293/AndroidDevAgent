package com.example.androiddevagent.agent.vcs

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class GitResult(
    val success: Boolean,
    val output: String
)

@Singleton
class GitIntegration @Inject constructor() {

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

    private fun executeGit(vararg args: String): GitResult {
        if (projectPath.isEmpty()) {
            return GitResult(false, "Project path not set")
        }

        val projectDir = File(projectPath)
        if (!projectDir.exists()) {
            return GitResult(false, "Project directory not found: $projectPath")
        }

        return try {
            val process = ProcessBuilder()
                .command(listOf("git") + args.toList())
                .directory(projectDir)
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
            GitResult(false, "Git error: ${e.message}")
        }
    }
}
