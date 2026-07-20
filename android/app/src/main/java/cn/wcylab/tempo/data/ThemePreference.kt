package cn.wcylab.tempo.data

import android.content.Context

enum class ThemeChoice { DARK, LIGHT, SYSTEM }

object ThemePreference {
    private const val FILE = "tempo_preferences"
    private const val KEY = "theme_choice"

    fun load(context: Context, ownerId: String = localOwnerId(context)): ThemeChoice = runCatching {
        ThemeChoice.valueOf(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("$KEY-$ownerId", ThemeChoice.SYSTEM.name)!!)
    }.getOrDefault(ThemeChoice.SYSTEM)

    fun save(context: Context, choice: ThemeChoice, ownerId: String = localOwnerId(context)) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("$KEY-$ownerId", choice.name).apply()
    }

    private fun localOwnerId(context: Context): String = TokenStore(context).cachedUser()?.id ?: "guest"
}
