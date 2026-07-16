package com.hutong.calendar

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hutong.calendar.data.CalendarEvent
import com.hutong.calendar.data.CategoryOption
import com.hutong.calendar.data.CategoryStore
import com.hutong.calendar.data.EventStatus
import com.hutong.calendar.data.FriendSummary
import com.hutong.calendar.data.GroupSummary
import com.hutong.calendar.data.NoticeItem
import com.hutong.calendar.data.PendingInvite
import com.hutong.calendar.data.ThemeChoice
import com.hutong.calendar.data.ThemePreference
import com.hutong.calendar.data.UserProfile
import java.util.UUID
import android.graphics.Color as AndroidColor
import java.time.YearMonth
import java.time.LocalDate

private data class AppPalette(val bg: Color, val panel: Color, val card: Color, val text: Color, val muted: Color, val accent: Color, val border: Color)
private val DarkPalette = AppPalette(Color(0xFF090A0B), Color(0xFF17191B), Color(0xFF202224), Color(0xFFF3F4F5), Color(0xFF85898F), Color(0xFF214A78), Color(0xFF4A4D52))
private val LightPalette = AppPalette(Color(0xFFF7F7F8), Color.White, Color(0xFFEDEEF0), Color(0xFF17191B), Color(0xFF6B7078), Color(0xFF75BDF2), Color(0xFFD1D5DB))
private var currentPalette by mutableStateOf(DarkPalette)
val Bg get() = currentPalette.bg
val Panel get() = currentPalette.panel
val Card get() = currentPalette.card
val MainText get() = currentPalette.text
val Muted get() = currentPalette.muted
val Coral get() = currentPalette.accent
val ThemeBorder get() = currentPalette.border
val Red = Color(0xFFF05B50)
val Green = Color(0xFF57C58A)
val Yellow = Color(0xFFEAB94E)
val Blue get() = currentPalette.accent

enum class CalendarMode { MONTH, WEEK, DAY, AGENDA }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HutongApp() }
    }
}

