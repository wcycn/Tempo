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

    fun refresh() = viewModelScope.launch {
        if (store.get().isNullOrBlank()) {
            _items.value = emptyList()
            return@launch
        }
        runCatching { api.invites() }.onSuccess { _items.value = it }.onFailure { _message.value = friendly(it) }
    }
    fun create(receiverId: Int, title: String, startAt: String, endAt: String) = viewModelScope.launch {
        runCatching { api.createInvite(InviteCreateDto(receiverId, title, startAt = startAt, endAt = endAt)) }
            .onSuccess { refresh(); _message.value = "邀约已发送" }.onFailure { _message.value = friendly(it) }
    }
    fun match(receiverId: Int, durationMinutes: Int, windowStartDate: String? = null, windowEndDate: String? = null, windowStartTime: String? = null, windowEndTime: String? = null) = viewModelScope.launch {
        runCatching { api.match(MatchRequestDto(receiverId, durationMinutes, java.time.LocalDate.now().toString(), windowStartDate = windowStartDate, windowEndDate = windowEndDate, windowStartTime = windowStartTime, windowEndTime = windowEndTime)) }
            .onSuccess { _options.value = it }.onFailure { _options.value = emptyList(); _message.value = friendly(it) }
    }
    fun respond(id: Int, status: String, onAccepted: (InviteDto) -> Unit) = viewModelScope.launch {
        runCatching { api.respondInvite(id, com.hutong.calendar.data.FriendResponseDto(status)) }
            .onSuccess { item -> refresh(); if (status == "ACCEPTED") onAccepted(item) }
            .onFailure { _message.value = friendly(it) }
    }
    fun clearMessage() { _message.value = null }
    fun clearOptions() { _options.value = emptyList() }
    private fun friendly(error: Throwable) = if (error is HttpException && error.code() in 500..599) "服务器暂时不可用，请稍后重试" else "邀约操作失败，请稍后重试"
}
