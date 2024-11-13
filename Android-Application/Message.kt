package com.example.legalwrite

data class Message(
    var text: String = "",
    val isUser: Boolean = false,
    val isTyping: Boolean = false
)