@Composable
fun HutongApp() {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel()
    val invitesViewModel: InvitesViewModel = viewModel()
    val authState by authViewModel.state.collectAsState()
    val accountScope = (authState as? AuthState.LoggedIn)?.session?.user?.id ?: "guest"
    var themeChoice by remember(accountScope) { mutableStateOf(ThemePreference.load(context, accountScope)) }
    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(themeChoice, systemDark) {
        currentPalette = if (themeChoice == ThemeChoice.DARK || (themeChoice == ThemeChoice.SYSTEM && systemDark)) DarkPalette else LightPalette
    }
    val colorScheme = if (currentPalette == DarkPalette) {
        darkColorScheme(
            background = Bg, surface = Panel, primary = Coral, onPrimary = Color.White,
            secondary = Coral, onSecondary = Color.White, tertiary = Coral,
            secondaryContainer = Coral.copy(alpha = .28f), onSecondaryContainer = Color.White,
            primaryContainer = Coral.copy(alpha = .28f), onPrimaryContainer = Color.White
        )
    } else {
        lightColorScheme(
            background = Bg, surface = Panel, primary = Coral, onPrimary = Color.White,
            secondary = Coral, onSecondary = Color(0xFF123B5B), tertiary = Coral,
            secondaryContainer = Coral.copy(alpha = .22f), onSecondaryContainer = Color(0xFF123B5B),
            primaryContainer = Coral.copy(alpha = .22f), onPrimaryContainer = Color(0xFF123B5B)
        )
    }
    var showAuth by remember { mutableStateOf(false) }
    if (showAuth && authState !is AuthState.LoggedIn) {
        MaterialTheme(colorScheme = colorScheme) {
            AuthScreen(authState, authViewModel::login, authViewModel::register, onBack = { showAuth = false })
        }
        return
    }
    val calendarViewModel: CalendarViewModel = viewModel()
    val contentViewModel: ContentViewModel = viewModel()
    val friendsViewModel: FriendsViewModel = viewModel()
    val friendResults by friendsViewModel.results.collectAsState()
    val friendshipDtos by friendsViewModel.friendships.collectAsState()
    val friendAvailability by friendsViewModel.availability.collectAsState()
    val friendMessage by friendsViewModel.message.collectAsState()
    val inviteItems by invitesViewModel.items.collectAsState()
    val inviteMessage by invitesViewModel.message.collectAsState()
    val matchOptions by invitesViewModel.options.collectAsState()
    val currentUserId = (authState as? AuthState.LoggedIn)?.session?.user?.id?.toIntOrNull()
    val friends = friendshipDtos.filter { it.status == "ACCEPTED" }.map { FriendSummary(it.friend.id.toString(), it.friend.displayName, "可邀约") }
    val pendingInvites = inviteItems.filter { it.status == "PENDING" }.map { item -> PendingInvite(item.id.toString(), item.title, "${item.startAt.replace('T', ' ')} — ${item.endAt.substringAfter('T')}", if (item.senderId == currentUserId) "我" else "好友") }
    val notices = contentViewModel.notices
    val groups = contentViewModel.groups
    val remoteEvents by calendarViewModel.eventsState.collectAsState()
    LaunchedEffect(accountScope) { calendarViewModel.refresh() }
    LaunchedEffect(accountScope) { friendsViewModel.refresh() }
    LaunchedEffect(accountScope) { invitesViewModel.refresh() }
    var page by remember { mutableStateOf("日程") }
    var showCreate by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var createDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var lastBackPressAt by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = authState is AuthState.LoggedIn) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressAt <= 2000L) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackPressAt = now
            Toast.makeText(context, "再按一次退出应用", Toast.LENGTH_SHORT).show()
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(containerColor = Bg, bottomBar = { BottomNav(page) { page = it } }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    "找时间" -> MatchPage(pendingInvites, friends, friendResults, friendshipDtos, friendAvailability, currentUserId, onSearchFriends = friendsViewModel::search, onClearSearch = friendsViewModel::clearSearch, onAddFriend = friendsViewModel::add, onRespondFriend = friendsViewModel::respond, onDeleteFriend = friendsViewModel::remove, onViewAvailability = friendsViewModel::loadAvailability, onStartInvite = { invitesViewModel.clearOptions(); showInvite = true }, onRespond = { pending, status -> inviteItems.firstOrNull { it.id.toString() == pending.id }?.let { item -> invitesViewModel.respond(item.id, status) { accepted -> calendarViewModel.saveEvent(CalendarEvent("invite-${accepted.id}", accepted.receiverId.toString(), accepted.title, accepted.startAt.replace('T', ' '), accepted.endAt.replace('T', ' '), "邀约", EventStatus.HARD)) } } })
                    "群组" -> GroupPage(groups)
                    "通知" -> NoticePage(notices)
                    "我的" -> SettingsPage(
                        user = (authState as? AuthState.LoggedIn)?.session?.user,
                        themeChoice = themeChoice,
                        onThemeChange = { themeChoice = it; ThemePreference.save(context, it, accountScope) },
                        onLogin = { showAuth = true },
                        onUpdateProfile = authViewModel::updateProfile,
                        onLogout = authViewModel::logout
                    )
                    else -> CalendarPage(remoteEvents, onAdd = { date -> createDate = date; showCreate = true }, onEdit = { editingEvent = it })
                }
            }
        }
        if (showCreate) CreateEventDialog(
            date = createDate,
            existingEvents = remoteEvents,
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
        if (showInvite) InviteDialog(friends, matchOptions, onScan = { receiverId, duration, startDate, endDate, startTime, endTime -> invitesViewModel.match(receiverId, duration, startDate, endDate, startTime, endTime) }, onSend = { receiverId, title, start, end -> invitesViewModel.create(receiverId, title, start, end); showInvite = false }, onClose = { showInvite = false })
        friendMessage?.let { message ->
            AlertDialog(onDismissRequest = friendsViewModel::clearMessage, containerColor = Panel, title = { Text("好友") }, text = { Text(message, color = Muted) }, confirmButton = { TextButton(onClick = friendsViewModel::clearMessage) { Text("知道了", color = Coral) } })
        }
        inviteMessage?.let { message ->
            AlertDialog(onDismissRequest = invitesViewModel::clearMessage, containerColor = Panel, title = { Text("邀约") }, text = { Text(message, color = Muted) }, confirmButton = { TextButton(onClick = invitesViewModel::clearMessage) { Text("知道了", color = Coral) } })
        }
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
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
fun CalendarPage(events: List<CalendarEvent>, onAdd: (String) -> Unit, onEdit: (CalendarEvent) -> Unit) {
    var mode by remember { mutableStateOf(CalendarMode.MONTH) }
    val today = remember { LocalDate.now() }
    var year by remember { mutableStateOf(today.year) }
    var month by remember { mutableStateOf(today.monthValue) }
    var selectedDay by remember { mutableStateOf(today.dayOfMonth) }
    val safeSelectedDay = selectedDay.coerceIn(1, YearMonth.of(year, month).lengthOfMonth())
    LaunchedEffect(year, month) {
        if (selectedDay != safeSelectedDay) selectedDay = safeSelectedDay
    }
    val pageScroll = rememberScrollState()
    val pageModifier = Modifier
        .fillMaxSize()
        .padding(18.dp)
        .then(if (mode == CalendarMode.MONTH || mode == CalendarMode.DAY) Modifier.verticalScroll(pageScroll) else Modifier)
        .pointerInput(mode) {
            var totalDrag = 0f
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    totalDrag += dragAmount
                },
                onDragEnd = {
                    if (kotlin.math.abs(totalDrag) > 100f) {
                        mode = when {
                            totalDrag < 0 && mode == CalendarMode.MONTH -> CalendarMode.WEEK
                            totalDrag < 0 && mode == CalendarMode.WEEK -> CalendarMode.DAY
                            totalDrag > 0 && mode == CalendarMode.DAY -> CalendarMode.WEEK
                            totalDrag > 0 && mode == CalendarMode.WEEK -> CalendarMode.MONTH
                            else -> mode
                        }
                    }
                }
            )
        }
    Column(pageModifier) {
        Header("我的时间 · ${year}年${month}月", "日程", "＋ 创建日程", { onAdd("%04d-%02d-%02d".format(year, month, safeSelectedDay)) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = MainText, fontSize = 26.sp, modifier = Modifier.clickable { month--; if (month == 0) { month = 12; year-- }; selectedDay = selectedDay.coerceAtMost(YearMonth.of(year, month).lengthOfMonth()) })
            Text("${year}年${month}月", color = MainText, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 12.dp).weight(1f))
            Text("›", color = MainText, fontSize = 26.sp, modifier = Modifier.clickable { month++; if (month == 13) { month = 1; year++ }; selectedDay = selectedDay.coerceAtMost(YearMonth.of(year, month).lengthOfMonth()) })
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(CalendarMode.MONTH to "月", CalendarMode.WEEK to "周", CalendarMode.DAY to "日", CalendarMode.AGENDA to "日程").forEach { (item, label) ->
                FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(label) }, colors = tempoFilterChipColors())
            }
        }
        Spacer(Modifier.height(14.dp))
        AnimatedContent(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (mode == CalendarMode.WEEK) Modifier.weight(1f) else Modifier),
            targetState = mode,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                if (forward) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "calendar-view-transition"
        ) { currentMode ->
            when (currentMode) {
                CalendarMode.MONTH -> MonthView(year, month, safeSelectedDay, events, onEdit, onSelect = { selectedDay = it }, onDoubleSelect = { selectedDay = it; mode = CalendarMode.DAY })
                CalendarMode.WEEK -> WeekView(year, month, safeSelectedDay, events, onEdit) { selectedDate -> year = selectedDate.year; month = selectedDate.monthValue; selectedDay = selectedDate.dayOfMonth }
                CalendarMode.DAY -> DayView(year, month, safeSelectedDay, events, onEdit)
                CalendarMode.AGENDA -> AgendaView(events, onEdit)
            }
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
                        var lastTapAt by remember(year, month, day) { mutableStateOf(0L) }
                        Box(Modifier.weight(1f).height(58.dp).padding(3.dp).clickable(enabled = validDay) {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastTapAt in 1..320) onDoubleSelect(day) else onSelect(day)
                            lastTapAt = now
                        }) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (validDay) {
                                    Surface(color = if (selected) Coral else Color.Transparent, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(width = 43.dp, height = 48.dp)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Text("$day", color = if (selected) Color.White else MainText, fontWeight = FontWeight.Bold)
                                            Text(lunarLabel(year, month, day), color = if (selected) Color.White else Muted, fontSize = 9.sp)
                                        }
                                    }
                                    val prefix = "%04d-%02d-%02d".format(year, month, day)
                                    val dayEvents = events.filter { it.start.startsWith(prefix) }
                                    if (dayEvents.isNotEmpty()) Row { dayEvents.take(3).forEach { event -> Box(Modifier.size(5.dp).clip(CircleShape).background(eventColor(event.status))); Spacer(Modifier.width(3.dp)) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekView(year: Int, month: Int, selectedDay: Int, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit, onSelect: (LocalDate) -> Unit) {
    val selectedDate = LocalDate.of(year, month, selectedDay)
    // Java time 的 dayOfWeek.value 为周一=1、周日=7；周视图统一从周一开始。
    val weekStart = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val currentWeekStart = LocalDate.now().let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
    val weekTitle = if (weekStart == currentWeekStart) {
        "本周"
    } else {
        "${weekStart.monthValue}月${weekStart.dayOfMonth}日–${days.last().monthValue}月${days.last().dayOfMonth}日"
    }
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("${weekStart.year}年${weekStart.monthValue}月${weekStart.dayOfMonth}日 · $weekTitle", color = MainText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    days.forEach { date ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp).clickable { onSelect(date) }) {
                            Text(weekdayFor(date.year, date.monthValue, date.dayOfMonth), color = Muted, fontSize = 11.sp)
                            Spacer(Modifier.height(5.dp))
                            Surface(color = if (date == selectedDate) Coral else Color.Transparent, shape = RoundedCornerShape(13.dp), modifier = Modifier.size(width = 43.dp, height = 48.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text("${date.dayOfMonth}", color = if (date == selectedDate) Color.White else MainText, fontWeight = FontWeight.Bold)
                                    Text(lunarLabel(date.year, date.monthValue, date.dayOfMonth), color = if (date == selectedDate) Color.White else Muted, fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        WeekScheduleGrid(days, events, onEdit, Modifier.weight(1f))
    }
}

@Composable
fun WeekScheduleGrid(days: List<LocalDate>, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit, modifier: Modifier = Modifier) {
    val gridBorder = ThemeBorder.copy(alpha = .7f)

    Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gridHeight = (maxHeight - 16.dp).coerceAtLeast(1.dp)
            val hourHeight = (gridHeight.value / 24f).dp
            Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(gridHeight)
                    .padding(horizontal = 8.dp)
            ) {
                days.forEach { date ->
                    val dayEvents = events.filter { it.start.startsWith(date.toString()) }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(gridHeight)
                            .clipToBounds()
                            .border(BorderStroke(1.dp, gridBorder))
                    ) {
                        Column {
                            repeat(24) {
                                Box(
                                    Modifier
                                        .height(hourHeight)
                                        .fillMaxWidth()
                                        .border(BorderStroke(.5.dp, gridBorder))
                                )
                            }
                        }
                        dayEvents.forEach { event ->
                            val startMinutes = eventTimeMinutes(event.start).coerceIn(0, 1440)
                            val endMinutes = eventTimeMinutes(event.end).coerceIn(startMinutes, 1440)
                            val gridHeightValue = gridHeight.value
                            val startOffset = (gridHeightValue * startMinutes / 1440f).dp
                            val durationHeight = (gridHeightValue * (endMinutes - startMinutes) / 1440f).dp
                            Surface(
                                color = categoryColorForName(event.category).copy(alpha = .92f),
                                contentColor = Color.White,
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(durationHeight)
                                    .offset(y = startOffset)
                                    .padding(2.dp)
                                    .clickable { onEdit(event) }
                            ) {
                                Text(
                                    event.title,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth().padding(3.dp)
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

fun eventTimeMinutes(value: String): Int {
    val time = value.substringAfter(" ", value)
    val parts = time.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

@Composable fun DayView(year: Int, month: Int, day: Int, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit) {
    val safeDate = runCatching { LocalDate.of(year, month, day) }.getOrElse { LocalDate.now() }
    val safeYear = safeDate.year
    val safeMonth = safeDate.monthValue
    val safeDay = safeDate.dayOfMonth
    Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("${safeYear}年${safeMonth}月${safeDay}日", color = MainText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("${lunarLabel(safeYear, safeMonth, safeDay)} · ${weekdayFor(safeYear, safeMonth, safeDay)} · ${constellationFor(safeMonth, safeDay)}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Text(if (safeMonth == 7 && safeDay == 14) "丙午年 六月初一 · 今日无节气" else "农历日期 · 二十四节气信息待同步", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            HorizontalDivider(color = Color(0xFF303236), modifier = Modifier.padding(vertical = 16.dp))
            Text("日程安排", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val prefix = "%04d-%02d-%02d".format(safeYear, safeMonth, safeDay)
            val dayEvents = events.filter { it.start.startsWith(prefix) }
            if (dayEvents.isEmpty()) Text("这一天还没有日程", color = Muted, modifier = Modifier.padding(top = 20.dp))
            dayEvents.forEach { event ->
                val startText = event.start.substringAfter(" ", "--:--")
                val endText = event.end.substringAfter(" ", "--:--")
                TimeEvent("$startText — $endText", event.title, "${event.category} · ${event.status}", eventColor(event.status), categoryColor = categoryColorForName(event.category), onClick = { onEdit(event) })
            }
        }
    }
}

fun weekdayFor(year: Int, month: Int, day: Int): String = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[LocalDate.of(year, month, day).dayOfWeek.value - 1]
fun constellationFor(month: Int, day: Int): String = when { month == 7 && day < 23 -> "巨蟹座"; month == 7 -> "狮子座"; else -> "星座待计算" }
fun lunarLabel(year: Int, month: Int, day: Int): String {
    val names = listOf("初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十", "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十", "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十")
    if (year == 2026 && month == 7) {
        val lunarDay = if (day >= 14) names[(day - 14).coerceIn(0, 29)] else names[(day + 16).coerceIn(0, 29)]
        return if (lunarDay == "初一") (if (day >= 14) "六月" else "五月") else lunarDay
    }
    val lunarDay = names[(day - 1).coerceIn(0, 29)]
    return if (lunarDay == "初一") "本月" else lunarDay
}
@Composable fun AgendaView(events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit) { LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(events) { event -> Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable { onEdit(event) }) { Text("${event.start} · ${event.title}", color = MainText, modifier = Modifier.fillMaxWidth().padding(18.dp)) } } } }

@Composable fun DayScheduleCard(year: Int, month: Int, day: Int, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit) { Surface(color = Panel, shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp)) { Text("${year}年${month}月${day}日", color = Muted, fontSize = 12.sp); Text("这一天的日程", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold); val prefix = "%04d-%02d-%02d".format(year, month, day); val dayEvents = events.filter { it.start.startsWith(prefix) }; if (dayEvents.isEmpty()) Text("暂无日程，点击右上角创建", color = Muted, modifier = Modifier.padding(top = 14.dp)); dayEvents.forEach { event -> TimeEvent("${event.start.substringAfter(" ")} — ${event.end.substringAfter(" ")}", event.title, event.category, eventColor(event.status), categoryColor = categoryColorForName(event.category), onClick = { onEdit(event) }) } } } }
fun eventColor(status: EventStatus): Color = when (status) { EventStatus.HARD -> Red; EventStatus.FREE -> Green; EventStatus.FLEXIBLE -> Yellow; EventStatus.PENDING -> Blue }

@Composable fun TimeEvent(time: String, title: String, detail: String, color: Color, categoryColor: Color? = null, onClick: (() -> Unit)? = null) { Row(Modifier.fillMaxWidth().padding(top = 14.dp).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier), verticalAlignment = Alignment.Top) { Text(time, color = Muted, fontSize = 11.sp, modifier = Modifier.width(92.dp)); Surface(color = color.copy(alpha = .14f), shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp), modifier = Modifier.weight(1f).border(1.dp, color.copy(alpha = .7f), RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))) { Column(Modifier.padding(9.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { categoryColor?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it)); Spacer(Modifier.width(6.dp)) }; Text(title, color = MainText, fontWeight = FontWeight.Bold, fontSize = 12.sp) }; Text(detail, color = Muted, fontSize = 10.sp) } } } }

@Composable
fun MatchPage(invites: List<PendingInvite>, friends: List<FriendSummary>, searchResults: List<com.hutong.calendar.data.FriendUserDto>, friendships: List<com.hutong.calendar.data.FriendshipDto>, availability: List<com.hutong.calendar.data.AvailabilityBlockDto>, currentUserId: Int?, onSearchFriends: (String) -> Unit, onClearSearch: () -> Unit, onAddFriend: (com.hutong.calendar.data.FriendUserDto) -> Unit, onRespondFriend: (Int, String) -> Unit, onDeleteFriend: (Int) -> Unit, onViewAvailability: (Int, String) -> Unit, onStartInvite: () -> Unit, onRespond: (PendingInvite, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var viewingFriend by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Header("一起安排，不用来回问", "找时间", if (friends.isNotEmpty()) "＋ 发起邀约" else null, onStartInvite)
        Surface(color = Card, shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(20.dp)) { Text("智能匹配", color = Yellow, fontSize = 12.sp); Text("谁的时间，刚好和你重合？", color = MainText, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp)); Text("先扫描双方完全空闲，再询问是否纳入机动尾巴。", color = Muted, fontSize = 12.sp) } }
        Text("待应答邀约  ${invites.size}", color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 25.dp, bottom = 12.dp))
        if (invites.isEmpty()) Text("暂时没有待应答邀约", color = Muted, modifier = Modifier.padding(vertical = 20.dp))
        invites.forEach { invite ->
            InviteCard(invite, isSender = invite.inviter == "我", onCancel = { onRespond(invite, "CANCELLED") }, onDecline = { onRespond(invite, "DECLINED") }, onAccept = { onRespond(invite, "ACCEPTED") })
            Spacer(Modifier.height(12.dp))
        }
        Text("添加好友", color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 25.dp, bottom = 12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("用户名或昵称") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp)); Button(onClick = { onSearchFriends(query) }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("搜索") }
            if (query.isNotBlank()) { Spacer(Modifier.width(4.dp)); TextButton(onClick = { query = ""; onClearSearch() }) { Text("取消", color = Muted) } }
        }
        if (query.isNotBlank() && searchResults.isEmpty()) Text("未找到联系人，请检查用户名或昵称", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        searchResults.forEach { result ->
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(result.displayName, color = MainText, fontWeight = FontWeight.Bold); Text(result.username, color = Muted, fontSize = 11.sp) }
                    TextButton(onClick = { onAddFriend(result) }) { Text("添加", color = Coral) }
                }
            }
        }
        val incoming = friendships.filter { it.status == "PENDING" && it.friendId == currentUserId }
        if (incoming.isNotEmpty()) {
            Text("好友申请", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            incoming.forEach { request ->
                Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${request.friend.displayName} 请求添加你", color = MainText, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRespondFriend(request.id, "ACCEPTED") }) { Text("同意", color = Coral) }
                        TextButton(onClick = { onRespondFriend(request.id, "DECLINED") }) { Text("拒绝", color = Muted) }
                    }
                }
            }
        }
        Text("现有好友", color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 12.dp))
        friendships.filter { it.status == "ACCEPTED" }.forEach { friendship ->
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(friendship.friend.displayName, color = MainText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("可邀约", color = Muted, fontSize = 11.sp)
                    TextButton(onClick = { viewingFriend = friendship.friend.displayName; onViewAvailability(friendship.friend.id, LocalDate.now().toString()) }) { Text("看时间", color = Coral) }
                    TextButton(onClick = { onDeleteFriend(friendship.id) }) { Text("删除", color = Red) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (viewingFriend != null) {
        AlertDialog(
            onDismissRequest = { viewingFriend = null },
            containerColor = Panel,
            title = { Text("${viewingFriend}的时间状态") },
            text = {
                if (availability.isEmpty()) Text("本周暂时没有同步的状态色块。", color = Muted)
                else FriendWeekTable(availability)
            },
            confirmButton = { TextButton(onClick = { viewingFriend = null }) { Text("关闭", color = Coral) } }
        )
    }
}

@Composable
fun FriendWeekTable(blocks: List<com.hutong.calendar.data.AvailabilityBlockDto>) {
    val dates = (0..6).map { LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1 - it).toLong()).toString() }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(34.dp))
            dates.forEach { date -> Text(date.substring(5), color = MainText, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        (0..23).forEach { hour ->
            Row(Modifier.fillMaxWidth().height(23.dp)) {
                Text("%02d".format(hour), color = Muted, fontSize = 9.sp, modifier = Modifier.width(34.dp))
                dates.forEach { date ->
                    val block = blocks.firstOrNull { it.date == date && it.startTime.substringBefore(":").toIntOrNull()?.let { start -> hour >= start && hour < (it.endTime.substringBefore(":").toIntOrNull() ?: start) } == true }
                    val color = when (block?.status) { "HARD" -> Red; "FREE" -> Green; "FLEXIBLE" -> Yellow; else -> ThemeBorder.copy(alpha = .16f) }
                    Box(Modifier.weight(1f).fillMaxHeight().padding(1.dp).background(color).border(0.5.dp, ThemeBorder))
                }
            }
        }
    }
}

