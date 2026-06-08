package com.example.androiddevagent.agent.memory

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSkills @Inject constructor() {

    private val skills = mapOf(
        "gradle" to GRADLE_SKILL,
        "manifest" to MANIFEST_SKILL,
        "compose" to COMPOSE_SKILL,
        "hilt" to HILT_SKILL,
        "navigation" to NAVIGATION_SKILL,
        "lifecycle" to LIFECYCLE_SKILL,
        "coroutines" to COROUTINES_SKILL
    )

    fun getRelevantSkills(context: String): String {
        val lowerContext = context.lowercase()
        val relevant = mutableListOf<String>()

        if (lowerContext.contains("gradle") || lowerContext.contains("build") || lowerContext.contains("compile")) {
            relevant.add(skills["gradle"]!!)
        }
        if (lowerContext.contains("manifest") || lowerContext.contains("activity") || lowerContext.contains("permission")) {
            relevant.add(skills["manifest"]!!)
        }
        if (lowerContext.contains("compose") || lowerContext.contains("composable") || lowerContext.contains("@composable")) {
            relevant.add(skills["compose"]!!)
        }
        if (lowerContext.contains("hilt") || lowerContext.contains("dagger") || lowerContext.contains("@inject") || lowerContext.contains("dependency")) {
            relevant.add(skills["hilt"]!!)
        }
        if (lowerContext.contains("navigation") || lowerContext.contains("navhost") || lowerContext.contains("navigate")) {
            relevant.add(skills["navigation"]!!)
        }
        if (lowerContext.contains("lifecycle") || lowerContext.contains("viewmodel") || lowerContext.contains("livedata")) {
            relevant.add(skills["lifecycle"]!!)
        }
        if (lowerContext.contains("coroutine") || lowerContext.contains("flow") || lowerContext.contains("suspend")) {
            relevant.add(skills["coroutines"]!!)
        }

        if (relevant.isEmpty()) {
            relevant.add(skills["gradle"]!!)
            relevant.add(skills["manifest"]!!)
        }

        return relevant.joinToString("\n\n")
    }

    companion object {
        const val GRADLE_SKILL = """
## Android Gradle Knowledge
- Build APK: ./gradlew assembleDebug
- Build release: ./gradlew assembleRelease
- Clean project: ./gradlew clean
- Run tests: ./gradlew test
- Common build errors:
  - SDK version mismatch: ensure compileSdk, targetSdk, minSdk are consistent
  - Dependency conflicts: use ./gradlew dependencies to inspect
  - Compose compiler version must match Kotlin version
  - ProGuard rules may need -keep for reflection-based code
- build.gradle key sections: android {}, dependencies {}, plugins {}
- Use implementation for app-only deps, api for transitive deps
"""

        const val MANIFEST_SKILL = """
## AndroidManifest Knowledge
- Every Activity must be registered in AndroidManifest.xml
- Use <activity android:name=".MyActivity" /> for registration
- Permissions: <uses-permission android:name="android.permission.INTERNET" />
- Application class: <application android:name=".MyApp" />
- Intent filters: <intent-filter> with <action>, <category>, <data>
- Exported activities (Android 12+): android:exported="true/false"
- Meta-data: <meta-data android:name="key" android:value="value" />
"""

        const val COMPOSE_SKILL = """
## Jetpack Compose Knowledge
- @Composable functions are the basic UI unit
- State management: remember, mutableStateOf, StateFlow, collectAsState()
- Side effects: LaunchedEffect(key), SideEffect, DisposableEffect
- Navigation: NavHost, composable(route), navController.navigate(route)
- Theming: MaterialTheme, colorScheme, typography
- Layouts: Column, Row, Box, LazyColumn, LazyRow
- Modifiers: Modifier.fillMaxWidth(), padding(), click(), etc.
- Preview: @Preview annotation for Android Studio preview
- Remember: always use remember for state in composables
"""

        const val HILT_SKILL = """
## Hilt Dependency Injection Knowledge
- @HiltAndroidApp: annotate Application class
- @AndroidEntryPoint: annotate Activity/Fragment/Service/View
- @Inject constructor: mark injectable constructors
- @Module + @InstallIn: define dependency providers
- @Provides / @Binds: provide dependencies
- @Singleton: scope to application lifecycle
- @ViewModelInject (deprecated) → @HiltViewModel
- Scopes: @Singleton, @ActivityScoped, @ViewModelScoped, etc.
"""

        const val NAVIGATION_SKILL = """
## Navigation Compose Knowledge
- Define routes: sealed class or object constants
- NavHost(navController, startDestination) { composable("route") { ... } }
- Navigate: navController.navigate("route")
- Pop back stack: navController.popBackStack()
- Pass arguments: composable("route/{arg}") { backStackEntry ->
    val arg = backStackEntry.arguments?.getString("arg")
  }
- Deep links: navDeepLink { uriPattern = "myapp://route" }
"""

        const val LIFECYCLE_SKILL = """
## Android Lifecycle Knowledge
- ViewModel: survives configuration changes, use for UI state
- LiveData: observable data holder, lifecycle-aware
- StateFlow: cold flow alternative to LiveData for Kotlin
- Lifecycle states: CREATED, STARTED, RESUMED, DESTROYED
- Lifecycle-aware components: implement LifecycleObserver
- onSaveInstanceState: save transient UI state
- rememberSaveable: Compose equivalent for saving state
"""

        const val COROUTINES_SKILL = """
## Kotlin Coroutines Knowledge
- suspend fun: mark async functions
- Dispatchers: IO (network/file), Main (UI), Default (CPU)
- viewModelScope.launch: auto-cancelled when ViewModel cleared
- withContext(Dispatchers.IO): switch dispatcher
- Flow: cold stream, collect in lifecycle-aware manner
- StateFlow/SharedFlow: hot streams for state sharing
- async/await: concurrent operations with results
- coroutineScope: structured concurrency scope
"""
    }
}
