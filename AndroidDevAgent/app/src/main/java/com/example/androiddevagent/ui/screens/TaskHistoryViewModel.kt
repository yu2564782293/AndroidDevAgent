package com.example.androiddevagent.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.data.TaskRecordDao
import com.example.androiddevagent.data.TaskRecordEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskHistoryUiState(
    val tasks: List<TaskRecordEntity> = emptyList()
)

@HiltViewModel
class TaskHistoryViewModel @Inject constructor(
    private val taskRecordDao: TaskRecordDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskHistoryUiState())
    val uiState: StateFlow<TaskHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            taskRecordDao.getAll().collect { tasks ->
                _uiState.value = TaskHistoryUiState(tasks = tasks)
            }
        }
    }

    fun deleteTask(task: TaskRecordEntity) {
        viewModelScope.launch {
            taskRecordDao.delete(task)
        }
    }
}
