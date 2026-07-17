package com.hutong.calendar

import com.nlf.calendar.Solar
import java.time.LocalDate

data class CalendarDayInfo(
    val lunar: String,
    val festival: String? = null,
    val solarTerm: String? = null
)

/**
 * 万年历统一数据入口。农历、节气和节日由标准农历库计算，不在页面内硬编码。
 * 该计算完全离线可用，结果由 CalendarViewModel 写入 Room 缓存。
 */
object CalendarInfoProvider {
    private val cache = HashMap<LocalDate, CalendarDayInfo>()

    fun info(date: LocalDate): CalendarDayInfo = synchronized(cache) {
        cache[date] ?: run {
            val lunar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar
            val lunarLabel = if (lunar.dayInChinese == "初一") lunar.monthInChinese else lunar.dayInChinese
            val festivals = lunar.festivals.toList().ifEmpty { lunar.otherFestivals.toList() }
            CalendarDayInfo(
                lunar = lunarLabel,
                festival = festivals.firstOrNull(),
                solarTerm = lunar.jieQi.takeIf { it.isNotBlank() }
            ).also { cache[date] = it }
        }
    }

    fun lunarLabel(date: LocalDate): String = info(date).lunar
}
