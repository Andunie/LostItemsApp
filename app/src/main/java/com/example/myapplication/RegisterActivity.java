package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.data.DatabaseHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";
    private TextInputEditText edtUsername, edtEmail, edtPassword;
    private MaterialButton btnRegister;
    private DatabaseHelper databaseHelper;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Firebase Authentication ve Firestore'u başlat
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // UI Elemanlarını bağla
        edtUsername = findViewById(R.id.edtUsername);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnRegister = findViewById(R.id.btnRegister);

        // Veritabanı yardımcı sınıfını başlat
        databaseHelper = new DatabaseHelper(this);

        // Kayıt butonu tıklama olayı
        btnRegister.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String username = edtUsername.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        // Validation (Doğrulama)
        if (!validateInputs(username, email, password)) {
            return;
        }

        // Firebase Authentication ile kullanıcıyı kaydet
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Email doğrulama linki gönder
                            sendEmailVerification(user);

                            // Firestore'a kullanıcı bilgilerini kaydet
                            saveUserToFirestore(user.getUid(), email, username);

                            // SQLite'a kullanıcıyı ekle
                            boolean isInserted = databaseHelper.kullaniciEkle(username, email, password);
                            Log.d(TAG, "kullaniciEkle sonucu: " + isInserted);

                            // Kullanıcıya email doğrulama gerektiğini bildir
                            Toast.makeText(RegisterActivity.this,
                                    "Kayıt başarılı! Lütfen email adresinizi doğrulayın.",
                                    Toast.LENGTH_LONG).show();
                            clearFields();

                            // LoginActivity'ye yönlendir
                            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finishAffinity();
                        }
                    } else {
                        Exception e = task.getException();
                        Log.e(TAG, "Kayıt hatası: ", e); // Tam hata yığınını logla
                        String errorMessage = e != null ? e.getMessage() : "Bilinmeyen hata";
                        Toast.makeText(RegisterActivity.this,
                                "Kayıt başarısız: " + errorMessage,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void sendEmailVerification(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Email doğrulama linki gönderildi: " + user.getEmail());
                    } else {
                        Log.e(TAG, "Email doğrulama linki gönderilemedi: " + task.getException().getMessage());
                        Toast.makeText(RegisterActivity.this,
                                "Email doğrulama linki gönderilemedi: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(String userId, String email, String username) {
        Map<String, Object> user = new HashMap<>();
        user.put("email", email);
        user.put("username", username);
        user.put("emailVerified", false); // Email doğrulama durumunu ekle
        user.put("foundItemsCount", 0);   // Yeni eklenen alan
        user.put("deliveredItemsCount", 0); // Yeni eklenen alan

        db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Kullanıcı Firestore'a kaydedildi: " + email))
                .addOnFailureListener(e -> Log.e(TAG, "Firestore'a kaydetme hatası: " + e.getMessage()));
    }

    private boolean validateInputs(String username, String email, String password) {
        if (username.isEmpty()) {
            edtUsername.setError("Kullanıcı adı boş olamaz!");
            return false;
        }

        if (email.isEmpty()) {
            edtEmail.setError("E-posta boş olamaz!");
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("Geçerli bir e-posta girin!");
            return false;
        }

        if (password.isEmpty()) {
            edtPassword.setError("Şifre boş olamaz!");
            return false;
        }

        if (password.length() < 6) {
            edtPassword.setError("Şifre en az 6 karakter olmalıdır!");
            return false;
        }

        return true;
    }

    private void clearFields() {
        edtUsername.setText("");
        edtEmail.setText("");
        edtPassword.setText("");
    }
}