@Composable fun InviteCard(invite: PendingInvite, isSender: Boolean, onCancel: () -> Unit, onDecline: () -> Unit, onAccept: () -> Unit) { Surface(color = Panel, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).clip(CircleShape).background(Blue), contentAlignment = Alignment.Center) { Text(invite.inviter.take(1)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(if (isSender) "已向好友发起：${invite.title}" else "好友邀请你：${invite.title}", color = MainText, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(invite.time, color = Muted, fontSize = 11.sp) }; Text("待处理", color = Blue, fontSize = 11.sp) }; Text("活动时间不会锁定接收方。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (isSender) Button(onClick = onCancel, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("取消邀约") } else { OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("拒绝") }; Button(onClick = onAccept, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("同意") } } } } } }

@Composable fun GroupPage(groups: List<GroupSummary>) { SimplePage("一起参加，不被代表", "群组", *groups.flatMap { listOf(it.name, it.activity, it.detail) }.toTypedArray()) }
@Composable fun NoticePage(notices: List<NoticeItem>) { SimplePage("重要的事，及时知道", "通知", *notices.flatMap { listOf(it.title, it.detail) }.toTypedArray()) }
@Composable
fun SettingsPage(user: UserProfile?, themeChoice: ThemeChoice, onThemeChange: (ThemeChoice) -> Unit, onLogin: () -> Unit, onUpdateProfile: (String, String?, String?, String?) -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    var categories by remember(user?.id) { mutableStateOf(CategoryStore.load(context, user?.id ?: "guest")) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var logoutStep by remember { mutableStateOf(0) }
    var editedName by remember(user?.displayName) { mutableStateOf(user?.displayName.orEmpty()) }
    var editedPhone by remember(user?.phone) { mutableStateOf(user?.phone.orEmpty()) }
    var editedHobbies by remember(user?.hobbies) { mutableStateOf(user?.hobbies.orEmpty()) }
    var editedSignature by remember(user?.signature) { mutableStateOf(user?.signature.orEmpty()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Header("账户与偏好", "我的")
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (user == null) "游客模式" else user.displayName, color = MainText, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (user != null) TextButton(onClick = { editedName = user.displayName; editedPhone = user.phone.orEmpty(); editedHobbies = user.hobbies.orEmpty(); editedSignature = user.signature.orEmpty(); showProfileEditor = true }) { Text("编辑资料", color = Coral) }
                }
                Text(if (user == null) "未登录 · 日程仅保存在本机" else "账号 ID：${user.accountId}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                user?.email?.let { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                user?.phone?.takeIf { it.isNotBlank() }?.let { Text("电话：$it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                user?.hobbies?.takeIf { it.isNotBlank() }?.let { Text("爱好：$it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                user?.signature?.takeIf { it.isNotBlank() }?.let { Text("签名：$it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                if (user == null) Button(onClick = onLogin, modifier = Modifier.padding(top = 14.dp)) { Text("登录 / 注册") }
            }
        }
        Text("主题", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(ThemeChoice.DARK to "深色", ThemeChoice.LIGHT to "浅色", ThemeChoice.SYSTEM to "跟随系统").forEach { (choice, label) ->
                    FilterChip(selected = themeChoice == choice, onClick = { onThemeChange(choice) }, label = { Text(label) }, colors = tempoFilterChipColors(), modifier = Modifier.weight(1f))
                }
            }
        }
        val settings = listOf("日程和已同意邀约会同步到本机离线缓存", "连续忙碌健康提醒", "默认导入分类 · 工作")
        settings.forEach { line -> Surface(color = Panel, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(line, color = MainText, fontSize = 14.sp, modifier = Modifier.padding(17.dp)) } }
        Text("分类标签", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                categories.forEach { option ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(option.colorHex)))
                        Text(option.name, color = MainText, modifier = Modifier.padding(start = 9.dp).weight(1f))
                        TextButton(onClick = { categories = CategoryStore.remove(context, option.name) }) { Text("删除", color = Red) }
                    }
                }
            }
        }
        if (user != null) OutlinedButton(onClick = { logoutStep = 1 }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("退出登录") }
    }
    if (showProfileEditor && user != null) {
        AlertDialog(
            onDismissRequest = { showProfileEditor = false },
            containerColor = Panel,
            title = { Text("编辑个人资料") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(editedName, { editedName = it }, label = { Text("昵称") }, singleLine = true)
                    OutlinedTextField(editedPhone, { editedPhone = it }, label = { Text("电话（可选）") }, singleLine = true)
                    OutlinedTextField(editedHobbies, { editedHobbies = it }, label = { Text("爱好（可选）") }, singleLine = true)
                    OutlinedTextField(editedSignature, { editedSignature = it }, label = { Text("签名（可选）") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = editedName.trim()
                        if (name.isNotEmpty()) {
                            onUpdateProfile(name, editedPhone, editedHobbies, editedSignature)
                            showProfileEditor = false
                        }
                    },
                    enabled = editedName.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral)
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showProfileEditor = false }) { Text("取消") } }
        )
    }
    if (logoutStep == 1) {
        AlertDialog(
            onDismissRequest = { logoutStep = 0 },
            containerColor = Panel,
            title = { Text("确认退出登录？") },
            text = { Text("为防止误触，请再次确认。", color = Muted) },
            confirmButton = {
                Button(onClick = { logoutStep = 2 }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("继续退出") }
            },
            dismissButton = { TextButton(onClick = { logoutStep = 0 }) { Text("取消") } }
        )
    }
    if (logoutStep == 2) {
        AlertDialog(
            onDismissRequest = { logoutStep = 0 },
            containerColor = Panel,
            title = { Text("再次确认退出登录") },
            text = { Text("退出后仍可离线使用本机日历，联网功能需要重新登录。", color = Muted) },
            confirmButton = {
                Button(onClick = { logoutStep = 0; onLogout() }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("确认退出") }
            },
            dismissButton = { TextButton(onClick = { logoutStep = 0 }) { Text("取消") } }
        )
    }
}
@Composable fun SimplePage(eyebrow: String, title: String, vararg lines: String) { Column(Modifier.fillMaxSize().padding(18.dp)) { Header(eyebrow, title); lines.forEach { line -> Surface(color = Panel, shape = RoundedCornerShape(17.dp), modifier = Modifier.padding(bottom = 10.dp)) { Text(line, color = MainText, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(18.dp)) } } } }

