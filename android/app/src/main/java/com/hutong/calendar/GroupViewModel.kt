package com.hutong.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hutong.calendar.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class GroupViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TokenStore(application)
    private val api = TempoApiFactory.create { store.get() }
    private val _groups = MutableStateFlow<List<GroupDto>>(emptyList())
    val groups = _groups.asStateFlow()
    private val _activities = MutableStateFlow<List<GroupActivityDto>>(emptyList())
    val activities = _activities.asStateFlow()
    private val _invitations = MutableStateFlow<List<GroupInvitationDto>>(emptyList())
    val invitations = _invitations.asStateFlow()
    private val _selectedGroupId = MutableStateFlow<Int?>(null)
    val selectedGroupId = _selectedGroupId.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun refresh() = viewModelScope.launch {
        if (store.get().isNullOrBlank()) { _groups.value = emptyList(); _activities.value = emptyList(); _invitations.value = emptyList(); return@launch }
        _loading.value = true
        runCatching { api.notifications() }.onSuccess { notifications ->
            notifications.firstOrNull()?.let { item ->
                _message.value = "${item.title}\n${item.body}"
                runCatching { api.markNotificationRead(item.id) }
            }
        }.onFailure { _message.value = friendly(it) }
        runCatching { api.groupInvitations() }.onSuccess { _invitations.value = it }.onFailure { _message.value = friendly(it) }
        runCatching { api.groups() }.onSuccess { list ->
            _groups.value = list
            val selected = _selectedGroupId.value?.let { id -> list.firstOrNull { it.id == id } } ?: list.firstOrNull()
            _selectedGroupId.value = selected?.id
            if (selected != null) loadActivities(selected.id) else _activities.value = emptyList()
        }.onFailure { _message.value = friendly(it) }
        _loading.value = false
    }
    fun selectGroup(id: Int) = viewModelScope.launch { _selectedGroupId.value = id; loadActivities(id) }
    private suspend fun loadActivities(id: Int) { runCatching { api.groupActivities(id) }.onSuccess { _activities.value = it }.onFailure { _message.value = friendly(it) } }
    fun createGroup(name: String) = viewModelScope.launch { runCatching { api.createGroup(GroupCreateDto(name.trim())) }.onSuccess { group -> _groups.value = _groups.value + group; _selectedGroupId.value = group.id; _activities.value = emptyList(); _message.value = "群组已创建" }.onFailure { _message.value = friendly(it) } }
    fun renameGroup(name: String) = viewModelScope.launch { _selectedGroupId.value?.let { id -> runCatching { api.updateGroup(id, GroupCreateDto(name.trim())) }.onSuccess { group -> _groups.value = _groups.value.map { if (it.id == group.id) group else it }; _message.value = "群名已更新" }.onFailure { _message.value = friendly(it) } } }
    fun createActivity(payload: GroupActivityCreateDto) = viewModelScope.launch { _selectedGroupId.value?.let { id -> runCatching { api.createGroupActivity(id, payload) }.onSuccess { _activities.value = listOf(it) + _activities.value; _message.value = "活动已发布" }.onFailure { _message.value = friendly(it) } } }
    fun removeMember(userId: Int) = viewModelScope.launch { _selectedGroupId.value?.let { id -> runCatching { api.removeGroupMember(id, userId) }.onSuccess { refresh(); _message.value = "成员已移出群组" }.onFailure { _message.value = friendly(it) } } }
    fun addMember(userId: Int) = viewModelScope.launch { _selectedGroupId.value?.let { id -> runCatching { api.addGroupMember(id, GroupMemberCreateDto(userId)) }.onSuccess { _message.value = "入群邀请已发送，等待对方确认" }.onFailure { _message.value = friendly(it) } } }
    fun respondInvitation(id: Int, status: String) = viewModelScope.launch { runCatching { api.respondGroupInvitation(id, GroupInvitationResponseDto(status)) }.onSuccess { _invitations.value = _invitations.value.filterNot { it.id == id }; refresh(); _message.value = if (status == "ACCEPTED") "已加入群组" else "已拒绝入群邀请" }.onFailure { _message.value = friendly(it) } }
    fun join(id: Int) = action { api.joinGroupActivity(id) }
    fun leave(id: Int) = action { api.leaveGroupActivity(id) }
    fun respond(id: Int, status: String) = action { api.respondGroupActivity(id, FriendResponseDto(status)) }
    fun recalculate(id: Int) = action { api.recalculateGroupActivity(id) }
    fun cancel(id: Int) = action { api.cancelGroupActivity(id) }
    private fun action(call: suspend () -> GroupActivityDto) = viewModelScope.launch { runCatching { call() }.onSuccess { item -> _activities.value = _activities.value.map { if (it.id == item.id) item else it }; _message.value = "操作已完成" }.onFailure { _message.value = friendly(it) } }
    fun clearMessage() { _message.value = null }
    private fun friendly(error: Throwable) = when (error) { is HttpException -> when (error.code()) { 401 -> "登录状态已失效，请重新登录"; 403 -> "你没有权限执行此操作"; 409 -> "当前群组活动状态不允许此操作"; 422 -> "活动参数不符合要求"; else -> "群组操作失败（HTTP ${error.code()}）" }; else -> "网络不可用，请稍后重试" }
}
