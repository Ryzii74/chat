package com.example.gamechat.ui.chat

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.gamechat.R

class ChatMessageAdapter(
    private val items: List<ChatMessage>,
    private val onMessageLongPress: (ChatMessage) -> Unit,
    private val onAnswerClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, onAnswerClick)
        holder.itemView.setOnLongClickListener {
            onMessageLongPress(item)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bubbleContainer: LinearLayout = view.findViewById(R.id.bubbleContainer)
        private val messageSender: TextView = view.findViewById(R.id.messageSender)
        private val messageText: TextView = view.findViewById(R.id.messageText)
        private val messageImage: ImageView = view.findViewById(R.id.messageImage)
        private val messageMetaRow: LinearLayout = view.findViewById(R.id.messageMetaRow)
        private val messageTime: TextView = view.findViewById(R.id.messageTime)
        private val messageStatus: TextView = view.findViewById(R.id.messageStatus)

        fun bind(item: ChatMessage, onAnswerClick: ((String) -> Unit)? = null) {
            val processedText = processClickableText(item.text, onAnswerClick)
            if (processedText.first != null) {
                // Есть кликабельный текст
                messageText.text = processedText.first
                messageText.movementMethod = LinkMovementMethod.getInstance()
            } else {
                messageText.text = item.text
                messageText.movementMethod = null
            }
            
            messageTime.text = item.timeLabel
            bindImage(item.imageUrl)
            messageText.visibility = if (item.text.isBlank()) View.GONE else View.VISIBLE
            bindSender(item.senderName)

            // Увеличиваем межстрочное расстояние для лучшей читаемости
            messageText.setLineSpacing(dp(4).toFloat(), 1.2f)

            val params = bubbleContainer.layoutParams as FrameLayout.LayoutParams
            if (item.isOutgoing) {
                params.gravity = Gravity.END
                bubbleContainer.setBackgroundResource(R.drawable.bg_chat_bubble_outgoing)
                messageText.setTextColor(0xFFFFFFFF.toInt())
                messageSender.setTextColor(0xFFB3E5FC.toInt())
                messageTime.setTextColor(0xB3FFFFFF.toInt())
                bindOutgoingStatus(item.deliveryState)
            } else {
                params.gravity = Gravity.START
                bubbleContainer.setBackgroundResource(R.drawable.bg_chat_bubble_incoming)
                messageText.setTextColor(0xFFE7ECF8.toInt())
                messageSender.setTextColor(0xFF90CAF9.toInt())
                messageTime.setTextColor(0xB3E7ECF8.toInt())
                messageStatus.visibility = View.GONE
            }

            val shouldShowMeta = item.timeLabel.isNotBlank() || (item.isOutgoing && item.deliveryState != DeliveryState.NONE)
            messageMetaRow.visibility = if (shouldShowMeta) View.VISIBLE else View.GONE
            bubbleContainer.layoutParams = params
        }

        private fun bindSender(senderName: String?) {
            val sender = senderName?.trim().orEmpty()
            if (sender.isBlank()) {
                messageSender.visibility = View.GONE
                messageText.setPadding(
                    messageText.paddingLeft,
                    dp(10),
                    messageText.paddingRight,
                    messageText.paddingBottom
                )
                return
            }
            messageSender.text = sender
            messageSender.visibility = View.VISIBLE
            messageText.setPadding(
                messageText.paddingLeft,
                dp(2),
                messageText.paddingRight,
                messageText.paddingBottom
            )
        }

        private fun dp(value: Int): Int {
            val density = itemView.resources.displayMetrics.density
            return (value * density).toInt()
        }

        private fun processClickableText(text: String, onAnswerClick: ((String) -> Unit)? = null): Pair<SpannableStringBuilder?, List<String>> {
            if (!text.contains("[CLICKABLE]")) {
                return Pair(null, emptyList())
            }

            val spannableBuilder = SpannableStringBuilder()
            val clickableAnswers = mutableListOf<String>()
            val parts = text.split("[CLICKABLE]")
            
            spannableBuilder.append(parts[0]) // Текст до первого маркера
            
            for (i in 1 until parts.size) {
                val part = parts[i]
                val endIndex = part.indexOf("[/CLICKABLE]")
                if (endIndex != -1) {
                    val clickableText = part.substring(0, endIndex).trim()
                    val remainingText = part.substring(endIndex + "[/CLICKABLE]".length)
                    
                    if (clickableText.isNotEmpty()) {
                        clickableAnswers.add(clickableText)
                        
                        val startIndex = spannableBuilder.length
                        spannableBuilder.append(clickableText)
                        val endIndexSpan = spannableBuilder.length
                        
                        val clickableSpan = object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                onAnswerClick?.invoke(clickableText)
                            }
                        }
                        
                        spannableBuilder.setSpan(
                            clickableSpan,
                            startIndex,
                            endIndexSpan,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    
                    spannableBuilder.append(remainingText)
                } else {
                    spannableBuilder.append(part)
                }
            }
            
            return Pair(spannableBuilder, clickableAnswers)
        }

        private fun bindImage(imageUrl: String?) {
            if (imageUrl.isNullOrBlank()) {
                messageImage.visibility = View.GONE
                messageImage.setImageDrawable(null)
                return
            }
            messageImage.visibility = View.VISIBLE
            messageImage.load(imageUrl) {
                crossfade(true)
            }
        }

        private fun bindOutgoingStatus(state: DeliveryState) {
            when (state) {
                DeliveryState.NONE -> {
                    messageStatus.visibility = View.GONE
                }

                DeliveryState.SENDING -> {
                    messageStatus.visibility = View.VISIBLE
                    messageStatus.text = "\u2713"
                    messageStatus.setTextColor(0xB3FFFFFF.toInt())
                }

                DeliveryState.SENT -> {
                    messageStatus.visibility = View.VISIBLE
                    messageStatus.text = "\u2713\u2713"
                    messageStatus.setTextColor(0xFFB3E5FC.toInt())
                }

                DeliveryState.FAILED -> {
                    messageStatus.visibility = View.VISIBLE
                    messageStatus.text = "!"
                    messageStatus.setTextColor(0xFFFFCDD2.toInt())
                }
            }
        }
    }
}
