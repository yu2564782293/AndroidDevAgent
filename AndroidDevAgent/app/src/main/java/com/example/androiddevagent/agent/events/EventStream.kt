package com.example.androiddevagent.agent.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventStream @Inject constructor() {

    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private val _history = mutableListOf<AgentEvent>()
    val history: List<AgentEvent> get() = _history.toList()

    suspend fun emit(event: AgentEvent) {
        _history.add(event)
        _events.emit(event)
    }

    fun emitSync(event: AgentEvent) {
        _history.add(event)
        _events.tryEmit(event)
    }

    fun clear() {
        _history.clear()
    }

    fun getToolCallHistory(): List<AgentEvent.ToolCallEvent> {
        return _history.filterIsInstance<AgentEvent.ToolCallEvent>()
    }

    fun getRecentEvents(count: Int): List<AgentEvent> {
        return _history.takeLast(count)
    }
}
