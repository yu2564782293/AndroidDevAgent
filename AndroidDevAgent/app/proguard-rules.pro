# Retrofit / Gson
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.internal.**

# Compose UI entry points and previews
-keep class com.example.androiddevagent.ui.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-dontwarn androidx.compose.**

# LLM request / response models and persisted entities
-keep class com.example.androiddevagent.agent.LLMMessage { *; }
-keep class com.example.androiddevagent.agent.ModelInfo { *; }
-keep class com.example.androiddevagent.agent.ModelParameters { *; }
-keep class com.example.androiddevagent.models.** { *; }
-keep class com.example.androiddevagent.settings.LLMConfig { *; }
-keep class com.example.androiddevagent.data.entity.** { *; }

# Application and Hilt entry points
-keep class com.example.androiddevagent.AndroidDevAgentApplication { *; }
-keep class com.example.androiddevagent.ui.MainActivity { *; }
