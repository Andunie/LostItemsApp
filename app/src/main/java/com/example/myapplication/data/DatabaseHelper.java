package com.example.myapplication.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import com.example.myapplication.model.LostItem;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "Userdata.db";
    private static final int DATABASE_VERSION = 4;
    private static final String TABLE_USERS = "Kullanici";
    private static final String TABLE_LOST_ITEMS = "KayipEsya";
    private FirebaseFirestore db;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        db = FirebaseFirestore.getInstance();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_USERS + " (" +
                "Id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "Username TEXT, " +
                "Email TEXT UNIQUE, " +
                "Password TEXT)";
        db.execSQL(createTableQuery);

        String createLostItemTable = "CREATE TABLE " + TABLE_LOST_ITEMS + " (" +
                "Id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "UserId TEXT, " +
                "Address TEXT, " +
                "ItemTitle TEXT, " +
                "Description TEXT)";
        db.execSQL(createLostItemTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN Soyad TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE " + TABLE_LOST_ITEMS + " (" +
                    "Id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "UserId INTEGER, " +
                    "Address TEXT, " +
                    "ItemTitle TEXT, " +
                    "Description TEXT, " +
                    "FOREIGN KEY(UserId) REFERENCES " + TABLE_USERS + "(Id))");
        }
        if (oldVersion < 4) {
            db.execSQL("CREATE TABLE temp_KayipEsya AS SELECT * FROM " + TABLE_LOST_ITEMS);
            db.execSQL("DROP TABLE " + TABLE_LOST_ITEMS);
            db.execSQL("CREATE TABLE " + TABLE_LOST_ITEMS + " (" +
                    "Id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "UserId TEXT, " +
                    "Address TEXT, " +
                    "ItemTitle TEXT, " +
                    "Description TEXT)");
            db.execSQL("INSERT INTO " + TABLE_LOST_ITEMS + " (Id, UserId, Address, ItemTitle, Description) " +
                    "SELECT Id, CAST(UserId AS TEXT), Address, ItemTitle, Description FROM temp_KayipEsya");
            db.execSQL("DROP TABLE temp_KayipEsya");
        }
    }

    public void migrateUsersToFirestore() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_USERS, null);
        while (cursor.moveToNext()) {
            String email = cursor.getString(cursor.getColumnIndexOrThrow("Email"));
            String username = cursor.getString(cursor.getColumnIndexOrThrow("Username"));
            Map<String, Object> user = new HashMap<>();
            user.put("email", email);
            user.put("username", username);
            this.db.collection("users")
                    .document()
                    .set(user)
                    .addOnSuccessListener(aVoid -> Log.d("DatabaseHelper", "User migrated: " + email))
                    .addOnFailureListener(e -> Log.e("DatabaseHelper", "Error migrating user: " + e.getMessage()));
        }
        cursor.close();
        db.close();
    }

    public void migrateLostItemsToFirestore() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_LOST_ITEMS, null);
        while (cursor.moveToNext()) {
            long id = cursor.getLong(cursor.getColumnIndexOrThrow("Id"));
            String userId = cursor.getString(cursor.getColumnIndexOrThrow("UserId"));
            String address = cursor.getString(cursor.getColumnIndexOrThrow("Address"));
            String itemTitle = cursor.getString(cursor.getColumnIndexOrThrow("ItemTitle"));
            String description = cursor.getString(cursor.getColumnIndexOrThrow("Description"));
            Map<String, Object> lostItem = new HashMap<>();
            lostItem.put("userId", userId);
            lostItem.put("address", address);
            lostItem.put("itemTitle", itemTitle);
            lostItem.put("description", description);
            this.db.collection("lostItems")
                    .document(String.valueOf(id))
                    .set(lostItem)
                    .addOnSuccessListener(aVoid -> Log.d("DatabaseHelper", "Lost item migrated: " + id))
                    .addOnFailureListener(e -> Log.e("DatabaseHelper", "Error migrating lost item: " + e.getMessage()));
        }
        cursor.close();
        db.close();
    }

    public boolean kullaniciEkle(String kullaniciAdi, String email, String sifre) {
        SQLiteDatabase sqliteDb = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("Username", kullaniciAdi);
        contentValues.put("Email", email);
        contentValues.put("Password", sifre);

        long result = sqliteDb.insert(TABLE_USERS, null, contentValues);
        sqliteDb.close();
        return result != -1;
    }

    public boolean kullaniciGirisi(String email, String sifre) {
        SQLiteDatabase sqliteDb = this.getReadableDatabase();
        Cursor cursor = sqliteDb.rawQuery("SELECT * FROM " + TABLE_USERS + " WHERE Email = ? AND Password = ?", new String[]{email, sifre});

        boolean girisBasarili = cursor.getCount() > 0;
        cursor.close();
        sqliteDb.close();
        return girisBasarili;
    }

    public void ilanEkle(String userId, String address, String itemTitle, String description, Callback<Boolean> callback) {
        Map<String, Object> lostItem = new HashMap<>();
        lostItem.put("userId", userId);
        lostItem.put("address", address);
        lostItem.put("itemTitle", itemTitle);
        lostItem.put("description", description);
        db.collection("lostItems")
                .add(lostItem)
                .addOnSuccessListener(documentReference -> {
                    SQLiteDatabase sqliteDb = this.getWritableDatabase();
                    ContentValues contentValues = new ContentValues();
                    contentValues.put("UserId", userId);
                    contentValues.put("Address", address);
                    contentValues.put("ItemTitle", itemTitle);
                    contentValues.put("Description", description);
                    long result = sqliteDb.insert(TABLE_LOST_ITEMS, null, contentValues);
                    sqliteDb.close();
                    callback.onSuccess(result != -1);
                })
                .addOnFailureListener(e -> {
                    Log.e("DatabaseHelper", "Error adding lost item to Firestore: " + e.getMessage());
                    callback.onSuccess(false);
                });
    }

    public void getMyAds(String userId, Callback<List<LostItem>> callback) {
        db.collection("lostItems")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<LostItem> ads = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String id = document.getId(); // String olarak al
                        String docUserId = document.getString("userId");
                        String address = document.getString("address");
                        String title = document.getString("itemTitle");
                        String description = document.getString("description");
                        LostItem item = new LostItem(id, docUserId, title, description, address);
                        getUserEmailById(docUserId, email -> {
                            item.setUserEmail(email);
                            ads.add(item);
                            if (ads.size() == queryDocumentSnapshots.size()) {
                                callback.onSuccess(ads);
                            }
                        });
                    }
                    if (queryDocumentSnapshots.isEmpty()) {
                        callback.onSuccess(ads);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("DatabaseHelper", "Error getting my ads: " + e.getMessage());
                    callback.onSuccess(new ArrayList<>());
                });
    }

    public void getOtherLostItems(String userId, Callback<List<LostItem>> callback) {
        db.collection("lostItems")
                .whereNotEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<LostItem> ads = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String id = document.getId(); // String olarak al
                        String docUserId = document.getString("userId");
                        String address = document.getString("address");
                        String title = document.getString("itemTitle");
                        String description = document.getString("description");
                        LostItem item = new LostItem(id, docUserId, title, description, address);
                        getUserEmailById(docUserId, email -> {
                            item.setUserEmail(email);
                            ads.add(item);
                            if (ads.size() == queryDocumentSnapshots.size()) {
                                callback.onSuccess(ads);
                            }
                        });
                    }
                    if (queryDocumentSnapshots.isEmpty()) {
                        callback.onSuccess(ads);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("DatabaseHelper", "Error getting other lost items: " + e.getMessage());
                    callback.onSuccess(new ArrayList<>());
                });
    }

    public void getUserEmailById(String userId, Callback<String> callback) {
        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String email = documentSnapshot.getString("email");
                        callback.onSuccess(email != null ? email : "Bilinmeyen Kullanıcı");
                    } else {
                        callback.onSuccess("Bilinmeyen Kullanıcı");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("DatabaseHelper", "Error getting user email: " + e.getMessage());
                    callback.onSuccess("Bilinmeyen Kullanıcı");
                });
    }

    public void getUserNameByEmail(String email, Callback<String> callback) {
        db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            String username = document.getString("username");
                            callback.onSuccess(username != null ? username : "Bilinmeyen Kullanıcı");
                            break;
                        }
                    } else {
                        callback.onSuccess("Bilinmeyen Kullanıcı");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("DatabaseHelper", "Error getting username: " + e.getMessage());
                    callback.onSuccess("Bilinmeyen Kullanıcı");
                });
    }

    public void getItemTitleById(String itemId, Callback<String> callback) {
        db.collection("lostItems")
                .document(itemId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String itemTitle = documentSnapshot.getString("itemTitle");
                        callback.onSuccess(itemTitle != null ? itemTitle : "Başlık Yok");
                    } else {
                        callback.onSuccess("Başlık Yok");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("DatabaseHelper", "Error getting item title: " + e.getMessage());
                    callback.onSuccess("Başlık Yok");
                });
    }

    public void getActiveAdsCount(String userId, Callback<Integer> callback) {
        db.collection("lostItems")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    callback.onSuccess(queryDocumentSnapshots.size());
                })
                .addOnFailureListener(e -> {
                    Log.e("DatabaseHelper", "Error getting active ads count: " + e.getMessage());
                    callback.onSuccess(0);
                });
    }

    public void deleteLostItems(Set<String> itemIds, String userId, Callback<Boolean> callback) {
        for (String itemId : itemIds) {
            if (itemId != null) {
                db.collection("lostItems")
                        .document(itemId)
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            SQLiteDatabase sqliteDb = this.getWritableDatabase();
                            int rowsDeleted = sqliteDb.delete(TABLE_LOST_ITEMS, "Id = ? AND UserId = ?",
                                    new String[]{itemId, userId});
                            sqliteDb.close();
                            Log.d("DatabaseHelper", "deleteLostItems - ItemId: " + itemId + ", Rows deleted from SQLite: " + rowsDeleted);
                        })
                        .addOnFailureListener(e -> Log.e("DatabaseHelper", "Error deleting lost item: " + e.getMessage()));
            }
        }
        callback.onSuccess(true);
    }

    public void getLostItemById(String itemId, Callback<LostItem> callback) {
        db.collection("lostItems")
                .document(itemId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String id = documentSnapshot.getId(); // String olarak al
                        String userId = documentSnapshot.getString("userId");
                        String address = documentSnapshot.getString("address");
                        String title = documentSnapshot.getString("itemTitle");
                        String description = documentSnapshot.getString("description");
                        LostItem item = new LostItem(id, userId, title, description, address);
                        getUserEmailById(userId, email -> {
                            item.setUserEmail(email);
                            callback.onSuccess(item);
                        });
                    } else {
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("DatabaseHelper", "Error getting lost item: " + e.getMessage());
                    callback.onSuccess(null);
                });
    }

    public void getUserIdByEmail(String email, Callback<String> callback) {
        Log.d("DatabaseHelper", "Fetching userId for email: " + email);
        db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String userId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        Log.d("DatabaseHelper", "UserId found: " + userId);
                        callback.onSuccess(userId);
                    } else {
                        Log.w("DatabaseHelper", "User not found for email: " + email);
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("DatabaseHelper", "Error getting userId by email: " + e.getMessage());
                    callback.onSuccess(null);
                });
    }

    public void getUserNameById(String userId, Callback<String> callback) {
        Log.d("DatabaseHelper", "Fetching username for userId: " + userId);
        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String username = documentSnapshot.getString("username");
                        Log.d("DatabaseHelper", "Username found: " + username + " for userId: " + userId);
                        callback.onSuccess(username);
                    } else {
                        Log.w("DatabaseHelper", "User not found for userId: " + userId);
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("DatabaseHelper", "Error getting username by userId: " + e.getMessage());
                    callback.onSuccess(null);
                });
    }

    public interface Callback<T> {
        void onSuccess(T result);
    }
}