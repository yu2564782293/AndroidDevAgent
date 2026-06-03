package com.example.androiddevagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 增强版 Markdown 渲染器
 * 支持: 标题、粗体、斜体、代码块(带语法高亮)、行内代码、列表、表格、链接
 * 代码块带复制按钮
 */
@Composable
fun MarkdownRenderer(
    markdown: String,
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> HeadingBlock(block, baseColor)
                is MarkdownBlock.Paragraph -> ParagraphBlock(block, baseColor)
                is MarkdownBlock.CodeBlock -> CodeBlockView(block)
                is MarkdownBlock.ListBlock -> ListBlockView(block, baseColor)
                is MarkdownBlock.TableBlock -> TableBlockView(block, baseColor)
                is MarkdownBlock.HorizontalRule -> HorizontalRuleView()
            }
        }
    }
}

// ==================== 数据模型 ====================

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val spans: List<InlineSpan>) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class ListBlock(val items: List<ListItem>, val ordered: Boolean) : MarkdownBlock()
    data class TableBlock(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
}

sealed class InlineSpan {
    data class Text(val text: String) : InlineSpan()
    data class Bold(val text: String) : InlineSpan()
    data class Italic(val text: String) : InlineSpan()
    data class BoldItalic(val text: String) : InlineSpan()
    data class Code(val text: String) : InlineSpan()
    data class Link(val text: String, val url: String) : InlineSpan()
}

data class ListItem(val content: List<InlineSpan>, val level: Int = 0)

// ==================== 解析器 ====================

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // 水平线
        if (line.trim().matches(Regex("^[-*_]{3,}$"))) {
            blocks.add(MarkdownBlock.HorizontalRule)
            i++
            continue
        }

        // 标题
        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val text = headingMatch.groupValues[2]
            blocks.add(MarkdownBlock.Heading(level, text))
            i++
            continue
        }

        // 代码块
        if (line.trim().startsWith("```")) {
            val language = line.trim().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            i++ // 跳过结束的 ```
            blocks.add(MarkdownBlock.CodeBlock(language, codeLines.joinToString("\n")))
            continue
        }

        // 表格
        if (line.contains("|") && i + 1 < lines.size && lines[i + 1].trim().matches(Regex("^[|\\s\\-:]+$"))) {
            val headers = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            i += 2 // 跳过分隔行
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].contains("|")) {
                val cells = lines[i].split("|").map { it.trim() }.filter { it.isNotEmpty() }
                rows.add(cells)
                i++
            }
            blocks.add(MarkdownBlock.TableBlock(headers, rows))
            continue
        }

        // 列表
        if (line.trim().matches(Regex("^[\\-\\*+]\\s+.+$")) || line.trim().matches(Regex("^\\d+\\.\\s+.+$"))) {
            val ordered = line.trim().matches(Regex("^\\d+\\.\\s+.+$"))
            val items = mutableListOf<ListItem>()
            while (i < lines.size) {
                val currentLine = lines[i].trim()
                val unorderedMatch = Regex("^(\\s*)[\\-\\*+]\\s+(.+)$").find(currentLine)
                val orderedMatch = Regex("^(\\s*)\\d+\\.\\s+(.+)$").find(currentLine)
                if (unorderedMatch != null) {
                    val indent = unorderedMatch.groupValues[1].length / 2
                    val content = unorderedMatch.groupValues[2]
                    items.add(ListItem(parseInlineSpans(content), indent))
                    i++
                } else if (orderedMatch != null) {
                    val indent = orderedMatch.groupValues[1].length / 2
                    val content = orderedMatch.groupValues[2]
                    items.add(ListItem(parseInlineSpans(content), indent))
                    i++
                } else {
                    break
                }
            }
            blocks.add(MarkdownBlock.ListBlock(items, ordered))
            continue
        }

        // 空行
        if (line.isBlank()) {
            i++
            continue
        }

        // 普通段落
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank() &&
               !lines[i].trim().startsWith("#") &&
               !lines[i].trim().startsWith("```") &&
               !lines[i].trim().matches(Regex("^[\\-\\*+]\\s+.+$")) &&
               !lines[i].trim().matches(Regex("^\\d+\\.\\s+.+$")) &&
               !lines[i].contains("|")
        ) {
            paragraphLines.add(lines[i])
            i++
        }
        if (paragraphLines.isNotEmpty()) {
            val text = paragraphLines.joinToString(" ")
            blocks.add(MarkdownBlock.Paragraph(parseInlineSpans(text)))
        }
    }

    return blocks
}

