package com.example.notificationlogger.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val packageName: String = "",
    val mediaPath: String? = null
)
