package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class ProfileActivity extends BaseActivity {

    private static final String TAG = "ProfileActivity";
    private TextView tvEmailValue, tvActiveAdsValue, tvUsernameValue, tvFoundItemsValue, tvDeliveredItemsValue;
    private String currentUserEmail;
    private String currentUserId; // String türünde

    private FirebaseFirestore db; // Firestore instance'ı

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kullanıcı bilgilerini BaseActivity'den al
        currentUserEmail = getUserEmail();
        currentUserId = getUserId(); // String türünde

        // Kullanıcı bilgileri eksikse hata göster ve çık
        if (currentUserEmail == null || currentUserId == null || currentUserId.isEmpty()) {
            Log.e(TAG, "Missing user data - Email: " + currentUserEmail + ", UserId: " + currentUserId);
            Toast.makeText(this, "Error: User data not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Firestore'u başlat
        db = FirebaseFirestore.getInstance();

        // UI bileşenlerini bağla
        initComponents();

        // Email'i göster
        tvEmailValue.setText(currentUserEmail);
        Log.d(TAG, "Displaying email: " + currentUserEmail);

        // Verileri yükle
        loadUsername();
        loadActiveAdsCount();
        loadFoundAndDeliveredCounts();
    }

    private void loadFoundAndDeliveredCounts() {
        if (currentUserId == null || currentUserId.isEmpty()) {
            Log.e(TAG, "Invalid userId, cannot load found and delivered counts");
            tvFoundItemsValue.setText("0");
            tvDeliveredItemsValue.setText("0");
            return;
        }

        db.collection("users").document(currentUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Long foundItemsCount = documentSnapshot.getLong("foundItemsCount");
                        Long deliveredItemsCount = documentSnapshot.getLong("deliveredItemsCount");

                        tvFoundItemsValue.setText(foundItemsCount != null ? foundItemsCount.toString() : "0");
                        tvDeliveredItemsValue.setText(deliveredItemsCount != null ? deliveredItemsCount.toString() : "0");
                        Log.d(TAG, "Found Items: " + foundItemsCount + ", Delivered Items: " + deliveredItemsCount);
                    } else {
                        Log.w(TAG, "No user document found for userId: " + currentUserId);
                        tvFoundItemsValue.setText("0");
                        tvDeliveredItemsValue.setText("0");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting found and delivered counts", e);
                    tvFoundItemsValue.setText("0");
                    tvDeliveredItemsValue.setText("0");
                });
    }

    private void loadActiveAdsCount() {
        if (currentUserId == null || currentUserId.isEmpty()) {
            Log.e(TAG, "Invalid userId, cannot load active ads count");
            tvActiveAdsValue.setText("0");
            return;
        }
        databaseHelper.getActiveAdsCount(currentUserId, count -> {
            tvActiveAdsValue.setText(String.valueOf(count));
            Log.d(TAG, "Active ads count loaded: " + count);
        });
    }

    private void loadUsername() {
        if (currentUserId == null || currentUserId.isEmpty()) {
            Log.e(TAG, "Invalid userId, cannot load username");
            return;
        }

        databaseHelper.getUserNameById(currentUserId, username -> {
            if (username != null && !username.isEmpty()) {
                tvUsernameValue.setText(username);
                Log.d(TAG, "Username loaded: " + username);
            } else {
                tvUsernameValue.setText("Bilinmeyen Kullanıcı");
                Log.w(TAG, "Username not found for userId: " + currentUserId);
            }
        });
    }

    private void initComponents() {
        tvEmailValue = findViewById(R.id.tvEmailValue);
        tvActiveAdsValue = findViewById(R.id.tvActiveAdsValue);
        tvUsernameValue = findViewById(R.id.tvUsernameValue);
        tvFoundItemsValue = findViewById(R.id.tvFoundItemsValue); // Yeni eklenen
        tvDeliveredItemsValue = findViewById(R.id.tvDeliveredItemsValue); // Yeni eklenen
    }

    @Override
    protected int getContentLayoutId() {
        return R.layout.activity_profile;
    }

    @Override
    protected int getSelectedNavigationItem() {
        return R.id.menu_profile;
    }
}