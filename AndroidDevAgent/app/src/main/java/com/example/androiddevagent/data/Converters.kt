package com.example.androiddevagent.data

import androidx.room.TypeConverter
import com.example.androiddevagent.models.ProgrammingLanguage

class Converters {

    @TypeConverter
    fun fromProgrammingLanguage(language: ProgrammingLanguage): String {
        return language.name
    }

    @TypeConverter
    fun toProgrammingLanguage(value: String): ProgrammingLanguage {
        return try {
            ProgrammingLanguage.valueOf(value)
        } catch (e: IllegalArgumentException) {
            ProgrammingLanguage.KOTLIN
        }
    }
}
