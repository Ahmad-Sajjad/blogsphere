package com.example.blogsphere.ui.admin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.blogsphere.R
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.ui.auth.LoginActivity
import com.example.blogsphere.util.installThemeToggle
import com.example.blogsphere.util.toast
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class AdminActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DataStore.currentUser?.isAdmin != true) {
            toast("Admins only")
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_admin)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.installThemeToggle(this)

        val pager = findViewById<ViewPager2>(R.id.pager)
        val tabs = findViewById<TabLayout>(R.id.tabs)

        pager.adapter = PagerAdapter(this)
        TabLayoutMediator(tabs, pager) { tab, pos ->
            tab.text = when (pos) {
                0 -> "Users"
                1 -> "Blogs"
                2 -> "Groups"
                else -> ""
            }
        }.attach()
    }

    private class PagerAdapter(a: FragmentActivity) : FragmentStateAdapter(a) {
        override fun getItemCount() = 3
        override fun createFragment(position: Int): Fragment =
            AdminListFragment.newInstance(
                when (position) {
                    0 -> AdminListFragment.TYPE_USER
                    1 -> AdminListFragment.TYPE_BLOG
                    else -> AdminListFragment.TYPE_GROUP
                }
            )
    }
}
