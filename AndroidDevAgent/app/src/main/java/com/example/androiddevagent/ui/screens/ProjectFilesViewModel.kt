package com.example.androiddevagent.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.engine.AgentEngine
import com.example.androiddevagent.agent.tools.ToolExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ProjectFilesUiState(
    val projectPath: String = "",
    val files: List<FileNode> = emptyList(),
    val isLoading: Boolean = false,
    val expandedDirs: Set<String> = emptySet()
)

@HiltViewModel
class ProjectFilesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val toolExecutor: ToolExecutor,
    private val agentEngine: AgentEngine
) : ViewModel() {

    private val prefs by lazy {
        context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<ProjectFilesUiState> = _uiState.asStateFlow()

    private fun loadState(): ProjectFilesUiState {
        val path = prefs.getString("project_path", "") ?: ""
        val state = ProjectFilesUiState(projectPath = path)
        return if (path.isNotEmpty()) {
            state.copy(files = listFiles(path, emptySet()))
        } else state
    }

    fun selectProject() {
        // TODO: integrate SAF file picker
    }

    fun toggleDirectory(path: String) {
        val current = _uiState.value
        val expanded = current.expandedDirs.toMutableSet()
        if (path in expanded) {
            expanded.remove(path)
        } else {
            expanded.add(path)
        }
        _uiState.value = current.copy(
            expandedDirs = expanded,
            files = listFiles(current.projectPath, expanded)
        )
    }

    fun searchFiles(query: String) {
        val current = _uiState.value
        if (query.isBlank()) {
            _uiState.value = current.copy(files = listFiles(current.projectPath, current.expandedDirs))
            return
        }
        viewModelScope.launch {
            _uiState.value = current.copy(isLoading = true)
            val results = searchFilesRecursive(File(current.projectPath), query, maxDepth = 5)
            _uiState.value = current.copy(files = results, isLoading = false)
        }
    }

    private fun listFiles(projectPath: String, expandedDirs: Set<String>): List<FileNode> {
        val root = File(projectPath)
        if (!root.exists()) return emptyList()
        return listFilesRecursive(root, expandedDirs, depth = 0)
    }

    private fun listFilesRecursive(dir: File, expandedDirs: Set<String>, depth: Int): List<FileNode> {
        if (depth > 3) return emptyList()
        val files = dir.listFiles()?.toList() ?: return emptyList()
        return files
            .filter { f ->
                !f.name.startsWith(".") &&
                f.name !in listOf("build", ".gradle", ".idea", "node_modules", ".git")
            }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { f ->
                val children = if (f.isDirectory && f.absolutePath in expandedDirs) {
                    listFilesRecursive(f, expandedDirs, depth + 1)
                } else emptyList()
                FileNode(
                    name = f.name,
                    path = f.absolutePath,
                    isDirectory = f.isDirectory,
                    lastModified = f.lastModified(),
                    size = if (f.isFile) f.length() else 0L,
                    children = children
                )
            }
    }

    private fun searchFilesRecursive(dir: File, query: String, maxDepth: Int): List<FileNode> {
        val results = mutableListOf<FileNode>()
        dir.walk().maxDepth(maxDepth).forEach { file ->
            if (!file.isFile) return@forEach
            if (file.absolutePath.contains("/build/")) return@forEach
            if (file.name.contains(query, ignoreCase = true)) {
                results.add(FileNode(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = false,
                    lastModified = file.lastModified(),
                    size = file.length()
                ))
            }
            if (results.size >= 50) return@forEach
        }
        return results
    }
}
