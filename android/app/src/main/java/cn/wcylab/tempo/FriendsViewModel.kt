package cn.wcylab.tempo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.wcylab.tempo.data.FriendUserDto
import cn.wcylab.tempo.data.TempoApiFactory
import cn.wcylab.tempo.data.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
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
    private val _friendships = MutableStateFlow<List<cn.wcylab.tempo.data.FriendshipDto>>(emptyList())
    val friendships: StateFlow<List<cn.wcylab.tempo.data.FriendshipDto>> = _friendships.asStateFlow()
    private val _availability = MutableStateFlow<List<cn.wcylab.tempo.data.AvailabilityBlockDto>>(emptyList())
    val availability: StateFlow<List<cn.wcylab.tempo.data.AvailabilityBlockDto>> = _availability.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun refresh() = viewModelScope.launch {
        if (store.get() == null) { _friendships.value = emptyList(); return@launch }
        _loading.value = true
        runCatching { retryRequest { api.listFriends() } }.onSuccess { _friendships.value = it }
        _loading.value = false
    }

    fun search(query: String) = viewModelScope.launch {
        if (query.trim().isEmpty() || store.get() == null) { _results.value = emptyList(); return@launch }
        try { _results.value = retryRequest { api.searchFriends(query.trim()) }; _message.value = null }
        catch (error: Exception) { _results.value = emptyList(); _message.value = friendFacingError(error) }
    }

    fun add(user: FriendUserDto) = viewModelScope.launch {
        if (user.id.toString() == store.cachedUser()?.id) {
            _message.value = "不能添加自己"
            return@launch
        }
        try { retryRequest { api.sendFriendRequest(cn.wcylab.tempo.data.FriendRequestDto(user.id)) }; refresh(); _message.value = "已向 ${user.displayName} 发送好友申请" }
        catch (error: Exception) { _message.value = friendFacingError(error) }
    }

    fun respond(friendshipId: Int, status: String) = viewModelScope.launch {
        runCatching { retryRequest { api.respondFriend(friendshipId, cn.wcylab.tempo.data.FriendResponseDto(status)) } }
            .onSuccess { refresh(); _message.value = if (status == "ACCEPTED") "已添加好友" else "已拒绝好友申请" }
            .onFailure { _message.value = friendFacingError(it as? Exception ?: Exception()) }
    }

    fun remove(friendshipId: Int) = viewModelScope.launch {
        // 删除是不可逆操作，不能在响应中断时自动重试。
        runCatching { api.deleteFriend(friendshipId) }
            .onSuccess { refresh(); _message.value = "好友关系已删除" }
            .onFailure { _message.value = friendFacingError(it as? Exception ?: Exception()) }
    }

    fun loadAvailability(friendId: Int, date: String) = viewModelScope.launch {
        _loading.value = true
        val center = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
        val monday = center.minusDays((center.dayOfWeek.value - 1).toLong())
        runCatching { (0..6).flatMap { day -> retryRequest { api.friendAvailability(friendId, monday.plusDays(day.toLong()).toString()) } } }
            .onSuccess { _availability.value = it }
            .onFailure { _availability.value = emptyList(); _message.value = friendFacingError(it as? Exception ?: Exception()) }
        _loading.value = false
    }

    fun clearMessage() { _message.value = null }
    fun clearSearch() { _results.value = emptyList() }

    private suspend fun <T> retryRequest(block: suspend () -> T): T {
        var last: Exception? = null
        repeat(3) { attempt ->
            try { return block() } catch (error: Exception) {
                last = error
                if (attempt < 2) delay(300L * (attempt + 1))
            }
        }
        throw last ?: IllegalStateException("request failed")
    }

    private fun friendFacingError(error: Exception): String = when (error) {
        is HttpException -> when (error.code()) {
            401 -> "登录状态已失效，请重新登录"
            409 -> "好友申请已经发送过，或双方已经是好友"
            422 -> "请输入有效的用户名或昵称"
            in 500..599 -> "服务器暂时不可用，请稍后重试"
            else -> "操作失败，请稍后重试"
        }
        else -> "好友网络请求失败：${error.javaClass.simpleName}${error.message?.let { "（$it）" } ?: ""}"
    }
}
