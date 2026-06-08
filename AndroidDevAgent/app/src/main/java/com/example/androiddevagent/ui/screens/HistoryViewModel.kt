package com.example.androiddevagent.ui.screens

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.R
import com.example.androiddevagent.data.dao.ConversationDao
import com.example.androiddevagent.data.entity.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val conversationDao: ConversationDao
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(HistoryFilter.All)
    private val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = combine(
        selectedFilter,
        searchQuery
    ) { filter, query ->
        filter to query
    }.flatMapLatest { (filter, query) ->
        val trimmedQuery = query.trim()
        val source = when {
            trimmedQuery.isNotEmpty() -> conversationDao.search(trimmedQuery)
            filter.screenType != null -> conversationDao.getByScreenType(filter.screenType)
            else -> conversationDao.getAll()
        }

        source.map { conversations ->
            val visibleConversations = if (trimmedQuery.isNotEmpty() && filter.screenType != null) {
                conversations.filter { it.screenType == filter.screenType }
            } else {
                conversations
            }

            HistoryUiState(
                conversations = visibleConversations,
                selectedFilter = filter,
                searchQuery = query
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    fun selectFilter(filter: HistoryFilter) {
        selectedFilter.value = filter
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationDao.delete(conversation)
        }
    }
}

data class HistoryUiState(
    val conversations: List<Conversation> = emptyList(),
    val selectedFilter: HistoryFilter = HistoryFilter.All,
    val searchQuery: String = ""
)

enum class HistoryFilter(
    @StringRes val labelRes: Int,
    val screenType: String?
) {
    All(R.string.history_filter_all, null),
    CodeGeneration(R.string.screen_label_code_generation, "code_gen"),
    CodeExplanation(R.string.screen_label_code_explanation, "code_explain"),
    Debug(R.string.screen_label_debug, "debug"),
    Architecture(R.string.screen_label_architecture, "architecture")
}
