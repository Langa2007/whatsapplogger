package com.example.notificationlogger

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notificationlogger.data.AppDatabase
import kotlinx.coroutines.launch

class ChatDetailActivity : AppCompatActivity() {

    private lateinit var messageAdapter: MessageAdapter
    private lateinit var senderName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_detail)

        senderName = intent.getStringExtra("SENDER_NAME") ?: "Unknown"

        val toolbar = findViewById<Toolbar>(R.id.chatToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = senderName
        toolbar.setNavigationOnClickListener { finish() }

        val recyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        messageAdapter = MessageAdapter { message ->
            if (message.mediaPath != null) {
                openMedia(message.mediaPath)
            } else {
                val details = "From: ${message.sender}\nContent: ${message.content}"
                android.widget.Toast.makeText(this, details, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = messageAdapter

        loadMessages()
    }

    private fun openMedia(path: String) {
        try {
            val file = java.io.File(path)
            if (!file.exists()) {
                android.widget.Toast.makeText(this, "File no longer exists", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                file
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getContentResolver().getType(uri))
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("ChatDetail", "Error opening media: ${e.message}")
            android.widget.Toast.makeText(this, "Error opening media", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.messageDao().getMessagesForChat(senderName).collect { messages ->
                messageAdapter.submitList(messages)
            }
        }
    }
}