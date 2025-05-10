package com.example.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.IntentCompat;

import com.example.myapplication.util.GeminiVisionService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateAdActivity extends BaseActivity implements OnMapReadyCallback {

    private static final String TAG = "CreateAdActivity";
    private MapView mapView;
    private GoogleMap googleMap;
    private EditText edtAddress, edtItemTitle, edtDescription;
    private Button btnPostAd, btnLocation, btnAddPhoto, btnTakePhoto;
    private ImageView ivItemPhoto;
    private ProgressBar progressAnalyzing;
    private RadioGroup rgPostType;
    private RadioButton rbLost, rbFound;
    private Geocoder geocoder;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int REQUEST_LOCATION = 1001;
    private static final int REQUEST_STORAGE = 1002;
    private static final int REQUEST_CAMERA = 1003;
    private Bitmap selectedImage;
    private GeminiVisionService geminiVisionService;
    
    // API anahtarını doğrudan koddan kaldırıp BuildConfig'den alıyoruz
    // private static final String GEMINI_API_KEY = "AIzaSyBYxC0LDyQzENMskG5irglAhkr-_P2uJRc";
    
    // Kamera için URI
    private Uri photoURI;

    private final ActivityResultLauncher<String> requestStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openGallery();
                } else {
                    showSnackbar("Fotoğraf eklemek için izin gerekli");
                }
            });
            
    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openCamera();
                } else {
                    showSnackbar("Fotoğraf çekmek için kamera izni gerekli");
                }
            });

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    try {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            InputStream inputStream = getContentResolver().openInputStream(imageUri);
                            selectedImage = BitmapFactory.decodeStream(inputStream);
                            ivItemPhoto.setImageBitmap(selectedImage);
                            ivItemPhoto.setVisibility(View.VISIBLE);
                            analyzeImage(); // Resmi yükledikten sonra otomatik analiz et
                        }
                    } catch (Exception e) {
                        showSnackbar("Fotoğraf yüklenemedi: " + e.getMessage());
                        Log.e(TAG, "Fotoğraf yükleme hatası: " + e.getMessage());
                    }
                }
            });
            
    private final ActivityResultLauncher<Intent> takePictureLauncher = 
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(photoURI);
                        selectedImage = BitmapFactory.decodeStream(inputStream);
                        ivItemPhoto.setImageBitmap(selectedImage);
                        ivItemPhoto.setVisibility(View.VISIBLE);
                        analyzeImage(); // Fotoğrafı çektikten sonra analiz et
                    } catch (Exception e) {
                        showSnackbar("Fotoğraf işlenemedi: " + e.getMessage());
                        Log.e(TAG, "Kamera fotoğraf işleme hatası: " + e.getMessage());
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Klavye davranışını düzenleme
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        
        // Gemini Vision servisini başlat (BuildConfig'den API anahtarı ile)
        geminiVisionService = new GeminiVisionService(BuildConfig.GEMINI_API_KEY);

        // UI bileşenlerini bağla
        initComponents();

        // Harita ve diğer başlangıç işlemleri
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        geocoder = new Geocoder(this, Locale.getDefault());
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Kullanıcı kimliğini kontrol et
        String userId = getUserId();
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Kullanıcı kimliği alınamadı!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnPostAd.setOnClickListener(v -> ilanEkle());
        btnLocation.setOnClickListener(v -> requestLocationPermission());
        btnAddPhoto.setOnClickListener(v -> checkStoragePermissionAndOpenGallery());
        btnTakePhoto.setOnClickListener(v -> checkCameraPermissionAndOpenCamera());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;
        
        // Tüm harita hareketlerini ve kontrollerini etkinleştir
        googleMap.getUiSettings().setAllGesturesEnabled(true);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        
        // Haritaya tıklama olayını ayarla
        googleMap.setOnMapClickListener(latLng -> {
            googleMap.clear();
            googleMap.addMarker(new MarkerOptions().position(latLng).title("Seçilen Konum"));
            
            // İlerleme çubuğunu göster
            progressAnalyzing.setVisibility(View.VISIBLE);
            
            // Adresi arka planda çöz
            new Thread(() -> {
                try {
                    List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                    runOnUiThread(() -> {
                        progressAnalyzing.setVisibility(View.GONE);
                        if (addresses != null && !addresses.isEmpty()) {
                            String address = addresses.get(0).getAddressLine(0);
                            edtAddress.setText(address);
                        } else {
                            edtAddress.setText("Adres bulunamadı");
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        progressAnalyzing.setVisibility(View.GONE);
                        edtAddress.setText("Adres alınamadı");
                        Log.e(TAG, "Geocoder hatası: " + e.getMessage());
                    });
                }
            }).start();
        });
    }

    private void checkStoragePermissionAndOpenGallery() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13 (API 33) ve üzeri için READ_MEDIA_IMAGES iznini iste
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_IMAGES) == 
                    PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES)) {
                // İzin açıklaması göster
                showPermissionRationaleDialog(Manifest.permission.READ_MEDIA_IMAGES);
            } else {
                requestStoragePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            // Android 12 ve altı için READ_EXTERNAL_STORAGE iznini iste
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE) == 
                    PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // İzin açıklaması göster
                showPermissionRationaleDialog(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }
    
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }
    
    private void analyzeImage() {
        if (selectedImage == null) {
            showSnackbar("Lütfen önce bir fotoğraf ekleyin");
            return;
        }
        
        // Analiz işlemi başlıyor, UI'yi güncelle
        progressAnalyzing.setVisibility(View.VISIBLE);
        btnAddPhoto.setEnabled(false);
        
        geminiVisionService.analyzeImage(selectedImage)
                .thenAccept(result -> {
                    runOnUiThread(() -> {
                        // Başlık ve açıklama alanlarını doldur
                        edtItemTitle.setText(result.getTitle());
                        edtDescription.setText(result.getDescription());
                        
                        // UI'yi güncelle
                        progressAnalyzing.setVisibility(View.GONE);
                        btnAddPhoto.setEnabled(true);
                        
                        showSnackbar("Görüntü başarıyla analiz edildi!");
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        // UI'yi güncelle
                        progressAnalyzing.setVisibility(View.GONE);
                        btnAddPhoto.setEnabled(true);
                        
                        // Root cause'u bul
                        Throwable rootCause = e;
                        while (rootCause.getCause() != null) {
                            rootCause = rootCause.getCause();
                        }
                        
                        String errorMsg = "Analiz sırasında hata: " + rootCause.getMessage();
                        Log.e(TAG, errorMsg);
                        
                        showSnackbar(errorMsg);
                        showErrorDialog("API Hatası", rootCause.getMessage());
                    });
                    return null;
                });
    }

    private void requestLocationPermission() {
        String permission = Manifest.permission.ACCESS_FINE_LOCATION;
        boolean isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
        boolean shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);

        if (isGranted) {
            // İzin zaten verilmiş, konumu al
            getCurrentLocation();
        } else if (shouldShowRationale) {
            // Kullanıcı daha önce reddetmiş, açıklama göster
            new AlertDialog.Builder(this)
                    .setTitle("Konum İzni Gerekli")
                    .setMessage("Bu özellik, kayıp eşyanızın yerini haritada işaretlemek için konumunuza ihtiyaç duyar. Lütfen izin verin.")
                    .setPositiveButton("Tamam", (dialog, which) -> {
                        ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_LOCATION);
                    })
                    .setNegativeButton("İptal", null)
                    .show();
        } else {
            // İlk defa izin isteniyor
            ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_LOCATION);
        }
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        
        // UI güncellemesi - yükleniyor göster
        progressAnalyzing.setVisibility(View.VISIBLE);
        
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    progressAnalyzing.setVisibility(View.GONE);
                    
                    if (location != null) {
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        googleMap.clear();
                        
                        // Marker ekle
                        googleMap.addMarker(new MarkerOptions()
                                .position(currentLatLng)
                                .title("Seçilen Konum"));
                        
                        // Kamera pozisyonunu daha akıcı şekilde ayarla
                        googleMap.animateCamera(
                            com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(
                                new com.google.android.gms.maps.model.CameraPosition.Builder()
                                    .target(currentLatLng)
                                    .zoom(15f)
                                    .bearing(0)
                                    .tilt(0)
                                    .build()
                            ),
                            1000,  // Animasyon süresi (ms)
                            null
                        );
                        
                        try {
                            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                            if (addresses != null && !addresses.isEmpty()) {
                                String address = addresses.get(0).getAddressLine(0);
                                edtAddress.setText(address);
                            } else {
                                edtAddress.setText("Adres bulunamadı");
                            }
                        } catch (IOException e) {
                            edtAddress.setText("Adres alınamadı");
                            Log.e(TAG, "Geocoder hatası: " + e.getMessage());
                        }
                    } else {
                        showSnackbar("Konum alınamadı, lütfen GPS'i açın!");
                    }
                })
                .addOnFailureListener(e -> {
                    progressAnalyzing.setVisibility(View.GONE);
                    showSnackbar("Konum alınırken hata: " + e.getMessage());
                    Log.e(TAG, "Konum hatası: " + e.getMessage());
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // İzin verildi, konumu al
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Konum izni verilmedi, harita manuel kullanılabilir!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void ilanEkle() {
        String address = edtAddress.getText().toString().trim();
        String itemTitle = edtItemTitle.getText().toString().trim();
        String description = edtDescription.getText().toString().trim();
        String userId = getUserId();

        // Alanların boş olup olmadığını kontrol et
        if (address.isEmpty() || itemTitle.isEmpty() || description.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), "Lütfen tüm alanları doldurun!", Snackbar.LENGTH_SHORT).show();
            return;
        }

        // RadioGroup'tan postedBy değerini al
        int selectedId = rgPostType.getCheckedRadioButtonId();
        if (selectedId == -1) {
            Snackbar.make(findViewById(android.R.id.content), "Lütfen ilanın türünü seçin (Lost/Found)!", Snackbar.LENGTH_SHORT).show();
            return;
        }
        String postedBy = selectedId == R.id.rbLost ? "lost" : "found";

        // Firestore'a kaydedilecek veri
        Map<String, Object> itemData = new HashMap<>();
        itemData.put("address", address);
        itemData.put("itemTitle", itemTitle);
        itemData.put("description", description);
        itemData.put("userId", userId);
        itemData.put("postedBy", postedBy);
        itemData.put("creatorId", userId);
        itemData.put("ownerId", postedBy.equals("lost") ? userId : null);
        itemData.put("finderId", postedBy.equals("found") ? userId : null);
        itemData.put("isDelivered", false);

        // Firestore'a ilanı ekle
        db.collection("lostItems")
                .add(itemData)
                .addOnSuccessListener(documentReference -> {
                    Log.d("CreateAdActivity", "İlan başarıyla eklendi, ID: " + documentReference.getId());
                    Snackbar.make(findViewById(android.R.id.content), "İlan başarıyla eklendi", Snackbar.LENGTH_SHORT).show();
                    Intent intent = new Intent(CreateAdActivity.this, LostItemsActivity.class);
                    intent.putExtra("USER_ID", userId);
                    intent.putExtra("USER_NAME", getUserName());
                    intent.putExtra("USER_EMAIL", getUserEmail());
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e("CreateAdActivity", "İlan eklenirken hata: " + e.getMessage());
                    Snackbar.make(findViewById(android.R.id.content), "İlan eklenirken hata oluştu!", Snackbar.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected int getContentLayoutId() {
        return R.layout.activity_create_ad;
    }

    @Override
    protected int getSelectedNavigationItem() {
        return R.id.menu_create_ad;
    }

    private void initComponents() {
        mapView = findViewById(R.id.mapView);
        edtAddress = findViewById(R.id.edtAddress);
        edtItemTitle = findViewById(R.id.edtItemTitle);
        edtDescription = findViewById(R.id.edtItemDescription);
        btnPostAd = findViewById(R.id.btnPostAd);
        btnLocation = findViewById(R.id.btnLocation);
        rgPostType = findViewById(R.id.rgPostType);
        rbLost = findViewById(R.id.rbLost);
        rbFound = findViewById(R.id.rbFound);
        
        // Yeni eklenen bileşenler
        btnAddPhoto = findViewById(R.id.btnAddPhoto);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        ivItemPhoto = findViewById(R.id.ivItemPhoto);
        progressAnalyzing = findViewById(R.id.progressAnalyzing);
    }
    
    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    private void showPermissionRationaleDialog(String permission) {
        new AlertDialog.Builder(this)
                .setTitle("Fotoğraf Erişim İzni Gerekli")
                .setMessage("Kayıp/bulunan eşya ilanınıza fotoğraf eklemek ve Gemini AI ile analiz etmek için galeriye erişim izni gerekiyor.")
                .setPositiveButton("İzin Ver", (dialog, which) -> {
                    requestStoragePermissionLauncher.launch(permission);
                })
                .setNegativeButton("İptal", (dialog, which) -> {
                    showSnackbar("Fotoğraf eklemek için izin gerekli");
                })
                .show();
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Tamam", null)
                .show();
    }

    private void checkCameraPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) == 
                PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // İzin açıklaması göster
            showCameraPermissionRationaleDialog();
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }
    
    private void showCameraPermissionRationaleDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Kamera İzni Gerekli")
                .setMessage("Kayıp/bulunan eşya ilanınıza fotoğraf eklemek için kamera erişim izni gerekiyor.")
                .setPositiveButton("İzin Ver", (dialog, which) -> {
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                })
                .setNegativeButton("İptal", (dialog, which) -> {
                    showSnackbar("Fotoğraf çekmek için kamera izni gerekli");
                })
                .show();
    }
    
    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Fotoğraf için dosya oluştur
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                showSnackbar("Kamera dosyası oluşturulamadı");
                Log.e(TAG, "Kamera dosyası oluşturma hatası", ex);
                return;
            }
            
            // Dosya başarıyla oluşturulduysa
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(this,
                        "com.example.myapplication.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                takePictureLauncher.launch(takePictureIntent);
            }
        } else {
            showSnackbar("Cihazınızda kamera uygulaması bulunamadı");
        }
    }
    
    private File createImageFile() throws IOException {
        // Benzersiz bir dosya adı oluştur
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                imageFileName,  /* öneki */
                ".jpg",         /* soneki */
                storageDir      /* dizini */
        );
    }
}