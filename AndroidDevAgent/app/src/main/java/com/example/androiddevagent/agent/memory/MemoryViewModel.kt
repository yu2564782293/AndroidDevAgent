package com.example.androiddevagent.agent.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryUiState(
    val memories: List<SmartMemoryEntity> = emptyList(),
    val filteredMemories: List<SmartMemoryEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: MemoryCategory? = null,
    val isAdding: Boolean = false,
    val newMemoryContent: String = "",
    val newMemoryCategory: MemoryCategory = MemoryCategory.FACT,
    val newMemoryImportance: Float = 0.5f,
    val isLoading: Boolean = false
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryManager: MemoryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        loadMemories()
    }

    fun loadMemories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val memories = memoryManager.getAllSmartMemories()
            _uiState.value = _uiState.value.copy(
                memories = memories,
                filteredMemories = applyFilter(memories, _uiState.value.selectedCategory, _uiState.value.searchQuery),
                isLoading = false
            )
        }
    }

    fun searchMemories(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                filteredMemories = applyFilter(_uiState.value.memories, _uiState.value.selectedCategory, "")
            )
        } else {
            viewModelScope.launch {
                val results = memoryManager.searchSmartMemories(query)
                _uiState.value = _uiState.value.copy(
                    filteredMemories = if (_uiState.value.selectedCategory != null) {
                        results.filter { it.category == _uiState.value.selectedCategory!!.name }
                    } else {
                        results
                    }
                )
            }
        }
    }

    fun selectCategory(category: MemoryCategory?) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            filteredMemories = applyFilter(_uiState.value.memories, category, _uiState.value.searchQuery)
        )
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            memoryManager.deleteSmartMemory(id)
            loadMemories()
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(
            isAdding = true,
            newMemoryContent = "",
            newMemoryCategory = MemoryCategory.FACT,
            newMemoryImportance = 0.5f
        )
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(isAdding = false)
    }

    fun updateNewMemoryContent(content: String) {
        _uiState.value = _uiState.value.copy(newMemoryContent = content)
    }

    fun updateNewMemoryCategory(category: MemoryCategory) {
        _uiState.value = _uiState.value.copy(newMemoryCategory = category)
    }

    fun updateNewMemoryImportance(importance: Float) {
        _uiState.value = _uiState.value.copy(newMemoryImportance = importance)
    }

    fun addMemory() {
        val content = _uiState.value.newMemoryContent.trim()
        if (content.isBlank()) return

        viewModelScope.launch {
            memoryManager.addMemory(
                content = content,
                category = _uiState.value.newMemoryCategory,
                importance = _uiState.value.newMemoryImportance
            )
            _uiState.value = _uiState.value.copy(isAdding = false)
            loadMemories()
        }
    }

    private fun applyFilter(
        memories: List<SmartMemoryEntity>,
        category: MemoryCategory?,
        query: String
    ): List<SmartMemoryEntity> {
        var result = memories
        if (category != null) {
            result = result.filter { it.category == category.name }
        }
        if (query.isNotBlank()) {
            val lowerQuery = query.lowercase()
            result = result.filter {
                it.content.lowercase().contains(lowerQuery) ||
                it.tags.any { tag -> tag.lowercase().contains(lowerQuery) }
            }
        }
        return result.sortedByDescending { it.updatedAt }
    }
}
