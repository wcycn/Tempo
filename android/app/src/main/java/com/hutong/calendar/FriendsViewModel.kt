package com.hutong.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hutong.calendar.data.FriendUserDto
import com.hutong.calendar.data.TempoApiFactory
import com.hutong.calendar.data.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDate

class FriendsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TokenStore(application)
    private val api = TempoApiFactory.create { store.get() }
    private val _results = MutableStateFlow<List<FriendUserDto>>(emptyList())
    val results: StateFlow<List<FriendUserDto>> = _results.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _friendships = MutableStateFlow<List<com.hutong.calendar.data.FriendshipDto>>(emptyList())
    val friendships: StateFlow<List<com.hutong.calendar.data.FriendshipDto>> = _friendships.asStateFlow()
    private val _availability = MutableStateFlow<List<com.hutong.calendar.data.AvailabilityBlockDto>>(emptyList())
    val availability: StateFlow<List<com.hutong.calendar.data.AvailabilityBlockDto>> = _availability.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        if (store.get() == null) { _friendships.value = emptyList(); return@launch }
        runCatching { api.listFriends() }.onSuccess { _friendships.value = it }
            .onFailure { _message.value = friendFacingError(it as? Exception ?: Exception()) }
    }

    fun search(query: String) = viewModelScope.launch {
        if (query.trim().isEmpty() || store.get() == null) { _results.value = emptyList(); return@launch }
        try { _results.value = api.searchFriends(query.trim()); _message.value = null }
        catch (error: Exception) { _results.value = emptyList(); _message.value = friendFacingError(error) }
    }

    fun add(user: FriendUserDto) = viewModelScope.launch {
        if (user.id.toString() == store.cachedUser()?.id) {
            _message.value = "不能添加自己"
            return@launch
        }
        try { api.sendFriendRequest(com.hutong.calendar.data.FriendRequestDto(user.id)); _message.value = "已向 ${user.displayName} 发送好友申请" }
        catch (error: Exception) { _message.value = friendFacingError(error) }
    }

    fun respond(friendshipId: Int, status: String) = viewModelScope.launch {
        runCatching { api.respondFriend(friendshipId, com.hutong.calendar.data.FriendResponseDto(status)) }
            .onSuccess { refresh(); _message.value = if (status == "ACCEPTED") "已添加好友" else "已拒绝好友申请" }
            .onFailure { _message.value = friendFacingError(it as? Exception ?: Exception()) }
    }

    fun remove(friendshipId: Int) = viewModelScope.launch {
        runCatching { api.deleteFriend(friendshipId) }
            .onSuccess { refresh(); _message.value = "好友关系已删除" }
            .onFailure { _message.value = friendFacingError(it as? Exception ?: Exception()) }
    }

    fun loadAvailability(friendId: Int, date: String) = viewModelScope.launch {
        val center = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
        val monday = center.minusDays((center.dayOfWeek.value - 1).toLong())
        runCatching { (0..6).flatMap { api.friendAvailability(friendId, monday.plusDays(it.toLong()).toString()) } }
            .onSuccess { _availability.value = it }
            .onFailure { _availability.value = emptyList(); _message.value = friendFacingError(it as? Exception ?: Exception()) }
    }

    fun clearMessage() { _message.value = null }
    fun clearSearch() { _results.value = emptyList() }

    private fun friendFacingError(error: Exception): String = when (error) {
        is HttpException -> when (error.code()) {
            401 -> "登录状态已失效，请重新登录"
            409 -> "好友申请已经发送过，或双方已经是好友"
            422 -> "请输入有效的用户名或昵称"
            in 500..599 -> "服务器暂时不可用，请稍后重试"
            else -> "操作失败，请稍后重试"
        }
        else -> "网络不可用，请检查网络连接后重试"
    }
}
