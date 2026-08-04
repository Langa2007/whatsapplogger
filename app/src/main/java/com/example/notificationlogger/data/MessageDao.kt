package com.example.notificationlogger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert
    suspend fun insertMessage(message: Message)

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE id IN (SELECT MAX(id) FROM messages GROUP BY sender) ORDER BY timestamp DESC")
    fun getChatList(): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE sender = :sender ORDER BY timestamp ASC")
    fun getMessagesForChat(sender: String): Flow<List<Message>>

    @Query("DELETE FROM messages WHERE timestamp < :timestamp")
    suspend fun deleteOldMessages(timestamp: Long)

    @Query("SELECT * FROM messages WHERE sender = :sender AND packageName = :packageName ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessageFromSender(sender: String, packageName: String): Message?

    @androidx.room.Update
    suspend fun updateMessage(message: Message)

    @Query("SELECT * FROM messages WHERE timestamp > :since ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessageForMedia(since: Long): Message?

    @Query("SELECT * FROM messages WHERE sender = :sender AND content = :content AND packageName = :packageName AND timestamp > :since ORDER BY timestamp DESC LIMIT 1")
    suspend fun findDuplicate(sender: String, content: String, packageName: String, since: Long): Message?
}