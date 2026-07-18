package com.hutong.calendar.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.zip.ZipInputStream

data class ImportCandidate(
    val title: String,
    val start: String,
    val end: String,
    val description: String? = null,
    val sourceRow: Int = 0
)

data class ImportParseResult(
    val items: List<ImportCandidate>,
    val errors: List<String>
)

/** Parses the documented Tempo CSV/XLSX/ICS formats without adding a large third-party dependency. */
object CalendarImportParser {
    private val output = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val accepted = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    )

    fun parse(context: Context, uri: Uri): ImportParseResult {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        }.orEmpty().lowercase()
        val type = context.contentResolver.getType(uri).orEmpty().lowercase()
        return runCatching {
            when {
                name.endsWith(".ics") || type.contains("calendar") -> parseIcs(context, uri)
                name.endsWith(".xlsx") || type.contains("spreadsheet") -> parseXlsx(context, uri)
                else -> parseCsv(context, uri)
            }
        }.getOrElse { ImportParseResult(emptyList(), listOf("文件读取失败：${it.message ?: "格式无法识别"}")) }
    }

    private fun openText(context: Context, uri: Uri): BufferedReader =
        BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri) ?: error("无法打开文件"), Charsets.UTF_8))

    private fun parseCsv(context: Context, uri: Uri): ImportParseResult {
        val lines = openText(context, uri).use { it.readLines() }
        if (lines.isEmpty()) return ImportParseResult(emptyList(), listOf("CSV 文件为空"))
        val headers = csvLine(lines.first()).map { it.trim().lowercase() }
        fun column(vararg names: String) = names.firstNotNullOfOrNull { wanted -> headers.indexOf(wanted).takeIf { it >= 0 } }
        val titleIndex = column("title", "标题", "活动名称")
        val startIndex = column("start", "开始", "开始时间", "start_at")
        val endIndex = column("end", "结束", "结束时间", "end_at")
        if (titleIndex == null || startIndex == null || endIndex == null) {
            return ImportParseResult(emptyList(), listOf("缺少必填列：title、start、end（也支持中文列名标题、开始时间、结束时间）"))
        }
        val descriptionIndex = column("description", "描述", "活动描述")
        val result = mutableListOf<ImportCandidate>()
        val errors = mutableListOf<String>()
        lines.drop(1).forEachIndexed { offset, line ->
            val row = offset + 2
            if (line.isBlank()) return@forEachIndexed
            val values = csvLine(line)
            val title = values.getOrNull(titleIndex).orEmpty().trim()
            val start = normalizeDate(values.getOrNull(startIndex).orEmpty())
            val end = normalizeDate(values.getOrNull(endIndex).orEmpty())
            when {
                title.isBlank() -> errors += "第$row 行：标题为空"
                start == null || end == null -> errors += "第$row 行：开始或结束时间格式错误"
                !isValidRange(start, end) -> errors += "第$row 行：结束时间必须晚于开始时间"
                else -> result += ImportCandidate(title, start, end, descriptionIndex?.let { values.getOrNull(it)?.trim() }, row)
            }
        }
        return ImportParseResult(result, errors)
    }

    private fun csvLine(line: String): List<String> {
        val values = mutableListOf<String>(); val current = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            when (val ch = line[i]) {
                '"' -> if (quoted && i + 1 < line.length && line[i + 1] == '"') { current.append('"'); i++ } else quoted = !quoted
                ',' -> if (quoted) current.append(ch) else { values += current.toString(); current.clear() }
                else -> current.append(ch)
            }
            i++
        }
        values += current.toString()
        return values
    }

    private fun parseIcs(context: Context, uri: Uri): ImportParseResult {
        val lines = openText(context, uri).use { reader ->
            val unfolded = mutableListOf<String>()
            reader.forEachLine { line -> if (line.startsWith(" ") || line.startsWith("\t")) unfolded[unfolded.lastIndex] += line.drop(1) else unfolded += line }
            unfolded
        }
        val result = mutableListOf<ImportCandidate>(); val errors = mutableListOf<String>(); var current: MutableMap<String, String>? = null
        fun finish(row: Int) {
            val item = current ?: return
            val title = item["SUMMARY"].orEmpty().trim(); val start = normalizeIcsDate(item["DTSTART"].orEmpty()); val end = normalizeIcsDate(item["DTEND"].orEmpty())
            if (title.isBlank() || start == null || end == null || !isValidRange(start, end)) errors += "VEVENT 第$row 项：标题或时间无效" else result += ImportCandidate(title, start, end, item["DESCRIPTION"], row)
        }
        lines.forEachIndexed { index, raw ->
            val line = raw.trimEnd()
            when {
                line.equals("BEGIN:VEVENT", true) -> current = mutableMapOf()
                line.equals("END:VEVENT", true) -> { finish(index + 1); current = null }
                current != null -> {
                    val colon = line.indexOf(':')
                    if (colon > 0) current!![line.substringBefore(';').uppercase()] = unescapeIcs(line.substring(colon + 1))
                }
            }
        }
        return ImportParseResult(result, errors)
    }

    private fun parseXlsx(context: Context, uri: Uri): ImportParseResult {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(context.contentResolver.openInputStream(uri) ?: error("无法打开 XLSX")).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml") entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        val shared = parseSharedStrings(entries["xl/sharedStrings.xml"])
        val rows = parseSheetRows(entries["xl/worksheets/sheet1.xml"] ?: error("XLSX 缺少第一个工作表"), shared)
        if (rows.isEmpty()) return ImportParseResult(emptyList(), listOf("XLSX 工作表为空"))
        val headers = rows.first().map { it.trim().lowercase() }
        fun column(vararg names: String) = names.firstNotNullOfOrNull { wanted -> headers.indexOf(wanted).takeIf { it >= 0 } }
        val titleIndex = column("title", "标题", "活动名称"); val startIndex = column("start", "开始", "开始时间", "start_at"); val endIndex = column("end", "结束", "结束时间", "end_at")
        if (titleIndex == null || startIndex == null || endIndex == null) return ImportParseResult(emptyList(), listOf("XLSX 缺少必填列：title、start、end"))
        val descriptionIndex = column("description", "描述", "活动描述"); val result = mutableListOf<ImportCandidate>(); val errors = mutableListOf<String>()
        rows.drop(1).forEachIndexed { index, row ->
            val line = index + 2; val title = row.getOrNull(titleIndex).orEmpty().trim(); val start = normalizeDate(row.getOrNull(startIndex).orEmpty()); val end = normalizeDate(row.getOrNull(endIndex).orEmpty())
            when { title.isBlank() -> errors += "第$line 行：标题为空"; start == null || end == null -> errors += "第$line 行：时间格式错误"; !isValidRange(start, end) -> errors += "第$line 行：结束时间必须晚于开始时间"; else -> result += ImportCandidate(title, start, end, descriptionIndex?.let { row.getOrNull(it) }, line) }
        }
        return ImportParseResult(result, errors)
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList(); val parser = Xml.newPullParser(); parser.setInput(bytes.inputStream(), "UTF-8"); val out = mutableListOf<String>(); var text = StringBuilder(); var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) { if (event == org.xmlpull.v1.XmlPullParser.TEXT) text.append(parser.text); if (event == org.xmlpull.v1.XmlPullParser.END_TAG && parser.name == "si") { out += text.toString(); text = StringBuilder() }; event = parser.next() }; return out
    }

    private fun parseSheetRows(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val parser = Xml.newPullParser(); parser.setInput(bytes.inputStream(), "UTF-8"); val rows = mutableListOf<List<String>>(); var row = mutableMapOf<Int, String>(); var col = 0; var type = ""; var value = StringBuilder(); var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) { "row" -> row = mutableMapOf(); "c" -> { val ref = parser.getAttributeValue(null, "r").orEmpty(); col = ref.takeWhile { it.isLetter() }.fold(0) { n, ch -> n * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1; type = parser.getAttributeValue(null, "t").orEmpty() }; "v", "t" -> value = StringBuilder() }
                org.xmlpull.v1.XmlPullParser.TEXT -> value.append(parser.text)
                org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) { "v", "t" -> { val raw = value.toString(); row[col] = if (type == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw }; "row" -> if (row.isNotEmpty()) rows += (0..(row.keys.maxOrNull() ?: 0)).map { row[it].orEmpty() } }
            }; event = parser.next()
        }; return rows
    }

    private fun normalizeDate(raw: String): String? {
        val value = raw.trim(); if (value.isBlank()) return null
        value.toDoubleOrNull()?.let { serial -> if (serial > 20000) return LocalDateTime.of(1899, 12, 30, 0, 0).plusSeconds((serial * 86400).toLong()).format(output) }
        accepted.forEach { formatter -> runCatching { return LocalDateTime.parse(value, formatter).format(output) } }
        return runCatching { LocalDate.parse(value).atStartOfDay().format(output) }.getOrNull()
    }

    private fun normalizeIcsDate(raw: String): String? {
        val value = raw.trim(); if (value.length == 8 && value.all(Char::isDigit)) return runCatching { LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay().format(output) }.getOrNull()
        val cleaned = value.removeSuffix("Z"); val parsed = runCatching { LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")) }.getOrNull() ?: runCatching { LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")) }.getOrNull()
        return parsed?.atZone(ZoneId.of("Asia/Shanghai"))?.withZoneSameInstant(ZoneId.of("Asia/Shanghai"))?.toLocalDateTime()?.format(output)
    }

    private fun unescapeIcs(value: String) = value.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
    private fun isValidRange(start: String, end: String) = runCatching { LocalDateTime.parse(start, output).isBefore(LocalDateTime.parse(end, output)) }.getOrDefault(false)
}
