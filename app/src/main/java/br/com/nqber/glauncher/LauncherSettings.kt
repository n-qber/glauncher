package br.com.nqber.glauncher

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object LauncherSettings {

    private const val PREFS_NAME = "glauncher_settings"

    const val THEME_SYSTEM = "system"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"

    const val LAYOUT_CIRCULAR = "circular"
    const val LAYOUT_LINE = "line"

    private const val KEY_THEME = "theme_mode"
    private const val KEY_LETTER_LAYOUT = "letter_layout"
    private const val KEY_CIRCLE_SCALE = "circle_scale"
    private const val KEY_SPACING_HORIZONTAL = "spacing_horizontal"
    private const val KEY_SPACING_VERTICAL = "spacing_vertical"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getThemeMode(context: Context): String {
        return getPrefs(context).getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    fun setThemeMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_THEME, mode).apply()
        applyTheme(mode)
    }

    fun applyTheme(mode: String) {
        val nightMode = when (mode) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun getLetterLayout(context: Context): String {
        return getPrefs(context).getString(KEY_LETTER_LAYOUT, LAYOUT_CIRCULAR) ?: LAYOUT_CIRCULAR
    }

    fun setLetterLayout(context: Context, layout: String) {
        getPrefs(context).edit().putString(KEY_LETTER_LAYOUT, layout).apply()
    }

    fun getCircleScale(context: Context): Int {
        return getPrefs(context).getInt(KEY_CIRCLE_SCALE, 100)
    }

    fun setCircleScale(context: Context, scalePercent: Int) {
        getPrefs(context).edit().putInt(KEY_CIRCLE_SCALE, scalePercent).apply()
    }

    fun getSpacingHorizontal(context: Context): Int {
        return getPrefs(context).getInt(KEY_SPACING_HORIZONTAL, 12)
    }

    fun setSpacingHorizontal(context: Context, spacingDp: Int) {
        getPrefs(context).edit().putInt(KEY_SPACING_HORIZONTAL, spacingDp).apply()
    }

    fun getSpacingVertical(context: Context): Int {
        return getPrefs(context).getInt(KEY_SPACING_VERTICAL, 12)
    }

    fun setSpacingVertical(context: Context, spacingDp: Int) {
        getPrefs(context).edit().putInt(KEY_SPACING_VERTICAL, spacingDp).apply()
    }
}
