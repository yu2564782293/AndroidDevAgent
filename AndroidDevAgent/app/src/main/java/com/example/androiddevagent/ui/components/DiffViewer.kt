package com.example.androiddevagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class DiffLineType { ADDED, REMOVED, UNCHANGED }

data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNum: Int?,
    val newLineNum: Int?
)

fun computeDiff(oldText: String, newText: String): List<DiffLine> {
    val oldLines = oldText.lines()
    val newLines = newText.lines()
    val m = oldLines.size
    val n = newLines.size

    val dp = Array(m + 1) { IntArray(n + 1) }
    for (i in 1..m) {
        for (j in 1..n) {
            if (oldLines[i - 1] == newLines[j - 1]) {
                dp[i][j] = dp[i - 1][j - 1] + 1
            } else {
                dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
    }

    val result = mutableListOf<DiffLine>()
    var i = m
    var j = n
    val stack = mutableListOf<DiffLine>()

    while (i > 0 || j > 0) {
        if (i > 0 && j > 0 && oldLines[i - 1] == newLines[j - 1]) {
            stack.add(DiffLine(DiffLineType.UNCHANGED, oldLines[i - 1], i, j))
            i--
            j--
        } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
            stack.add(DiffLine(DiffLineType.ADDED, newLines[j - 1], null, j))
            j--
        } else {
            stack.add(DiffLine(DiffLineType.REMOVED, oldLines[i - 1], i, null))
            i--
        }
    }

    for (k in stack.size - 1 downTo 0) {
        result.add(stack[k])
    }

    return result
}

@Composable
fun DiffViewer(
    oldText: String,
    newText: String,
    fileName: String = "",
    onDismiss: () -> Unit
) {
    val diffLines = computeDiff(oldText, newText)
    val addedCount = diffLines.count { it.type == DiffLineType.ADDED }
    val removedCount = diffLines.count { it.type == DiffLineType.REMOVED }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName.ifEmpty { "差异对比" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "新增 $addedCount 行，删除 $removedCount 行",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(diffLines) { line ->
                        DiffLineRow(line)
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val backgroundColor = when (line.type) {
        DiffLineType.ADDED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        DiffLineType.REMOVED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        DiffLineType.UNCHANGED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }

    val textColor = when (line.type) {
        DiffLineType.ADDED -> MaterialTheme.colorScheme.primary
        DiffLineType.REMOVED -> MaterialTheme.colorScheme.error
        DiffLineType.UNCHANGED -> MaterialTheme.colorScheme.onSurface
    }

    val prefix = when (line.type) {
        DiffLineType.ADDED -> "+"
        DiffLineType.REMOVED -> "-"
        DiffLineType.UNCHANGED -> " "
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        Text(
            text = line.oldLineNum?.toString() ?: "",
            modifier = Modifier
                .width(44.dp)
                .padding(end = 4.dp),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = line.newLineNum?.toString() ?: "",
            modifier = Modifier
                .width(44.dp)
                .padding(end = 4.dp),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = prefix,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor
        )
        Text(
            text = line.content,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            softWrap = true
        )
    }
}
