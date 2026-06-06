package com.example.blogsphere.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.Blog

class PostTileAdapter(
    private val blogs: List<Blog>,
    private val onPostClick: (Blog) -> Unit
) : RecyclerView.Adapter<PostTileAdapter.TileVH>() {

    class TileVH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tileTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_post_tile, parent, false)
        return TileVH(v)
    }

    override fun onBindViewHolder(h: TileVH, pos: Int) {
        val blog = blogs[pos]
        h.title.text = blog.title
        h.itemView.setOnClickListener { onPostClick(blog) }
    }

    override fun getItemCount() = blogs.size
}
