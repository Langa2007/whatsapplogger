package com.example.notificationlogger

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.notificationlogger.data.AppDatabase
import com.example.notificationlogger.data.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatInterceptorService : AccessibilityService() {

    private val TAG = "ChatInterceptor"
    private var currentChatUser: String = "Unknown"

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return

        // 1. Try to find the person we are chatting with from the toolbar
        updateChatUser(rootNode)

        // 2. Scrape all visible text messages
        scrapeMessages(rootNode)
        
        rootNode.recycle()
    }

    private fun updateChatUser(root: AccessibilityNodeInfo) {
        // In WhatsApp, the contact name is usually in a TextView in the top toolbar
        // We look for the node that represents the chat title
        val toolbarNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/conversation_contact_name")
        if (toolbarNodes.isNotEmpty()) {
            val name = toolbarNodes[0].text?.toString()
            if (!name.isNullOrEmpty()) {
                currentChatUser = name
            }
        }
    }

    private fun scrapeMessages(root: AccessibilityNodeInfo) {
        // WhatsApp messages usually have the ID "message_text"
        val messageNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/message_text")
        
        for (node in messageNodes) {
            val content = node.text?.toString()
            if (!content.isNullOrEmpty()) {
                // If it's a message we haven't seen yet, save it
                saveInChatLevel(content)
            }
        }
    }

    private fun saveInChatLevel(content: String) {
        val context = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                
                // Duplicate check (don't save the same message twice in the same minute)
                val tenSecondsAgo = System.currentTimeMillis() - 10000
                val duplicate = db.messageDao().findDuplicate(currentChatUser, content, "com.whatsapp", tenSecondsAgo)
                
                if (duplicate == null) {
                    val message = Message(
                        sender = currentChatUser,
                        content = content,
                        packageName = "com.whatsapp",
                        timestamp = System.currentTimeMillis()
                    )
                    db.messageDao().insertMessage(message)
                    Log.d(TAG, "Scrapped in-chat message: $currentChatUser -> $content")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scrapping error: ${e.message}")
            }
        }
    }

    override fun onInterrupt() {}
}
