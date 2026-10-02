package com.example.data

import java.io.Serializable

/** One message inside a 1:1 chat. */
data class ChatMessageEntity(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/** Inbox row for the current user. */
data class ChatThreadEntity(
    val chatId: String = "",
    val otherUid: String = "",
    val otherName: String = "",
    val otherPhoto: String = "",
    val lastMessage: String = "",
    val updatedAt: Long = 0L,
    val unread: Int = 0
) : Serializable

fun chatIdFor(uidA: String, uidB: String): String =
    listOf(uidA, uidB).sorted().joinToString("__")
