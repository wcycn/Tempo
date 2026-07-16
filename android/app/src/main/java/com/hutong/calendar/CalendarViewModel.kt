package com.hutong.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.hutong.calendar.data.CalendarEvent
import com.hutong.calendar.data.MockCalendarDataSource

class CalendarViewModel : ViewModel() {
    private val dataSource = MockCalendarDataSource()
    var events by mutableStateOf(dataSource.getEvents())
        private set

    fun saveEvent(event: CalendarEvent) { dataSource.saveEvent(event); events = dataSource.getEvents() }
    fun deleteEvent(eventId: String) { dataSource.deleteEvent(eventId); events = dataSource.getEvents() }
}
