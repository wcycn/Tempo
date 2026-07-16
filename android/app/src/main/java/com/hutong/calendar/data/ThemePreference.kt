package com.hutong.calendar.data

import android.content.Context

enum class ThemeChoice { DARK, LIGHT, SYSTEM }

object ThemePreference {
    private const val FILE = "tempo_preferences"
    private const val KEY = "theme_choice"

    fun load(context: Context): ThemeChoice = runCatching {
        ThemeChoice.valueOf(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, ThemeChoice.SYSTEM.name)!!)
    }.getOrDefault(ThemeChoice.SYSTEM)

    fun save(context: Context, choice: ThemeChoice) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, choice.name).apply()
    }
}
