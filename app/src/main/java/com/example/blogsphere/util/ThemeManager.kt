package com.example.blogsphere.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    private const val PREFS = "blogsphere_prefs"
    private const val KEY_DARK = "dark_mode"

    fun isDark(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, false)

    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDark(context)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun toggle(context: Context) {
        val newValue = !isDark(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, newValue).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (newValue) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
