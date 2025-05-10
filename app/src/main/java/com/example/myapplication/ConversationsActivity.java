package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.data.DatabaseHelper;
import com.example.myapplication.model.Conversation;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;

public class ConversationsActivity extends BaseActivity {

    private RecyclerView rvMessages;
    private ConversationAdapter adapter;
    private List<Conversation> conversationList;
    private FirebaseFirestore db;
    private String currentUserEmail;
    private String currentUserId;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("ConversationsActivity", "onCreate started");

        db = FirebaseFirestore.getInstance();
        databaseHelper = new DatabaseHelper(this);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.e("ConversationsActivity", "User is not signed in!");
            showSnackbar("Hata: Lütfen giriş yapın!");
            finish();
            return;
        }
        currentUserId = auth.getCurrentUser().getUid();
        currentUserEmail = auth.getCurrentUser().getEmail();
        Log.d("ConversationsActivity", "currentUserId: " + currentUserId + ", currentUserEmail: " + currentUserEmail);

        if (currentUserId == null || currentUserEmail == null) {
            Log.e("ConversationsActivity", "currentUserId or currentUserEmail is null");
            showSnackbar("Hata: Kullanıcı bilgileri bulunamadı!");
            finish();
            return;
        }

        rvMessages = findViewById(R.id.rvMessages);
        conversationList = new ArrayList<>();
        adapter = new ConversationAdapter(conversationList, currentUserEmail, conversation -> {
            Intent chatIntent = new Intent(ConversationsActivity.this, ChatActivity.class);
            chatIntent.putExtra("ITEM_ID", conversation.getItemId());
            chatIntent.putExtra("CONVERSATION_ID", conversation.getId());
            String otherUserId = conversation.getParticipants().stream()
                    .filter(uid -> !uid.equals(currentUserId))
                    .findFirst()
                    .orElse(null);
            if (otherUserId != null) {
                databaseHelper.getUserEmailById(otherUserId, otherUserEmail -> {
                    chatIntent.putExtra("OTHER_USER_EMAIL", otherUserEmail);
                    chatIntent.putExtra("OTHER_USER_NAME", conversation.getOtherUserName());
                    chatIntent.putExtra("USER_EMAIL", currentUserEmail);
                    chatIntent.putExtra("USER_ID", currentUserId);
                    chatIntent.putExtra("CREATOR_ID", conversation.getCreatorId()); // creatorId’yi ekliyoruz
                    Log.d("ConversationsActivity", "Navigating to ChatActivity - conversationId: " + conversation.getId() + ", creatorId: " + conversation.getCreatorId());
                    startActivity(chatIntent);
                });
            } else {
                Log.e("ConversationsActivity", "Other user ID not found in participants");
                showSnackbar("Hata: Diğer kullanıcı bulunamadı!");
            }
        });
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        loadConversations();
    }

    private void loadConversations() {
        db.collection("conversations")
                .whereArrayContains("participants", currentUserId)
                .orderBy("lastMessageTime", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("ConversationsActivity", "Error loading conversations: " + error.getMessage());
                        if (error.getMessage().contains("PERMISSION_DENIED")) {
                            showSnackbar("Sohbetler yüklenemedi: Erişim izni eksik! Güvenlik kurallarını kontrol edin.");
                        } else if (error.getMessage().contains("orderBy")) {
                            showSnackbar("Sohbetler yüklenemedi: lastMessageTime alanı eksik veya hatalı!");
                        } else {
                            showSnackbar("Sohbetler yüklenemedi: " + error.getMessage());
                        }
                        return;
                    }
                    conversationList.clear();
                    if (value != null) {
                        List<Conversation> tempList = new ArrayList<>();
                        for (var doc : value) {
                            Conversation conv = doc.toObject(Conversation.class);
                            conv.setId(doc.getId());
                            tempList.add(conv);
                        }
                        for (Conversation conv : tempList) {
                            String itemId = conv.getItemId();
                            if (itemId != null) {
                                db.collection("lostItems")
                                        .document(itemId)
                                        .get()
                                        .addOnSuccessListener(itemDoc -> {
                                            if (itemDoc.exists()) {
                                                String creatorId = itemDoc.getString("creatorId");
                                                conv.setCreatorId(creatorId);
                                            } else {
                                                Log.w("ConversationsActivity", "Item not found for itemId: " + itemId);
                                            }

                                            String otherUserId = conv.getParticipants().stream()
                                                    .filter(uid -> !uid.equals(currentUserId))
                                                    .findFirst()
                                                    .orElse(null);
                                            if (otherUserId != null) {
                                                databaseHelper.getUserNameById(otherUserId, username -> {
                                                    conv.setOtherUserName(username != null ? username : "Bilinmeyen Kullanıcı");
                                                    conversationList.add(conv);
                                                    adapter.updateConversations(conversationList);
                                                    Log.d("ConversationsActivity", "Added conversation: " + conv.getId() +
                                                            ", otherUserName: " + conv.getOtherUserName() + ", creatorId: " + conv.getCreatorId());
                                                });
                                            } else {
                                                conv.setOtherUserName("Bilinmeyen Kullanıcı");
                                                conversationList.add(conv);
                                                adapter.updateConversations(conversationList);
                                                Log.d("ConversationsActivity", "Added conversation: " + conv.getId() +
                                                        ", otherUserName: " + conv.getOtherUserName() + ", creatorId: " + conv.getCreatorId());
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e("ConversationsActivity", "Error fetching creatorId for itemId: " + itemId + ", error: " + e.getMessage());
                                            conv.setOtherUserName("Bilinmeyen Kullanıcı");
                                            conversationList.add(conv);
                                            adapter.updateConversations(conversationList);
                                            Log.d("ConversationsActivity", "Added conversation: " + conv.getId() +
                                                    ", otherUserName: " + conv.getOtherUserName());
                                        });
                            } else {
                                Log.w("ConversationsActivity", "ItemId is null for conversation: " + conv.getId());
                                conv.setOtherUserName("Bilinmeyen Kullanıcı");
                                conversationList.add(conv);
                                adapter.updateConversations(conversationList);
                            }
                        }
                    }
                    if (conversationList.isEmpty()) {
                        Log.d("ConversationsActivity", "No conversations found");
                    }
                    Log.d("ConversationsActivity", "Loaded " + conversationList.size() + " conversations");
                });
    }

    private void showSnackbar(@NonNull String message) {
        View view = findViewById(R.id.rvMessages);
        if (view != null) {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    protected int getContentLayoutId() {
        return R.layout.activity_messages;
    }

    @Override
    protected int getSelectedNavigationItem() {
        return R.id.menu_messages;
    }

    @Override
    protected boolean shouldShowBottomNavigation() {
        return true;
    }

    @Override
    protected String getToolbarTitle() {
        return getString(R.string.toolbar_title_messages);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
}