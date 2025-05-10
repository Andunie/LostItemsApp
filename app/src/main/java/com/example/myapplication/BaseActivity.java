package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.myapplication.data.DatabaseHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.example.myapplication.util.LocaleHelper;

public abstract class BaseActivity extends AppCompatActivity {

    protected BottomNavigationView bottomNavigationView;
    protected Toolbar toolbar;
    protected DatabaseHelper databaseHelper;
    protected String userId; // int yerine String
    protected String userName;
    protected String userEmail;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "UserSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_NAME = "username";
    private static final String KEY_USER_EMAIL = "userEmail";

    @Override
    protected void attachBaseContext(Context newBase) {
        // Her aktivite oluşturulduğunda dil ayarlarını kontrol edip güncelliyoruz
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);
        
        // Klavye davranışını düzenleme - bottom navigation'ın klavye açıldığında sabit kalmasını sağlar
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        // SharedPreferences'ı başlat
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        databaseHelper = new DatabaseHelper(this);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(getToolbarTitle());
        }

        // Çıkış butonunu bağla (LoginActivity hariç)
        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        try {
            // LoginActivity kontrolü
            boolean isLoginActivity = getClass().getName().equals("com.example.myapplication.LoginActivity");
            if (!isLoginActivity && btnLogout != null) {
                btnLogout.setVisibility(View.VISIBLE);
                btnLogout.setOnClickListener(v -> logout());
            } else if (btnLogout != null) {
                btnLogout.setVisibility(View.GONE); // LoginActivity'de gizle
            }
        } catch (Exception e) {
            Log.e("BaseActivity", "Error checking LoginActivity: " + e.getMessage());
            if (btnLogout != null) {
                btnLogout.setVisibility(View.VISIBLE); // Güvenli varsayılan: göster
                btnLogout.setOnClickListener(v -> logout());
            }
        }

        Intent intent = getIntent();
        if (intent != null) {
            userId = intent.getStringExtra("USER_ID"); // String olarak al
            userName = intent.getStringExtra("USER_NAME");
            userEmail = intent.getStringExtra("USER_EMAIL");
            Log.d("BaseActivity", "onCreate - Intent received - UserId: " + (userId != null ? userId : "null") +
                    ", UserName: " + (userName != null ? userName : "null") +
                    ", UserEmail: " + (userEmail != null ? userEmail : "null"));
        } else {
            Log.w("BaseActivity", "Intent is null in onCreate");
        }

        if (savedInstanceState != null) {
            userId = savedInstanceState.getString("USER_ID", userId);
            userName = savedInstanceState.getString("USER_NAME", userName);
            userEmail = savedInstanceState.getString("USER_EMAIL", userEmail);
            Log.d("BaseActivity", "onRestoreInstanceState - Restored - UserId: " + (userId != null ? userId : "null") +
                    ", UserName: " + (userName != null ? userName : "null") +
                    ", UserEmail: " + (userEmail != null ? userEmail : "null"));
        }

        FrameLayout contentContainer = findViewById(R.id.frame_container);
        LayoutInflater inflater = getLayoutInflater();
        inflater.inflate(getContentLayoutId(), contentContainer, true);

        bottomNavigationView = findViewById(R.id.bottom_navigation_menu);
        if (bottomNavigationView != null) {
            if (shouldShowBottomNavigation()) {
                bottomNavigationView.setVisibility(View.VISIBLE);
                bottomNavigationView.setSelectedItemId(getSelectedNavigationItem());
                bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
                    int itemId = item.getItemId();
                    Log.d("BaseActivity", "Selected item ID: " + itemId);
                    
                    // Tıklanan menü zaten aktifse işlem yapma
                    if (itemId == getSelectedNavigationItem()) {
                        return true;
                    }
                    
                    if (itemId == R.id.menu_lost_items && !(this instanceof LostItemsActivity)) {
                        Log.d("BaseActivity", "Navigating to LostItemsActivity");
                        startActivityWithUserData(LostItemsActivity.class);
                        overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
                        finish();
                        return true;
                    } else if (itemId == R.id.menu_create_ad && !(this instanceof CreateAdActivity)) {
                        Log.d("BaseActivity", "Navigating to CreateAdActivity");
                        startActivityWithUserData(CreateAdActivity.class);
                        overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
                        finish();
                        return true;
                    } else if (itemId == R.id.menu_my_ads && !(this instanceof MyAdsActivity)) {
                        Log.d("BaseActivity", "Navigating to MyAdsActivity");
                        startActivityWithUserData(MyAdsActivity.class);
                        overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
                        finish();
                        return true;
                    } else if (itemId == R.id.menu_profile && !(this instanceof ProfileActivity)) {
                        Log.d("BaseActivity", "Navigating to ProfileActivity");
                        startActivityWithUserData(ProfileActivity.class);
                        overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
                        finish();
                        return true;
                    } else if (itemId == R.id.menu_messages && !(this instanceof ConversationsActivity)) {
                        Log.d("BaseActivity", "Navigating to ConversationsActivity");
                        startActivityWithUserData(ConversationsActivity.class);
                        overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
                        finish();
                        return true;
                    }
                    Log.d("BaseActivity", "No navigation action for item: " + itemId);
                    return false;
                });
            } else {
                bottomNavigationView.setVisibility(View.GONE);
            }
        }
    }

    // Çıkış işlemi
    private void logout() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        Log.d("BaseActivity", "Logout successful, redirecting to LoginActivity");

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startActivityWithUserData(Class<?> clazz) {
        Intent intent = new Intent(this, clazz);
        intent.putExtra("USER_ID", userId);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("USER_EMAIL", userEmail);
        startActivity(intent);
        
        // Alt menü geçişi değilse standart animasyonu kullan (styles.xml'deki tanım genel kullanılacak)
        if (!(bottomNavigationView != null && bottomNavigationView.getVisibility() == View.VISIBLE)) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    @Override
    public void onBackPressed() {
        Log.d("BaseActivity", "onBackPressed called in: " + this.getClass().getSimpleName());
        if (this instanceof ChatActivity) {
            Intent intent = new Intent(this, ConversationsActivity.class);
            intent.putExtra("USER_ID", userId);
            intent.putExtra("USER_NAME", userName);
            intent.putExtra("USER_EMAIL", userEmail);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            finish();
        } else if (shouldNavigateToMenuActivity()) {
            Log.d("BaseActivity", "Navigating to LostItemsActivity from: " + this.getClass().getSimpleName());
            Intent intent = new Intent(this, LostItemsActivity.class);
            intent.putExtra("USER_ID", userId);
            intent.putExtra("USER_NAME", userName);
            intent.putExtra("USER_EMAIL", userEmail);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            finish();
        } else {
            Log.d("BaseActivity", "Calling super.onBackPressed in: " + this.getClass().getSimpleName());
            super.onBackPressed();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }
    }

    protected boolean shouldNavigateToMenuActivity() {
        boolean shouldNavigate = !(this instanceof LostItemsActivity);
        Log.d("BaseActivity", "shouldNavigateToMenuActivity: " + shouldNavigate + " (Current activity: " + this.getClass().getSimpleName() + ")");
        return shouldNavigate;
    }

    protected boolean shouldShowBottomNavigation() {
        return true;
    }

    protected String getToolbarTitle() {
        if (this instanceof LostItemsActivity) {
            return getString(R.string.toolbar_lost_items);
        } else if (this instanceof LostItemDetailActivity) {
            return getString(R.string.toolbar_item_detail);
        } else if (this instanceof CreateAdActivity) {
            return getString(R.string.toolbar_create_ad);
        } else if (this instanceof MyAdsActivity) {
            return getString(R.string.toolbar_my_ads);
        } else if (this instanceof ProfileActivity) {
            return getString(R.string.toolbar_profile);
        } else if (this instanceof ConversationsActivity) {
            return getString(R.string.toolbar_messages);
        }
        return getString(R.string.toolbar_my_app);
    }

    protected String getUserId() {
        if (userId == null) {
            Intent intent = getIntent();
            if (intent != null) {
                userId = intent.getStringExtra("USER_ID");
            }
            Log.w("BaseActivity", "getUserId was null, resolved to: " + (userId != null ? userId : "still null"));
        }
        return userId;
    }

    protected String getUserName() {
        return userName;
    }

    protected String getUserEmail() {
        if (userEmail == null) {
            Intent intent = getIntent();
            if (intent != null) {
                userEmail = intent.getStringExtra("USER_EMAIL");
                if (userEmail == null) {
                    userEmail = intent.getStringExtra("CURRENT_USER_EMAIL");
                }
            }
            Log.w("BaseActivity", "getUserEmail was null, resolved to: " + (userEmail != null ? userEmail : "still null"));
        }
        return userEmail;
    }

    protected abstract int getContentLayoutId();
    protected abstract int getSelectedNavigationItem();

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("USER_ID", userId);
        outState.putString("USER_NAME", userName);
        outState.putString("USER_EMAIL", userEmail);
        Log.d("BaseActivity", "onSaveInstanceState - Saving UserId: " + userId);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        userId = savedInstanceState.getString("USER_ID", userId);
        userName = savedInstanceState.getString("USER_NAME", userName);
        userEmail = savedInstanceState.getString("USER_EMAIL", userEmail);
        Log.d("BaseActivity", "onRestoreInstanceState - Restored UserId: " + userId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
}