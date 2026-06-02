package com.example.androiddevagent.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.skills.SkillManager
import com.example.androiddevagent.agent.skills.SkillSearchResult
import com.example.androiddevagent.data.SkillEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SkillUiState(
    val installedSkills: List<SkillEntity> = emptyList(),
    val recommendedSkills: List<SkillSearchResult> = emptyList(),
    val searchResults: List<SkillSearchResult> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isInstalling: Boolean = false,
    val installingSkillId: String = "",
    val error: String = "",
    val success: String = ""
)

@HiltViewModel
class SkillViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val skillManager: SkillManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillUiState())
    val uiState: StateFlow<SkillUiState> = _uiState.asStateFlow()

    init {
        loadSkills()
    }

    fun loadSkills() {
        viewModelScope.launch {
            val installed = skillManager.getInstalledSkills()
            val recommended = skillManager.getRecommendedSkills()
            _uiState.value = _uiState.value.copy(
                installedSkills = installed,
                recommendedSkills = recommended
            )
        }
    }

    fun searchSkills(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), searchQuery = query, isSearching = false)
            return
        }
        _uiState.value = _uiState.value.copy(searchQuery = query, isSearching = true, error = "")
        viewModelScope.launch {
            try {
                val results = skillManager.searchSkills(query)
                _uiState.value = _uiState.value.copy(searchResults = results, isSearching = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSearching = false, error = "搜索失败: ${e.message}")
            }
        }
    }

    fun installSkill(source: String, repo: String, branch: String = "main") {
        _uiState.value = _uiState.value.copy(isInstalling = true, installingSkillId = repo, error = "", success = "")
        viewModelScope.launch {
            skillManager.installSkill(source, repo, branch).fold(
                onSuccess = { skill ->
                    _uiState.value = _uiState.value.copy(
                        isInstalling = false,
                        installingSkillId = "",
                        success = "${skill.name} v${skill.version} 安装成功！"
                    )
                    loadSkills()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isInstalling = false,
                        installingSkillId = "",
                        error = "安装失败: ${it.message}"
                    )
                }
            )
        }
    }

    fun uninstallSkill(skillId: String) {
        viewModelScope.launch {
            skillManager.uninstallSkill(skillId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(success = "技能已卸载")
                    loadSkills()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(error = "卸载失败: ${it.message}")
                }
            )
        }
    }

    fun toggleSkill(skillId: String, enabled: Boolean) {
        viewModelScope.launch {
            skillManager.toggleSkill(skillId, enabled)
            loadSkills()
        }
    }

    fun updateSkill(skillId: String) {
        _uiState.value = _uiState.value.copy(isInstalling = true, installingSkillId = skillId, error = "", success = "")
        viewModelScope.launch {
            skillManager.updateSkill(skillId).fold(
                onSuccess = { skill ->
                    _uiState.value = _uiState.value.copy(
                        isInstalling = false,
                        installingSkillId = "",
                        success = "${skill.name} 已更新到 v${skill.version}"
                    )
                    loadSkills()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isInstalling = false,
                        installingSkillId = "",
                        error = "更新失败: ${it.message}"
                    )
                }
            )
        }
    }

    fun rollbackSkill(skillId: String) {
        viewModelScope.launch {
            skillManager.rollbackSkill(skillId).fold(
                onSuccess = { skill ->
                    _uiState.value = _uiState.value.copy(success = "${skill.name} 已回滚到 v${skill.version}")
                    loadSkills()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(error = "回滚失败: ${it.message}")
                }
            )
        }
    }

    fun createSkill(
        type: String,
        id: String,
        name: String,
        description: String,
        toolName: String,
        toolDescription: String,
        knowledge: String
    ) {
        viewModelScope.launch {
            skillManager.createSkillFromTemplate(type, id, name, description, toolName, toolDescription, knowledge, "derek-user").fold(
                onSuccess = { skill ->
                    _uiState.value = _uiState.value.copy(success = "${skill.name} 创建成功！")
                    loadSkills()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(error = "创建失败: ${it.message}")
                }
            )
        }
    }

    fun publishSkill(skillId: String) {
        viewModelScope.launch {
            val result = skillManager.publishSkill(skillId)
            if (result.success) {
                _uiState.value = _uiState.value.copy(success = result.message)
            } else {
                _uiState.value = _uiState.value.copy(error = "发布失败: ${result.message}")
            }
        }
    }

    fun exportSkill(skillId: String) {
        viewModelScope.launch {
            val result = skillManager.exportSkillPackage(skillId)
            if (result.success) {
                _uiState.value = _uiState.value.copy(success = result.message)
            } else {
                _uiState.value = _uiState.value.copy(error = "导出失败: ${result.message}")
            }
        }
    }

    fun getUsageStats(skillId: String): com.example.androiddevagent.agent.skills.SkillUsageStats {
        return skillManager.getUsageStats(skillId)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = "", success = "")
    }
}
