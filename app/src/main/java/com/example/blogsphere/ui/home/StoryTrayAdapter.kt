package com.example.blogsphere.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.data.User
import com.example.blogsphere.util.bindAvatar

class StoryTrayAdapter(
    private val authors: List<User>,
    private val me: User?,
    private val onAuthorClick: (User) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<StoryTrayAdapter.StoryVH>() {

    class StoryVH(v: View) : RecyclerView.ViewHolder(v) {
        val ring: View = v.findViewById(R.id.storyRing)
        val avatarBg: View = v.findViewById(R.id.avatarBg)
        val avatarInitial: TextView = v.findViewById(R.id.avatarInitial)
        val avatarImage: ImageView = v.findViewById(R.id.avatarImage)
        val name: TextView = v.findViewById(R.id.userName)
        val plus: ImageView = v.findViewById(R.id.plusIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_story_tray, parent, false)
        return StoryVH(v)
    }

    override fun onBindViewHolder(h: StoryVH, pos: Int) {
        if (pos == 0 && me != null) {
            bindMe(h)
        } else {
            val author = if (me != null) authors[pos - 1] else authors[pos]
            bindAuthor(h, author)
        }
    }

    private fun bindMe(h: StoryVH) {
        val user = me ?: return
        h.name.text = "Your story"
        bindAvatar(user, h.avatarBg, h.avatarInitial, h.avatarImage)

        val active = DataStore.activeStoriesByAuthor(user.id)
        if (active.isEmpty()) {
            h.ring.visibility = View.GONE
            h.plus.visibility = View.VISIBLE
            h.itemView.setOnClickListener { onAddClick() }
        } else {
            h.ring.visibility = View.VISIBLE
            h.ring.setBackgroundResource(R.drawable.bg_story_ring_seen)
            h.plus.visibility = View.GONE
            h.itemView.setOnClickListener { onAuthorClick(user) }
        }
    }

    private fun bindAuthor(h: StoryVH, author: User) {
        h.name.text = author.username
        bindAvatar(author, h.avatarBg, h.avatarInitial, h.avatarImage)
        h.plus.visibility = View.GONE
        
        val unseen = me?.let { DataStore.hasUnseenStory(author.id, it.id) } ?: true
        h.ring.visibility = View.VISIBLE
        h.ring.setBackgroundResource(if (unseen) R.drawable.bg_story_ring else R.drawable.bg_story_ring_seen)
        
        h.itemView.setOnClickListener { onAuthorClick(author) }
    }

    override fun getItemCount() = if (me != null) authors.size + 1 else authors.size
}
