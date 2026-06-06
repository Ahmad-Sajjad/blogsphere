package com.example.blogsphere.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.blogsphere.R
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.databinding.ActivityUserListBinding
import com.example.blogsphere.ui.blog.UserProfileActivity
import com.example.blogsphere.ui.common.UserAdapter
import com.google.android.material.divider.MaterialDividerItemDecoration

class UserListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra("title") ?: "Users"
        val userIds = intent.getStringArrayListExtra("userIds") ?: arrayListOf()

        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Thin divider between rows, inset so it starts past the avatar.
        val divider = MaterialDividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        divider.dividerInsetStart = (76 * resources.displayMetrics.density).toInt()
        divider.dividerColor = ContextCompat.getColor(this, R.color.divider_light)
        divider.isLastItemDecorated = false
        binding.rvUsers.addItemDecoration(divider)

        val users = userIds.mapNotNull { DataStore.findUserById(it) }
        binding.empty.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
        binding.rvUsers.adapter = UserAdapter(
            users,
            onUserClick = { user ->
                // Use the correct extra key that UserProfileActivity reads.
                startActivity(
                    Intent(this, UserProfileActivity::class.java)
                        .putExtra(UserProfileActivity.EXTRA_USER_ID, user.id)
                )
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            },
            subtitleProvider = { it.bio.ifBlank { null } }
        )
    }
}
