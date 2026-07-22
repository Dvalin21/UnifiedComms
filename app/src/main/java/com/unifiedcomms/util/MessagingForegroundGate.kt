package com.unifiedcomms.util

import android.content.Context
import com.unifiedcomms.data.model.Conversation

object MessagingForegroundGate {
    private val openConversations = mutableSetOf<String>()

    fun setOpen(conversationId: String, open: Boolean) {
        if (open) openConversations.add(conversationId) else openConversations.remove(conversationId)
    }

    fun isOpen(conversationId: String): Boolean = openConversations.contains(conversationId)
}
