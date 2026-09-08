package com.devicemonitor.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.devicemonitor.app.data.repository.DeviceRepository
import com.devicemonitor.app.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var repository: DeviceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = DeviceRepository(this)

        if (repository.getToken() != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text?.toString()?.trim() ?: ""
            val password = binding.passwordInput.text?.toString() ?: ""

            if (email.isEmpty() || password.isEmpty()) {
                showError("Email and password required")
                return@setOnClickListener
            }

            binding.loginButton.isEnabled = false
            lifecycleScope.launch {
                val result = repository.login(email, password)
                binding.loginButton.isEnabled = true
                result.onSuccess {
                    Toast.makeText(this@LoginActivity, "Logged in", Toast.LENGTH_SHORT).show()
                    // Start tracking immediately on login — no manual "Start" needed.
                    // Service will keep running until user explicitly logs out.
                    com.devicemonitor.app.service.LocationForegroundService.start(this@LoginActivity)
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }.onFailure { e ->
                    showError(e.message ?: "Login failed")
                }
            }
        }
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }
}
