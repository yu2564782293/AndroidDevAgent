package com.example.androiddevagent.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.data.dao.ConversationDao
import com.example.androiddevagent.data.entity.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    conversationDao: ConversationDao
) : ViewModel() {

    val recentConversations: StateFlow<List<Conversation>> = conversationDao.getAll()
        .map { conversations -> conversations.take(3) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}
