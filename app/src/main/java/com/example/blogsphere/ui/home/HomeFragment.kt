package com.example.blogsphere.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.blogsphere.R
import com.example.blogsphere.data.Blog
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.ui.blog.BlogDetailActivity
import com.example.blogsphere.ui.blog.UserProfileActivity
import com.example.blogsphere.util.Broadcasts
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class HomeFragment : Fragment() {

    companion object {
        /** Special chip tag for the personalized "Following" feed. */
        private const val FOLLOWING = "__following__"
    }

    private lateinit var adapter: BlogAdapter
    private lateinit var storyAdapter: StoryTrayAdapter
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var rv: RecyclerView
    private lateinit var rvStories: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var emptyState: View
    private lateinit var chipGroup: ChipGroup

    /** null = "All", otherwise a Group id. */
    private var selectedGroupId: String? = null

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Chips only depend on group membership — rebuild them only when groups change,
            // otherwise a like/view/comment update would churn the chips and could reset the
            // selected "Following" filter. The feed itself always refreshes.
            if (intent?.action == Broadcasts.ACTION_GROUPS_CHANGED) rebuildChips()
            if (intent?.action == Broadcasts.ACTION_STORIES_CHANGED) refreshStories()
            applyFilters()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipe = view.findViewById(R.id.swipeRefresh)
        rv = view.findViewById(R.id.rvBlogs)
        rvStories = view.findViewById(R.id.rvStories)
        etSearch = view.findViewById(R.id.etSearch)
        emptyState = view.findViewById(R.id.emptyState)
        chipGroup = view.findViewById(R.id.groupChips)

        adapter = BlogAdapter(
            items = mutableListOf(),
            onBlogClick = { blog ->
                startActivity(Intent(requireContext(), BlogDetailActivity::class.java)
                    .putExtra(BlogDetailActivity.EXTRA_BLOG_ID, blog.id))
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            },
            onAuthorClick = { userId ->
                startActivity(Intent(requireContext(), UserProfileActivity::class.java)
                    .putExtra(UserProfileActivity.EXTRA_USER_ID, userId))
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            },
            onLikeToggled = {
                // No-op; the adapter mutates DataStore directly. Like count
                // already updates in-place inside the bound view.
            }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        swipe.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), R.color.purple_primary)
        )
        swipe.setOnRefreshListener {
            swipe.postDelayed({
                applyFilters()
                swipe.isRefreshing = false
            }, 600)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedId)
            selectedGroupId = chip?.tag as? String
            applyFilters()
        }

        rebuildChips()
        refreshStories()
        applyFilters()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(refreshReceiver, IntentFilter().apply {
                addAction(Broadcasts.ACTION_NEW_BLOG)
                addAction(Broadcasts.ACTION_BLOG_UPDATED)
                addAction(Broadcasts.ACTION_GROUPS_CHANGED)
                addAction(Broadcasts.ACTION_STORIES_CHANGED)
            })
        rebuildChips()
        refreshStories()
        applyFilters()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(refreshReceiver)
    }

    private fun rebuildChips() {
        val me = DataStore.currentUser ?: return
        val joined = DataStore.userGroups(me)

        // If the previously selected group was left/deleted, fall back to "All".
        if (selectedGroupId != null && selectedGroupId != FOLLOWING &&
            joined.none { it.id == selectedGroupId }) {
            selectedGroupId = null
        }

        chipGroup.removeAllViews()

        chipGroup.addView(makeChip("All", tag = null, checked = selectedGroupId == null))
        chipGroup.addView(makeChip("Following", tag = FOLLOWING, checked = selectedGroupId == FOLLOWING))
        joined.forEach { g ->
            chipGroup.addView(makeChip(g.name, tag = g.id, checked = selectedGroupId == g.id))
        }
    }

    private fun makeChip(label: String, tag: String?, checked: Boolean): Chip {
        val ctx = requireContext()
        val chip = Chip(ctx)
        chip.text = label
        chip.tag = tag
        chip.isCheckable = true
        chip.isChecked = checked
        chip.isClickable = true
        chip.isCheckedIconVisible = false
        chip.setEnsureMinTouchTargetSize(false)
        chip.chipStrokeWidth = 0f
        chip.textSize = 13f
        chip.typeface = android.graphics.Typeface.DEFAULT_BOLD
        chip.chipMinHeight = ctx.resources.displayMetrics.density * 34f
        chip.chipBackgroundColor = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(
                ContextCompat.getColor(ctx, R.color.purple_primary),
                ContextCompat.getColor(ctx, R.color.divider_light)
            )
        )
        chip.setTextColor(
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(
                    ContextCompat.getColor(ctx, R.color.white),
                    ContextCompat.getColor(ctx, R.color.on_surface_variant_light)
                )
            )
        )
        return chip
    }

    private fun applyFilters() {
        val me = DataStore.currentUser ?: return
        val q = etSearch.text?.toString().orEmpty().trim().lowercase()
        val sel = selectedGroupId

        val visible = DataStore.blogsVisibleTo(me)
        val scoped = when (sel) {
            null -> visible
            FOLLOWING -> visible.filter { me.following.contains(it.authorId) }
            else -> visible.filter { it.groupId == sel }
        }
        val filtered = scoped.filter {
            q.isEmpty() ||
                it.title.lowercase().contains(q) ||
                it.content.lowercase().contains(q) ||
                it.tags.any { tag -> tag.lowercase().contains(q) }
        }

        adapter.replace(filtered)
        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshStories() {
        val me = DataStore.currentUser ?: return
        val visibleStories = DataStore.storiesVisibleTo(me)
        val authorsWithStories = visibleStories.map { it.authorId }.distinct()
            .filter { it != me.id } // exclude me, handled separately by adapter
            .mapNotNull { DataStore.findUserById(it) }

        storyAdapter = StoryTrayAdapter(
            authors = authorsWithStories,
            me = me,
            onAuthorClick = { author ->
                startActivity(Intent(requireContext(), com.example.blogsphere.ui.stories.StoryViewerActivity::class.java)
                    .putExtra("authorId", author.id))
            },
            onAddClick = {
                startActivity(Intent(requireContext(), com.example.blogsphere.ui.stories.StoryComposerActivity::class.java))
            }
        )
        rvStories.adapter = storyAdapter
    }
}
