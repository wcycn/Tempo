package cn.wcylab.tempo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.wcylab.tempo.data.InviteCreateDto
import cn.wcylab.tempo.data.InviteDto
import cn.wcylab.tempo.data.MatchOptionDto
import cn.wcylab.tempo.data.MatchRequestDto
import cn.wcylab.tempo.data.TempoApiFactory
import cn.wcylab.tempo.data.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
        runCatching { retryRequest { api.invites() } }.onSuccess { _items.value = it }
        _loading.value = false
    }
    fun create(receiverId: Int, title: String, startAt: String, endAt: String, onCreated: (InviteDto) -> Unit = {}) = viewModelScope.launch {
        // 创建请求不能自动重试：若服务器已创建成功但响应中断，重试会生成重复邀约。
        runCatching { api.createInvite(InviteCreateDto(receiverId, title, startAt = startAt, endAt = endAt)) }
            .onSuccess { item -> refresh(); onCreated(item); _message.value = "邀约已发送" }.onFailure { _message.value = friendly(it) }
    }
    fun match(receiverId: Int, durationMinutes: Int, windowStartDate: String? = null, windowEndDate: String? = null, windowStartTime: String? = null, windowEndTime: String? = null) = viewModelScope.launch {
        _loading.value = true
        runCatching { api.matchOptions(receiverId, durationMinutes, java.time.LocalDate.now().toString(), 7, windowStartDate, windowEndDate, windowStartTime, windowEndTime) }
            .onSuccess { _options.value = it }.onFailure { _options.value = emptyList(); _message.value = friendly(it) }
        _loading.value = false
    }
    fun respond(id: Int, status: String, onCompleted: (InviteDto) -> Unit) = viewModelScope.launch {
        // 应答会改变状态，避免重试后把已成功的操作误报成“邀约不存在”。
        runCatching { api.respondInvite(id, cn.wcylab.tempo.data.FriendResponseDto(status)) }
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
    private fun friendly(error: Throwable) = when (error) {
        is HttpException -> when (error.code()) {
            401 -> "登录状态已失效，请重新登录"
            403 -> "没有权限执行此邀约操作"
            404 -> "好友或邀约不存在，可能已被删除"
            409 -> "该时间已发生冲突，请重新扫描"
            422 -> "邀约时间或活动信息不符合要求"
            in 500..599 -> "服务器暂时不可用，请稍后重试"
            else -> "邀约操作失败（HTTP ${error.code()}）"
        }
        else -> "邀约网络请求失败：${error.javaClass.simpleName}${error.message?.let { "（$it）" } ?: ""}"
    }
}
