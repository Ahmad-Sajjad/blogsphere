package com.example.blogsphere.ui.groups

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.data.Group
import com.example.blogsphere.util.avatarColor

class GroupsAdapter(
    private val items: MutableList<Group>,
    private val onClick: (Group) -> Unit
) : RecyclerView.Adapter<GroupsAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: View = v.findViewById(R.id.groupCard)
        val iconBg: View = v.findViewById(R.id.iconBg)
        val name: TextView = v.findViewById(R.id.groupName)
        val meta: TextView = v.findViewById(R.id.groupMeta)
        val desc: TextView = v.findViewById(R.id.groupDesc)
        val badge: TextView = v.findViewById(R.id.memberBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val g = items[pos]
        h.iconBg.background.setTint(avatarColor(g.name))
        h.name.text = g.name
        h.meta.text = "${g.memberIds.size} member${if (g.memberIds.size == 1) "" else "s"}"
        h.desc.text = g.description
        val me = DataStore.currentUser?.id
        h.badge.visibility = if (me != null && g.memberIds.contains(me)) View.VISIBLE else View.GONE
        h.card.setOnClickListener { onClick(g) }
    }

    override fun getItemCount(): Int = items.size

    fun replace(newItems: List<Group>) {
        items.clear(); items.addAll(newItems); notifyDataSetChanged()
    }
}
