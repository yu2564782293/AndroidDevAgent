package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.templates.ProjectTemplate
import com.example.androiddevagent.agent.templates.ProjectTemplateGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewProjectUiState(
    val templates: List<ProjectTemplate> = emptyList(),
    val selectedTemplate: String = "compose_hilt",
    val projectName: String = "",
    val packageName: String = "com.example.myapp",
    val projectDir: String = "/sdcard/",
    val isCreating: Boolean = false,
    val result: String? = null
)

@HiltViewModel
class NewProjectViewModel @Inject constructor(
    private val templateGenerator: ProjectTemplateGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewProjectUiState(templates = templateGenerator.templates))
    val uiState: StateFlow<NewProjectUiState> = _uiState.asStateFlow()

    fun selectTemplate(id: String) {
        _uiState.value = _uiState.value.copy(selectedTemplate = id)
    }

    fun updateProjectName(name: String) {
        _uiState.value = _uiState.value.copy(
            projectName = name,
            projectDir = "/sdcard/$name"
        )
    }

    fun updatePackageName(name: String) {
        _uiState.value = _uiState.value.copy(packageName = name)
    }

    fun updateProjectDir(dir: String) {
        _uiState.value = _uiState.value.copy(projectDir = dir)
    }

    fun createProject() {
        val state = _uiState.value
        if (state.projectName.isBlank()) return
        _uiState.value = _uiState.value.copy(isCreating = true)
        viewModelScope.launch {
            val result = templateGenerator.generate(
                state.selectedTemplate,
                state.projectDir,
                state.packageName,
                state.projectName
            )
            _uiState.value = _uiState.value.copy(
                isCreating = false,
                result = result.getOrElse { it.message ?: "创建失败" }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(
    viewModel: NewProjectViewModel = hiltViewModel(),
    onProjectCreated: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("新建项目") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("选择模板", style = MaterialTheme.typography.titleMedium)

            uiState.templates.forEach { template ->
                TemplateCard(
                    template = template,
                    selected = template.id == uiState.selectedTemplate,
                    onSelect = { viewModel.selectTemplate(template.id) }
                )
            }

            Divider()

            Text("项目信息", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = uiState.projectName,
                onValueChange = { viewModel.updateProjectName(it) },
                label = { Text("项目名称") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("MyApp") },
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.packageName,
                onValueChange = { viewModel.updatePackageName(it) },
                label = { Text("包名") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("com.example.myapp") },
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.projectDir,
                onValueChange = { viewModel.updateProjectDir(it) },
                label = { Text("项目目录") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("/sdcard/MyApp") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.createProject() },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.projectName.isNotBlank() && !uiState.isCreating
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("创建中...")
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("创建项目")
                }
            }

            uiState.result?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.startsWith("项目已创建"))
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        result,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TemplateCard(
    template: ProjectTemplate,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(template.name, style = MaterialTheme.typography.bodyMedium)
                Text(template.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
