package com.example.blogsphere.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.AuthRepository
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.ui.blog.BlogDetailActivity
import com.example.blogsphere.ui.blog.UserProfileActivity
import com.example.blogsphere.ui.groups.GroupChipAdapter
import com.example.blogsphere.ui.groups.GroupDetailActivity
import com.example.blogsphere.ui.home.BlogAdapter
import com.example.blogsphere.util.Broadcasts
import com.example.blogsphere.util.avatarColor
import com.example.blogsphere.util.initialOf
import com.example.blogsphere.util.toast
import com.google.android.material.button.MaterialButton

class ProfileFragment : Fragment() {

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                requireContext().contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                DataStore.currentUser?.avatarUri = it.toString()
                AuthRepository.saveCurrentUserAsync()   // persist to Firestore
                bindAvatar()
                LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(Intent(Broadcasts.ACTION_USER_PROFILE_CHANGED))
                toast("Avatar updated")
            } ?: run {
                // getContent doesn't need persistable perms; fall back silently
                // (handled above)
            }
        }

    private val pickImageSafe =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                DataStore.currentUser?.avatarUri = it.toString()
                AuthRepository.saveCurrentUserAsync()   // persist to Firestore
                bindAvatar()
                LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(Intent(Broadcasts.ACTION_USER_PROFILE_CHANGED))
                toast("Avatar updated")
            }
        }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pickImageSafe.launch("image/*")
            else toast("Permission needed to pick an image")
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindAll()
        view.findViewById<View>(R.id.avatarContainer).setOnClickListener { chooseAvatar() }
        view.findViewById<MaterialButton>(R.id.btnEditBio).setOnClickListener { editBioDialog() }
    }

    override fun onResume() {
        super.onResume()
        bindAll()
    }

    private fun bindAll() {
        val v = view ?: return
        val user = DataStore.currentUser ?: return
        v.findViewById<TextView>(R.id.username).text = user.username
        v.findViewById<TextView>(R.id.email).text = user.email
        v.findViewById<TextView>(R.id.bio).text =
            if (user.bio.isBlank()) "No bio yet." else user.bio
        bindAvatar()

        // Groups
        val myGroups = DataStore.userGroups(user)
        val rvGroups = v.findViewById<RecyclerView>(R.id.rvGroups)
        rvGroups.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvGroups.adapter = GroupChipAdapter(myGroups) { g ->
            startActivity(Intent(requireContext(), GroupDetailActivity::class.java)
                .putExtra(GroupDetailActivity.EXTRA_GROUP_ID, g.id))
        }
        v.findViewById<TextView>(R.id.emptyGroups).visibility =
            if (myGroups.isEmpty()) View.VISIBLE else View.GONE

        // Blogs
        val myBlogs = DataStore.blogsByAuthor(user.id)
        val rvBlogs = v.findViewById<RecyclerView>(R.id.rvBlogs)
        rvBlogs.layoutManager = LinearLayoutManager(requireContext())
        rvBlogs.adapter = BlogAdapter(
            items = myBlogs.toMutableList(),
            onBlogClick = { blog ->
                startActivity(Intent(requireContext(), BlogDetailActivity::class.java)
                    .putExtra(BlogDetailActivity.EXTRA_BLOG_ID, blog.id))
            },
            onAuthorClick = { /* own profile */ }
        )
        v.findViewById<TextView>(R.id.emptyBlogs).visibility =
            if (myBlogs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun bindAvatar() {
        val v = view ?: return
        val user = DataStore.currentUser ?: return
        val bg = v.findViewById<View>(R.id.avatarBg)
        val initial = v.findViewById<TextView>(R.id.avatarInitial)
        val image = v.findViewById<ImageView>(R.id.avatarImage)

        bg.background.setTint(avatarColor(user.username))
        initial.text = initialOf(user.username)

        if (!user.avatarUri.isNullOrEmpty()) {
            runCatching {
                image.setImageURI(Uri.parse(user.avatarUri))
                image.visibility = View.VISIBLE
                initial.visibility = View.GONE
            }
        } else {
            image.visibility = View.GONE
            initial.visibility = View.VISIBLE
        }
    }

    private fun chooseAvatar() {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
        val granted = ContextCompat.checkSelfPermission(requireContext(), permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) pickImageSafe.launch("image/*")
        else permLauncher.launch(permission)
    }

    private fun editBioDialog() {
        val user = DataStore.currentUser ?: return
        val edit = EditText(requireContext()).apply {
            setText(user.bio)
            setSingleLine(false)
            maxLines = 4
            setPadding(40, 20, 40, 20)
        }
        // Input AlertDialog
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Bio")
            .setView(edit)
            .setPositiveButton(R.string.save) { _, _ ->
                user.bio = edit.text.toString().trim()
                AuthRepository.saveCurrentUserAsync()   // persist to Firestore
                toast("Bio updated")
                bindAll()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
