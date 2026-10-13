package com.example.utils

/**
 * Tiny in-process flag set so an incoming chat push isn't shown as a system
 * notification when the user is already looking at that exact conversation.
 * [appInForeground] is toggled by MainActivity.onStart/onStop; [activeChatId]
 * by the ViewModel when a conversation is opened/closed.
 */
object ChatPushState {
    @Volatile var appInForeground: Boolean = false
    @Volatile var activeChatId: String = ""
}
