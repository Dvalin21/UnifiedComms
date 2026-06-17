package com.unifiedcomms.messaging;

import com.unifiedcomms.messaging.MessageParcel;
import com.unifiedcomms.messaging.ConversationParcel;

interface IMessagingService {
    String sendMessage(in MessageParcel message);
    List<ConversationParcel> getConversations(String userId);
    void markAsRead(List<String> messageIds);
    void registerCallback(IMessagingCallback callback);
    void unregisterCallback(IMessagingCallback callback);
}