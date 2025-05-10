package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.data.DatabaseHelper;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText edtEmail, edtPassword;
    private TextInputLayout emailLayout, passwordLayout;
    private Button btnLogin;
    private DatabaseHelper dbHelper;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "UserSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_NAME = "username";
    private static final String KEY_USER_EMAIL = "userEmail";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Firebase Authentication ve Firestore'u başlat
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // SharedPreferences'ı başlat
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Kullanıcı zaten giriş yapmışsa LostItemsActivity'ye yönlendir
        if (isLoggedIn()) {
            Intent intent = new Intent(LoginActivity.this, LostItemsActivity.class);
            intent.putExtra("USER_ID", sharedPreferences.getString(KEY_USER_ID, null));
            intent.putExtra("USER_NAME", sharedPreferences.getString(KEY_USER_NAME, ""));
            intent.putExtra("USER_EMAIL", sharedPreferences.getString(KEY_USER_EMAIL, ""));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // TextInputLayout ve TextInputEditText’leri bağla
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);

        dbHelper = new DatabaseHelper(this);

        btnLogin.setOnClickListener(v -> loginUser());
    }

    private void loginUser() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        // Form doğrulama
        if (!validateForm(email, password)) {
            return;
        }

        // Firebase Authentication ile giriş yap
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Email doğrulama kontrolü
                            if (!user.isEmailVerified()) {
                                Log.w("LoginActivity", "Email doğrulanmamış: " + email);
                                Toast.makeText(this, "Lütfen email adresinizi doğrulayın! Doğrulama linki email adresinize gönderildi.", Toast.LENGTH_LONG).show();
                                mAuth.signOut(); // Kullanıcıyı çıkış yaptır
                                return;
                            }

                            // Firestore'dan kullanıcı bilgilerini al
                            String userId = user.getUid();
                            db.collection("users").document(userId)
                                    .get()
                                    .addOnSuccessListener(documentSnapshot -> {
                                        if (documentSnapshot.exists()) {
                                            String username = documentSnapshot.getString("username");
                                            String userEmail = documentSnapshot.getString("email");

                                            if (username == null || userEmail == null) {
                                                Log.e("LoginActivity", "Firestore'da kullanıcı bilgileri eksik: username veya email null");
                                                Toast.makeText(this, "Hata: Kullanıcı bilgileri eksik!", Toast.LENGTH_LONG).show();
                                                mAuth.signOut();
                                                return;
                                            }

                                            // Firestore'da emailVerified alanını güncelle
                                            db.collection("users").document(userId)
                                                    .update("emailVerified", true)
                                                    .addOnSuccessListener(aVoid -> Log.d("LoginActivity", "Firestore'da emailVerified güncellendi: true"))
                                                    .addOnFailureListener(e -> Log.w("LoginActivity", "Firestore'da emailVerified güncelleme hatası: " + e.getMessage()));

                                            // SQLite'a kullanıcıyı ekle (mevcut yapıyı bozmamak için)
                                            boolean isInserted = dbHelper.kullaniciEkle(username, userEmail, password);
                                            if (!isInserted) {
                                                Log.w("LoginActivity", "SQLite'a kullanıcı eklenemedi, ancak işlem devam ediyor");
                                            }

                                            // Oturum bilgilerini kaydet
                                            saveUserSession(userId, username, userEmail);

                                            Toast.makeText(this, "Giriş başarılı!", Toast.LENGTH_SHORT).show();

                                            // LostItemsActivity’ye kullanıcı bilgilerini aktar
                                            Intent intent = new Intent(LoginActivity.this, LostItemsActivity.class);
                                            intent.putExtra("USER_ID", userId);
                                            intent.putExtra("USER_NAME", username);
                                            intent.putExtra("USER_EMAIL", userEmail);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                            startActivity(intent);
                                            finish();
                                        } else {
                                            Log.e("LoginActivity", "Firestore'da kullanıcı belgesi bulunamadı: userId=" + userId);
                                            Toast.makeText(this, "Hata: Kullanıcı bilgileri Firestore'da bulunamadı! Lütfen tekrar kayıt olun.", Toast.LENGTH_LONG).show();
                                            mAuth.signOut();
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("LoginActivity", "Firestore'dan veri alma hatası: " + e.getMessage());
                                        if (e.getMessage().contains("PERMISSION_DENIED")) {
                                            Toast.makeText(this, "Erişim yetkisi hatası: Firestore güvenlik kurallarını kontrol edin!", Toast.LENGTH_LONG).show();
                                        } else {
                                            Toast.makeText(this, "Firestore'dan veri alma hatası: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                        mAuth.signOut();
                                    });
                        } else {
                            Log.w("LoginActivity", "Kullanıcı null");
                            Toast.makeText(this, "Hata: Kullanıcı bulunamadı!", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.w("LoginActivity", "Giriş başarısız: " + task.getException().getMessage());
                        Toast.makeText(this, "Hatalı e-posta veya şifre: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        passwordLayout.setError("E-posta veya şifre yanlış");
                    }
                });
    }

    // Form doğrulama metodu
    private boolean validateForm(String email, String password) {
        boolean isValid = true;

        // E-posta kontrolü
        if (email.isEmpty()) {
            emailLayout.setError("E-posta alanı boş olamaz!");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError("Geçerli bir e-posta adresi girin!");
            isValid = false;
        } else {
            emailLayout.setError(null);
        }

        // Şifre kontrolü
        if (password.isEmpty()) {
            passwordLayout.setError("Şifre alanı boş olamaz!");
            isValid = false;
        } else if (password.length() < 6) {
            passwordLayout.setError("Şifre en az 6 karakter olmalı!");
            isValid = false;
        } else {
            passwordLayout.setError(null);
        }

        return isValid;
    }

    // Oturum bilgilerini kaydet
    private void saveUserSession(String userId, String username, String userEmail) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_NAME, username);
        editor.putString(KEY_USER_EMAIL, userEmail);
        editor.apply();
    }

    // Kullanıcının giriş yapıp yapmadığını kontrol et
    private boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }
}