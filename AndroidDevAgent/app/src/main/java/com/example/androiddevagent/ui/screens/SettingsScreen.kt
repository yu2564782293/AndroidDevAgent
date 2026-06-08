package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.R
import com.example.androiddevagent.settings.LLMProvider
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProviderDropdown(
            selectedProvider = uiState.selectedProvider,
            onProviderSelected = viewModel::onProviderSelected
        )

        OutlinedTextField(
            value = uiState.config.apiKey,
            onValueChange = viewModel::onApiKeyChanged,
            label = { Text(stringResource(R.string.settings_api_key_label)) },
            singleLine = true,
            visualTransformation = if (uiState.isApiKeyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = viewModel::toggleApiKeyVisibility) {
                    Icon(
                        imageVector = if (uiState.isApiKeyVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = if (uiState.isApiKeyVisible) {
                            stringResource(R.string.settings_hide_api_key)
                        } else {
                            stringResource(R.string.settings_show_api_key)
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.config.baseUrl,
            onValueChange = viewModel::onBaseUrlChanged,
            label = { Text(stringResource(R.string.settings_base_url_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.config.modelName,
            onValueChange = viewModel::onModelNameChanged,
            label = { Text(stringResource(R.string.settings_model_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        ModelSuggestions(
            provider = uiState.selectedProvider,
            onModelSelected = viewModel::onModelNameChanged
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_temperature_label),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = String.format(Locale.US, "%.1f", uiState.config.temperature),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = uiState.config.temperature.toFloat(),
                onValueChange = viewModel::onTemperatureChanged,
                valueRange = 0f..2f,
                steps = 19
            )
        }

        OutlinedTextField(
            value = uiState.maxTokensInput,
            onValueChange = viewModel::onMaxTokensChanged,
            label = { Text(stringResource(R.string.settings_max_tokens_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        uiState.successMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = !uiState.isTesting && !uiState.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_testing))
                } else {
                    Text(stringResource(R.string.btn_test_connection))
                }
            }

            Button(
                onClick = viewModel::saveConfig,
                enabled = !uiState.isTesting && !uiState.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_saving))
                } else {
                    Text(stringResource(R.string.btn_save))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    selectedProvider: LLMProvider,
    onProviderSelected: (LLMProvider) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedProvider.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_provider_label)) },
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
            LLMProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = {
                        expanded = false
                        onProviderSelected(provider)
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelSuggestions(
    provider: LLMProvider,
    onModelSelected: (String) -> Unit
) {
    if (provider.modelSuggestions.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_common_models),
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            provider.modelSuggestions.forEach { modelName ->
                AssistChip(
                    onClick = { onModelSelected(modelName) },
                    label = { Text(modelName) }
                )
            }
        }
    }
}
