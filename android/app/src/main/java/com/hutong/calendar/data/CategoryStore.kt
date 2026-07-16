package com.hutong.calendar.data

import android.content.Context

data class CategoryOption(val name: String, val colorHex: String)

object CategoryStore {
    private const val FILE = "tempo_preferences"
    private const val KEY = "categories"
    private const val ITEM_SEPARATOR = "||"
    private const val FIELD_SEPARATOR = "::"

    private val defaults = listOf(
        CategoryOption("工作", "#4A90E2"),
        CategoryOption("学习", "#57A773"),
        CategoryOption("游戏", "#9B78D1"),
        CategoryOption("聚会", "#E29A45"),
        CategoryOption("会议", "#D86A75")
    )

    fun load(context: Context): List<CategoryOption> {
        val saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return defaults
        val parsed = saved.split(ITEM_SEPARATOR).mapNotNull { item ->
            val fields = item.split(FIELD_SEPARATOR)
            if (fields.size == 2 && fields[0].isNotBlank()) CategoryOption(fields[0], fields[1]) else null
        }
        return if (parsed.isEmpty()) defaults else parsed
    }

    fun add(context: Context, option: CategoryOption): List<CategoryOption> {
        val result = (load(context).filterNot { it.name == option.name } + option)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY, result.joinToString(ITEM_SEPARATOR) { "${it.name}$FIELD_SEPARATOR${it.colorHex}" })
            .apply()
        return result
    }

    fun remove(context: Context, name: String): List<CategoryOption> {
        val result = load(context).filterNot { it.name == name }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY, result.joinToString(ITEM_SEPARATOR) { "${it.name}$FIELD_SEPARATOR${it.colorHex}" })
            .apply()
        return result
    }
}
