package com.example.androiddevagent.agent.tools

import com.example.androiddevagent.agent.llm.ChatCompletionRequest

object ToolDefinitions {

    fun allTools(): List<ChatCompletionRequest.ToolDefinition> = listOf(
        readFileTool(),
        writeFileTool(),
        editFileTool(),
        listFilesTool(),
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
        askUserTool()
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
            description = "Make a targeted search-and-replace edit to a file. Finds the exact old_text in the file and replaces it with new_text. The old_text must match exactly. After editing, a syntax check is performed automatically.",
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
                required = listOf()
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
                required = listOf()
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
                required = listOf()
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
                required = listOf()
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
                required = listOf()
            )
        )
    )

    private fun gitRevertTool() = ChatCompletionRequest.ToolDefinition(
        function = ChatCompletionRequest.FunctionDef(
            name = "git_revert",
            description = "Undo the last Git commit. Changes are preserved in the working directory. Use this to rollback a bad change.",
            parameters = ChatCompletionRequest.Parameters(
                properties = mapOf(),
                required = listOf()
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
}
