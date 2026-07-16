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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hutong.calendar.data.CalendarEvent
import com.hutong.calendar.data.EventStatus
import com.hutong.calendar.data.FriendSummary
import com.hutong.calendar.data.GroupSummary
import com.hutong.calendar.data.NoticeItem
import com.hutong.calendar.data.PendingInvite
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
    val calendarViewModel: CalendarViewModel = viewModel()
    val contentViewModel: ContentViewModel = viewModel()
    val friends = contentViewModel.friends
    val pendingInvites = contentViewModel.pendingInvites
    val notices = contentViewModel.notices
    val groups = contentViewModel.groups
    var page by remember { mutableStateOf("日程") }
    var showCreate by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var createDate by remember { mutableStateOf("2026-07-14") }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Coral)) {
        Scaffold(containerColor = Bg, bottomBar = { BottomNav(page) { page = it } }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    "找时间" -> MatchPage(pendingInvites, friends, onStartInvite = { showInvite = true }, onRespond = { contentViewModel.respondToInvite(it.id) })
                    "群组" -> GroupPage(groups)
                    "通知" -> NoticePage(notices)
                    "我的" -> SettingsPage()
                    else -> CalendarPage(calendarViewModel.events, onAdd = { date -> createDate = date; showCreate = true }, onEdit = { editingEvent = it })
                }
            }
        }
        if (showCreate) CreateEventDialog(
            date = createDate,
            onSave = { event -> calendarViewModel.saveEvent(event); showCreate = false },
            onCancel = { showCreate = false }
        )
        editingEvent?.let { event ->
            EventEditorDialog(
                event = event,
                onSave = { updated -> calendarViewModel.saveEvent(updated); editingEvent = null },
                onDelete = { calendarViewModel.deleteEvent(event.id); editingEvent = null },
                onCancel = { editingEvent = null }
            )
        }
        if (showInvite) InviteDialog(friends, onSend = { contentViewModel.addInvite(it); showInvite = false }, onClose = { showInvite = false })
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
fun CalendarPage(events: List<CalendarEvent>, onAdd: (String) -> Unit, onEdit: (CalendarEvent) -> Unit) {
    var mode by remember { mutableStateOf(CalendarMode.MONTH) }
    var year by remember { mutableStateOf(2026) }
    var month by remember { mutableStateOf(7) }
    var selectedDay by remember { mutableStateOf(14) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Header("我的时间 · ${year}年${month}月", "日程", "＋ 创建日程", { onAdd("%04d-%02d-%02d".format(year, month, selectedDay)) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = MainText, fontSize = 26.sp, modifier = Modifier.clickable { month--; if (month == 0) { month = 12; year-- }; selectedDay = selectedDay.coerceAtMost(YearMonth.of(year, month).lengthOfMonth()) })
            Text("${year}年${month}月", color = MainText, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 12.dp).weight(1f))
            Text("›", color = MainText, fontSize = 26.sp, modifier = Modifier.clickable { month++; if (month == 13) { month = 1; year++ }; selectedDay = selectedDay.coerceAtMost(YearMonth.of(year, month).lengthOfMonth()) })
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(CalendarMode.MONTH to "月", CalendarMode.WEEK to "周", CalendarMode.DAY to "日", CalendarMode.AGENDA to "日程").forEach { (item, label) ->
                FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(14.dp))
        when (mode) {
            CalendarMode.MONTH -> MonthView(year, month, selectedDay, events, onEdit, onSelect = { selectedDay = it }, onDoubleSelect = { selectedDay = it; mode = CalendarMode.DAY })
            CalendarMode.WEEK -> WeekView(year, month, selectedDay, events, onEdit) { selectedDay = it }
            CalendarMode.DAY -> DayView(year, month, selectedDay, events, onEdit)
            CalendarMode.AGENDA -> AgendaView(events, onEdit)
        }
    }
}

@Composable
fun MonthView(year: Int, month: Int, selectedDay: Int, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit, onSelect: (Int) -> Unit, onDoubleSelect: (Int) -> Unit = {}) {
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
    Spacer(Modifier.height(14.dp)); DayScheduleCard(year, month, selectedDay, events, onEdit)
}