@Composable fun BottomNav(current: String, onSelect: (String) -> Unit) { NavigationBar(containerColor = Panel) { listOf("日程" to "▣", "找时间" to "⌕", "群组" to "♧", "通知" to "♢", "我的" to "⚙").forEach { (name, icon) -> NavigationBarItem(selected = current == name, onClick = { onSelect(name) }, icon = { Text(icon, fontSize = 20.sp) }, label = { Text(name, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = Coral, indicatorColor = Coral, unselectedIconColor = Muted, unselectedTextColor = Muted)) } } }

@Composable
fun tempoFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Panel,
    labelColor = MainText,
    selectedContainerColor = Coral,
    selectedLabelColor = Color.White,
    selectedLeadingIconColor = Color.White,
    selectedTrailingIconColor = Color.White
)

@Composable
fun CreateEventDialog(date: String, existingEvents: List<CalendarEvent>, onSave: (CalendarEvent) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var category by remember {
        mutableStateOf(CategoryStore.load(context).firstOrNull()?.name ?: "工作")
    }
    var status by remember { mutableStateOf("硬性") }
    var start by remember { mutableStateOf<String?>(null) }
    var end by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onCancel, containerColor = Panel, title = { Text("安排一段时间", fontWeight = FontWeight.Bold) }, text = {
        Column {
            OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("活动名称") }, singleLine = true)
            Surface(color = Card, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ThemeBorder), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("日期：$date", color = MainText, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp))
            }
            TimeRangeButton(start, end, onClick = { showPicker = true })
            CategorySelector(category = category, onCategoryChange = { category = it })
            Text("对外状态", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("硬性", "空闲", "机动").forEach { item -> FilterChip(selected = status == item, onClick = { status = item }, label = { Text(item) }, colors = tempoFilterChipColors()) } }
        }
    }, confirmButton = { Button(onClick = {
        val finalName = name.ifBlank { "未命名日程" }
        val eventStatus = when (status) { "空闲" -> EventStatus.FREE; "机动" -> EventStatus.FLEXIBLE; else -> EventStatus.HARD }
        if (start != null && end != null) onSave(CalendarEvent("local-${UUID.randomUUID()}", "me", finalName, "$date ${start!!}", "$date ${end!!}", category, eventStatus, if (eventStatus == EventStatus.FLEXIBLE) 30 else 0))
    }, colors = ButtonDefaults.buttonColors(containerColor = Coral), enabled = start != null && end != null) { Text("保存日程") } }, dismissButton = { TextButton(onClick = onCancel) { Text("取消") } })
    if (showPicker) TimeGridDialog(initialStart = start, initialEnd = end, date = date, existingEvents = existingEvents, onCancel = { showPicker = false }, onSelect = { s, e -> start = s; end = e; showPicker = false })
}

