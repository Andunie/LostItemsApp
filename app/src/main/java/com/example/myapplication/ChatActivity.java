package com.example.myapplication;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.model.Message;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatActivity extends BaseActivity {

    private RecyclerView rvMessages;
    private MessageAdapter adapter;
    private List<Message> messageList;
    private EditText etMessage;
    private ImageButton btnSend;
    private TextView tvConversationTitle;
    private Button btnEndChat;
    private FirebaseFirestore db;
    private String conversationId;
    private String currentUserEmail;
    private String currentUserId;
    private String otherUserEmail;
    private String otherUserName;
    private String itemId;
    private String ownerId;
    private String finderId;
    private String creatorId; // Yeni eklenen alan
    private boolean isActive;
    private List<String> participants;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = FirebaseFirestore.getInstance();
        messageList = new ArrayList<>();
        currentUserEmail = getIntent().getStringExtra("USER_EMAIL");
        currentUserId = getIntent().getStringExtra("USER_ID");
        otherUserEmail = getIntent().getStringExtra("OTHER_USER_EMAIL");
        otherUserName = getIntent().getStringExtra("OTHER_USER_NAME");
        conversationId = getIntent().getStringExtra("CONVERSATION_ID");
        itemId = getIntent().getStringExtra("ITEM_ID");
        creatorId = getIntent().getStringExtra("CREATOR_ID"); // creatorId'yi alıyoruz

        if (currentUserEmail == null || currentUserId == null || conversationId == null || otherUserEmail == null || itemId == null || creatorId == null) {
            Log.e("ChatActivity", "Required data missing - currentUserEmail: " + currentUserEmail +
                    ", currentUserId: " + currentUserId + ", conversationId: " + conversationId +
                    ", otherUserEmail: " + otherUserEmail + ", itemId: " + itemId + ", creatorId: " + creatorId);
            showSnackbar("Hata: Gerekli bilgiler eksik!");
            finish();
            return;
        }

        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        tvConversationTitle = findViewById(R.id.tvConversationTitle);
        btnEndChat = findViewById(R.id.btnEndChat);

        loadItemTitle();

        adapter = new MessageAdapter(messageList, currentUserEmail);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        checkConversationStatus();

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void checkConversationStatus() {
        db.collection("conversations")
                .document(conversationId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        isActive = documentSnapshot.getBoolean("isActive") != null ? documentSnapshot.getBoolean("isActive") : true;
                        participants = (List<String>) documentSnapshot.get("participants");
                        Log.d("ChatActivity", "Participants loaded: " + (participants != null ? participants.toString() : "null"));
                        if (participants != null && participants.size() >= 2) {
                            finderId = participants.get(0);
                            ownerId = participants.get(1);
                            Log.d("ChatActivity", "finderId: " + finderId + ", ownerId: " + ownerId);
                        } else {
                            Log.e("ChatActivity", "Invalid participants list: " + participants);
                            showSnackbar("Hata: Katılımcı listesi geçersiz!");
                            finish();
                            return;
                        }
                        if (!isActive) {
                            Log.w("ChatActivity", "Conversation is not active: " + conversationId);
                            disableMessageInput();
                        } else {
                            loadMessages();
                        }
                        setupEndChatButton(); // ownerId yüklendikten sonra buton ayarını yap
                    } else {
                        Log.e("ChatActivity", "Conversation not found: " + conversationId);
                        showSnackbar("Hata: Konuşma bulunamadı!");
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatActivity", "Error checking conversation status: " + e.getMessage());
                    showSnackbar("Hata: Konuşma durumu kontrol edilemedi!");
                });
    }

    private void disableMessageInput() {
        etMessage.setEnabled(false);
        btnSend.setEnabled(false);
        etMessage.setHint("Bu sohbet sonlandırılmıştır.");
    }

    private void loadItemTitle() {
        db.collection("lostItems")
                .document(itemId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String itemTitle = documentSnapshot.getString("itemTitle");
                        tvConversationTitle.setText(itemTitle != null ? itemTitle : otherUserName != null ? otherUserName : otherUserEmail);
                        Log.d("ChatActivity", "Item title loaded: " + itemTitle);
                    } else {
                        tvConversationTitle.setText(otherUserName != null ? otherUserName : otherUserEmail);
                        Log.w("ChatActivity", "Item not found for itemId: " + itemId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatActivity", "Error fetching item title: " + e.getMessage());
                    tvConversationTitle.setText(otherUserName != null ? otherUserName : otherUserEmail);
                    showSnackbar("Hata: İlan başlığı yüklenemedi!");
                });
    }

    private void setupEndChatButton() {
        Log.d("ChatActivity", "Setting up end chat button. currentUserId: " + currentUserId + ", creatorId: " + creatorId);
        if (currentUserId != null && creatorId != null && currentUserId.equals(creatorId)) {
            btnEndChat.setVisibility(View.VISIBLE);
            btnEndChat.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Eşya Durumu")
                        .setMessage("Did the item reach its owner?")
                        .setPositiveButton("Yes", (dialog, which) -> endConversation(true))
                        .setNegativeButton("No", (dialog, which) -> endConversation(false))
                        .setCancelable(false)
                        .show();
            });
        } else {
            btnEndChat.setVisibility(View.GONE);
            Log.d("ChatActivity", "User is not authorized to end the conversation. currentUserId: " + currentUserId + ", creatorId: " + creatorId);
        }
    }

    private void endConversation(boolean isFound) {
        // Update user counts when conversation ends
        updateUserCounts();
        
        if (isFound) {
            // İlgili item'in isDelivered alanını true yap ve finderId'yi güncelle
            Map<String, Object> updates = new HashMap<>();
            updates.put("isDelivered", true);
            
            // finderId null ise, conversations koleksiyonundaki bulan kişinin ID'sini kullan
            if (finderId != null && !finderId.isEmpty()) {
                updates.put("finderId", finderId);
                Log.d("ChatActivity", "Updating finderId in lostItems to: " + finderId);
            }
            
            db.collection("lostItems")
                    .document(itemId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d("ChatActivity", "Lost item marked as delivered: " + itemId);
                        showSnackbar("Eşya teslim edildi olarak işaretlendi.");
                    })
                    .addOnFailureListener(e -> {
                        Log.e("ChatActivity", "Error updating lost item status: " + e.getMessage());
                        showSnackbar("Hata: Eşya durumu güncellenemedi! " + e.getMessage());
                    });
        }

        HashMap<String, Object> updateData = new HashMap<>();
        updateData.put("isActive", false);

        db.collection("conversations")
                .document(conversationId)
                .update(updateData)
                .addOnSuccessListener(aVoid -> {
                    Log.d("ChatActivity", "Conversation marked as inactive: " + conversationId);
                    showSnackbar("Sohbet sonlandırıldı.");
                    checkConversationStatus();
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatActivity", "Error marking conversation as inactive: " + e.getMessage());
                    showSnackbar("Hata: Sohbet sonlandırılamadı! " + e.getMessage());
                });
    }

    private void updateUserCounts() {
        if (ownerId == null || finderId == null) {
            Log.e("ChatActivity", "ownerId or finderId is null - ownerId: " + ownerId + ", finderId: " + finderId);
            showSnackbar("Hata: Kullanıcı bilgileri eksik!");
            return;
        }

        Log.d("ChatActivity", "Updating user counts - ownerId: " + ownerId + ", finderId: " + finderId);

        // itemId'yi kullanarak ilanın bilgilerini çek
        db.collection("lostItems")
                .document(itemId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String postedBy = documentSnapshot.getString("postedBy");
                        String creatorIdFromItem = documentSnapshot.getString("creatorId");
                        
                        Log.d("ChatActivity", "Item details: postedBy=" + postedBy + 
                               ", creatorId=" + creatorIdFromItem + 
                               ", conversations/participants: finderId=" + finderId + 
                               ", ownerId=" + ownerId);

                        // İlan türüne göre sayaçları güncelle
                        if ("lost".equals(postedBy)) {
                            // Kaybolan eşya ilanında:
                            // 1. Kaybeden kişinin (creator/owner) deliveredItemsCount artmalı
                            incrementDeliveredItemsCount(creatorIdFromItem);
                            Log.d("ChatActivity", "Incremented deliveredItemsCount for owner: " + creatorIdFromItem);
                            
                            // 2. Bulan kişinin (finder from conversations) foundItemsCount artmalı
                            incrementFoundItemsCount(finderId);
                            Log.d("ChatActivity", "Incremented foundItemsCount for finder: " + finderId);
                            
                        } else if ("found".equals(postedBy)) {
                            // Bulunan eşya ilanında:
                            // 1. Bulan kişinin (creator) deliveredItemsCount artmalı
                            incrementDeliveredItemsCount(creatorIdFromItem);
                            Log.d("ChatActivity", "Incremented deliveredItemsCount for finder: " + creatorIdFromItem);
                            
                            // 2. Eşya sahibinin (owner from conversations) foundItemsCount artmalı
                            incrementFoundItemsCount(ownerId);
                            Log.d("ChatActivity", "Incremented foundItemsCount for owner: " + ownerId);
                        } else {
                            Log.e("ChatActivity", "Invalid postedBy value: " + postedBy);
                            showSnackbar("Hata: Geçersiz ilan türü!");
                        }
                        
                        Log.d("ChatActivity", "User counts updated successfully for item: " + itemId);
                    } else {
                        Log.e("ChatActivity", "Lost item document does not exist for itemId: " + itemId);
                        showSnackbar("Hata: İlan bulunamadı!");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatActivity", "Error fetching lost item: " + e.getMessage());
                    showSnackbar("Hata: İlan bilgileri alınamadı!");
                });
    }

    // Kullanıcının deliveredItemsCount değerini artır
    private void incrementDeliveredItemsCount(String userId) {
        Log.d("ChatActivity", "Incrementing deliveredItemsCount for user: " + userId);
        db.collection("users")
                .document(userId)
                .update("deliveredItemsCount", FieldValue.increment(1))
                .addOnSuccessListener(aVoid -> 
                    Log.d("ChatActivity", "deliveredItemsCount incremented successfully for user: " + userId))
                .addOnFailureListener(e -> 
                    Log.e("ChatActivity", "Error incrementing deliveredItemsCount for user: " + userId + " - " + e.getMessage()));
    }

    // Kullanıcının foundItemsCount değerini artır
    private void incrementFoundItemsCount(String userId) {
        Log.d("ChatActivity", "Incrementing foundItemsCount for user: " + userId);
        db.collection("users")
                .document(userId)
                .update("foundItemsCount", FieldValue.increment(1))
                .addOnSuccessListener(aVoid -> 
                    Log.d("ChatActivity", "foundItemsCount incremented successfully for user: " + userId))
                .addOnFailureListener(e -> 
                    Log.e("ChatActivity", "Error incrementing foundItemsCount for user: " + userId + " - " + e.getMessage()));
    }

    private void loadMessages() {
        db.collection("messages")
                .whereEqualTo("conversationId", conversationId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("ChatActivity", "Error loading messages: " + error.getMessage());
                        if (error.getMessage().contains("PERMISSION_DENIED")) {
                            showSnackbar("Mesajlar yüklenemedi: Erişim izni eksik! Güvenlik kurallarını kontrol edin.");
                        } else if (error.getMessage().contains("orderBy")) {
                            showSnackbar("Mesajlar yüklenemedi: timestamp alanı eksik veya hatalı!");
                        } else {
                            showSnackbar("Mesajlar yüklenemedi: " + error.getMessage());
                        }
                        return;
                    }
                    messageList.clear();
                    if (value != null) {
                        for (var doc : value) {
                            Message message = doc.toObject(Message.class);
                            messageList.add(message);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    rvMessages.scrollToPosition(messageList.size() - 1);
                    Log.d("ChatActivity", "Loaded " + messageList.size() + " messages for conversationId: " + conversationId);
                });
    }

    private void sendMessage() {
        String messageContent = etMessage.getText().toString().trim();
        if (messageContent.isEmpty()) {
            showSnackbar("Mesaj boş olamaz!");
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("conversationId", conversationId);
        message.put("senderEmail", currentUserEmail);
        message.put("messageContent", messageContent);
        message.put("timestamp", FieldValue.serverTimestamp());

        db.collection("messages")
                .add(message)
                .addOnSuccessListener(documentReference -> {
                    Log.d("ChatActivity", "Message sent with ID: " + documentReference.getId());
                    etMessage.setText("");

                    db.collection("conversations")
                            .document(conversationId)
                            .update(
                                    "lastMessage", messageContent,
                                    "lastMessageTime", FieldValue.serverTimestamp()
                            )
                            .addOnSuccessListener(aVoid -> Log.d("ChatActivity", "Conversation updated"))
                            .addOnFailureListener(e -> Log.e("ChatActivity", "Error updating conversation: " + e.getMessage()));
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatActivity", "Error sending message: " + e.getMessage());
                    if (e.getMessage().contains("PERMISSION_DENIED")) {
                        showSnackbar("Mesaj gönderilemedi: Erişim izni eksik! Güvenlik kurallarını kontrol edin.");
                    } else {
                        showSnackbar("Mesaj gönderilemedi: " + e.getMessage());
                    }
                });
    }

    private void showSnackbar(@NonNull String message) {
        View view = findViewById(R.id.rvMessages);
        if (view != null) {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        } else {
            Log.e("ChatActivity", "Cannot show Snackbar: View is null. Message: " + message);
        }
    }

    private void SetInvisibleEndConversationButton()
    {
        btnEndChat.setVisibility(View.GONE);
    }

    @Override
    protected int getContentLayoutId() {
        return R.layout.activity_chat;
    }

    @Override
    protected int getSelectedNavigationItem() {
        return R.id.menu_messages;
    }

    @Override
    protected boolean shouldShowBottomNavigation() {
        return false;
    }

    @Override
    protected String getToolbarTitle() {
        return "Chat";
    }
}