@Composable
fun WeekView(year: Int, month: Int, selectedDay: Int, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit, onSelect: (Int) -> Unit) {
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
                if (dayEvents.isNotEmpty()) { Text("${month}月${day}日 · ${weekdayFor(year, month, day)}", color = Coral, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp)); dayEvents.forEach { event -> TimeEvent(event.start.substringAfter(" "), event.title, event.category, eventColor(event.status), onClick = { onEdit(event) }) } }
            }
            if (events.none { it.start.contains("-${month.toString().padStart(2, '0')}") }) Text("本周还没有日程", color = Muted, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable fun DayView(year: Int, month: Int, day: Int, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("${year}年${month}月${day}日", color = MainText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("${lunarLabel(year, month, day)} · ${weekdayFor(year, month, day)} · ${constellationFor(month, day)}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Text(if (month == 7 && day == 14) "丙午年 六月初一 · 今日无节气" else "农历日期 · 二十四节气信息待同步", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            HorizontalDivider(color = Color(0xFF303236), modifier = Modifier.padding(vertical = 16.dp))
            Text("日程安排", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val dayEvents = events.filter { it.start.contains("-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}") }
            if (dayEvents.isEmpty()) Text("这一天还没有日程", color = Muted, modifier = Modifier.padding(top = 20.dp))
            dayEvents.forEach { event -> TimeEvent(event.start.substringAfter(" "), event.title, "${event.category} · ${event.status}", eventColor(event.status), onClick = { onEdit(event) }) }
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
@Composable fun AgendaView(events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit) { LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(events) { event -> Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable { onEdit(event) }) { Text("${event.start} · ${event.title}", color = MainText, modifier = Modifier.fillMaxWidth().padding(18.dp)) } } } }

@Composable fun DayScheduleCard(year: Int, month: Int, day: Int, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit) { Surface(color = Panel, shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp)) { Text("${year}年${month}月${day}日", color = Muted, fontSize = 12.sp); Text("这一天的日程", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold); val prefix = "%04d-%02d-%02d".format(year, month, day); val dayEvents = events.filter { it.start.startsWith(prefix) }; if (dayEvents.isEmpty()) Text("暂无日程，点击右上角创建", color = Muted, modifier = Modifier.padding(top = 14.dp)); dayEvents.forEach { event -> TimeEvent(event.start.substringAfter(" "), event.title, "${event.start.substringAfter(" ")} — ${event.end.substringAfter(" ")} · ${event.category}", eventColor(event.status), onClick = { onEdit(event) }) } } } }
fun eventColor(status: EventStatus): Color = when (status) { EventStatus.HARD -> Red; EventStatus.FREE -> Green; EventStatus.FLEXIBLE -> Yellow; EventStatus.PENDING -> Blue }

@Composable fun Legend(color: Color, text: String) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(5.dp)); Text(text, color = Muted, fontSize = 11.sp) } }
@Composable fun TimeEvent(time: String, title: String, detail: String, color: Color, onClick: (() -> Unit)? = null) { Row(Modifier.fillMaxWidth().padding(top = 14.dp).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier), verticalAlignment = Alignment.Top) { Text(time, color = Muted, fontSize = 11.sp, modifier = Modifier.width(48.dp)); Surface(color = color.copy(alpha = .14f), shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp), modifier = Modifier.weight(1f).border(1.dp, color.copy(alpha = .7f), RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))) { Column(Modifier.padding(9.dp)) { Text(title, color = MainText, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(detail, color = Muted, fontSize = 10.sp) } } } }

