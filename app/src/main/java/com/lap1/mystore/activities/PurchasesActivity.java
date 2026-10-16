package com.lap1.mystore.activities;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

import java.util.ArrayList;

public class PurchasesActivity extends AppCompatActivity {

    private EditText etPurchaseBarcode, etPurchaseQty, etPurchaseUnitPrice, etPurchaseExpiry, etPurchasePaid;
    private TextView tvPurchaseResult, tvPurchaseRemaining;
    private Spinner spSupplier, spPurchaseUnit;
    private DatabaseHelper dbHelper;

    private ArrayList<String> supplierNames;
    private ArrayList<Integer> supplierIds;
    private ArrayAdapter<String> supplierAdapter;

    private ArrayList<String> unitOptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchases);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        etPurchaseBarcode = findViewById(R.id.etPurchaseBarcode);
        etPurchaseQty = findViewById(R.id.etPurchaseQty);
        etPurchaseUnitPrice = findViewById(R.id.etPurchaseUnitPrice);
        etPurchaseExpiry = findViewById(R.id.etPurchaseExpiry);
        etPurchasePaid = findViewById(R.id.etPurchasePaid);
        tvPurchaseResult = findViewById(R.id.tvPurchaseResult);
        tvPurchaseRemaining = findViewById(R.id.tvPurchaseRemaining);

        spSupplier = findViewById(R.id.spSupplier);
        spPurchaseUnit = findViewById(R.id.spPurchaseUnit);
        Button btnAddSupplier = findViewById(R.id.btnAddSupplier);

        Button btnSavePurchase = findViewById(R.id.btnSavePurchase);
        Button btnScanBarcode = findViewById(R.id.btnScanPurchaseBarcode);
        View btnBack = findViewById(R.id.btnPurchasesBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        dbHelper = new DatabaseHelper(this);

        loadSuppliers();
        loadUnitOptions();

        btnAddSupplier.setOnClickListener(v -> showAddSupplierDialog());

        btnScanBarcode.setOnClickListener(v -> {

            GmsBarcodeScannerOptions options =
                    new GmsBarcodeScannerOptions.Builder()
                            .enableAutoZoom()
                            .build();

            GmsBarcodeScanner scanner =
                    GmsBarcodeScanning.getClient(this, options);

            scanner.startScan()
                    .addOnSuccessListener(barcode -> {

                        String code = barcode.getRawValue();

                        if (code != null && !code.isEmpty()) {

                            etPurchaseBarcode.setText(code);

                            Toast.makeText(
                                    PurchasesActivity.this,
                                    "تم مسح الباركود: " + code,
                                    Toast.LENGTH_SHORT
                            ).show();

                        } else {

                            Toast.makeText(
                                    PurchasesActivity.this,
                                    "تعذر قراءة الباركود",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }

                    })
                    .addOnCanceledListener(() -> {

                        Toast.makeText(
                                PurchasesActivity.this,
                                "تم إلغاء عملية المسح",
                                Toast.LENGTH_SHORT
                        ).show();

                    })
                    .addOnFailureListener(e -> {

                        Toast.makeText(
                                PurchasesActivity.this,
                                "فشل تشغيل ماسح الباركود: " +
                                        e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    });
        });
        btnSavePurchase.setOnClickListener(v -> processPurchase());

        // الشريط السفلي الثابت
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "");
    }

    private void loadSuppliers() {
        supplierNames = new ArrayList<>();
        supplierIds = new ArrayList<>();

        supplierNames.add("-- اختر المورد --");
        supplierIds.add(-1);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id, name FROM suppliers ORDER BY name", null);
        while (cursor.moveToNext()) {
            supplierIds.add(cursor.getInt(0));
            supplierNames.add(cursor.getString(1));
        }
        cursor.close();

        supplierAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, supplierNames);
        supplierAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSupplier.setAdapter(supplierAdapter);
    }

    private void loadUnitOptions() {
        unitOptions = new ArrayList<>();
        unitOptions.add("حبة (افتراضي)");
        unitOptions.add("كرتون / درزن (بالجملة)");

        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, unitOptions);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPurchaseUnit.setAdapter(unitAdapter);
    }

    private void showAddSupplierDialog() {
        EditText etName = new EditText(this);
        etName.setHint("اسم المورد الجديد");
        EditText etPhone = new EditText(this);
        etPhone.setHint("رقم الهاتف (اختياري)");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);
        layout.addView(etName);
        layout.addView(etPhone);

        new MaterialAlertDialogBuilder(this)
                .setTitle("إضافة مورد جديد")
                .setView(layout)
                .setPositiveButton("إضافة", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        SQLiteDatabase db = dbHelper.getWritableDatabase();
                        ContentValues cv = new ContentValues();
                        cv.put("name", name);
                        cv.put("phone", etPhone.getText().toString().trim());
                        db.insert("suppliers", null, cv);
                        loadSuppliers();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void processPurchase() {
        int supplierIndex = spSupplier.getSelectedItemPosition();
        int supplierId = supplierIds.get(supplierIndex);
        String supplierName = supplierNames.get(supplierIndex);

        String productQuery = etPurchaseBarcode.getText().toString().trim();
        String qtyStr = etPurchaseQty.getText().toString().trim();
        String priceStr = etPurchaseUnitPrice.getText().toString().trim();
        String newExpiry = etPurchaseExpiry.getText().toString().trim();
        String paidStr = etPurchasePaid.getText().toString().trim();

        if (productQuery.isEmpty() || qtyStr.isEmpty() || priceStr.isEmpty()) {
            Toast.makeText(this, "الرجاء تعبئة بيانات المنتج والكمية والسعر", Toast.LENGTH_SHORT).show();
            return;
        }

        double qty;
        double unitPrice;
        double paid;
        try {
            qty = Double.parseDouble(qtyStr);
            unitPrice = Double.parseDouble(priceStr);
            paid = paidStr.isEmpty() ? 0.0 : Double.parseDouble(paidStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "القيم المُدخلة غير صالحة، تأكد من إدخال أرقام صحيحة", Toast.LENGTH_SHORT).show();
            return;
        }

        // [FIX] التحقق من صحة القيم المُدخلة لمنع تضخم التكاليف
        if (qty <= 0) {
            Toast.makeText(this, "الكمية يجب أن تكون أكبر من الصفر", Toast.LENGTH_SHORT).show();
            return;
        }
        if (unitPrice <= 0) {
            Toast.makeText(this, "سعر شراء الوحدة يجب أن يكون أكبر من الصفر", Toast.LENGTH_SHORT).show();
            return;
        }
        if (paid < 0) {
            Toast.makeText(this, "المبلغ المدفوع لا يمكن أن يكون سالباً", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        Cursor cursor = db.rawQuery("SELECT id, quantity, unit_factor FROM products WHERE name = ? OR barcode = ?", new String[]{productQuery, productQuery});
        if (!cursor.moveToFirst()) {
            Toast.makeText(this, "المنتج غير موجود! يرجى إضافته أولاً", Toast.LENGTH_LONG).show();
            cursor.close();
            return;
        }

        int productId = cursor.getInt(0);
        double currentStock = cursor.getDouble(1);
        double unitFactor = cursor.getDouble(2);
        cursor.close();

        // تحويل الكمية والأسعار إذا تم اختيار كرتون/جملة
        boolean isWholesale = spPurchaseUnit.getSelectedItemPosition() == 1;
        double actualQtyToAdd = isWholesale ? (qty * unitFactor) : qty;
        // [FIX] السعر المُدخل دائماً هو سعر الوحدة الواحدة (حبة)
        // في حالة الجملة: المستخدم يُدخل سعر الوحدة (حبة) أيضاً ثم نحسب التكلفة الكلية
        double costPerPiece = isWholesale ? (unitPrice / unitFactor) : unitPrice;

        double total = qty * unitPrice; // الإجمالي المالي يعتمد على ما دفعه للمورد فعلياً
        double remaining = total - paid;

        if (paid > total) {
            Toast.makeText(this, "المبلغ المدفوع أكبر من إجمالي الفاتورة!", Toast.LENGTH_SHORT).show();
            return;
        }

        db.beginTransaction();
        try {
            // تسجيل الفاتورة وتفاصيل العناصر
            ContentValues cvPurchase = new ContentValues();
            cvPurchase.put("supplier_id", supplierId != -1 ? supplierId : null);
            cvPurchase.put("supplier_name", supplierId == -1 ? "مورد عام" : supplierName);
            cvPurchase.put("total", total);
            cvPurchase.put("paid", paid);
            cvPurchase.put("remaining", remaining);
            long purchaseId = db.insert("purchases", null, cvPurchase);

            ContentValues cvItem = new ContentValues();
            cvItem.put("purchase_id", purchaseId);
            cvItem.put("product_id", productId);
            cvItem.put("quantity", actualQtyToAdd);
            cvItem.put("unit_price", costPerPiece); // نخزن تكلفة الحبة الواحدة للتقارير
            cvItem.put("total", total);
            db.insert("purchase_items", null, cvItem);

            // تحديث رصيد المورد
            if (supplierId > 0 && remaining > 0) {
                db.execSQL("UPDATE suppliers SET balance = balance + ? WHERE id = ?", new Object[]{remaining, supplierId});
            }

            // تحديث المخزون وتكلفة الشراء
            double newStock = currentStock + actualQtyToAdd;
            if (!newExpiry.isEmpty()) {
                db.execSQL("UPDATE products SET quantity = ?, expiry_date = ?, purchase_price = ? WHERE id = ?", new Object[]{newStock, newExpiry, costPerPiece, productId});
            } else {
                db.execSQL("UPDATE products SET quantity = ?, purchase_price = ? WHERE id = ?", new Object[]{newStock, costPerPiece, productId});
            }

            db.setTransactionSuccessful();
            Toast.makeText(this, "تمت العملية بنجاح! المخزون الجديد: " + newStock, Toast.LENGTH_LONG).show();

            tvPurchaseResult.setText("آخر عملية: " + actualQtyToAdd + " حبة مضافة.");
            tvPurchaseRemaining.setText("الباقي للدفع: " + remaining);

            etPurchaseBarcode.setText("");
            etPurchaseQty.setText("");
            etPurchaseUnitPrice.setText("");
            etPurchasePaid.setText("");

        } catch (Exception e) {
            Toast.makeText(this, "خطأ: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            db.endTransaction();
        }
    }
}
