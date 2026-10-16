package com.lap1.mystore.activities;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

public class ReturnsActivity extends AppCompatActivity {

    private EditText etInvoiceNo, etProductBarcode, etReturnQty;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_returns);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        etInvoiceNo = findViewById(R.id.etReturnInvoiceNo);
        etProductBarcode = findViewById(R.id.etReturnProductBarcode);
        etReturnQty = findViewById(R.id.etReturnQty);
        Button btnProcessReturn = findViewById(R.id.btnProcessReturn);
        View btnBack = findViewById(R.id.btnReturnsBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        dbHelper = new DatabaseHelper(this);

        btnProcessReturn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                processReturn();
            }
        });

        // الشريط السفلي الثابت
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "");
    }

    private void processReturn() {
        String invoiceNo = etInvoiceNo.getText().toString().trim();
        String productQuery = etProductBarcode.getText().toString().trim();
        String qtyStr = etReturnQty.getText().toString().trim();

        if (invoiceNo.isEmpty() || productQuery.isEmpty() || qtyStr.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال رقم الفاتورة والمنتج والكمية", Toast.LENGTH_SHORT).show();
            return;
        }

        double returnQty;
        try {
            returnQty = Double.parseDouble(qtyStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "كمية الإرجاع غير صالحة", Toast.LENGTH_SHORT).show();
            return;
        }

        // [FIX 2] منع الكميات الصفرية أو السالبة
        if (returnQty <= 0) {
            Toast.makeText(this, "كمية الإرجاع يجب أن تكون أكبر من الصفر", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // 1. البحث عن الفاتورة والعميل المرتبط بها
        Cursor saleCursor = db.rawQuery("SELECT id, customer_id, payment_status FROM sales WHERE invoice_number = ?", new String[]{invoiceNo});
        if (!saleCursor.moveToFirst()) {
            Toast.makeText(this, "رقم الفاتورة غير موجود!", Toast.LENGTH_SHORT).show();
            saleCursor.close();
            return;
        }
        int saleId = saleCursor.getInt(0);
        int customerId = saleCursor.getInt(1);
        String payStatus = saleCursor.getString(2);
        saleCursor.close();

        // 2. البحث عن المنتج في هذه الفاتورة تحديداً (للحصول على سعر البيع وسعر الشراء وقتها)
        String itemSql = "SELECT si.product_id, si.unit_price, si.purchase_price, si.quantity " +
                "FROM sale_items si JOIN products p ON si.product_id = p.id " +
                "WHERE si.sale_id = ? AND (p.name = ? OR p.barcode = ?)";
        Cursor itemCursor = db.rawQuery(itemSql, new String[]{String.valueOf(saleId), productQuery, productQuery});

        if (!itemCursor.moveToFirst()) {
            Toast.makeText(this, "هذا المنتج غير موجود في هذه الفاتورة!", Toast.LENGTH_SHORT).show();
            itemCursor.close();
            return;
        }

        int productId = itemCursor.getInt(0);
        double saleUnitPrice = itemCursor.getDouble(1);
        double purchaseUnitPrice = itemCursor.getDouble(2);
        double originalSoldQty = itemCursor.getDouble(3);
        itemCursor.close();

        // [FIX 1] حساب الكمية المُرجعة مسبقاً لنفس الفاتورة والمنتج
        double alreadyReturnedQty = 0;
        Cursor cPrevReturns = db.rawQuery(
                "SELECT COALESCE(SUM(quantity), 0) FROM returns WHERE sale_id = ? AND product_id = ?",
                new String[]{String.valueOf(saleId), String.valueOf(productId)}
        );
        if (cPrevReturns.moveToFirst()) {
            alreadyReturnedQty = cPrevReturns.getDouble(0);
        }
        cPrevReturns.close();

        double availableToReturn = originalSoldQty - alreadyReturnedQty;

        if (alreadyReturnedQty >= originalSoldQty) {
            Toast.makeText(this, "لقد تم إرجاع هذا الصنف بالكامل مسبقاً!", Toast.LENGTH_LONG).show();
            return;
        }

        if (returnQty > availableToReturn) {
            Toast.makeText(this,
                    "الكمية المرتجعة أكبر من الكمية المتاحة للإرجاع! المتاح: " + availableToReturn,
                    Toast.LENGTH_LONG).show();
            return;
        }

        double refundAmount = saleUnitPrice * returnQty;

        db.beginTransaction();
        try {
            // ج. تحديد طريقة المرتجع وحساب التخفيض من الدين
            String returnMethod = "نقدي";
            double debtReduction = 0.0;

            if (customerId > 0) {
                Cursor cCust = db.rawQuery("SELECT balance FROM customers WHERE id = ?", new String[]{String.valueOf(customerId)});
                if (cCust.moveToFirst()) {
                    double currentDebt = cCust.getDouble(0);
                    if (currentDebt > 0) {
                        debtReduction = Math.min(refundAmount, currentDebt);
                        db.execSQL("UPDATE customers SET balance = balance - ? WHERE id = ?", new Object[]{debtReduction, customerId});

                        if (debtReduction == refundAmount) {
                            returnMethod = "خصم من الدين";
                        } else {
                            returnMethod = "مختلط (دين + نقدي)";
                        }
                    }
                }
                cCust.close();
            }

            // أ. تسجيل عملية الإرجاع مع التخفيض من الدين
            ContentValues cvReturn = new ContentValues();
            cvReturn.put("sale_id", saleId);
            cvReturn.put("product_id", productId);
            cvReturn.put("quantity", returnQty);
            cvReturn.put("amount", refundAmount);
            cvReturn.put("purchase_price", purchaseUnitPrice);
            cvReturn.put("return_method", returnMethod);
            cvReturn.put("debt_reduction", debtReduction);
            db.insert("returns", null, cvReturn);

            // ب. إعادة الكمية إلى المخزون
            db.execSQL("UPDATE products SET quantity = quantity + ? WHERE id = ?", new Object[]{returnQty, productId});

            db.setTransactionSuccessful();

            if (debtReduction > 0) {
                if (debtReduction < refundAmount) {
                    Toast.makeText(this, "تم خصم " + debtReduction + " من الدين ويرجى رد " + (refundAmount - debtReduction) + " نقداً", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "تم خصم كامل المرتجع من ديون العميل", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "يرجى رد " + refundAmount + " نقداً للعميل", Toast.LENGTH_LONG).show();
            }
            etInvoiceNo.setText("");
            etProductBarcode.setText("");
            etReturnQty.setText("");

        } catch (Exception e) {
            Toast.makeText(this, "خطأ: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            db.endTransaction();
        }
    }
}
