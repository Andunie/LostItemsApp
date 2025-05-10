package com.example.myapplication;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.example.myapplication.util.LocaleHelper;
import com.example.myapplication.util.ThemeHelper;

public class MyApplication extends Application {
    private static final String TAG = "MyApplication";
    
    @Override
    protected void attachBaseContext(Context base) {
        // Uygulamayı başlatırken temel context'e dil ayarını ekliyoruz
        Context context = LocaleHelper.setLocale(base);
        super.attachBaseContext(context);
        Log.d(TAG, "Dil ayarları yapılandırıldı");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Tema ayarlarını uygula (sistem temasını takip et)
        ThemeHelper.applyTheme(this);
        Log.d(TAG, "Tema ayarları yapılandırıldı (Sistem teması takip ediliyor)");
    }
} 