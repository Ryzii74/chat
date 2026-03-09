package com.example.gamechat.ui.chat

enum class DeliveryState {
    NONE,
    SENDING,
    SENT,
    FAILED
}

data class ChatMessage(
    val id: String? = null,
    val senderName: String? = null,
    val text: String,
    val isOutgoing: Boolean,
    val deliveryState: DeliveryState = DeliveryState.NONE,
    val timeLabel: String = "",
    val imageUrl: String? = null
)
