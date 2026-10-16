package com.lap1.mystore.activities;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

import java.util.ArrayList;
import java.util.Locale;

public class ClosingActivity extends AppCompatActivity {

    private TextView tvSysCash, tvSysNetwork, tvSysCredit, tvSysTotal, tvDifference, tvStatusLabel;
    private EditText etPhysicalCash;
    private LinearLayout layoutResult;
    private ListView lvClosings;
    private DatabaseHelper dbHelper;

    private double systemCashTotal = 0;
    private double systemNetworkTotal = 0;
    private double systemCreditTotal = 0;
    private double totalSystemRevenue = 0;
    private double expectedCashInDrawer = 0;

    // [FIX] متغيرات إضافية للحسابات الكاملة
    private double debtCollectedToday = 0;
    private double supplierPaidToday = 0;

    private ArrayList<String> closingHistory;
    private ArrayAdapter<String> historyAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_closing);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        tvSysCash = findViewById(R.id.tvSystemCash);
        tvSysNetwork = findViewById(R.id.tvSystemNetwork);
        tvSysCredit = findViewById(R.id.tvSystemCredit);
        tvSysTotal = findViewById(R.id.tvSystemTotal);
        tvDifference = findViewById(R.id.tvDifference);
        tvStatusLabel = findViewById(R.id.tvStatusLabel);
        etPhysicalCash = findViewById(R.id.etPhysicalCash);
        layoutResult = findViewById(R.id.layoutClosingResult);
        lvClosings = findViewById(R.id.lvClosings);
        Button btnCalculate = findViewById(R.id.btnCalculateClosing);
        View btnBack = findViewById(R.id.btnClosingBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        dbHelper = new DatabaseHelper(this);
        closingHistory = new ArrayList<>();
        historyAdapter = new ArrayAdapter<>(this, R.layout.item_history_entry, R.id.tvHistoryText, closingHistory);
        lvClosings.setAdapter(historyAdapter);

        loadTodaySystemTotals();
        loadClosingHistory();

        btnCalculate.setOnClickListener(v -> calculateAndSaveClosing());

        // الشريط السفلي الثابت
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "more");
    }

    private void loadTodaySystemTotals() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // 1. مبيعات كاش اليوم (المدفوع في فواتير الكاش)
        Cursor cCash = db.rawQuery("SELECT COALESCE(SUM(paid), 0) FROM sales WHERE date(sale_date, 'localtime') = date('now', 'localtime') AND payment_method = 'كاش'", null);
        if (cCash.moveToFirst()) systemCashTotal = cCash.getDouble(0);
        cCash.close();

        // مبيعات شبكة اليوم
        Cursor cNet = db.rawQuery("SELECT COALESCE(SUM(paid), 0) FROM sales WHERE date(sale_date, 'localtime') = date('now', 'localtime') AND payment_method = 'شبكة'", null);
        if (cNet.moveToFirst()) systemNetworkTotal = cNet.getDouble(0);
        cNet.close();

        // إجمالي مبيعات اليوم (صافي شامل الآجل)
        Cursor cTotal = db.rawQuery("SELECT COALESCE(SUM(total - discount), 0) FROM sales WHERE date(sale_date, 'localtime') = date('now', 'localtime')", null);
        if (cTotal.moveToFirst()) totalSystemRevenue = cTotal.getDouble(0);
        cTotal.close();

        // الآجل = إجمالي المبيعات - المقبوض كاش - المقبوض شبكة
        systemCreditTotal = totalSystemRevenue - (systemCashTotal + systemNetworkTotal);
        if (systemCreditTotal < 0) systemCreditTotal = 0;

        // المصروفات اليومية
        Cursor cExp = db.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE date(expense_date, 'localtime') = date('now', 'localtime')", null);
        double totalExpenses = 0;
        if (cExp.moveToFirst()) totalExpenses = cExp.getDouble(0);
        cExp.close();

        // المرتجعات النقدية (المبالغ المُعادة نقداً فقط، بعد خصم ما أُزيل من الديون)
        Cursor cRet = db.rawQuery("SELECT COALESCE(SUM(amount - debt_reduction), 0) FROM returns WHERE date(return_date, 'localtime') = date('now', 'localtime')", null);
        double totalCashReturns = 0;
        if (cRet.moveToFirst()) totalCashReturns = cRet.getDouble(0);
        cRet.close();

        // [FIX] مقبوضات ديون العملاء النقدية اليوم (من جدول payments)
        debtCollectedToday = 0;
        Cursor cDebtPay = db.rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE date(payment_date, 'localtime') = date('now', 'localtime')",
                null
        );
        if (cDebtPay.moveToFirst()) debtCollectedToday = cDebtPay.getDouble(0);
        cDebtPay.close();

        // [FIX] المبالغ المدفوعة للموردين نقداً اليوم (تخرج من الصندوق)
        supplierPaidToday = 0;
        Cursor cSupPay = db.rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM supplier_payments WHERE date(payment_date, 'localtime') = date('now', 'localtime')",
                null
        );
        if (cSupPay.moveToFirst()) supplierPaidToday = cSupPay.getDouble(0);
        cSupPay.close();

        // [FIX] النقدية المتوقعة الصحيحة في الدرج:
        // = مبيعات كاش + مقبوضات ديون العملاء - مصروفات - مرتجعات نقدية - مدفوعات للموردين
        expectedCashInDrawer = systemCashTotal + debtCollectedToday - totalExpenses - totalCashReturns - supplierPaidToday;

        tvSysCash.setText(String.format(Locale.US,
                "💰 الكاش المتوقع في الدرج: %.2f\n   (مبيعات كاش: %.2f + ديون محصلة: %.2f - مصاريف: %.2f - مرتجعات: %.2f - مدفوع للمورد: %.2f)",
                expectedCashInDrawer, systemCashTotal, debtCollectedToday, totalExpenses, totalCashReturns, supplierPaidToday));
        tvSysNetwork.setText(String.format(Locale.US, "📱 إجمالي الشبكة: %.2f", systemNetworkTotal));
        tvSysCredit.setText(String.format(Locale.US, "⏳ إجمالي الآجل (ديون اليوم): %.2f", systemCreditTotal));
        tvSysTotal.setText(String.format(Locale.US, "🧾 إجمالي مبيعات اليوم (شامل الآجل): %.2f", totalSystemRevenue));
    }

    private void calculateAndSaveClosing() {
        String physicalStr = etPhysicalCash.getText().toString().trim();
        if (physicalStr.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال المبلغ الموجود في الدرج", Toast.LENGTH_SHORT).show();
            return;
        }

        double physicalCash;
        try {
            physicalCash = Double.parseDouble(physicalStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "مبلغ غير صالح", Toast.LENGTH_SHORT).show();
            return;
        }

        double difference = physicalCash - expectedCashInDrawer;

        layoutResult.setVisibility(View.VISIBLE);
        tvDifference.setText("الفرق (عجز/زيادة): " + String.format(Locale.US, "%.2f", difference));

        String status;
        if (Math.abs(difference) < 0.01) {
            status = "مطابقة تامة ✅";
            tvStatusLabel.setTextColor(getResources().getColor(R.color.success));
        } else if (difference > 0) {
            status = "زيادة نقدية في الدرج 📈";
            tvStatusLabel.setTextColor(getResources().getColor(R.color.primary));
        } else {
            status = "عجز في الدرج ⚠️";
            tvStatusLabel.setTextColor(getResources().getColor(R.color.danger));
        }
        tvStatusLabel.setText("حالة الصندوق: " + status);

        // حفظ التقفيل مع ملاحظات تفصيلية
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("physical_cash", physicalCash);
        cv.put("system_cash", expectedCashInDrawer);
        cv.put("system_network", systemNetworkTotal);
        cv.put("system_credit", systemCreditTotal);
        cv.put("difference", difference);
        cv.put("notes", status + String.format(Locale.US,
                " | كاش مبيعات: %.2f + ديون: %.2f - مصاريف - مدفوع للمورد: %.2f",
                systemCashTotal, debtCollectedToday, supplierPaidToday));
        db.insert("daily_closings", null, cv);

        Toast.makeText(this, "تم اعتماد التقفيل وحفظه في السجل بنجاح", Toast.LENGTH_SHORT).show();
        loadClosingHistory();
    }

    private void loadClosingHistory() {
        closingHistory.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT closing_date, physical_cash, system_cash, difference, notes FROM daily_closings ORDER BY id DESC", null);

        while (cursor.moveToNext()) {
            String date = cursor.getString(0);
            double phys = cursor.getDouble(1);
            double sys = cursor.getDouble(2);
            double diff = cursor.getDouble(3);
            String note = cursor.getString(4);

            closingHistory.add("📅 تاريخ التقفيل: " + date +
                    "\n💵 الفعلي في الدرج: " + String.format(Locale.US, "%.2f", phys) + " ر.س" +
                    " | النظام: " + String.format(Locale.US, "%.2f", sys) + " ر.س" +
                    "\nالفرق: " + String.format(Locale.US, "%.2f", diff) + " ر.س (" + note + ")");
        }
        cursor.close();
        historyAdapter.notifyDataSetChanged();
    }
}
