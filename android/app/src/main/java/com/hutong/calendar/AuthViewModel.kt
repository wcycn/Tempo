package com.hutong.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hutong.calendar.data.AuthSession
import com.hutong.calendar.data.TempoApiFactory
import com.hutong.calendar.data.TokenStore
import com.hutong.calendar.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        if (tokenStore.get() != null) refreshUser()
    }

    fun login(account: String, password: String) = runAuth {
        api.login(com.hutong.calendar.data.LoginRequestDto(account, password))
    }

    fun register(username: String, email: String, password: String, displayName: String) = runAuth {
        api.register(com.hutong.calendar.data.RegisterRequestDto(username, email, password, displayName))
    }

    fun updateProfile(displayName: String, phone: String?, hobbies: String?, signature: String?) = viewModelScope.launch {
        val current = _state.value as? AuthState.LoggedIn ?: return@launch
        try {
            val user = api.updateProfile(com.hutong.calendar.data.ProfileUpdateRequestDto(displayName.trim(), phone?.trim(), hobbies?.trim(), signature?.trim()))
            val session = AuthSession(current.session.accessToken, user.toProfile())
            tokenStore.save(session)
            _state.value = AuthState.LoggedIn(session)
        } catch (error: Exception) {
            _state.value = AuthState.Error(userFacingError(error, "资料保存失败"))
        }
    }

    fun updateDisplayName(displayName: String) = updateProfile(displayName, null, null, null)

    fun logout() {
        tokenStore.clear()
        _state.value = AuthState.LoggedOut
    }

    private fun refreshUser() = viewModelScope.launch {
        val savedToken = tokenStore.get()
        try {
            val user = api.me()
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

    private fun runAuth(block: suspend () -> com.hutong.calendar.data.AuthResponseDto) = viewModelScope.launch {
        _state.value = AuthState.Loading
        try {
            val response = block()
            val session = AuthSession(response.accessToken, response.user.toProfile())
            tokenStore.save(session)
            _state.value = AuthState.LoggedIn(session)
        } catch (error: Exception) {
            _state.value = AuthState.Error(userFacingError(error, "网络请求失败"))
        }
    }
}

private fun com.hutong.calendar.data.UserDto.toProfile() = UserProfile(id.toString(), displayName, email = email, accountId = accountId.toString(), phone = phone, hobbies = hobbies, signature = signature)

private fun userFacingError(error: Exception, fallback: String): String = when (error) {
    is HttpException -> when (error.code()) {
        401 -> "账号或密码错误"
        409 -> "用户名或邮箱已被占用"
        422 -> "输入内容不符合要求，请检查后重试"
        in 500..599 -> "服务器暂时不可用，请稍后重试"
        else -> fallback
    }
    else -> "网络不可用，请检查网络连接后重试"
}
