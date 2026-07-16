package com.hutong.calendar.data

/** 后端同步快照边界；Room 离线缓存将在后续实现类中接入。 */
interface SyncRepository {
    suspend fun pullSnapshot(): SyncSnapshot
}

data class SyncSnapshot(
    val events: List<CalendarEvent>,
    val acceptedInvites: List<Invite>,
    val calendarCache: List<CalendarDayCache>,
    val serverTime: String
)

data class CalendarDayCache(
    val date: String,
    val solarLabel: String,
    val lunarLabel: String,
    val festival: String? = null,
    val solarTerm: String? = null
)

