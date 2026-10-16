package com.lap1.mystore;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class MyStoreApp extends Application {
    public static final String PREFS_NAME = "MyStorePrefs";
    public static final String KEY_THEME_MODE = "app_theme_mode"; // "system", "dark", "light"
    public static final String KEY_DARK_MODE = "is_dark_mode";

    @Override
    public void onCreate() {
        super.onCreate();
        applyAppTheme();
    }

    public void applyAppTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        // Default to "system" so the app naturally matches the phone's light or dark mode!
        String themeMode = prefs.getString(KEY_THEME_MODE, "system");

        if ("dark".equalsIgnoreCase(themeMode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if ("light".equalsIgnoreCase(themeMode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            // Support phone system theme (وضع الهاتف التلقائي)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
