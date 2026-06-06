package com.example.blogsphere.ui.blog

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.AuthRepository
import com.example.blogsphere.data.ContentRepository
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.ui.home.BlogAdapter
import com.example.blogsphere.util.avatarColor
import com.example.blogsphere.util.initialOf
import com.example.blogsphere.util.installThemeToggle
import com.example.blogsphere.util.toast
import com.google.android.material.button.MaterialButton

class UserProfileActivity : AppCompatActivity() {

    companion object { const val EXTRA_USER_ID = "user_id" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.installThemeToggle(this)

        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: run { finish(); return }
        val user = DataStore.findUserById(userId) ?: run { toast("User not found"); finish(); return }

        toolbar.title = user.username

        findViewById<View>(R.id.avatarBg).background.setTint(avatarColor(user.username))
        findViewById<TextView>(R.id.avatarInitial).text = initialOf(user.username)
        findViewById<TextView>(R.id.username).text = user.username
        findViewById<TextView>(R.id.email).text = user.email
        val bioView = findViewById<TextView>(R.id.bio)
        bioView.text = if (user.bio.isNotBlank()) user.bio else "No bio yet."

        val blogs = DataStore.blogsByAuthor(user.id)
            .filter { it.groupId == null }  // public only on public profile
        val groupsCount = DataStore.userGroups(user).size
        val followerCount = DataStore.users.count { it.following.contains(user.id) }
        findViewById<TextView>(R.id.stats).text =
            "${blogs.size} blog${if (blogs.size == 1) "" else "s"} • " +
            "$groupsCount group${if (groupsCount == 1) "" else "s"} • " +
            "$followerCount follower${if (followerCount == 1) "" else "s"}"

        // Follow / Unfollow (hidden on your own profile)
        val me = DataStore.currentUser
        val btnFollow = findViewById<MaterialButton>(R.id.btnFollow)
        if (me != null && me.id != user.id) {
            btnFollow.visibility = View.VISIBLE
            fun renderFollow() {
                btnFollow.text = if (me.following.contains(user.id)) "Following ✓" else "+ Follow"
            }
            renderFollow()
            btnFollow.setOnClickListener {
                if (me.following.contains(user.id)) {
                    me.following.remove(user.id)
                } else {
                    me.following.add(user.id)
                    ContentRepository.createNotification(
                        recipientId = user.id,
                        actorId = me.id,
                        type = "follow",
                        actorName = me.username,
                        text = "${me.username} started following you"
                    )
                }
                AuthRepository.saveCurrentUserAsync()   // persist the follow list
                renderFollow()
            }
        }

        val rv = findViewById<RecyclerView>(R.id.rvBlogs)
        val empty = findViewById<TextView>(R.id.emptyState)
        val adapter = BlogAdapter(
            items = blogs.toMutableList(),
            onBlogClick = { blog ->
                startActivity(Intent(this, BlogDetailActivity::class.java)
                    .putExtra(BlogDetailActivity.EXTRA_BLOG_ID, blog.id))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            },
            onAuthorClick = { /* already here */ }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        if (blogs.isEmpty()) {
            empty.visibility = View.VISIBLE
            rv.visibility = View.GONE
        }
    }
}
