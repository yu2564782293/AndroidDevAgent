package com.example.androiddevagent.agent.tools

import com.example.androiddevagent.agent.llm.ChatCompletionRequest

object ToolDefinitions {

    fun builtInTools(): List<ChatCompletionRequest.ToolDefinition> = listOf(
        readFileTool(),
        writeFileTool(),
        editFileTool(),
        listFilesTool(),
        globTool(),
        grepTool(),
        deleteFileTool(),
        gradleBuildTool(),
        runTestsTool(),
        readLogcatTool(),
        lintCheckTool(),
        searchCodeTool(),
        analyzeProjectTool(),
        findUsagesTool(),
        gitCommitTool(),
        gitDiffTool(),
        gitRevertTool(),
        askUserTool(),
        runCommandTool(),
        installApkTool(),
        launchAppTool(),
        gitCloneTool(),
        gitPushTool(),
        gitPullTool(),
        gitBranchTool(),
        todoWriteTool(),
        githubReadFileTool(),
        githubWriteFileTool(),
        githubListDirTool(),
        githubDeleteFileTool(),
        githubBranchTool(),
        githubRepoInfoTool(),
        githubCommitsTool(),
        githubCreatePRTool(),
        githubSearchCodeTool()
    )

    private fun readFileTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "read_file",
            description = "Read the contents of a file in the project. Returns file content with line numbers. By default returns the first 100 lines.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Relative path to the file from the project root"
                    ),
                    "start_line" to ChatCompletionRequest.PropertyDef(
                        type = "integer",
                        description = "Starting line number (1-based, optional, default: 1)"
                    ),
                    "end_line" to ChatCompletionRequest.PropertyDef(
                        type = "integer",
                        description = "Ending line number (optional, default: start_line + 99)"
                    )
                ),
                required = listOf("path")
            )
        )
    )

    private fun writeFileTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "write_file",
            description = "Create a new file or completely overwrite an existing file with the given content. Use this for creating new files or when you need to replace the entire file content.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Relative path to the file from the project root"
                    ),
                    "content" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "The complete content to write to the file"
                    )
                ),
                required = listOf("path", "content")
            )
        )
    )

    private fun editFileTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "edit_file",
            description = "Make a targeted search-and-replace edit to a file. Finds the exact old_text in the file and replaces it with new_text. The old_text must match exactly. After editing, a syntax check is performed automatically. If old_text appears multiple times, set replace_all to true to replace all occurrences, or provide more context to make it unique.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Relative path to the file from the project root"
                    ),
                    "old_text" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "The exact text to find in the file (must match exactly)"
                    ),
                    "new_text" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "The text to replace the old_text with"
                    ),
                    "replace_all" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Set to 'true' to replace all occurrences of old_text (optional, default: false). Use when you want to rename a variable or update repeated patterns."
                    )
                ),
                required = listOf("path", "old_text", "new_text")
            )
        )
    )

    private fun listFilesTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "list_files",
            description = "List files and directories in the project. Returns a tree structure showing the directory contents.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Relative directory path from the project root (use '.' for root)"
                    ),
                    "max_depth" to ChatCompletionRequest.PropertyDef(
                        type = "integer",
                        description = "Maximum depth to traverse (optional, default: 3)"
                    )
                ),
                required = listOf("path")
            )
        )
    )

    private fun deleteFileTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "delete_file",
            description = "Delete a file from the project. This action cannot be undone. Use with caution.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Relative path to the file to delete"
                    )
                ),
                required = listOf("path")
            )
        )
    )

    private fun gradleBuildTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "gradle_build",
            description = "Execute a Gradle build task in the project. Returns structured result with success/failure status and error summary if it fails. Common tasks: assembleDebug, assembleRelease, clean.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "task" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Gradle task to run (optional, default: assembleDebug)"
                    )
                ),
                required = emptyList()
            )
        )
    )

    private fun runTestsTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "run_tests",
            description = "Run tests in the project. Returns pass/fail counts and failure details. Optionally filter by test class.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "test_class" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Specific test class to run (optional, runs all tests if not specified)"
                    )
                ),
                required = emptyList()
            )
        )
    )

    private fun readLogcatTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "read_logcat",
            description = "Read recent Logcat output from the device. Useful for diagnosing runtime errors and crashes. Returns the most recent log lines filtered by tag or level.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "filter" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Filter pattern for log tags or messages (optional)"
                    ),
                    "lines" to ChatCompletionRequest.PropertyDef(
                        type = "integer",
                        description = "Number of recent lines to return (optional, default: 50)"
                    )
                ),
                required = emptyList()
            )
        )
    )

    private fun lintCheckTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "lint_check",
            description = "Run a syntax check on a file. Returns a list of issues found with line numbers and descriptions. Supports Kotlin, Java, and XML files.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Relative path to the file to check"
                    )
                ),
                required = listOf("path")
            )
        )
    )

    private fun searchCodeTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "search_code",
            description = "Search for a text pattern across all source files in the project. Returns matching file paths, line numbers, and context lines. Useful for finding where a class/function/variable is used.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "query" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "The text pattern to search for"
                    ),
                    "file_pattern" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "File extension filter (optional, e.g. '.kt', '.xml'). Searches all source files if not specified."
                    )
                ),
                required = listOf("query")
            )
        )
    )

    private fun analyzeProjectTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "analyze_project",
            description = "Analyze the project structure and return a comprehensive summary including modules, dependencies, key files, and manifest info. Use this to understand the project before making changes.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(),
                required = emptyList()
            )
        )
    )

    private fun findUsagesTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "find_usages",
            description = "Find all usages of a symbol (class name, function name, variable) across the project. Returns file paths and line numbers where the symbol is referenced.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "symbol" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "The symbol name to search for (class, function, or variable name)"
                    ),
                    "path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Directory to search in (optional, defaults to project root)"
                    )
                ),
                required = listOf("symbol")
            )
        )
    )

    private fun gitCommitTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "git_commit",
            description = "Commit all current changes to Git. Returns the commit hash. Changes are automatically staged before committing.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "message" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Commit message describing the changes"
                    )
                ),
                required = listOf("message")
            )
        )
    )

    private fun gitDiffTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "git_diff",
            description = "Show uncommitted changes in the project. Returns a summary of modified, added, and deleted files.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "stat" to ChatCompletionRequest.PropertyDef(
                        type = "boolean",
                        description = "If true, show only stat summary instead of full diff (optional, default: true)"
                    )
                ),
                required = emptyList()
            )
        )
    )

    private fun gitRevertTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "git_revert",
            description = "Undo the last Git commit. Changes are preserved in the working directory. Use this to rollback a bad change.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(),
                required = emptyList()
            )
        )
    )

    private fun askUserTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "ask_user",
            description = "Ask the user a question when you need clarification or a decision. The user's response will be provided as the tool result.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "question" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "The question to ask the user"
                    )
                ),
                required = listOf("question")
            )
        )
    )

    private fun runCommandTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "run_command",
            description = "Execute a shell command on the device. Use for running builds, tests, or any terminal command. The command runs in the project directory by default.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "command" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "The shell command to execute"
                    ),
                    "working_dir" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Working directory for the command. Defaults to the project root."
                    ),
                    "timeout" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Timeout in milliseconds. Defaults to 120000 (2 minutes)."
                    )
                ),
                required = listOf("command")
            )
        )
    )

    private fun installApkTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "install_apk",
            description = "Install an APK file on the device. After a successful build, use this to install the generated APK.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "apk_path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Absolute path to the APK file to install"
                    )
                ),
                required = listOf("apk_path")
            )
        )
    )

    private fun launchAppTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "launch_app",
            description = "Launch an installed application by its package name. Use after installing an APK to test it.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "package_name" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "The package name of the app to launch (e.g. com.example.myapp)"
                    )
                ),
                required = listOf("package_name")
            )
        )
    )

    private fun gitCloneTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "git_clone",
            description = "Clone a remote Git repository to a local directory. Use this to import an existing project from GitHub.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "url" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "The Git repository URL to clone (e.g. https://github.com/user/repo.git)"
                    ),
                    "directory" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Local directory path to clone into (e.g. /sdcard/MyProject)"
                    )
                ),
                required = listOf("url", "directory")
            )
        )
    )

    private fun gitPushTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "git_push",
            description = "Push local commits to a remote Git repository. Requires GitHub token to be configured in settings.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "remote" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Remote name (optional, default: origin)"
                    ),
                    "branch" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Branch name to push (optional, pushes current branch if not specified)"
                    )
                ),
                required = emptyList()
            )
        )
    )

    private fun gitPullTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "git_pull",
            description = "Pull latest changes from a remote Git repository. Use this to sync with the remote.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "remote" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Remote name (optional, default: origin)"
                    ),
                    "branch" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Branch name to pull (optional, pulls current branch if not specified)"
                    )
                ),
                required = emptyList()
            )
        )
    )

    private fun gitBranchTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "git_branch",
            description = "Manage Git branches: list, create, or switch branches.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "action" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Action to perform: 'list' (list all branches), 'create' (create new branch), 'switch' (switch to existing branch)"
                    ),
                    "name" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Branch name (required for create and switch actions)"
                    )
                ),
                required = listOf("action")
            )
        )
    )

    private fun globTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "glob",
            description = "Find files matching a glob pattern. Returns list of matching file paths relative to project root. Fast way to find files by name pattern. Examples: '**/*.kt' finds all Kotlin files, 'src/**/MainActivity.kt' finds MainActivity in any subdirectory.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "pattern" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Glob pattern to match (e.g. '**/*.kt', 'src/**/*.xml', '**/build.gradle')"
                    ),
                    "path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Directory to search in, relative to project root (optional, default: project root)"
                    )
                ),
                required = listOf("pattern")
            )
        )
    )

    private fun grepTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "grep",
            description = "Search for a regex pattern in file contents. Returns matching lines with file paths and line numbers. More powerful than search_code - supports regex patterns. Automatically skips build/, .gradle/, .idea/ directories.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "pattern" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Regular expression pattern to search for"
                    ),
                    "path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Directory to search in, relative to project root (optional, default: project root)"
                    ),
                    "file_pattern" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "File extension filter (optional, e.g. '.kt', '.xml'). Searches all source files if not specified."
                    ),
                    "case_insensitive" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Set to 'true' for case-insensitive search (optional, default: false)"
                    )
                ),
                required = listOf("pattern")
            )
        )
    )

    private fun todoWriteTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "todo_write",
            description = "Update your task list. Use this to track progress on multi-step tasks. Write the current state of your todo list after completing each step. This helps you stay organized and not forget pending work.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "todos" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "JSON array of todo items, each with 'content' (string) and 'status' ('pending'|'in_progress'|'completed'). Example: [{\"content\":\"Read MainActivity.kt\",\"status\":\"completed\"},{\"content\":\"Add login screen\",\"status\":\"in_progress\"},{\"content\":\"Test login flow\",\"status\":\"pending\"}]"
                    )
                ),
                required = listOf("todos")
            )
        )
    )

    private fun githubReadFileTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "github_read_file",
            description = "Read a file directly from the connected GitHub repository. No local files needed - reads from the cloud. Use this to understand existing code before making changes.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Path to the file in the repository (e.g. 'app/src/main/java/com/example/MainActivity.kt')"
                    ),
                    "branch" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Branch to read from (optional, defaults to current branch)"
                    )
                ),
                required = listOf("path")
            )
        )
    )

    private fun githubWriteFileTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "github_write_file",
            description = "Create or update a file directly in the GitHub repository. The change is committed immediately to the cloud. Use this to write code to the remote repository. If the file exists, it will be updated; if not, it will be created.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Path to the file in the repository"
                    ),
                    "content" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "The complete file content to write"
                    ),
                    "message" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Commit message describing the change"
                    ),
                    "branch" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Branch to write to (optional, defaults to current branch)"
                    )
                ),
                required = listOf("path", "content", "message")
            )
        )
    )

    private fun githubListDirTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "github_list_dir",
            description = "List files and directories in the connected GitHub repository. Returns directory contents with file types and sizes. Use this to explore the repository structure.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Directory path to list (empty string for root, e.g. 'app/src/main/java')"
                    ),
                    "branch" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Branch to list from (optional, defaults to current branch)"
                    )
                ),
                required = emptyList()
            )
        )
    )

    private fun githubDeleteFileTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "github_delete_file",
            description = "Delete a file from the GitHub repository. The deletion is committed immediately. Use with caution.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "path" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Path to the file to delete"
                    ),
                    "message" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Commit message for the deletion"
                    ),
                    "branch" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Branch to delete from (optional, defaults to current branch)"
                    )
                ),
                required = listOf("path", "message")
            )
        )
    )

    private fun githubBranchTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "github_branch",
            description = "Manage GitHub repository branches: list branches, create a new branch, or switch the current working branch. Creating a branch on GitHub allows you to work on changes in isolation.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "action" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Action: 'list' (list all branches), 'create' (create new branch), 'switch' (switch current working branch)"
                    ),
                    "name" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Branch name (required for create and switch)"
                    ),
                    "from_branch" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Source branch to create from (optional, defaults to current branch)"
                    )
                ),
                required = listOf("action")
            )
        )
    )

    private fun githubRepoInfoTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "github_repo_info",
            description = "Get information about the connected GitHub repository: name, description, default branch, language, visibility, etc.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(),
                required = emptyList()
            )
        )
    )

    private fun githubCommitsTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "github_commits",
            description = "Get recent commits from the GitHub repository. Shows commit messages, authors, and dates.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "count" to ChatCompletionRequest.PropertyDef(
                        type = "integer",
                        description = "Number of recent commits to show (optional, default: 10)"
                    )
                ),
                required = emptyList()
            )
        )
    )

    private fun githubCreatePRTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "github_create_pr",
            description = "Create a Pull Request on GitHub. Use this after making changes on a feature branch to request merging into the main branch.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "title" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Title of the Pull Request"
                    ),
                    "body" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Description of the Pull Request (optional)"
                    ),
                    "head_branch" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "The branch containing your changes"
                    ),
                    "base_branch" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "The branch you want to merge into (optional, defaults to main)"
                    )
                ),
                required = listOf("title", "head_branch")
            )
        )
    )

    private fun githubSearchCodeTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "github_search_code",
            description = "Search for code in the GitHub repository. Returns matching file paths. Useful for finding where a class or function is defined.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "query" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Search query (e.g. 'class MainActivity', 'fun onCreate')"
                    )
                ),
                required = listOf("query")
            )
        )
    )

    fun skillManagementTools(): List<ChatCompletionRequest.ToolDefinition> = listOf(
        skillSearchTool(),
        skillInstallTool(),
        skillListTool(),
        skillUninstallTool(),
        skillUpdateTool(),
        skillConfigTool(),
        skillCreateTool(),
        skillRollbackTool(),
        skillPublishTool()
    )

    fun allTools(skillTools: List<ChatCompletionRequest.ToolDefinition> = emptyList()): List<ChatCompletionRequest.ToolDefinition> {
        return builtInTools() + skillManagementTools() + skillTools
    }

    private fun skillSearchTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "skill_search",
            description = "搜索可安装的技能扩展。技能可以为 DEREK AI 添加新能力，如网页抓取、API测试、代码审查等。当现有工具无法完成任务时，搜索并安装相关技能。",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "query" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "搜索关键词，如 'web scraping', 'api testing', 'code review'"
                    )
                ),
                required = listOf("query")
            )
        )
    )

    private fun skillInstallTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "skill_install",
            description = "安装技能扩展。安装后技能提供的工具将立即可用。支持从 GitHub 仓库或 URL 安装。",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "source" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "来源类型: 'github' 或 'url' (默认: github)"
                    ),
                    "repo" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "GitHub 仓库路径 (如 'derek-skills/web-scraper') 或技能 URL"
                    ),
                    "branch" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "Git 分支 (可选, 默认: main)"
                    )
                ),
                required = listOf("repo")
            )
        )
    )

    private fun skillListTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "skill_list",
            description = "列出所有已安装的技能扩展及其提供的工具。",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(),
                required = emptyList()
            )
        )
    )

    private fun skillUninstallTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "skill_uninstall",
            description = "卸载已安装的技能扩展。卸载后该技能提供的工具将不再可用。",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "skill_id" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "要卸载的技能 ID"
                    )
                ),
                required = listOf("skill_id")
            )
        )
    )

    private fun skillUpdateTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "skill_update",
            description = "更新已安装的技能扩展到最新版本。",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "skill_id" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "要更新的技能 ID"
                    )
                ),
                required = listOf("skill_id")
            )
        )
    )

    private fun skillConfigTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "skill_config",
            description = "查看或修改技能扩展的配置项。不提供 key/value 时查看配置，提供时修改配置。",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "skill_id" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "技能 ID"
                    ),
                    "key" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "配置项名称 (可选, 不提供则查看所有配置)"
                    ),
                    "value" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "配置项值 (可选, 不提供则查看配置)"
                    )
                ),
                required = listOf("skill_id")
            )
        )
    )

    private fun skillCreateTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "skill_create",
            description = "创建自定义技能扩展。可以创建脚本型、提示型或混合型技能，让 DEREK AI 获得新能力。创建后技能立即可用。",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "type" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "技能类型: script(脚本), prompt(纯知识), hybrid(混合)"
                    ),
                    "id" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "技能唯一ID，如 my-custom-tool"
                    ),
                    "name" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "技能显示名称"
                    ),
                    "description" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "技能描述"
                    ),
                    "tool_name" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "提供的工具名称 (script/hybrid 类型必填)"
                    ),
                    "tool_description" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "工具描述 (script/hybrid 类型必填)"
                    ),
                    "knowledge" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "技能知识文本，注入到 system prompt (prompt 类型必填)"
                    )
                ),
                required = listOf("type", "id", "name", "description")
            )
        )
    )

    private fun skillRollbackTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "skill_rollback",
            description = "回滚技能到上一个版本。当技能更新后出现问题时可使用此工具恢复。",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "skill_id" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "要回滚的技能 ID"
                    )
                ),
                required = listOf("skill_id")
            )
        )
    )

    private fun skillPublishTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "skill_publish",
            description = "将自定义技能发布到技能市场，供其他用户安装使用。也可导出为离线技能包。",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(
                    "skill_id" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "要发布的技能 ID"
                    ),
                    "action" to ChatCompletionRequest.PropertyDef(
                        type = "string",
                        description = "操作: publish(发布到市场) 或 export(导出离线包)"
                    )
                ),
                required = listOf("skill_id", "action")
            )
        )
    )
}
