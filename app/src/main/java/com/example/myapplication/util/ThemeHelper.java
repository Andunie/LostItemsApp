package com.example.myapplication.util;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeHelper {
    private static final String TAG = "ThemeHelper";

    /**
     * Cihazın mevcut tema ayarına göre uygulamanın temasını ayarlar
     * (Koyu/Açık/Sistem Varsayılanı)
     */
    public static void applyTheme(Context context) {
        // Sistem temasını kontrol et
        int nightMode = context.getResources().getConfiguration().uiMode & 
                Configuration.UI_MODE_NIGHT_MASK;
        
        Log.d(TAG, "Sistem tema modu: " + (nightMode == Configuration.UI_MODE_NIGHT_YES ? 
                "Koyu Tema" : "Açık Tema"));
        
        // Uygulama temasını sistem temasına göre otomatik olarak ayarla
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /**
     * Cihazın koyu tema kullanıp kullanmadığını kontrol eder
     */
    public static boolean isNightModeActive(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode & 
                Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }
    
    /**
     * Uygulamayı belirtilen tema moduna değiştirir
     * @param nightMode true: koyu tema, false: açık tema
     */
    public static void setNightMode(boolean nightMode) {
        AppCompatDelegate.setDefaultNightMode(
                nightMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
    
    /**
     * Uygulamayı sistem temasını takip edecek şekilde ayarlar
     */
    public static void setFollowSystemMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
} 