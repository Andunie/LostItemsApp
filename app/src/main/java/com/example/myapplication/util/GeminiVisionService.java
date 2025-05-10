package com.example.myapplication.util;

import android.graphics.Bitmap;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeminiVisionService {
    private static final String TAG = "GeminiVisionService";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent";
    private final String apiKey;
    private final Executor executor;

    public GeminiVisionService(String apiKey) {
        this.apiKey = apiKey;
        this.executor = Executors.newCachedThreadPool();
    }

    // Fotoğrafı analiz etmek için metod
    public CompletableFuture<GeminiAnalysisResult> analyzeImage(Bitmap image) {
        CompletableFuture<GeminiAnalysisResult> future = new CompletableFuture<>();
        
        CompletableFuture.runAsync(() -> {
            try {
                String base64Image = bitmapToBase64(image);
                String jsonRequest = createJsonRequest(base64Image);
                String jsonResponse = makeApiCall(jsonRequest);
                String responseText = parseJsonResponse(jsonResponse);
                GeminiAnalysisResult result = parseResponse(responseText);
                future.complete(result);
            } catch (Exception e) {
                Log.e(TAG, "Görüntü analizi hatası: " + e.getMessage());
                future.completeExceptionally(e);
            }
        }, executor);
        
        return future;
    }
    
    private String bitmapToBase64(Bitmap bitmap) {
        Bitmap processedBitmap = bitmap;
        int maxDimension = 1024;
        
        if (bitmap.getWidth() > maxDimension || bitmap.getHeight() > maxDimension) {
            float scaleFactor = bitmap.getWidth() > bitmap.getHeight() 
                ? (float) maxDimension / bitmap.getWidth() 
                : (float) maxDimension / bitmap.getHeight();
            
            int targetWidth = Math.round(bitmap.getWidth() * scaleFactor);
            int targetHeight = Math.round(bitmap.getHeight() * scaleFactor);
            
            processedBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        processedBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream);
        
        if (processedBitmap != bitmap) {
            processedBitmap.recycle();
        }
        
        byte[] imageBytes = outputStream.toByteArray();
        
        try {
            outputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "OutputStream kapatma hatası");
        }
        
        return android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);
    }
    
    private String createJsonRequest(String base64Image) throws Exception {
        JSONObject jsonRequest = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        
        // Metin parçasını ekle
        JSONObject textPart = new JSONObject();
        textPart.put("text", "Bu fotoğraftaki nesneyi tanımla. Önce nesnenin adını söyle, " +
                "sonra birkaç cümle ile ayrıntılı açıklama yap. Yanıtını şu formatta döndür:\n\n" +
                "İSİM: [nesnenin adı]\n" +
                "AÇIKLAMA: [nesnenin detaylı açıklaması]");
        parts.put(textPart);
        
        // Görüntü parçasını ekle
        JSONObject imagePart = new JSONObject();
        JSONObject imageData = new JSONObject();
        imageData.put("mime_type", "image/jpeg");
        imageData.put("data", base64Image);
        imagePart.put("inline_data", imageData);
        parts.put(imagePart);
        
        content.put("parts", parts);
        contents.put(content);
        jsonRequest.put("contents", contents);
        
        // Gemini 2.0 için gerekli yapılandırmalar
        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.4);
        generationConfig.put("maxOutputTokens", 800);
        generationConfig.put("topP", 0.95);
        generationConfig.put("topK", 40);
        jsonRequest.put("generationConfig", generationConfig);
        
        return jsonRequest.toString();
    }
    
    private String makeApiCall(String jsonRequest) throws IOException {
        URL url = new URL(API_URL + "?key=" + apiKey);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonRequest.getBytes("UTF-8");
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                StringBuilder errorResponse = new StringBuilder();
                try (Scanner scanner = new Scanner(connection.getErrorStream(), "UTF-8")) {
                    while (scanner.hasNextLine()) {
                        errorResponse.append(scanner.nextLine());
                    }
                }
                throw new IOException("API isteği başarısız - HTTP Kodu: " + responseCode + 
                                     ", Yanıt: " + errorResponse.toString());
            }
            
            StringBuilder response = new StringBuilder();
            try (Scanner scanner = new Scanner(connection.getInputStream(), "UTF-8")) {
                while (scanner.hasNextLine()) {
                    response.append(scanner.nextLine());
                }
            }
            
            return response.toString();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    private String parseJsonResponse(String jsonResponse) throws Exception {
        try {
            JSONObject response = new JSONObject(jsonResponse);
            
            if (response.has("error")) {
                JSONObject error = response.getJSONObject("error");
                throw new Exception("API Hata: " + error.optString("message", "Bilinmeyen hata"));
            }
            
            JSONArray candidates = response.getJSONArray("candidates");
            
            if (candidates.length() > 0) {
                JSONObject candidate = candidates.getJSONObject(0);
                
                if (candidate.has("content")) {
                    JSONObject content = candidate.getJSONObject("content");
                    if (content.has("parts")) {
                        JSONArray parts = content.getJSONArray("parts");
                        
                        if (parts.length() > 0) {
                            return parts.getJSONObject(0).getString("text");
                        }
                    }
                }
            }
            
            throw new Exception("Gemini API yanıtı anlaşılamadı");
        } catch (Exception e) {
            Log.e(TAG, "JSON yanıtı ayrıştırma hatası: " + e.getMessage());
            throw new Exception("API yanıtı işlenemedi: " + e.getMessage());
        }
    }
    
    private GeminiAnalysisResult parseResponse(String response) {
        String title = "";
        String description = "";
        
        if (response.contains("İSİM:") && response.contains("AÇIKLAMA:")) {
            int titleStart = response.indexOf("İSİM:") + "İSİM:".length();
            int titleEnd = response.indexOf("AÇIKLAMA:");
            int descStart = response.indexOf("AÇIKLAMA:") + "AÇIKLAMA:".length();
            
            if (titleStart > 0 && titleEnd > titleStart) {
                title = response.substring(titleStart, titleEnd).trim();
            }
            
            if (descStart > 0) {
                description = response.substring(descStart).trim();
            }
        } else {
            String[] lines = response.split("\n");
            if (lines.length > 0) {
                title = lines[0].trim();
                
                if (lines.length > 1) {
                    StringBuilder descBuilder = new StringBuilder();
                    for (int i = 1; i < lines.length; i++) {
                        descBuilder.append(lines[i].trim()).append("\n");
                    }
                    description = descBuilder.toString().trim();
                }
            }
        }
        
        return new GeminiAnalysisResult(title, description);
    }
    
    public static class GeminiAnalysisResult {
        private final String title;
        private final String description;
        
        public GeminiAnalysisResult(String title, String description) {
            this.title = title;
            this.description = description;
        }
        
        public String getTitle() {
            return title;
        }
        
        public String getDescription() {
            return description;
        }
    }
}