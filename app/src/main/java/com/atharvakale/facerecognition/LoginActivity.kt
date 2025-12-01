package com.atharvakale.facerecognition

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : AppCompatActivity() {

    private lateinit var bpLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var bpInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var goToFeatureButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        bindViews()
        setupListeners()
    }

    private fun bindViews() {
        bpLayout = findViewById(R.id.layoutBpId)
        passwordLayout = findViewById(R.id.layoutPassword)
        bpInput = findViewById(R.id.inputBpId)
        passwordInput = findViewById(R.id.inputPassword)
        loginButton = findViewById(R.id.buttonLogin)
        goToFeatureButton = findViewById(R.id.buttonGoToFeature)
    }

    private fun setupListeners() {
        loginButton.setOnClickListener { handleLogin() }
        goToFeatureButton.setOnClickListener { navigateToMain() }

        bpInput.doAfterTextChanged { bpLayout.error = null }
        passwordInput.doAfterTextChanged { passwordLayout.error = null }
    }

    private fun handleLogin() {
        val bpId = bpInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString()?.trim().orEmpty()
        var hasError = false

        if (bpId.isEmpty()) {
            bpLayout.error = getString(R.string.error_bp_required)
            hasError = true
        }

        if (password.isEmpty()) {
            passwordLayout.error = getString(R.string.error_password_required)
            hasError = true
        }

        if (hasError) return

        Toast.makeText(this, getString(R.string.toast_login_success), Toast.LENGTH_SHORT).show()
        navigateToMain()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }
}

