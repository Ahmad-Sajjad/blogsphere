package com.example.blogsphere.ui.stories

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.blogsphere.data.ContentRepository
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.data.Story
import com.example.blogsphere.databinding.ActivityStoryViewerBinding
import com.example.blogsphere.ui.profile.UserListActivity
import com.example.blogsphere.util.bindAvatar
import com.example.blogsphere.util.relativeTimeFrom

class StoryViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoryViewerBinding
    private var stories = listOf<Story>()
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private var isPaused = false

    private val advanceRunnable = object : Runnable {
        override fun run() {
            if (!isPaused) {
                if (currentIndex < stories.size - 1) {
                    currentIndex++
                    showStory()
                } else {
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoryViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val authorId = intent.getStringExtra("authorId") ?: run { finish(); return }
        stories = DataStore.activeStoriesByAuthor(authorId)
        if (stories.isEmpty()) { finish(); return }

        binding.btnClose.setOnClickListener { finish() }

        binding.navLeft.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                showStory()
            }
        }
        binding.navRight.setOnClickListener {
            if (currentIndex < stories.size - 1) {
                currentIndex++
                showStory()
            } else {
                finish()
            }
        }

        binding.rootView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { isPaused = true; true }
                MotionEvent.ACTION_UP -> { isPaused = false; true }
                else -> false
            }
        }

        setupProgressBars()
        showStory()
    }

    private fun setupProgressBars() {
        binding.progressContainer.removeAllViews()
        for (i in stories.indices) {
            val v = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = 4; marginEnd = 4
                }
                setBackgroundColor(if (i < currentIndex) Color.WHITE else Color.parseColor("#44FFFFFF"))
            }
            binding.progressContainer.addView(v)
        }
    }

    private fun showStory() {
        val story = stories[currentIndex]
        val author = DataStore.findUserById(story.authorId)
        
        binding.rootView.setBackgroundColor(Color.parseColor(story.bgColor))
        binding.tvStoryText.text = story.text
        binding.tvAuthorName.text = author?.username ?: "Unknown"
        binding.tvTimestamp.text = relativeTimeFrom(story.createdAt)
        
        bindAvatar(author, binding.avatarBg, binding.avatarInitial, binding.avatarImage)

        val me = DataStore.currentUser
        if (me != null) {
            if (story.authorId == me.id) {
                // Own story: don't count yourself as a viewer; show who has seen it.
                binding.footerViewers.visibility = View.VISIBLE
                binding.tvViewerCount.text = "Seen by ${story.viewedBy.size}"
                binding.footerViewers.setOnClickListener {
                    startActivity(Intent(this, UserListActivity::class.java)
                        .putExtra("title", "Viewers")
                        .putStringArrayListExtra("userIds", ArrayList(story.viewedBy.toList())))
                }
            } else {
                // Someone else's story: record this account as a viewer (once).
                ContentRepository.markStoryViewed(story.id, me.id)
                binding.footerViewers.visibility = View.GONE
            }
        }

        updateProgressBars()
        handler.removeCallbacks(advanceRunnable)
        handler.postDelayed(advanceRunnable, 5000)
    }

    private fun updateProgressBars() {
        for (i in 0 until binding.progressContainer.childCount) {
            binding.progressContainer.getChildAt(i).setBackgroundColor(
                if (i <= currentIndex) Color.WHITE else Color.parseColor("#44FFFFFF")
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(advanceRunnable)
    }
}
