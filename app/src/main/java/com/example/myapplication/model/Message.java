package com.example.myapplication.model;

import com.google.firebase.Timestamp;

public class Message {
    private String conversationId;
    private String senderEmail;
    private String messageContent;
    private Timestamp timestamp; // Timestamp türünde

    public Message() {
        // Boş yapıcı, Firestore için gerekli
    }

    public Message(String conversationId, String senderEmail, String messageContent, Timestamp timestamp) {
        this.conversationId = conversationId;
        this.senderEmail = senderEmail;
        this.messageContent = messageContent;
        this.timestamp = timestamp;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }
}