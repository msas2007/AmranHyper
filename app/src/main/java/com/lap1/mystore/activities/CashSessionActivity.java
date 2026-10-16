package com.lap1.mystore.activities;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.utils.SessionManager;

import java.util.Locale;

public class CashSessionActivity extends AppCompatActivity {

    private TextView tvSessionStatus, tvSessionInfo, tvExpectedCash;
    private Button btnOpenSession, btnCloseSession;
    private LinearLayout layoutHistory;
    private DatabaseHelper dbHelper;

    private long currentSessionId = -1;
    private double openingCash = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_session);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        tvSessionStatus = findViewById(R.id.tvSessionStatus);
        tvSessionInfo = findViewById(R.id.tvSessionInfo);
        tvExpectedCash = findViewById(R.id.tvExpectedCash);
        btnOpenSession = findViewById(R.id.btnOpenSession);
        btnCloseSession = findViewById(R.id.btnCloseSession);
        layoutHistory = findViewById(R.id.layoutSessionHistory);
        View btnBack = findViewById(R.id.btnSessionBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        dbHelper = new DatabaseHelper(this);

        if (btnOpenSession != null) btnOpenSession.setOnClickListener(v -> showOpenSessionDialog());
        if (btnCloseSession != null) btnCloseSession.setOnClickListener(v -> showCloseSessionDialog());

        loadSessionStatus();
        loadSessionHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSessionStatus();
        loadSessionHistory();
    }

    private void loadSessionStatus() {
        Cursor c = dbHelper.getOpenSession();
        try {
            if (c != null && c.moveToFirst()) {
                currentSessionId = c.getLong(c.getColumnIndexOrThrow("id"));
                openingCash = c.getDouble(c.getColumnIndexOrThrow("opening_cash"));
                String sessionNum = c.getString(c.getColumnIndexOrThrow("session_number"));
                String openedAt = c.getString(c.getColumnIndexOrThrow("opened_at"));
                int openedBy = c.getInt(c.getColumnIndexOrThrow("opened_by"));

                if (tvSessionStatus != null) {
                    tvSessionStatus.setText("🟢 وردية مفتوحة");
                    tvSessionStatus.setTextColor(ContextCompat.getColor(this, R.color.success));
                }
                if (tvSessionInfo != null) {
                    tvSessionInfo.setText("رقم الوردية: " + sessionNum +
                            "\nفتحت في: " + (openedAt != null ? openedAt.substring(0, Math.min(16, openedAt.length())) : "--") +
                            "\nالرصيد الافتتاحي: " + String.format(Locale.US, "%.2f", openingCash) + " ر.ي");
                }

                // Calculate expected cash in drawer since session opened
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                String openedAtStr = openedAt != null ? openedAt : "2000-01-01";
                double cashIn = dbHelper.getCashInSince(db, openedAtStr);
                double cashOut = dbHelper.getCashOutSince(db, openedAtStr);
                double expected = openingCash + cashIn - cashOut;
                if (tvExpectedCash != null) {
                    tvExpectedCash.setText("💰 الكاش المتوقع في الدرج: " + String.format(Locale.US, "%.2f", expected) + " ر.ي" +
                            "\n  (افتتاحي: " + String.format(Locale.US, "%.2f", openingCash) +
                            " + وارد: " + String.format(Locale.US, "%.2f", cashIn) +
                            " - صادر: " + String.format(Locale.US, "%.2f", cashOut) + ")");
                }

                if (btnOpenSession != null) btnOpenSession.setEnabled(false);
                if (btnCloseSession != null) btnCloseSession.setEnabled(true);
            } else {
                currentSessionId = -1;
                if (tvSessionStatus != null) {
                    tvSessionStatus.setText("🔴 لا توجد وردية مفتوحة");
                    tvSessionStatus.setTextColor(ContextCompat.getColor(this, R.color.danger));
                }
                if (tvSessionInfo != null) tvSessionInfo.setText("افتح وردية جديدة للبدء في تسجيل المعاملات");
                if (tvExpectedCash != null) tvExpectedCash.setText("");
                if (btnOpenSession != null) btnOpenSession.setEnabled(true);
                if (btnCloseSession != null) btnCloseSession.setEnabled(false);
            }
        } finally {
            if (c != null) c.close();
        }
    }

    private void showOpenSessionDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_open_session, null);
        EditText etOpening = dialogView != null ? dialogView.findViewById(R.id.etOpeningCashAmount) : null;

        new MaterialAlertDialogBuilder(this)
                .setTitle("🔓 فتح وردية جديدة")
                .setView(dialogView)
                .setPositiveButton("فتح الوردية", (d, w) -> {
                    double openAmt = 0;
                    if (etOpening != null) {
                        try { openAmt = Double.parseDouble(etOpening.getText().toString().trim()); }
                        catch (Exception ignored) {}
                    }
                    int userId = SessionManager.getInstance(this).getUserId();
                    long result = dbHelper.openCashSession(openAmt, userId);
                    if (result == -2) {
                        Toast.makeText(this, "⚠️ يوجد وردية مفتوحة بالفعل", Toast.LENGTH_LONG).show();
                    } else if (result > 0) {
                        Toast.makeText(this, "✅ تم فتح الوردية بنجاح", Toast.LENGTH_SHORT).show();
                        loadSessionStatus();
                        loadSessionHistory();
                    } else {
                        Toast.makeText(this, "❌ فشل في فتح الوردية", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showCloseSessionDialog() {
        if (currentSessionId < 0) return;

        // Calculate expected cash
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = dbHelper.getOpenSession();
        double expectedCash = openingCash;
        try {
            if (c != null && c.moveToFirst()) {
                String openedAt = c.getString(c.getColumnIndexOrThrow("opened_at"));
                if (openedAt != null) {
                    expectedCash = openingCash + dbHelper.getCashInSince(db, openedAt) - dbHelper.getCashOutSince(db, openedAt);
                }
            }
        } finally { if (c != null) c.close(); }

        final double expected = expectedCash;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_close_session, null);
        EditText etPhysical = dialogView != null ? dialogView.findViewById(R.id.etPhysicalCashClose) : null;
        EditText etNotes = dialogView != null ? dialogView.findViewById(R.id.etSessionCloseNotes) : null;
        TextView tvExp = dialogView != null ? dialogView.findViewById(R.id.tvExpectedCashClose) : null;
        if (tvExp != null) tvExp.setText("الكاش المتوقع: " + String.format(Locale.US, "%.2f", expected) + " ر.ي");

        new MaterialAlertDialogBuilder(this)
                .setTitle("🔒 إغلاق الوردية")
                .setView(dialogView)
                .setPositiveButton("إغلاق الوردية", (d, w) -> {
                    double physical = 0;
                    try { physical = Double.parseDouble(etPhysical != null ? etPhysical.getText().toString().trim() : "0"); }
                    catch (Exception ignored) {}
                    String notes = etNotes != null ? etNotes.getText().toString().trim() : "";
                    int userId = SessionManager.getInstance(this).getUserId();
                    boolean closed = dbHelper.closeCurrentSession(currentSessionId, physical, expected, userId, notes);
                    if (closed) {
                        double diff = physical - expected;
                        String msg = "✅ تم إغلاق الوردية\nالفرق: " + String.format(Locale.US, "%.2f", diff) + " ر.ي";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        loadSessionStatus();
                        loadSessionHistory();
                    } else {
                        Toast.makeText(this, "❌ فشل في إغلاق الوردية", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void loadSessionHistory() {
        if (layoutHistory == null) return;
        layoutHistory.removeAllViews();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT session_number, opening_cash, opened_at, closed_at, physical_cash, expected_cash, difference, status, notes FROM cash_sessions ORDER BY id DESC LIMIT 10",
                null);
        try {
            while (c.moveToNext()) {
                String num = c.getString(0);
                double opening = c.getDouble(1);
                String openedAt = c.getString(2);
                String closedAt = c.getString(3);
                double physical = c.getDouble(4);
                double expected = c.getDouble(5);
                double diff = c.getDouble(6);
                String status = c.getString(7);
                String notes = c.getString(8);

                android.widget.TextView tv = new android.widget.TextView(this);
                tv.setPadding(12, 10, 12, 10);
                tv.setTextSize(12f);
                boolean isOpen = "OPEN".equals(status);
                tv.setBackgroundColor(isOpen ?
                        ContextCompat.getColor(this, R.color.success) + 0x33000000 :
                        android.graphics.Color.TRANSPARENT);
                String txt = (isOpen ? "🟢 " : "🔒 ") + num +
                        " | افتتاحي: " + String.format(Locale.US, "%.2f", opening) +
                        " | " + (openedAt != null ? openedAt.substring(0, Math.min(16, openedAt.length())) : "--");
                if (!isOpen) {
                    txt += "\n   مادي: " + String.format(Locale.US, "%.2f", physical) +
                            " | متوقع: " + String.format(Locale.US, "%.2f", expected) +
                            " | فرق: " + String.format(Locale.US, "%.2f", diff);
                    if (notes != null && !notes.isEmpty()) txt += "\n   " + notes;
                }
                tv.setText(txt);
                tv.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                layoutHistory.addView(tv);

                View divider = new View(this);
                divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.stroke_color));
                layoutHistory.addView(divider);
            }
        } finally { c.close(); }

        if (layoutHistory.getChildCount() == 0) {
            android.widget.TextView empty = new android.widget.TextView(this);
            empty.setText("لا توجد ورديات مسجلة بعد");
            empty.setPadding(16, 24, 16, 24);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            layoutHistory.addView(empty);
        }
    }
}
