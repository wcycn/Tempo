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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.hutong.calendar.data.GroupActivityCreateDto
import com.hutong.calendar.data.GroupActivityDto
import com.hutong.calendar.data.GroupInvitationDto
import com.hutong.calendar.data.PendingInvite
import com.hutong.calendar.data.AcceptedInvite
import com.hutong.calendar.data.ThemeChoice
import com.hutong.calendar.data.ThemePreference
import com.hutong.calendar.data.UserProfile
import com.hutong.calendar.data.CalendarImportParser
import com.hutong.calendar.data.ImportCandidate
import com.hutong.calendar.data.ImportParseResult
import java.util.UUID
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import java.time.YearMonth
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppPalette(val bg: Color, val panel: Color, val card: Color, val text: Color, val muted: Color, val accent: Color, val border: Color)
private val DarkPalette = AppPalette(Color(0xFF090A0B), Color(0xFF17191B), Color(0xFF202224), Color(0xFFF3F4F5), Color(0xFF85898F), Color(0xFF214A78), Color(0xFF4A4D52))
private val LightPalette = AppPalette(Color(0xFFF8F9FA), Color(0xFFF0F1F3), Color(0xFFE5E7EA), Color(0xFF17191B), Color(0xFF6B7078), Color(0xFF75BDF2), Color(0xFFD1D5DB))
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
    var importUri by remember { mutableStateOf<Uri?>(null) }
    val calendarViewModel: CalendarViewModel = viewModel()
    val importReport by calendarViewModel.importReport.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> importUri = uri }
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
    val contentViewModel: ContentViewModel = viewModel()
    val groupViewModel: GroupViewModel = viewModel()
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
            inviter = item.senderDisplayName ?: if (item.senderId == currentUserId) "我" else friends.firstOrNull { it.id == item.senderId.toString() }?.name ?: "好友",
            receiver = item.receiverDisplayName ?: if (item.receiverId == currentUserId) "我" else friends.firstOrNull { it.id == item.receiverId.toString() }?.name ?: "好友",
            description = item.description
        )
    }
    val handledInvites = inviteItems.filter { it.status in setOf("DECLINED", "CANCELLED", "EXPIRED") }.map { item ->
        PendingInvite(
            id = item.id.toString(),
            title = item.title,
            time = "${item.startAt.replace('T', ' ')} — ${item.endAt.substringAfter('T')}",
            inviter = item.senderDisplayName ?: if (item.senderId == currentUserId) "我" else friends.firstOrNull { it.id == item.senderId.toString() }?.name ?: "好友",
            receiver = item.receiverDisplayName ?: if (item.receiverId == currentUserId) "我" else friends.firstOrNull { it.id == item.receiverId.toString() }?.name ?: "好友",
            description = item.description,
            status = item.status
        )
    }
    val acceptedInvites = inviteItems.filter { it.status == "ACCEPTED" }.map { item ->
        val counterpartId = if (item.senderId == currentUserId) item.receiverId else item.senderId
        val counterpart = if (item.senderId == currentUserId) item.receiverDisplayName else item.senderDisplayName
        val counterpartName = counterpart ?: friends.firstOrNull { it.id == counterpartId.toString() }?.name ?: "好友"
        AcceptedInvite(item.id.toString(), item.title, "${item.startAt.replace('T', ' ')} — ${item.endAt.substringAfter('T')}", counterpartName, item.startAt, item.endAt, item.description)
    }.sortedBy { it.startAt }
    val groups = contentViewModel.groups
    val remoteGroups by groupViewModel.groups.collectAsState()
    val groupActivities by groupViewModel.activities.collectAsState()
    val groupInvitations by groupViewModel.invitations.collectAsState()
    val selectedGroupId by groupViewModel.selectedGroupId.collectAsState()
    val groupMessage by groupViewModel.message.collectAsState()
    val groupLoading by groupViewModel.loading.collectAsState()
    val remoteEvents by calendarViewModel.eventsState.collectAsState()
    val calendarLoading by calendarViewModel.loading.collectAsState()
    val calendarError by calendarViewModel.error.collectAsState()
    val syncNotice by calendarViewModel.syncNotice.collectAsState()
    LaunchedEffect(syncNotice) { syncNotice?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); calendarViewModel.clearSyncNotice() } }
    var page by remember { mutableStateOf("日程") }
    LaunchedEffect(accountScope) { calendarViewModel.refresh() }
    LaunchedEffect(accountScope, page) {
        if (page == "找时间") {
            friendsViewModel.refresh()
            invitesViewModel.refresh()
        } else if (page == "群组") {
            groupViewModel.refresh()
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
    LaunchedEffect(groupActivities, currentUserId) {
        groupActivities.forEach { activity ->
            val eventId = "group-${activity.id}"
            if (activity.status == "CONFIRMED" && currentUserId != null && activity.confirmedParticipantIds.orEmpty().contains(currentUserId) && activity.proposedStartAt != null && activity.proposedEndAt != null) {
                calendarViewModel.saveEvent(CalendarEvent(eventId, currentUserId.toString(), activity.title, activity.proposedStartAt.replace('T', ' '), activity.proposedEndAt.replace('T', ' '), "群组", EventStatus.HARD))
            } else if (activity.status == "CANCELLED" || (activity.status == "CONFIRMED" && currentUserId != null && !activity.confirmedParticipantIds.orEmpty().contains(currentUserId))) {
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
                        pendingInvites, handledInvites, acceptedInvites, friends, friendResults, friendshipDtos, friendAvailability, currentUserId,
                        onSearchFriends = friendsViewModel::search,
                        onClearSearch = friendsViewModel::clearSearch,
                        onAddFriend = friendsViewModel::add,
                        onRespondFriend = friendsViewModel::respond,
                        onDeleteFriend = friendsViewModel::remove,
                        onViewAvailability = friendsViewModel::loadAvailability,
                        onStartInvite = { invitesViewModel.clearOptions(); showInvite = true },
                        onDeleteInvite = invitesViewModel::delete,
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
                    "群组" -> GroupPage(remoteGroups, groupActivities, groupInvitations, selectedGroupId, friends, currentUserId, groupLoading, groupViewModel::selectGroup, groupViewModel::createGroup, groupViewModel::renameGroup, groupViewModel::addMember, groupViewModel::removeMember, groupViewModel::respondInvitation, groupViewModel::createActivity, groupViewModel::join, groupViewModel::leave, groupViewModel::respond, groupViewModel::recalculate, groupViewModel::cancel)
                    "我的" -> SettingsPage(
                        user = (authState as? AuthState.LoggedIn)?.session?.user,
                        themeChoice = themeChoice,
                        onThemeChange = { themeChoice = it; ThemePreference.save(context, it, accountScope) },
                        onLogin = { showAuth = true },
                        onUpdateProfile = authViewModel::updateProfile,
                        onLogout = authViewModel::logout,
                        aiAccessState = aiAccessState,
                        onVerifyAi = aiVoiceViewModel::verifyAccess,
                        onImport = { importLauncher.launch(arrayOf("text/csv", "text/calendar", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) }
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
            onSave = { events -> events.forEach { calendarViewModel.saveEvent(it) }; aiVoiceViewModel.clear(); showCreate = false },
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
        groupMessage?.let { message ->
            AlertDialog(onDismissRequest = groupViewModel::clearMessage, containerColor = Panel, title = { Text("群组") }, text = { Text(message, color = Muted) }, confirmButton = { TextButton(onClick = groupViewModel::clearMessage) { Text("知道了", color = Coral) } })
        }
        authMessage?.let { message ->
            AlertDialog(onDismissRequest = authViewModel::clearMessage, containerColor = Panel, title = { Text("资料") }, text = { Text(message, color = Muted) }, confirmButton = { TextButton(onClick = authViewModel::clearMessage) { Text("知道了", color = Coral) } })
        }
        importUri?.let { uri ->
            ImportEventsDialog(
                uri = uri,
                onCancel = { importUri = null },
                onImport = { events -> calendarViewModel.importEvents(events); importUri = null }
            )
        }
        importReport?.let { report ->
            AlertDialog(
                onDismissRequest = calendarViewModel::clearImportReport,
                containerColor = Panel,
                title = { Text("导入结果") },
                text = { Text(report, color = Muted) },
                confirmButton = { TextButton(onClick = calendarViewModel::clearImportReport) { Text("知道了", color = Coral) } }
            )
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
fun MatchPage(invites: List<PendingInvite>, handledInvites: List<PendingInvite>, acceptedInvites: List<AcceptedInvite>, friends: List<FriendSummary>, searchResults: List<com.hutong.calendar.data.FriendUserDto>, friendships: List<com.hutong.calendar.data.FriendshipDto>, availability: List<com.hutong.calendar.data.AvailabilityBlockDto>, currentUserId: Int?, onSearchFriends: (String) -> Unit, onClearSearch: () -> Unit, onAddFriend: (com.hutong.calendar.data.FriendUserDto) -> Unit, onRespondFriend: (Int, String) -> Unit, onDeleteFriend: (Int) -> Unit, onViewAvailability: (Int, String) -> Unit, onStartInvite: () -> Unit, onRespond: (PendingInvite, String) -> Unit, onDeleteInvite: (Int) -> Unit) {
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
        if (handledInvites.isNotEmpty()) {
            Text("已处理邀约  ${handledInvites.size}", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            handledInvites.forEach { invite ->
                HandledInviteCard(invite, onDelete = { onDeleteInvite(invite.id.toInt()) })
                Spacer(Modifier.height(8.dp))
            }
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

@Composable
fun HandledInviteCard(invite: PendingInvite, onDelete: () -> Unit) {
    val (label, color, detail) = when (invite.status) {
        "DECLINED" -> Triple("已拒绝", Red, if (invite.inviter == "我") "对方拒绝了这条邀约" else "你已拒绝这条邀约")
        "CANCELLED" -> Triple("已取消", Muted, if (invite.inviter == "我") "你已取消这条邀约" else "发起人已取消这条邀约")
        else -> Triple("已过期", Muted, "这条邀约已超过应答时间")
    }
    Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(invite.title, color = MainText, fontWeight = FontWeight.Bold)
                    Text(if (invite.inviter == "我") "你邀请 ${invite.receiver}" else "${invite.inviter} 邀请你", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Text(label, color = color, fontSize = 12.sp)
            }
            Text(invite.time, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            Text(detail, color = color, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) { Text("删除记录", color = Red) }
        }
    }
}

@Composable
fun GroupPage(
    groups: List<com.hutong.calendar.data.GroupDto>, activities: List<GroupActivityDto>, invitations: List<GroupInvitationDto>, selectedGroupId: Int?, friends: List<FriendSummary>, currentUserId: Int?, loading: Boolean,
    onSelectGroup: (Int) -> Unit, onCreateGroup: (String) -> Unit, onRenameGroup: (String) -> Unit, onAddMember: (Int) -> Unit, onRemoveMember: (Int) -> Unit, onRespondInvitation: (Int, String) -> Unit, onCreateActivity: (GroupActivityCreateDto) -> Unit,
    onJoin: (Int) -> Unit, onLeave: (Int) -> Unit, onRespond: (Int, String) -> Unit, onRecalculate: (Int) -> Unit, onCancelActivity: (Int) -> Unit
) {
    var showCreateGroup by remember { mutableStateOf(false) }
    var showRenameGroup by remember { mutableStateOf(false) }
    var showAddMember by remember { mutableStateOf(false) }
    var showCreateActivity by remember { mutableStateOf(false) }
    var showGroupGuide by remember { mutableStateOf(false) }
    val selectedGroup = groups.firstOrNull { it.id == selectedGroupId } ?: groups.firstOrNull()
    val isOwner = selectedGroup?.ownerId == currentUserId
    val canCreateActivity = selectedGroup?.members.orEmpty().any { it.id == currentUserId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Header("多人一起安排，所有人都确认", "群组", "＋ 新建群组", { showCreateGroup = true })
        TextButton(onClick = { showGroupGuide = true }, modifier = Modifier.align(Alignment.End)) { Text("群组使用说明", color = Coral) }
        if (invitations.isNotEmpty()) {
            Text("待处理入群邀请", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            invitations.forEach { invitation ->
                Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${invitation.inviterDisplayName ?: "群主"} 邀请你加入「${invitation.groupName ?: "群组"}」", color = MainText, fontWeight = FontWeight.Bold)
                        Text("确认后你才会成为群组成员", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onRespondInvitation(invitation.id, "DECLINED") }) { Text("拒绝", color = Muted) }
                            Button(onClick = { onRespondInvitation(invitation.id, "ACCEPTED") }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("同意加入") }
                        }
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) { Text("还没有群组", color = MainText, fontWeight = FontWeight.Bold); Text("创建群组后，邀请好友一起接龙成团。", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp)) }
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { group -> FilterChip(selected = group.id == selectedGroup?.id, onClick = { onSelectGroup(group.id) }, label = { Text(group.name) }, colors = tempoFilterChipColors()) }
            }
            selectedGroup?.let { group ->
                Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(group.name, color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); if (isOwner) { Text("群主", color = Blue, fontSize = 12.sp); TextButton(onClick = { showRenameGroup = true }) { Text("编辑群名", color = Coral) } } }
                        Text("成员 ${group.members.orEmpty().size} 人", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                        Text(group.members.orEmpty().joinToString("、") { it.displayName }, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                        if (isOwner) {
                            group.members.orEmpty().filter { it.id != group.ownerId }.forEach { member ->
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(member.displayName, color = MainText, modifier = Modifier.weight(1f)); TextButton(onClick = { onRemoveMember(member.id) }) { Text("移出", color = Red) } }
                            }
                        }
                        if (isOwner) OutlinedButton(onClick = { showAddMember = true }, modifier = Modifier.padding(top = 10.dp)) { Text("邀请好友加入") }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text("群组活动", color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); if (canCreateActivity) Button(onClick = { showCreateActivity = true }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("发起接龙") } }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral)
                if (activities.isEmpty()) Text("暂无群组活动", color = Muted, modifier = Modifier.padding(vertical = 18.dp))
                activities.forEach { activity -> GroupActivityCard(activity, currentUserId, isOwner, onJoin, onLeave, onRespond, onRecalculate, onCancelActivity) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (showCreateGroup) SimpleInputDialog("新建群组", "群组名称", "创建", onDismiss = { showCreateGroup = false }) { name -> onCreateGroup(name); showCreateGroup = false }
    if (showRenameGroup && selectedGroup != null) SimpleInputDialog("编辑群名", "群组名称", "保存", initialValue = selectedGroup.name, onDismiss = { showRenameGroup = false }) { name -> onRenameGroup(name); showRenameGroup = false }
    if (showAddMember && selectedGroup != null) {
        AlertDialog(onDismissRequest = { showAddMember = false }, containerColor = Panel, title = { Text("邀请好友确认入群") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { friends.filter { friend -> selectedGroup.members.orEmpty().none { it.id.toString() == friend.id } }.forEach { friend -> TextButton(onClick = { onAddMember(friend.id.toInt()); showAddMember = false }, modifier = Modifier.fillMaxWidth()) { Text("邀请 ${friend.name}", color = MainText) } } } }, confirmButton = { TextButton(onClick = { showAddMember = false }) { Text("关闭", color = Coral) } })
    }
    if (showCreateActivity && selectedGroup != null) CreateGroupActivityDialog(onDismiss = { showCreateActivity = false }) { payload -> onCreateActivity(payload); showCreateActivity = false }
    if (showGroupGuide) GroupGuideDialog(onDismiss = { showGroupGuide = false })
}

@Composable
private fun GroupGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Tempo 群组使用说明", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GuideSection("一、群组是什么", "群组用于多人一起约时间。群主或任何已确认入群的成员都可以发起群组活动，系统会根据成员接龙和个人硬性日程寻找合适时间。")
                GuideSection("二、如何加入群组", "群主选择好友发送入群邀请。对方必须点击“同意加入”后才会成为正式成员；拒绝邀请不会加入群组。")
                GuideSection("三、发起群组活动", "填写活动名称、活动时长、响应截止时间、人数规则和时间规则，然后发布活动。活动创建者会自动进入接龙池。")
                GuideSection("四、人数规则", "最低人数：达到指定人数即可成团，候选时间内所有有空成员都可以确认；确认人数达到目标后，剩余待确认成员不再阻塞活动。\n\n确定人数：只选择指定数量的成员。发起人优先，其余按照点击“我有空”的先后顺序选择；被选中的成员必须全部确认。")
                GuideSection("五、时间规则", "固定时间：指定某一天的具体时间段，只检查该时间段内有空的参与者。\n\n最近时间：在框选的时间范围内，从最早时刻开始按 15 分钟扫描，返回最早满足人数要求的时段。\n\n人数峰值：在框选的时间范围内比较所有候选时段，选择可参加人数最多的时段；人数相同时选择更早的时段。")
                GuideSection("六、可约范围", "自动匹配必须先框选未来 7 天的大致时间范围，避免活动被安排到凌晨或其他不希望的时间。固定时间规则不需要额外框选，因为它本身已经指定了时间段。")
                GuideSection("七、接龙和确认", "点击“我有空 / 参与”进入接龙池。达到最低人数后，活动进入匹配阶段，入口会暂时关闭。系统找到方案后，被选成员需要点击“确认出席”。只有满足对应人数规则，活动才会正式成立并写入确认成员的日历。")
                GuideSection("八、拒绝、超时和重新匹配", "确定人数模式中，任何被选成员拒绝或超时，都可能导致本轮失败。最低人数不足时也会进入重新匹配。群主或活动创建者可以重新匹配；明确拒绝的人不会自动回到候选池，超时成员可以重新参与。重新匹配后，之前确认过的人也需要再次确认新的时间。")
                GuideSection("九、取消活动", "活动创建者或群主可以取消尚未完成的活动。取消后，相关成员的待确认状态失效；已经写入的群组日程会被释放，并向相关成员生成取消提示。")
                GuideSection("十、状态说明", "接龙中：成员可以加入或退出。\n匹配中：系统正在计算，暂时不能加入。\n等待全员确认：等待被选成员响应。\n等待群主重算：本轮未达到成团条件。\n已成团：活动已确认并写入日历。\n暂无可用时间：当前范围内没有满足条件的时间。")
                Text("提示：群组状态以服务器为准；个人日程仍优先保存在本地。", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了", color = Coral) } }
    )
}

@Composable
private fun GuideSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = MainText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(body, color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
    }
}

@Composable
fun GroupActivityCard(activity: GroupActivityDto, currentUserId: Int?, isOwner: Boolean, onJoin: (Int) -> Unit, onLeave: (Int) -> Unit, onRespond: (Int, String) -> Unit, onRecalculate: (Int) -> Unit, onCancel: (Int) -> Unit) {
    val joined = activity.participants.orEmpty().any { it.id == currentUserId }
    val pending = currentUserId != null && activity.pendingConfirmationIds.orEmpty().contains(currentUserId)
    var showCancelConfirm by remember(activity.id, activity.status) { mutableStateOf(false) }
    val statusLabel = when (activity.status) { "OPEN" -> "接龙中"; "MATCHING" -> "匹配中"; "CONFIRMING" -> "等待全员确认"; "CONFIRMED" -> "已成团"; "RECALC_REQUIRED" -> "等待群主重算"; "NO_AVAILABLE" -> "暂无可用时间"; "CANCELLED" -> "已取消"; else -> activity.status }
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(activity.title, color = MainText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(statusLabel, color = if (activity.status == "CONFIRMED") Green else Blue, fontSize = 12.sp) }
            Text("活动编号：${activity.activityCode.orEmpty().ifBlank { "未同步" }}", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            Text("发起人：${activity.creatorDisplayName?.takeIf { it.isNotBlank() } ?: "未知用户"}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            Text(if (activity.participantMode == "EXACT") "确定人数：${activity.minParticipants} 人" else "最低人数：${activity.minParticipants} 人", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            activity.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp)) }
            Text("当前接龙 ${activity.participants.orEmpty().size} 人", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            activity.proposedStartAt?.let { start ->
                val ruleLabel = when (activity.timeRule) { "FIXED" -> "固定时间"; "EARLIEST" -> "最近时间"; "PEAK" -> "人数峰值"; else -> activity.timeRule }
                Text("拟定时间：${start.replace('T', ' ')} — ${activity.proposedEndAt?.substringAfter('T') ?: ""}", color = MainText, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                Text("匹配规则：$ruleLabel", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
            if (activity.status == "CONFIRMING") {
                Text("已确认 ${activity.confirmedCount} 人 / 待确认 ${activity.pendingCount} 人${if (activity.declinedCount > 0) "（拒绝 ${activity.declinedCount} 人）" else ""}", color = MainText, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                LinearProgressIndicator(progress = { (activity.confirmedCount.toFloat() / (activity.confirmedCount + activity.pendingCount + activity.declinedCount).coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), color = Coral)
                Text("所有拟定参与者都确认后才会正式写入日历。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (!joined && activity.status in setOf("MATCHING", "CONFIRMING", "RECALC_REQUIRED")) Text(if (activity.status == "MATCHING") "系统正在匹配，暂不可加入。" else "活动已进入确认阶段，暂不可加入。", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
            val canManage = isOwner || activity.creatorId == currentUserId
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (!joined && activity.status == "OPEN") Button(onClick = { onJoin(activity.id) }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("我有空 / 参与") }
                if (joined && activity.status == "OPEN") OutlinedButton(onClick = { onLeave(activity.id) }) { Text("退出接龙") }
                if (pending) { OutlinedButton(onClick = { onRespond(activity.id, "DECLINED") }) { Text("拒绝") }; Button(onClick = { onRespond(activity.id, "CONFIRMED") }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("确认出席") } }
                if (canManage && activity.status in setOf("RECALC_REQUIRED", "NO_AVAILABLE")) Button(onClick = { onRecalculate(activity.id) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("重新匹配") }
                if (canManage && activity.status !in setOf("CONFIRMED", "CANCELLED")) TextButton(onClick = { showCancelConfirm = true }) { Text("取消活动", color = Red) }
            }
        }
    }
    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            containerColor = Panel,
            title = { Text("取消群组活动？") },
            text = { Text("取消后，所有相关成员的待确认状态都会失效。", color = Muted) },
            confirmButton = { TextButton(onClick = { showCancelConfirm = false; onCancel(activity.id) }) { Text("确认取消", color = Red) } },
            dismissButton = { TextButton(onClick = { showCancelConfirm = false }) { Text("暂不取消", color = Coral) } }
        )
    }
}

@Composable
fun CreateGroupActivityDialog(onDismiss: () -> Unit, onCreate: (GroupActivityCreateDto) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var minPeople by remember { mutableStateOf("2") }
    var participantMode by remember { mutableStateOf("MINIMUM") }
    var duration by remember { mutableStateOf("60") }
    var rule by remember { mutableStateOf("EARLIEST") }
    val initialDateTime = remember { LocalDateTime.now().plusDays(1).withSecond(0).withNano(0) }
    var deadlineDate by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var deadlineTime by remember { mutableStateOf("23:59") }
    var fixedDate by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var fixedStart by remember { mutableStateOf<String?>("19:00") }
    var fixedEnd by remember { mutableStateOf<String?>("20:00") }
    var windowStart by remember { mutableStateOf<String?>(null) }
    var windowEnd by remember { mutableStateOf<String?>(null) }
    var dateTarget by remember { mutableStateOf<String?>(null) }
    var showDeadlineTime by remember { mutableStateOf(false) }
    var showFixedGrid by remember { mutableStateOf(false) }
    var showWindowGrid by remember { mutableStateOf(false) }
    val deadline = "${deadlineDate}T${deadlineTime}"
    fun isoAt(date: String, time: String): String = if (time == "24:00") {
        "${LocalDate.parse(date).plusDays(1)}T00:00"
    } else "${date}T$time"
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel, title = { Text("发起群组活动") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text("活动名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("说明（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("人数规则", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("MINIMUM" to "最低人数（有空都可确认）", "EXACT" to "确定人数（只选指定人数）").forEach { (value, label) ->
                FilterChip(selected = participantMode == value, onClick = { participantMode = value }, label = { Text(label) }, colors = tempoFilterChipColors())
            }
        }
        OutlinedTextField(minPeople, { minPeople = it.filter(Char::isDigit) }, label = { Text(if (participantMode == "EXACT") "确定参与人数" else "最低参与人数") }, supportingText = { Text(if (participantMode == "EXACT") "只选择指定数量的成员参加" else "达到这个人数后即可成团") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, label = { Text("活动时长（分钟）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("时间规则", color = Muted, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("FIXED" to "固定时间", "EARLIEST" to "最近时间", "PEAK" to "人数峰值").forEach { (value, label) -> FilterChip(selected = rule == value, onClick = { rule = value }, label = { Text(label) }, colors = tempoFilterChipColors()) } }
        Text("响应截止时间", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedButton(onClick = { dateTarget = "deadline" }, modifier = Modifier.weight(1f)) { Text(deadlineDate.toString()) }
            OutlinedButton(onClick = { showDeadlineTime = true }, modifier = Modifier.weight(1f)) { Text(deadlineTime) }
        }
        if (rule == "FIXED") {
            Text("固定时间段", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            OutlinedButton(onClick = { dateTarget = "fixed" }, modifier = Modifier.fillMaxWidth()) { Text("日期：$fixedDate") }
            TimeRangeButton(fixedStart, fixedEnd, onClick = { showFixedGrid = true })
            Text("先点开始格，再点结束格；最小单位 15 分钟。", color = Muted, fontSize = 11.sp)
        } else {
            Text("可约时间范围（必须设置）", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            OutlinedButton(onClick = { showWindowGrid = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (windowStart == null || windowEnd == null) "点击框选未来 7 天范围" else "已框选：${windowStart!!.replace('T', ' ')} — ${windowEnd!!.replace('T', ' ')}")
            }
            Text("最近时间优先返回最早满足人数的时段；人数峰值优先返回可参加人数最多的时段。", color = Muted, fontSize = 11.sp)
        }
    } }, confirmButton = { Button(onClick = {
        onCreate(GroupActivityCreateDto(title.ifBlank { "未命名活动" }, description.ifBlank { null }, duration.toIntOrNull()?.coerceIn(15, 1440) ?: 60, minPeople.toIntOrNull()?.coerceIn(2, 100) ?: 2, participantMode, deadline, rule,
            if (rule == "FIXED") isoAt(fixedDate.toString(), fixedStart ?: "19:00") else null,
            if (rule == "FIXED") isoAt(fixedDate.toString(), fixedEnd ?: "20:00") else null,
            if (rule == "FIXED") null else windowStart,
            if (rule == "FIXED") null else windowEnd))
    }, enabled = title.isNotBlank() && (rule == "FIXED" && fixedStart != null && fixedEnd != null || rule != "FIXED" && windowStart != null && windowEnd != null), colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("发布活动") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
    if (dateTarget != null) DateChoiceDialog(initial = if (dateTarget == "fixed") fixedDate else deadlineDate, onCancel = { dateTarget = null }) { picked -> if (dateTarget == "fixed") fixedDate = picked else deadlineDate = picked; dateTarget = null }
    if (showDeadlineTime) WheelTimeDialog(initial = deadlineTime, onCancel = { showDeadlineTime = false }) { deadlineTime = it; showDeadlineTime = false }
    if (showFixedGrid) TimeGridDialog(initialStart = fixedStart, initialEnd = fixedEnd, date = fixedDate.toString(), onCancel = { showFixedGrid = false }) { s, e -> fixedStart = s; fixedEnd = e; showFixedGrid = false }
    if (showWindowGrid) WeekWindowPicker(onCancel = { showWindowGrid = false }, onSelected = { sd, ed, st, et -> windowStart = isoAt(sd, st); windowEnd = isoAt(ed, et); showWindowGrid = false })
}

@Composable
private fun WheelTimeDialog(initial: String, onCancel: () -> Unit, onSelected: (String) -> Unit) {
    val initialParts = initial.split(":")
    var hour by remember { mutableStateOf(initialParts.getOrNull(0)?.toIntOrNull() ?: 23) }
    var minute by remember { mutableStateOf(initialParts.getOrNull(1)?.toIntOrNull() ?: 0) }
    AlertDialog(onDismissRequest = onCancel, containerColor = Panel, title = { Text("选择时间点") }, text = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WheelColumn((0..23).toList(), hour, { hour = it }, Modifier.weight(1f))
            WheelColumn((0..59).toList(), minute, { minute = it }, Modifier.weight(1f))
        }
    }, confirmButton = { Button(onClick = { onSelected("%02d:%02d".format(hour, minute)) }, colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("确定") } }, dismissButton = { TextButton(onClick = onCancel) { Text("取消") } })
}

@Composable
private fun WheelColumn(values: List<Int>, selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        settled = !listState.isScrollInProgress
        if (!listState.isScrollInProgress) {
            val center = listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset / 2
            val nearest = listState.layoutInfo.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs((item.offset + item.size / 2) - center)
            }
            nearest?.let { item ->
                val value = values.getOrNull(item.index) ?: return@let
                if (value != selected) {
                    onSelected(value)
                }
                val target = (item.index - 2).coerceAtLeast(0)
                if (kotlin.math.abs(item.offset + item.size / 2 - center) > 2) {
                    listState.scrollToItem(target)
                }
            }
        }
    }
    Box(modifier.height(190.dp).fillMaxWidth()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            items(values) { value ->
                Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
                    Text("%02d".format(value), color = MainText, fontWeight = FontWeight.Normal)
                }
            }
        }
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (settled) Coral else Color.Transparent)
                .border(1.dp, Coral, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (settled) Text("%02d".format(selected), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DateChoiceDialog(initial: LocalDate, onCancel: () -> Unit, onSelected: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    AlertDialog(onDismissRequest = onCancel, containerColor = Panel, title = { Text("选择日期") }, text = {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹", color = Coral, fontSize = 24.sp) }
                Text("${month.year}年${month.monthValue}月", color = MainText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                TextButton(onClick = { month = month.plusMonths(1) }) { Text("›", color = Coral, fontSize = 24.sp) }
            }
            Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
            val leading = month.atDay(1).dayOfWeek.value - 1
            val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week -> Row(Modifier.fillMaxWidth()) { week.forEach { day -> Box(Modifier.weight(1f).height(42.dp).padding(2.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = day != null) { day?.let(onSelected) }, contentAlignment = Alignment.Center) { Text(day?.dayOfMonth?.toString() ?: "", color = if (day == initial) Color.White else MainText, modifier = if (day == initial) Modifier.background(Coral, RoundedCornerShape(8.dp)).padding(8.dp) else Modifier) } } } }
        }
    }, confirmButton = { TextButton(onClick = onCancel) { Text("关闭", color = Coral) } })
}

@Composable
fun SimpleInputDialog(title: String, label: String, confirm: String, initialValue: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) }, confirmButton = { Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text(confirm) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun DailyFortuneCard(context: android.content.Context, ownerId: String) {
    val today = LocalDate.now().toString()
    val prefs = remember(ownerId) { context.getSharedPreferences("tempo_daily_fortune", android.content.Context.MODE_PRIVATE) }
    var drawnDate by remember(ownerId) { mutableStateOf(prefs.getString("date", null)) }
    var fortune by remember(ownerId) { mutableStateOf(prefs.getString("fortune", null)) }
    val options = listOf(
        "今日宜主动沟通，重要的事适合现在开始。" to "大吉",
        "今天适合整理计划，稳步推进会有收获。" to "吉",
        "保持耐心，先完成最重要的一件事。" to "平",
        "今天宜放慢节奏，给自己留一点休息时间。" to "小吉"
    )
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("今日抽签", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("每日一次", color = Muted, fontSize = 11.sp)
            }
            if (drawnDate == today && fortune != null) {
                Text(fortune!!, color = MainText, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                Text("今日签运已记录，明天再来抽取", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
            } else {
                Text("抽取一个今日提示，仅作日常娱乐。", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                Button(onClick = {
                    val item = options[Random.nextInt(options.size)]
                    fortune = "${item.second} · ${item.first}"
                    drawnDate = today
                    prefs.edit().putString("date", today).putString("fortune", fortune).apply()
                }, modifier = Modifier.padding(top = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("抽取今日签") }
            }
        }
    }
}

@Composable
fun SettingsPage(user: UserProfile?, themeChoice: ThemeChoice, onThemeChange: (ThemeChoice) -> Unit, onLogin: () -> Unit, onUpdateProfile: (String, String?, String?, String?) -> Unit, onLogout: () -> Unit, aiAccessState: AiAccessState = AiAccessState.Disabled, onVerifyAi: (String) -> Unit = {}, onImport: () -> Unit = {}) {
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
    var newCategoryName by remember { mutableStateOf("") }
    var draggingCategory by remember { mutableStateOf<String?>(null) }
    var showProfileDetails by remember { mutableStateOf(false) }
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
                Text(if (user == null) "未登录 · 日程仅保存在本机" else "账号：${user.accountId}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                user?.let { Text("用户名：${it.username ?: "未设置"}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                if (user != null) {
                    TextButton(onClick = { showProfileDetails = !showProfileDetails }, modifier = Modifier.padding(top = 4.dp)) { Text(if (showProfileDetails) "收起资料" else "展开资料", color = Coral) }
                    if (showProfileDetails) {
                        user.email?.let { Text("邮箱：$it", color = Muted, fontSize = 12.sp) }
                        Text("昵称：${user.displayName}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                        user.phone?.takeIf { it.isNotBlank() }?.let { Text("电话：$it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                        user.hobbies?.takeIf { it.isNotBlank() }?.let { Text("爱好：$it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                        user.signature?.takeIf { it.isNotBlank() }?.let { Text("签名：$it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
                    }
                }
                if (user == null) Button(onClick = onLogin, modifier = Modifier.padding(top = 14.dp)) { Text("登录 / 注册") }
            }
        }
        DailyFortuneCard(context, user?.id ?: "guest")
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
                Text("长按标签后上下拖动，可调整创建日程中的显示顺序", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp))
                categories.forEachIndexed { index, option ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp)
                            .pointerInput(categories) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingCategory = option.name },
                                    onDragCancel = { draggingCategory = null },
                                    onDragEnd = { draggingCategory = null },
                                    onDrag = { change, dragAmount ->
                                        val currentIndex = categories.indexOfFirst { it.name == option.name }
                                        val targetIndex = when {
                                            dragAmount.y > 12f -> currentIndex + 1
                                            dragAmount.y < -12f -> currentIndex - 1
                                            else -> currentIndex
                                        }
                                        if (targetIndex in categories.indices && targetIndex != currentIndex) {
                                            val reordered = categories.toMutableList().apply { add(targetIndex, removeAt(currentIndex)) }
                                            categories = CategoryStore.reorder(context, reordered)
                                            change.consume()
                                        }
                                    }
                                )
                            }
                            .background(if (draggingCategory == option.name) Coral.copy(alpha = .18f) else Color.Transparent, RoundedCornerShape(10.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("☷", color = Muted, modifier = Modifier.padding(end = 7.dp))
                        Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(option.colorHex)))
                        Text(option.name, color = MainText, modifier = Modifier.padding(start = 9.dp).weight(1f))
                        TextButton(onClick = { categories = CategoryStore.remove(context, option.name) }) { Text("删除", color = Red) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newCategoryName, { newCategoryName = it }, placeholder = { Text("新增标签") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val name = newCategoryName.trim()
                        if (name.isNotEmpty() && categories.none { it.name == name }) {
                            val colors = listOf("#6B8FD6", "#6BB58A", "#C58BD8", "#D69A5A", "#D9798A")
                            categories = CategoryStore.add(context, CategoryOption(name, colors[categories.size % colors.size]))
                            newCategoryName = ""
                        }
                    }, enabled = newCategoryName.trim().isNotEmpty()) { Text("添加", color = Coral) }
                }
            }
        }
        Text("数据导入", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(17.dp)) {
                Text("导入模板日程", color = MainText, fontWeight = FontWeight.Bold)
                Text("支持 CSV、XLSX 和 ICS；导入后保存在本机，和普通日程一样可编辑。", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                Button(onClick = onImport, modifier = Modifier.padding(top = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Text("选择文件") }
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
@Composable
fun ImportEventsDialog(uri: Uri, onCancel: () -> Unit, onImport: (List<CalendarEvent>) -> Unit) {
    val context = LocalContext.current
    var parsed by remember(uri) { mutableStateOf<ImportParseResult?>(null) }
    var loading by remember(uri) { mutableStateOf(true) }
    var selectedCategory by remember(uri) { mutableStateOf(CategoryStore.load(context).firstOrNull()?.name ?: "工作") }
    var categoryMenu by remember { mutableStateOf(false) }
    val categories = remember { CategoryStore.load(context) }
    LaunchedEffect(uri) {
        loading = true
        parsed = withContext(Dispatchers.IO) { CalendarImportParser.parse(context, uri) }
        loading = false
    }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Panel,
        title = { Text("导入本地日程") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Coral) }
                } else {
                    val data = parsed ?: ImportParseResult(emptyList(), listOf("无法读取文件"))
                    Text("已识别 ${data.items.size} 条有效日程", color = MainText, fontWeight = FontWeight.Bold)
                    Text("导入后统一标记为硬性事务，并写入本机日历。", color = Muted, fontSize = 12.sp)
                    Box {
                        OutlinedButton(onClick = { categoryMenu = true }) { Text("分类：$selectedCategory", color = MainText) }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            categories.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { selectedCategory = option.name; categoryMenu = false }) }
                        }
                    }
                    data.items.take(8).forEach { item ->
                        Surface(color = Bg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(item.title, color = MainText, fontWeight = FontWeight.Medium)
                                Text("${item.start} — ${item.end}", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    if (data.items.size > 8) Text("其余 ${data.items.size - 8} 条将在导入后写入", color = Muted, fontSize = 12.sp)
                    data.errors.take(5).forEach { Text("⚠ $it", color = Red, fontSize = 12.sp) }
                    if (data.errors.size > 5) Text("其余 ${data.errors.size - 5} 条格式错误已省略", color = Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            val data = parsed
            Button(
                onClick = {
                    if (data != null) onImport(data.items.map { item -> CalendarEvent("local-import-${UUID.randomUUID()}", "guest", item.title, item.start, item.end, selectedCategory, EventStatus.HARD) })
                },
                enabled = !loading && data?.items?.isNotEmpty() == true,
                colors = ButtonDefaults.buttonColors(containerColor = Coral)
            ) { Text("导入有效日程") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消", color = Muted) } }
    )
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
fun CreateEventDialog(date: String, aiState: AiVoiceState = AiVoiceState.Idle, aiEnabled: Boolean = true, onVoice: () -> Unit = {}, onCancelVoice: () -> Unit = {}, existingEvents: List<CalendarEvent>, onSave: (List<CalendarEvent>) -> Unit, onCancel: () -> Unit) {
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
    var repeatRule by remember { mutableStateOf("NONE") }
    var repeatUntil by remember { mutableStateOf(LocalDate.parse(date).plusDays(30)) }
    var showRepeatEndPicker by remember { mutableStateOf(false) }
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
            Text("重复规则", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("NONE" to "不重复", "DAILY" to "每天", "WEEKLY" to "每周同一时刻", "WORKDAYS" to "工作日", "HOLIDAYS" to "节假日").forEach { (value, label) ->
                    FilterChip(selected = repeatRule == value, onClick = { repeatRule = value }, label = { Text(label) }, colors = tempoFilterChipColors())
                }
            }
            if (repeatRule != "NONE") {
                OutlinedButton(onClick = { showRepeatEndPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) { Text("重复至：$repeatUntil") }
                Text("节假日依据当前万年历中的节日数据判断。", color = Muted, fontSize = 10.sp)
            }
            Text("对外状态", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("硬性", "空闲", "机动").forEach { item -> FilterChip(selected = status == item, onClick = { status = item }, label = { Text(item) }, colors = tempoFilterChipColors()) } }
        }
    }, confirmButton = { Button(onClick = {
        val finalName = name.ifBlank { "未命名日程" }
        val eventStatus = when (status) { "空闲" -> EventStatus.FREE; "机动" -> EventStatus.FLEXIBLE; else -> EventStatus.HARD }
        if (start != null && end != null) {
            val savedEnd = if (end == "00:00" && start != "00:00") "24:00" else end!!
            val firstDate = LocalDate.parse(selectedDate)
            val dates = generateRepeatDates(firstDate, repeatUntil, repeatRule)
            onSave(dates.map { itemDate -> CalendarEvent("local-${UUID.randomUUID()}", "me", finalName, "$itemDate ${start!!}", "$itemDate $savedEnd", category, eventStatus, if (eventStatus == EventStatus.FLEXIBLE) 30 else 0) })
        }
    }, colors = ButtonDefaults.buttonColors(containerColor = Coral), enabled = start != null && end != null) { Text("保存日程") } }, dismissButton = { TextButton(onClick = onCancel) { Text("取消") } })
    if (showPicker) TimeGridDialog(initialStart = start, initialEnd = end, date = date, existingEvents = existingEvents, onCancel = { showPicker = false }, onSelect = { s, e -> start = s; end = e; showPicker = false })
    if (showRepeatEndPicker) DateChoiceDialog(initial = repeatUntil, onCancel = { showRepeatEndPicker = false }) { picked -> repeatUntil = picked; showRepeatEndPicker = false }
}

private fun generateRepeatDates(start: LocalDate, until: LocalDate, rule: String): List<LocalDate> {
    if (rule == "NONE" || until < start) return listOf(start)
    val result = mutableListOf<LocalDate>()
    var cursor = start
    while (cursor <= until && result.size < 370) {
        val include = when (rule) {
            "DAILY" -> true
            "WEEKLY" -> cursor.dayOfWeek == start.dayOfWeek
            "WORKDAYS" -> cursor.dayOfWeek.value <= 5
            "HOLIDAYS" -> !CalendarInfoProvider.info(cursor).festival.isNullOrBlank()
            else -> cursor == start
        }
        if (include) result += cursor
        cursor = cursor.plusDays(1)
    }
    return result.ifEmpty { listOf(start) }
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
