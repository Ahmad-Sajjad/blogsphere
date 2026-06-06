package com.example.blogsphere.ui.create

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.blogsphere.R
import com.example.blogsphere.data.Blog
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.ui.home.HomeActivity
import com.example.blogsphere.util.Broadcasts
import com.example.blogsphere.util.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CreateFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_create, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tilTitle = view.findViewById<TextInputLayout>(R.id.tilTitle)
        val tilContent = view.findViewById<TextInputLayout>(R.id.tilContent)
        val etTitle = view.findViewById<TextInputEditText>(R.id.etTitle)
        val etContent = view.findViewById<TextInputEditText>(R.id.etContent)
        val etTags = view.findViewById<TextInputEditText>(R.id.etTags)
        val group = view.findViewById<RadioGroup>(R.id.visibilityGroup)
        val tilGroups = view.findViewById<TextInputLayout>(R.id.tilGroups)
        val spGroups = view.findViewById<MaterialAutoCompleteTextView>(R.id.spGroups)
        val btn = view.findViewById<MaterialButton>(R.id.btnPost)
        val progress = view.findViewById<ProgressBar>(R.id.publishProgress)

        val user = DataStore.currentUser ?: return
        val myGroups = DataStore.userGroups(user)

        var selectedGroupIndex = if (myGroups.isNotEmpty()) 0 else -1

        spGroups.setAdapter(
            ArrayAdapter(
                requireContext(),
                R.layout.item_dropdown_group,
                myGroups.map { it.name }
            )
        )
        if (selectedGroupIndex >= 0) spGroups.setText(myGroups[0].name, false)

        spGroups.setOnItemClickListener { _, _, position, _ ->
            selectedGroupIndex = position
        }

        group.setOnCheckedChangeListener { _, id ->
            tilGroups.visibility =
                if (id == R.id.rbGroup && myGroups.isNotEmpty()) View.VISIBLE else View.GONE
        }

        etTitle.setOnFocusChangeListener { _, f ->
            if (!f) tilTitle.error =
                if ((etTitle.text?.length ?: 0) < 3) "Title must be at least 3 characters" else null
        }
        etContent.setOnFocusChangeListener { _, f ->
            if (!f) tilContent.error =
                if ((etContent.text?.length ?: 0) < 10) "Content must be at least 10 characters" else null
        }

        btn.setOnClickListener {
            val title = etTitle.text?.toString()?.trim().orEmpty()
            val content = etContent.text?.toString()?.trim().orEmpty()
            if (title.length < 3) { tilTitle.error = "Title too short"; toast("Fix errors first"); return@setOnClickListener }
            if (content.length < 10) { tilContent.error = "Content too short"; toast("Fix errors first"); return@setOnClickListener }

            val gid: String? = if (group.checkedRadioButtonId == R.id.rbGroup && myGroups.isNotEmpty()) {
                val pos = selectedGroupIndex.coerceIn(0, myGroups.size - 1)
                myGroups[pos].id
            } else null

            btn.isEnabled = false
            progress.visibility = View.VISIBLE

            btn.postDelayed({
                // Parse comma-separated tags into a clean list.
                val tags = etTags.text?.toString()
                    ?.split(",")
                    ?.map { it.trim().removePrefix("#") }
                    ?.filter { it.isNotEmpty() }
                    ?.toMutableList() ?: mutableListOf()

                val blog = Blog(
                    id = DataStore.newId(),
                    authorId = user.id,
                    title = title,
                    content = content,
                    groupId = gid,
                    tags = tags
                )
                DataStore.addBlog(blog)
                LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(Intent(Broadcasts.ACTION_NEW_BLOG))
                toast("Blog posted!")

                etTitle.setText("")
                etContent.setText("")
                etTags.setText("")
                group.check(R.id.rbPublic)
                progress.visibility = View.GONE
                btn.isEnabled = true

                (activity as? HomeActivity)?.switchTab(R.id.nav_home)
            }, 700)
        }
    }
}
