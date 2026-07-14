package com.hutong.calendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import com.hutong.calendar.data.CalendarEvent
import com.hutong.calendar.data.EventStatus
import java.util.UUID
import java.time.YearMonth
import java.time.LocalDate

private val Bg = Color(0xFF090A0B)
private val Panel = Color(0xFF17191B)
private val Card = Color(0xFF202224)
private val MainText = Color(0xFFF3F4F5)
private val Muted = Color(0xFF85898F)
private val Coral = Color(0xFFFF624D)
private val Red = Color(0xFFF05B50)
private val Green = Color(0xFF57C58A)
private val Yellow = Color(0xFFEAB94E)
private val Blue = Color(0xFF6E9CFF)

enum class CalendarMode { MONTH, WEEK, DAY, AGENDA }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HutongApp() }
    }
}

@Composable
fun HutongApp() {
    var page by remember { mutableStateOf("日程") }
    var showCreate by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    val events = remember { mutableStateListOf<CalendarEvent>().apply {
        add(CalendarEvent("seed-1", "me", "产品评审", "2026-07-14 10:00", "2026-07-14 11:30", "工作", EventStatus.HARD))
        add(CalendarEvent("seed-2", "me", "羽毛球", "2026-07-14 14:00", "2026-07-14 16:00", "健身", EventStatus.FLEXIBLE, 30))
    } }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Coral)) {
        Scaffold(containerColor = Bg, bottomBar = { BottomNav(page) { page = it } }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    "找时间" -> MatchPage { showInvite = true }
                    "群组" -> GroupPage()
                    "通知" -> NoticePage()
                    "我的" -> SettingsPage()
                    else -> CalendarPage(events, onAdd = { showCreate = true })
                }
            }
        }
        if (showCreate) CreateEventDialog(
            onSave = { event -> events.add(event); showCreate = false },
            onCancel = { showCreate = false }
        )
        if (showInvite) InviteDialog { showInvite = false }
    }
}

@Composable
fun Header(eyebrow: String, title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Text(eyebrow, color = Muted, fontSize = 12.sp); Text(title, color = MainText, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
        if (action != null) Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text(action) }
    }
}

@Composable
fun CalendarPage(events: List<CalendarEvent>, onAdd: () -> Unit) {
    var mode by remember { mutableStateOf(CalendarMode.MONTH) }
    var year by remember { mutableStateOf(2026) }
    var month by remember { mutableStateOf(7) }
    var selectedDay by remember { mutableStateOf(14) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Header("我的时间 · ${year}年${month}月", "日程", "＋ 创建日程", onAdd)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = MainText, fontSize = 26.sp, modifier = Modifier.clickable { month--; if (month == 0) { month = 12; year-- } })
            Text("${year}年${month}月", color = MainText, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 12.dp).weight(1f))
            Text("›", color = MainText, fontSize = 26.sp, modifier = Modifier.clickable { month++; if (month == 13) { month = 1; year++ } })
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(CalendarMode.MONTH to "月", CalendarMode.WEEK to "周", CalendarMode.DAY to "日", CalendarMode.AGENDA to "日程").forEach { (item, label) ->
                FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(14.dp))
        when (mode) {
            CalendarMode.MONTH -> MonthView(year, month, selectedDay, events, onSelect = { selectedDay = it }, onDoubleSelect = { selectedDay = it; mode = CalendarMode.DAY })
            CalendarMode.WEEK -> WeekView(year, month, selectedDay, events) { selectedDay = it }
            CalendarMode.DAY -> DayView(year, month, selectedDay, events)
            CalendarMode.AGENDA -> AgendaView(events)
        }
    }
}

