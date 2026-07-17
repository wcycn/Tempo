package com.hutong.calendar.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "tempo_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun get(): String? = prefs.getString(KEY_TOKEN, null)
    fun getAiAccessToken(): String? = prefs.getString(KEY_AI_ACCESS_TOKEN, null)
    fun saveAiAccessToken(token: String) { prefs.edit().putString(KEY_AI_ACCESS_TOKEN, token).apply() }
    fun clearAiAccessToken() { prefs.edit().remove(KEY_AI_ACCESS_TOKEN).apply() }
    fun save(session: AuthSession) {
        prefs.edit()
            .putString(KEY_TOKEN, session.accessToken)
            .putString(KEY_USER_ID, session.user.id)
            .putString(KEY_USERNAME, session.user.username)
            .putString(KEY_USER_NAME, session.user.displayName)
            .putString(KEY_USER_EMAIL, session.user.email)
            .putString(KEY_ACCOUNT_ID, session.user.accountId)
            .putString(KEY_PHONE, session.user.phone)
            .putString(KEY_HOBBIES, session.user.hobbies)
            .putString(KEY_SIGNATURE, session.user.signature)
            .apply()
    }
    fun cachedUser(): UserProfile? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        return UserProfile(id, prefs.getString(KEY_USER_NAME, "") ?: "", email = prefs.getString(KEY_USER_EMAIL, null), accountId = prefs.getString(KEY_ACCOUNT_ID, id) ?: id, phone = prefs.getString(KEY_PHONE, null), hobbies = prefs.getString(KEY_HOBBIES, null), signature = prefs.getString(KEY_SIGNATURE, null), username = prefs.getString(KEY_USERNAME, null))
    }
    fun clear() { prefs.edit().remove(KEY_TOKEN).remove(KEY_USER_ID).remove(KEY_USERNAME).remove(KEY_USER_NAME).remove(KEY_USER_EMAIL).remove(KEY_ACCOUNT_ID).remove(KEY_PHONE).remove(KEY_HOBBIES).remove(KEY_SIGNATURE).remove(KEY_AI_ACCESS_TOKEN).apply() }

    private companion object {
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_PHONE = "phone"
        const val KEY_HOBBIES = "hobbies"
        const val KEY_SIGNATURE = "signature"
        const val KEY_AI_ACCESS_TOKEN = "ai_access_token"
    }
}
