package com.hutong.calendar.data

import com.hutong.calendar.BuildConfig
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.DELETE
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody

data class LoginRequestDto(val account: String, val password: String)
data class RegisterRequestDto(val username: String, val email: String, val password: String, @SerializedName("display_name") val displayName: String)
data class UserDto(val id: Int, @SerializedName("account_id") val accountId: Int, val username: String, val email: String, @SerializedName("display_name") val displayName: String, val phone: String? = null, val hobbies: String? = null, val signature: String? = null)
data class ProfileUpdateRequestDto(@SerializedName("display_name") val displayName: String? = null, val phone: String? = null, val hobbies: String? = null, val signature: String? = null)
data class FriendUserDto(val id: Int, @SerializedName("account_id") val accountId: Int, val username: String, @SerializedName("display_name") val displayName: String)
data class FriendshipDto(val id: Int, @SerializedName("user_id") val userId: Int, @SerializedName("friend_id") val friendId: Int, val status: String, val friend: FriendUserDto)
data class FriendRequestDto(@SerializedName("friend_id") val friendId: Int)
data class FriendResponseDto(val status: String)
data class AvailabilityBlockDto(val date: String, @SerializedName("start_time") val startTime: String, @SerializedName("end_time") val endTime: String, val status: String)
data class AvailabilityUpdateDto(val blocks: List<AvailabilityBlockDto>)
data class AuthResponseDto(@SerializedName("access_token") val accessToken: String, @SerializedName("token_type") val tokenType: String, val user: UserDto)
data class SessionDto(@SerializedName("session_key") val sessionKey: String, @SerializedName("created_at") val createdAt: String, @SerializedName("expires_at") val expiresAt: String, @SerializedName("is_current") val isCurrent: Boolean)
data class InviteDto(val id: Int, @SerializedName("sender_id") val senderId: Int, @SerializedName("receiver_id") val receiverId: Int, val title: String, val description: String?, @SerializedName("start_at") val startAt: String, @SerializedName("end_at") val endAt: String, val status: String)
data class InviteCreateDto(@SerializedName("receiver_id") val receiverId: Int, val title: String, val description: String? = null, @SerializedName("start_at") val startAt: String, @SerializedName("end_at") val endAt: String)
data class MatchRequestDto(@SerializedName("receiver_id") val receiverId: Int, @SerializedName("duration_minutes") val durationMinutes: Int, @SerializedName("from_date") val fromDate: String, val days: Int = 7, @SerializedName("window_start_date") val windowStartDate: String? = null, @SerializedName("window_end_date") val windowEndDate: String? = null, @SerializedName("window_start_time") val windowStartTime: String? = null, @SerializedName("window_end_time") val windowEndTime: String? = null)
data class MatchOptionDto(@SerializedName("start_at") val startAt: String, @SerializedName("end_at") val endAt: String, @SerializedName("match_type") val matchType: String, val score: Int)
data class CalendarDraftDto(val title: String, val description: String?, val date: String?, @SerializedName("start_time") val startTime: String?, @SerializedName("end_time") val endTime: String?, val category: String, val status: String, @SerializedName("flexible_tail_minutes") val flexibleTailMinutes: Int, val confidence: Double, @SerializedName("missing_fields") val missingFields: List<String>)
data class CalendarDraftResponseDto(val draft: CalendarDraftDto, val transcript: String?, val provider: String)

interface TempoApi {
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequestDto): AuthResponseDto
    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequestDto): AuthResponseDto
    @POST("api/auth/logout") suspend fun logout()
    @GET("api/auth/me") suspend fun me(): UserDto
    @GET("api/auth/sessions") suspend fun sessions(): List<SessionDto>
    @DELETE("api/auth/sessions/{sessionKey}") suspend fun revokeSession(@retrofit2.http.Path("sessionKey") sessionKey: String)
    @PATCH("api/auth/me") suspend fun updateProfile(@Body body: ProfileUpdateRequestDto): UserDto
    @GET("api/friends/search") suspend fun searchFriends(@retrofit2.http.Query("q") query: String): List<FriendUserDto>
    @GET("api/friends") suspend fun listFriends(): List<FriendshipDto>
    @POST("api/friends/requests") suspend fun sendFriendRequest(@Body body: FriendRequestDto): FriendshipDto
    @PATCH("api/friends/{friendshipId}") suspend fun respondFriend(@retrofit2.http.Path("friendshipId") id: Int, @Body body: FriendResponseDto): FriendshipDto
    @DELETE("api/friends/{friendshipId}") suspend fun deleteFriend(@retrofit2.http.Path("friendshipId") id: Int)
    @GET("api/friends/{friendId}/availability") suspend fun friendAvailability(@retrofit2.http.Path("friendId") id: Int, @retrofit2.http.Query("date") date: String): List<AvailabilityBlockDto>
    @retrofit2.http.PUT("api/friends/availability") suspend fun updateAvailability(@Body body: AvailabilityUpdateDto)
    @GET("api/invites") suspend fun invites(): List<InviteDto>
    @POST("api/invites") suspend fun createInvite(@Body body: InviteCreateDto): InviteDto
    @PATCH("api/invites/{inviteId}") suspend fun respondInvite(@retrofit2.http.Path("inviteId") id: Int, @Body body: FriendResponseDto): InviteDto
    @POST("api/invites/match") suspend fun match(@Body body: MatchRequestDto): List<MatchOptionDto>
    @Multipart
    @POST("api/ai/calendar/parse-audio")
    suspend fun parseCalendarAudio(@Part file: MultipartBody.Part, @Part("timezone") timezone: RequestBody, @Part("today") today: RequestBody): CalendarDraftResponseDto
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
