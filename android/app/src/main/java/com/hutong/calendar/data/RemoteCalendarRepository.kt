package com.hutong.calendar.data

import android.content.Context

class RemoteCalendarRepository(context: Context) {
    private val api = TempoApiFactory.create { TokenStore(context).get() }

    suspend fun getEvents(): List<CalendarEvent> = api.events().map { it.toDomain() }
    suspend fun createEvent(event: CalendarEvent): CalendarEvent = api.createEvent(event.toRequest()).toDomain()
    suspend fun updateEvent(event: CalendarEvent): CalendarEvent = api.updateEvent(event.id, event.toRequest()).toDomain()
    suspend fun deleteEvent(eventId: String) { api.deleteEvent(eventId) }
}

private fun CalendarEvent.toRequest() = EventRequestDto(
    title = title,
    startAt = start.replace(" ", "T"),
    endAt = end.replace(" ", "T"),
    category = category,
    status = when (status) { EventStatus.FREE -> "FREE"; EventStatus.FLEXIBLE -> "FLEXIBLE"; else -> "HARD" },
    flexibleTailMinutes = flexibleTailMinutes
)

private fun EventDto.toDomain() = CalendarEvent(
    id = id.toString(), ownerId = userId.toString(), title = title,
    start = startAt.replace("T", " "), end = endAt.replace("T", " "),
    category = category, status = when (status) { "FREE" -> EventStatus.FREE; "FLEXIBLE" -> EventStatus.FLEXIBLE; else -> EventStatus.HARD },
    flexibleTailMinutes = flexibleTailMinutes
)

