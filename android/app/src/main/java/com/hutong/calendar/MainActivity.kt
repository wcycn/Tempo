package com.hutong.calendar

import android.Manifest
import android.os.Bundle
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hutong.calendar.data.CalendarEvent
import com.hutong.calendar.data.CategoryOption
import com.hutong.calendar.data.CategoryStore
import com.hutong.calendar.data.EventStatus
import com.hutong.calendar.data.FriendSummary
import com.hutong.calendar.data.GroupSummary
import com.hutong.calendar.data.PendingInvite
import com.hutong.calendar.data.AcceptedInvite
import com.hutong.calendar.data.ThemeChoice
import com.hutong.calendar.data.ThemePreference
import com.hutong.calendar.data.UserProfile
import java.util.UUID
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import java.time.YearMonth
import java.time.LocalDate
import java.time.LocalDateTime

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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1001)
        }
    }
}

@Composable
fun HutongApp() {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel()
    val invitesViewModel: InvitesViewModel = viewModel()
    val aiVoiceViewModel: AiVoiceViewModel = viewModel()
    val authState by authViewModel.state.collectAsState()
    val authMessage by authViewModel.message.collectAsState()
    val aiVoiceState by aiVoiceViewModel.state.collectAsState()
    val aiAccessState by aiVoiceViewModel.access.collectAsState()
    LaunchedEffect(authMessage) {
        authMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            authViewModel.clearMessage()
        }
    }
    val accountScope = (authState as? AuthState.LoggedIn)?.session?.user?.id ?: "guest"
    var themeChoice by remember(accountScope) { mutableStateOf(ThemePreference.load(context, accountScope)) }
    val systemDark = isSystemInDarkTheme()
    val microphoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) aiVoiceViewModel.startRecording()
    }
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
    val friendsLoading by friendsViewModel.loading.collectAsState()
    val inviteItems by invitesViewModel.items.collectAsState()
    val inviteMessage by invitesViewModel.message.collectAsState()
    val invitesLoading by invitesViewModel.loading.collectAsState()
    val matchOptions by invitesViewModel.options.collectAsState()
    val currentUserId = (authState as? AuthState.LoggedIn)?.session?.user?.id?.toIntOrNull()
    val friends = friendshipDtos.filter { it.status == "ACCEPTED" }.map { FriendSummary(it.friend.id.toString(), it.friend.displayName, "可邀约") }
    val pendingInvites = inviteItems.filter { it.status == "PENDING" }.map { item ->
        PendingInvite(
            id = item.id.toString(),
            title = item.title,
            time = "${item.startAt.replace('T', ' ')} — ${item.endAt.substringAfter('T')}",
            inviter = item.senderDisplayName ?: if (item.senderId == currentUserId) "我" else "好友",
            receiver = item.receiverDisplayName ?: if (item.receiverId == currentUserId) "我" else "好友",
            description = item.description
        )
    }
    val acceptedInvites = inviteItems.filter { it.status == "ACCEPTED" }.map { item ->
        val counterpartId = if (item.senderId == currentUserId) item.receiverId else item.senderId
        val counterpart = if (item.senderId == currentUserId) item.receiverDisplayName else item.senderDisplayName
        val counterpartName = counterpart ?: friends.firstOrNull { it.id == counterpartId.toString() }?.name ?: "好友"
        AcceptedInvite(item.id.toString(), item.title, "${item.startAt.replace('T', ' ')} — ${item.endAt.substringAfter('T')}", counterpartName, item.startAt, item.endAt, item.description)
    }.sortedBy { it.startAt }
    val groups = contentViewModel.groups
    val remoteEvents by calendarViewModel.eventsState.collectAsState()
    val calendarLoading by calendarViewModel.loading.collectAsState()
    val calendarError by calendarViewModel.error.collectAsState()
    var page by remember { mutableStateOf("日程") }
    LaunchedEffect(accountScope) { calendarViewModel.refresh() }
    LaunchedEffect(accountScope, page) {
        if (page == "找时间") {
            friendsViewModel.refresh()
            invitesViewModel.refresh()
        } else if (page == "日程") {
            // 日程页只做后台邀约同步，不阻塞本地日历首屏显示。
            friendsViewModel.refresh()
            invitesViewModel.refresh()
        }
    }
    LaunchedEffect(inviteItems, friendshipDtos, currentUserId) {
        inviteItems.forEach { invite ->
            val eventId = "invite-${invite.id}"
            if (invite.status == "ACCEPTED") {
                val otherId = if (currentUserId == invite.senderId) invite.receiverId else invite.senderId
                val otherName = friends.firstOrNull { it.id == otherId.toString() }?.name ?: "好友"
                calendarViewModel.saveEvent(CalendarEvent(eventId, currentUserId?.toString() ?: "guest", "${invite.title}(with $otherName)", invite.startAt.replace('T', ' '), invite.endAt.replace('T', ' '), "邀约", EventStatus.HARD))
            } else if (invite.status in setOf("DECLINED", "CANCELLED", "EXPIRED")) {
                calendarViewModel.deleteEvent(eventId)
            }
        }
    }
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
                if (calendarLoading || (page == "找时间" && (friendsLoading || invitesLoading))) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Coral)
                }
                when (page) {
                    "找时间" -> MatchPage(
                        pendingInvites, acceptedInvites, friends, friendResults, friendshipDtos, friendAvailability, currentUserId,
                        onSearchFriends = friendsViewModel::search,
                        onClearSearch = friendsViewModel::clearSearch,
                        onAddFriend = friendsViewModel::add,
                        onRespondFriend = friendsViewModel::respond,
                        onDeleteFriend = friendsViewModel::remove,
                        onViewAvailability = friendsViewModel::loadAvailability,
                        onStartInvite = { invitesViewModel.clearOptions(); showInvite = true },
                        onRespond = { pending, status ->
                            inviteItems.firstOrNull { it.id.toString() == pending.id }?.let { item ->
                                invitesViewModel.respond(item.id, status) { completed ->
                                    if (status == "ACCEPTED") {
                                        val counterpart = if (currentUserId == completed.senderId) {
                                            friends.firstOrNull { it.id == completed.receiverId.toString() }?.name ?: "好友"
                                        } else {
                                            friends.firstOrNull { it.id == completed.senderId.toString() }?.name ?: "好友"
                                        }
                                        calendarViewModel.saveEvent(CalendarEvent("invite-${completed.id}", currentUserId?.toString() ?: "guest", "${completed.title}(with $counterpart)", completed.startAt.replace('T', ' '), completed.endAt.replace('T', ' '), "邀约", EventStatus.HARD))
                                    } else if (status == "CANCELLED") {
                                        calendarViewModel.deleteEvent("invite-${completed.id}")
                                    }
                                }
                            }
                        }
                    )
                    "群组" -> GroupPage(groups)
                    "我的" -> SettingsPage(
                        user = (authState as? AuthState.LoggedIn)?.session?.user,
                        themeChoice = themeChoice,
                        onThemeChange = { themeChoice = it; ThemePreference.save(context, it, accountScope) },
                        onLogin = { showAuth = true },
                        onUpdateProfile = authViewModel::updateProfile,
                        onLogout = authViewModel::logout,
                        aiAccessState = aiAccessState,
                        onVerifyAi = aiVoiceViewModel::verifyAccess
                    )
                    else -> CalendarPage(remoteEvents, onAdd = { date -> createDate = date; showCreate = true }, onEdit = { editingEvent = it })
                }
                AnimatedVisibility(
                    visible = page == "日程" && calendarLoading && remoteEvents.isEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Surface(color = Bg.copy(alpha = .97f), modifier = Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = Coral, strokeWidth = 3.dp)
                            Spacer(Modifier.height(16.dp))
                            Text("正在准备你的日历…", color = MainText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("首次进入可能需要一点时间", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        if (showCreate) CreateEventDialog(
            date = createDate,
            aiState = aiVoiceState,
            aiEnabled = aiAccessState is AiAccessState.Enabled,
            onVoice = {
                if (aiVoiceState is AiVoiceState.Recording) aiVoiceViewModel.stopRecording()
                else if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) aiVoiceViewModel.startRecording()
                else microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onCancelVoice = aiVoiceViewModel::cancelRecording,
            existingEvents = remoteEvents,
            onSave = { event -> calendarViewModel.saveEvent(event); aiVoiceViewModel.clear(); showCreate = false },
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
        if (showInvite) InviteDialog(friends, matchOptions, onScan = { receiverId, duration, startDate, endDate, startTime, endTime -> invitesViewModel.match(receiverId, duration, startDate, endDate, startTime, endTime) }, onSend = { receiverId, title, start, end -> invitesViewModel.create(receiverId, title, start, end) { created -> calendarViewModel.saveEvent(CalendarEvent("invite-${created.id}", currentUserId?.toString() ?: "guest", "${created.title}(待应约)", created.startAt.replace('T', ' '), created.endAt.replace('T', ' '), "邀约", EventStatus.PENDING)) }; showInvite = false }, onClose = { showInvite = false })
        friendMessage?.let { message ->
            AlertDialog(onDismissRequest = friendsViewModel::clearMessage, containerColor = Panel, title = { Text("好友") }, text = { Text(message, color = Muted) }, confirmButton = { TextButton(onClick = friendsViewModel::clearMessage) { Text("知道了", color = Coral) } })
        }
        inviteMessage?.let { message ->
            AlertDialog(onDismissRequest = invitesViewModel::clearMessage, containerColor = Panel, title = { Text("邀约") }, text = { Text(message, color = Muted) }, confirmButton = { TextButton(onClick = invitesViewModel::clearMessage) { Text("知道了", color = Coral) } })
        }
        authMessage?.let { message ->
            AlertDialog(onDismissRequest = authViewModel::clearMessage, containerColor = Panel, title = { Text("资料") }, text = { Text(message, color = Muted) }, confirmButton = { TextButton(onClick = authViewModel::clearMessage) { Text("知道了", color = Coral) } })
        }
        calendarError?.let { message ->
            AlertDialog(
                onDismissRequest = { calendarViewModel.clearError() },
                containerColor = Panel,
                title = { Text("日历数据") },
                text = { Text(if (remoteEvents.isEmpty()) "$message\n当前没有本地日程。" else "$message\n已为你显示本地日程。", color = Muted) },
                confirmButton = { TextButton(onClick = { calendarViewModel.clearError(); calendarViewModel.refresh() }) { Text("重试", color = Coral) } },
                dismissButton = { TextButton(onClick = { calendarViewModel.clearError() }) { Text("关闭", color = Muted) } }
            )
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
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    fun nextModeForDrag(totalDrag: Float): CalendarMode? = when {
        totalDrag < 0f && mode == CalendarMode.MONTH -> CalendarMode.WEEK
        totalDrag < 0f && mode == CalendarMode.WEEK -> CalendarMode.DAY
        totalDrag > 0f && mode == CalendarMode.DAY -> CalendarMode.WEEK
        totalDrag > 0f && mode == CalendarMode.WEEK -> CalendarMode.MONTH
        else -> null
    }
    val pageModifier = Modifier
        .fillMaxSize()
        .padding(18.dp)
        .then(if (mode == CalendarMode.MONTH || mode == CalendarMode.DAY) Modifier.verticalScroll(pageScroll) else Modifier)
        .graphicsLayer { translationX = swipeOffset }
        .pointerInput(Unit) {
            var totalDrag = 0f
            var switchedTarget: CalendarMode? = null
            detectHorizontalDragGestures(
                onDragStart = { totalDrag = 0f; switchedTarget = null; swipeOffset = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    totalDrag += dragAmount
                    if (switchedTarget == null && kotlin.math.abs(totalDrag) > 100f) {
                        switchedTarget = nextModeForDrag(totalDrag)
                        switchedTarget?.let { mode = it }
                    }
                    // 拖动过程直接更新，避免每个触摸事件都创建协程造成画面滞后。
                    swipeOffset = totalDrag
                },
                onDragEnd = {
                    // 达到阈值时页面已经切换，松手立即归中，避免动画协程造成下一次拖动停滞。
                    swipeOffset = 0f
                },
                onDragCancel = { swipeOffset = 0f }
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
        when (mode) {
            CalendarMode.MONTH -> MonthView(year, month, safeSelectedDay, events, onEdit, onSelect = { selectedDay = it }, onDoubleSelect = { selectedDay = it; mode = CalendarMode.DAY })
            CalendarMode.WEEK -> WeekView(
                year, month, safeSelectedDay, events, onEdit,
                onSelect = { selectedDate -> year = selectedDate.year; month = selectedDate.monthValue; selectedDay = selectedDate.dayOfMonth },
                onDoubleSelect = { selectedDate -> year = selectedDate.year; month = selectedDate.monthValue; selectedDay = selectedDate.dayOfMonth; mode = CalendarMode.DAY }
            )
            CalendarMode.DAY -> DayView(year, month, safeSelectedDay, events, onEdit)
            CalendarMode.AGENDA -> AgendaView(events, onEdit)
        }
    }
}

@Composable
fun MonthView(year: Int, month: Int, selectedDay: Int, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit, onSelect: (Int) -> Unit, onDoubleSelect: (Int) -> Unit = {}) {
    val monthInfo = YearMonth.of(year, month)
    val daysInMonth = monthInfo.lengthOfMonth()
    val firstOffset = monthInfo.atDay(1).dayOfWeek.value - 1
    val lunarByDay = remember(year, month) { (1..daysInMonth).associateWith { day -> lunarLabel(year, month, day) } }
    val eventsByDate = remember(events) { events.groupBy { it.start.substringBefore(" ").trim() } }
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
                                            Text(lunarByDay[day].orEmpty(), color = if (selected) Color.White else Muted, fontSize = 9.sp)
                                        }
                                    }
                                    val prefix = "%04d-%02d-%02d".format(year, month, day)
                                    val dayEvents = eventsByDate[prefix].orEmpty()
                                    if (dayEvents.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                                        dayEvents.take(3).forEach { event ->
                                            Box(Modifier.size(5.dp).clip(CircleShape).background(eventColor(event.status)))
                                            Spacer(Modifier.width(3.dp))
                                        }
                                        if (dayEvents.size > 3) Text("+${dayEvents.size - 3}", color = Muted, fontSize = 8.sp)
                                    }
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
@OptIn(ExperimentalFoundationApi::class)
fun WeekView(year: Int, month: Int, selectedDay: Int, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit, onSelect: (LocalDate) -> Unit, onDoubleSelect: (LocalDate) -> Unit = {}) {
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp).combinedClickable(onClick = { onSelect(date) }, onDoubleClick = { onDoubleSelect(date) })) {
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
        var showExpandedSchedule by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("周日程表", color = MainText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showExpandedSchedule = true }) { Text("展开表格", color = Coral, fontSize = 12.sp) }
        }
        WeekScheduleGrid(days, events, onEdit, Modifier.weight(1f), showEventText = false)
        if (showExpandedSchedule) {
            Dialog(
                onDismissRequest = { showExpandedSchedule = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
            ) {
                Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("完整周日程表", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("${days.first()} — ${days.last()} · 00:00–24:00", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                            }
                            TextButton(onClick = { showExpandedSchedule = false }) { Text("关闭", color = Coral) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                days.forEach { date ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text(weekdayFor(date.year, date.monthValue, date.dayOfMonth), color = Muted, fontSize = 10.sp)
                                        Text("${date.monthValue}/${date.dayOfMonth}", color = MainText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 60.dp)) {
                            WeekScheduleGrid(days, events, onEdit, Modifier.fillMaxSize(), showEventText = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekScheduleGrid(days: List<LocalDate>, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit, modifier: Modifier = Modifier, showEventText: Boolean = true) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val segmentsByDay = remember(days, events) {
        days.map { date -> events.mapNotNull { weekEventSegmentForDay(it, date) } }
    }
    val categoryColors = remember(events) {
        val options = CategoryStore.load(context).associate { it.name to categoryColor(it.colorHex) }
        events.associate { it.id to (options[it.category] ?: Coral) }
    }
    val gridBorder = ThemeBorder.copy(alpha = .7f)

    Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .pointerInput(segmentsByDay) {
                    detectTapGestures { offset ->
                        val columnWidth = size.width / days.size.coerceAtLeast(1)
                        val dayIndex = (offset.x / columnWidth).toInt().coerceIn(0, days.lastIndex)
                        val minutes = (offset.y / size.height * 1440f).toInt()
                        segmentsByDay[dayIndex].firstOrNull { minutes in it.startMinutes until it.endMinutes }?.let { onEdit(it.event) }
                    }
                }
        ) {
            val columnWidth = size.width / days.size.coerceAtLeast(1)
            val hourHeight = size.height / 24f
            val lineWidth = with(density) { 1.dp.toPx() }
            val blockRadius = with(density) { 7.dp.toPx() }

            for (column in 0..days.size) {
                val x = column * columnWidth
                drawLine(gridBorder, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), lineWidth)
            }
            for (row in 0..24) {
                val y = row * hourHeight
                drawLine(gridBorder, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), lineWidth)
            }

            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = if (currentPalette == DarkPalette) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                textAlign = AndroidPaint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = with(density) { 9.sp.toPx() }
            }
            segmentsByDay.forEachIndexed { column, segments ->
                segments.forEach { segment ->
                    val left = column * columnWidth + with(density) { 2.dp.toPx() }
                    val right = (column + 1) * columnWidth - with(density) { 2.dp.toPx() }
                    val top = size.height * segment.startMinutes / 1440f + with(density) { 1.dp.toPx() }
                    val bottom = size.height * segment.endMinutes / 1440f - with(density) { 1.dp.toPx() }
                    drawRoundRect(
                        color = categoryColors[segment.event.id] ?: Coral,
                        topLeft = androidx.compose.ui.geometry.Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, (bottom - top).coerceAtLeast(with(density) { 4.dp.toPx() })),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(blockRadius, blockRadius)
                    )
                    if (showEventText) drawIntoCanvas { canvas ->
                        val centeredBaseline = (top + bottom) / 2f - (paint.ascent() + paint.descent()) / 2f
                        val minBaseline = top - paint.ascent()
                        val maxBaseline = bottom - paint.descent()
                        val safeBaseline = if (minBaseline <= maxBaseline) centeredBaseline.coerceIn(minBaseline, maxBaseline) else centeredBaseline
                        canvas.nativeCanvas.drawText(
                            segment.event.title,
                            (left + right) / 2f,
                            safeBaseline,
                            paint
                        )
                    }
                }
            }
        }
    }
}

private data class WeekEventSegment(
    val event: CalendarEvent,
    val startMinutes: Int,
    val endMinutes: Int
)

private fun eventDateAndMinutes(value: String): Pair<LocalDate?, Int> {
    val normalized = value.trim().replace('T', ' ')
    val dateText = normalized.substringBefore(' ').trim()
    val timeText = normalized.substringAfter(' ', normalized).trim()
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val parts = timeText.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val total = if (hour == 24 && minute == 0) 1440 else (hour * 60 + minute).coerceIn(0, 1440)
    return date to total
}

private fun weekEventSegmentForDay(event: CalendarEvent, date: LocalDate): WeekEventSegment? {
    val (startDate, startMinutes) = eventDateAndMinutes(event.start)
    val (endDate, parsedEndMinutes) = eventDateAndMinutes(event.end)
    if (startDate == null || endDate == null) return null

    // 兼容旧数据：23:00–24:00 曾被保存成同一天的 23:00–00:00。
    val sameDayMidnightEnd = startDate == endDate && parsedEndMinutes == 0 && startMinutes > 0
    val effectiveEndDate = when {
        endDate.isBefore(startDate) -> startDate
        else -> endDate
    }
    if (date.isBefore(startDate) || date.isAfter(effectiveEndDate)) return null

    val segmentStart = if (date == startDate) startMinutes else 0
    val segmentEnd = if (sameDayMidnightEnd) 1440 else if (date == effectiveEndDate) parsedEndMinutes else 1440
    if (segmentEnd <= segmentStart) return null
    return WeekEventSegment(event, segmentStart.coerceIn(0, 1439), segmentEnd.coerceIn(1, 1440))
}

private fun eventEndLabel(event: CalendarEvent): String {
    val (startDate, startMinutes) = eventDateAndMinutes(event.start)
    val (endDate, endMinutes) = eventDateAndMinutes(event.end)
    val original = event.end.substringAfter(" ", "--:--")
    return if (startDate != null && startDate == endDate && endMinutes == 0 && startMinutes > 0) "24:00" else original
}

fun eventTimeMinutes(value: String): Int {
    return eventDateAndMinutes(value).second
}

@Composable fun DayView(year: Int, month: Int, day: Int, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit) {
    val safeDate = runCatching { LocalDate.of(year, month, day) }.getOrElse { LocalDate.now() }
    val safeYear = safeDate.year
    val safeMonth = safeDate.monthValue
    val safeDay = safeDate.dayOfMonth
    Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("${safeYear}年${safeMonth}月${safeDay}日", color = MainText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            val dayInfo = CalendarInfoProvider.info(safeDate)
            Text("${dayInfo.lunar} · ${weekdayFor(safeYear, safeMonth, safeDay)} · ${constellationFor(safeMonth, safeDay)}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Text(listOfNotNull(dayInfo.festival, dayInfo.solarTerm).joinToString(" · ").ifBlank { "农历与节气数据已使用本地缓存" }, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            HorizontalDivider(color = Color(0xFF303236), modifier = Modifier.padding(vertical = 16.dp))
            Text("日程安排", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val prefix = "%04d-%02d-%02d".format(safeYear, safeMonth, safeDay)
            val dayEvents = events.filter { it.start.startsWith(prefix) }
            if (dayEvents.isEmpty()) Text("这一天还没有日程", color = Muted, modifier = Modifier.padding(top = 20.dp))
            dayEvents.forEach { event ->
                val startText = event.start.substringAfter(" ", "--:--")
                val endText = eventEndLabel(event)
                TimeEvent("$startText — $endText", event.title, "${event.category} · ${event.status}", eventColor(event.status), categoryColor = categoryColorForName(event.category), onClick = { onEdit(event) })
            }
        }
    }
}

fun weekdayFor(year: Int, month: Int, day: Int): String = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[LocalDate.of(year, month, day).dayOfWeek.value - 1]
fun constellationFor(month: Int, day: Int): String = when { month == 7 && day < 23 -> "巨蟹座"; month == 7 -> "狮子座"; else -> "星座待计算" }
fun lunarLabel(year: Int, month: Int, day: Int): String = CalendarInfoProvider.lunarLabel(LocalDate.of(year, month, day))
@Composable fun AgendaView(events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit) { LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(events) { event -> Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable { onEdit(event) }) { Text("${event.start} · ${event.title}", color = MainText, modifier = Modifier.fillMaxWidth().padding(18.dp)) } } } }

@Composable fun DayScheduleCard(year: Int, month: Int, day: Int, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit) { Surface(color = Panel, shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp)) { Text("${year}年${month}月${day}日", color = Muted, fontSize = 12.sp); Text("这一天的日程", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold); val prefix = "%04d-%02d-%02d".format(year, month, day); val dayEvents = events.filter { it.start.startsWith(prefix) }; if (dayEvents.isEmpty()) Text("暂无日程，点击右上角创建", color = Muted, modifier = Modifier.padding(top = 14.dp)); dayEvents.forEach { event -> TimeEvent("${event.start.substringAfter(" ")} — ${event.end.substringAfter(" ")}", event.title, event.category, eventColor(event.status), categoryColor = categoryColorForName(event.category), onClick = { onEdit(event) }) } } } }
fun eventColor(status: EventStatus): Color = when (status) { EventStatus.HARD -> Red; EventStatus.FREE -> Green; EventStatus.FLEXIBLE -> Yellow; EventStatus.PENDING -> Blue }

