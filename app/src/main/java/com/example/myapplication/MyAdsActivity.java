package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.model.LostItem;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import android.util.Log;
import android.widget.TextView;

public class MyAdsActivity extends BaseActivity {

    private FirebaseFirestore db;
    private RecyclerView rvMyAds;
    private LostItemAdapter adapter;
    private List<LostItem> myAdsList;
    private List<LostItem> filteredAdsList;
    private ActionMode actionMode;
    private EditText etSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Firestore'u başlat
        db = FirebaseFirestore.getInstance();

        // Kullanıcı bilgilerini kontrol et
        String userId = getUserId();
        String userEmail = getUserEmail();
        if (userId == null || userId.isEmpty() || userEmail == null) {
            Log.e("MyAdsActivity", "Invalid userId or userEmail, redirecting to LoginActivity");
            showSnackbar("Hata: Kullanıcı bilgileri eksik!");
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // Arama çubuğunu ayarla
        etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Bir şey yapmaya gerek yok
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAds(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Bir şey yapmaya gerek yok
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                filterAds(etSearch.getText().toString());
                return true;
            }
            return false;
        });

        // RecyclerView'ı başlat
        rvMyAds = findViewById(R.id.rvMyAds);
        myAdsList = new ArrayList<>();
        filteredAdsList = new ArrayList<>();
        adapter = new LostItemAdapter(filteredAdsList, item -> {
            if (!adapter.isSelectionMode()) {
                Intent intent = new Intent(MyAdsActivity.this, LostItemDetailActivity.class);
                intent.putExtra("itemId", item.getId());
                intent.putExtra("currentUserEmail", getUserEmail());
                intent.putExtra("USER_ID", getUserId());
                intent.putExtra("USER_NAME", getUserName());
                intent.putExtra("USER_EMAIL", getUserEmail());
                intent.putExtra("postedBy", item.getPostedBy());
                intent.putExtra("creatorId", item.getCreatorId());
                intent.putExtra("ownerId", item.getOwnerId());
                intent.putExtra("finderId", item.getFinderId());
                intent.putExtra("isDelivered", item.isDelivered());
                Log.d("MyAdsActivity", "Navigating to LostItemDetailActivity - itemId: " + item.getId());
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("MyAdsActivity", "Error starting LostItemDetailActivity: " + e.getMessage());
                    showSnackbar("Hata: Detay ekranı açılamadı!");
                }
            }
        });
        rvMyAds.setLayoutManager(new LinearLayoutManager(this));
        rvMyAds.setAdapter(adapter);

        // Uzun basma için ActionMode başlat
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                if (adapter.isSelectionMode() && actionMode == null) {
                    startActionMode();
                } else if (!adapter.isSelectionMode() && actionMode != null) {
                    actionMode.finish();
                }
                updateActionModeTitle();
            }
        });

        // Kendi ilanlarını yükle
        loadMyAds();

        // FAB'ı bağla
        FloatingActionButton fabMessage = findViewById(R.id.fabMessage);
        if (fabMessage != null) {
            fabMessage.setOnClickListener(v -> {
                String currentUserEmail = getUserEmail();
                if (currentUserEmail == null) {
                    Log.e("MyAdsActivity", "currentUserEmail is null, cannot proceed to ConversationsActivity");
                    showSnackbar("Hata: Kullanıcı email'i bulunamadı!");
                    return;
                }
                Intent intent = new Intent(MyAdsActivity.this, ConversationsActivity.class);
                intent.putExtra("itemId", "-1");
                intent.putExtra("currentUserEmail", currentUserEmail);
                intent.putExtra("itemOwnerEmail", currentUserEmail);
                intent.putExtra("USER_ID", getUserId());
                intent.putExtra("USER_NAME", getUserName());
                intent.putExtra("USER_EMAIL", currentUserEmail);
                Log.d("MyAdsActivity", "Navigating to ConversationsActivity - itemId: -1, currentUserEmail: " + currentUserEmail +
                        ", itemOwnerEmail: " + currentUserEmail);
                startActivity(intent);
            });
        } else {
            Log.e("MyAdsActivity", "fabMessage is null, check activity_my_ads.xml");
        }
    }

    private void filterAds(String searchText) {
        filteredAdsList.clear();

        if (searchText.isEmpty()) {
            // Arama metni boşsa, tüm listeyi göster
            filteredAdsList.addAll(myAdsList);
        } else {
            // Arama metni varsa, filtreleme yap
            String searchLower = searchText.toLowerCase(Locale.getDefault());
            for (LostItem item : myAdsList) {
                if (item.getTitle() != null && item.getTitle().toLowerCase(Locale.getDefault()).contains(searchLower)) {
                    filteredAdsList.add(item);
                }
            }
        }

        adapter.notifyDataSetChanged();
        TextView tvEmptyMyAds = findViewById(R.id.tvEmptyMyAds);
        if (tvEmptyMyAds != null) {
            tvEmptyMyAds.setVisibility(filteredAdsList.isEmpty() ? View.VISIBLE : View.GONE);
        }
        Log.d("MyAdsActivity", "Filtered ads: " + filteredAdsList.size() + " with search: " + searchText);
    }

    private void loadMyAds() {
        myAdsList.clear();
        filteredAdsList.clear();
        String userId = getUserId();
        if (userId == null || userId.isEmpty()) {
            Log.e("MyAdsActivity", "Invalid userId, cannot load ads");
            showSnackbar("Hata: Kullanıcı ID'si bulunamadı!");
            return;
        }
        db.collection("lostItems")
                .whereEqualTo("creatorId", userId)
                .whereEqualTo("isDelivered", false)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("MyAdsActivity", "Listen failed: " + error.getMessage());
                        showSnackbar("Hata: İlanlar yüklenemedi!");
                        return;
                    }

                    myAdsList.clear();
                    filteredAdsList.clear();
                    for (QueryDocumentSnapshot doc : value) {
                        LostItem item = new LostItem(
                                doc.getId(),
                                doc.getString("userId"),
                                doc.getString("itemTitle"),
                                doc.getString("description"),
                                doc.getString("address")
                        );
                        item.setPostedBy(doc.getString("postedBy"));
                        item.setCreatorId(doc.getString("creatorId"));
                        item.setOwnerId(doc.getString("ownerId"));
                        item.setFinderId(doc.getString("finderId"));
                        item.setDelivered(doc.getBoolean("isDelivered") != null ? doc.getBoolean("isDelivered") : false);
                        myAdsList.add(item);
                        Log.d("MyAdsActivity", "Ad: " + item.getTitle() + ", Address: " + item.getAddress());
                    }

                    // Mevcut arama filtresini uygula
                    filterAds(etSearch.getText().toString());

                    Log.d("MyAdsActivity", "Number of all ads: " + myAdsList.size());
                });
    }

    private void showSnackbar(String message) {
        View rootView = findViewById(R.id.rootLayout);
        if (rootView != null) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
        } else {
            Log.e("MyAdsActivity", "Root view is null, cannot show Snackbar");
            try {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("MyAdsActivity", "Error showing Toast: " + e.getMessage());
            }
        }
    }

    private void startActionMode() {
        actionMode = startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_action_mode, menu);
                mode.setTitle("Seçili: " + adapter.getSelectedItems().size());
                Log.d("MyAdsActivity", "ActionMode created");
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == R.id.action_delete) {
                    Set<String> selectedIds = adapter.getSelectedItems();
                    Log.d("MyAdsActivity", "Deleting items with IDs: " + selectedIds);
                    if (!selectedIds.isEmpty()) {
                        for (String itemId : selectedIds) {
                            db.collection("lostItems").document(itemId)
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d("MyAdsActivity", "Item deleted: " + itemId);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("MyAdsActivity", "Error deleting item: " + e.getMessage());
                                        showSnackbar("İlan silinirken bir hata oluştu!");
                                    });
                        }
                        loadMyAds(); // Listeyi yenile
                        showSnackbar("Seçili ilanlar silindi.");
                    } else {
                        Log.w("MyAdsActivity", "No items selected for deletion");
                        showSnackbar("Silmek için ilan seçilmedi.");
                    }
                    mode.finish();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                Log.d("MyAdsActivity", "ActionMode destroyed");
                adapter.clearSelection();
                actionMode = null;
            }
        });
        updateActionModeTitle();
    }

    private void updateActionModeTitle() {
        if (actionMode != null) {
            actionMode.setTitle("Seçili: " + adapter.getSelectedItems().size());
        }
    }

    @Override
    protected int getContentLayoutId() {
        return R.layout.activity_my_ads;
    }

    @Override
    protected int getSelectedNavigationItem() {
        return R.id.menu_my_ads;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (adapter.isSelectionMode()) {
            adapter.clearSelection();
            if (actionMode != null) {
                actionMode.finish();
            }
        } else {
            super.onBackPressed();
        }
    }
}