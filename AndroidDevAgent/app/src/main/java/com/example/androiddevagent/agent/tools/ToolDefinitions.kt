package com.example.androiddevagent.agent.tools

import com.example.androiddevagent.agent.llm.ChatCompletionRequest

object ToolDefinitions {

    fun allTools(): List<ChatCompletionRequest.ToolDefinition> = listOf(
        readFileTool(),
        writeFileTool(),
        editFileTool(),
        listFilesTool(),
        deleteFileTool(),
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