@Composable
fun MonthView(year: Int, month: Int, selectedDay: Int, events: List<CalendarEvent>, onSelect: (Int) -> Unit, onDoubleSelect: (Int) -> Unit = {}) {
    val monthInfo = YearMonth.of(year, month)
    val daysInMonth = monthInfo.lengthOfMonth()
    val firstOffset = monthInfo.atDay(1).dayOfWeek.value - 1
    Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, color = Muted, fontSize = 12.sp) } }
            Spacer(Modifier.height(10.dp))
            for (row in 0..5) {
                Row(Modifier.fillMaxWidth()) {
                    for (column in 0..6) {
                        val day = row * 7 + column - firstOffset + 1
                        val validDay = day in 1..daysInMonth
                        val selected = day == selectedDay
                        Box(Modifier.weight(1f).height(58.dp).padding(3.dp).pointerInput(year to month) { detectTapGestures(onTap = { if (validDay) onSelect(day) }, onDoubleTap = { if (validDay) { onSelect(day); onDoubleSelect(day) } }) }) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (validDay) {
                                    Box(Modifier.size(32.dp).clip(CircleShape).background(if (selected) Coral else Color.Transparent), contentAlignment = Alignment.Center) { Text("$day", color = if (selected) Color.White else MainText, fontWeight = FontWeight.Bold) }
                                    Text(lunarLabel(year, month, day), color = if (selected) Color(0xFFFFC7BF) else Muted, fontSize = 9.sp)
                                    val prefix = "%04d-%02d-%02d".format(year, month, day)
                                    val dayEvents = events.filter { it.start.startsWith(prefix) }
                                    if (dayEvents.isNotEmpty()) Row { dayEvents.take(3).forEach { event -> Box(Modifier.size(5.dp).clip(CircleShape).background(eventColor(event.status))); Spacer(Modifier.width(3.dp)) } }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { Legend(Red, "硬性"); Legend(Green, "空闲"); Legend(Yellow, "机动"); Legend(Blue, "待应答") }
        }
    }
    Spacer(Modifier.height(14.dp)); DayScheduleCard(selectedDay, events)
}

@Composable
fun WeekView(year: Int, month: Int, selectedDay: Int, events: List<CalendarEvent>, onSelect: (Int) -> Unit) {
    val weekStart = (selectedDay - 1) / 7 * 7 + 1
    val days = (0..6).map { weekStart + it }.filter { it <= YearMonth.of(year, month).lengthOfMonth() }
    Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("${year}年${month}月 · 本周", color = MainText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach { day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp).clickable { onSelect(day) }) {
                        Text(weekdayFor(year, month, day), color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.size(34.dp).clip(CircleShape).background(if (day == selectedDay) Coral else Color.Transparent), contentAlignment = Alignment.Center) { Text("$day", color = if (day == selectedDay) Color.White else MainText, fontWeight = FontWeight.Bold) }
                        Text(lunarLabel(year, month, day), color = if (day == selectedDay) Color(0xFFFFC7BF) else Muted, fontSize = 8.sp)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("本周日程", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            days.forEach { day ->
                val dayEvents = events.filter { it.start.contains("-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}") }
                if (dayEvents.isNotEmpty()) { Text("${month}月${day}日 · ${weekdayFor(year, month, day)}", color = Coral, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp)); dayEvents.forEach { event -> TimeEvent(event.start.substringAfter(" "), event.title, event.category, eventColor(event.status)) } }
            }
            if (events.none { it.start.contains("-${month.toString().padStart(2, '0')}") }) Text("本周还没有日程", color = Muted, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable fun DayView(year: Int, month: Int, day: Int, events: List<CalendarEvent>) {
    Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("${year}年${month}月${day}日", color = MainText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("${lunarLabel(year, month, day)} · ${weekdayFor(year, month, day)} · ${constellationFor(month, day)}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Text(if (month == 7 && day == 14) "丙午年 六月初一 · 今日无节气" else "农历日期 · 二十四节气信息待同步", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            HorizontalDivider(color = Color(0xFF303236), modifier = Modifier.padding(vertical = 16.dp))
            Text("日程安排", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val dayEvents = events.filter { it.start.contains("-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}") }
            if (dayEvents.isEmpty()) Text("这一天还没有日程", color = Muted, modifier = Modifier.padding(top = 20.dp))
            dayEvents.forEach { event -> TimeEvent(event.start.substringAfter(" "), event.title, "${event.category} · ${event.status}", eventColor(event.status)) }
        }
    }
}

fun weekdayFor(year: Int, month: Int, day: Int): String = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[LocalDate.of(year, month, day).dayOfWeek.value - 1]
fun constellationFor(month: Int, day: Int): String = when { month == 7 && day < 23 -> "巨蟹座"; month == 7 -> "狮子座"; else -> "星座待计算" }
fun lunarLabel(year: Int, month: Int, day: Int): String {
    val names = listOf("初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十", "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十", "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十")
    if (year == 2026 && month == 7) return if (day >= 14) "六月${names[(day - 14).coerceIn(0, 29)]}" else "五月${names[(day + 16).coerceIn(0, 29)]}"
    return "农历${names[(day - 1).coerceIn(0, 29)]}"
}
@Composable fun AgendaView(events: List<CalendarEvent>) { LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(events) { event -> Surface(color = Panel, shape = RoundedCornerShape(16.dp)) { Text("${event.start} · ${event.title}", color = MainText, modifier = Modifier.fillMaxWidth().padding(18.dp)) } } } }

@Composable fun DayScheduleCard(day: Int, events: List<CalendarEvent>) { Surface(color = Panel, shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp)) { Text("7月${day}日", color = Muted, fontSize = 12.sp); Text("这一天的日程", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold); val dayEvents = events.filter { it.start.contains("07-${day.toString().padStart(2, '0')}") }; if (dayEvents.isEmpty()) Text("暂无日程，点击右上角创建", color = Muted, modifier = Modifier.padding(top = 14.dp)); dayEvents.forEach { event -> TimeEvent(event.start.substringAfter(" "), event.title, "${event.start.substringAfter(" ")} — ${event.end.substringAfter(" ")} · ${event.category}", eventColor(event.status)) } } } }
fun eventColor(status: EventStatus): Color = when (status) { EventStatus.HARD -> Red; EventStatus.FREE -> Green; EventStatus.FLEXIBLE -> Yellow; EventStatus.PENDING -> Blue }

@Composable fun Legend(color: Color, text: String) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(5.dp)); Text(text, color = Muted, fontSize = 11.sp) } }
@Composable fun TimeEvent(time: String, title: String, detail: String, color: Color) { Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Top) { Text(time, color = Muted, fontSize = 11.sp, modifier = Modifier.width(48.dp)); Surface(color = color.copy(alpha = .14f), shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp), modifier = Modifier.weight(1f).border(1.dp, color.copy(alpha = .7f), RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))) { Column(Modifier.padding(9.dp)) { Text(title, color = MainText, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(detail, color = Muted, fontSize = 10.sp) } } } }

