package com.lap1.mystore.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
    private CheckBox cbRememberMe;
    private DatabaseHelper dbHelper;

    private static final String PREFS_NAME = "MyStorePrefs";
    private static final String KEY_DARK_MODE = "is_dark_mode";
    private static final String KEY_REMEMBER_USER = "remember_username";
    private static final String KEY_REMEMBER_PASS = "remember_password";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        dbHelper = new DatabaseHelper(this);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        ImageButton btnToggleTheme = findViewById(R.id.btnToggleTheme);
        Button btnLogin = findViewById(R.id.btnLogin);

        // Pre-fill remembered user data if exists, else load current user from DB
        String savedUser = prefs.getString(KEY_REMEMBER_USER, "");
        String savedPass = prefs.getString(KEY_REMEMBER_PASS, "");
        if (!savedUser.isEmpty()) {
            etUsername.setText(savedUser);
            etPassword.setText(savedPass);
            cbRememberMe.setChecked(true);
        } else {
            // تحميل مستخدم القواعد التلقائي
            String[] user = dbHelper.getFirstUser();
            etUsername.setText(user[0]);
            etPassword.setText(user[1]);
        }

        btnToggleTheme.setOnClickListener(v -> {
            boolean currentDark = prefs.getBoolean(KEY_DARK_MODE, false);
            boolean newDark = !currentDark;
            prefs.edit().putBoolean(KEY_DARK_MODE, newDark).apply();

            if (newDark) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                Toast.makeText(this, "تم تفعيل الوضع الداكن", Toast.LENGTH_SHORT).show();
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                Toast.makeText(this, "تم تفعيل الوضع الفاتح", Toast.LENGTH_SHORT).show();
            }
        });

        btnLogin.setOnClickListener(v -> handleLogin());
    }

    private void handleLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty()) {
            etUsername.setError("يرجى إدخال اسم المستخدم");
            etUsername.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("يرجى إدخال كلمة المرور");
            etPassword.requestFocus();
            return;
        }

        // التحقق من قاعدة البيانات بشكل حقيقي
        boolean isValid = dbHelper.authenticateUser(username, password);
        if (!isValid) {
            Toast.makeText(this, "❌ اسم المستخدم أو كلمة المرور غير صحيحة", Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (cbRememberMe.isChecked()) {
            editor.putString(KEY_REMEMBER_USER, username);
            editor.putString(KEY_REMEMBER_PASS, password);
        } else {
            editor.remove(KEY_REMEMBER_USER);
            editor.remove(KEY_REMEMBER_PASS);
        }
        editor.apply();

        Toast.makeText(this, "✅ أهلاً بك " + username, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
        startActivity(intent);
        finish();
    }
}