@Composable
fun EventEditorDialog(event: CalendarEvent, onSave: (CalendarEvent) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    var name by remember(event.id) { mutableStateOf(event.title) }
    var category by remember(event.id) { mutableStateOf(event.category) }
    var status by remember(event.id) { mutableStateOf(event.status) }
    var start by remember(event.id) { mutableStateOf(event.start.substringAfter(" ")) }
    var end by remember(event.id) { mutableStateOf(event.end.substringAfter(" ")) }
    var showPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val statusOptions = listOf(EventStatus.HARD to "硬性", EventStatus.FREE to "空闲", EventStatus.FLEXIBLE to "机动")

    AlertDialog(onDismissRequest = onCancel, containerColor = Panel, title = { Text("编辑日程", fontWeight = FontWeight.Bold) }, text = {
        Column {
            OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("活动名称") }, singleLine = true)
            Surface(color = Card, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ThemeBorder), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("日期：${event.start.substringBefore(" ")}", color = MainText, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp))
            }
            TimeRangeButton(start, end, onClick = { showPicker = true })
            CategorySelector(category = category, onCategoryChange = { category = it })
            Text("对外状态", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { statusOptions.forEach { (value, label) -> FilterChip(selected = status == value, onClick = { status = value }, label = { Text(label) }, colors = tempoFilterChipColors()) } }
            TextButton(onClick = { showDeleteConfirm = true }, colors = ButtonDefaults.textButtonColors(contentColor = Red), modifier = Modifier.padding(top = 10.dp)) { Text("删除这个日程") }
        }
    }, confirmButton = { Button(onClick = {
        onSave(event.copy(title = name.ifBlank { "未命名日程" }, category = category, start = "${event.start.substringBefore(" ")} $start", end = "${event.end.substringBefore(" ")} $end", status = status, flexibleTailMinutes = if (status == EventStatus.FLEXIBLE) 30 else 0))
    }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("保存修改") } }, dismissButton = { TextButton(onClick = onCancel) { Text("取消") } })
    if (showPicker) TimeGridDialog(initialStart = start, initialEnd = end, onCancel = { showPicker = false }, onSelect = { s, e -> start = s; end = e; showPicker = false })
    if (showDeleteConfirm) AlertDialog(onDismissRequest = { showDeleteConfirm = false }, containerColor = Panel, title = { Text("删除日程？") }, text = { Text("删除后，这个日程将从当前设备移除。若它已经关联邀约，后续需要通知参与者。", color = Muted) }, confirmButton = { Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("确认删除") } }, dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } })
}

