package cn.wcylab.tempo

import android.app.Application
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.wcylab.tempo.data.AuthSession
import cn.wcylab.tempo.data.TempoApiFactory
import cn.wcylab.tempo.data.TokenStore
import cn.wcylab.tempo.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val session: AuthSession) : AuthState
    data class Error(val message: String) : AuthState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val api = TempoApiFactory.create { tokenStore.get() }
    private val _state = MutableStateFlow<AuthState>(if (tokenStore.get() == null) AuthState.LoggedOut else AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        if (tokenStore.get() != null) refreshUser()
    }

    fun login(account: String, password: String) = runAuth {
        api.login(cn.wcylab.tempo.data.LoginRequestDto(account, password))
    }

    fun register(username: String, email: String, password: String, displayName: String) = runAuth {
        api.register(cn.wcylab.tempo.data.RegisterRequestDto(username, email, password, displayName))
    }

    fun updateProfile(displayName: String, phone: String?, hobbies: String?, signature: String?) = viewModelScope.launch {
        val current = _state.value as? AuthState.LoggedIn ?: return@launch
        try {
            _message.value = null
            val user = retryRequest { api.updateProfile(cn.wcylab.tempo.data.ProfileUpdateRequestDto(displayName.trim(), phone?.trim(), hobbies?.trim(), signature?.trim())) }
            val session = AuthSession(current.session.accessToken, user.toProfile())
            tokenStore.save(session)
            _state.value = AuthState.LoggedIn(session)
        } catch (error: Exception) {
            _state.value = current
            _message.value = profileFacingError(error)
        }
    }

    fun clearMessage() { _message.value = null }

    fun updateDisplayName(displayName: String) = updateProfile(displayName, null, null, null)

    fun logout() = viewModelScope.launch {
            runCatching { if (tokenStore.get() != null) retryRequest { api.logout() } }
        tokenStore.clear()
        _state.value = AuthState.LoggedOut
    }

    private fun refreshUser() = viewModelScope.launch {
        val savedToken = tokenStore.get()
        try {
            val user = retryRequest { api.me() }
            _state.value = AuthState.LoggedIn(AuthSession(savedToken.orEmpty(), user.toProfile()))
        } catch (error: Exception) {
            val cachedUser = tokenStore.cachedUser()
            _state.value = if (cachedUser != null) {
                AuthState.LoggedIn(AuthSession(savedToken.orEmpty(), cachedUser))
            } else {
                tokenStore.clear()
                AuthState.Error(userFacingError(error, "登录状态已失效"))
            }
        }
    }

    private fun runAuth(block: suspend () -> cn.wcylab.tempo.data.AuthResponseDto) = viewModelScope.launch {
        _state.value = AuthState.Loading
        try {
            // OkHttp 已会处理建连失败的安全重试，认证请求不再额外连续发送三次。
            val response = block()
            val session = AuthSession(response.accessToken, response.user.toProfile())
            tokenStore.save(session)
            _state.value = AuthState.LoggedIn(session)
        } catch (error: Exception) {
            _state.value = AuthState.Error(userFacingError(error, "网络请求失败"))
        }
    }

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
}

private fun cn.wcylab.tempo.data.UserDto.toProfile() = UserProfile(id.toString(), displayName, email = email, accountId = accountId.toString(), phone = phone, hobbies = hobbies, signature = signature, username = username)

private fun userFacingError(error: Exception, fallback: String): String = when (error) {
    is HttpException -> when (error.code()) {
        401 -> "账号或密码错误"
        409 -> "用户名或邮箱已被占用"
        422 -> "输入内容不符合要求，请检查后重试"
        in 500..599 -> "服务器暂时不可用，请稍后重试"
        else -> fallback
    }
    is UnknownHostException -> "无法解析服务器地址，请检查手机网络或 DNS"
    is SSLHandshakeException -> "服务器安全连接失败，请检查系统时间或证书"
    is SocketTimeoutException -> "连接服务器超时，请稍后重试"
    is ConnectException -> "无法连接服务器，请检查网络或服务器状态"
    else -> transportError(error)
}

private fun profileFacingError(error: Exception): String = when (error) {
    is HttpException -> when (error.code()) {
        401 -> "登录状态已失效，请重新登录后再保存资料"
        404 -> "服务器尚未启用资料保存接口，请重启后端服务"
        422 -> "资料内容不符合要求，请检查昵称和文字长度"
        in 500..599 -> "服务器保存资料失败，请稍后重试"
        else -> "资料保存失败（HTTP ${error.code()}）"
    }
    is UnknownHostException -> "无法解析服务器地址，请检查手机网络或 DNS"
    is SSLHandshakeException -> "服务器安全连接失败，请检查系统时间或证书"
    is SocketTimeoutException -> "连接服务器超时，请稍后重试"
    is ConnectException -> "无法连接服务器，请检查网络或服务器状态"
    else -> transportError(error)
}

private fun transportError(error: Exception): String {
    val detail = error.message
        ?.replace(Regex("https?://\\S+"), "服务器")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    return if (detail == null) {
        "网络请求失败：${error.javaClass.simpleName}"
    } else {
        "网络请求失败：${error.javaClass.simpleName}（$detail）"
    }
}
