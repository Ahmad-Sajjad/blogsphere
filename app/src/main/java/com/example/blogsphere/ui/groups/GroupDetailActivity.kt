package com.example.blogsphere.ui.groups

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.AuthRepository
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.ui.blog.BlogDetailActivity
import com.example.blogsphere.ui.blog.UserProfileActivity
import com.example.blogsphere.ui.home.BlogAdapter
import com.example.blogsphere.util.Broadcasts
import com.example.blogsphere.util.installThemeToggle
import com.example.blogsphere.util.toast
import com.google.android.material.button.MaterialButton

class GroupDetailActivity : AppCompatActivity() {

    companion object { const val EXTRA_GROUP_ID = "group_id" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_detail)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.installThemeToggle(this)

        val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: run { finish(); return }
        val group = DataStore.findGroupById(groupId) ?: run { toast("Group not found"); finish(); return }

        toolbar.title = group.name
        findViewById<TextView>(R.id.groupName).text = group.name
        findViewById<TextView>(R.id.groupDesc).text = group.description
        findViewById<TextView>(R.id.groupMembers).text =
            "${group.memberIds.size} member${if (group.memberIds.size == 1) "" else "s"}"
        findViewById<TextView>(R.id.inviteCode).text = group.inviteCode

        val me = DataStore.currentUser
        val leave = findViewById<MaterialButton>(R.id.btnLeave)
        leave.visibility = if (me != null && group.memberIds.contains(me.id) && me.id != group.creatorId) View.VISIBLE else View.GONE

        leave.setOnClickListener {
            // Consent AlertDialog
            AlertDialog.Builder(this)
                .setTitle("Leave ${group.name}?")
                .setMessage("You'll no longer see posts in this group.")
                .setPositiveButton("Leave") { _, _ ->
                    DataStore.leaveGroup(me!!, group)
                    AuthRepository.saveCurrentUserAsync()   // persist joinedGroupIds
                    toast("Left group")
                    LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(Intent(Broadcasts.ACTION_GROUPS_CHANGED))
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<ImageButton>(R.id.btnCopy).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Invite code", group.inviteCode))
            toast("Code copied")
        }

        findViewById<ImageButton>(R.id.btnShare).setOnClickListener {
            val shareText = "Join my group \"${group.name}\" on BlogSphere!\nInvite code: ${group.inviteCode}"
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Join ${group.name} on BlogSphere")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(share, "Share invite"))
        }

        val rv = findViewById<RecyclerView>(R.id.rvBlogs)
        val empty = findViewById<TextView>(R.id.emptyState)
        val blogs = DataStore.blogsInGroup(group.id)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = BlogAdapter(
            items = blogs.toMutableList(),
            onBlogClick = { blog ->
                startActivity(Intent(this, BlogDetailActivity::class.java)
                    .putExtra(BlogDetailActivity.EXTRA_BLOG_ID, blog.id))
            },
            onAuthorClick = { uid ->
                startActivity(Intent(this, UserProfileActivity::class.java)
                    .putExtra(UserProfileActivity.EXTRA_USER_ID, uid))
            }
        )
        if (blogs.isEmpty()) { empty.visibility = View.VISIBLE; rv.visibility = View.GONE }
    }
}
