package cn.wcylab.tempo.data

import android.util.Log
import cn.wcylab.tempo.BuildConfig
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
import java.io.IOException
import java.util.concurrent.TimeUnit

data class LoginRequestDto(val account: String, val password: String)
data class AiAccessVerifyRequestDto(val code: String)
data class AiAccessVerifyResponseDto(val enabled: Boolean, @SerializedName("access_token") val accessToken: String, @SerializedName("expires_in") val expiresIn: Int)
data class RegisterRequestDto(val username: String, val email: String, val password: String, @SerializedName("display_name") val displayName: String)
data class UserDto(val id: Int, @SerializedName("account_id") val accountId: Int, val username: String, val email: String, @SerializedName("display_name") val displayName: String, val phone: String? = null, val hobbies: String? = null, val signature: String? = null)
data class ProfileUpdateRequestDto(@SerializedName("display_name") val displayName: String? = null, val phone: String? = null, val hobbies: String? = null, val signature: String? = null)
data class FriendUserDto(val id: Int, @SerializedName("account_id") val accountId: Int, val username: String, @SerializedName("display_name") val displayName: String, val phone: String? = null, val hobbies: String? = null, val signature: String? = null)
data class FriendshipDto(val id: Int, @SerializedName("user_id") val userId: Int, @SerializedName("friend_id") val friendId: Int, val status: String, val friend: FriendUserDto)
data class FriendRequestDto(@SerializedName("friend_id") val friendId: Int)
data class FriendResponseDto(val status: String)
data class GroupDto(val id: Int, @SerializedName("owner_id") val ownerId: Int, val name: String, val members: List<FriendUserDto> = emptyList())
data class GroupActivityDto(val id: Int, @SerializedName("activity_code") val activityCode: String = "", @SerializedName("group_id") val groupId: Int, @SerializedName("creator_id") val creatorId: Int, @SerializedName("creator_display_name") val creatorDisplayName: String? = null, val title: String, val description: String? = null, @SerializedName("duration_minutes") val durationMinutes: Int, @SerializedName("min_participants") val minParticipants: Int, @SerializedName("participant_mode") val participantMode: String = "MINIMUM", @SerializedName("deadline_at") val deadlineAt: String, @SerializedName("time_rule") val timeRule: String, @SerializedName("fixed_start_at") val fixedStartAt: String? = null, @SerializedName("fixed_end_at") val fixedEndAt: String? = null, val status: String, @SerializedName("proposed_start_at") val proposedStartAt: String? = null, @SerializedName("proposed_end_at") val proposedEndAt: String? = null, val round: Int, val participants: List<FriendUserDto> = emptyList(), @SerializedName("pending_confirmation_ids") val pendingConfirmationIds: List<Int> = emptyList(), @SerializedName("confirmed_count") val confirmedCount: Int = 0, @SerializedName("pending_count") val pendingCount: Int = 0, @SerializedName("declined_count") val declinedCount: Int = 0, @SerializedName("confirmed_participant_ids") val confirmedParticipantIds: List<Int> = emptyList())
data class GroupCreateDto(val name: String)
data class GroupMemberCreateDto(@SerializedName("user_id") val userId: Int)
data class GroupInvitationDto(val id: Int, @SerializedName("group_id") val groupId: Int, @SerializedName("group_name") val groupName: String?, @SerializedName("inviter_id") val inviterId: Int, @SerializedName("inviter_display_name") val inviterDisplayName: String?, @SerializedName("target_id") val targetId: Int, val status: String, @SerializedName("created_at") val createdAt: String)
data class GroupInvitationResponseDto(val status: String)
data class NotificationDto(val id: Int, val type: String, val title: String, val body: String, @SerializedName("reference_type") val referenceType: String? = null, @SerializedName("reference_id") val referenceId: String? = null, @SerializedName("created_at") val createdAt: String)
data class GroupActivityCreateDto(val title: String, val description: String? = null, @SerializedName("duration_minutes") val durationMinutes: Int = 60, @SerializedName("min_participants") val minParticipants: Int = 2, @SerializedName("participant_mode") val participantMode: String = "MINIMUM", @SerializedName("deadline_at") val deadlineAt: String, @SerializedName("time_rule") val timeRule: String, @SerializedName("fixed_start_at") val fixedStartAt: String? = null, @SerializedName("fixed_end_at") val fixedEndAt: String? = null, @SerializedName("window_start_at") val windowStartAt: String? = null, @SerializedName("window_end_at") val windowEndAt: String? = null)
data class AvailabilityBlockDto(val date: String, @SerializedName("start_time") val startTime: String, @SerializedName("end_time") val endTime: String, val status: String)
data class AvailabilityUpdateDto(
    val blocks: List<AvailabilityBlockDto>,
    val dates: List<String> = emptyList(),
    @SerializedName("replace_all") val replaceAll: Boolean = false
)
data class AuthResponseDto(@SerializedName("access_token") val accessToken: String, @SerializedName("token_type") val tokenType: String, val user: UserDto)
data class SessionDto(@SerializedName("session_key") val sessionKey: String, @SerializedName("created_at") val createdAt: String, @SerializedName("expires_at") val expiresAt: String, @SerializedName("is_current") val isCurrent: Boolean)
data class InviteDto(val id: Int, @SerializedName("sender_id") val senderId: Int, @SerializedName("receiver_id") val receiverId: Int, val title: String, val description: String?, @SerializedName("sender_display_name") val senderDisplayName: String? = null, @SerializedName("receiver_display_name") val receiverDisplayName: String? = null, @SerializedName("start_at") val startAt: String, @SerializedName("end_at") val endAt: String, val status: String)
data class InviteCreateDto(@SerializedName("receiver_id") val receiverId: Int, val title: String, val description: String? = null, @SerializedName("start_at") val startAt: String, @SerializedName("end_at") val endAt: String)
data class MatchRequestDto(@SerializedName("receiver_id") val receiverId: Int, @SerializedName("duration_minutes") val durationMinutes: Int, @SerializedName("from_date") val fromDate: String, val days: Int = 7, @SerializedName("window_start_date") val windowStartDate: String? = null, @SerializedName("window_end_date") val windowEndDate: String? = null, @SerializedName("window_start_time") val windowStartTime: String? = null, @SerializedName("window_end_time") val windowEndTime: String? = null)
data class MatchOptionDto(@SerializedName("start_at") val startAt: String, @SerializedName("end_at") val endAt: String, @SerializedName("match_type") val matchType: String, val score: Int)
data class CalendarDraftDto(val title: String, val description: String?, val date: String?, @SerializedName("start_time") val startTime: String?, @SerializedName("end_time") val endTime: String?, val category: String, val status: String, @SerializedName("flexible_tail_minutes") val flexibleTailMinutes: Int, val confidence: Double, @SerializedName("missing_fields") val missingFields: List<String>)
data class CalendarDraftResponseDto(val draft: CalendarDraftDto, val transcript: String?, val provider: String)
data class SyncSnapshotDto(val events: List<Any> = emptyList(), @SerializedName("accepted_invites") val acceptedInvites: List<Any> = emptyList(), @SerializedName("calendar_cache") val calendarCache: List<Any> = emptyList(), @SerializedName("server_time") val serverTime: String? = null, @SerializedName("server_version") val serverVersion: String? = null)

