package com.unifiedcomms.ui.main

import androidx.lifecycle.ViewModel
import com.unifiedcomms.data.model.Conversation
import com.unifiedcomms.data.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MiniMessagingViewModel : ViewModel() {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _currentMessages = MutableStateFlow<List<Message>>(emptyList())
    val currentMessages: StateFlow<List<Message>> = _currentMessages

    private val _isPulling = MutableStateFlow(false)
    val isPulling: StateFlow<Boolean> = _isPulling

    private val _pullError = MutableStateFlow<String?>(null)
    val pullError: StateFlow<String?> = _pullError

    suspend fun pullMessages(_conversationId: String) { }

    suspend fun sendMessage(_conversationId: String, _message: Message) { }
}
