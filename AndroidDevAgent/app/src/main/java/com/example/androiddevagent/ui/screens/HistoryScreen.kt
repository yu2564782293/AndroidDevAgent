package com.example.androiddevagent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.androiddevagent.R
import com.example.androiddevagent.data.entity.Conversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            label = { Text(stringResource(R.string.history_search_label)) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.history_clear_search)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        ScrollableTabRow(
            selectedTabIndex = HistoryFilter.entries.indexOf(uiState.selectedFilter),
            edgePadding = 0.dp
        ) {
            HistoryFilter.entries.forEach { filter ->
                Tab(
                    selected = uiState.selectedFilter == filter,
                    onClick = { viewModel.selectFilter(filter) },
                    text = { Text(stringResource(filter.labelRes)) }
                )
            }
        }

        if (uiState.conversations.isEmpty()) {
            EmptyHistoryState(
                hasSearchQuery = uiState.searchQuery.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = uiState.conversations,
                    key = { it.id }
                ) { conversation ->
                    val dismissState = rememberDismissState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue != DismissValue.Default) {
                                viewModel.deleteConversation(conversation)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismiss(
                        state = dismissState,
                        directions = setOf(
                            DismissDirection.EndToStart,
                            DismissDirection.StartToEnd
                        ),
                        background = {
                            DeleteBackground()
                        },
                        dismissContent = {
                            ConversationHistoryCard(conversation = conversation)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(
    hasSearchQuery: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                text = if (hasSearchQuery) {
                    stringResource(R.string.history_empty_search)
                } else {
                    stringResource(R.string.history_empty_all)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (hasSearchQuery) {
                    stringResource(R.string.history_empty_search_hint)
                } else {
                    stringResource(R.string.history_empty_all_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun DeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(R.string.history_delete),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ConversationHistoryCard(
    conversation: Conversation,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(conversation.id) { mutableStateOf(false) }
    val screenInfo = remember(conversation.screenType) { conversation.screenInfo() }
    val timestamp = remember(conversation.createdAt) {
        conversation.createdAt.formatConversationTime()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = screenInfo.icon,
                    contentDescription = stringResource(screenInfo.labelRes),
                    tint = MaterialTheme.colorScheme.primary
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(screenInfo.labelRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Text(
                        text = conversation.userMessage.previewText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    conversation.language?.takeIf { it.isNotBlank() }?.let { language ->
                        Text(
                            text = stringResource(R.string.history_language_type_format, language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HistoryTextSection(
                        title = stringResource(R.string.history_user_input),
                        text = conversation.userMessage
                    )
                    HistoryTextSection(
                        title = stringResource(R.string.history_ai_response),
                        text = conversation.aiResponse
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryTextSection(
    title: String,
    text: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class ScreenInfo(
    val labelRes: Int,
    val icon: ImageVector
)

private fun Conversation.screenInfo(): ScreenInfo {
    return when (screenType) {
        "code_gen" -> ScreenInfo(R.string.screen_label_code_generation, Icons.Filled.Code)
        "code_explain" -> ScreenInfo(R.string.screen_label_code_explanation, Icons.Filled.MenuBook)
        "debug" -> ScreenInfo(R.string.screen_label_debug, Icons.Filled.BugReport)
        "architecture" -> ScreenInfo(R.string.screen_label_architecture, Icons.Filled.AccountTree)
        else -> ScreenInfo(R.string.screen_label_conversation, Icons.Filled.History)
    }
}

private fun String.previewText(): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .ifBlank { "无输入内容" }
}

private fun Long.formatConversationTime(): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(this))
}
