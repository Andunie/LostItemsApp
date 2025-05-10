package com.example.myapplication;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.example.myapplication.model.LostItem;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class LostItemDetailActivity extends BaseActivity implements OnMapReadyCallback {

    private TextView tvItemTitle, tvAddress, tvUserName, tvMapError;
    private String currentUserEmail;
    private String currentUserId;
    private String userName;
    private GoogleMap googleMap;
    private String address;
    private String itemOwnerEmail;
    private String itemId;
    private String ownerId;
    private String finderId;
    private String creatorId; // Yeni eklenen alan
    private String postedBy;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.e("LostItemDetailActivity", "User is not signed in!");
            showSnackbar("Hata: Lütfen giriş yapın!");
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        } else {
            Log.d("LostItemDetailActivity", "User is signed in: " + auth.getCurrentUser().getUid());
        }

        db = FirebaseFirestore.getInstance();

        tvItemTitle = findViewById(R.id.tvItemTitle);
        tvAddress = findViewById(R.id.tvAddress);
        tvUserName = findViewById(R.id.tvUserName);
        tvMapError = findViewById(R.id.tvMapError);

        Intent intent = getIntent();
        if (intent != null) {
            itemId = intent.getStringExtra("itemId");
            currentUserId = intent.getStringExtra("USER_ID");
            userName = intent.getStringExtra("USER_NAME");
            currentUserEmail = intent.getStringExtra("currentUserEmail");
            Log.d("LostItemDetailActivity", "onCreate - Intent received - itemId: " + itemId +
                    ", UserId: " + currentUserId + ", UserName: " + (userName != null ? userName : "null") +
                    ", CurrentUserEmail: " + (currentUserEmail != null ? currentUserEmail : "null"));
        } else {
            Log.e("LostItemDetailActivity", "Intent is null!");
            showSnackbar("Hata: Intent alınamadı!");
            finish();
            return;
        }

        if (currentUserEmail == null || itemId == null || itemId.isEmpty() || currentUserId == null || currentUserId.isEmpty()) {
            Log.e("LostItemDetailActivity", "Invalid data - currentUserEmail: " + currentUserEmail +
                    ", itemId: " + itemId + ", userId: " + currentUserId);
            showSnackbar("Hata: Gerekli bilgiler eksik!");
            finish();
            return;
        }

        loadLostItem();
    }

    private void loadLostItem() {
        db.collection("lostItems")
                .document(itemId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        LostItem item = new LostItem(
                                documentSnapshot.getId(),
                                documentSnapshot.getString("userId"),
                                documentSnapshot.getString("itemTitle"),
                                documentSnapshot.getString("description"),
                                documentSnapshot.getString("address")
                        );
                        item.setPostedBy(documentSnapshot.getString("postedBy"));
                        item.setCreatorId(documentSnapshot.getString("creatorId"));
                        item.setOwnerId(documentSnapshot.getString("ownerId"));
                        item.setFinderId(documentSnapshot.getString("finderId"));
                        item.setDelivered(documentSnapshot.getBoolean("isDelivered") != null ? documentSnapshot.getBoolean("isDelivered") : false);

                        postedBy = item.getPostedBy();
                        ownerId = item.getOwnerId();
                        finderId = item.getFinderId();
                        creatorId = item.getCreatorId(); // creatorId’yi alıyoruz
                        Log.d("LostItemDetailActivity", "Loaded - postedBy: " + postedBy + ", ownerId: " + (ownerId != null ? ownerId : "null") +
                                ", finderId: " + (finderId != null ? finderId : "null") + ", creatorId: " + (creatorId != null ? creatorId : "null"));

                        address = item.getAddress();
                        tvItemTitle.setText(item.getTitle() != null ? item.getTitle() : "Başlık Yok");
                        tvAddress.setText(address != null ? address : "Adres Yok");

                        String creatorIdLocal = item.getCreatorId();
                        if (creatorIdLocal != null && !creatorIdLocal.isEmpty()) {
                            db.collection("users")
                                    .document(creatorIdLocal)
                                    .get()
                                    .addOnSuccessListener(userDoc -> {
                                        if (userDoc.exists()) {
                                            String creatorUserName = userDoc.getString("username");
                                            itemOwnerEmail = userDoc.getString("email");
                                            tvUserName.setText(creatorUserName != null && !creatorUserName.isEmpty() ? creatorUserName : "Bilinmeyen Kullanıcı");
                                            Log.d("LostItemDetailActivity", "Creator username loaded: " + creatorUserName + " for creatorId: " + creatorIdLocal);
                                        } else {
                                            tvUserName.setText("Bilinmeyen Kullanıcı");
                                            Log.w("LostItemDetailActivity", "Creator user not found for creatorId: " + creatorIdLocal);
                                        }

                                        setupMap();
                                        setupFabMessage();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("LostItemDetailActivity", "Error fetching creator user: " + e.getMessage());
                                        tvUserName.setText("Bilinmeyen Kullanıcı");
                                        showSnackbar("Hata: Kullanıcı adı yüklenemedi! Hata: " + e.getMessage());

                                        setupMap();
                                        setupFabMessage();
                                    });
                        } else {
                            tvUserName.setText("Bilinmeyen Kullanıcı");
                            Log.w("LostItemDetailActivity", "CreatorId is null or empty for itemId: " + itemId);

                            setupMap();
                            setupFabMessage();
                        }
                    } else {
                        Log.e("LostItemDetailActivity", "Item not found for itemId: " + itemId);
                        showSnackbar("Hata: İlan bulunamadı!");
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("LostItemDetailActivity", "Error fetching item: " + e.getMessage());
                    showSnackbar("Hata: İlan yüklenemedi! Hata: " + e.getMessage());
                    finish();
                });
    }

    private void setupFabMessage() {
        FloatingActionButton fabMessage = findViewById(R.id.fabMessage);
        if (fabMessage != null) {
            fabMessage.setOnClickListener(v -> {
                Log.d("LostItemDetailActivity", "fabMessage clicked");
                if (itemOwnerEmail == null || itemOwnerEmail.isEmpty()) {
                    Log.e("LostItemDetailActivity", "Item owner email is null or empty");
                    showSnackbar("Hata: İlan sahibinin email'i bulunamadı!");
                    return;
                }
                if (itemOwnerEmail.equals(currentUserEmail)) {
                    Log.d("LostItemDetailActivity", "User cannot message themselves");
                    showSnackbar("Hata: Kendi ilanınıza mesaj gönderemezsiniz!");
                    return;
                }
                startOrJoinConversation();
            });
        } else {
            Log.e("LostItemDetailActivity", "fabMessage is null, check activity_lost_item_detail.xml");
            showSnackbar("Hata: Mesaj butonu bulunamadı!");
        }
    }

    private void startOrJoinConversation() {
        db.collection("conversations")
                .whereArrayContains("participants", currentUserId)
                .whereEqualTo("itemId", itemId)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Log.d("LostItemDetailActivity", "No existing conversation found, creating a new one");
                        createNewConversation();
                    } else {
                        String conversationId = null;
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            List<String> participants = (List<String>) document.get("participants");
                            if (participants != null && participants.contains(currentUserId)) {
                                conversationId = document.getId();
                                break;
                            }
                        }

                        if (conversationId != null) {
                            Log.d("LostItemDetailActivity", "Existing conversation found - conversationId: " + conversationId);
                            navigateToChatActivity(conversationId);
                        } else {
                            Log.d("LostItemDetailActivity", "No matching conversation found, creating a new one");
                            createNewConversation();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("LostItemDetailActivity", "Error checking conversation: " + e.getMessage());
                    if (e.getMessage().contains("PERMISSION_DENIED")) {
                        Log.d("LostItemDetailActivity", "Permission denied, likely due to empty collection, creating a new conversation");
                        createNewConversation();
                    } else {
                        showSnackbar("Hata: Konuşma kontrol edilirken bir hata oluştu! " + e.getMessage());
                    }
                });
    }

    private void createNewConversation() {
        db.collection("users")
                .whereEqualTo("email", itemOwnerEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String otherUserId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        HashMap<String, Object> conversationData = new HashMap<>();
                        List<String> participants;

                        if ("lost".equals(postedBy)) {
                            participants = Arrays.asList(currentUserId, otherUserId);
                            Log.d("LostItemDetailActivity", "Creating conversation for lost item - finderId: " + currentUserId + ", ownerId: " + otherUserId);
                            db.collection("lostItems")
                                    .document(itemId)
                                    .update("finderId", currentUserId)
                                    .addOnSuccessListener(aVoid -> Log.d("LostItemDetailActivity", "finderId updated in lostItems: " + currentUserId))
                                    .addOnFailureListener(e -> Log.e("LostItemDetailActivity", "Error updating finderId: " + e.getMessage()));
                        } else if ("found".equals(postedBy)) {
                            participants = Arrays.asList(otherUserId, currentUserId);
                            Log.d("LostItemDetailActivity", "Creating conversation for found item - finderId: " + otherUserId + ", ownerId: " + currentUserId);
                            db.collection("lostItems")
                                    .document(itemId)
                                    .update("ownerId", currentUserId)
                                    .addOnSuccessListener(aVoid -> Log.d("LostItemDetailActivity", "ownerId updated in lostItems: " + currentUserId))
                                    .addOnFailureListener(e -> Log.e("LostItemDetailActivity", "Error updating ownerId: " + e.getMessage()));
                        } else {
                            Log.e("LostItemDetailActivity", "Invalid postedBy value: " + postedBy);
                            showSnackbar("Hata: Geçersiz ilan türü!");
                            return;
                        }

                        conversationData.put("participants", participants);
                        conversationData.put("lastMessage", "");
                        conversationData.put("lastMessageTime", new Timestamp(new java.util.Date()));
                        conversationData.put("itemId", itemId);
                        conversationData.put("isActive", true);

                        db.collection("conversations")
                                .add(conversationData)
                                .addOnSuccessListener(documentReference -> {
                                    String conversationId = documentReference.getId();
                                    Log.d("LostItemDetailActivity", "New conversation created - conversationId: " + conversationId);
                                    navigateToChatActivity(conversationId);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("LostItemDetailActivity", "Error creating conversation: " + e.getMessage());
                                    showSnackbar("Hata: Konuşma oluşturulurken bir hata oluştu! " + e.getMessage());
                                });
                    } else {
                        Log.e("LostItemDetailActivity", "Could not find userId for itemOwnerEmail: " + itemOwnerEmail);
                        showSnackbar("Hata: İlan sahibinin kimliği bulunamadı!");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("LostItemDetailActivity", "Error fetching owner userId: " + e.getMessage());
                    showSnackbar("Hata: İlan sahibinin kimliği alınamadı!");
                });
    }

    private void navigateToChatActivity(String conversationId) {
        db.collection("users")
                .whereEqualTo("email", itemOwnerEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    String otherUserName = "Bilinmeyen Kullanıcı";
                    if (!queryDocumentSnapshots.isEmpty()) {
                        otherUserName = queryDocumentSnapshots.getDocuments().get(0).getString("username");
                    }
                    Intent intentToChat = new Intent(LostItemDetailActivity.this, ChatActivity.class);
                    intentToChat.putExtra("ITEM_ID", itemId);
                    intentToChat.putExtra("CONVERSATION_ID", conversationId);
                    intentToChat.putExtra("OTHER_USER_EMAIL", itemOwnerEmail);
                    intentToChat.putExtra("OTHER_USER_NAME", otherUserName != null ? otherUserName : "Bilinmeyen Kullanıcı");
                    intentToChat.putExtra("USER_ID", currentUserId);
                    intentToChat.putExtra("USER_NAME", userName);
                    intentToChat.putExtra("USER_EMAIL", currentUserEmail);
                    intentToChat.putExtra("CREATOR_ID", creatorId); // creatorId’yi ekliyoruz
                    Log.d("LostItemDetailActivity", "Navigating to ChatActivity - itemId: " + itemId +
                            ", currentUserEmail: " + currentUserEmail + ", otherUserEmail: " + itemOwnerEmail +
                            ", conversationId: " + conversationId + ", creatorId: " + creatorId);
                    try {
                        startActivity(intentToChat);
                    } catch (Exception e) {
                        Log.e("LostItemDetailActivity", "Failed to start ChatActivity: " + e.getMessage());
                        showSnackbar("Hata: Sohbet başlatılamadı! " + e.getMessage());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("LostItemDetailActivity", "Error fetching otherUserName: " + e.getMessage());
                    Intent intentToChat = new Intent(LostItemDetailActivity.this, ChatActivity.class);
                    intentToChat.putExtra("ITEM_ID", itemId);
                    intentToChat.putExtra("CONVERSATION_ID", conversationId);
                    intentToChat.putExtra("OTHER_USER_EMAIL", itemOwnerEmail);
                    intentToChat.putExtra("OTHER_USER_NAME", "Bilinmeyen Kullanıcı");
                    intentToChat.putExtra("USER_ID", currentUserId);
                    intentToChat.putExtra("USER_NAME", userName);
                    intentToChat.putExtra("USER_EMAIL", currentUserEmail);
                    intentToChat.putExtra("CREATOR_ID", creatorId); // creatorId’yi ekliyoruz
                    Log.d("LostItemDetailActivity", "Navigating to ChatActivity - itemId: " + itemId +
                            ", currentUserEmail: " + currentUserEmail + ", otherUserEmail: " + itemOwnerEmail +
                            ", conversationId: " + conversationId + ", creatorId: " + creatorId);
                    try {
                        startActivity(intentToChat);
                    } catch (Exception ex) {
                        Log.e("LostItemDetailActivity", "Failed to start ChatActivity: " + ex.getMessage());
                        showSnackbar("Hata: Sohbet başlatılamadı! " + ex.getMessage());
                    }
                });
    }

    private void setupMap() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        transaction.replace(R.id.mapContainer, mapFragment);
        transaction.commit();
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        if (address != null && !address.isEmpty()) {
            LatLng location = getLocationFromAddress(address);
            if (location != null) {
                googleMap.addMarker(new MarkerOptions()
                        .position(location)
                        .title("Lost Item Location"));
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
                tvMapError.setVisibility(View.GONE);
            } else {
                Log.w("LostItemDetailActivity", "Could not find location for address: " + address);
                tvMapError.setVisibility(View.VISIBLE);
            }
        } else {
            Log.w("LostItemDetailActivity", "Address is null or empty");
            tvMapError.setVisibility(View.VISIBLE);
        }
    }

    private LatLng getLocationFromAddress(String address) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(address, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address location = addresses.get(0);
                return new LatLng(location.getLatitude(), location.getLongitude());
            }
        } catch (IOException e) {
            Log.e("LostItemDetailActivity", "Geocoder error: " + e.getMessage());
        }
        return null;
    }

    private void showSnackbar(@NonNull String message) {
        View view = findViewById(android.R.id.content);
        if (view != null) {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        } else {
            Log.e("LostItemDetailActivity", "Cannot show Snackbar: View is null. Message: " + message);
        }
    }

    @Override
    protected int getContentLayoutId() {
        return R.layout.activity_lost_item_detail;
    }

    @Override
    protected int getSelectedNavigationItem() {
        return R.id.menu_lost_items;
    }

    @Override
    protected boolean shouldShowBottomNavigation() {
        return true;
    }

    @Override
    protected String getToolbarTitle() {
        return "Item Detail";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}