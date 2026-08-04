package com.example.notificationlogger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.notificationlogger.data.Message
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(private val onItemClick: (Message) -> Unit) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private var messages: List<Message> = emptyList()

    fun submitList(newMessages: List<Message>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(itemView: View, private val onItemClick: (Message) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val senderTextView: TextView = itemView.findViewById(R.id.senderTextView)
        private val contentTextView: TextView = itemView.findViewById(R.id.contentTextView)
        private val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
        private val mediaIndicator: TextView = itemView.findViewById(R.id.mediaIndicator)

        fun bind(message: Message) {
            senderTextView.text = message.sender
            
            // Show [DELETED] prefix if the message was unsent
            if (message.isDeleted) {
                contentTextView.text = "🚫 [UNSENT] ${message.content}"
                contentTextView.alpha = 0.5f
            } else {
                contentTextView.text = message.content
                contentTextView.alpha = 1.0f
            }

            mediaIndicator.visibility = if (message.mediaPath != null) View.VISIBLE else View.GONE

            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            timestampTextView.text = dateFormat.format(Date(message.timestamp))

            itemView.setOnClickListener {
                onItemClick(message)
            }
        }
    }
}