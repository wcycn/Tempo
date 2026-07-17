package com.hutong.calendar.data

import android.content.Context
import android.provider.CalendarContract
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CalendarDataRepository {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun readSystemEvents(context: Context): List<CalendarEvent> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY
        )
        return runCatching {
            resolver.query(CalendarContract.Events.CONTENT_URI, projection, "${CalendarContract.Events.ALL_DAY} = 0", null, "${CalendarContract.Events.DTSTART} ASC")?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val startIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                buildList {
                    while (cursor.moveToNext()) {
                        val start = cursor.getLong(startIndex)
                        val end = cursor.getLong(endIndex)
                        if (end <= start) continue
                        val zone = ZoneId.systemDefault()
                        add(CalendarEvent(
                            id = "system-${cursor.getLong(idIndex)}",
                            ownerId = "system",
                            title = cursor.getString(titleIndex).orEmpty().ifBlank { "系统日历事件" },
                            start = Instant.ofEpochMilli(start).atZone(zone).toLocalDateTime().format(formatter),
                            end = Instant.ofEpochMilli(end).atZone(zone).toLocalDateTime().format(formatter),
                            category = "系统日历",
                            status = EventStatus.HARD
                        ))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}
