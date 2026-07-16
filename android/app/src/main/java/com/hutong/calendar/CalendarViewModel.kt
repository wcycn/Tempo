package com.hutong.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hutong.calendar.data.CalendarEvent
import com.hutong.calendar.data.CachedEvent
import com.hutong.calendar.data.EventStatus
import com.hutong.calendar.data.TempoDatabase
import com.hutong.calendar.data.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val local = TempoDatabase.get(application).offlineCalendarDao()
    private val tokenStore = TokenStore(application)
    private val ownerId: String get() = tokenStore.cachedUser()?.id ?: "guest"
    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val eventsState = _events.asStateFlow()
    val events: List<CalendarEvent> get() = _events.value
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        try {
            _error.value = null
            _events.value = local.events(ownerId).map { it.toDomain() }
        } catch (error: Exception) {
            _events.value = local.events(ownerId).map { it.toDomain() }
            _error.value = error.message ?: "当前离线，已显示本地日程"
        }
    }

    fun saveEvent(event: CalendarEvent) = viewModelScope.launch {
        try {
            _error.value = null
            val saved = event.copy(ownerId = ownerId)
            local.saveEvent(saved.toCached())
            _events.value = _events.value.filterNot { it.id == event.id } + saved
        } catch (error: Exception) {
            _error.value = error.message ?: "保存日程失败"
        }
    }

    fun deleteEvent(eventId: String) = viewModelScope.launch {
        try {
            _error.value = null
            local.deleteEvent(eventId)
            _events.value = _events.value.filterNot { it.id == eventId }
        } catch (error: Exception) { _error.value = error.message ?: "删除日程失败" }
    }
}

private fun CachedEvent.toDomain() = CalendarEvent(id, ownerId, title, start, end, category, runCatching { EventStatus.valueOf(status) }.getOrDefault(EventStatus.HARD), flexibleTailMinutes)
private fun CalendarEvent.toCached() = CachedEvent(id, ownerId, title, start, end, category, status.name, flexibleTailMinutes)