@Composable
fun TimeRangeButton(start: String?, end: String?, onClick: () -> Unit) {
    val selected = start != null && end != null
    val emptyBackground = if (currentPalette == DarkPalette) Card else Color.White
    val emptyText = if (currentPalette == DarkPalette) MainText else Color.Black
    Surface(
        color = if (selected) Coral else emptyBackground,
        contentColor = if (selected) Color.White else emptyText,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 8.dp).border(1.dp, ThemeBorder, RoundedCornerShape(10.dp)).clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (selected) "$start — $end" else "点击选择15min时间段", color = if (selected) Color.White else emptyText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun CategorySelector(category: String, onCategoryChange: (String) -> Unit) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(CategoryStore.load(context)) }
    var customName by remember { mutableStateOf("") }
    Text("个人分类标签", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = category == option.name,
                onClick = { onCategoryChange(option.name) },
                label = { Text(option.name) },
                leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColor(option.colorHex))) },
                colors = tempoFilterChipColors()
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = customName,
            onValueChange = { customName = it },
            placeholder = { Text("新增自定义标签") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = {
            val name = customName.trim()
            if (name.isNotEmpty()) {
                val colors = listOf("#6B8FD6", "#6BB58A", "#C58BD8", "#D69A5A", "#D9798A")
                options = CategoryStore.add(context, CategoryOption(name, colors[options.size % colors.size]))
                onCategoryChange(name)
                customName = ""
            }
        }) { Text("添加") }
    }
}

