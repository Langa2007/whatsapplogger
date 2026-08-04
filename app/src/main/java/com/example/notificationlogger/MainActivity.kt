package com.example.notificationlogger

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notificationlogger.data.AppDatabase
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var messageAdapter: MessageAdapter
    private lateinit var statusText: TextView
    private lateinit var emptyView: TextView
    private lateinit var enableButton: Button
    private lateinit var accessibilityButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.title = "Logged Chats"

        statusText = findViewById(R.id.statusText)
        emptyView = findViewById(R.id.emptyView)
        enableButton = findViewById(R.id.enableButton)
        accessibilityButton = findViewById(R.id.accessibilityButton)
        val recyclerView = findViewById<RecyclerView>(R.id.messageRecyclerView)

        // Setup RecyclerView
        messageAdapter = MessageAdapter { message ->
            val intent = Intent(this, ChatDetailActivity::class.java).apply {
                putExtra("SENDER_NAME", message.sender)
            }
            startActivity(intent)
        }
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

        // Long click status to clear database (for debugging)
        statusText.setOnLongClickListener {
            clearDatabase()
            true
        }
    }

    private fun clearDatabase() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.messageDao().deleteOldMessages(System.currentTimeMillis() + 10000) // Delete all
            android.widget.Toast.makeText(this@MainActivity, "Database Cleared", android.widget.Toast.LENGTH_SHORT).show()
        }
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
        val componentName = android.content.ComponentName(packageName, ChatInterceptorService::class.java.name)
        
        // On Android 14+ (API 34), we can attempt to open the details page directly
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                // Using the literal string to avoid compilation issues if SDK index is lagging
                val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                    putExtra(Intent.EXTRA_COMPONENT_NAME, componentName.flattenToString())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to open specific settings: ${e.message}")
            }
        }

        // Fallback for older versions or if the direct intent fails
        try {
            // Show a helpful dialog first because the screen they are going to is confusing (especially on Samsung)
            AlertDialog.Builder(this)
                .setTitle("Enable Scrapper")
                .setMessage("On the next screen:\n\n1. Tap 'Installed apps' (usually at the bottom)\n2. Find 'Notification Logger'\n3. Turn the switch ON")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error opening accessibility settings: ${e.message}")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.messageDao().getChatList().collect { messages ->
                messageAdapter.submitList(messages)
                emptyView.visibility = if (messages.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                
                // Debug status update
                if (messages.isNotEmpty()) {
                    statusText.text = "✅ Logging Active (${messages.size} chats)"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationAccess()
        checkAccessibilityStatus()
    }
}

