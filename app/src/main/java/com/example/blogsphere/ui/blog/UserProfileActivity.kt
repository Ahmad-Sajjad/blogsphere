package com.example.blogsphere.ui.blog

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.AuthRepository
import com.example.blogsphere.data.ContentRepository
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.ui.profile.PostTileAdapter
import com.example.blogsphere.ui.profile.UserListActivity
import com.example.blogsphere.ui.stories.StoryViewerActivity
import com.example.blogsphere.util.bindAvatar
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
        
        findViewById<TextView>(R.id.username).text = user.username
        findViewById<TextView>(R.id.bio).text = if (user.bio.isNotBlank()) user.bio else "No bio yet."
        
        bindAvatar(user)
        bindStats(user)

        // Follow / Unfollow
        val me = DataStore.currentUser
        val btnFollow = findViewById<MaterialButton>(R.id.btnFollow)
        if (me != null && me.id != user.id) {
            btnFollow.visibility = View.VISIBLE
            fun renderFollow() {
                btnFollow.text = if (me.following.contains(user.id)) "Following ✓" else "Follow"
                btnFollow.alpha = if (me.following.contains(user.id)) 0.7f else 1.0f
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
                AuthRepository.saveCurrentUserAsync()
                renderFollow()
                bindStats(user)
            }
        }

        // Blogs Grid
        val blogs = DataStore.blogsByAuthor(user.id).filter { it.groupId == null }
        val rv = findViewById<RecyclerView>(R.id.rvBlogs)
        val empty = findViewById<TextView>(R.id.emptyState)
        rv.layoutManager = GridLayoutManager(this, 3)
        rv.adapter = PostTileAdapter(blogs) { blog ->
            startActivity(Intent(this, BlogDetailActivity::class.java)
                .putExtra(BlogDetailActivity.EXTRA_BLOG_ID, blog.id))
        }
        if (blogs.isEmpty()) {
            empty.visibility = View.VISIBLE
            rv.visibility = View.GONE
        }
    }

    private fun bindAvatar(user: com.example.blogsphere.data.User) {
        val bg = findViewById<View>(R.id.avatarBg)
        val initial = findViewById<TextView>(R.id.avatarInitial)
        val image = findViewById<android.widget.ImageView>(R.id.avatarImage)
        val ring = findViewById<View>(R.id.storyRing)

        bindAvatar(user, bg, initial, image)

        val active = DataStore.activeStoriesByAuthor(user.id)
        if (active.isNotEmpty()) {
            ring.visibility = View.VISIBLE
            val me = DataStore.currentUser
            val unseen = me?.let { DataStore.hasUnseenStory(user.id, it.id) } ?: true
            ring.setBackgroundResource(if (unseen) R.drawable.bg_story_ring else R.drawable.bg_story_ring_seen)
            
            findViewById<View>(R.id.avatarContainer).setOnClickListener {
                startActivity(Intent(this, StoryViewerActivity::class.java)
                    .putExtra("authorId", user.id))
            }
        } else {
            ring.visibility = View.GONE
        }
    }

    private fun bindStats(user: com.example.blogsphere.data.User) {
        val posts = DataStore.blogsByAuthor(user.id).size
        val followers = DataStore.users.count { it.following.contains(user.id) }
        val following = user.following.size

        findViewById<TextView>(R.id.countPosts).text = posts.toString()
        findViewById<TextView>(R.id.countFollowers).text = followers.toString()
        findViewById<TextView>(R.id.countFollowing).text = following.toString()

        findViewById<View>(R.id.statFollowers).setOnClickListener {
            val fIds = DataStore.users.filter { it.following.contains(user.id) }.map { it.id }
            startActivity(Intent(this, UserListActivity::class.java)
                .putExtra("title", "Followers")
                .putStringArrayListExtra("userIds", ArrayList(fIds)))
        }
        findViewById<View>(R.id.statFollowing).setOnClickListener {
            startActivity(Intent(this, UserListActivity::class.java)
                .putExtra("title", "Following")
                .putStringArrayListExtra("userIds", ArrayList(user.following)))
        }
    }
}
