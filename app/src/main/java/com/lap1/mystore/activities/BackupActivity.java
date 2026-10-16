package com.lap1.mystore.activities;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.utils.BottomNavHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackupActivity extends AppCompatActivity {

    private static final String BACKUP_FILE_NAME = "mystore_backup.db";
    private static final String BACKUP_FOLDER = Environment.DIRECTORY_DOWNLOADS + "/MyStoreBackups/";

    private TextView tvBackupLocation;
    private TextView tvBackupStatus;
    private EditText etSettingsUsername;
    private EditText etSettingsPassword;

    private DatabaseHelper dbHelper;

    private static final String PREFS_NAME = "MyStorePrefs";
    private static final String KEY_DARK_MODE = "is_dark_mode";
    private static final String KEY_REMEMBER_USER = "remember_username";
    private static final String KEY_REMEMBER_PASS = "remember_password";

    private final ActivityResultLauncher<String> restoreFilePicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            restoreFromUri(uri);
                        } else {
                            Toast.makeText(this, "لم يتم اختيار أي ملف.", Toast.LENGTH_SHORT).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_backup);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        dbHelper = new DatabaseHelper(this);

        tvBackupLocation = findViewById(R.id.tvBackupLocation);
        tvBackupStatus = findViewById(R.id.tvBackupStatus);
        etSettingsUsername = findViewById(R.id.etSettingsUsername);
        etSettingsPassword = findViewById(R.id.etSettingsPassword);

        Button btnCreateBackup = findViewById(R.id.btnCreateBackup);
        Button btnRestoreBackup = findViewById(R.id.btnRestoreBackup);
        Button btnSaveUserCredentials = findViewById(R.id.btnSaveUserCredentials);
        Button btnSettingsToggleTheme = findViewById(R.id.btnSettingsToggleTheme);
        View btnBack = findViewById(R.id.btnBackupBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        tvBackupLocation.setText(
                "مكان النسخة الاحتياطية:\n" +
                        "Download/MyStoreBackups/" +
                        BACKUP_FILE_NAME
        );

        // تحميل بيانات الحساب الحالي لعرضها في الحقول
        String[] currentUser = dbHelper.getFirstUser();
        etSettingsUsername.setText(currentUser[0]);
        etSettingsPassword.setText(currentUser[1]);

        // حفظ بيانات المستخدم الجديدة (اسم المستخدم وكلمة المرور)
        btnSaveUserCredentials.setOnClickListener(v -> saveNewCredentials(currentUser[0]));

        // تبديل الوضع الداكن / الفاتح
        btnSettingsToggleTheme.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
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

        btnCreateBackup.setOnClickListener(v -> createBackup());
        btnRestoreBackup.setOnClickListener(v -> restoreBackup());

        // الشريط السفلي الثابت
        BottomNavHelper.setupBottomNav(this, "more");
    }

    private void saveNewCredentials(String oldUsername) {
        String newUsername = etSettingsUsername.getText().toString().trim();
        String newPassword = etSettingsPassword.getText().toString().trim();

        if (newUsername.isEmpty()) {
            etSettingsUsername.setError("أدخل اسم المستخدم");
            etSettingsUsername.requestFocus();
            return;
        }

        if (newPassword.isEmpty()) {
            etSettingsPassword.setError("أدخل كلمة المرور الجديدة");
            etSettingsPassword.requestFocus();
            return;
        }

        boolean success = dbHelper.updateUserCredentials(oldUsername, newUsername, newPassword);
        if (success) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            if (prefs.contains(KEY_REMEMBER_USER)) {
                prefs.edit()
                        .putString(KEY_REMEMBER_USER, newUsername)
                        .putString(KEY_REMEMBER_PASS, newPassword)
                        .apply();
            }
            Toast.makeText(this, "✅ تم حفظ بيانات الحساب الجديدة بنجاح!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "❌ فشل حفظ البيانات الجديدة", Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================
    // النسخ الاحتياطي
    // =========================================================

    private void createBackup() {
        try {
            prepareDatabaseForBackup();
            File dbFile = getDatabasePath("mystore.db");

            if (!dbFile.exists()) {
                Toast.makeText(this, "لم يتم العثور على قاعدة البيانات mystore.db", Toast.LENGTH_LONG).show();
                tvBackupStatus.setText("❌ قاعدة البيانات غير موجودة.");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveBackupUsingMediaStore(dbFile);
            } else {
                saveBackupForOldAndroid(dbFile);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ أثناء النسخ الاحتياطي:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
            tvBackupStatus.setText("❌ فشل النسخ الاحتياطي:\n" + e.getMessage());
        }
    }

    private void prepareDatabaseForBackup() {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            try {
                Cursor cursor = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null);
                cursor.close();
            } catch (Exception ignored) {}
            dbHelper.close();
        } catch (Exception e) {
            try { dbHelper.close(); } catch (Exception ignored) {}
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void saveBackupUsingMediaStore(File dbFile) throws Exception {
        ContentResolver resolver = getContentResolver();
        Uri downloadsUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

        Uri oldBackupUri = findBackupUri();
        if (oldBackupUri != null) {
            try { resolver.delete(oldBackupUri, null, null); } catch (Exception ignored) {}
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, BACKUP_FILE_NAME);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
        values.put(MediaStore.Downloads.RELATIVE_PATH, BACKUP_FOLDER);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri backupUri = resolver.insert(downloadsUri, values);
        if (backupUri == null) {
            throw new Exception("تعذر إنشاء ملف النسخة الاحتياطية في Download.");
        }

        try {
            try (InputStream input = new FileInputStream(dbFile);
                 OutputStream output = resolver.openOutputStream(backupUri, "w")) {
                if (output == null) {
                    throw new Exception("تعذر فتح ملف النسخة الاحتياطية للكتابة.");
                }
                copyStream(input, output);
            }

            ContentValues publishValues = new ContentValues();
            publishValues.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(backupUri, publishValues, null, null);

            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            Toast.makeText(this, "✅ تم إنشاء النسخة الاحتياطية بنجاح!", Toast.LENGTH_LONG).show();
            tvBackupStatus.setText("✅ تمت عملية النسخ الاحتياطي بنجاح.\n\nالتاريخ: " + currentTime + "\n\nالمكان:\nDownload/MyStoreBackups/" + BACKUP_FILE_NAME);

        } catch (Exception e) {
            try { resolver.delete(backupUri, null, null); } catch (Exception ignored) {}
            throw e;
        }
    }

    private void saveBackupForOldAndroid(File dbFile) throws Exception {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File backupFolder = new File(downloads, "MyStoreBackups");

        if (!backupFolder.exists()) {
            if (!backupFolder.mkdirs() && !backupFolder.exists()) {
                throw new Exception("تعذر إنشاء مجلد النسخ الاحتياطي.");
            }
        }

        File backupFile = new File(backupFolder, BACKUP_FILE_NAME);
        copyFile(dbFile, backupFile);

        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        Toast.makeText(this, "✅ تم إنشاء النسخة الاحتياطية بنجاح!", Toast.LENGTH_LONG).show();
        tvBackupStatus.setText("✅ تمت عملية النسخ الاحتياطي بنجاح.\n\nالتاريخ: " + currentTime + "\n\nالمكان:\n" + backupFile.getAbsolutePath());
    }

    private Uri findBackupUri() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null;
        }

        ContentResolver resolver = getContentResolver();
        Uri downloadsUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

        String[] projection = {
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.RELATIVE_PATH
        };

        String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?";
        String[] selectionArgs = { BACKUP_FILE_NAME, BACKUP_FOLDER };

        try (Cursor cursor = resolver.query(downloadsUri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                return ContentUris.withAppendedId(downloadsUri, id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private void restoreBackup() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Uri backupUri = findBackupUri();
                if (backupUri != null) {
                    restoreFromUri(backupUri);
                } else {
                    openBackupPicker();
                }
            } else {
                File backupFile = getOldAndroidBackupFile();
                if (backupFile != null && backupFile.exists()) {
                    restoreFromFile(backupFile);
                } else {
                    Toast.makeText(this, "لم يتم العثور على النسخة الاحتياطية.", Toast.LENGTH_LONG).show();
                    openBackupPicker();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ أثناء الاستعادة:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
            tvBackupStatus.setText("❌ فشل الاستعادة:\n" + e.getMessage());
        }
    }

    private void openBackupPicker() {
        restoreFilePicker.launch("*/*");
    }

    private void restoreFromUri(Uri backupUri) {
        File tempFile = new File(getCacheDir(), "restore_temp.db");
        try {
            tvBackupStatus.setText("⏳ جارٍ قراءة وتحليل النسخة الاحتياطية...");
            try (InputStream input = getContentResolver().openInputStream(backupUri);
                 OutputStream output = new FileOutputStream(tempFile)) {
                if (input == null) {
                    throw new Exception("تعذر قراءة ملف النسخة الاحتياطية.");
                }
                copyStream(input, output);
            }

            validateSQLiteDatabase(tempFile);

            String fileDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(tempFile.lastModified()));
            long fileSizeKb = tempFile.length() / 1024;

            new MaterialAlertDialogBuilder(this)
                    .setTitle("⚠️ تأكيد استعادة النسخة الاحتياطية")
                    .setMessage("سيتم استبدال جميع البيانات الحالية بالبيانات المتاحة في هذه النسخة الاحتياطية.\n\n" +
                            "📅 تاريخ النسخة: " + fileDate + "\n" +
                            "📦 حجم الملف: " + fileSizeKb + " كيلوبايت\n\n" +
                            "🛡️ سيقوم النظام بإنشاء نقطة استرجاع أمان لقاعدة البيانات الحالية تلقائياً قبل البدء.\n\nهل أنت متأكد من المتابعة؟")
                    .setPositiveButton("تأكيد الاستعادة", (dialog, which) -> executeDatabaseRestore(tempFile))
                    .setNegativeButton("إلغاء", (dialog, which) -> tempFile.delete())
                    .setCancelable(false)
                    .show();

        } catch (Exception e) {
            e.printStackTrace();
            tempFile.delete();
            Toast.makeText(this, "❌ فشلت الاستعادة:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
            tvBackupStatus.setText("❌ فشل استعادة البيانات:\n" + e.getMessage());
        }
    }

    private void restoreFromFile(File backupFile) throws Exception {
        validateSQLiteDatabase(backupFile);

        String fileDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(backupFile.lastModified()));
        long fileSizeKb = backupFile.length() / 1024;

        new MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ تأكيد استعادة النسخة الاحتياطية")
                .setMessage("سيتم استبدال جميع البيانات الحالية بالبيانات المتاحة في هذه النسخة الاحتياطية.\n\n" +
                        "📅 تاريخ النسخة: " + fileDate + "\n" +
                        "📦 حجم الملف: " + fileSizeKb + " كيلوبايت\n\n" +
                        "🛡️ سيقوم النظام بإنشاء نقطة استرجاع أمان لقاعدة البيانات الحالية تلقائياً قبل البدء.\n\nهل أنت متأكد من المتابعة؟")
                .setPositiveButton("تأكيد الاستعادة", (dialog, which) -> executeDatabaseRestore(backupFile))
                .setNegativeButton("إلغاء", null)
                .setCancelable(false)
                .show();
    }

    private void executeDatabaseRestore(File sourceBackupFile) {
        try {
            // 1. إنشاء نقطة استرجاع أمان قبل التغيير
            File currentDbFile = getDatabasePath("mystore.db");
            if (currentDbFile.exists()) {
                prepareDatabaseForBackup();
                File safetyBackup = new File(currentDbFile.getParent(), "mystore_pre_restore_safety.db");
                copyFile(currentDbFile, safetyBackup);
            }

            try { dbHelper.close(); } catch (Exception ignored) {}

            // 2. استبدال قاعدة البيانات
            deleteSQLiteSideFiles(currentDbFile);
            copyFile(sourceBackupFile, currentDbFile);
            if (sourceBackupFile.getParent() != null && sourceBackupFile.getParent().contains("cache")) {
                sourceBackupFile.delete();
            }

            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            Toast.makeText(this, "🎉 تم استعادة البيانات بنجاح! تم حفظ نقطة أمان سابقة تلقائياً.", Toast.LENGTH_LONG).show();
            tvBackupStatus.setText("🎉 تمت استعادة البيانات بنجاح.\n\nوقت الاستعادة: " + currentTime + "\n\n🛡️ تم حفظ نقطة أمان سابقة باسم (mystore_pre_restore_safety.db).");

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "❌ فشلت عملية الاستعادة:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
            tvBackupStatus.setText("❌ فشل استعادة البيانات:\n" + e.getMessage());
        }
    }

    private File getOldAndroidBackupFile() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloads, "MyStoreBackups/" + BACKUP_FILE_NAME);
    }

    private void validateSQLiteDatabase(File file) throws Exception {
        if (!file.exists()) {
            throw new Exception("ملف النسخة الاحتياطية غير موجود.");
        }
        if (file.length() == 0) {
            throw new Exception("ملف النسخة الاحتياطية فارغ.");
        }

        SQLiteDatabase testDatabase = null;
        try {
            testDatabase = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        } catch (Exception e) {
            throw new Exception("الملف المحدد ليس قاعدة بيانات SQLite صحيحة.");
        } finally {
            if (testDatabase != null) {
                testDatabase.close();
            }
        }
    }

    private void deleteSQLiteSideFiles(File dbFile) {
        File walFile = new File(dbFile.getAbsolutePath() + "-wal");
        File shmFile = new File(dbFile.getAbsolutePath() + "-shm");
        File journalFile = new File(dbFile.getAbsolutePath() + "-journal");

        if (dbFile.exists()) dbFile.delete();
        if (walFile.exists()) walFile.delete();
        if (shmFile.exists()) shmFile.delete();
        if (journalFile.exists()) journalFile.delete();
    }

    private void copyStream(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        output.flush();
    }

    private void copyFile(File source, File destination) throws Exception {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            copyStream(input, output);
        }
    }
}
