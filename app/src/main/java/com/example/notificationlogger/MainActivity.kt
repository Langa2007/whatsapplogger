package com.example.notificationlogger

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notificationlogger.data.AppDatabase
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var messageAdapter: MessageAdapter
    private lateinit var statusText: TextView
    private lateinit var enableButton: Button
    private lateinit var accessibilityButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        enableButton = findViewById(R.id.enableButton)
        accessibilityButton = findViewById(R.id.accessibilityButton)
        val recyclerView = findViewById<RecyclerView>(R.id.messageRecyclerView)

        // Setup RecyclerView
        messageAdapter = MessageAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = messageAdapter

        // Setup button
        enableButton.setOnClickListener {
            openNotificationSettings()
        }

        accessibilityButton.setOnClickListener {
            openAccessibilitySettings()
        }

        // Check if notification access is granted
        checkNotificationAccess()
        
        // Check accessibility
        checkAccessibilityStatus()
        
        // Check for "All Files Access" (Required for Media Interceptor)
        checkAllFilesAccess()

        // Load messages
        loadMessages()
    }

    private fun checkAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    private fun checkNotificationAccess() {
        val notificationListenerEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val enabledListeners = Settings.Secure.getString(
                contentResolver,
                "enabled_notification_listeners"
            )
            enabledListeners?.contains(packageName) ?: false
        } else {
            false
        }

        if (notificationListenerEnabled) {
            statusText.text = "✅ Service is running"
            enableButton.text = "Service Active"
            enableButton.isEnabled = false
        } else {
            statusText.text = "❌ Notification access not granted"
            enableButton.text = "Grant Permission"
            enableButton.isEnabled = true
        }
    }

    private fun checkAccessibilityStatus() {
        val enabled = isAccessibilityServiceEnabled()
        if (enabled) {
            accessibilityButton.text = "Scrapper Active"
            accessibilityButton.isEnabled = false
        } else {
            accessibilityButton.text = "Enable Scrapper"
            accessibilityButton.isEnabled = true
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${ChatInterceptorService::class.java.canonicalName}"
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        if (enabled == 1) {
            val settingValue = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return settingValue?.contains(service) == true
        }
        return false
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.messageDao().getAllMessages().collect { messages ->
                messageAdapter.submitList(messages)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationAccess()
        checkAccessibilityStatus()
    }
}

