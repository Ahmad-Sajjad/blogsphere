package com.example.blogsphere.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.blogsphere.R
import com.example.blogsphere.data.AuthRepository
import com.example.blogsphere.util.PasswordStrength
import com.example.blogsphere.util.installThemeToggle
import com.example.blogsphere.util.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val tilUsername = findViewById<TextInputLayout>(R.id.tilUsername)
        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val tilConfirm = findViewById<TextInputLayout>(R.id.tilConfirm)
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirm = findViewById<TextInputEditText>(R.id.etConfirm)
        val etBio = findViewById<TextInputEditText>(R.id.etBio)
        val btnSignup = findViewById<MaterialButton>(R.id.btnSignup)
        val btnGoLogin = findViewById<MaterialButton>(R.id.btnGoLogin)
        val progress = findViewById<ProgressBar>(R.id.signupProgress)

        val pwBlock = findViewById<LinearLayout>(R.id.pwStrengthBlock)
        val pwBars = listOf<View>(
            findViewById(R.id.pwBar1),
            findViewById(R.id.pwBar2),
            findViewById(R.id.pwBar3),
            findViewById(R.id.pwBar4)
        )
        val pwRules = listOf<TextView>(
            findViewById(R.id.pwRule1),
            findViewById(R.id.pwRule2),
            findViewById(R.id.pwRule3),
            findViewById(R.id.pwRule4)
        )
        val pwLabel = findViewById<TextView>(R.id.pwLabel)

        findViewById<ImageButton>(R.id.btnThemeToggle).installThemeToggle(this)

        etUsername.setOnFocusChangeListener { _, f ->
            if (!f) tilUsername.error =
                if ((etUsername.text?.length ?: 0) < 3) "At least 3 characters" else null
        }
        etEmail.setOnFocusChangeListener { _, f ->
            if (!f) {
                val e = etEmail.text?.toString()?.trim().orEmpty()
                tilEmail.error = when {
                    e.isEmpty() -> "Email required"
                    !Patterns.EMAIL_ADDRESS.matcher(e).matches() -> "Enter a valid email"
                    else -> null
                    // Note: "already registered" is now reported by Firebase on submit.
                }
            }
        }

        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                val text = s?.toString().orEmpty()
                if (text.isEmpty()) {
                    pwBlock.visibility = View.GONE
                    tilPassword.error = null
                    return
                }
                pwBlock.visibility = View.VISIBLE
                renderStrength(PasswordStrength.evaluate(text), pwBars, pwRules, pwLabel)
                tilPassword.error = null
            }
        })

        etConfirm.setOnFocusChangeListener { _, f ->
            if (!f) tilConfirm.error =
                if (etConfirm.text?.toString() != etPassword.text?.toString())
                    "Passwords don't match" else null
        }

        btnGoLogin.setOnClickListener { finish() }

        btnSignup.setOnClickListener {
            val username = etUsername.text?.toString()?.trim().orEmpty()
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val pass = etPassword.text?.toString().orEmpty()
            val conf = etConfirm.text?.toString().orEmpty()
            val bio = etBio.text?.toString()?.trim().orEmpty()

            // Reset previous errors so we don't carry stale ones across submits
            tilUsername.error = null
            tilEmail.error = null
            tilPassword.error = null
            tilConfirm.error = null

            var firstBadField: View? = null
            var firstMessage: String? = null
            fun fail(field: View, til: TextInputLayout, fieldMsg: String, toastMsg: String) {
                til.error = fieldMsg
                if (firstBadField == null) {
                    firstBadField = field
                    firstMessage = toastMsg
                }
            }

            if (username.length < 3) {
                fail(etUsername, tilUsername,
                    "At least 3 characters",
                    "Username must be at least 3 characters")
            }

            when {
                email.isEmpty() ->
                    fail(etEmail, tilEmail, "Email required", "Email is required")
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                    fail(etEmail, tilEmail, "Enter a valid email", "Email looks invalid")
            }

            val strength = PasswordStrength.evaluate(pass)
            if (strength.score < 3) {
                pwBlock.visibility = View.VISIBLE
                renderStrength(strength, pwBars, pwRules, pwLabel)
                fail(etPassword, tilPassword,
                    "Choose a stronger password",
                    "Password is too weak — see the checklist below the field")
            }

            if (pass != conf) {
                fail(etConfirm, tilConfirm,
                    "Passwords don't match",
                    "Passwords don't match")
            }

            firstBadField?.let { field ->
                showError(firstMessage ?: "Please fix the highlighted field")
                field.requestFocus()
                field.post {
                    field.requestRectangleOnScreen(
                        android.graphics.Rect(0, 0, field.width, field.height),
                        false
                    )
                }
                return@setOnClickListener
            }

            // All client-side checks passed → create the account in Firebase.
            btnSignup.isEnabled = false
            progress.visibility = View.VISIBLE

            lifecycleScope.launch {
                try {
                    AuthRepository.signUp(username, email, pass, bio)
                    toast("Account created. Please sign in.")
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(LoginActivity.EXTRA_PREFILL_EMAIL, email)
                    )
                    finish()
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                } catch (e: Exception) {
                    // e.g. email already in use, weak password rejected by Firebase, no network
                    progress.visibility = View.GONE
                    btnSignup.isEnabled = true
                    tilEmail.error = e.localizedMessage ?: "Sign-up failed"
                    showError(e.localizedMessage ?: "Sign-up failed. Please try again.")
                }
            }
        }
    }

    private fun showError(msg: String) {
        val root = findViewById<View>(android.R.id.content)
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.strength_weak))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
    }

    private fun renderStrength(
        s: PasswordStrength.Strength,
        bars: List<View>,
        rules: List<TextView>,
        label: TextView
    ) {
        val color = when (s.score) {
            0, 1 -> R.color.strength_weak
            2, 3 -> R.color.strength_medium
            else -> R.color.strength_strong
        }
        val activeColor = ContextCompat.getColor(this, color)
        val idleColor = ContextCompat.getColor(this, R.color.divider_light)
        bars.forEachIndexed { i, v ->
            v.setBackgroundColor(if (i < s.score) activeColor else idleColor)
        }
        label.text = s.label
        label.setTextColor(activeColor)

        val passedColor = ContextCompat.getColor(this, R.color.strength_strong)
        val mutedColor = ContextCompat.getColor(this, R.color.on_surface_variant_light)
        rules.forEachIndexed { i, tv ->
            val rule = s.rules[i]
            tv.text = (if (rule.passed) "✓ " else "•  ") + rule.text
            tv.setTextColor(if (rule.passed) passedColor else mutedColor)
        }
    }
}
