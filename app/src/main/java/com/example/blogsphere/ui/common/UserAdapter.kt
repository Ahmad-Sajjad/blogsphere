package com.example.blogsphere.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.User
import com.example.blogsphere.util.bindAvatar

class UserAdapter(
    private val users: List<User>,
    private val onUserClick: (User) -> Unit,
    private val subtitleProvider: ((User) -> String?)? = null
) : RecyclerView.Adapter<UserAdapter.UserVH>() {

    class UserVH(v: View) : RecyclerView.ViewHolder(v) {
        val avatarBg: View = v.findViewById(R.id.userAvatarBg)
        val avatarInitial: TextView = v.findViewById(R.id.userAvatarInitial)
        val avatarImage: ImageView = v.findViewById(R.id.userAvatarImage)
        val name: TextView = v.findViewById(R.id.userName)
        val subtitle: TextView = v.findViewById(R.id.userSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_user_row, parent, false)
        return UserVH(v)
    }

    override fun onBindViewHolder(h: UserVH, pos: Int) {
        val user = users[pos]
        bindAvatar(user, h.avatarBg, h.avatarInitial, h.avatarImage)
        h.name.text = user.username
        
        val sub = subtitleProvider?.invoke(user)
        if (sub != null) {
            h.subtitle.text = sub
            h.subtitle.visibility = View.VISIBLE
        } else {
            h.subtitle.visibility = View.GONE
        }

        h.itemView.setOnClickListener { onUserClick(user) }
    }

    override fun getItemCount() = users.size
}