private fun parseInlineSpans(text: String): List<InlineSpan> {
    val spans = mutableListOf<InlineSpan>()
    // 匹配粗斜体、粗体、斜体、行内代码、链接
    val regex = Regex("""\*\*\*(.+?)\*\*\*|\*\*(.+?)\*\*|\*(.+?)\*|`([^`]+)`|\[([^\]]+)\]\(([^)]+)\)""")
    var lastIndex = 0

    for (match in regex.findAll(text)) {
        if (match.range.first > lastIndex) {
            spans.add(InlineSpan.Text(text.substring(lastIndex, match.range.first)))
        }
        when {
            match.groupValues[1].isNotEmpty() -> spans.add(InlineSpan.BoldItalic(match.groupValues[1]))
            match.groupValues[2].isNotEmpty() -> spans.add(InlineSpan.Bold(match.groupValues[2]))
            match.groupValues[3].isNotEmpty() -> spans.add(InlineSpan.Italic(match.groupValues[3]))
            match.groupValues[4].isNotEmpty() -> spans.add(InlineSpan.Code(match.groupValues[4]))
            match.groupValues[5].isNotEmpty() -> spans.add(InlineSpan.Link(match.groupValues[5], match.groupValues[6]))
        }
        lastIndex = match.range.last + 1
    }

    if (lastIndex < text.length) {
        spans.add(InlineSpan.Text(text.substring(lastIndex)))
    }

    return spans
}

// ==================== 渲染组件 ====================

@Composable
private fun HeadingBlock(block: MarkdownBlock.Heading, baseColor: Color) {
    val fontSize = when (block.level) {
        1 -> 20.sp
        2 -> 18.sp
        3 -> 16.sp
        4 -> 15.sp
        5 -> 14.sp
        else -> 13.sp
    }
    Text(
        text = block.text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = baseColor
        )
    )
}

@Composable
private fun ParagraphBlock(block: MarkdownBlock.Paragraph, baseColor: Color) {
    val annotated = buildAnnotatedStringFromSpans(block.spans, baseColor)
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall.copy(color = baseColor)
    )
}

@Composable
private fun ListBlockView(block: MarkdownBlock.ListBlock, baseColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        block.items.forEachIndexed { index, item ->
            val bullet = if (block.ordered) "${index + 1}." else "•"
            val indent = 16.dp * item.level
            Row(modifier = Modifier.padding(start = indent)) {
                Text(
                    text = "$bullet ",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = baseColor,
                        fontWeight = FontWeight.Bold
                    )
                )
                val annotated = buildAnnotatedStringFromSpans(item.content, baseColor)
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodySmall.copy(color = baseColor)
                )
            }
        }
    }
}

@Composable
private fun CodeBlockView(block: MarkdownBlock.CodeBlock) {
    val clipboardManager = LocalClipboardManager.current
    var showCopied by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E1E),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 代码块头部 - 语言标签和复制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = block.language.ifEmpty { "代码" },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF9CDCFE)
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                ) {
                    if (showCopied) {
                        Text(
                            "已复制",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF4EC9B0)
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "复制代码",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(block.code))
                                showCopied = true
                            },
                        tint = Color(0xFF9CDCFE)
                    )
                }
            }

            // 代码内容
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                val highlighted = highlightSyntax(block.code, block.language)
                Text(
                    text = highlighted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = Color(0xFFD4D4D4)
                )
            }
        }
    }
}

@Composable
private fun TableBlockView(block: MarkdownBlock.TableBlock, baseColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        // 表头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            block.headers.forEach { header ->
                Text(
                    text = header,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = baseColor
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // 表格行
        block.rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.labelSmall.copy(color = baseColor),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalRuleView() {
    Divider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

// ==================== 辅助函数 ====================

private fun buildAnnotatedStringFromSpans(spans: List<InlineSpan>, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        for (span in spans) {
            when (span) {
                is InlineSpan.Text -> withStyle(SpanStyle(color = baseColor)) { append(span.text) }
                is InlineSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) { append(span.text) }
                is InlineSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) { append(span.text) }
                is InlineSpan.BoldItalic -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = baseColor)) { append(span.text) }
                is InlineSpan.Code -> {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFCE9178),
                        background = Color(0xFF2D2D2D).copy(alpha = 0.7f)
                    )) { append(" ${span.text} ") }
                }
                is InlineSpan.Link -> {
                    withStyle(SpanStyle(
                        color = Color(0xFF569CD6),
                        fontWeight = FontWeight.Medium
                    )) { append(span.text) }
                }
            }
        }
    }
}

