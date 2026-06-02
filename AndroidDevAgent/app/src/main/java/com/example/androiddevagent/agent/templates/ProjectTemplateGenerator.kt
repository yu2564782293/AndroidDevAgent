package com.example.androiddevagent.agent.templates

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ProjectTemplate(
    val id: String,
    val name: String,
    val description: String,
    val minSdk: Int = 24,
    val targetSdk: Int = 34,
    val composeEnabled: Boolean = false
)

@Singleton
class ProjectTemplateGenerator @Inject constructor() {

    val templates = listOf(
        ProjectTemplate("empty_activity", "空 Activity", "最基础的 Android 项目，仅包含一个空 Activity"),
        ProjectTemplate("compose_activity", "Compose Activity", "使用 Jetpack Compose 的现代 Android 项目"),
        ProjectTemplate("compose_hilt", "Compose + Hilt", "包含 Hilt 依赖注入的 Compose 项目（推荐）"),
        ProjectTemplate("library_module", "库模块", "Android Library 模块项目")
    )

    fun generate(templateId: String, projectDir: String, packageName: String, appName: String): Result<String> {
        val dir = File(projectDir)
        if (dir.exists() && dir.listFiles()?.isNotEmpty() == true) {
            return Result.failure(IllegalArgumentException("目录不为空: $projectDir"))
        }

        val packagePath = packageName.replace('.', '/')
        val kotlinDir = File(dir, "app/src/main/java/$packagePath")
        val resLayoutDir = File(dir, "app/src/main/res/layout")
        val resValuesDir = File(dir, "app/src/main/res/values")
        val resDrawableDir = File(dir, "app/src/main/res/drawable")
        val resMipmapDir = File(dir, "app/src/main/res/mipmap-hdpi")

        kotlinDir.mkdirs()
        resLayoutDir.mkdirs()
        resValuesDir.mkdirs()
        resDrawableDir.mkdirs()
        resMipmapDir.mkdirs()

        try {
            generateGradleFiles(dir, packageName, appName, templateId)
            generateManifest(dir, packageName, templateId)
            generateSourceFiles(kotlinDir, packageName, appName, templateId)
            generateResourceFiles(resValuesDir, resLayoutDir, appName, templateId)
            generateGitIgnore(dir)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success("项目已创建: $projectDir")
    }

    private fun generateGradleFiles(dir: File, packageName: String, appName: String, templateId: String) {
        val useCompose = templateId in listOf("compose_activity", "compose_hilt")
        val useHilt = templateId == "compose_hilt"
        val isLibrary = templateId == "library_module"

        File(dir, "settings.gradle").writeText("""
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "$appName"
include ':app'
""".trimIndent())

        File(dir, "build.gradle").writeText("""
plugins {
    id 'com.android.application' version '8.1.0' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.22' apply false${if (useHilt) "\n    id 'com.google.dagger.hilt.android' version '2.48' apply false" else ""}
}
""".trimIndent())

        val composeDeps = if (useCompose) """
    implementation platform('androidx.compose:compose-bom:2023.10.01')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.activity:activity-compose:1.8.0'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2'
    debugImplementation 'androidx.compose.ui:ui-tooling'
""" else """
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.10.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
"""

        val hiltDeps = if (useHilt) """
    implementation 'com.google.dagger:hilt-android:2.48'
    ksp 'com.google.dagger:hilt-compiler:2.48'
    implementation 'androidx.hilt:hilt-navigation-compose:1.1.0'
""" else ""

        val composeBlock = if (useCompose) """
    buildFeatures {
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion '1.5.8'
    }
""" else ""

        val hiltPlugin = if (useHilt) "    id 'com.google.dagger.hilt.android'\n" else ""
        val kspPlugin = if (useHilt) "    id 'com.google.devtools.ksp'\n" else ""

        File(dir, "app/build.gradle").writeText("""
plugins {
    id 'com.android.application'${if (isLibrary) "" else ""}
    id 'org.jetbrains.kotlin.android'
$hiltPlugin$kspPlugin
}

android {
    namespace '$packageName'
    compileSdk 34

    defaultConfig {
        applicationId "$packageName"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
$composeBlock
}

dependencies {
$composeDeps$hiltDeps
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
}
""".trimIndent())

        File(dir, "gradle.properties").writeText("""
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""".trimIndent())
    }

    private fun generateManifest(dir: File, packageName: String, templateId: String) {
        File(dir, "app/src/main/AndroidManifest.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""".trimIndent())
    }

    private fun generateSourceFiles(kotlinDir: File, packageName: String, appName: String, templateId: String) {
        val useCompose = templateId in listOf("compose_activity", "compose_hilt")
        val useHilt = templateId == "compose_hilt"

        val hiltAnnotation = if (useHilt) "@HiltAndroidApp\n" else ""
        val hiltImport = if (useHilt) "import dagger.hilt.android.HiltAndroidApp\n" else ""

        File(kotlinDir, "MyApplication.kt").writeText("""
package $packageName

$hiltImport
${hiltAnnotation}class MyApplication : android.app.Application()
""".trimIndent())

        if (useCompose) {
            val hiltActivityAnnotation = if (useHilt) "@AndroidEntryPoint\n" else ""
            val hiltActivityImport = if (useHilt) "import dagger.hilt.android.AndroidEntryPoint\n" else ""

            File(kotlinDir, "MainActivity.kt").writeText("""
package $packageName

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
$hiltActivityImport
${hiltActivityAnnotation}class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("$appName")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(
        text = "你好, ${'$'}name!",
        modifier = Modifier.padding(16.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MaterialTheme {
        Greeting("Android")
    }
}
""".trimIndent())
        } else {
            File(kotlinDir, "MainActivity.kt").writeText("""
package $packageName

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
""".trimIndent())
        }
    }

    private fun generateResourceFiles(valuesDir: File, layoutDir: File, appName: String, templateId: String) {
        File(valuesDir, "strings.xml").writeText("""
<resources>
    <string name="app_name">$appName</string>
</resources>
""".trimIndent())

        File(valuesDir, "colors.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>
""".trimIndent())

        File(valuesDir, "themes.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.MyApp" parent="Theme.Material3.DayNight.NoActionBar" />
</resources>
""".trimIndent())

        if (templateId == "empty_activity") {
            File(layoutDir, "activity_main.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello World!"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
""".trimIndent())
        }
    }

    private fun generateGitIgnore(dir: File) {
        File(dir, ".gitignore").writeText("""
*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
/app/build
""".trimIndent())
    }
}
