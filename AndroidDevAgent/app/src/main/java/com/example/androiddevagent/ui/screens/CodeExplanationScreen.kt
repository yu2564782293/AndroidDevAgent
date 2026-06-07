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
import com.example.androiddevagent.models.ProgrammingLanguage
import com.example.androiddevagent.ui.components.ErrorCard
import com.example.androiddevagent.ui.components.LoadingIndicator
<<<<<<< HEAD
import com.example.androiddevagent.ui.theme.DevAgentTheme
=======
>>>>>>> dev-commercial-v2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeExplanationScreen(
    modifier: Modifier = Modifier,
    viewModel: CodeExplanationViewModel = hiltViewModel()
) {
    var codeInput by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(ProgrammingLanguage.KOTLIN) }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.screen_code_explanation_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LanguageDropdown(
            selectedLanguage = selectedLanguage,
            onLanguageSelected = { selectedLanguage = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = codeInput,
            onValueChange = { codeInput = it },
            label = { Text(stringResource(R.string.label_code_to_explain)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            maxLines = 12,
            textStyle = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.explainCode(codeInput, selectedLanguage)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = codeInput.isNotBlank() && !uiState.isLoading
        ) {
            Text(stringResource(R.string.action_explain_code))
        }

        uiState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            ErrorCard(
                message = error,
                onRetry = {
                    viewModel.explainCode(codeInput, selectedLanguage)
                },
                retryEnabled = codeInput.isNotBlank() && !uiState.isLoading
            )
        }

        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            LoadingIndicator(
                statusMessage = uiState.loadingMessage,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (uiState.explanation.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.title_explanation_result),
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
                    text = uiState.explanation,
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
                            context.getString(R.string.clipboard_code_explanation),
                            uiState.explanation
                        )
                    },
                    enabled = uiState.explanation.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_copy_explanation))
                }

                OutlinedButton(
                    onClick = {
                        codeInput = ""
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
private fun LanguageDropdown(
    selectedLanguage: ProgrammingLanguage,
    onLanguageSelected: (ProgrammingLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val languages = listOf(
        ProgrammingLanguage.KOTLIN,
        ProgrammingLanguage.JAVA,
        ProgrammingLanguage.XML,
        ProgrammingLanguage.GRADLE,
        ProgrammingLanguage.PYTHON,
        ProgrammingLanguage.JAVASCRIPT
    )
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLanguage.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.label_code_language)) },
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
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
                    onClick = {
                        expanded = false
                        onLanguageSelected(language)
                    }
                )
            }
        }
    }
}
