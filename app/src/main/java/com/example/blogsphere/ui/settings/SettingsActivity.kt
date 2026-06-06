package com.example.blogsphere.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.blogsphere.R
import com.example.blogsphere.data.AuthRepository
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.ui.splash.SplashActivity
import com.example.blogsphere.util.installThemeToggle
import com.example.blogsphere.util.toast
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("blogsphere_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.installThemeToggle(this)

        findViewById<View>(R.id.rowChangePassword).setOnClickListener { showChangePasswordDialog() }
        findViewById<View>(R.id.rowClearData).setOnClickListener { confirmClear() }

        val switchDark = findViewById<SwitchMaterial>(R.id.switchDark)
        switchDark.isChecked = prefs.getBoolean("dark_mode", false)
        switchDark.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("dark_mode", checked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            recreate()
        }
    }

    private fun showChangePasswordDialog() {
        // Must be signed in to change a password.
        if (DataStore.currentUser == null) return
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)
        val tilNew = view.findViewById<TextInputLayout>(R.id.tilNewPassword)
        val tilConfirm = view.findViewById<TextInputLayout>(R.id.tilConfirmPassword)
        val etNew = view.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirm = view.findViewById<TextInputEditText>(R.id.etConfirmPassword)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Change password")
            .setView(view)
            .setPositiveButton("Update", null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = etNew.text?.toString().orEmpty()
                val c = etConfirm.text?.toString().orEmpty()
                tilNew.error = null
                tilConfirm.error = null
                when {
                    n.length < 6 -> tilNew.error = "At least 6 characters"
                    n != c -> tilConfirm.error = "Passwords don't match"
                    else -> {
                        // Update the password in Firebase Auth (network call → coroutine).
                        lifecycleScope.launch {
                            try {
                                AuthRepository.changePassword(n)
                                toast("Password updated")
                                dialog.dismiss()
                            } catch (e: Exception) {
                                // Firebase requires a recent login to change the password.
                                tilNew.error = e.localizedMessage ?: "Could not update password"
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmClear() {
        // Consent AlertDialog
        AlertDialog.Builder(this)
            .setTitle("Reset demo data?")
            .setMessage("This wipes all in-memory data and re-seeds. You'll be logged out.")
            .setPositiveButton("Reset") { _, _ ->
                DataStore.resetAndReseed()
                AuthRepository.signOut()   // also end the Firebase session
                toast("Data reset")
                startActivity(Intent(this, SplashActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                })
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
