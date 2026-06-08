package com.example.blogsphere.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.Blog
import com.example.blogsphere.data.ContentRepository
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.data.Group
import com.example.blogsphere.data.User
import com.example.blogsphere.ui.blog.BlogDetailActivity
import com.example.blogsphere.ui.blog.UserProfileActivity
import com.example.blogsphere.ui.groups.GroupDetailActivity
import com.example.blogsphere.util.Broadcasts
import com.example.blogsphere.util.toast

class AdminListFragment : Fragment() {

    companion object {
        const val TYPE_USER = "user"
        const val TYPE_BLOG = "blog"
        const val TYPE_GROUP = "group"
        private const val ARG_TYPE = "type"

        fun newInstance(type: String) = AdminListFragment().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, type) }
        }
    }

    private lateinit var type: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = requireArguments().getString(ARG_TYPE) ?: TYPE_USER
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rv)
        val empty = view.findViewById<TextView>(R.id.emptyState)

        val adapter = AdminAdapter(type)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        adapter.refresh()
        empty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    inner class AdminAdapter(private val t: String) :
        RecyclerView.Adapter<AdminAdapter.RowVH>() {

        private val items = mutableListOf<Any>()

        fun refresh() {
            items.clear()
            when (t) {
                TYPE_USER -> items.addAll(DataStore.users)
                TYPE_BLOG -> items.addAll(DataStore.blogs)
                TYPE_GROUP -> items.addAll(DataStore.groups)
            }
            notifyDataSetChanged()
            view?.findViewById<TextView>(R.id.emptyState)?.visibility =
                if (items.isEmpty()) View.VISIBLE else View.GONE
        }

        inner class RowVH(v: View) : RecyclerView.ViewHolder(v) {
            val root: View = v
            val primary: TextView = v.findViewById(R.id.rowPrimary)
            val secondary: TextView = v.findViewById(R.id.rowSecondary)
            val btnEdit: android.widget.ImageButton = v.findViewById(R.id.btnEdit)
            val btnDelete: android.widget.ImageButton = v.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_row, parent, false)
            return RowVH(v)
        }

        override fun onBindViewHolder(h: RowVH, position: Int) {
            val item = items[position]
            when (item) {
                is User -> {
                    h.primary.text = "${item.username}${if (item.isAdmin) "  👑" else ""}"
                    h.secondary.text = "${item.email}  •  ${DataStore.blogsByAuthor(item.id).size} blogs"
                    h.btnEdit.setOnClickListener { editUser(item, position) }
                    h.btnDelete.setOnClickListener { deleteUser(item, position) }
                    h.root.setOnClickListener {
                        startActivity(Intent(requireContext(), UserProfileActivity::class.java)
                            .putExtra(UserProfileActivity.EXTRA_USER_ID, item.id))
                    }
                }
                is Blog -> {
                    val author = DataStore.findUserById(item.authorId)?.username ?: "?"
                    h.primary.text = item.title
                    h.secondary.text = "by $author  •  ${item.likedByUserIds.size} likes"
                    h.btnEdit.setOnClickListener { editBlog(item, position) }
                    h.btnDelete.setOnClickListener { deleteBlog(item, position) }
                    h.root.setOnClickListener {
                        startActivity(Intent(requireContext(), BlogDetailActivity::class.java)
                            .putExtra(BlogDetailActivity.EXTRA_BLOG_ID, item.id))
                    }
                }
                is Group -> {
                    h.primary.text = "${item.name}  (${item.inviteCode})"
                    h.secondary.text = "${item.memberIds.size} members  •  ${item.description}"
                    h.btnEdit.setOnClickListener { editGroup(item, position) }
                    h.btnDelete.setOnClickListener { deleteGroup(item, position) }
                    h.root.setOnClickListener {
                        startActivity(Intent(requireContext(), GroupDetailActivity::class.java)
                            .putExtra(GroupDetailActivity.EXTRA_GROUP_ID, item.id))
                    }
                }
            }
        }

        override fun getItemCount(): Int = items.size

        private fun deleteUser(u: User, pos: Int) {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete user ${u.username}?")
                .setMessage("All their blogs will be removed too.")
                .setPositiveButton(R.string.delete) { _, _ ->
                    DataStore.deleteUser(u.id)
                    toast("User deleted")
                    refresh()
                    broadcast()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun editUser(u: User, pos: Int) {
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 20, 48, 8)
            }
            val etUsername = EditText(requireContext()).apply { setText(u.username); hint = "Username" }
            val etEmail = EditText(requireContext()).apply { setText(u.email); hint = "Email" }
            // Passwords are managed by Firebase Auth and cannot be edited here.
            container.addView(etUsername); container.addView(etEmail)

            AlertDialog.Builder(requireContext())
                .setTitle("Edit user")
                .setView(container)
                .setPositiveButton(R.string.save) { _, _ ->
                    u.username = etUsername.text.toString().trim().ifBlank { u.username }
                    u.email = etEmail.text.toString().trim().ifBlank { u.email }
                    ContentRepository.pushUser(u)   // persist the edit
                    refresh(); toast("User updated"); broadcast()
                }
                .setNeutralButton(if (u.isAdmin) "Remove admin" else "Make admin") { _, _ ->
                    u.isAdmin = !u.isAdmin
                    ContentRepository.pushUser(u)   // persist admin toggle
                    refresh(); toast(if (u.isAdmin) "Now admin" else "Admin removed")
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun editBlog(b: Blog, pos: Int) {
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 20, 48, 8)
            }
            val etTitle = EditText(requireContext()).apply { setText(b.title); hint = "Title" }
            val etContent = EditText(requireContext()).apply {
                setText(b.content); hint = "Content"
                isSingleLine = false
                maxLines = 6
            }
            container.addView(etTitle); container.addView(etContent)

            AlertDialog.Builder(requireContext())
                .setTitle("Edit blog")
                .setView(container)
                .setPositiveButton(R.string.save) { _, _ ->
                    val updated = b.copy(
                        title = etTitle.text.toString().trim().ifBlank { b.title },
                        content = etContent.text.toString().trim().ifBlank { b.content }
                    )
                    DataStore.updateBlog(updated)   // new instance so feeds refresh + persists
                    refresh(); toast("Blog updated"); broadcast()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun deleteBlog(b: Blog, pos: Int) {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete blog?")
                .setMessage(b.title)
                .setPositiveButton(R.string.delete) { _, _ ->
                    DataStore.deleteBlog(b.id)
                    toast("Blog deleted"); refresh(); broadcast()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun editGroup(g: Group, pos: Int) {
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 20, 48, 8)
            }
            val etName = EditText(requireContext()).apply { setText(g.name); hint = "Name" }
            val etDesc = EditText(requireContext()).apply { setText(g.description); hint = "Description" }
            container.addView(etName); container.addView(etDesc)

            AlertDialog.Builder(requireContext())
                .setTitle("Edit group")
                .setView(container)
                .setPositiveButton(R.string.save) { _, _ ->
                    g.name = etName.text.toString().trim().ifBlank { g.name }
                    g.description = etDesc.text.toString().trim().ifBlank { g.description }
                    ContentRepository.pushGroup(g)   // persist the edit
                    refresh(); toast("Group updated"); broadcast()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun deleteGroup(g: Group, pos: Int) {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete ${g.name}?")
                .setMessage("Blogs in this group will also be removed.")
                .setPositiveButton(R.string.delete) { _, _ ->
                    DataStore.deleteGroup(g.id)
                    toast("Group deleted"); refresh(); broadcast()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun broadcast() {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(Broadcasts.ACTION_BLOG_UPDATED))
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(Broadcasts.ACTION_GROUPS_CHANGED))
        }
    }
}
