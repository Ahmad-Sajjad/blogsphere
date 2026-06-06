package com.example.blogsphere.ui.groups

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.Group

class GroupChipAdapter(
    private val items: List<Group>,
    private val onClick: (Group) -> Unit
) : RecyclerView.Adapter<GroupChipAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val root: View = v.findViewById(R.id.chipRoot)
        val name: TextView = v.findViewById(R.id.groupName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_group_chip, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val g = items[pos]
        h.name.text = g.name
        h.root.setOnClickListener { onClick(g) }
    }

    override fun getItemCount() = items.size
}
