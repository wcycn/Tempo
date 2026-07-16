package com.hutong.calendar.data

import android.content.Context

/** 网络可用时更新 Room；网络失败时返回上一次成功同步的本地快照。 */
class RemoteSyncRepository(context: Context, private val tokenStore: TokenStore = TokenStore(context)) : SyncRepository {
    private val dao = TempoDatabase.get(context).offlineCalendarDao()
    private val api = TempoApiFactory.create { tokenStore.get() }

    override suspend fun pullSnapshot(): SyncSnapshot = try {
        val snapshot = api.snapshot()
        dao.clearEvents()
        dao.replaceEvents(snapshot.events.map { it.toCached() })
        dao.replaceInvites(snapshot.acceptedInvites.map { it.toCached() })
        dao.replaceCalendarDays(snapshot.calendarCache.map {
            CachedCalendarDay(it.date, it.solarLabel, it.lunarLabel, it.festival, it.solarTerm)
        })
        snapshot.toDomain()
    } catch (error: Exception) {
        SyncSnapshot(
            events = dao.events().map { CalendarEvent(it.id, it.ownerId, it.title, it.start, it.end, it.category, EventStatus.valueOf(it.status), it.flexibleTailMinutes) },
            acceptedInvites = dao.acceptedInvites().map { Invite(it.id, it.title, it.inviterId, it.inviteeId, it.proposedStart, it.proposedEnd, InviteStatus.valueOf(it.status)) },
            calendarCache = dao.calendarDays().map { CalendarDayCache(it.date, it.solarLabel, it.lunarLabel, it.festival, it.solarTerm) },
            serverTime = "offline"
        )
    }
}

private fun EventDto.toCached() = CachedEvent(id.toString(), userId.toString(), title, startAt, endAt, category, status, flexibleTailMinutes)
private fun InviteDto.toCached() = CachedInvite(id.toString(), title, senderId.toString(), receiverId.toString(), startAt, endAt, status)
private fun SyncSnapshotDto.toDomain() = SyncSnapshot(
    events.map { CalendarEvent(it.id.toString(), it.userId.toString(), it.title, it.startAt, it.endAt, it.category, EventStatus.valueOf(it.status), it.flexibleTailMinutes) },
    acceptedInvites.map { Invite(it.id.toString(), it.title, it.senderId.toString(), it.receiverId.toString(), it.startAt, it.endAt, InviteStatus.valueOf(it.status)) },
    calendarCache.map { CalendarDayCache(it.date, it.solarLabel, it.lunarLabel, it.festival, it.solarTerm) },
    serverTime
)

