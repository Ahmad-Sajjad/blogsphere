package com.example.blogsphere.ui.blog

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.blogsphere.R
import com.example.blogsphere.data.ContentRepository
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.util.Broadcasts
import com.example.blogsphere.util.installThemeToggle
import com.example.blogsphere.util.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class EditBlogActivity : AppCompatActivity() {

    companion object { const val EXTRA_BLOG_ID = "blog_id" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_blog)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.installThemeToggle(this)

        val id = intent.getStringExtra(EXTRA_BLOG_ID) ?: run { finish(); return }
        val blog = DataStore.findBlogById(id) ?: run { toast("Blog not found"); finish(); return }

        val tilTitle = findViewById<TextInputLayout>(R.id.tilTitle)
        val tilContent = findViewById<TextInputLayout>(R.id.tilContent)
        val etTitle = findViewById<TextInputEditText>(R.id.etTitle)
        val etContent = findViewById<TextInputEditText>(R.id.etContent)

        etTitle.setText(blog.title)
        etContent.setText(blog.content)

        etTitle.setOnFocusChangeListener { _, f ->
            if (!f) tilTitle.error =
                if ((etTitle.text?.length ?: 0) < 3) "Title too short" else null
        }
        etContent.setOnFocusChangeListener { _, f ->
            if (!f) tilContent.error =
                if ((etContent.text?.length ?: 0) < 10) "Content too short" else null
        }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val newTitle = etTitle.text?.toString()?.trim().orEmpty()
            val newContent = etContent.text?.toString()?.trim().orEmpty()
            if (newTitle.length < 3) { tilTitle.error = "Title too short"; return@setOnClickListener }
            if (newContent.length < 10) { tilContent.error = "Content too short"; return@setOnClickListener }

            blog.title = newTitle
            blog.content = newContent
            blog.timestamp = System.currentTimeMillis()
            ContentRepository.pushBlog(blog)   // persist the edit
            toast("Blog updated")
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(Intent(Broadcasts.ACTION_BLOG_UPDATED))
            finish()
        }
    }
}
