You are continuing work on AndroidDevAgent. Phase 1 (LLM engine) and Phase 2 (4 feature screens) are complete. Now Phase 3.

The project root is the current directory. Source is under AndroidDevAgent/app/src/main/java/com/example/androiddevagent/

## PHASE 3 Tasks - Conversation History + Project Management + Polish

### Task 1: Conversation History System
Create: app/src/main/java/com/example/androiddevagent/data/entity/Conversation.kt
- Room entity: id (auto PK), screenType (String: "code_gen", "code_explain", "debug", "architecture"), userMessage (String), aiResponse (String), language (String?), createdAt (Long), isFavorite (Boolean)

Create: app/src/main/java/com/example/androiddevagent/data/dao/ConversationDao.kt
- DAO: getAll (Flow), getByScreenType (Flow), insert, delete, deleteByScreenType, getFavorites, search

Modify: app/src/main/java/com/example/androiddevagent/data/ProjectDatabase.kt
- Add Conversation entity to the database, add ConversationDao
- Bump version to 2, use destructive migration (OK for dev)

### Task 2: History Screen
Create: app/src/main/java/com/example/androiddevagent/ui/screens/HistoryScreen.kt
Create: app/src/main/java/com/example/androiddevagent/ui/screens/HistoryViewModel.kt
- Tab layout: 全部 / 代码生成 / 代码解释 / 调试 / 架构
- List of conversation cards showing: screen type icon, preview text, timestamp
- Tap to expand and see full conversation
- Swipe to delete
- Search bar at top
- Empty state message

### Task 3: Update Navigation - Add History Tab
Modify: app/src/main/java/com/example/androiddevagent/ui/MainActivity.kt
- Add History destination (route: history, icon: Icons.Default.History, label: 历史)
- Add to bottom nav (5 tabs now: 首页, 代码生成, ..., 历史, 设置)
- Wire up HistoryScreen in NavHost

### Task 4: Save Conversations from All Screens
Modify each ViewModel to save conversations to Room after AI response completes:
- app/src/main/java/com/example/androiddevagent/ui/screens/CodeGenerationViewModel.kt
- app/src/main/java/com/example/androiddevagent/ui/screens/CodeExplanationViewModel.kt
- app/src/main/java/com/example/androiddevagent/ui/screens/DebugViewModel.kt
- app/src/main/java/com/example/androiddevagent/ui/screens/ArchitectureViewModel.kt
- Inject ConversationDao, save after successful response

### Task 5: Polish HomeScreen
Modify: app/src/main/java/com/example/androiddevagent/ui/screens/HomeScreen.kt
- Add a "最近对话" section showing last 3 conversations
- Add app version info at bottom
- Improve feature cards with better descriptions

### Task 6: Error Handling UI
Create: app/src/main/java/com/example/androiddevagent/ui/components/ErrorCard.kt
- Reusable error display component with retry button
- Use in all feature screens for consistent error handling

### Task 7: Update AndroidManifest
Modify: app/src/main/AndroidManifest.xml
- Ensure backup rules and data extraction rules are referenced properly

### Task 8: Commit
- git add -A
- git commit -m "feat: Phase 3 - Conversation history, project management, and UI polish"
- Do NOT push

RULES:
- Write COMPLETE, compilable Kotlin code
- Use Room, Hilt, Coroutines, Flow, Compose Material 3
- Chinese UI labels
- Read existing files before modifying
