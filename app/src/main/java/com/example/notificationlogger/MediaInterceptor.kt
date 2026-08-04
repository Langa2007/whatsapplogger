package com.example.notificationlogger

import android.content.Context
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.notificationlogger.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

class MediaInterceptor(private val context: Context) {

    private val TAG = "MediaInterceptor"
    private val executor = Executors.newSingleThreadExecutor()
    private val observers = mutableListOf<FileObserver>()
    
    // The "Vault" where we store intercepted files
    private val vaultDir: File by lazy {
        // Try to find SD Card first, then fallback to internal storage
        val sdCard = getExternalSdCardPath()
        val baseDir = if (sdCard != null) {
            File(sdCard, "WhatsAppVault")
        } else {
            // Use public Pictures folder as a safe fallback
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WhatsAppVault")
        }
        
        if (!baseDir.exists()) baseDir.mkdirs()
        baseDir
    }

    private val pathsToWatch = listOf(
        "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images",
        "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video",
        "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
        "WhatsApp/Media/WhatsApp Images",
        "WhatsApp/Media/WhatsApp Video"
    )

    fun startWatching() {
        Log.d(TAG, "Starting media interception...")
        
        pathsToWatch.forEach { relativePath ->
            val fullPath = File(Environment.getExternalStorageDirectory(), relativePath)
            if (fullPath.exists()) {
                val observer = createObserver(fullPath)
                observer.startWatching()
                observers.add(observer)
                Log.d(TAG, "Watching folder: ${fullPath.absolutePath}")
            }
            
            // Also check for "Sent" folders inside them
            val sentPath = File(fullPath, "Sent")
            if (sentPath.exists()) {
                val sentObserver = createObserver(sentPath)
                sentObserver.startWatching()
                observers.add(sentObserver)
                Log.d(TAG, "Watching folder: ${sentPath.absolutePath}")
            }
        }
    }

    private fun createObserver(folder: File): FileObserver {
        return object : FileObserver(folder.path, CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null) {
                    val newFile = File(folder, path)
                    Log.d(TAG, "New file detected: ${newFile.absolutePath}")
                    interceptFile(newFile)
                }
            }
        }
    }

    private fun interceptFile(file: File) {
        executor.execute {
            try {
                // RACE CONDITION: Wait for WhatsApp to finish writing, but copy before it deletes.
                // We retry a few times in case the file is locked while writing.
                var copied = false
                var attempts = 0
                while (!copied && attempts < 10) {
                    Thread.sleep(300) // Small wait
                    if (file.exists() && file.length() > 0) {
                        val destination = File(vaultDir, "SAVED_${System.currentTimeMillis()}_${file.name}")
                        file.inputStream().use { input ->
                            destination.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d(TAG, "🚀 INTERCEPTED: Saved to ${destination.absolutePath}")
                        copied = true
                        
                        // Try to link this media to the most recent message in the database (within 30 seconds)
                        linkMediaToDatabase(destination.absolutePath)
                    }
                    attempts++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Interception failed for ${file.name}: ${e.message}")
            }
        }
    }

    private fun linkMediaToDatabase(path: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val thirtySecondsAgo = System.currentTimeMillis() - 30000
                val latestMessage = db.messageDao().getLatestMessageForMedia(thirtySecondsAgo)
                
                latestMessage?.let {
                    // Only link if it doesn't have media yet
                    if (it.mediaPath == null) {
                        val updated = it.copy(mediaPath = path)
                        db.messageDao().updateMessage(updated)
                        Log.d(TAG, "Linked media to message from ${it.sender}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error linking media: ${e.message}")
            }
        }
    }

    private fun getExternalSdCardPath(): File? {
        val externalStorageVolumes = ContextCompat.getExternalFilesDirs(context, null)
        for (volume in externalStorageVolumes) {
            if (volume != null && Environment.isExternalStorageRemovable(volume)) {
                // volume is something like /storage/XXXX-XXXX/Android/data/com.example...
                // We want the root: /storage/XXXX-XXXX/
                return volume.parentFile?.parentFile?.parentFile?.parentFile
            }
        }
        return null
    }

    fun stopWatching() {
        observers.forEach { it.stopWatching() }
        observers.clear()
    }
}
