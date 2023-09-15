package com.example.aimobileapp

import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(val message: String, val isBot: Boolean, val isTyping: Boolean, val isFirstMessage: Boolean, val timestamp: String = getCurrentTime())

private fun getCurrentTime(): String {
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return dateFormat.format(calendar.time)
}

