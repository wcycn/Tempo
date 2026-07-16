package com.hutong.calendar.data

import com.hutong.calendar.BuildConfig
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH

data class LoginRequestDto(val account: String, val password: String)
data class RegisterRequestDto(val username: String, val email: String, val password: String, @SerializedName("display_name") val displayName: String)
data class UserDto(val id: Int, @SerializedName("account_id") val accountId: Int, val username: String, val email: String, @SerializedName("display_name") val displayName: String, val phone: String? = null, val hobbies: String? = null, val signature: String? = null)
data class ProfileUpdateRequestDto(@SerializedName("display_name") val displayName: String? = null, val phone: String? = null, val hobbies: String? = null, val signature: String? = null)
data class FriendUserDto(val id: Int, @SerializedName("account_id") val accountId: Int, val username: String, @SerializedName("display_name") val displayName: String)
data class FriendshipDto(val id: Int, @SerializedName("user_id") val userId: Int, @SerializedName("friend_id") val friendId: Int, val status: String, val friend: FriendUserDto)
data class FriendRequestDto(@SerializedName("friend_id") val friendId: Int)
data class AuthResponseDto(@SerializedName("access_token") val accessToken: String, @SerializedName("token_type") val tokenType: String, val user: UserDto)

interface TempoApi {
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequestDto): AuthResponseDto
    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequestDto): AuthResponseDto
    @GET("api/auth/me") suspend fun me(): UserDto
    @PATCH("api/auth/me") suspend fun updateProfile(@Body body: ProfileUpdateRequestDto): UserDto
    @GET("api/friends/search") suspend fun searchFriends(@retrofit2.http.Query("q") query: String): List<FriendUserDto>
    @GET("api/friends") suspend fun listFriends(): List<FriendshipDto>
    @POST("api/friends/requests") suspend fun sendFriendRequest(@Body body: FriendRequestDto): FriendshipDto
}

object TempoApiFactory {
    fun create(tokenProvider: () -> String?): TempoApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request().newBuilder()
            tokenProvider()?.let { request.header("Authorization", "Bearer $it") }
            chain.proceed(request.build())
        }.build())
        .build()
        .create(TempoApi::class.java)
}
