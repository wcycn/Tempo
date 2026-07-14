package com.hutong.calendar.data

/**
 * Android UI 只依赖这个接口，后续可以替换成 Room + Retrofit 的真实实现。
 * 所有时间统一建议使用 ISO-8601 字符串或 Instant 存储，服务端负责时区转换。
 */
interface CalendarRepository {
    suspend fun getCurrentUser(): UserProfile
    suspend fun getEvents(ownerId: String, from: String, to: String): List<CalendarEvent>
    suspend fun createEvent(event: CalendarEvent): CalendarEvent
    suspend fun updateEvent(event: CalendarEvent): CalendarEvent
    suspend fun deleteEvent(eventId: String)
    suspend fun getFriends(userId: String): List<UserProfile>
    suspend fun createInvite(invite: Invite): Invite
    suspend fun respondToInvite(inviteId: String, status: InviteStatus): Invite
}

/** 后端 REST/WebSocket 接入时，可将此接口映射到 Retrofit service。 */
interface CalendarApi {
    suspend fun fetchCalendar(from: String, to: String): List<CalendarEvent>
    suspend fun postCalendarEvent(event: CalendarEvent): CalendarEvent
    suspend fun postInvite(invite: Invite): Invite
    suspend fun postInviteResponse(inviteId: String, status: InviteStatus): Invite
}
