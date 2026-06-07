package com.example.androiddevagent.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class CodeEditorUiState(
    val filePath: String = "",
    val fileName: String = "",
    val content: String = "",
    val isEditing: Boolean = false,
    val isModified: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class CodeEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeEditorUiState())
    val uiState: StateFlow<CodeEditorUiState> = _uiState.asStateFlow()

    private var originalContent: String = ""

    fun loadFile(filePath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val file = File(filePath)
                val content = if (file.exists()) file.readText() else ""
                val fileName = file.name
                originalContent = content
                _uiState.value = CodeEditorUiState(
                    filePath = filePath,
                    fileName = fileName,
                    content = content,
                    isEditing = false,
                    isModified = false,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun onContentChange(newContent: String) {
        _uiState.value = _uiState.value.copy(
            content = newContent,
            isModified = newContent != originalContent
        )
    }

    fun saveFile() {
        val state = _uiState.value
        if (state.filePath.isEmpty() || !state.isModified) return
        viewModelScope.launch {
            try {
                val file = File(state.filePath)
                file.writeText(state.content)
                originalContent = state.content
                _uiState.value = state.copy(isModified = false)
            } catch (_: Exception) {
            }
        }
    }

    fun toggleEditing() {
        val current = _uiState.value
        _uiState.value = current.copy(isEditing = !current.isEditing)
    }

    fun reloadFile() {
        val path = _uiState.value.filePath
        if (path.isNotEmpty()) {
            loadFile(path)
        }
    }
}
