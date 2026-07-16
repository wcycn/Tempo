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
    fun save(session: AuthSession) {
        prefs.edit()
            .putString(KEY_TOKEN, session.accessToken)
            .putString(KEY_USER_ID, session.user.id)
            .putString(KEY_USER_NAME, session.user.displayName)
            .putString(KEY_USER_EMAIL, session.user.email)
            .apply()
    }
    fun cachedUser(): UserProfile? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        return UserProfile(id, prefs.getString(KEY_USER_NAME, "") ?: "", email = prefs.getString(KEY_USER_EMAIL, null))
    }
    fun clear() { prefs.edit().remove(KEY_TOKEN).remove(KEY_USER_ID).remove(KEY_USER_NAME).remove(KEY_USER_EMAIL).apply() }

    private companion object {
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_EMAIL = "user_email"
    }
}
