package com.example.androiddevagent.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.data.ProjectDao
import com.example.androiddevagent.data.ProjectEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectListUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val activeProject: ProjectEntity? = null
)

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val projectDao: ProjectDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectListUiState())
    val uiState: StateFlow<ProjectListUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    fun loadProjects() {
        viewModelScope.launch {
            projectDao.getAll().collect { projects ->
                val active = projects.find { it.isActive }
                _uiState.value = _uiState.value.copy(
                    projects = projects,
                    activeProject = active
                )
            }
        }
    }

    fun addProject(path: String, name: String) {
        viewModelScope.launch {
            val isActive = _uiState.value.activeProject == null
            val project = ProjectEntity(
                path = path,
                name = name,
                lastOpenedAt = System.currentTimeMillis(),
                isActive = isActive
            )
            projectDao.insert(project)
            if (isActive) {
                projectDao.setActive(path)
            }
        }
    }

    fun removeProject(project: ProjectEntity) {
        viewModelScope.launch {
            projectDao.delete(project)
        }
    }

    fun switchProject(path: String) {
        viewModelScope.launch {
            projectDao.setActive(path)
        }
    }
}