@Composable fun TimeEvent(time: String, title: String, detail: String, color: Color, categoryColor: Color? = null, onClick: (() -> Unit)? = null) { Row(Modifier.fillMaxWidth().padding(top = 14.dp).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier), verticalAlignment = Alignment.Top) { Text(time, color = Muted, fontSize = 11.sp, modifier = Modifier.width(92.dp)); Surface(color = color.copy(alpha = .14f), shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp), modifier = Modifier.weight(1f).border(1.dp, color.copy(alpha = .7f), RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))) { Column(Modifier.padding(9.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { categoryColor?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it)); Spacer(Modifier.width(6.dp)) }; Text(title, color = MainText, fontWeight = FontWeight.Bold, fontSize = 12.sp) }; Text(detail, color = Muted, fontSize = 10.sp) } } } }

@Composable
fun MatchPage(invites: List<PendingInvite>, acceptedInvites: List<AcceptedInvite>, friends: List<FriendSummary>, searchResults: List<com.hutong.calendar.data.FriendUserDto>, friendships: List<com.hutong.calendar.data.FriendshipDto>, availability: List<com.hutong.calendar.data.AvailabilityBlockDto>, currentUserId: Int?, onSearchFriends: (String) -> Unit, onClearSearch: () -> Unit, onAddFriend: (com.hutong.calendar.data.FriendUserDto) -> Unit, onRespondFriend: (Int, String) -> Unit, onDeleteFriend: (Int) -> Unit, onViewAvailability: (Int, String) -> Unit, onStartInvite: () -> Unit, onRespond: (PendingInvite, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var viewingFriend by remember { mutableStateOf<String?>(null) }
    var selectedFriend by remember { mutableStateOf<com.hutong.calendar.data.FriendUserDto?>(null) }
    var showAllAccepted by remember { mutableStateOf(false) }
    val upcomingAccepted = acceptedInvites.filter { invite ->
        runCatching { LocalDateTime.parse(invite.endAt.replace(' ', 'T')) > LocalDateTime.now() }.getOrDefault(true)
    }
    val visibleAccepted = if (showAllAccepted) upcomingAccepted else upcomingAccepted.take(3)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Header("一起安排，不用来回问", "找时间", if (friends.isNotEmpty()) "＋ 发起邀约" else null, onStartInvite)
        Surface(color = Card, shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(20.dp)) { Text("智能匹配", color = Yellow, fontSize = 12.sp); Text("谁的时间，刚好和你重合？", color = MainText, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp)); Text("先扫描双方完全空闲，再询问是否纳入机动尾巴。", color = Muted, fontSize = 12.sp) } }
        Text("待应答邀约  ${invites.size}", color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 25.dp, bottom = 12.dp))
        if (invites.isEmpty()) Text("暂时没有待应答邀约", color = Muted, modifier = Modifier.padding(vertical = 20.dp))
        invites.forEach { invite ->
            InviteCard(invite, isSender = invite.inviter == "我", onCancel = { onRespond(invite, "CANCELLED") }, onDecline = { onRespond(invite, "DECLINED") }, onAccept = { onRespond(invite, "ACCEPTED") })
            Spacer(Modifier.height(12.dp))
        }
        if (upcomingAccepted.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("已确认邀约", color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (upcomingAccepted.size > 3) TextButton(onClick = { showAllAccepted = !showAllAccepted }) { Text(if (showAllAccepted) "收起" else "展开全部", color = Coral, fontSize = 12.sp) }
            }
            visibleAccepted.forEach { invite ->
                Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(Blue), contentAlignment = Alignment.Center) { Text(invite.counterpart.take(1), color = Color.White) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(invite.title, color = MainText, fontWeight = FontWeight.Bold)
                                Text("与 ${invite.counterpart} · 已确认", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                        Text(invite.time, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        invite.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
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
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(bottom = 8.dp).clickable { selectedFriend = friendship.friend }) {
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
    selectedFriend?.let { friend ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { selectedFriend = null },
            containerColor = Panel,
            title = { Text("好友资料") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("昵称：${friend.displayName}", color = MainText)
                    val phone = friend.phone?.trim().orEmpty()
                    if (phone.isNotEmpty()) {
                        Text(
                            "电话：$phone",
                            color = Blue,
                            modifier = Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")))
                            }
                        )
                    }
                    friend.hobbies?.takeIf { it.isNotBlank() }?.let { Text("爱好：$it", color = Muted) }
                    friend.signature?.takeIf { it.isNotBlank() }?.let { Text("签名：$it", color = Muted) }
                    if (phone.isEmpty() && friend.hobbies.isNullOrBlank() && friend.signature.isNullOrBlank()) {
                        Text("对方暂未填写其他公开资料", color = Muted)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedFriend = null }) { Text("关闭", color = Coral) } }
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

@Composable fun InviteCard(invite: PendingInvite, isSender: Boolean, onCancel: () -> Unit, onDecline: () -> Unit, onAccept: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Blue), contentAlignment = Alignment.Center) {
                    Text((if (isSender) invite.receiver else invite.inviter).take(1), color = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (isSender) "你邀请 ${invite.receiver}：${invite.title}" else "${invite.inviter} 邀请你：${invite.title}", color = MainText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(invite.time, color = Muted, fontSize = 11.sp)
                }
                Text("待处理", color = Blue, fontSize = 11.sp)
            }
            Text("邀请人：${invite.inviter}", color = MainText, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Text("提议时间：${invite.time}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            invite.description?.takeIf { it.isNotBlank() }?.let { Text("活动说明：$it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            Text(if (isSender) "该时间已在你的日历中暂时锁定。" else "你仍可继续接收其他邀约，确认后只保留一个。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isSender) Button(onClick = onCancel, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("取消邀约") }
                else { OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("拒绝") }; Button(onClick = onAccept, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("同意") } }
            }
        }
    }
}

