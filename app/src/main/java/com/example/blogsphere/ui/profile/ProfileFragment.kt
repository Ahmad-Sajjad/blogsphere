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
import androidx.recyclerview.widget.GridLayoutManager
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
import com.example.blogsphere.ui.stories.StoryComposerActivity
import com.example.blogsphere.ui.stories.StoryViewerActivity
import com.example.blogsphere.util.Broadcasts
import com.example.blogsphere.util.avatarColor
import com.example.blogsphere.util.decodeAvatar
import com.example.blogsphere.util.encodeAvatar
import com.example.blogsphere.util.initialOf
import com.example.blogsphere.util.toast
import com.google.android.material.button.MaterialButton

class ProfileFragment : Fragment() {

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { saveAvatar(it) }
        }

    private val pickImageSafe =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { saveAvatar(it) }
        }

    /** Compress the picked image to a Base64 thumbnail, persist it, and refresh the UI. */
    private fun saveAvatar(uri: Uri) {
        val data = encodeAvatar(requireContext(), uri)
        if (data == null) { toast("Couldn't read that image"); return }
        DataStore.currentUser?.avatarData = data
        AuthRepository.saveCurrentUserAsync()   // persist to Firestore (visible to everyone)
        bindAvatar()
        LocalBroadcastManager.getInstance(requireContext())
            .sendBroadcast(Intent(Broadcasts.ACTION_USER_PROFILE_CHANGED))
        toast("Avatar updated")
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
        view.findViewById<View>(R.id.avatarContainer).setOnClickListener {
            val user = DataStore.currentUser ?: return@setOnClickListener
            if (DataStore.activeStoriesByAuthor(user.id).isNotEmpty()) {
                startActivity(Intent(requireContext(), StoryViewerActivity::class.java)
                    .putExtra("authorId", user.id))
            } else {
                chooseAvatar()
            }
        }
        view.findViewById<View>(R.id.avatarContainer).setOnLongClickListener {
            chooseAvatar()
            true
        }
        view.findViewById<MaterialButton>(R.id.btnEditProfile).setOnClickListener { editBioDialog() }
        view.findViewById<MaterialButton>(R.id.btnAddStory).setOnClickListener {
            startActivity(Intent(requireContext(), StoryComposerActivity::class.java))
        }

        view.findViewById<View>(R.id.statFollowers).setOnClickListener {
            val user = DataStore.currentUser ?: return@setOnClickListener
            val followers = DataStore.users.filter { it.following.contains(user.id) }.map { it.id }
            startActivity(Intent(requireContext(), UserListActivity::class.java)
                .putExtra("title", "Followers")
                .putStringArrayListExtra("userIds", ArrayList(followers)))
        }

        view.findViewById<View>(R.id.statFollowing).setOnClickListener {
            val user = DataStore.currentUser ?: return@setOnClickListener
            startActivity(Intent(requireContext(), UserListActivity::class.java)
                .putExtra("title", "Following")
                .putStringArrayListExtra("userIds", ArrayList(user.following)))
        }
    }

    override fun onResume() {
        super.onResume()
        bindAll()
    }

    private fun bindAll() {
        val v = view ?: return
        val user = DataStore.currentUser ?: return
        v.findViewById<TextView>(R.id.username).text = user.username
        v.findViewById<TextView>(R.id.bio).text =
            if (user.bio.isBlank()) "No bio yet." else user.bio
        
        // Stats
        val posts = DataStore.blogsByAuthor(user.id).size
        val followers = DataStore.users.count { it.following.contains(user.id) }
        val following = user.following.size
        
        v.findViewById<TextView>(R.id.countPosts).text = posts.toString()
        v.findViewById<TextView>(R.id.countFollowers).text = followers.toString()
        v.findViewById<TextView>(R.id.countFollowing).text = following.toString()
        
        bindAvatar()

        // Groups
        val myGroups = DataStore.userGroups(user)
        val rvGroups = v.findViewById<RecyclerView>(R.id.rvGroups)
        rvGroups.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvGroups.adapter = GroupChipAdapter(myGroups) { g ->
            startActivity(Intent(requireContext(), GroupDetailActivity::class.java)
                .putExtra(GroupDetailActivity.EXTRA_GROUP_ID, g.id))
        }

        // Blogs Grid
        val myBlogs = DataStore.blogsByAuthor(user.id)
        val rvBlogs = v.findViewById<RecyclerView>(R.id.rvBlogs)
        rvBlogs.layoutManager = GridLayoutManager(requireContext(), 3)
        rvBlogs.adapter = PostTileAdapter(myBlogs) { blog ->
            startActivity(Intent(requireContext(), BlogDetailActivity::class.java)
                .putExtra(BlogDetailActivity.EXTRA_BLOG_ID, blog.id))
        }
    }

    private fun bindAvatar() {
        val v = view ?: return
        val user = DataStore.currentUser ?: return
        val bg = v.findViewById<View>(R.id.avatarBg)
        val initial = v.findViewById<TextView>(R.id.avatarInitial)
        val image = v.findViewById<ImageView>(R.id.avatarImage)
        val ring = v.findViewById<View>(R.id.storyRing)

        bg.background.setTint(avatarColor(user.username))
        initial.text = initialOf(user.username)

        // Story ring
        val active = DataStore.activeStoriesByAuthor(user.id)
        if (active.isNotEmpty()) {
            ring.visibility = View.VISIBLE
            val unseen = DataStore.hasUnseenStory(user.id, user.id) // though user probably saw their own
            ring.setBackgroundResource(if (unseen) R.drawable.bg_story_ring else R.drawable.bg_story_ring_seen)
        } else {
            ring.visibility = View.GONE
        }

        val bmp = decodeAvatar(user.avatarData)
        if (bmp != null) {
            image.setImageBitmap(bmp)
            image.visibility = View.VISIBLE
            initial.visibility = View.GONE
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
