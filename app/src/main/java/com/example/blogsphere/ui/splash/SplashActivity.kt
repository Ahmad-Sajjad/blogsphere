package com.example.blogsphere.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.blogsphere.R
import com.example.blogsphere.data.AuthRepository
import com.example.blogsphere.data.ContentRepository
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.ui.auth.LoginActivity
import com.example.blogsphere.ui.home.HomeActivity
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val darkPref = getSharedPreferences("blogsphere_prefs", MODE_PRIVATE)
            .getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkPref) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Trigger DataStore init (seeder runs now if not already).
        DataStore.users.size

        val fade = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        findViewById<android.widget.TextView>(R.id.splashLogo).startAnimation(fade)
        findViewById<android.widget.TextView>(R.id.splashAppName).startAnimation(fade)
        findViewById<android.widget.TextView>(R.id.splashTagline).startAnimation(fade)

        Handler(Looper.getMainLooper()).postDelayed({
            routeToNextScreen()
        }, 1800)
    }

    /**
     * Decide where to go after the splash:
     *  - If a Firebase session exists, restore the user's profile from Firestore into memory
     *    (so a previously-logged-in user lands straight on Home, even after the app was killed).
     *  - Otherwise, show the Login screen.
     */
    private fun routeToNextScreen() {
        if (AuthRepository.isLoggedIn) {
            lifecycleScope.launch {
                // Pull the profile doc into DataStore.currentUser, then load all content
                // from Firestore, before opening Home.
                runCatching { AuthRepository.loadProfileIntoSession() }
                if (DataStore.currentUser != null) {
                    runCatching { ContentRepository.syncDown() }
                    goTo(HomeActivity::class.java)
                } else {
                    goTo(LoginActivity::class.java)
                }
            }
        } else {
            goTo(LoginActivity::class.java)
        }
    }

    private fun goTo(target: Class<*>) {
        startActivity(Intent(this, target))
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }
}