interface TempoApi {
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequestDto): AuthResponseDto
    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequestDto): AuthResponseDto
    @POST("api/auth/logout") suspend fun logout()
    @GET("api/auth/me") suspend fun me(): UserDto
    @GET("api/sync/snapshot") suspend fun syncSnapshot(): SyncSnapshotDto
    @GET("api/auth/sessions") suspend fun sessions(): List<SessionDto>
    @DELETE("api/auth/sessions/{sessionKey}") suspend fun revokeSession(@retrofit2.http.Path("sessionKey") sessionKey: String)
    @PATCH("api/auth/me") suspend fun updateProfile(@Body body: ProfileUpdateRequestDto): UserDto
    @POST("api/ai/access/verify") suspend fun verifyAiAccess(@Body body: AiAccessVerifyRequestDto): AiAccessVerifyResponseDto
    @GET("api/friends/find") suspend fun searchFriends(@retrofit2.http.Query("q") query: String): List<FriendUserDto>
    @GET("api/friends") suspend fun listFriends(): List<FriendshipDto>
    @POST("api/friends/requests") suspend fun sendFriendRequest(@Body body: FriendRequestDto): FriendshipDto
    @PATCH("api/friends/{friendshipId}") suspend fun respondFriend(@retrofit2.http.Path("friendshipId") id: Int, @Body body: FriendResponseDto): FriendshipDto
    @DELETE("api/friends/{friendshipId}")
    suspend fun deleteFriend(
        @retrofit2.http.Path("friendshipId") id: Int,
        @retrofit2.http.Query("confirm") confirm: Boolean = true
    )
    @GET("api/friends/{friendId}/availability") suspend fun friendAvailability(@retrofit2.http.Path("friendId") id: Int, @retrofit2.http.Query("date") date: String): List<AvailabilityBlockDto>
    @retrofit2.http.PUT("api/friends/availability") suspend fun updateAvailability(@Body body: AvailabilityUpdateDto)
    @GET("api/groups") suspend fun groups(): List<GroupDto>
    @POST("api/groups") suspend fun createGroup(@Body body: GroupCreateDto): GroupDto
    @PATCH("api/groups/{groupId}") suspend fun updateGroup(@retrofit2.http.Path("groupId") id: Int, @Body body: GroupCreateDto): GroupDto
    @POST("api/groups/{groupId}/members") suspend fun addGroupMember(@retrofit2.http.Path("groupId") id: Int, @Body body: GroupMemberCreateDto): GroupInvitationDto
    @GET("api/groups/invitations") suspend fun groupInvitations(): List<GroupInvitationDto>
    @PATCH("api/groups/invitations/{invitationId}") suspend fun respondGroupInvitation(@retrofit2.http.Path("invitationId") id: Int, @Body body: GroupInvitationResponseDto): GroupInvitationDto
    @GET("api/notifications") suspend fun notifications(): List<NotificationDto>
    @POST("api/notifications/{notificationId}/read") suspend fun markNotificationRead(@retrofit2.http.Path("notificationId") id: Int)
    @DELETE("api/groups/{groupId}/members/{memberId}") suspend fun removeGroupMember(@retrofit2.http.Path("groupId") groupId: Int, @retrofit2.http.Path("memberId") memberId: Int)
    @GET("api/groups/{groupId}/activities") suspend fun groupActivities(@retrofit2.http.Path("groupId") id: Int): List<GroupActivityDto>
    @POST("api/groups/{groupId}/activities") suspend fun createGroupActivity(@retrofit2.http.Path("groupId") id: Int, @Body body: GroupActivityCreateDto): GroupActivityDto
    @POST("api/groups/activities/{activityId}/join") suspend fun joinGroupActivity(@retrofit2.http.Path("activityId") id: Int): GroupActivityDto
    @DELETE("api/groups/activities/{activityId}/join") suspend fun leaveGroupActivity(@retrofit2.http.Path("activityId") id: Int): GroupActivityDto
    @PATCH("api/groups/activities/{activityId}/response") suspend fun respondGroupActivity(@retrofit2.http.Path("activityId") id: Int, @Body body: FriendResponseDto): GroupActivityDto
    @POST("api/groups/activities/{activityId}/recalculate") suspend fun recalculateGroupActivity(@retrofit2.http.Path("activityId") id: Int): GroupActivityDto
    @POST("api/groups/activities/{activityId}/cancel") suspend fun cancelGroupActivity(@retrofit2.http.Path("activityId") id: Int): GroupActivityDto
    @GET("api/invites") suspend fun invites(): List<InviteDto>
    @POST("api/invites") suspend fun createInvite(@Body body: InviteCreateDto): InviteDto
    @PATCH("api/invites/{inviteId}") suspend fun respondInvite(@retrofit2.http.Path("inviteId") id: Int, @Body body: FriendResponseDto): InviteDto
    @DELETE("api/invites/{inviteId}") suspend fun deleteInvite(@retrofit2.http.Path("inviteId") id: Int)
    @POST("api/invites/options") suspend fun match(@Body body: MatchRequestDto): List<MatchOptionDto>
    @GET("api/invites/options") suspend fun matchOptions(
        @retrofit2.http.Query("receiver_id") receiverId: Int,
        @retrofit2.http.Query("duration_minutes") durationMinutes: Int,
        @retrofit2.http.Query("from_date") fromDate: String,
        @retrofit2.http.Query("days") days: Int = 7,
        @retrofit2.http.Query("window_start_date") windowStartDate: String? = null,
        @retrofit2.http.Query("window_end_date") windowEndDate: String? = null,
        @retrofit2.http.Query("window_start_time") windowStartTime: String? = null,
        @retrofit2.http.Query("window_end_time") windowEndTime: String? = null
    ): List<MatchOptionDto>
    @Multipart
    @POST("api/ai/calendar/parse-audio")
    suspend fun parseCalendarAudio(@retrofit2.http.Header("X-Tempo-AI-Token") aiToken: String, @Part file: MultipartBody.Part, @Part("timezone") timezone: RequestBody, @Part("today") today: RequestBody): CalendarDraftResponseDto
}

object TempoApiFactory {
    fun create(tokenProvider: () -> String?): TempoApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(okhttp3.OkHttpClient.Builder()
            // 国内部分移动网络到 Cloudflare 的 HTTP/2 长连接会被中途重置。
            // 固定使用 HTTP/1.1；TLS 与证书验证仍完全由系统和 OkHttp 处理。
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                tokenProvider()?.let { request.header("Authorization", "Bearer $it") }
                val prepared = request.build()
                Log.d("TempoApi", "request ${prepared.method} ${prepared.url.encodedPath}")
                try {
                    val response = chain.proceed(prepared)
                    Log.d("TempoApi", "response ${response.code} ${prepared.url.encodedPath}")
                    response
                } catch (error: IOException) {
                    val detail = error.message?.takeIf { it.isNotBlank() } ?: "连接被重置"
                    Log.e(
                        "TempoApi",
                        "transport ${prepared.method} ${prepared.url.encodedPath}: " +
                            "${error.javaClass.simpleName}: $detail",
                        error
                    )
                    throw IOException(
                        "${prepared.method} ${prepared.url.encodedPath} 请求失败：$detail",
                        error
                    )
                }
            }.build())
        .build()
        .create(TempoApi::class.java)
}
