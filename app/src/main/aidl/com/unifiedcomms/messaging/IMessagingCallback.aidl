package com.unifiedcomms.messaging;

import com.unifiedcomms.messaging.MessageParcel;
import com.unifiedcomms.messaging.ConversationParcel;

interface IMessagingCallback {
    void onMessageReceived(in MessageParcel message);
    void onMessageSent(in MessageParcel message);
    void onMessageDelivered(String messageId);
    void onMessageRead(String messageId);
    void onConversationUpdated(in ConversationParcel conversation);
    void onSyncStarted();
    void onSyncCompleted();
    void onSyncFailed(String error);
}