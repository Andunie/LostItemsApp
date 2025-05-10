package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.model.LostItem;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LostItemsActivity extends BaseActivity {

    private FirebaseFirestore db;
    private RecyclerView rvLostItems;
    private LostItemAdapter adapter;
    private List<LostItem> lostItemsList;
    private List<LostItem> filteredItemsList;
    private ActionMode actionMode;
    private TextView tvEmptyList;
    private EditText etSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Firestore'u başlat
        db = FirebaseFirestore.getInstance();

        // Kullanıcı bilgilerini kontrol et
        String userId = getUserId();
        if (userId == null || userId.isEmpty() || getUserEmail() == null) {
            Log.e("LostItemsActivity", "Invalid userId or userEmail, redirecting to LoginActivity");
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
                filterItems(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Bir şey yapmaya gerek yok
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                filterItems(etSearch.getText().toString());
                return true;
            }
            return false;
        });

        // RecyclerView'ı başlat
        rvLostItems = findViewById(R.id.rvLostItems);
        tvEmptyList = findViewById(R.id.tvEmptyList);
        lostItemsList = new ArrayList<>();
        filteredItemsList = new ArrayList<>();
        adapter = new LostItemAdapter(filteredItemsList, item -> {
            if (!adapter.isSelectionMode()) {
                Intent intent = new Intent(LostItemsActivity.this, LostItemDetailActivity.class);
                intent.putExtra("itemId", item.getId());
                intent.putExtra("currentUserEmail", getUserEmail());
                intent.putExtra("USER_ID", getUserId());
                intent.putExtra("USER_NAME", getUserName());
                intent.putExtra("postedBy", item.getPostedBy());
                intent.putExtra("creatorId", item.getCreatorId());
                intent.putExtra("ownerId", item.getOwnerId());
                intent.putExtra("finderId", item.getFinderId());
                intent.putExtra("isDelivered", item.isDelivered());
                Log.d("LostItemsActivity", "Navigating to LostItemDetailActivity - itemId: " + item.getId());
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("LostItemsActivity", "Error starting LostItemDetailActivity: " + e.getMessage());
                    showSnackbar("Hata: Detay ekranı açılamadı!");
                }
            }
        });
        rvLostItems.setLayoutManager(new GridLayoutManager(this, 2)); // 2 sütun
        rvLostItems.setAdapter(adapter);

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

        // Kayıp eşyaları yükle
        loadLostItems();
    }

    private void filterItems(String searchText) {
        filteredItemsList.clear();

        if (searchText.isEmpty()) {
            // Arama metni boşsa, tüm listeyi göster
            filteredItemsList.addAll(lostItemsList);
        } else {
            // Arama metni varsa, filtreleme yap
            String searchLower = searchText.toLowerCase(Locale.getDefault());
            for (LostItem item : lostItemsList) {
                if (item.getTitle() != null && item.getTitle().toLowerCase(Locale.getDefault()).contains(searchLower)) {
                    filteredItemsList.add(item);
                }
            }
        }

        adapter.notifyDataSetChanged();
        tvEmptyList.setVisibility(filteredItemsList.isEmpty() ? View.VISIBLE : View.GONE);
        Log.d("LostItemsActivity", "Filtered items: " + filteredItemsList.size() + " with search: " + searchText);
    }

    private void loadLostItems() {
        lostItemsList.clear();
        filteredItemsList.clear();
        String userId = getUserId();
        if (userId == null || userId.isEmpty()) {
            Log.e("LostItemsActivity", "Invalid userId, cannot load items");
            showSnackbar("Hata: Kullanıcı ID'si bulunamadı!");
            return;
        }

        // Önce tüm liste temizlensin ve boş olduğunu gösterelim
        lostItemsList.clear();
        filteredItemsList.clear();
        adapter.notifyDataSetChanged();
        tvEmptyList.setVisibility(View.VISIBLE);
        
        try {
            // Firestore'dan diğer kullanıcıların ilanlarını çek (kendi ilanlarım hariç, isDelivered=false olanlar)
            db.collection("lostItems")
                    .whereEqualTo("isDelivered", false)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (queryDocumentSnapshots.isEmpty()) {
                            Log.d("LostItemsActivity", "No items found in Firestore");
                            tvEmptyList.setVisibility(View.VISIBLE);
                            return;
                        }
                        
                        lostItemsList.clear();
                        filteredItemsList.clear();
                        
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            // Kullanıcının kendi ilanlarını filtrele
                            String creatorId = doc.getString("creatorId");
                            if (creatorId != null && creatorId.equals(userId)) {
                                continue; // Kullanıcının kendi ilanını atla
                            }
                            
                            LostItem item = new LostItem(
                                    doc.getId(),
                                    doc.getString("userId"),
                                    doc.getString("itemTitle"),
                                    doc.getString("description"),
                                    doc.getString("address")
                            );
                            item.setPostedBy(doc.getString("postedBy"));
                            item.setCreatorId(creatorId);
                            item.setOwnerId(doc.getString("ownerId"));
                            item.setFinderId(doc.getString("finderId"));
                            item.setDelivered(doc.getBoolean("isDelivered") != null ? doc.getBoolean("isDelivered") : false);
                            lostItemsList.add(item);
                            Log.d("LostItemsActivity", "Item: " + item.getTitle() + ", Address: " + item.getAddress() + ", CreatorId: " + item.getCreatorId());
                        }

                        // Mevcut arama filtresini uygula
                        filterItems(etSearch.getText().toString());

                        Log.d("LostItemsActivity", "Number of all items: " + lostItemsList.size());
                        tvEmptyList.setVisibility(filteredItemsList.isEmpty() ? View.VISIBLE : View.GONE);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("LostItemsActivity", "Error loading items: " + e.getMessage(), e);
                        // Hata durumunda kullanıcıya bilgi ver
                        showSnackbar("İlanlar yüklenirken bir sorun oluştu. Lütfen internet bağlantınızı kontrol edin.");
                    });
        } catch (Exception e) {
            Log.e("LostItemsActivity", "Unexpected error in loadLostItems: " + e.getMessage(), e);
            showSnackbar("Beklenmeyen bir hata oluştu. Lütfen daha sonra tekrar deneyin.");
        }
    }

    private void showSnackbar(String message) {
        View rootView = findViewById(R.id.rootLayout);
        if (rootView != null) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
        } else {
            Log.e("LostItemsActivity", "Root view is null, cannot show Snackbar");
            try {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("LostItemsActivity", "Error showing Toast: " + e.getMessage());
            }
        }
    }

    private void startActionMode() {
        actionMode = startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_action_mode, menu);
                mode.setTitle("Seçili: " + adapter.getSelectedItems().size());
                Log.d("LostItemsActivity", "ActionMode created");
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
                    Log.d("LostItemsActivity", "Deleting items with IDs: " + selectedIds);
                    if (!selectedIds.isEmpty()) {
                        String currentUserId = getUserId();
                        List<String> authorizedItems = new ArrayList<>();

                        // Yetki kontrolü: Sadece creatorId'si currentUserId ile eşleşen ilanlar silinebilir
                        for (String itemId : selectedIds) {
                            for (LostItem lostItem : filteredItemsList) {
                                if (lostItem.getId().equals(itemId) && lostItem.getCreatorId().equals(currentUserId)) {
                                    authorizedItems.add(itemId);
                                    break;
                                }
                            }
                        }

                        if (authorizedItems.isEmpty()) {
                            showSnackbar("Silmek için yetkiniz olmayan ilanlar seçildi!");
                            mode.finish();
                            return true;
                        }

                        // Yetkili ilanları sil
                        for (String itemId : authorizedItems) {
                            db.collection("lostItems").document(itemId)
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d("LostItemsActivity", "Item deleted: " + itemId);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("LostItemsActivity", "Error deleting item: " + e.getMessage());
                                        showSnackbar("İlan silinirken bir hata oluştu!");
                                    });
                        }
                        loadLostItems(); // Listeyi yenile
                        showSnackbar("Seçili ilanlar silindi.");
                    } else {
                        Log.w("LostItemsActivity", "No items selected for deletion");
                        showSnackbar("Silmek için ilan seçilmedi.");
                    }
                    mode.finish();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                Log.d("LostItemsActivity", "ActionMode destroyed");
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
        return R.layout.activity_lost_items;
    }

    @Override
    protected int getSelectedNavigationItem() {
        return R.id.menu_lost_items;
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