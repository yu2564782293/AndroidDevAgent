You are working on AndroidDevAgent. The user's LATEST code is on branch `trae/solo-agent-uNtcW8` (currently checked out as `dev-commercial-v2`). We previously made Phase 1-4 changes on the old `main` branch. Now we need to port those changes to this branch.

## Context
The `trae/solo-agent-uNtcW8` branch has features that main did NOT have:
- 语音唤醒 (voice wake word)
- 悬浮窗 (floating window)
- UI升级
- 智能记忆 (smart memory)
- 技能扩展系统 (skill extension system)
- Various bug fixes

Our Phase 1-4 changes added:
- Real LLM integration (LLMClient.kt with SSE streaming)
- Multi-provider API key settings (settings/LLMConfig.kt, SettingsRepository.kt, KeyStoreHelper.kt)
- Settings UI screen (SettingsScreen.kt, SettingsViewModel.kt)
- Code Explanation, Debug, Architecture screens
- Conversation history (Room DB)
- Input validation, rate limiting
- Various bug fixes

## Your Task
Port our Phase 1-4 changes onto this branch. Steps:

### 1. First, read the existing files to understand what's already here:
- Read `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/agent/LLMProvider.kt` - check if there's already an LLM integration
- Read `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/di/AppModule.kt`
- Read `AndroidDevAgent/app/src/main/java/com/example/androiddevagent/ui/MainActivity.kt`
- List all Kotlin files: `find AndroidDevAgent/app/src/main/java -name '*.kt' | sort`

### 2. Check which of our new files already exist or conflict:
- `settings/LLMConfig.kt`
- `settings/KeyStoreHelper.kt`  
- `settings/SettingsRepository.kt`
- `agent/LLMClient.kt`
- `ui/screens/SettingsScreen.kt`
- `ui/screens/SettingsViewModel.kt`
- `ui/screens/CodeExplanationScreen.kt`
- `ui/screens/DebugScreen.kt`
- `ui/screens/ArchitectureScreen.kt`
- `ui/screens/HistoryScreen.kt`
- `utils/InputValidator.kt`
- `utils/RateLimiter.kt`

### 3. Copy new files from main branch that don't exist here:
Use `git checkout main -- <file>` for files that don't exist on this branch.

### 4. For files that exist on both branches (merge carefully):
- LLMProvider.kt: Keep the existing interface but replace the simulated implementation with real LLMClient calls
- AppModule.kt: Add new providers (DataStore, SettingsRepository, LLMClient, OkHttpClient) WITHOUT removing existing providers
- MainActivity.kt: Add Settings and History to navigation WITHOUT removing existing screens
- build.gradle: Add new dependencies (DataStore, etc.) WITHOUT breaking existing ones

### 5. Update AndroidManifest.xml if needed

### 6. Ensure gradle.properties does NOT have the local aapt2 override

### 7. Commit:
- git add -A
- git commit -m "feat: port Phase 1-4 LLM engine and settings to latest branch with voice/floating/memory features"
- Do NOT push

IMPORTANT:
- Do NOT delete or overwrite existing features (voice wake, floating window, smart memory, skill system)
- Only ADD new functionality
- Read each file before modifying
- If there's a conflict, keep the existing feature and integrate our changes alongside it
