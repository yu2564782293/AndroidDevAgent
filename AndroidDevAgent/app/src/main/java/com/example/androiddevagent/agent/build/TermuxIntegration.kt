package com.example.androiddevagent.agent.build

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val success: Boolean
)

@Singleton
class TermuxIntegration @Inject constructor() {

    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun executeCommand(context: Context, command: String, workingDir: String): CommandResult {
        if (!isTermuxInstalled(context)) {
            return CommandResult(-1, "Termux 未安装", false)
        }

        return try {
            val intent = Intent("com.termux.RUN_COMMAND")
            intent.setClassName("com.termux", "com.termux.app.RunCommandService")
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", workingDir)
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            context.startService(intent)
            CommandResult(0, "命令已发送至 Termux", true)
        } catch (e: Exception) {
            CommandResult(-1, "Termux 命令执行失败: ${e.message}", false)
        }
    }

    fun executeLocalCommand(
        command: String,
        workingDir: String,
        timeoutMs: Long = 120000
    ): CommandResult {
        val workDir = File(workingDir)
        if (!workDir.exists()) {
            return CommandResult(-1, "工作目录不存在: $workingDir", false)
        }

        return try {
            val process = ProcessBuilder()
                .command("bash", "-c", command)
                .directory(workDir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return CommandResult(-1, "命令执行超时 (${timeoutMs}ms)\n输出:\n$output", false)
            }

            val exitCode = process.exitValue()
            CommandResult(exitCode, output.trim(), exitCode == 0)
        } catch (e: Exception) {
            CommandResult(-1, "本地命令执行失败: ${e.message}", false)
        }
    }

    fun executeGradleBuild(projectPath: String, task: String = "assembleDebug"): CommandResult {
        val projectDir = File(projectPath)
        if (!projectDir.exists()) {
            return CommandResult(-1, "项目目录不存在: $projectPath", false)
        }

        val gradlew = File(projectDir, "gradlew")
        if (!gradlew.exists()) {
            return CommandResult(-1, "项目中未找到 gradlew", false)
        }

        if (!gradlew.canExecute()) {
            gradlew.setExecutable(true)
        }

        return executeLocalCommand("./gradlew $task", projectPath)
    }

    fun installApk(context: Context, apkPath: String): Boolean {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            return false
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(
                Uri.fromFile(apkFile),
                "application/vnd.android.package-archive"
            )
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val result = executeLocalCommand("pm install -r $apkPath", "/")
                result.success
            } catch (e2: Exception) {
                false
            }
        }
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun readLogcat(filter: String = "", lines: Int = 50): String {
        return try {
            val command = if (filter.isNotEmpty()) {
                "logcat -d -t $lines -s $filter"
            } else {
                "logcat -d -t $lines"
            }

            val result = executeLocalCommand(command, "/")
            if (result.success) {
                result.output
            } else {
                "Logcat 读取失败: ${result.output}"
            }
        } catch (e: Exception) {
            "Logcat 读取错误: ${e.message}"
        }
    }
}