@Composable
fun MatchPage(invites: List<PendingInvite>, friends: List<FriendSummary>, onStartInvite: () -> Unit, onRespond: (PendingInvite) -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Header("一起安排，不用来回问", "找时间", "＋ 发起邀约", onStartInvite)
        Surface(color = Card, shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(20.dp)) { Text("智能匹配", color = Yellow, fontSize = 12.sp); Text("谁的时间，刚好和你重合？", color = MainText, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp)); Text("先扫描双方完全空闲，再询问是否纳入机动尾巴。", color = Muted, fontSize = 12.sp) } }
        Text("待应答邀约  ${invites.size}", color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 25.dp, bottom = 12.dp))
        if (invites.isEmpty()) Text("暂时没有待应答邀约", color = Muted, modifier = Modifier.padding(vertical = 20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { items(invites) { invite -> InviteCard(invite, onRespond = { onRespond(invite) }) } }
        Text("好友", color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 25.dp, bottom = 12.dp))
        friends.forEach { friend -> Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(bottom = 8.dp)) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(friend.name, color = MainText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(friend.availability, color = Muted, fontSize = 11.sp) } } }
    }
}

@Composable fun InviteCard(invite: PendingInvite, onRespond: () -> Unit) { Surface(color = Panel, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).clip(CircleShape).background(Blue), contentAlignment = Alignment.Center) { Text(invite.inviter.take(1)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("${invite.inviter}邀请你${invite.title}", color = MainText, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(invite.time, color = Muted, fontSize = 11.sp) }; Text("待应答", color = Blue, fontSize = 11.sp) }; Text("提议时间可与你的日历叠加，不会锁定对方。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = onRespond, modifier = Modifier.weight(1f)) { Text("拒绝") }; Button(onClick = onRespond, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("同意") } } } } }

@Composable fun GroupPage(groups: List<GroupSummary>) { SimplePage("一起参加，不被代表", "群组", *groups.flatMap { listOf(it.name, it.activity, it.detail) }.toTypedArray()) }
@Composable fun NoticePage(notices: List<NoticeItem>) { SimplePage("重要的事，及时知道", "通知", *notices.flatMap { listOf(it.title, it.detail) }.toTypedArray()) }
@Composable fun SettingsPage() { SimplePage("让日历更像你的日历", "我的设置", "林小满 · linxiaoman@example.com", "连续忙碌健康提醒", "外部日历同步 · 已连接", "默认导入分类 · 工作") }
@Composable fun SimplePage(eyebrow: String, title: String, vararg lines: String) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header(eyebrow, title); lines.forEach { line -> Surface(color = Panel, shape = RoundedCornerShape(17.dp), modifier = Modifier.padding(bottom = 10.dp)) { Text(line, color = MainText, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(18.dp)) } } } }

@Composable fun BottomNav(current: String, onSelect: (String) -> Unit) { NavigationBar(containerColor = Panel) { listOf("日程" to "▣", "找时间" to "⌕", "群组" to "♧", "通知" to "♢", "我的" to "⚙").forEach { (name, icon) -> NavigationBarItem(selected = current == name, onClick = { onSelect(name) }, icon = { Text(icon, fontSize = 20.sp) }, label = { Text(name, fontSize = 10.sp) }) } } }

@Composable
fun CreateEventDialog(date: String, onSave: (CalendarEvent) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("硬性") }
    var start by remember { mutableStateOf("16:00") }
    var end by remember { mutableStateOf("17:00") }
    var showPicker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onCancel, containerColor = Panel, title = { Text("安排一段时间", fontWeight = FontWeight.Bold) }, text = {
        Column {
            OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("活动名称") }, singleLine = true)
            Text("日期：$date", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("${start} — ${end}  ·  点击选择15分钟时间段") }
            Text("对外状态", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("硬性", "空闲", "机动").forEach { item -> FilterChip(selected = status == item, onClick = { status = item }, label = { Text(item) }) } }
        }
    }, confirmButton = { Button(onClick = {
        val finalName = name.ifBlank { "未命名日程" }
        val eventStatus = when (status) { "空闲" -> EventStatus.FREE; "机动" -> EventStatus.FLEXIBLE; else -> EventStatus.HARD }
        onSave(CalendarEvent(UUID.randomUUID().toString(), "me", finalName, "$date $start", "$date $end", "工作", eventStatus, if (eventStatus == EventStatus.FLEXIBLE) 30 else 0))
    }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("保存日程") } }, dismissButton = { TextButton(onClick = onCancel) { Text("取消") } })
    if (showPicker) TimeGridDialog(initialStart = start, initialEnd = end, onCancel = { showPicker = false }, onSelect = { s, e -> start = s; end = e; showPicker = false })
}

