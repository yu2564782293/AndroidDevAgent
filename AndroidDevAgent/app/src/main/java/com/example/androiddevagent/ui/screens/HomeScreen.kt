package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androiddevagent.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCodeGeneration: () -> Unit = {},
    onNavigateToCodeExplanation: () -> Unit = {},
    onNavigateToDebugging: () -> Unit = {},
    onNavigateToArchitecture: () -> Unit = {}
) {
    val features = listOf(
        FeatureItem(
            title = "代码生成",
            description = "根据需求描述生成完整的安卓代码",
            icon = "代码生成",
            onClick = onNavigateToCodeGeneration
        ),
        FeatureItem(
            title = "代码解释",
            description = "分析和解释现有代码的功能",
            icon = "代码解释",
            onClick = onNavigateToCodeExplanation
        ),
        FeatureItem(
            title = "调试助手",
            description = "帮助定位和解决开发问题",
            icon = "调试助手",
            onClick = onNavigateToDebugging
        ),
        FeatureItem(
            title = "架构设计",
            description = "提供项目架构设计建议",
            icon = "架构设计",
            onClick = onNavigateToArchitecture
        )
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题栏
        Text(
            text = "Android Dev Agent",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "您的智能安卓开发助手",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            textAlign = TextAlign.Center
        )
        
        // 功能网格
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(features) { feature ->
                FeatureCard(feature = feature)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureCard(feature: FeatureItem) {
    Card(
        onClick = feature.onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标
            Text(
                text = feature.icon,
                fontSize = 32.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // 标题
            Text(
                text = feature.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 描述
            Text(
                text = feature.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

data class FeatureItem(
    val title: String,
    val description: String,
    val icon: String,
    val onClick: () -> Unit
)