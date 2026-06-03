package com.example.androiddevagent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.androiddevagent.ui.theme.GlassBorderDark
import com.example.androiddevagent.ui.theme.GlassBorderLight
import com.example.androiddevagent.ui.theme.GlassTintDark
import com.example.androiddevagent.ui.theme.GlassTintLight
import com.example.androiddevagent.ui.theme.isDarkGlassTheme

/**
 * 液态玻璃效果卡片
 * 实现毛玻璃/玻璃拟态(Glassmorphism)效果
 *
 * @param modifier 修饰符
 * @param blurRadius 模糊半径
 * @param tintColor 着色颜色，默认根据主题自动选择
 * @param borderColor 边框颜色，默认根据主题自动选择
 * @param cornerRadius 圆角半径
 * @param content 卡片内容
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 20.dp,
    tintColor: Color = if (isDarkGlassTheme()) GlassTintDark else GlassTintLight,
    borderColor: Color = if (isDarkGlassTheme()) GlassBorderDark else GlassBorderLight,
    cornerRadius: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier
            .clip(cardShape)
            .graphicsLayer {
                shape = cardShape
                clip = true
                alpha = 0.95f
            },
        shape = cardShape,
        color = tintColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .blur(blurRadius)
                .graphicsLayer { alpha = 0.3f }
        ) {
            // 模糊背景层
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
            )
        }
        Column(
            modifier = Modifier
                .graphicsLayer { alpha = 1f }
        ) {
            content()
        }
    }
}

/**
 * 简化版液态玻璃卡片 - 适用于聊天气泡等场景
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    isUser: Boolean = false,
    content: @Composable () -> Unit
) {
    val tintColor = if (isUser) {
        if (isDarkGlassTheme()) GlassTintDark else GlassTintLight.copy(alpha = 0.9f)
    } else {
        if (isDarkGlassTheme()) GlassTintDark.copy(alpha = 0.7f) else GlassTintLight.copy(alpha = 0.8f)
    }

    val borderColor = if (isDarkGlassTheme()) GlassBorderDark else GlassBorderLight

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = tintColor,
        border = BorderStroke(0.5.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        content()
    }
}
