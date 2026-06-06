package com.example.blogsphere.ui.saved

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.ui.blog.BlogDetailActivity
import com.example.blogsphere.ui.blog.UserProfileActivity
import com.example.blogsphere.ui.home.BlogAdapter
import com.example.blogsphere.util.installThemeToggle

class SavedBlogsActivity : AppCompatActivity() {

    private lateinit var adapter: BlogAdapter
    private lateinit var emptyState: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.installThemeToggle(this)

        emptyState = findViewById(R.id.emptyState)
        val rv = findViewById<RecyclerView>(R.id.rvSaved)
        adapter = BlogAdapter(
            items = mutableListOf(),
            onBlogClick = { blog ->
                startActivity(Intent(this, BlogDetailActivity::class.java)
                    .putExtra(BlogDetailActivity.EXTRA_BLOG_ID, blog.id))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            },
            onAuthorClick = { userId ->
                startActivity(Intent(this, UserProfileActivity::class.java)
                    .putExtra(UserProfileActivity.EXTRA_USER_ID, userId))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val me = DataStore.currentUser ?: run { finish(); return }
        val saved = DataStore.bookmarkedBlogs(me)
        adapter.replace(saved)
        emptyState.visibility = if (saved.isEmpty()) View.VISIBLE else View.GONE
    }
}