fun categoryColor(hex: String): Color = runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(Coral)
@Composable
fun categoryColorForName(name: String): Color {
    val option = CategoryStore.load(LocalContext.current).firstOrNull { it.name == name }
    return option?.let { categoryColor(it.colorHex) } ?: Coral
}

@Composable
fun TimeGridDialog(initialStart: String?, initialEnd: String?, date: String? = null, existingEvents: List<CalendarEvent> = emptyList(), onCancel: () -> Unit, onSelect: (String, String) -> Unit) {
    var selectedStart by remember(initialStart) { mutableStateOf<String?>(initialStart) }
    var selectedEnd by remember(initialEnd) { mutableStateOf<String?>(initialEnd) }
    val slots = (0 until 97).map { minutes ->
        val total = minutes * 15
        "%02d:%02d".format(total / 60, total % 60)
    }
    AlertDialog(onDismissRequest = onCancel, containerColor = Panel, title = { Text("选择时间段", fontWeight = FontWeight.Bold) }, text = {
        Column {
            Text(if (selectedStart == null) "先点击起始时间" else if (selectedEnd == null) "再点击结束时间" else "已选择 $selectedStart — $selectedEnd", color = Muted, fontSize = 12.sp)
            if (date != null && existingEvents.any { it.start.startsWith("$date ") }) {
                Text("灰色区域表示已有日程，但仍允许重复选择", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.height(330.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(slots) { time ->
                    val active = selectedStart != null && selectedEnd != null && time >= selectedStart!! && time < selectedEnd!!
                    val occupied = date != null && existingEvents.any { event ->
                        event.start.startsWith("$date ") && time >= event.start.substringAfter(" ") && time < event.end.substringAfter(" ")
                    }
                    val highlighted = active || time == selectedStart || time == selectedEnd
                    Surface(color = if (highlighted) Coral.copy(alpha = .3f) else if (occupied) ThemeBorder.copy(alpha = .35f) else Card, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, if (occupied && !highlighted) ThemeBorder else Color.Transparent, RoundedCornerShape(8.dp)).clickable {
                        when { selectedStart == null -> selectedStart = time; selectedEnd == null && time > selectedStart!! -> selectedEnd = time; else -> { selectedStart = time; selectedEnd = null } }
                    }) { Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Text(time, color = if (highlighted) Color.White else MainText); if (occupied && !highlighted) { Spacer(Modifier.weight(1f)); Text("已有日程", color = Muted, fontSize = 10.sp) } } }
                }
            }
        }
    }, confirmButton = { Button(onClick = { if (selectedStart != null && selectedEnd != null) onSelect(selectedStart!!, selectedEnd!!) }, colors = ButtonDefaults.buttonColors(containerColor = Coral), enabled = selectedStart != null && selectedEnd != null) { Text("确定") } }, dismissButton = { TextButton(onClick = onCancel) { Text("取消") } })
}

