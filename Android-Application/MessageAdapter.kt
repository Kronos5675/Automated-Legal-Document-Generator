package com.example.legalwriteapp

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.legalwrite.R
import com.example.legalwrite.Message

class MessageAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_USER_MESSAGE = 0
    private val VIEW_TYPE_BOT_MESSAGE = 1
    private val VIEW_TYPE_TYPING_INDICATOR = 2

    private var isTyping = false

    override fun getItemViewType(position: Int): Int {
        return when {
            messages[position].isTyping -> VIEW_TYPE_TYPING_INDICATOR
            messages[position].isUser -> VIEW_TYPE_USER_MESSAGE
            else -> VIEW_TYPE_BOT_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_TYPING_INDICATOR -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.typing_indicator, parent, false)
                TypingIndicatorViewHolder(view)
            }
            VIEW_TYPE_USER_MESSAGE -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_message, parent, false)
                MessageViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bot_message, parent, false)
                MessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MessageViewHolder -> holder.bind(messages[position])
            is TypingIndicatorViewHolder -> holder.bind()
        }
    }

    override fun getItemCount(): Int = messages.size

    // Show typing indicator
    fun showTypingIndicator() {
        if (!isTyping) {
            isTyping = true
            messages.add(Message(isTyping = true))
            notifyItemInserted(messages.size - 1)
        }
    }

    // Hide typing indicator
    fun hideTypingIndicator() {
        if (isTyping) {
            isTyping = false
            val position = messages.indexOfFirst { it.isTyping }
            if (position != -1) {
                messages.removeAt(position)
                notifyItemRemoved(position)
            }
        }
    }

    // Add message to the list and simulate typing for bot messages
    fun addMessage(messageText: String, isUser: Boolean) {
        val message = Message(isUser = isUser, text = messageText)
        messages.add(message)
        notifyItemInserted(messages.size - 1)

        // If it's a bot message, simulate typing effect
        if (!isUser) {
            simulateBotTypingEffect(messageText)
        }
    }

    // Simulate typing effect for bot messages
    private fun simulateBotTypingEffect(messageText: String) {
        val handler = Handler(Looper.getMainLooper())
        val delay = 100L // Delay between each character in milliseconds
        val stringBuilder = StringBuilder()

        // Loop through the message and add characters one by one
        for (i in messageText.indices) {
            handler.postDelayed({
                stringBuilder.append(messageText[i])
                val position = messages.size - 1
                messages[position].text = stringBuilder.toString() // Update the message text
                notifyItemChanged(position) // Notify RecyclerView of the update
            }, i * delay) // Delay increases with each character
        }
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val senderLabel: TextView = itemView.findViewById(R.id.senderLabel)

        fun bind(message: Message) {
            messageText.text = message.text
            senderLabel.text = if (message.isUser) "User" else "LegalDocs Bot"
        }
    }

    inner class TypingIndicatorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dot1: ImageView = itemView.findViewById(R.id.dot1)
        private val dot2: ImageView = itemView.findViewById(R.id.dot2)
        private val dot3: ImageView = itemView.findViewById(R.id.dot3)

        fun bind() {
            // Animation for dot1
            val animator1 = ObjectAnimator.ofFloat(dot1, "scaleX", 0.5f, 1f).apply {
                duration = 500
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
            }
            // Animation for dot2
            val animator2 = ObjectAnimator.ofFloat(dot2, "scaleX", 0.5f, 1f).apply {
                startDelay = 250
                duration = 500
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
            }
            // Animation for dot3
            val animator3 = ObjectAnimator.ofFloat(dot3, "scaleX", 0.5f, 1f).apply {
                startDelay = 500
                duration = 500
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
            }

            animator1.start()
            animator2.start()
            animator3.start()
        }
    }
}
