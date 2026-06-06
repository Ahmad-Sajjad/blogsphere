package com.example.blogsphere.ui.stories

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.blogsphere.data.ContentRepository
import com.example.blogsphere.databinding.ActivityStoryComposerBinding
import com.example.blogsphere.util.toast

class StoryComposerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoryComposerBinding
    private var selectedColor = "#6200EE"
    private var visibility = "all"

    private val colors = listOf("#6200EE", "#FF6B35", "#E53935", "#43A047", "#1E88E5", "#000000")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoryComposerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }

        binding.btnPost.setOnClickListener {
            val text = binding.etStoryText.text.toString().trim()
            if (text.isEmpty()) { toast("Type something first"); return@setOnClickListener }
            ContentRepository.createStory(text, selectedColor, visibility)
            toast("Story posted!")
            finish()
        }

        binding.bottomBar.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Who can see this?")
                .setItems(arrayOf("Everyone", "Followers only")) { _, which ->
                    visibility = if (which == 0) "all" else "followers"
                    binding.tvVisibility.text = if (visibility == "all") "Everyone can see" else "Followers only"
                }
                .show()
        }

        setupSwatches()
    }

    private fun setupSwatches() {
        colors.forEach { colorStr ->
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(100, 100).apply { marginStart = 16; marginEnd = 16 }
                setBackgroundColor(Color.parseColor(colorStr))
                setOnClickListener {
                    selectedColor = colorStr
                    binding.rootView.setBackgroundColor(Color.parseColor(colorStr))
                }
            }
            binding.swatchContainer.addView(swatch)
        }
    }
}
