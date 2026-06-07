You are continuing work on AndroidDevAgent. Phases 1-3 are complete. Now Phase 4 - final polish and production readiness.

The project root is the current directory. Source is under AndroidDevAgent/app/src/main/java/com/example/androiddevagent/

## PHASE 4 Tasks - Production Polish + Security + Performance

### Task 1: ProGuard / R8 Rules
Create: AndroidDevAgent/app/proguard-rules.pro
- Keep rules for Retrofit, Gson, OkHttp, Room, Hilt
- Keep LLM response models
- Keep Compose classes

Modify: AndroidDevAgent/app/build.gradle
- Enable minification: minifyEnabled true for release
- Enable shrinkResources for release
- Reference proguard-rules.pro

### Task 2: Network Security
Modify: app/src/main/res/xml/network_security_config.xml
- Add certificate pinning for major LLM API domains (optional, allow user CAs too)
- Ensure HTTPS enforcement for production

### Task 3: App Icon and Branding
Modify: app/src/main/res/values/strings.xml
- App name: "AI开发助手" or "DevAgent"
- Add all string resources properly (avoid hardcoded strings in Compose)

### Task 4: Release Build Configuration
Modify: AndroidDevAgent/app/build.gradle
- Configure signing config placeholder (user fills in their keystore)
- Set versionCode and versionName properly
- Add buildTypes: debug and release with proper settings

### Task 5: Input Validation and Sanitization
Create: app/src/main/java/com/example/androiddevagent/utils/InputValidator.kt
- Validate API key format (non-empty, reasonable length)
- Validate URL format
- Validate model name (non-empty, alphanumeric + hyphens)
- Sanitize user input before sending to LLM (trim, limit length)

### Task 6: Rate Limiting (Client-Side)
Create: app/src/main/java/com/example/androiddevagent/utils/RateLimiter.kt
- Track API call count per minute/hour
- Warn user when approaching limits
- Prevent accidental spam (debounce on rapid button presses)

### Task 7: App Theme Enhancement
Modify: app/src/main/java/com/example/androiddevagent/ui/theme/Theme.kt
- Improve dark theme colors for better readability
- Add custom color tokens for AI response sections
- Ensure consistent styling across all screens

### Task 8: Loading States
Create: app/src/main/java/com/example/androiddevagent/ui/components/LoadingIndicator.kt
- Animated loading indicator with status message
- Use in all feature screens during AI processing

### Task 9: README Update
Modify: AndroidDevAgent/README.md
- Complete project documentation
- Setup instructions
- Feature list with screenshots placeholder
- API key setup guide for each provider
- Build instructions

### Task 10: Final Commit
- git add -A
- git commit -m "feat: Phase 4 - Production polish, security hardening, and release config"
- Do NOT push

RULES:
- Write COMPLETE, compilable Kotlin/XML code
- Follow Android best practices
- Chinese UI labels where appropriate
- Read existing files before modifying
