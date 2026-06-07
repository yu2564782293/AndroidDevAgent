You are working on AndroidDevAgent, an Android app that helps Android developers with AI-powered code generation, code explanation, debugging, and architecture design.

CRITICAL CONTEXT: Users provide their own API keys (BYOK model). The app connects DIRECTLY to LLM APIs from the phone. No backend server. All providers use OpenAI-compatible /v1/chat/completions API.

The project root is the current directory. Source code is under AndroidDevAgent/app/src/main/java/com/example/androiddevagent/

## YOUR TASKS - Complete ALL of them:

### Task 1: LLM Config Data Model
Create file: app/src/main/java/com/example/androiddevagent/settings/LLMConfig.kt
- Package: com.example.androiddevagent.settings
- Data class LLMConfig with fields: providerName, apiKey, baseUrl, modelName, temperature, topP, maxTokens
- Enum LLMProvider with values: OPENAI, DEEPSEEQ, QWEN, KIMI, CHATGLM, CUSTOM
- Each provider has default baseUrl:
  - OpenAI: https://api.openai.com/v1
  - DeepSeek: https://api.deepseek.com/v1
  - Qwen: https://dashscope.aliyuncs.com/compatible-mode/v1
  - Kimi: https://api.moonshot.cn/v1
  - ChatGLM: https://open.bigmodel.cn/api/paas/v4
- Default model for each provider

### Task 2: API Key Encryption Helper
Create file: app/src/main/java/com/example/androiddevagent/settings/KeyStoreHelper.kt
- Use Android Keystore to encrypt/decrypt API keys
- AES/GCM encryption
- Store encrypted key in DataStore

### Task 3: Settings Repository with DataStore
Create file: app/src/main/java/com/example/androiddevagent/settings/SettingsRepository.kt
- Use DataStore<Preferences> to persist all LLM config
- Flow-based reactive config
- Save/load/update config
- Inject with Hilt

### Task 4: Real LLM HTTP Client with SSE Streaming
Create file: app/src/main/java/com/example/androiddevagent/agent/LLMClient.kt
- Use OkHttp to POST to /v1/chat/completions with stream=true
- Parse SSE response (data: lines, handle data: [DONE])
- Return Flow<String> emitting each token
- Handle errors: 401 (bad key), 429 (rate limit), timeout, network error
- Retry with exponential backoff (max 3 retries)
- Accept messages list, model name, temperature, maxTokens as params

### Task 5: Rewrite LLMProviderImpl
Modify file: app/src/main/java/com/example/androiddevagent/agent/LLMProvider.kt
- DELETE all simulated/hardcoded response code
- Implement LLMProviderImpl to use LLMClient for real API calls
- generateCompletion() collects full streamed response and returns it
- streamCompletion() returns Flow<String> from LLMClient
- isAvailable() checks if config has valid API key
- configure() loads settings from SettingsRepository
- Use Hilt @Inject constructor

### Task 6: Create Settings Screen UI
Create file: app/src/main/java/com/example/androiddevagent/ui/screens/SettingsScreen.kt
- Package: com.example.androiddevagent.ui.screens
- Provider dropdown (ExposedDropdownMenuBox)
- API Key text field (password visual transform, with visibility toggle icon)
- Base URL text field (auto-filled when provider changes)
- Model name text field with suggestions
- Temperature slider (0.0 to 2.0, step 0.1, default 0.7)
- Max Tokens number input
- Test Connection button - sends a minimal chat request to verify key works, shows success/error
- Save button - persists all settings
- Material 3 design, Chinese labels matching existing UI style

### Task 7: Create SettingsViewModel
Create file: app/src/main/java/com/example/androiddevagent/ui/screens/SettingsViewModel.kt
- HiltViewModel
- Manages SettingsUiState (loading, success, error)
- Load/save config from SettingsRepository
- Test connection logic using LLMClient
- StateFlow-based

### Task 8: Update Navigation in MainActivity
Modify file: app/src/main/java/com/example/androiddevagent/ui/MainActivity.kt
- Add Settings destination to AppDestination enum (route: settings, icon: Icons.Default.Settings, label: 设置)
- Add SettingsScreen composable to NavHost
- Add settings tab to bottom navigation

### Task 9: Update AppModule for New Dependencies
Modify file: app/src/main/java/com/example/androiddevagent/di/AppModule.kt
- Provide DataStore<Preferences> for settings
- Provide SettingsRepository
- Provide LLMClient
- Update LLMProvider provision to use new implementation with SettingsRepository

### Task 10: Fix Existing Bugs
In app/src/main/java/com/example/androiddevagent/agent/LLMProvider.kt:
- Fix AgentResponse.Loading: change from object to data class Loading(val message: String)
In app/src/main/java/com/example/androiddevagent/models/Models.kt:
- Update AgentResponse sealed class if Loading is defined there
In app/src/main/java/com/example/androiddevagent/ui/screens/CodeGenerationScreen.kt:
- Fix copy button to actually copy to clipboard (use ClipboardManager)
- Fix any Thread.sleep calls to use kotlinx.coroutines.delay

### Task 11: Update build.gradle if needed
Modify: app/build.gradle
- Add datastore dependency if missing: implementation "androidx.datastore:datastore-preferences:1.0.0"
- Add security-crypto if needed for KeyStore

### Task 12: Commit All Changes
Run:
- git add -A
- git commit -m "feat: Phase 1 - Real LLM engine with multi-provider BYOK support and settings UI"
- Do NOT push

IMPORTANT RULES:
- Write COMPLETE, compilable Kotlin code (no TODOs, no placeholders, no stubs)
- Use Kotlin Coroutines and Flow for all async work
- Use Hilt @Inject and @Singleton where appropriate
- All UI uses Jetpack Compose + Material 3
- Chinese UI labels (设置, 保存, 测试连接, etc.)
- Follow existing package structure: com.example.androiddevagent.*
- Read existing files before modifying them to understand current structure
