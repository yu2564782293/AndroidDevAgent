package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.agent.AndroidDevAgent
import com.example.androiddevagent.agent.AgentResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeGenerationScreen(
    viewModel: CodeGenerationViewModel = hiltViewModel()
) {
    var userInput by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("Kotlin") }
    var generatedCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val languages = listOf("Kotlin", "Java", "Python", "JavaScript", "Swift")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = "智能代码生成",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // 语言选择
        Text(
            text = "选择编程语言:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            languages.forEach { language ->
                FilterChip(
                    selected = language == selectedLanguage,
                    onClick = { selectedLanguage = language },
                    label = { Text(language) }
                )
            }
        }
        
        // 输入框
        OutlinedTextField(
            value = userInput,
            onValueChange = { userInput = it },
            label = { Text("描述你想要的代码功能...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            maxLines = 5
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 生成按钮
        Button(
            onClick = {
                if (userInput.isNotBlank()) {
                    isLoading = true
                    viewModel.generateCode(userInput, selectedLanguage)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = userInput.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("生成中...")
            } else {
                Text("生成代码")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 生成的代码显示
        if (generatedCode.isNotBlank()) {
            Text(
                text = "生成的代码:",
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
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = generatedCode,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 复制按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { /* 复制到剪贴板 */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("复制代码")
                }
                
                OutlinedButton(
                    onClick = {
                        userInput = ""
                        generatedCode = ""
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空")
                }
            }
        }
    }
}

@HiltViewModel
class CodeGenerationViewModel @Inject constructor(
    private val agent: AndroidDevAgent
) : ViewModel() {
    
    fun generateCode(description: String, language: String) {
        viewModelScope.launch {
            agent.generateCode(description, language)
                .collect { response ->
                    when (response) {
                        is AgentResponse.Success -> {
                            // 更新UI状态
                        }
                        is AgentResponse.Error -> {
                            // 处理错误
                        }
                        is AgentResponse.Loading -> {
                            // 显示加载状态
                        }
                    }
                }
        }
    }
}

data class CodeGenerationUiState(
    val generatedCode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)