package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.androiddevagent.ui.theme.DevAgentTheme

@Composable
internal fun StreamingMarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val segments = remember(text) { parseMarkdownCodeSegments(text) }

    Column(modifier = modifier) {
        segments.forEach { segment ->
            if (segment.isCode) {
                Text(
                    text = segment.text.trimEnd(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = DevAgentTheme.colors.onCodeBlockContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(
                            color = DevAgentTheme.colors.codeBlockContainer,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            } else {
                Text(
                    text = segment.text.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

private data class MarkdownSegment(
    val text: String,
    val isCode: Boolean
)

private fun parseMarkdownCodeSegments(text: String): List<MarkdownSegment> {
    if (text.isBlank()) return emptyList()

    val segments = mutableListOf<MarkdownSegment>()
    var isCode = false
    val buffer = StringBuilder()

    text.lines().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            if (buffer.isNotEmpty()) {
                segments += MarkdownSegment(buffer.toString(), isCode)
                buffer.clear()
            }
            isCode = !isCode
        } else {
            buffer.append(line).append('\n')
        }
    }

    if (buffer.isNotEmpty()) {
        segments += MarkdownSegment(buffer.toString(), isCode)
    }

    return segments.filter { it.text.isNotBlank() }
}
