You are working on AndroidDevAgent. The current branch `clean-main` has the user's LATEST code with features: voice wake, floating window, smart memory, skill system, etc.

Your job: ADD new features without breaking anything existing. Read before write.

## Files to CREATE (new, don't exist yet):
1. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/settings/LLMConfig.kt` - LLM config data model with 6 providers (OpenAI/DeepSeek/Qwen/Kimi/ChatGLM/Custom), each with default baseUrl and model
2. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/settings/KeyStoreHelper.kt` - Android KeyStore AES/GCM encryption for API keys
3. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/settings/SettingsRepository.kt` - DataStore persistence for LLM config
4. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/agent/LLMClient.kt` - OkHttp SSE streaming client for OpenAI-compatible /v1/chat/completions API, with retry and error handling
5. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/ui/screens/SettingsScreen.kt` - Settings UI: provider dropdown, API key field, base URL, model name, temperature slider, test connection, save
6. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/ui/screens/SettingsViewModel.kt` - HiltViewModel for settings
7. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/ui/screens/CodeExplanationScreen.kt` - Code explanation feature screen
8. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/ui/screens/CodeExplanationViewModel.kt`
9. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/ui/screens/DebugScreen.kt` - Debug assistant screen
10. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/ui/screens/DebugViewModel.kt`
11. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/ui/screens/ArchitectureScreen.kt` - Architecture design screen
12. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/ui/screens/ArchitectureViewModel.kt`
13. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/data/entity/Conversation.kt` - Room entity for conversation history
14. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/data/dao/ConversationDao.kt` - DAO for conversations

## Files to MODIFY (carefully, read first):
1. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/di/AppModule.kt` - ADD providers for DataStore, SettingsRepository, LLMClient, OkHttpClient. Do NOT remove existing providers.
2. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/data/AppDatabase.kt` - ADD Conversation entity and ConversationDao. Bump version.
3. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/ui/navigation/Screen.kt` - ADD Settings, CodeExplanation, Debug, Architecture, History screen routes
4. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/ui/navigation/AppNavHost.kt` - ADD navigation destinations for new screens
5. `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/ui/components/SidebarNavigation.kt` - ADD sidebar items for new screens
6. `AndroidDevAgent/app/build.gradle` - ADD DataStore dependency if missing
7. `AndroidDevAgent/app/src/main/AndroidManifest.xml` - Ensure android:name=".AndroidDevAgentApplication" on application tag if not present

## RULES:
- Write COMPLETE Kotlin code, no stubs
- Read each existing file before modifying
- Use existing package names and patterns from the codebase
- Use Hilt for DI, Coroutines+Flow for async, Compose Material3 for UI
- Chinese UI labels
- After all changes: git add -A && git commit -m "feat: add LLM engine, multi-provider settings, and feature screens to latest branch"
- Do NOT push
