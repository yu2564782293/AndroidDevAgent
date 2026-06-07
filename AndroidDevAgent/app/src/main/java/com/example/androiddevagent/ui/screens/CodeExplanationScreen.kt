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
import com.example.androiddevagent.models.ProgrammingLanguage

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
            text = "粘贴代码并选择语言，Agent 会解释实现逻辑、设计模式和优化方向。",
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
            label = { Text("粘贴需要解释的代码...") },
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
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(uiState.loadingMessage ?: "解释中...")
            } else {
                Text("解释代码")
            }
        }

        uiState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (uiState.explanation.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "解释结果",
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
                        copyTextToClipboard(context, "代码解释", uiState.explanation)
                    },
                    enabled = uiState.explanation.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("复制解释")
                }

                OutlinedButton(
                    onClick = {
                        codeInput = ""
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
            label = { Text("代码语言") },
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
