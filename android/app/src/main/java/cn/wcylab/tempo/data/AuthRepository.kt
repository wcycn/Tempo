package cn.wcylab.tempo.data

/**
 * 登录边界。实现类负责调用后端并将 Token 安全保存到 Android Keystore/Encrypted storage；
 * 页面不读取数据库，也不自行拼接登录请求。
 */
interface AuthRepository {
    suspend fun register(username: String, email: String, password: String, displayName: String): AuthSession
    suspend fun login(account: String, password: String): AuthSession
    suspend fun currentUser(): UserProfile
    suspend fun logout()
}

data class AuthSession(
    val accessToken: String,
    val user: UserProfile
)

