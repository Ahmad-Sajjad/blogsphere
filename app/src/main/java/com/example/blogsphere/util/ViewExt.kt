package com.example.blogsphere.util

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.text.format.DateUtils
import android.util.Base64
import android.util.LruCache
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import com.example.blogsphere.data.User
import java.io.ByteArrayOutputStream

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

// ---------------------------------------------------------------------------------------
// Avatar pictures (stored as a Base64 JPEG thumbnail on the user, so they show everywhere)
// ---------------------------------------------------------------------------------------

/** Small cache so we don't re-decode the same Base64 string while scrolling lists. */
private val avatarBitmapCache = LruCache<String, Bitmap>(60)

/** Decode a Base64 JPEG thumbnail into a Bitmap (cached). Null if absent/invalid. */
fun decodeAvatar(data: String?): Bitmap? {
    if (data.isNullOrEmpty()) return null
    avatarBitmapCache.get(data)?.let { return it }
    return try {
        val bytes = Base64.decode(data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { avatarBitmapCache.put(data, it) }
    } catch (e: Exception) {
        null
    }
}

/**
 * Render a user's avatar consistently across the whole app: shows the Base64 picture when the
 * user has one, otherwise a colored circle with their initial. [image] may be null for the few
 * spots that only have a colored circle.
 */
fun bindAvatar(user: User?, bg: View, initial: TextView, image: ImageView?) {
    val name = user?.username ?: "?"
    bg.background?.setTint(avatarColor(name))
    initial.text = initialOf(name)
    val bmp = decodeAvatar(user?.avatarData)
    if (image != null && bmp != null) {
        image.setImageBitmap(bmp)
        image.visibility = View.VISIBLE
        initial.visibility = View.GONE
    } else {
        image?.visibility = View.GONE
        initial.visibility = View.VISIBLE
    }
}

/**
 * Compress a picked image [uri] into a small Base64 JPEG thumbnail (max ~256px, quality 75)
 * suitable for storing in Firestore. Returns null on failure.
 */
fun encodeAvatar(context: Context, uri: Uri, maxPx: Int = 256): String? {
    return try {
        val original = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it)
        } ?: return null
        val longest = maxOf(original.width, original.height).coerceAtLeast(1)
        val scale = (maxPx.toFloat() / longest).coerceAtMost(1f)
        val w = (original.width * scale).toInt().coerceAtLeast(1)
        val h = (original.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(original, w, h, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}