@Composable fun GroupPage(groups: List<GroupSummary>) { SimplePage("一起参加，不被代表", "群组", *groups.flatMap { listOf(it.name, it.activity, it.detail) }.toTypedArray()) }
@Composable
fun SettingsPage(user: UserProfile?, themeChoice: ThemeChoice, onThemeChange: (ThemeChoice) -> Unit, onLogin: () -> Unit, onUpdateProfile: (String, String?, String?, String?) -> Unit, onLogout: () -> Unit, aiAccessState: AiAccessState = AiAccessState.Disabled, onVerifyAi: (String) -> Unit = {}) {
    val context = LocalContext.current
    var categories by remember(user?.id) { mutableStateOf(CategoryStore.load(context, user?.id ?: "guest")) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var logoutStep by remember { mutableStateOf(0) }
    var editedName by remember(user?.displayName) { mutableStateOf(user?.displayName.orEmpty()) }
    var editedPhone by remember(user?.phone) { mutableStateOf(user?.phone.orEmpty()) }
    var editedHobbies by remember(user?.hobbies) { mutableStateOf(user?.hobbies.orEmpty()) }
    var editedSignature by remember(user?.signature) { mutableStateOf(user?.signature.orEmpty()) }
    var showAiAccess by remember { mutableStateOf(false) }
    var aiCode by remember { mutableStateOf("") }
    fun openProfileEditor() {
        user?.let {
            editedName = it.displayName
            editedPhone = it.phone.orEmpty()
            editedHobbies = it.hobbies.orEmpty()
            editedSignature = it.signature.orEmpty()
            showProfileEditor = true
        }
    }
    LaunchedEffect(aiAccessState) { if (aiAccessState is AiAccessState.Enabled) showAiAccess = false }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Header("账户与偏好", "我的")
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().then(if (user != null) Modifier.clickable { openProfileEditor() } else Modifier)) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (user == null) "游客模式" else user.displayName, color = MainText, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (user != null) TextButton(onClick = { openProfileEditor() }) { Text("编辑资料", color = Coral) }
                }
                Text(if (user == null) "未登录 · 日程仅保存在本机" else "账号 ID：${user.accountId}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                user?.let { Text("用户名：${it.username ?: "未设置"}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                user?.email?.let { Text("邮箱：$it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                user?.phone?.takeIf { it.isNotBlank() }?.let { Text("电话：$it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                user?.hobbies?.takeIf { it.isNotBlank() }?.let { Text("爱好：$it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                user?.signature?.takeIf { it.isNotBlank() }?.let { Text("签名：$it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                if (user == null) Button(onClick = onLogin, modifier = Modifier.padding(top = 14.dp)) { Text("登录 / 注册") }
            }
        }
        Text("主题", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(ThemeChoice.LIGHT to "浅色", ThemeChoice.DARK to "深色", ThemeChoice.SYSTEM to "跟随系统").forEach { (choice, label) ->
                    FilterChip(
                        selected = themeChoice == choice,
                        onClick = { onThemeChange(choice) },
                        label = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text(label, textAlign = TextAlign.Center) } },
                        colors = tempoFilterChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Text("AI 内测", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable { showAiAccess = true }) {
            Column(Modifier.padding(17.dp)) {
                Text(if (aiAccessState is AiAccessState.Enabled) "AI 语音填写已开启" else "开启 AI 语音填写", color = MainText, fontWeight = FontWeight.Bold)
                Text(if (aiAccessState is AiAccessState.Enabled) "当前账号已获得内测权限" else "仅限受邀用户输入内测密码后使用", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
        val settings = listOf("日程和已同意邀约会同步到本机离线缓存")
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
                    OutlinedTextField(editedName, { editedName = it }, label = { Text("昵称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editedPhone, { editedPhone = it }, label = { Text("电话（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editedHobbies, { editedHobbies = it }, label = { Text("爱好（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editedSignature, { editedSignature = it }, label = { Text("签名（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
    if (showAiAccess) {
        AlertDialog(
            onDismissRequest = { showAiAccess = false },
            containerColor = Panel,
            title = { Text("AI 内测验证") },
            text = {
                Column {
                    Text("请输入内测密码，验证成功后本账号可使用 AI 语音填写日程。", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(aiCode, { aiCode = it }, label = { Text("内测密码") }, singleLine = true, modifier = Modifier.padding(top = 10.dp))
                    if (aiAccessState is AiAccessState.Error) Text((aiAccessState as AiAccessState.Error).message, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { Button(onClick = { onVerifyAi(aiCode) }, enabled = aiCode.isNotBlank() && aiAccessState !is AiAccessState.Verifying, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text(if (aiAccessState is AiAccessState.Verifying) "验证中…" else "验证") } },
            dismissButton = { TextButton(onClick = { showAiAccess = false }) { Text("取消") } }
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

@Composable fun BottomNav(current: String, onSelect: (String) -> Unit) { NavigationBar(containerColor = Panel) { listOf("日程" to "▣", "找时间" to "⌕", "群组" to "♧", "我的" to "⚙").forEach { (name, icon) -> NavigationBarItem(selected = current == name, onClick = { onSelect(name) }, icon = { Text(icon, fontSize = 20.sp) }, label = { Text(name, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = Coral, indicatorColor = Coral, unselectedIconColor = Muted, unselectedTextColor = Muted)) } } }

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
fun CreateEventDialog(date: String, aiState: AiVoiceState = AiVoiceState.Idle, aiEnabled: Boolean = true, onVoice: () -> Unit = {}, onCancelVoice: () -> Unit = {}, existingEvents: List<CalendarEvent>, onSave: (CalendarEvent) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var selectedDate by remember(date) { mutableStateOf(date) }
    var category by remember {
        mutableStateOf(CategoryStore.load(context).firstOrNull()?.name ?: "工作")
    }
    var status by remember { mutableStateOf("硬性") }
    var start by remember { mutableStateOf<String?>(null) }
    var end by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    LaunchedEffect(aiState) {
        val ready = aiState as? AiVoiceState.Ready ?: return@LaunchedEffect
        name = ready.draft.title
        ready.draft.date?.let { selectedDate = it }
        ready.draft.startTime?.let { start = it }
        ready.draft.endTime?.let { end = it }
        if (ready.draft.category.isNotBlank()) category = ready.draft.category
        status = when (ready.draft.status) { "FREE" -> "空闲"; "FLEXIBLE" -> "机动"; else -> "硬性" }
    }
    AlertDialog(onDismissRequest = onCancel, containerColor = Panel, title = { Text("安排一段时间", fontWeight = FontWeight.Bold) }, text = {
        Column {
            OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("活动名称") }, singleLine = true)
            if (aiState is AiVoiceState.Recording) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onVoice, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("结束录音") }
                    OutlinedButton(onClick = onCancelVoice, modifier = Modifier.weight(1f)) { Text("取消") }
                }
            } else {
                Button(onClick = onVoice, enabled = aiEnabled && aiState !is AiVoiceState.Uploading, colors = ButtonDefaults.buttonColors(containerColor = Coral), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(if (!aiEnabled) "请先在“我的”开启 AI 内测" else if (aiState is AiVoiceState.Uploading) "正在识别…" else "语音填写日程")
                }
            }
            when (aiState) {
                is AiVoiceState.Uploading -> Text("录音已上传，正在生成日程草稿…", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                is AiVoiceState.Ready -> Text("已识别，请检查内容后再点击保存", color = Coral, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                is AiVoiceState.Error -> Text(aiState.message, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                else -> Unit
            }
            Surface(color = Card, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ThemeBorder), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("日期：$selectedDate", color = MainText, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp))
            }
            TimeRangeButton(start, end, onClick = { showPicker = true })
            CategorySelector(category = category, onCategoryChange = { category = it })
            Text("对外状态", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("硬性", "空闲", "机动").forEach { item -> FilterChip(selected = status == item, onClick = { status = item }, label = { Text(item) }, colors = tempoFilterChipColors()) } }
        }
    }, confirmButton = { Button(onClick = {
        val finalName = name.ifBlank { "未命名日程" }
        val eventStatus = when (status) { "空闲" -> EventStatus.FREE; "机动" -> EventStatus.FLEXIBLE; else -> EventStatus.HARD }
        if (start != null && end != null) {
            val savedEnd = if (end == "00:00" && start != "00:00") "24:00" else end!!
            onSave(CalendarEvent("local-${UUID.randomUUID()}", "me", finalName, "$selectedDate ${start!!}", "$selectedDate $savedEnd", category, eventStatus, if (eventStatus == EventStatus.FLEXIBLE) 30 else 0))
        }
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
        val savedEnd = if (end == "00:00" && start != "00:00") "24:00" else end
        onSave(event.copy(title = name.ifBlank { "未命名日程" }, category = category, start = "${event.start.substringBefore(" ")} $start", end = "${event.end.substringBefore(" ")} $savedEnd", status = status, flexibleTailMinutes = if (status == EventStatus.FLEXIBLE) 30 else 0))
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
                LazyColumn(Modifier.heightIn(max = 190.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(candidateOptions) { slot ->
                        val label = "${if (slot.matchType == "PURE_GREEN") "双方空闲" else "包含机动"} · ${slot.startAt.replace('T', ' ')} — ${slot.endAt.substringAfter('T')}"
                        FilterChip(selected = selectedSlot == slot, onClick = { selectedSlot = slot }, label = { Text(label) }, colors = tempoFilterChipColors(), modifier = Modifier.fillMaxWidth())
                    }
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
