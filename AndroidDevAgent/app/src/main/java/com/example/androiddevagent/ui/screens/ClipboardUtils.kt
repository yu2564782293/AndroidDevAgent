package com.example.androiddevagent.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

internal fun copyTextToClipboard(
    context: Context,
    label: String,
    text: String
) {
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    clipboardManager?.setPrimaryClip(ClipData.newPlainText(label, text))
}
