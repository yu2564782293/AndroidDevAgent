package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.ui.components.ErrorCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchitectureScreen(
    modifier: Modifier = Modifier,
    viewModel: ArchitectureViewModel = hiltViewModel()
) {
    var requirementsInput by remember { mutableStateOf("") }
    var selectedProjectType by remember { mutableStateOf(ProjectTypeOption.App) }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "描述项目目标、功能范围和技术约束，Agent 会生成模块、数据流和技术栈建议。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        ProjectTypeDropdown(
            selectedProjectType = selectedProjectType,
            onProjectTypeSelected = { selectedProjectType = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = requirementsInput,
            onValueChange = { requirementsInput = it },
            label = { Text("输入项目描述、需求和约束...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            maxLines = 12,
            textStyle = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.designArchitecture(
                    requirements = requirementsInput,
                    projectType = selectedProjectType.displayName
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = requirementsInput.isNotBlank() && !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(uiState.loadingMessage ?: "设计中...")
            } else {
                Text("设计架构")
            }
        }

        uiState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            ErrorCard(
                message = error,
                onRetry = {
                    viewModel.designArchitecture(
                        requirements = requirementsInput,
                        projectType = selectedProjectType.displayName
                    )
                },
                retryEnabled = requirementsInput.isNotBlank() && !uiState.isLoading
            )
        }

        if (uiState.proposal.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "架构方案",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                StreamingMarkdownText(
                    text = uiState.proposal,
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        copyTextToClipboard(context, "架构方案", uiState.proposal)
                    },
                    enabled = uiState.proposal.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("复制方案")
                }

                OutlinedButton(
                    onClick = {
                        requirementsInput = ""
                        viewModel.clearResult()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectTypeDropdown(
    selectedProjectType: ProjectTypeOption,
    onProjectTypeSelected: (ProjectTypeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedProjectType.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("项目类型") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ProjectTypeOption.entries.forEach { projectType ->
                DropdownMenuItem(
                    text = { Text(projectType.displayName) },
                    onClick = {
                        expanded = false
                        onProjectTypeSelected(projectType)
                    }
                )
            }
        }
    }
}

private enum class ProjectTypeOption(val displayName: String) {
    App("App"),
    Library("Library"),
    Game("Game"),
    Plugin("Plugin")
}
