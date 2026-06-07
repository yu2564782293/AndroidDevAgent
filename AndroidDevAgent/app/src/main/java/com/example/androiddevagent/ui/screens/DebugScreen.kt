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

@Composable
fun DebugScreen(
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = hiltViewModel()
) {
    var errorInput by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.screen_debug_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = errorInput,
            onValueChange = { errorInput = it },
            label = { Text(stringResource(R.string.label_debug_input)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            maxLines = 14,
            textStyle = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.analyzeError(errorInput)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = errorInput.isNotBlank() && !uiState.isLoading
        ) {
            Text(stringResource(R.string.action_analyze_error))
        }

        uiState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            ErrorCard(
                message = error,
                onRetry = {
                    viewModel.analyzeError(errorInput)
                },
                retryEnabled = errorInput.isNotBlank() && !uiState.isLoading
            )
        }

        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            LoadingIndicator(
                statusMessage = uiState.loadingMessage,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (uiState.analysis.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.title_analysis_result),
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
                    text = uiState.analysis,
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
                            context.getString(R.string.clipboard_debug_analysis),
                            uiState.analysis
                        )
                    },
                    enabled = uiState.analysis.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_copy_result))
                }

                OutlinedButton(
                    onClick = {
                        errorInput = ""
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
