package com.hutong.calendar.data

import com.hutong.calendar.BuildConfig
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class LoginRequestDto(val account: String, val password: String)
data class RegisterRequestDto(val username: String, val email: String, val password: String, @SerializedName("display_name") val displayName: String)
data class UserDto(val id: Int, val username: String, val email: String, @SerializedName("display_name") val displayName: String)
data class AuthResponseDto(@SerializedName("access_token") val accessToken: String, @SerializedName("token_type") val tokenType: String, val user: UserDto)

interface TempoApi {
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequestDto): AuthResponseDto
    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequestDto): AuthResponseDto
    @GET("api/auth/me") suspend fun me(): UserDto
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
