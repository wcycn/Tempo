package com.hutong.calendar.data

/** 仅用于前端界面演示的内存数据，不读写数据库、不访问后端。 */
class MockCalendarDataSource {
    private val events = mutableListOf(
        CalendarEvent("seed-1", "me", "产品评审", "2026-07-14 10:00", "2026-07-14 11:30", "工作", EventStatus.HARD),
        CalendarEvent("seed-2", "me", "羽毛球", "2026-07-14 14:00", "2026-07-14 16:00", "健身", EventStatus.FLEXIBLE, 30)
    )
    fun getEvents(): List<CalendarEvent> = events.toList()
    fun saveEvent(event: CalendarEvent) { events.removeAll { it.id == event.id }; events.add(event) }
    fun deleteEvent(eventId: String) { events.removeAll { it.id == eventId } }
}
