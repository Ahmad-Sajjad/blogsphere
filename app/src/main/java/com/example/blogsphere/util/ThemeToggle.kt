package com.example.blogsphere.util

import android.app.Activity
import android.view.MenuItem
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar
import com.example.blogsphere.R

/**
 * Adds a sun/moon menu item to a Toolbar. Tap toggles the theme and
 * recreates the activity. Works whether or not the toolbar is set as
 * the support action bar.
 */
fun Toolbar.installThemeToggle(activity: Activity) {
    menu.findItem(R.id.action_toggle_theme)?.let { menu.removeItem(R.id.action_toggle_theme) }
    inflateMenu(R.menu.menu_theme_toggle)
    refreshThemeIcon(activity)
    setOnMenuItemClickListener { item ->
        if (item.itemId == R.id.action_toggle_theme) {
            ThemeManager.toggle(activity)
            activity.recreate()
            true
        } else false
    }
}

/** For screens with no toolbar (Login/Signup): wire a plain ImageButton. */
fun ImageButton.installThemeToggle(activity: Activity) {
    setImageResource(
        if (ThemeManager.isDark(activity)) R.drawable.ic_sun else R.drawable.ic_moon
    )
    setOnClickListener {
        ThemeManager.toggle(activity)
        activity.recreate()
    }
}

private fun Toolbar.refreshThemeIcon(activity: Activity) {
    val item: MenuItem? = menu.findItem(R.id.action_toggle_theme)
    item?.setIcon(
        if (ThemeManager.isDark(activity)) R.drawable.ic_sun else R.drawable.ic_moon
    )
}