@Composable
fun InviteDialog(friends: List<FriendSummary>, candidateOptions: List<com.hutong.calendar.data.MatchOptionDto>, onScan: (Int, Int, String?, String?, String?, String?) -> Unit, onSend: (Int, String, String, String) -> Unit, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var friend by remember { mutableStateOf(friends.firstOrNull()) }
    var scanned by remember { mutableStateOf(false) }
    var selectedSlot by remember { mutableStateOf<com.hutong.calendar.data.MatchOptionDto?>(null) }
    var durationSlots by remember { mutableStateOf(4) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var showWindowPicker by remember { mutableStateOf(false) }
    var windowStartDate by remember { mutableStateOf<String?>(null) }
    var windowEndDate by remember { mutableStateOf<String?>(null) }
    var windowStartTime by remember { mutableStateOf<String?>(null) }
    var windowEndTime by remember { mutableStateOf<String?>(null) }
    val durationOptions = (1..32).toList()
    fun durationLabel(slots: Int): String {
        val minutes = slots * 15
        return if (minutes < 60) "${minutes} 分钟" else "${minutes / 60} 小时${if (minutes % 60 == 0) "" else " ${minutes % 60} 分钟"}"
    }
    AlertDialog(onDismissRequest = onClose, containerColor = Panel, title = { Text("发起邀约", fontWeight = FontWeight.Bold) }, text = {
        Column {
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("活动名称，例如：晚餐") }, singleLine = true)
            Text("选择好友", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                friends.forEach { item ->
                    FilterChip(selected = friend?.id == item.id, onClick = { friend = item; scanned = false; selectedSlot = null }, label = { Text(item.name) }, colors = tempoFilterChipColors())
                }
            }
            Text("活动持续时间", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Text("系统会在未来 7 天内比较双方的多个空闲时间段，再生成可选方案。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            OutlinedButton(onClick = { showDurationPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(durationLabel(durationSlots))
            }
            Text("可约日期和时间范围", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(onClick = { showWindowPicker = true; scanned = false; selectedSlot = null }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(if (windowStartDate == null || windowEndDate == null) "不限制：扫描未来 7 天全天" else "已框选：$windowStartDate 至 $windowEndDate · $windowStartTime—$windowEndTime")
            }
            if (scanned) {
                Text("可用时间段", color = MainText, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                if (candidateOptions.isEmpty()) Text("没有找到满足条件的时间段", color = Muted, fontSize = 12.sp)
                candidateOptions.forEach { slot ->
                    val label = "${if (slot.matchType == "PURE_GREEN") "双方空闲" else "包含机动"} · ${slot.startAt.replace('T', ' ')} — ${slot.endAt.substringAfter('T')}"
                    FilterChip(selected = selectedSlot == slot, onClick = { selectedSlot = slot }, label = { Text(label) }, colors = tempoFilterChipColors(), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
                }
            }
        }
    }, confirmButton = { Button(onClick = { if (!scanned) { if (friend != null) { onScan(friend!!.id.toInt(), durationSlots * 15, windowStartDate, windowEndDate, windowStartTime, windowEndTime); scanned = true } } else if (selectedSlot != null && friend != null) onSend(friend!!.id.toInt(), title.ifBlank { "未命名邀约" }, selectedSlot!!.startAt, selectedSlot!!.endAt) }, enabled = if (!scanned) friend != null else selectedSlot != null && friend != null, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text(if (scanned) "发送邀约" else "扫描可用时间") } }, dismissButton = { TextButton(onClick = onClose) { Text("取消") } })
    if (showDurationPicker) {
        AlertDialog(
            onDismissRequest = { showDurationPicker = false },
            containerColor = Panel,
            title = { Text("选择活动时长") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("请选择活动持续时间", color = Muted, fontSize = 12.sp)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(vertical = 78.dp)
                    ) {
                        items(durationOptions) { slots ->
                            val selected = slots == durationSlots
                            Box(
                                modifier = Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp)).clickable {
                                    durationSlots = slots
                                    scanned = false
                                    selectedSlot = null
                                }.background(if (selected) Coral else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    durationLabel(slots),
                                    color = if (selected) Color.White else MainText,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showDurationPicker = false }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("确定") } }
        )
    }
    if (showWindowPicker) {
        WeekWindowPicker(
            onCancel = { showWindowPicker = false },
            onSelected = { startDate, endDate, startTime, endTime -> windowStartDate = startDate; windowEndDate = endDate; windowStartTime = startTime; windowEndTime = endTime; showWindowPicker = false }
        )
    }
}

@Composable
private fun WeekWindowPicker(onCancel: () -> Unit, onSelected: (String, String, String, String) -> Unit) {
    var first by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var second by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val today = LocalDate.now()
    val dates = (0..6).map { today.plusDays(it.toLong()) }
    val normalized = if (first != null && second != null) {
        val a = first!!; val b = second!!
        Pair(minOf(a.first, b.first) to minOf(a.second, b.second), maxOf(a.first, b.first) to maxOf(a.second, b.second))
    } else null
    fun choose(day: Int, hour: Int) {
        if (first == null || second != null) { first = day to hour; second = null } else second = day to hour
    }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Panel,
        title = { Text("框选可约范围") },
        text = {
            Column {
                Text("先点左上角，再点右下角；系统只在框选范围内匹配。", color = Muted, fontSize = 11.sp)
                Row(Modifier.padding(top = 10.dp)) {
                    Text("时间", color = Muted, fontSize = 10.sp, modifier = Modifier.width(42.dp))
                    dates.forEach { date -> Text("${date.monthValue}/${date.dayOfMonth}", color = MainText, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
                }
                LazyColumn(Modifier.height(430.dp).padding(top = 4.dp)) {
                    items(24) { hour ->
                        Row(Modifier.height(28.dp)) {
                            Text("%02d:00".format(hour), color = Muted, fontSize = 9.sp, modifier = Modifier.width(42.dp).align(Alignment.CenterVertically))
                            dates.indices.forEach { day ->
                                val selected = normalized?.let {
                                    day in it.first.first..it.second.first && hour in it.first.second..it.second.second
                                } == true
                                Box(Modifier.weight(1f).fillMaxHeight().padding(1.dp).clip(RoundedCornerShape(3.dp)).background(if (selected) Coral else Card).border(1.dp, ThemeBorder, RoundedCornerShape(3.dp)).clickable { choose(day, hour) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { normalized?.let { range ->
            onSelected(
                dates[range.first.first].toString(),
                dates[range.second.first].toString(),
                "%02d:00".format(range.first.second),
                "%02d:00".format((range.second.second + 1).coerceAtMost(24))
            )
        } }, enabled = normalized != null, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("确定") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } }
    )
}