@Composable
fun EventEditorDialog(event: CalendarEvent, onSave: (CalendarEvent) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    var name by remember(event.id) { mutableStateOf(event.title) }
    var status by remember(event.id) { mutableStateOf(event.status) }
    var start by remember(event.id) { mutableStateOf(event.start.substringAfter(" ")) }
    var end by remember(event.id) { mutableStateOf(event.end.substringAfter(" ")) }
    var showPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val statusOptions = listOf(EventStatus.HARD to "硬性", EventStatus.FREE to "空闲", EventStatus.FLEXIBLE to "机动")

    AlertDialog(onDismissRequest = onCancel, containerColor = Panel, title = { Text("编辑日程", fontWeight = FontWeight.Bold) }, text = {
        Column {
            OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("活动名称") }, singleLine = true)
            Text("日期：${event.start.substringBefore(" ")}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("$start — $end  ·  修改15分钟时间段") }
            Text("对外状态", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { statusOptions.forEach { (value, label) -> FilterChip(selected = status == value, onClick = { status = value }, label = { Text(label) }) } }
            TextButton(onClick = { showDeleteConfirm = true }, colors = ButtonDefaults.textButtonColors(contentColor = Red), modifier = Modifier.padding(top = 10.dp)) { Text("删除这个日程") }
        }
    }, confirmButton = { Button(onClick = {
        onSave(event.copy(title = name.ifBlank { "未命名日程" }, start = "${event.start.substringBefore(" ")} $start", end = "${event.end.substringBefore(" ")} $end", status = status, flexibleTailMinutes = if (status == EventStatus.FLEXIBLE) 30 else 0))
    }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("保存修改") } }, dismissButton = { TextButton(onClick = onCancel) { Text("取消") } })
    if (showPicker) TimeGridDialog(initialStart = start, initialEnd = end, onCancel = { showPicker = false }, onSelect = { s, e -> start = s; end = e; showPicker = false })
    if (showDeleteConfirm) AlertDialog(onDismissRequest = { showDeleteConfirm = false }, containerColor = Panel, title = { Text("删除日程？") }, text = { Text("删除后，这个日程将从当前设备移除。若它已经关联邀约，后续需要通知参与者。", color = Muted) }, confirmButton = { Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("确认删除") } }, dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } })
}

@Composable
fun TimeGridDialog(initialStart: String, initialEnd: String, onCancel: () -> Unit, onSelect: (String, String) -> Unit) {
    var selectedStart by remember(initialStart) { mutableStateOf<String?>(initialStart) }
    var selectedEnd by remember(initialEnd) { mutableStateOf<String?>(initialEnd) }
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
fun InviteDialog(friends: List<FriendSummary>, onSend: (PendingInvite) -> Unit, onClose: () -> Unit) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { friends.forEach { item -> FilterChip(selected = friend == item.name, onClick = { friend = item.name }, label = { Text(item.name) }) } }
            Text("活动时长", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("60分钟", "90分钟", "120分钟").forEach { duration -> FilterChip(selected = duration == "90分钟", onClick = {}, label = { Text(duration) }) } }
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("07月14日  $start — $end  ·  选择15分钟时间段") }
            if (scanned) { Surface(color = Green.copy(alpha = .12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 14.dp)) { Text("找到 3 个可用时段：07月14日 19:00、07月15日 18:30、07月16日 12:00", color = Green, fontSize = 12.sp, modifier = Modifier.padding(12.dp)) } }
        }
    }, confirmButton = { Button(onClick = { if (!scanned) scanned = true else onSend(PendingInvite("invite-${System.currentTimeMillis()}", title.ifBlank { "未命名邀约" }, "07月14日  $start — $end", friend)) }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text(if (scanned) "发送邀约" else "扫描可用时间") } }, dismissButton = { TextButton(onClick = onClose) { Text("取消") } })
    if (showPicker) TimeGridDialog(initialStart = start, initialEnd = end, onCancel = { showPicker = false }, onSelect = { s, e -> start = s; end = e; scanned = false; showPicker = false })
}
