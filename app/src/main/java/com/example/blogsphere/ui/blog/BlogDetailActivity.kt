package com.example.blogsphere.ui.blog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.blogsphere.R
import com.example.blogsphere.data.Blog
import com.example.blogsphere.data.Comment
import com.example.blogsphere.data.ContentRepository
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.util.Broadcasts
import com.example.blogsphere.util.animateHeart
import com.example.blogsphere.util.avatarColor
import com.example.blogsphere.util.initialOf
import com.example.blogsphere.util.installThemeToggle
import com.example.blogsphere.util.relativeTimeFrom
import com.example.blogsphere.util.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/**
 * Demonstrates Activity Lifecycle — each callback logs under tag LIFECYCLE.
 * Also demonstrates ScrollView, AlertDialog (consent), ObjectAnimator, Intent.ACTION_SEND.
 */
class BlogDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LIFECYCLE"
        const val EXTRA_BLOG_ID = "blog_id"
    }

    private var blog: Blog? = null
    private var blogId: String? = null

    /**
     * Re-renders the screen when the real-time listener reports this blog or its comments
     * changed (likes/comments added from another device, etc.).
     */
    private val changeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = blogId ?: return
            val updated = DataStore.findBlogById(id)
            if (updated == null) {
                toast("This blog was removed")
                finish()
            } else {
                blog = updated
                bind()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate — BlogDetailActivity")
        setContentView(R.layout.activity_blog_detail)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.installThemeToggle(this)

        val id = intent.getStringExtra(EXTRA_BLOG_ID)
        blogId = id
        blog = id?.let { DataStore.findBlogById(it) }
        if (blog == null) { toast("Blog not found"); finish(); return }

        // Count this view once per person: add the current user to viewedBy and persist
        // only if they hadn't viewed it before (Set.add returns false if already present).
        DataStore.currentUser?.id?.let { uid ->
            blog?.let { b ->
                if (b.viewedBy.add(uid)) ContentRepository.pushBlog(b)
            }
        }

        bind()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart — BlogDetailActivity")
        LocalBroadcastManager.getInstance(this).registerReceiver(
            changeReceiver,
            IntentFilter().apply {
                addAction(Broadcasts.ACTION_BLOG_UPDATED)
                addAction(Broadcasts.ACTION_COMMENTS_CHANGED)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume — BlogDetailActivity")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause — BlogDetailActivity")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop — BlogDetailActivity")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(changeReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy — BlogDetailActivity")
    }

    override fun onRestart() {
        super.onRestart()
        Log.d(TAG, "onRestart — BlogDetailActivity")
    }

    private fun bind() {
        val b = blog ?: return
        val author = DataStore.findUserById(b.authorId)

        findViewById<View>(R.id.avatarBg).background.setTint(avatarColor(author?.username ?: "?"))
        findViewById<TextView>(R.id.avatarInitial).text = initialOf(author?.username ?: "?")
        val authorName = findViewById<TextView>(R.id.authorName)
        authorName.text = author?.username ?: "unknown"
        findViewById<TextView>(R.id.timestamp).text =
            relativeTimeFrom(b.timestamp) + "  ·  ${b.viewedBy.size} views"

        val chip = findViewById<TextView>(R.id.groupChip)
        if (b.groupId != null) {
            val g = DataStore.findGroupById(b.groupId)
            chip.text = "in ${g?.name ?: "group"}"
            chip.visibility = View.VISIBLE
        }

        findViewById<TextView>(R.id.blogTitle).text = b.title
        findViewById<TextView>(R.id.blogContent).text = b.content

        val me = DataStore.currentUser?.id
        val heart = findViewById<ImageView>(R.id.heartIcon)
        val likeText = findViewById<TextView>(R.id.likeCount)

        fun updateLikeUi() {
            val liked = me != null && b.likedByUserIds.contains(me)
            heart.setImageResource(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline)
            heart.setColorFilter(
                ContextCompat.getColor(this, if (liked) R.color.pink_accent else R.color.grey_heart)
            )
            likeText.text = "${b.likedByUserIds.size} likes"
        }
        updateLikeUi()

        heart.setOnClickListener {
            if (me == null) return@setOnClickListener
            DataStore.toggleLike(b, me)
            updateLikeUi()
            heart.animateHeart()
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(Intent(Broadcasts.ACTION_BLOG_UPDATED))
        }

        findViewById<View>(R.id.authorRow).setOnClickListener {
            author?.let {
                startActivity(Intent(this, UserProfileActivity::class.java)
                    .putExtra(UserProfileActivity.EXTRA_USER_ID, it.id))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        val controls = findViewById<View>(R.id.authorControls)
        val isAuthor = me == b.authorId
        val isAdmin = DataStore.currentUser?.isAdmin == true
        controls.visibility = if (isAuthor || isAdmin) View.VISIBLE else View.GONE

        findViewById<MaterialButton>(R.id.btnEdit).setOnClickListener {
            startActivity(Intent(this, EditBlogActivity::class.java)
                .putExtra(EditBlogActivity.EXTRA_BLOG_ID, b.id))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener {
            // Consent AlertDialog
            AlertDialog.Builder(this)
                .setTitle("Delete blog?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    DataStore.deleteBlog(b.id)
                    toast("Blog deleted")
                    LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(Intent(Broadcasts.ACTION_BLOG_UPDATED))
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<MaterialButton>(R.id.btnShare).setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, b.title)
                putExtra(Intent.EXTRA_TEXT, "${b.title}\n\n${b.content}\n\n— via BlogSphere")
            }
            startActivity(Intent.createChooser(share, "Share blog"))
        }

        bindCommentsSection(b)
    }

    private fun bindCommentsSection(b: Blog) {
        val container = findViewById<LinearLayout>(R.id.commentsContainer)
        val header = findViewById<TextView>(R.id.commentsHeader)
        val empty = findViewById<TextView>(R.id.commentsEmpty)
        val etComment = findViewById<TextInputEditText>(R.id.etComment)
        val btnPost = findViewById<MaterialButton>(R.id.btnPostComment)

        renderComments(container, header, empty, b)

        btnPost.setOnClickListener {
            val me = DataStore.currentUser ?: return@setOnClickListener
            val text = etComment.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) { toast("Write something first"); return@setOnClickListener }
            DataStore.addComment(
                Comment(
                    id = DataStore.newId(),
                    blogId = b.id,
                    authorId = me.id,
                    text = text
                )
            )
            etComment.setText("")
            renderComments(container, header, empty, b)
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(Intent(Broadcasts.ACTION_COMMENTS_CHANGED))
        }
    }

    private fun renderComments(
        container: LinearLayout,
        header: TextView,
        empty: TextView,
        b: Blog
    ) {
        val list = DataStore.commentsForBlog(b.id)
        header.text = "Comments (${list.size})"
        container.removeAllViews()
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

        val me = DataStore.currentUser
        val isAdmin = me?.isAdmin == true

        list.forEach { c ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_comment, container, false)
            val cAuthor = DataStore.findUserById(c.authorId)
            val name = cAuthor?.username ?: "unknown"
            row.findViewById<View>(R.id.cmtAvatarBg).background.setTint(avatarColor(name))
            row.findViewById<TextView>(R.id.cmtAvatarInitial).text = initialOf(name)
            row.findViewById<TextView>(R.id.cmtAuthor).text = name
            row.findViewById<TextView>(R.id.cmtTimestamp).text = relativeTimeFrom(c.timestamp)
            row.findViewById<TextView>(R.id.cmtText).text = c.text

            val canDelete = me != null && (c.authorId == me.id || isAdmin)
            if (canDelete) {
                row.setOnLongClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Delete comment?")
                        .setMessage(c.text)
                        .setPositiveButton("Delete") { _, _ ->
                            DataStore.deleteComment(c.id)
                            renderComments(container, header, empty, b)
                            LocalBroadcastManager.getInstance(this)
                                .sendBroadcast(Intent(Broadcasts.ACTION_COMMENTS_CHANGED))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            }
            container.addView(row)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Log.d(TAG, "onRestoreInstanceState — BlogDetailActivity")
    }
}
