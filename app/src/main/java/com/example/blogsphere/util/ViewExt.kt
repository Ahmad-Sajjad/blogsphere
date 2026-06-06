package com.example.blogsphere.util

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.text.format.DateUtils
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment

fun Context.toast(msg: String, long: Boolean = false) {
    Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

fun Fragment.toast(msg: String, long: Boolean = false) {
    context?.toast(msg, long)
}

/** Heart pulse animation for the like button. */
fun View.animateHeart() {
    val scaleX = ObjectAnimator.ofFloat(this, "scaleX", 1f, 1.35f, 1f)
    val scaleY = ObjectAnimator.ofFloat(this, "scaleY", 1f, 1.35f, 1f)
    AnimatorSet().apply {
        playTogether(scaleX, scaleY)
        duration = 400
        interpolator = OvershootInterpolator(2f)
        start()
    }
}

fun ImageView.tintRes(colorInt: Int) {
    setColorFilter(colorInt)
}

/** "2h ago", "just now", etc. */
fun relativeTimeFrom(ts: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        ts,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
}

/** Builds a color for an avatar based on username hash. */
fun avatarColor(username: String): Int {
    val colors = intArrayOf(
        Color.parseColor("#6750A4"),
        Color.parseColor("#E91E63"),
        Color.parseColor("#3F51B5"),
        Color.parseColor("#009688"),
        Color.parseColor("#FF5722"),
        Color.parseColor("#795548"),
        Color.parseColor("#607D8B"),
        Color.parseColor("#F57C00")
    )
    val idx = (username.hashCode().let { if (it == Int.MIN_VALUE) 0 else it }).let { Math.abs(it) } % colors.size
    return colors[idx]
}

fun initialOf(username: String): String = username.trim().firstOrNull()?.uppercase() ?: "?"