@Composable
fun MatchPage(onStartInvite: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Header("一起安排，不用来回问", "找时间", "＋ 发起邀约", onStartInvite)
        Surface(color = Card, shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(20.dp)) { Text("智能匹配", color = Yellow, fontSize = 12.sp); Text("谁的时间，刚好和你重合？", color = MainText, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp)); Text("先扫描双方完全空闲，再询问是否纳入机动尾巴。", color = Muted, fontSize = 12.sp) } }
        Text("待应答邀约  2", color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 25.dp, bottom = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { items(listOf("周子昂邀请你共进晚餐", "许知远邀请你打羽毛球")) { InviteCard(it) } }
    }
}

@Composable fun InviteCard(title: String) { Surface(color = Panel, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).clip(CircleShape).background(Blue), contentAlignment = Alignment.Center) { Text(title.take(1)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(title, color = MainText, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("07月14日 19:00 — 20:30", color = Muted, fontSize = 11.sp) }; Text("待应答", color = Blue, fontSize = 11.sp) }; Text("提议时间可与你的日历叠加，不会锁定对方。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("换个时间") }; Button(onClick = {}, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("同意") } } } } }

@Composable fun GroupPage() { SimplePage("一起参加，不被代表", "群组", "羽毛球俱乐部", "周末羽毛球约局", "最低 4 人 · 响应截止还有 18:42:10") }
@Composable fun NoticePage() { SimplePage("重要的事，及时知道", "通知", "周子昂发来一条邀约", "周末羽毛球约局达到成团人数", "你的活动已同步") }
@Composable fun SettingsPage() { SimplePage("让日历更像你的日历", "我的设置", "林小满 · linxiaoman@example.com", "连续忙碌健康提醒", "外部日历同步 · 已连接", "默认导入分类 · 工作") }
@Composable fun SimplePage(eyebrow: String, title: String, vararg lines: String) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header(eyebrow, title); lines.forEach { line -> Surface(color = Panel, shape = RoundedCornerShape(17.dp), modifier = Modifier.padding(bottom = 10.dp)) { Text(line, color = MainText, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(18.dp)) } } } }

@Composable fun BottomNav(current: String, onSelect: (String) -> Unit) { NavigationBar(containerColor = Panel) { listOf("日程" to "▣", "找时间" to "⌕", "群组" to "♧", "通知" to "♢", "我的" to "⚙").forEach { (name, icon) -> NavigationBarItem(selected = current == name, onClick = { onSelect(name) }, icon = { Text(icon, fontSize = 20.sp) }, label = { Text(name, fontSize = 10.sp) }) } } }

