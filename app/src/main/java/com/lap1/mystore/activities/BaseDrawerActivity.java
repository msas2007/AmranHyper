package com.lap1.mystore.activities;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.models.StoreSettings;
import com.lap1.mystore.utils.DrawerNavigationHelper;
import com.lap1.mystore.utils.SessionManager;

/**
 * الفئة الأساسية للشاشات التي تستخدم القائمة الجانبية (BaseDrawerActivity).
 * توفر الربط التلقائي للمستخدم المسجل وإعدادات المتجر والتنقل الموحد عبر القائمة الجانبية.
 */
public abstract class BaseDrawerActivity extends AppCompatActivity {

    protected DatabaseHelper dbHelper;
    protected SessionManager sessionManager;
    protected StoreSettings storeSettings;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbHelper = new DatabaseHelper(this);
        sessionManager = SessionManager.getInstance(this);
        storeSettings = dbHelper.getStoreSettings();
    }

    protected void setupDrawer(String activeCategory, String pageTitle) {
        DrawerNavigationHelper.setupDrawer(this, activeCategory, pageTitle);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStoreAndSession();
    }

    protected void refreshStoreAndSession() {
        if (dbHelper != null) {
            storeSettings = dbHelper.getStoreSettings();
        }
    }

    public String getCurrentStoreName() {
        return storeSettings != null ? storeSettings.getStoreName() : "عمران هايبر";
    }

    public String getCurrentUserFullName() {
        return sessionManager != null && sessionManager.isLoggedIn() ? sessionManager.getFullName() : "زائر";
    }

    public String getCurrentUserRole() {
        return sessionManager != null && sessionManager.isLoggedIn() ? sessionManager.getRole() : "admin";
    }

    @Override
    public void onBackPressed() {
        if (DrawerNavigationHelper.onBackPressed(this)) {
            return;
        }
        super.onBackPressed();
    }
}
