package com.client.bluehock.data

import android.content.Context
import android.content.SharedPreferences

internal object PlayerPreferences {

    private const val PREFS_NAME = "air_hockey_prefs"
    private const val KEY_PLAYER_NAME = "player_name"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPlayerName(context: Context): String? {
        val name = prefs(context).getString(KEY_PLAYER_NAME, null)
        return name?.trim()?.ifBlank { null }
    }

    fun savePlayerName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_PLAYER_NAME, name.trim()).apply()
    }
}
