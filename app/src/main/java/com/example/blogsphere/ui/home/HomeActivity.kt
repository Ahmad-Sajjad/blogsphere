package com.example.blogsphere.ui.home

import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.blogsphere.R
import com.example.blogsphere.data.AuthRepository
import com.example.blogsphere.data.ContentRepository
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.ui.about.AboutActivity
import com.example.blogsphere.ui.admin.AdminActivity
import com.example.blogsphere.ui.auth.LoginActivity
import com.example.blogsphere.ui.create.CreateFragment
import com.example.blogsphere.ui.groups.GroupsFragment
import com.example.blogsphere.ui.notifications.NotificationsActivity
import com.example.blogsphere.ui.profile.ProfileFragment
import com.example.blogsphere.ui.saved.SavedBlogsActivity
import com.example.blogsphere.ui.settings.SettingsActivity
import com.example.blogsphere.util.Broadcasts
import com.example.blogsphere.util.ConnectivityReceiver
import com.example.blogsphere.util.avatarColor
import com.example.blogsphere.util.bindAvatar
import com.example.blogsphere.util.initialOf
import com.example.blogsphere.util.installThemeToggle
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView

class HomeActivity : AppCompatActivity() {

    private lateinit var drawer: DrawerLayout
    private lateinit var toolbar: Toolbar
    private lateinit var navView: NavigationView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private val connectivityReceiver = ConnectivityReceiver()

    /** Unread-count badge on the toolbar notifications bell. */
    private var notifBadge: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DataStore.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_home)

        drawer = findViewById(R.id.drawerLayout)
        toolbar = findViewById(R.id.toolbar)
        navView = findViewById(R.id.navigationView)
        bottomNav = findViewById(R.id.bottomNav)

        drawerToggle = ActionBarDrawerToggle(
            this,
            drawer,
            toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawer.addDrawerListener(drawerToggle)
        drawer.setScrimColor(0x80000000.toInt())
        drawerToggle.isDrawerIndicatorEnabled = true
        drawerToggle.syncState()

        // Notifications bell in the toolbar (with an unread-count badge). Added before the
        // theme toggle so it sits to its left; the bell uses its own action-view click,
        // so it doesn't interfere with the theme-toggle menu listener.
        toolbar.inflateMenu(R.menu.home_toolbar)
        val bellView = toolbar.menu.findItem(R.id.action_notifications)?.actionView
        notifBadge = bellView?.findViewById(R.id.notifBadge)
        bellView?.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        toolbar.installThemeToggle(this)

        setupNavHeader()
        setupDrawerMenu()

        // Real-time: when Firestore data changes (from this or any device), the listener
        // updates the cache and we re-broadcast the existing refresh actions so the feed,
        // groups and comments screens update themselves live.
        ContentRepository.startRealtime { broadcastRefresh() }

        // Live unread-notification count → shown as a badge on the toolbar bell.
        DataStore.currentUser?.id?.let { uid ->
            ContentRepository.startNotificationsListener(uid) { unread -> updateNotifBadge(unread) }
        }

        bottomNav.setOnItemSelectedListener { item ->
            loadFragment(item.itemId)
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START)
                } else {
                    // Consent AlertDialog
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("Exit BlogSphere?")
                        .setMessage("Are you sure you want to leave?")
                        .setPositiveButton("Exit") { _, _ -> finish() }
                        .setNegativeButton("Stay", null)
                        .show()
                }
            }
        })
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (::drawerToggle.isInitialized) drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::drawerToggle.isInitialized) drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onResume() {
        super.onResume()
        // System broadcast receiver
        registerReceiver(connectivityReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    override fun onDestroy() {
        super.onDestroy()
        ContentRepository.stopRealtime()
    }

    /** Show/update the unread badge on the toolbar bell (hidden when zero). */
    private fun updateNotifBadge(unread: Int) {
        notifBadge?.let { badge ->
            if (unread > 0) {
                badge.text = if (unread > 9) "9+" else unread.toString()
                badge.visibility = android.view.View.VISIBLE
            } else {
                badge.visibility = android.view.View.GONE
            }
        }
    }

    /** Re-broadcast the in-app refresh actions so listening screens reload from the cache. */
    private fun broadcastRefresh() {
        val lbm = LocalBroadcastManager.getInstance(this)
        lbm.sendBroadcast(Intent(Broadcasts.ACTION_BLOG_UPDATED))
        lbm.sendBroadcast(Intent(Broadcasts.ACTION_GROUPS_CHANGED))
        lbm.sendBroadcast(Intent(Broadcasts.ACTION_COMMENTS_CHANGED))
        lbm.sendBroadcast(Intent(Broadcasts.ACTION_STORIES_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(connectivityReceiver) }
    }

    private fun setupNavHeader() {
        val header = navView.getHeaderView(0)
        val user = DataStore.currentUser ?: return
        val avatarBg = header.findViewById<View>(R.id.navAvatarBg)
        val avatarInitial = header.findViewById<TextView>(R.id.navAvatarInitial)
        val avatarImage = header.findViewById<ImageView>(R.id.navAvatarImage)
        val usernameTv = header.findViewById<TextView>(R.id.navUsername)
        val emailTv = header.findViewById<TextView>(R.id.navEmail)

        usernameTv.text = user.username
        emailTv.text = user.email
        bindAvatar(user, avatarBg, avatarInitial, avatarImage)
    }

    private fun setupDrawerMenu() {
        val menu = navView.menu
        menu.findItem(R.id.drawer_admin)?.isVisible =
            DataStore.currentUser?.isAdmin == true

        navView.setNavigationItemSelectedListener { item: MenuItem ->
            drawer.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.drawer_admin -> {
                    startActivity(Intent(this, AdminActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                R.id.drawer_saved -> {
                    startActivity(Intent(this, SavedBlogsActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                R.id.drawer_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                R.id.drawer_about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                R.id.drawer_logout -> confirmLogout()
            }
            true
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Log out?")
            .setMessage("You'll need to sign in again to view BlogSphere.")
            .setPositiveButton("Log out") { _, _ ->
                ContentRepository.stopRealtime()
                AuthRepository.signOut()   // ends the Firebase session + clears currentUser
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                })
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Public — lets fragments/activities programmatically switch tabs. */
    fun switchTab(id: Int) {
        if (bottomNav.selectedItemId == id) loadFragment(id)
        else bottomNav.selectedItemId = id   // triggers listener → loadFragment
    }

    private fun loadFragment(id: Int) {
        val frag = when (id) {
            R.id.nav_home -> HomeFragment()
            R.id.nav_groups -> GroupsFragment()
            R.id.nav_create -> CreateFragment()
            R.id.nav_profile -> ProfileFragment()
            else -> return
        }
        toolbar.title = when (id) {
            R.id.nav_home -> getString(R.string.app_name)
            R.id.nav_groups -> getString(R.string.nav_groups)
            R.id.nav_create -> getString(R.string.nav_create)
            R.id.nav_profile -> getString(R.string.nav_profile)
            else -> getString(R.string.app_name)
        }
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.fragmentContainer, frag)
            .commit()
    }
}
