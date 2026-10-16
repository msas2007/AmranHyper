package com.lap1.mystore.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lap1.mystore.MyStoreApp;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.models.StoreSettings;
import com.lap1.mystore.utils.BottomNavHelper;

public class SettingsActivity extends AppCompatActivity {

    private EditText etStoreName, etAddress, etPhone, etCurrency, etInvoiceFooter, etExpiryDays;
    private RadioGroup rgTheme;
    private RadioButton rbLight, rbDark, rbSystem;
    private DatabaseHelper dbHelper;

    public static final String PREF_EXPIRY_DAYS = "pref_expiry_alert_days";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        etStoreName = findViewById(R.id.etSettingsStoreName);
        etAddress = findViewById(R.id.etSettingsAddress);
        etPhone = findViewById(R.id.etSettingsPhone);
        etCurrency = findViewById(R.id.etSettingsCurrency);
        etInvoiceFooter = findViewById(R.id.etSettingsInvoiceFooter);
        etExpiryDays = findViewById(R.id.etSettingsExpiryDays);

        rgTheme = findViewById(R.id.rgSettingsTheme);
        rbLight = findViewById(R.id.rbThemeLight);
        rbDark = findViewById(R.id.rbThemeDark);
        rbSystem = findViewById(R.id.rbThemeSystem);

        Button btnSave = findViewById(R.id.btnSaveSettings);
        ImageButton btnBack = findViewById(R.id.btnSettingsBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        dbHelper = new DatabaseHelper(this);

        loadSettings();

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveSettings());
        }

        BottomNavHelper.setupBottomNav(this, "more");
    }

    private void loadSettings() {
        StoreSettings s = dbHelper.getStoreSettings();
        if (s != null) {
            if (etStoreName != null) etStoreName.setText(s.getStoreName());
            if (etAddress != null) etAddress.setText(s.getAddress());
            if (etPhone != null) etPhone.setText(s.getPhone());
            if (etCurrency != null) etCurrency.setText(s.getCurrency());
            if (etInvoiceFooter != null) etInvoiceFooter.setText(s.getInvoiceFooter());
        }

        SharedPreferences prefs = getSharedPreferences(MyStoreApp.PREFS_NAME, MODE_PRIVATE);
        String theme = prefs.getString(MyStoreApp.KEY_THEME_MODE, "system");
        if ("light".equals(theme)) {
            if (rbLight != null) rbLight.setChecked(true);
        } else if ("dark".equals(theme)) {
            if (rbDark != null) rbDark.setChecked(true);
        } else {
            if (rbSystem != null) rbSystem.setChecked(true);
        }

        int expDays = prefs.getInt(PREF_EXPIRY_DAYS, 30);
        if (etExpiryDays != null) etExpiryDays.setText(String.valueOf(expDays));
    }

    private void saveSettings() {
        String storeName = (etStoreName != null) ? etStoreName.getText().toString().trim() : "عمران هايبر";
        String address = (etAddress != null) ? etAddress.getText().toString().trim() : "";
        String phone = (etPhone != null) ? etPhone.getText().toString().trim() : "";
        String currency = (etCurrency != null) ? etCurrency.getText().toString().trim() : "ر.ي";
        String footer = (etInvoiceFooter != null) ? etInvoiceFooter.getText().toString().trim() : "";

        if (storeName.isEmpty()) storeName = "عمران هايبر";
        if (currency.isEmpty()) currency = "ر.ي";

        boolean updated = dbHelper.updateStoreSettings(storeName, address, phone, currency, footer);

        SharedPreferences prefs = getSharedPreferences(MyStoreApp.PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        String selectedTheme = "system";
        if (rbLight != null && rbLight.isChecked()) selectedTheme = "light";
        else if (rbDark != null && rbDark.isChecked()) selectedTheme = "dark";

        editor.putString(MyStoreApp.KEY_THEME_MODE, selectedTheme);

        if (etExpiryDays != null) {
            try {
                int expDays = Integer.parseInt(etExpiryDays.getText().toString().trim());
                editor.putInt(PREF_EXPIRY_DAYS, Math.max(1, expDays));
            } catch (Exception ignored) {}
        }
        editor.apply();

        ((MyStoreApp) getApplication()).applyAppTheme();

        if (updated) {
            Toast.makeText(this, "✅ تم حفظ جميع الإعدادات بنجاح!", Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(this, "❌ حدث خطأ أثناء حفظ الإعدادات", Toast.LENGTH_SHORT).show();
        }
    }
}
