package com.example.androiddevagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.R
import com.example.androiddevagent.BuildConfig
import com.example.androiddevagent.data.entity.Conversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToCodeGeneration: () -> Unit = {},
    onNavigateToCodeExplanation: () -> Unit = {},
    onNavigateToDebugging: () -> Unit = {},
    onNavigateToArchitecture: () -> Unit = {}
) {
    val features = listOf(
        FeatureItem(
            title = stringResource(R.string.feature_code_generation_title),
            description = stringResource(R.string.feature_code_generation_description),
            icon = Icons.Filled.Code,
            onClick = onNavigateToCodeGeneration
        ),
        FeatureItem(
            title = stringResource(R.string.feature_code_explanation_title),
            description = stringResource(R.string.feature_code_explanation_description),
            icon = Icons.Filled.MenuBook,
            onClick = onNavigateToCodeExplanation
        ),
        FeatureItem(
            title = stringResource(R.string.feature_debug_title),
            description = stringResource(R.string.feature_debug_description),
            icon = Icons.Filled.BugReport,
            onClick = onNavigateToDebugging
        ),
        FeatureItem(
            title = stringResource(R.string.feature_architecture_title),
            description = stringResource(R.string.feature_architecture_description),
            icon = Icons.Filled.AccountTree,
            onClick = onNavigateToArchitecture
        )
    )
    val recentConversations by viewModel.recentConversations.collectAsState()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            WelcomeCard()
        }

        item {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(352.dp),
                userScrollEnabled = false
            ) {
                items(features) { feature ->
                    FeatureCard(feature = feature)
                }
            }
        }

        item {
            RecentConversationsSection(conversations = recentConversations)
        }

        item {
            AppVersionFooter()
        }
    }
}

@Composable
private fun WelcomeCard() {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.home_welcome_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_welcome_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
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
            .height(168.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = feature.title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RecentConversationsSection(
    conversations: List<Conversation>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.home_recent_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (conversations.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = stringResource(R.string.home_recent_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            conversations.forEach { conversation ->
                RecentConversationCard(conversation = conversation)
            }
        }
    }
}

@Composable
private fun RecentConversationCard(
    conversation: Conversation,
    modifier: Modifier = Modifier
) {
    val screenLabel = conversation.screenType.toHomeScreenLabel()
    val emptyInputText = stringResource(R.string.empty_input)
    val timestamp = remember(conversation.createdAt) {
        conversation.createdAt.toHomeTimeText()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = screenLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Text(
                text = conversation.userMessage.homePreview(emptyInputText),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AppVersionFooter() {
    Text(
        text = stringResource(R.string.app_version_format, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

@Composable
private fun String.toHomeScreenLabel(): String {
    return when (this) {
        "code_gen" -> stringResource(R.string.screen_label_code_generation)
        "code_explain" -> stringResource(R.string.screen_label_code_explanation)
        "debug" -> stringResource(R.string.screen_label_debug)
        "architecture" -> stringResource(R.string.screen_label_architecture)
        else -> stringResource(R.string.screen_label_conversation)
    }
}

private fun String.homePreview(emptyText: String): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .ifBlank { emptyText }
}

private fun Long.toHomeTimeText(): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(this))
}

data class FeatureItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)