@Composable
fun CreateEventDialog(onSave: (CalendarEvent) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("硬性") }
    var start by remember { mutableStateOf("16:00") }
    var end by remember { mutableStateOf("17:00") }
    var showPicker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onCancel, containerColor = Panel, title = { Text("安排一段时间", fontWeight = FontWeight.Bold) }, text = {
        Column {
            OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("活动名称") }, singleLine = true)
            Text("日期：2026年7月14日", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("${start} — ${end}  ·  点击选择15分钟时间段") }
            Text("对外状态", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("硬性", "空闲", "机动").forEach { item -> FilterChip(selected = status == item, onClick = { status = item }, label = { Text(item) }) } }
        }
    }, confirmButton = { Button(onClick = {
        val finalName = name.ifBlank { "未命名日程" }
        val eventStatus = when (status) { "空闲" -> EventStatus.FREE; "机动" -> EventStatus.FLEXIBLE; else -> EventStatus.HARD }
        onSave(CalendarEvent(UUID.randomUUID().toString(), "me", finalName, "2026-07-14 $start", "2026-07-14 $end", "工作", eventStatus, if (eventStatus == EventStatus.FLEXIBLE) 30 else 0))
    }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("保存日程") } }, dismissButton = { TextButton(onClick = onCancel) { Text("取消") } })
    if (showPicker) TimeGridDialog(initialStart = start, initialEnd = end, onCancel = { showPicker = false }, onSelect = { s, e -> start = s; end = e; showPicker = false })
}

@Composable
fun TimeGridDialog(initialStart: String, initialEnd: String, onCancel: () -> Unit, onSelect: (String, String) -> Unit) {
    var selectedStart by remember { mutableStateOf<String?>(null) }
    var selectedEnd by remember { mutableStateOf<String?>(null) }
    val slots = (0 until 57).map { minutes ->
        val total = 9 * 60 + minutes * 15
        "%02d:%02d".format(total / 60, total % 60)
    }
    AlertDialog(onDismissRequest = onCancel, containerColor = Panel, title = { Text("选择时间段", fontWeight = FontWeight.Bold) }, text = {
        Column {
            Text(if (selectedStart == null) "先点击起始时间" else if (selectedEnd == null) "再点击结束时间" else "已选择 $selectedStart — $selectedEnd", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.height(330.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(slots) { time ->
                    val active = selectedStart != null && selectedEnd != null && time >= selectedStart!! && time < selectedEnd!!
                    Surface(color = if (active || time == selectedStart || time == selectedEnd) Coral.copy(alpha = .3f) else Card, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().clickable {
                        when { selectedStart == null -> selectedStart = time; selectedEnd == null && time > selectedStart!! -> selectedEnd = time; else -> { selectedStart = time; selectedEnd = null } }
                    }) { Text(time, color = if (active) Color.White else MainText, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) }
                }
            }
        }
    }, confirmButton = { Button(onClick = { if (selectedStart != null && selectedEnd != null) onSelect(selectedStart!!, selectedEnd!!) }, colors = ButtonDefaults.buttonColors(containerColor = Coral), enabled = selectedStart != null && selectedEnd != null) { Text("确定") } }, dismissButton = { TextButton(onClick = onCancel) { Text("取消") } })
}

@Composable
fun InviteDialog(onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var friend by remember { mutableStateOf("周子昂") }
    var scanned by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf("19:00") }
    var end by remember { mutableStateOf("20:30") }
    var showPicker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose, containerColor = Panel, title = { Text("发起邀约", fontWeight = FontWeight.Bold) }, text = {
        Column {
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("活动名称，例如：晚餐") }, singleLine = true)
            Text("选择好友", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("周子昂", "许知远", "陈一一").forEach { name -> FilterChip(selected = friend == name, onClick = { friend = name }, label = { Text(name) }) } }
            Text("活动时长", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("60分钟", "90分钟", "120分钟").forEach { duration -> FilterChip(selected = duration == "90分钟", onClick = {}, label = { Text(duration) }) } }
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("07月14日  $start — $end  ·  选择15分钟时间段") }
            if (scanned) { Surface(color = Green.copy(alpha = .12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 14.dp)) { Text("找到 3 个可用时段：07月14日 19:00、07月15日 18:30、07月16日 12:00", color = Green, fontSize = 12.sp, modifier = Modifier.padding(12.dp)) } }
        }
    }, confirmButton = { Button(onClick = { if (!scanned) scanned = true else onClose() }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text(if (scanned) "发送邀约" else "扫描可用时间") } }, dismissButton = { TextButton(onClick = onClose) { Text("取消") } })
    if (showPicker) TimeGridDialog(initialStart = start, initialEnd = end, onCancel = { showPicker = false }, onSelect = { s, e -> start = s; end = e; scanned = false; showPicker = false })
}
