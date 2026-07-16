package com.hutong.calendar.data

import com.hutong.calendar.BuildConfig
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path

data class LoginRequestDto(val account: String, val password: String)
data class RegisterRequestDto(val username: String, val email: String, val password: String, @SerializedName("display_name") val displayName: String)
data class EventRequestDto(val title: String, val description: String? = null, @SerializedName("start_at") val startAt: String, @SerializedName("end_at") val endAt: String, val category: String, val status: String, @SerializedName("flexible_tail_minutes") val flexibleTailMinutes: Int)
data class UserDto(val id: Int, val username: String, val email: String, @SerializedName("display_name") val displayName: String)
data class AuthResponseDto(@SerializedName("access_token") val accessToken: String, @SerializedName("token_type") val tokenType: String, val user: UserDto)

data class SyncSnapshotDto(
    val events: List<EventDto>,
    @SerializedName("accepted_invites")
    val acceptedInvites: List<InviteDto>,
    @SerializedName("calendar_cache")
    val calendarCache: List<CalendarDayDto>,
    val serverTime: String
)

data class EventDto(val id: Int, @SerializedName("user_id") val userId: Int, val title: String, @SerializedName("start_at") val startAt: String, @SerializedName("end_at") val endAt: String, val category: String, val status: String, @SerializedName("flexible_tail_minutes") val flexibleTailMinutes: Int)
data class InviteDto(val id: Int, @SerializedName("sender_id") val senderId: Int, @SerializedName("receiver_id") val receiverId: Int, val title: String, @SerializedName("start_at") val startAt: String, @SerializedName("end_at") val endAt: String, val status: String)
data class CalendarDayDto(val date: String, val solarLabel: String, val lunarLabel: String, val festival: String?, val solarTerm: String?)

interface TempoApi {
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequestDto): AuthResponseDto
    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequestDto): AuthResponseDto
    @GET("api/auth/me") suspend fun me(): UserDto
    @GET("api/sync/snapshot") suspend fun snapshot(): SyncSnapshotDto
    @GET("api/events") suspend fun events(): List<EventDto>
    @POST("api/events") suspend fun createEvent(@Body body: EventRequestDto): EventDto
    @PUT("api/events/{id}") suspend fun updateEvent(@Path("id") id: String, @Body body: EventRequestDto): EventDto
    @DELETE("api/events/{id}") suspend fun deleteEvent(@Path("id") id: String)
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
