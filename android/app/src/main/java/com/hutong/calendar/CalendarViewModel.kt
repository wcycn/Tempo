package com.hutong.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hutong.calendar.data.CalendarEvent
import com.hutong.calendar.data.CachedEvent
import com.hutong.calendar.data.EventStatus
import com.hutong.calendar.data.TempoDatabase
import com.hutong.calendar.data.TokenStore
import com.hutong.calendar.data.TempoApiFactory
import com.hutong.calendar.data.AvailabilityBlockDto
import com.hutong.calendar.data.AvailabilityUpdateDto
import com.hutong.calendar.data.CalendarDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val local = TempoDatabase.get(application).offlineCalendarDao()
    private val tokenStore = TokenStore(application)
    private val remote = TempoApiFactory.create { tokenStore.get() }
    private val ownerId: String get() = tokenStore.cachedUser()?.id ?: "guest"
    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val eventsState = _events.asStateFlow()
    val events: List<CalendarEvent> get() = _events.value
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _syncNotice = MutableStateFlow<String?>(null)
    val syncNotice = _syncNotice.asStateFlow()
    private val _importReport = MutableStateFlow<String?>(null)
    val importReport = _importReport.asStateFlow()

    fun refresh() = viewModelScope.launch {
        _loading.value = true
        _error.value = null
        val localEvents = try {
            local.events(ownerId).map { it.toDomain() }
        } catch (error: Exception) {
            Log.e("TempoCalendar", "读取本地日历失败", error)
            _events.value = emptyList()
            _error.value = "本地日历数据库读取失败，请重启应用后重试"
            _loading.value = false
            return@launch
        }
        // 系统日历是可选数据源；权限未授予或系统日历读取失败时，不影响 Tempo 本地日历。
        val systemEvents = runCatching { CalendarDataRepository.readSystemEvents(getApplication()) }
            .onFailure { Log.w("TempoCalendar", "读取系统日历失败，已跳过", it) }
            .getOrDefault(emptyList())
        _events.value = (localEvents + systemEvents).distinctBy { it.id }.sortedBy { it.start }
        if (tokenStore.get() != null) {
            runCatching { remote.syncSnapshot() }.onSuccess { snapshot ->
                val previous = tokenStore.serverSyncVersion()
                    val current = snapshot.serverVersion
                    if (!current.isNullOrBlank()) {
                        if (previous != null && previous != current) _syncNotice.value = "服务器数据已更新，正在使用最新状态"
                        tokenStore.saveServerSyncVersion(current)
                    }
            }.onFailure { Log.w("TempoCalendar", "同步服务器状态失败，继续使用本地日历", it) }
        }
        runCatching { cacheCalendarInfo() }
            .onFailure { Log.w("TempoCalendar", "缓存万年历失败，不影响日历使用", it) }
        publishAvailability(null)
        _loading.value = false
    }

    fun saveEvent(event: CalendarEvent) = viewModelScope.launch {
        try {
            _error.value = null
            val saved = event.copy(ownerId = ownerId)
            local.saveEvent(saved.toCached())
            tokenStore.bumpLocalRevision()
            _events.value = _events.value.filterNot { it.id == event.id } + saved
            publishAvailability(saved.start.substringBefore(" "))
        } catch (error: Exception) {
            _error.value = if (error is IOException) "当前无法保存到本地，请稍后重试" else "保存日程失败，请稍后重试"
        }
    }

    fun deleteEvent(eventId: String) = viewModelScope.launch {
        try {
            _error.value = null
            local.deleteEvent(eventId, ownerId)
            tokenStore.bumpLocalRevision()
            _events.value = _events.value.filterNot { it.id == eventId }
            publishAvailability(null)
        } catch (error: Exception) { _error.value = if (error is IOException) "当前无法删除日程，请稍后重试" else "删除日程失败，请稍后重试" }
    }

    fun importEvents(items: List<CalendarEvent>) = viewModelScope.launch {
        if (items.isEmpty()) { _importReport.value = "没有可以导入的有效日程"; return@launch }
        try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            val existing = local.events(ownerId).map { it.toDomain() }
            val accepted = mutableListOf<CalendarEvent>()
            val failures = mutableListOf<String>()
            items.forEach { candidate ->
                val start = runCatching { LocalDateTime.parse(candidate.start, formatter) }.getOrNull()
                val end = runCatching { LocalDateTime.parse(candidate.end, formatter) }.getOrNull()
                if (start == null || end == null || !start.isBefore(end)) {
                    failures += "${candidate.title}：时间无效"
                } else if ((existing + accepted).any { other ->
                        val otherStart = runCatching { LocalDateTime.parse(other.start, formatter) }.getOrNull()
                        val otherEnd = runCatching { LocalDateTime.parse(other.end, formatter) }.getOrNull()
                        otherStart != null && otherEnd != null && start.isBefore(otherEnd) && end.isAfter(otherStart)
                    }) {
                    failures += "${candidate.title}：与已有日程重叠"
                } else {
                    accepted += candidate.copy(ownerId = ownerId, id = "local-import-${java.util.UUID.randomUUID()}", status = EventStatus.HARD)
                }
            }
            local.saveEvents(accepted.map { it.toCached() })
            tokenStore.bumpLocalRevision()
            _events.value = (_events.value.filterNot { old -> accepted.any { it.id == old.id } } + accepted).sortedBy { it.start }
            publishAvailability(null)
            _importReport.value = buildString {
                append("成功导入 ${accepted.size} 条")
                if (failures.isNotEmpty()) append("\n失败 ${failures.size} 条：\n").append(failures.take(8).joinToString("\n"))
                if (failures.size > 8) append("\n……其余失败条目已省略")
            }
        } catch (error: Exception) {
            Log.e("TempoCalendar", "批量导入失败", error)
            _importReport.value = "导入失败：${error.message ?: "本地数据库写入失败"}"
        }
    }

    fun clearImportReport() { _importReport.value = null }

    fun clearError() { _error.value = null }
    fun clearSyncNotice() { _syncNotice.value = null }

    private suspend fun cacheCalendarInfo() {
        val start = LocalDate.now().minusDays(365)
        val days = (0..730).map { dateOffset ->
            val date = start.plusDays(dateOffset.toLong())
            val info = CalendarInfoProvider.info(date)
            com.hutong.calendar.data.CachedCalendarDay(ownerId, date.toString(), date.toString(), info.lunar, info.festival, info.solarTerm)
        }
        local.replaceCalendarDays(days)
    }

    private suspend fun publishAvailability(date: String?) {
        if (tokenStore.get() == null) return
        runCatching {
            val all = local.events(ownerId)
            val selected = if (date == null) all else all.filter { it.start.startsWith(date) }
            remote.updateAvailability(AvailabilityUpdateDto(selected.map {
                AvailabilityBlockDto(it.start.substringBefore(" "), it.start.substringAfter(" "), it.end.substringAfter(" "), if (it.status == EventStatus.PENDING.name) EventStatus.HARD.name else it.status)
            }))
        }
    }
}

private fun CachedEvent.toDomain() = CalendarEvent(id, ownerId, title, start, end, category, runCatching { EventStatus.valueOf(status) }.getOrDefault(EventStatus.HARD), flexibleTailMinutes)
private fun CalendarEvent.toCached() = CachedEvent(ownerId, id, title, start, end, category, status.name, flexibleTailMinutes)
