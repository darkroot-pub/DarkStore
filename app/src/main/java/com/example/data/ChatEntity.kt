package com.example.data

/** One message under chats/{chatId}/messages/{msgId} (or globalChat/messages). */
data class ChatMessageEntity(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val timestamp: Long = 0L,
    /** Non-zero when the sender edited the message. */
    val editedAt: Long = 0L,
    /** Soft-delete flag; UI shows a placeholder when true. */
    val deleted: Boolean = false
)

/** One inbox row under userChats/{uid}/{chatId}. */
data class ChatThreadEntity(
    val chatId: String = "",
    val otherUid: String = "",
    val otherName: String = "",
    val otherPhoto: String = "",
    val lastMessage: String = "",
    val updatedAt: Long = 0L,
    val unread: Int = 0
)

/** Deterministic 1:1 chat id from two user uids. */
fun chatIdFor(uidA: String, uidB: String): String {
    val (x, y) = if (uidA < uidB) uidA to uidB else uidB to uidA
    return "c_${x}_$y"
}
