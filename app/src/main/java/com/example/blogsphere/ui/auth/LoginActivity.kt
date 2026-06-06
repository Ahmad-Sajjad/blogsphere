package com.example.blogsphere.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.blogsphere.R
import com.example.blogsphere.data.AuthRepository
import com.example.blogsphere.data.ContentRepository
import com.example.blogsphere.ui.home.HomeActivity
import com.example.blogsphere.util.installThemeToggle
import com.example.blogsphere.util.toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PREFILL_EMAIL = "prefill_email"
    }

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnGoSignup: MaterialButton
    private lateinit var btnGoogle: MaterialButton
    private lateinit var btnForgot: TextView
    private lateinit var progress: ProgressBar

    /** Google Sign-In client, configured in [onCreate]. */
    private lateinit var googleClient: GoogleSignInClient

    // After SignUpActivity finishes, it returns the email so we can pre-fill it here.
    private val signupLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val email = result.data?.getStringExtra(EXTRA_PREFILL_EMAIL)
            if (!email.isNullOrEmpty()) {
                etEmail.setText(email)
                etPassword.requestFocus()
            }
        }

    // Launches Google's account picker and receives the chosen account.
    private val googleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken == null) {
                    showLoading(false)
                    toast("Google sign-in failed (no token)")
                } else {
                    completeGoogleSignIn(idToken)
                }
            } catch (e: ApiException) {
                showLoading(false)
                toast("Google sign-in cancelled")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnGoSignup = findViewById(R.id.btnGoSignup)
        btnGoogle = findViewById(R.id.btnGoogleSignIn)
        btnForgot = findViewById(R.id.btnForgotPassword)
        progress = findViewById(R.id.loginProgress)

        findViewById<ImageButton>(R.id.btnThemeToggle).installThemeToggle(this)

        // Configure Google Sign-In. `default_web_client_id` is generated automatically by the
        // google-services plugin from google-services.json (needs the OAuth client to exist).
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleClient = GoogleSignIn.getClient(this, gso)

        // Inline validation when leaving a field.
        etEmail.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) validateEmail() }
        etPassword.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) validatePassword() }

        btnLogin.setOnClickListener { attemptLogin() }
        btnForgot.setOnClickListener { sendPasswordReset() }
        btnGoogle.setOnClickListener {
            showLoading(true)
            googleLauncher.launch(googleClient.signInIntent)
        }
        btnGoSignup.setOnClickListener {
            signupLauncher.launch(Intent(this, SignUpActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------------------------

    private fun validateEmail(): Boolean {
        val e = etEmail.text?.toString()?.trim().orEmpty()
        return when {
            e.isEmpty() -> { tilEmail.error = "Email is required"; false }
            !Patterns.EMAIL_ADDRESS.matcher(e).matches() -> { tilEmail.error = "Enter a valid email"; false }
            else -> { tilEmail.error = null; true }
        }
    }

    private fun validatePassword(): Boolean {
        val p = etPassword.text?.toString().orEmpty()
        return when {
            p.isEmpty() -> { tilPassword.error = "Password is required"; false }
            p.length < 4 -> { tilPassword.error = "Too short"; false }
            else -> { tilPassword.error = null; true }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Sign-in flows
    // ---------------------------------------------------------------------------------------

    /** Email + password sign-in via Firebase Auth. */
    private fun attemptLogin() {
        if (!validateEmail() || !validatePassword()) {
            toast("Please fix errors first")
            return
        }
        val email = etEmail.text!!.toString().trim()
        val pass = etPassword.text!!.toString()

        showLoading(true)
        lifecycleScope.launch {
            try {
                val user = AuthRepository.signIn(email, pass)
                runCatching { ContentRepository.syncDown() }   // load content before Home
                goHome(user.username)
            } catch (e: Exception) {
                showLoading(false)
                tilPassword.error = "Invalid email or password"
                toast(e.localizedMessage ?: "Login failed")
            }
        }
    }

    /** Finish the Google flow by exchanging the Google token for a Firebase session. */
    private fun completeGoogleSignIn(idToken: String) {
        lifecycleScope.launch {
            try {
                val user = AuthRepository.signInWithGoogle(idToken)
                runCatching { ContentRepository.syncDown() }   // load content before Home
                goHome(user.username)
            } catch (e: Exception) {
                showLoading(false)
                toast(e.localizedMessage ?: "Google sign-in failed")
            }
        }
    }

    /** Send a Firebase password-reset email to the address in the email field. */
    private fun sendPasswordReset() {
        val email = etEmail.text?.toString()?.trim().orEmpty()
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Enter your email first"
            return
        }
        lifecycleScope.launch {
            try {
                AuthRepository.sendPasswordReset(email)
                toast("Password reset email sent to $email")
            } catch (e: Exception) {
                toast(e.localizedMessage ?: "Could not send reset email")
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private fun goHome(username: String) {
        toast("Welcome, $username!")
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    /** Show/hide the progress spinner and enable/disable the sign-in buttons together. */
    private fun showLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
        btnGoogle.isEnabled = !loading
    }
}
