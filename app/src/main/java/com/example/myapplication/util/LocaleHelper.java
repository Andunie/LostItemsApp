package com.example.myapplication.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import java.util.Locale;

public class LocaleHelper {
    private static final String TAG = "LocaleHelper";

    // Uygulamanın dilini telefon diline göre ayarlar
    public static Context setLocale(Context context) {
        return updateResources(context, getLanguageFromDeviceLocale());
    }

    // Cihazın geçerli dil ayarını alır
    private static String getLanguageFromDeviceLocale() {
        String language = Locale.getDefault().getLanguage();
        Log.d(TAG, "Cihaz dili: " + language);
        
        // Eğer Türkçe ise "tr", değilse "en" olarak ayarla
        return "tr".equals(language) ? "tr" : "en";
    }

    // Uygulamanın dil ayarlarını günceller
    private static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLocale(locale);
            return context.createConfigurationContext(configuration);
        } else {
            configuration.locale = locale;
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
            return context;
        }
    }
} 