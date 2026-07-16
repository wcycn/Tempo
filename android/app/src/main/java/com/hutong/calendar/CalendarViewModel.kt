package com.hutong.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hutong.calendar.data.CalendarEvent
import com.hutong.calendar.data.RemoteCalendarRepository
import com.hutong.calendar.data.RemoteSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val remote = RemoteCalendarRepository(application)
    private val sync = RemoteSyncRepository(application)
    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val eventsState = _events.asStateFlow()
    val events: List<CalendarEvent> get() = _events.value
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        try {
            _error.value = null
            _events.value = sync.pullSnapshot().events
        } catch (error: Exception) {
            _error.value = error.message ?: "日程同步失败"
        }
    }

    fun saveEvent(event: CalendarEvent) = viewModelScope.launch {
        try {
            _error.value = null
            val saved = if (event.id.startsWith("local-")) remote.createEvent(event) else remote.updateEvent(event)
            _events.value = _events.value.filterNot { it.id == event.id } + saved
        } catch (error: Exception) { _error.value = error.message ?: "保存日程失败" }
    }

    fun deleteEvent(eventId: String) = viewModelScope.launch {
        try {
            _error.value = null
            remote.deleteEvent(eventId)
            _events.value = _events.value.filterNot { it.id == eventId }
        } catch (error: Exception) { _error.value = error.message ?: "删除日程失败" }
    }
}
