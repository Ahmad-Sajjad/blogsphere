package com.example.blogsphere.ui.notifications

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.ContentRepository
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.data.Notification
import com.example.blogsphere.ui.blog.BlogDetailActivity
import com.example.blogsphere.util.installThemeToggle
import com.example.blogsphere.util.relativeTimeFrom
import kotlinx.coroutines.launch

/**
 * Shows the signed-in user's in-app notifications (likes, comments, follows).
 * Opens the related blog on tap and marks everything read when shown.
 */
class NotificationsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.installThemeToggle(this)

        val rv = findViewById<RecyclerView>(R.id.rvNotifications)
        val empty = findViewById<TextView>(R.id.emptyState)
        rv.layoutManager = LinearLayoutManager(this)

        val uid = DataStore.currentUser?.id ?: run { finish(); return }

        lifecycleScope.launch {
            val items = runCatching { ContentRepository.loadNotifications(uid) }.getOrDefault(emptyList())
            rv.adapter = NotificationAdapter(items)
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            // Mark all as read now that the user has seen them.
            ContentRepository.markNotificationsRead(uid)
        }
    }

    private inner class NotificationAdapter(private val items: List<Notification>) :
        RecyclerView.Adapter<NotificationAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.notifIcon)
            val text: TextView = v.findViewById(R.id.notifText)
            val time: TextView = v.findViewById(R.id.notifTime)
            val dot: View = v.findViewById(R.id.notifUnreadDot)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notification, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val n = items[position]
            h.text.text = n.text
            h.time.text = relativeTimeFrom(n.timestamp)
            h.icon.setImageResource(
                when (n.type) {
                    "like" -> R.drawable.ic_heart
                    "comment" -> R.drawable.ic_comment
                    else -> R.drawable.ic_bell
                }
            )
            h.dot.visibility = if (n.read) View.GONE else View.VISIBLE

            h.itemView.setOnClickListener {
                if (n.blogId != null) {
                    startActivity(
                        Intent(this@NotificationsActivity, BlogDetailActivity::class.java)
                            .putExtra(BlogDetailActivity.EXTRA_BLOG_ID, n.blogId)
                    )
                }
            }
        }
    }
}
