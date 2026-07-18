package com.hutong.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hutong.calendar.data.InviteCreateDto
import com.hutong.calendar.data.InviteDto
import com.hutong.calendar.data.MatchOptionDto
import com.hutong.calendar.data.MatchRequestDto
import com.hutong.calendar.data.TempoApiFactory
import com.hutong.calendar.data.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class InvitesViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TokenStore(application)
    private val api = TempoApiFactory.create { store.get() }
    private val _items = MutableStateFlow<List<InviteDto>>(emptyList())
    val items = _items.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _options = MutableStateFlow<List<MatchOptionDto>>(emptyList())
    val options = _options.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    fun refresh() = viewModelScope.launch {
        if (store.get().isNullOrBlank()) {
            _items.value = emptyList()
            return@launch
        }
        _loading.value = true
        runCatching { api.invites() }.onSuccess { _items.value = it }.onFailure { _message.value = friendly(it) }
        _loading.value = false
    }
    fun create(receiverId: Int, title: String, startAt: String, endAt: String, onCreated: (InviteDto) -> Unit = {}) = viewModelScope.launch {
        runCatching { api.createInvite(InviteCreateDto(receiverId, title, startAt = startAt, endAt = endAt)) }
            .onSuccess { item -> refresh(); onCreated(item); _message.value = "邀约已发送" }.onFailure { _message.value = friendly(it) }
    }
    fun match(receiverId: Int, durationMinutes: Int, windowStartDate: String? = null, windowEndDate: String? = null, windowStartTime: String? = null, windowEndTime: String? = null) = viewModelScope.launch {
        _loading.value = true
        runCatching { api.match(MatchRequestDto(receiverId, durationMinutes, java.time.LocalDate.now().toString(), windowStartDate = windowStartDate, windowEndDate = windowEndDate, windowStartTime = windowStartTime, windowEndTime = windowEndTime)) }
            .onSuccess { _options.value = it }.onFailure { _options.value = emptyList(); _message.value = friendly(it) }
        _loading.value = false
    }
    fun respond(id: Int, status: String, onCompleted: (InviteDto) -> Unit) = viewModelScope.launch {
        runCatching { api.respondInvite(id, com.hutong.calendar.data.FriendResponseDto(status)) }
            .onSuccess { item -> refresh(); onCompleted(item) }
            .onFailure { _message.value = friendly(it) }
    }
    fun delete(id: Int) = viewModelScope.launch {
        runCatching { api.deleteInvite(id) }
            .onSuccess { _items.value = _items.value.filterNot { it.id == id }; _message.value = "已删除这条邀约记录" }
            .onFailure { _message.value = friendly(it) }
    }
    fun clearMessage() { _message.value = null }
    fun clearOptions() { _options.value = emptyList() }
    private fun friendly(error: Throwable) = if (error is HttpException && error.code() in 500..599) "服务器暂时不可用，请稍后重试" else "邀约操作失败，请稍后重试"
}
