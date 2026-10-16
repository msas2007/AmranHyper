package com.lap1.mystore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.lap1.mystore.database.DatabaseHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StoreDatabaseLogicTest {

    private DatabaseHelper dbHelper;
    private SQLiteDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        dbHelper = new DatabaseHelper(context);
        db = dbHelper.getWritableDatabase();
    }

    @After
    public void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    @Test
    public void testProductArchivingLogic() {
        // 1. إضافة تصنيف ومنتج
        ContentValues cvCat = new ContentValues();
        cvCat.put("name", "أغذية");
        long catId = db.insert("categories", null, cvCat);

        ContentValues cvProd = new ContentValues();
        cvProd.put("name", "عصير برتقال");
        cvProd.put("barcode", "6291000111222");
        cvProd.put("category_id", catId);
        cvProd.put("purchase_price", 3.0);
        cvProd.put("sale_price", 5.0);
        cvProd.put("quantity", 50.0);
        cvProd.put("is_archived", 0);
        long prodId = db.insert("products", null, cvProd);

        // 2. محاكاة فاتورة بيع للمنتج
        ContentValues cvSale = new ContentValues();
        cvSale.put("invoice_number", "INV-TEST-1");
        cvSale.put("total", 10.0);
        cvSale.put("paid", 10.0);
        long saleId = db.insert("sales", null, cvSale);

        ContentValues cvItem = new ContentValues();
        cvItem.put("sale_id", saleId);
        cvItem.put("product_id", prodId);
        cvItem.put("quantity", 2.0);
        cvItem.put("unit_price", 5.0);
        cvItem.put("total", 10.0);
        db.insert("sale_items", null, cvItem);

        // 3. التحقق من وجود سجل تاريخي يمنع الحذف النهائي ويكتفي بالأرشفة
        Cursor cHistory = db.rawQuery("SELECT COUNT(*) FROM sale_items WHERE product_id = ?", new String[]{String.valueOf(prodId)});
        assertTrue(cHistory.moveToFirst());
        int historyCount = cHistory.getInt(0);
        cHistory.close();

        assertEquals(1, historyCount);

        // أرشفة المنتج
        ContentValues cvArchive = new ContentValues();
        cvArchive.put("is_archived", 1);
        int rows = db.update("products", cvArchive, "id = ?", new String[]{String.valueOf(prodId)});
        assertEquals(1, rows);

        // التحقق من استبعاد المنتجات المؤرشفة من القائمة النشطة
        Cursor cActive = db.rawQuery("SELECT id FROM products WHERE (is_archived IS NULL OR is_archived = 0)", null);
        assertEquals(0, cActive.getCount());
        cActive.close();
    }

    @Test
    public void testUniqueBarcodeValidation() {
        ContentValues cvProd1 = new ContentValues();
        cvProd1.put("name", "حليب طازج");
        cvProd1.put("barcode", "1122334455");
        cvProd1.put("sale_price", 10.0);
        cvProd1.put("quantity", 20.0);
        cvProd1.put("is_archived", 0);
        db.insert("products", null, cvProd1);

        // فحص الباركود قبل إضافة منتج ثاني
        String testBarcode = "1122334455";
        Cursor cCheck = db.rawQuery("SELECT id FROM products WHERE barcode = ? AND (is_archived IS NULL OR is_archived = 0)", new String[]{testBarcode});
        boolean exists = cCheck.moveToFirst();
        cCheck.close();

        assertTrue("الباركود المكرر يجب كشفه لمنع التكرار", exists);
    }

    @Test
    public void testStockAdjustmentRecord() {
        ContentValues cvProd = new ContentValues();
        cvProd.put("name", "أرز بسمتي");
        cvProd.put("quantity", 100.0);
        long prodId = db.insert("products", null, cvProd);

        double systemQty = 100.0;
        double actualQty = 95.0;
        double diff = actualQty - systemQty;

        // تسجيل حركة جرد
        ContentValues cvAdj = new ContentValues();
        cvAdj.put("product_id", prodId);
        cvAdj.put("product_name", "أرز بسمتي");
        cvAdj.put("system_quantity", systemQty);
        cvAdj.put("actual_quantity", actualQty);
        cvAdj.put("difference", diff);
        cvAdj.put("notes", "تلف أكياس أثناء النقل");
        long adjId = db.insert("stock_adjustments", null, cvAdj);

        assertTrue(adjId != -1);

        // تحديث الكمية
        db.execSQL("UPDATE products SET quantity = ? WHERE id = ?", new Object[]{actualQty, prodId});

        Cursor cUpdated = db.rawQuery("SELECT quantity FROM products WHERE id = ?", new String[]{String.valueOf(prodId)});
        assertTrue(cUpdated.moveToFirst());
        assertEquals(95.0, cUpdated.getDouble(0), 0.001);
        cUpdated.close();
    }

    @Test
    public void testCustomerChronologicalAccountStatement() {
        // إضافة عميل
        ContentValues cvCust = new ContentValues();
        cvCust.put("name", "سالم محمد");
        cvCust.put("balance", 150.0);
        long custId = db.insert("customers", null, cvCust);

        // 1. فاتورة آجل بمبلغ 200 (المدفوع 50، المتبقي 150)
        ContentValues cvSale = new ContentValues();
        cvSale.put("invoice_number", "INV-101");
        cvSale.put("customer_id", custId);
        cvSale.put("total", 200.0);
        cvSale.put("paid", 50.0);
        cvSale.put("remaining", 150.0);
        cvSale.put("payment_status", "دفع جزئي");
        db.insert("sales", null, cvSale);

        // 2. دفعة سداد بعد فترة بمبلغ 100
        ContentValues cvPay = new ContentValues();
        cvPay.put("customer_id", custId);
        cvPay.put("amount", 100.0);
        cvPay.put("notes", "دفعة جزئية نقدية");
        db.insert("payments", null, cvPay);

        // حساب الرصيد المتراكم
        Cursor cSumPaid = db.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE customer_id = ?", new String[]{String.valueOf(custId)});
        assertTrue(cSumPaid.moveToFirst());
        double totalPaidAfter = cSumPaid.getDouble(0);
        cSumPaid.close();

        assertEquals(100.0, totalPaidAfter, 0.001);
        double currentRemDebt = 150.0 - totalPaidAfter;
        assertEquals(50.0, currentRemDebt, 0.001);
    }
}
