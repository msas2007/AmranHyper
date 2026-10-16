package com.lap1.mystore.activities;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

import java.util.ArrayList;
import java.util.Locale;

public class SupplierDetailActivity extends AppCompatActivity {

    private TextView tvName, tvPhone, tvBalance;
    private ListView lvHistory;
    private DatabaseHelper dbHelper;

    private int supplierId;
    private double currentBalance = 0.0;
    private ArrayList<String> historyList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_supplier_detail);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        supplierId = getIntent().getIntExtra("SUPPLIER_ID", -1);
        if (supplierId == -1) { finish(); return; }

        tvName = findViewById(R.id.tvSupplierNameDetail);
        tvPhone = findViewById(R.id.tvSupplierPhoneDetail);
        tvBalance = findViewById(R.id.tvSupplierBalanceDetail);
        lvHistory = findViewById(R.id.lvSupplierHistory);
        Button btnPay = findViewById(R.id.btnPaySupplier);
        View btnBack = findViewById(R.id.btnSupplierDetailBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        dbHelper = new DatabaseHelper(this);
        historyList = new ArrayList<>();

        loadSupplierData();

        btnPay.setOnClickListener(v -> showPaymentDialog());

        // الشريط السفلي الثابت
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "");
    }

    private void loadSupplierData() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // 1. بيانات المورد
        Cursor cSup = db.rawQuery("SELECT name, phone, balance FROM suppliers WHERE id = ?", new String[]{String.valueOf(supplierId)});
        if (cSup.moveToFirst()) {
            tvName.setText(cSup.getString(0));
            tvPhone.setText("الهاتف: " + (cSup.getString(1) != null ? cSup.getString(1) : "--"));
            currentBalance = cSup.getDouble(2);
            tvBalance.setText(String.format(Locale.US, "%.2f ر.س", currentBalance));
        }
        cSup.close();

        // 2. سجل العمليات التحليلي للمورد بالترتيب الزمني مع الرصيد المتراكم
        historyList.clear();

        class SupplierTx {
            final int id;
            final String date;
            final double amountChange;
            final String displayText;

            SupplierTx(int id, String date, double amountChange, String displayText) {
                this.id = id;
                this.date = date != null ? date : "";
                this.amountChange = amountChange;
                this.displayText = displayText;
            }
        }

        ArrayList<SupplierTx> txList = new ArrayList<>();

        // أ. المشتريات (تزيد الدين المتبقي للمورد)
        Cursor cPurchases = db.rawQuery(
                "SELECT id, total, paid, remaining, purchase_date FROM purchases WHERE supplier_id = ? ORDER BY id ASC",
                new String[]{String.valueOf(supplierId)}
        );
        while (cPurchases.moveToNext()) {
            int purchId = cPurchases.getInt(0);
            double tot = cPurchases.getDouble(1);
            double paidVal = cPurchases.getDouble(2);
            double rem = cPurchases.getDouble(3);
            String pDate = cPurchases.getString(4);

            String text = "📦 فاتورة شراء #" + purchId + " (" + pDate + ")" +
                    "\nالإجمالي: " + String.format(Locale.US, "%.2f", tot) +
                    " | المدفوع: " + String.format(Locale.US, "%.2f", paidVal) +
                    " | الدين المتبقي: " + String.format(Locale.US, "%.2f", rem);

            txList.add(new SupplierTx(purchId, pDate, rem, text));
        }
        cPurchases.close();

        // ب. المدفوعات المسددة للمورد (تنقص الدين)
        Cursor cPayments = db.rawQuery(
                "SELECT id, amount, payment_date, notes FROM supplier_payments WHERE supplier_id = ? ORDER BY id ASC",
                new String[]{String.valueOf(supplierId)}
        );
        while (cPayments.moveToNext()) {
            int payId = cPayments.getInt(0);
            double amt = cPayments.getDouble(1);
            String payDate = cPayments.getString(2);
            String notes = cPayments.getString(3);

            String text = "💵 دفعة سداد للمورد: " + String.format(Locale.US, "%.2f ر.س", amt) +
                    "\nالتاريخ: " + payDate +
                    "\nملاحظات: " + (notes != null && !notes.isEmpty() ? notes : "تسديد حساب مورد");

            txList.add(new SupplierTx(payId, payDate, -amt, text));
        }
        cPayments.close();

        // ترتيب الحركات زمنيًا
        txList.sort((o1, o2) -> {
            int dateCmp = o1.date.compareTo(o2.date);
            if (dateCmp != 0) return dateCmp;
            return Integer.compare(o1.id, o2.id);
        });

        // حساب الرصيد المتراكم
        double runningBal = 0.0;
        for (SupplierTx tx : txList) {
            runningBal += tx.amountChange;
            if (runningBal < 0) runningBal = 0;
            String entry = tx.displayText + "\n📊 رصيد الدين المتبقي للمورد بعد الحركة: " + String.format(Locale.US, "%.2f ر.س", runningBal);
            historyList.add(entry);
        }

        if (historyList.isEmpty()) historyList.add("لا توجد عمليات مسجلة لهذا المورد حتى الآن.");

        lvHistory.setAdapter(new ArrayAdapter<>(this, R.layout.item_history_entry, R.id.tvHistoryText, historyList));
    }

    private void showPaymentDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_simple, null);
        EditText etAmount = view.findViewById(R.id.etSimpleName);
        EditText etNotes = view.findViewById(R.id.etSimplePhone);

        etAmount.setHint("المبلغ المدفوع");
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etNotes.setHint("ملاحظات (اختياري)");
        etNotes.setInputType(InputType.TYPE_CLASS_TEXT);

        new MaterialAlertDialogBuilder(this)
                .setTitle("تسجيل دفعة للمورد")
                .setView(view)
                .setPositiveButton("تأكيد الدفع", (dialog, which) -> {
                    String amtStr = etAmount.getText().toString().trim();
                    if (!amtStr.isEmpty()) {
                        try {
                            double amount = Double.parseDouble(amtStr);
                            if (amount <= 0) {
                                Toast.makeText(this, "المبلغ يجب أن يكون أكبر من الصفر", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (amount > currentBalance + 0.01) {
                                Toast.makeText(this, "المبلغ المدفوع أكبر من رصيد الدين للمورد!", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            processPayment(amount, etNotes.getText().toString().trim());
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "مبلغ غير صالح", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void processPayment(double amount, String notes) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // 1. تسجيل الدفعة
            ContentValues cv = new ContentValues();
            cv.put("supplier_id", supplierId);
            cv.put("amount", amount);
            cv.put("notes", notes);
            db.insert("supplier_payments", null, cv);

            // 2. تحديث رصيد المورد الإجمالي
            db.execSQL("UPDATE suppliers SET balance = balance - ? WHERE id = ?", new Object[]{amount, supplierId});

            // [FIX] 3. توزيع مبلغ الدفع على فواتير الشراء المستحقة (من الأقدم للأحدث)
            double remainingPayment = amount;
            Cursor cOutstanding = db.rawQuery(
                    "SELECT id, remaining FROM purchases WHERE supplier_id = ? AND remaining > 0.001 ORDER BY id ASC",
                    new String[]{String.valueOf(supplierId)}
            );
            while (cOutstanding.moveToNext() && remainingPayment > 0.001) {
                int purchId = cOutstanding.getInt(0);
                double purchRemaining = cOutstanding.getDouble(1);
                double toApply = Math.min(remainingPayment, purchRemaining);
                double newRemaining = purchRemaining - toApply;
                db.execSQL(
                        "UPDATE purchases SET remaining = ?, paid = paid + ? WHERE id = ?",
                        new Object[]{newRemaining, toApply, purchId}
                );
                remainingPayment -= toApply;
            }
            cOutstanding.close();

            db.setTransactionSuccessful();
            Toast.makeText(this, "تم تسجيل الدفعة بنجاح", Toast.LENGTH_SHORT).show();
            loadSupplierData();
        } catch (Exception e) {
            Toast.makeText(this, "خطأ: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            db.endTransaction();
        }
    }
}
