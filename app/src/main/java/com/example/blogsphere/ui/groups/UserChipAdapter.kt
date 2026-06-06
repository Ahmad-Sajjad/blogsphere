package com.example.blogsphere.ui.groups

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.User
import com.example.blogsphere.util.bindAvatar

class UserChipAdapter(
    private val users: List<User>,
    private val onUserClick: (User) -> Unit
) : RecyclerView.Adapter<UserChipAdapter.UserVH>() {

    class UserVH(v: View) : RecyclerView.ViewHolder(v) {
        val avatarBg: View = v.findViewById(R.id.avatarBg)
        val avatarInitial: TextView = v.findViewById(R.id.avatarInitial)
        val avatarImage: ImageView = v.findViewById(R.id.avatarImage)
        val name: TextView = v.findViewById(R.id.userName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_user_chip, parent, false)
        return UserVH(v)
    }

    override fun onBindViewHolder(h: UserVH, pos: Int) {
        val user = users[pos]
        bindAvatar(user, h.avatarBg, h.avatarInitial, h.avatarImage)
        h.name.text = user.username
        h.itemView.setOnClickListener { onUserClick(user) }
    }

    override fun getItemCount() = users.size
}
