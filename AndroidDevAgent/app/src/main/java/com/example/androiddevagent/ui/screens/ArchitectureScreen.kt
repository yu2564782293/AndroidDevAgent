package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.R
import com.example.androiddevagent.ui.components.ErrorCard
import com.example.androiddevagent.ui.components.LoadingIndicator
<<<<<<< HEAD
import com.example.androiddevagent.ui.theme.DevAgentTheme
=======
>>>>>>> dev-commercial-v2

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
            text = stringResource(R.string.screen_architecture_description),
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
            label = { Text(stringResource(R.string.label_architecture_input)) },
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
            Text(stringResource(R.string.action_design_architecture))
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

        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            LoadingIndicator(
                statusMessage = uiState.loadingMessage,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (uiState.proposal.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.title_architecture_proposal),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
<<<<<<< HEAD
                    containerColor = DevAgentTheme.colors.aiResponseContainer
=======
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
>>>>>>> dev-commercial-v2
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
                        copyTextToClipboard(
                            context,
                            context.getString(R.string.clipboard_architecture_proposal),
                            uiState.proposal
                        )
                    },
                    enabled = uiState.proposal.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_copy_proposal))
                }

                OutlinedButton(
                    onClick = {
                        requirementsInput = ""
                        viewModel.clearResult()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_clear))
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
            label = { Text(stringResource(R.string.label_project_type)) },
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
