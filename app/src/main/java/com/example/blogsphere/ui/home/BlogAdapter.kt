package com.example.blogsphere.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.AuthRepository
import com.example.blogsphere.data.Blog
import com.example.blogsphere.data.Comment
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.util.Broadcasts
import com.example.blogsphere.util.animateHeart
import com.example.blogsphere.util.avatarColor
import com.example.blogsphere.util.initialOf
import com.example.blogsphere.util.relativeTimeFrom
import com.example.blogsphere.util.toast
import com.google.android.material.textfield.TextInputEditText

class BlogAdapter(
    private val items: MutableList<Blog>,
    private val onBlogClick: (Blog) -> Unit,
    private val onAuthorClick: (String) -> Unit,
    private val onLikeToggled: (Blog) -> Unit = {}
) : RecyclerView.Adapter<BlogAdapter.BlogVH>() {

    class BlogVH(v: View) : RecyclerView.ViewHolder(v) {
        val card: View = v.findViewById(R.id.blogCard)
        val authorRow: View = v.findViewById(R.id.authorRow)
        val avatarBg: View = v.findViewById(R.id.avatarBg)
        val avatarInitial: TextView = v.findViewById(R.id.avatarInitial)
        val authorName: TextView = v.findViewById(R.id.authorName)
        val timestamp: TextView = v.findViewById(R.id.timestamp)
        val groupChip: TextView = v.findViewById(R.id.groupChip)
        val title: TextView = v.findViewById(R.id.blogTitle)
        val preview: TextView = v.findViewById(R.id.blogPreview)
        val tags: TextView = v.findViewById(R.id.blogTags)
        val heart: ImageView = v.findViewById(R.id.heartIcon)
        val likeCount: TextView = v.findViewById(R.id.likeCount)
        val comment: ImageView = v.findViewById(R.id.commentIcon)
        val commentCount: TextView = v.findViewById(R.id.commentCount)
        val bookmark: ImageView = v.findViewById(R.id.bookmarkIcon)
        val heartBurst: ImageView = v.findViewById(R.id.heartBurst)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlogVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_blog, parent, false)
        return BlogVH(v)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(h: BlogVH, pos: Int) {
        val blog = items[pos]
        val author = DataStore.findUserById(blog.authorId)
        val ctx = h.itemView.context

        // Author
        h.avatarBg.background.setTint(avatarColor(author?.username ?: "?"))
        h.avatarInitial.text = initialOf(author?.username ?: "?")
        h.authorName.text = author?.username ?: "unknown"
        h.timestamp.text = relativeTimeFrom(blog.timestamp) +
            if (blog.viewedBy.isNotEmpty()) "  ·  ${blog.viewedBy.size} views" else ""

        // Tags (e.g. "#kotlin  #android")
        if (blog.tags.isNotEmpty()) {
            h.tags.text = blog.tags.joinToString("  ") { "#$it" }
            h.tags.visibility = View.VISIBLE
        } else {
            h.tags.visibility = View.GONE
        }

        // Group tag
        if (blog.groupId != null) {
            val g = DataStore.findGroupById(blog.groupId)
            h.groupChip.text = "in ${g?.name ?: "group"}"
            h.groupChip.visibility = View.VISIBLE
        } else {
            h.groupChip.visibility = View.GONE
        }

        h.title.text = blog.title
        h.preview.text = blog.content

        // Like state
        val meUser = DataStore.currentUser
        val me = meUser?.id
        renderLike(h, blog, me)
        renderBookmark(h, blog, meUser)

        // Author row → open profile (separate from card double-tap)
        h.authorRow.setOnClickListener {
            author?.let { onAuthorClick(it.id) }
        }

        // Single-tap on heart toggles like
        h.heart.setOnClickListener {
            if (me == null) return@setOnClickListener
            DataStore.toggleLike(blog, me)
            renderLike(h, blog, me)
            h.heart.animateHeart()
            onLikeToggled(blog)
        }

        // Bookmark toggle
        h.bookmark.setOnClickListener {
            if (meUser == null) return@setOnClickListener
            DataStore.toggleBookmark(blog, meUser)
            AuthRepository.saveCurrentUserAsync()   // persist bookmarkedBlogIds
            renderBookmark(h, blog, meUser)
            h.bookmark.animateHeart()
        }

        // Comment count + dialog
        h.commentCount.text = DataStore.commentsForBlog(blog.id).size.toString()
        h.comment.setOnClickListener {
            showCommentsDialog(it.context, blog) {
                h.commentCount.text = DataStore.commentsForBlog(blog.id).size.toString()
            }
        }

        // Card gestures: single-tap → open detail, double-tap → like + burst
        val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onBlogClick(blog)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (me != null && !blog.likedByUserIds.contains(me)) {
                    DataStore.toggleLike(blog, me)
                    renderLike(h, blog, me)
                    h.heart.animateHeart()
                    onLikeToggled(blog)
                }
                playHeartBurst(h.heartBurst)
                return true
            }
        })

        h.card.setOnTouchListener { _, e ->
            gd.onTouchEvent(e)
            false   // let ripple/foreground draw
        }
    }

    override fun getItemCount(): Int = items.size

    fun replace(newItems: List<Blog>) {
        val diff = DiffUtil.calculateDiff(BlogDiff(items.toList(), newItems))
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    private class BlogDiff(
        private val old: List<Blog>,
        private val new: List<Blog>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(o: Int, n: Int) = old[o].id == new[n].id
        override fun areContentsTheSame(o: Int, n: Int): Boolean {
            val a = old[o]; val b = new[n]
            return a.title == b.title &&
                a.content == b.content &&
                a.likedByUserIds.size == b.likedByUserIds.size &&
                a.groupId == b.groupId &&
                a.viewedBy.size == b.viewedBy.size &&
                a.tags == b.tags &&
                DataStore.commentsForBlog(a.id).size == DataStore.commentsForBlog(b.id).size
        }
    }

    // ---- inline comments dialog ---------------------------------------------

    private fun showCommentsDialog(ctx: Context, blog: Blog, onChanged: () -> Unit) {
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_comments, null)
        val container = view.findViewById<LinearLayout>(R.id.dlgCommentsContainer)
        val empty = view.findViewById<TextView>(R.id.dlgCommentsEmpty)
        val etComment = view.findViewById<TextInputEditText>(R.id.dlgEtComment)
        val btnPost = view.findViewById<ImageButton>(R.id.dlgBtnPostComment)
        val btnClose = view.findViewById<ImageButton>(R.id.dlgBtnClose)

        fun render() {
            val list = DataStore.commentsForBlog(blog.id)
            container.removeAllViews()
            empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            val me = DataStore.currentUser
            val isAdmin = me?.isAdmin == true
            list.forEach { c ->
                val row = LayoutInflater.from(ctx).inflate(R.layout.item_comment, container, false)
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
                        AlertDialog.Builder(ctx)
                            .setTitle("Delete comment?")
                            .setMessage(c.text)
                            .setPositiveButton("Delete") { _, _ ->
                                DataStore.deleteComment(c.id)
                                render()
                                onChanged()
                                LocalBroadcastManager.getInstance(ctx)
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

        val dialog = AlertDialog.Builder(ctx)
            .setView(view)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }

        btnPost.setOnClickListener {
            val me = DataStore.currentUser ?: return@setOnClickListener
            val text = etComment.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) { ctx.toast("Write something first"); return@setOnClickListener }
            DataStore.addComment(
                Comment(
                    id = DataStore.newId(),
                    blogId = blog.id,
                    authorId = me.id,
                    text = text
                )
            )
            etComment.setText("")
            render()
            onChanged()
            LocalBroadcastManager.getInstance(ctx)
                .sendBroadcast(Intent(Broadcasts.ACTION_COMMENTS_CHANGED))
        }

        render()
        dialog.show()
    }

    private fun renderLike(h: BlogVH, blog: Blog, meId: String?) {
        val liked = meId != null && blog.likedByUserIds.contains(meId)
        val ctx = h.heart.context
        h.heart.setImageResource(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline)
        h.heart.setColorFilter(
            ContextCompat.getColor(ctx, if (liked) R.color.pink_accent else R.color.grey_heart)
        )
        h.likeCount.text = blog.likedByUserIds.size.toString()
    }

    private fun renderBookmark(h: BlogVH, blog: Blog, me: com.example.blogsphere.data.User?) {
        val saved = me != null && me.bookmarkedBlogIds.contains(blog.id)
        val ctx = h.bookmark.context
        h.bookmark.setImageResource(
            if (saved) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outline
        )
        h.bookmark.setColorFilter(
            ContextCompat.getColor(ctx, if (saved) R.color.purple_primary else R.color.grey_heart)
        )
    }

    private fun playHeartBurst(view: ImageView) {
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 0.4f
        view.scaleY = 0.4f
        view.visibility = View.VISIBLE
        view.animate()
            .scaleX(1.4f).scaleY(1.4f)
            .alpha(0f)
            .setDuration(600)
            .withEndAction {
                view.visibility = View.GONE
                view.scaleX = 1f
                view.scaleY = 1f
            }
            .start()
    }
}
