package com.example.androiddevagent.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloatingBubbleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "floating_bubble_prefs"
        private const val KEY_BUBBLE_ENABLED = "bubble_enabled"
        const val OVERLAY_PERMISSION_REQUEST_CODE = 2001
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isBubbleEnabled(): Boolean {
        return prefs.getBoolean(KEY_BUBBLE_ENABLED, false)
    }

    fun setBubbleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUBBLE_ENABLED, enabled).apply()
        if (enabled) {
            startFloatingService()
        } else {
            stopFloatingService()
        }
    }

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            } else {
                null
            }
        } else {
            null
        }
    }

    fun startFloatingService() {
        if (!hasOverlayPermission()) return
        val intent = Intent(context, FloatingChatService::class.java).apply {
            action = FloatingChatService.ACTION_SHOW_BUBBLE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopFloatingService() {
        val intent = Intent(context, FloatingChatService::class.java).apply {
            action = FloatingChatService.ACTION_HIDE_BUBBLE
        }
        context.startService(intent)
    }

    fun expandChat() {
        val intent = Intent(context, FloatingChatService::class.java).apply {
            action = FloatingChatService.ACTION_EXPAND_CHAT
        }
        context.startService(intent)
    }

    fun collapseChat() {
        val intent = Intent(context, FloatingChatService::class.java).apply {
            action = FloatingChatService.ACTION_COLLAPSE_CHAT
        }
        context.startService(intent)
    }
}