/**
 * 简单的语法高亮
 * 支持 Kotlin/Java/Python/JavaScript 等常见语言的关键字高亮
 */
private fun highlightSyntax(code: String, language: String): AnnotatedString {
    return buildAnnotatedString {
        val keywords = when (language.lowercase()) {
            "kotlin", "kt" -> listOf(
                "fun", "val", "var", "class", "object", "interface", "package", "import",
                "if", "else", "when", "for", "while", "do", "return", "break", "continue",
                "try", "catch", "finally", "throw", "null", "true", "false", "this", "super",
                "override", "private", "protected", "public", "internal", "abstract", "open",
                "data", "sealed", "enum", "annotation", "companion", "init", "constructor",
                "suspend", "inline", "reified", "typealias", "by", "lazy", "lateinit",
                "composable", "remember", "mutablestateof", "launcedeffect"
            )
            "java" -> listOf(
                "public", "private", "protected", "class", "interface", "extends", "implements",
                "static", "final", "void", "int", "long", "double", "float", "boolean", "String",
                "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
                "return", "new", "try", "catch", "finally", "throw", "throws", "null", "true",
                "false", "this", "super", "import", "package", "abstract", "override"
            )
            "python", "py" -> listOf(
                "def", "class", "if", "elif", "else", "for", "while", "return", "import",
                "from", "as", "try", "except", "finally", "raise", "with", "yield", "lambda",
                "pass", "break", "continue", "and", "or", "not", "in", "is", "None", "True",
                "False", "self", "async", "await", "global", "nonlocal"
            )
            "javascript", "js", "typescript", "ts" -> listOf(
                "function", "const", "let", "var", "class", "if", "else", "for", "while",
                "return", "import", "export", "from", "try", "catch", "finally", "throw",
                "new", "this", "super", "async", "await", "yield", "typeof", "instanceof",
                "null", "undefined", "true", "false", "switch", "case", "break", "default"
            )
            else -> listOf(
                "function", "class", "if", "else", "for", "while", "return", "import",
                "try", "catch", "null", "true", "false", "const", "var", "let", "def"
            )
        }

        val commentColor = Color(0xFF6A9955)
        val keywordColor = Color(0xFF569CD6)
        val stringColor = Color(0xFFCE9178)
        val numberColor = Color(0xFFB5CEA8)
        val functionColor = Color(0xFFDCDCAA)
        val defaultColor = Color(0xFFD4D4D4)

        val lines = code.lines()
        lines.forEachIndexed { lineIndex, line ->
            if (lineIndex > 0) append("\n")

            var pos = 0
            while (pos < line.length) {
                // 行注释
                if (line.substring(pos).startsWith("//") || line.substring(pos).startsWith("#")) {
                    withStyle(SpanStyle(color = commentColor)) {
                        append(line.substring(pos))
                    }
                    pos = line.length
                    continue
                }

                // 字符串
                if (line[pos] == '"' || line[pos] == '\'') {
                    val quote = line[pos]
                    val end = findClosingQuote(line, pos + 1, quote)
                    withStyle(SpanStyle(color = stringColor)) {
                        append(line.substring(pos, end + 1))
                    }
                    pos = end + 1
                    continue
                }

                // 数字
                if (line[pos].isDigit()) {
                    var end = pos
                    while (end < line.length && (line[end].isDigit() || line[end] == '.')) end++
                    withStyle(SpanStyle(color = numberColor)) {
                        append(line.substring(pos, end))
                    }
                    pos = end
                    continue
                }

                // 标识符/关键字
                if (line[pos].isLetter() || line[pos] == '_') {
                    var end = pos
                    while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) end++
                    val word = line.substring(pos, end)
                    if (word in keywords) {
                        withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) {
                            append(word)
                        }
                    } else if (end < line.length && line[end] == '(') {
                        withStyle(SpanStyle(color = functionColor)) {
                            append(word)
                        }
                    } else {
                        withStyle(SpanStyle(color = defaultColor)) {
                            append(word)
                        }
                    }
                    pos = end
                    continue
                }

                // 其他字符
                withStyle(SpanStyle(color = defaultColor)) {
                    append(line[pos])
                }
                pos++
            }
        }
    }
}

private fun findClosingQuote(text: String, start: Int, quote: Char): Int {
    var i = start
    while (i < text.length) {
        if (text[i] == '\\' && i + 1 < text.length) {
            i += 2
            continue
        }
        if (text[i] == quote) return i
        i++
    }
    return text.length - 1
}
