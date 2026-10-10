package com.example.data

import java.io.Serializable

/** One message inside a 1:1 chat. */
data class ChatMessageEntity(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val edited: Boolean = false,
    val editedAt: Long = 0L,
    /** Soft-deleted: shown as "This message was deleted" for everyone. */
    val deleted: Boolean = false,
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToName: String = "",
    /** Only filled in the global room (1:1 chats already know who is who). */
    val senderName: String = "",
    val senderPhoto: String = "",
    /** uid → emoji */
    val reactions: Map<String, String> = emptyMap()
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
