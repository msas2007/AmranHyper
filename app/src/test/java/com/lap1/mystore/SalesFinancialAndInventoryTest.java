package com.lap1.mystore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.models.StockMovement;
import com.lap1.mystore.utils.SessionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SalesFinancialAndInventoryTest {

    private DatabaseHelper dbHelper;
    private SQLiteDatabase db;
    private Context context;

    private int testWarehouseId;
    private int testProductId;
    private int testCustomerId;
    private int testUserId;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        dbHelper = new DatabaseHelper(context);
        db = dbHelper.getWritableDatabase();

        // 1. Ensure default warehouse
        testWarehouseId = dbHelper.getDefaultWarehouseId();
        assertTrue("المخزن الافتراضي يجب أن يكون موجوداً", testWarehouseId > 0);

        // 2. Insert test user & set session
        ContentValues userCv = new ContentValues();
        userCv.put("username", "cashier1");
        userCv.put("password", "hash123");
        userCv.put("full_name", "كاشير المبيعات");
        userCv.put("role", "cashier");
        testUserId = (int) db.insert("users", null, userCv);
        SessionManager.getInstance(context).createLoginSession(testUserId, "cashier1", "كاشير المبيعات", "cashier");

        // 3. Insert test product with 100 qty
        ContentValues prodCv = new ContentValues();
        prodCv.put("name", "أرز تايلندي 10 كجم");
        prodCv.put("barcode", "628100000001");
        prodCv.put("purchase_price", 40.0);
        prodCv.put("sale_price", 50.0);
        prodCv.put("quantity", 100.0);
        prodCv.put("min_quantity", 10.0);
        testProductId = (int) db.insert("products", null, prodCv);

        // Ensure warehouse_stock is initialized for this product
        ContentValues wsCv = new ContentValues();
        wsCv.put("warehouse_id", testWarehouseId);
        wsCv.put("product_id", testProductId);
        wsCv.put("quantity", 100.0);
        db.insertWithOnConflict("warehouse_stock", null, wsCv, SQLiteDatabase.CONFLICT_REPLACE);

        // 4. Insert test customer
        ContentValues custCv = new ContentValues();
        custCv.put("name", "أحمد علي التاجر");
        custCv.put("phone", "777123456");
        custCv.put("balance", 0.0);
        testCustomerId = (int) db.insert("customers", null, custCv);
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

    private double getCustomerBalance(int customerId) {
        Cursor c = db.rawQuery("SELECT balance FROM customers WHERE id = ?", new String[]{String.valueOf(customerId)});
        try {
            return c.moveToFirst() ? c.getDouble(0) : 0.0;
        } finally {
            c.close();
        }
    }

    @Test
    public void testCashSaleAffectsInventoryAndMovementsCorrectly() {
        // Initial state
        assertEquals(100.0, dbHelper.getTotalProductQuantity(db, testProductId), 0.001);
        assertEquals(100.0, dbHelper.getWarehouseProductQuantity(db, testWarehouseId, testProductId), 0.001);

        // Perform Cash Sale: 10 units @ 50.0 = 500.0
        double saleQty = 10.0;
        double unitPrice = 50.0;
        double total = saleQty * unitPrice;

        db.beginTransaction();
        long saleId;
        try {
            ContentValues saleCv = new ContentValues();
            saleCv.put("invoice_number", "INV-TEST-CASH");
            saleCv.put("customer_id", (Integer) null);
            saleCv.put("total", total);
            saleCv.put("discount", 0.0);
            saleCv.put("paid", total);
            saleCv.put("remaining", 0.0);
            saleCv.put("payment_status", "مدفوعة بالكامل");
            saleCv.put("payment_method", "كاش");
            saleCv.put("created_by_user_id", testUserId);
            saleId = db.insert("sales", null, saleCv);

            ContentValues itemCv = new ContentValues();
            itemCv.put("sale_id", saleId);
            itemCv.put("product_id", testProductId);
            itemCv.put("quantity", saleQty);
            itemCv.put("unit_price", unitPrice);
            itemCv.put("purchase_price", 40.0);
            itemCv.put("total", total);
            db.insert("sale_items", null, itemCv);

            // Update warehouse stock & movements atomically
            dbHelper.adjustWarehouseStock(db, testWarehouseId, testProductId, -saleQty,
                    StockMovement.SALE, (int) saleId, testUserId, "فاتورة مبيعات INV-TEST-CASH");

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        assertTrue(saleId > 0);

        // Verify stock deducted
        assertEquals(90.0, dbHelper.getTotalProductQuantity(db, testProductId), 0.001);
        assertEquals(90.0, dbHelper.getWarehouseProductQuantity(db, testWarehouseId, testProductId), 0.001);

        // Verify products table quantity sync
        Cursor pCur = db.rawQuery("SELECT quantity FROM products WHERE id = ?", new String[]{String.valueOf(testProductId)});
        assertTrue(pCur.moveToFirst());
        assertEquals(90.0, pCur.getDouble(0), 0.001);
        pCur.close();

        // Verify stock_movements entry
        Cursor mCur = db.rawQuery("SELECT quantity, movement_type, reference_id, user_id FROM stock_movements WHERE product_id = ? ORDER BY id DESC LIMIT 1",
                new String[]{String.valueOf(testProductId)});
        assertTrue(mCur.moveToFirst());
        assertEquals(-10.0, mCur.getDouble(0), 0.001);
        assertEquals(StockMovement.SALE, mCur.getString(1));
        assertEquals(saleId, mCur.getInt(2));
        assertEquals(testUserId, mCur.getInt(3));
        mCur.close();

        // Verify sales table created_by_user_id
        Cursor sCur = db.rawQuery("SELECT created_by_user_id, paid, remaining, payment_status FROM sales WHERE id = ?", new String[]{String.valueOf(saleId)});
        assertTrue(sCur.moveToFirst());
        assertEquals(testUserId, sCur.getInt(0));
        assertEquals(500.0, sCur.getDouble(1), 0.001);
        assertEquals(0.0, sCur.getDouble(2), 0.001);
        assertEquals("مدفوعة بالكامل", sCur.getString(3));
        sCur.close();
    }

    @Test
    public void testCreditSaleUpdatesCustomerLedgerAndBalance() {
        // Credit sale: 5 units @ 50.0 = 250.0, discount = 10.0 -> net = 240.0, paid = 0, remaining = 240.0
        double saleQty = 5.0;
        double unitPrice = 50.0;
        double discount = 10.0;
        double netTotal = 240.0;

        db.beginTransaction();
        long saleId;
        try {
            ContentValues saleCv = new ContentValues();
            saleCv.put("invoice_number", "INV-TEST-CREDIT");
            saleCv.put("customer_id", testCustomerId);
            saleCv.put("total", 250.0);
            saleCv.put("discount", discount);
            saleCv.put("paid", 0.0);
            saleCv.put("remaining", netTotal);
            saleCv.put("payment_status", "آجلة");
            saleCv.put("payment_method", "كاش");
            saleCv.put("created_by_user_id", testUserId);
            saleId = db.insert("sales", null, saleCv);

            ContentValues itemCv = new ContentValues();
            itemCv.put("sale_id", saleId);
            itemCv.put("product_id", testProductId);
            itemCv.put("quantity", saleQty);
            itemCv.put("unit_price", unitPrice);
            itemCv.put("purchase_price", 40.0);
            itemCv.put("total", 250.0);
            db.insert("sale_items", null, itemCv);

            dbHelper.adjustWarehouseStock(db, testWarehouseId, testProductId, -saleQty,
                    StockMovement.SALE, (int) saleId, testUserId, "فاتورة مبيعات آجلة INV-TEST-CREDIT");

            // Record customer ledger entry
            dbHelper.recordCustomerTransaction(db, testCustomerId, DatabaseHelper.TX_SALE, saleId, netTotal, "فاتورة آجلة INV-TEST-CREDIT");
            dbHelper.syncCustomerBalance(db, testCustomerId);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        // Verify customer balance = 240.0
        assertEquals(240.0, getCustomerBalance(testCustomerId), 0.001);

        // Verify stock deducted to 95.0
        assertEquals(95.0, dbHelper.getTotalProductQuantity(db, testProductId), 0.001);
    }

    @Test
    public void testPartialPaymentSaleAndCustomerDebtPayment() {
        // Partial payment sale: 4 units @ 50.0 = 200.0, paid = 50.0, remaining = 150.0
        double saleQty = 4.0;
        double total = 200.0;
        double paid = 50.0;
        double remaining = 150.0;

        db.beginTransaction();
        long saleId;
        try {
            ContentValues saleCv = new ContentValues();
            saleCv.put("invoice_number", "INV-TEST-PARTIAL");
            saleCv.put("customer_id", testCustomerId);
            saleCv.put("total", total);
            saleCv.put("discount", 0.0);
            saleCv.put("paid", paid);
            saleCv.put("remaining", remaining);
            saleCv.put("payment_status", "دفع جزئي");
            saleCv.put("payment_method", "كاش");
            saleCv.put("created_by_user_id", testUserId);
            saleId = db.insert("sales", null, saleCv);

            ContentValues itemCv = new ContentValues();
            itemCv.put("sale_id", saleId);
            itemCv.put("product_id", testProductId);
            itemCv.put("quantity", saleQty);
            itemCv.put("unit_price", 50.0);
            itemCv.put("purchase_price", 40.0);
            itemCv.put("total", total);
            db.insert("sale_items", null, itemCv);

            dbHelper.adjustWarehouseStock(db, testWarehouseId, testProductId, -saleQty,
                    StockMovement.SALE, (int) saleId, testUserId, "فاتورة جزئية");

            // Customer ledger: +200 sale, -50 payment -> balance = +150
            dbHelper.recordCustomerTransaction(db, testCustomerId, DatabaseHelper.TX_SALE, saleId, total, "فاتورة مبيعات");
            dbHelper.recordCustomerTransaction(db, testCustomerId, DatabaseHelper.TX_SALE_PAYMENT, saleId, -paid, "دفعة مقدمة");
            dbHelper.syncCustomerBalance(db, testCustomerId);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        assertEquals(150.0, getCustomerBalance(testCustomerId), 0.001);

        // Now customer pays remaining 150.0
        db.beginTransaction();
        try {
            ContentValues payCv = new ContentValues();
            payCv.put("customer_id", testCustomerId);
            payCv.put("amount", 150.0);
            payCv.put("payment_method", "كاش");
            payCv.put("notes", "سداد دين الفاتورة الجزئية");
            long payId = db.insert("payments", null, payCv);

            dbHelper.recordCustomerTransaction(db, testCustomerId, DatabaseHelper.TX_PAYMENT, payId, -150.0, "سداد نقدي");
            dbHelper.syncCustomerBalance(db, testCustomerId);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        // Customer balance should now be 0.0
        assertEquals(0.0, getCustomerBalance(testCustomerId), 0.001);
    }

    @Test
    public void testReturnsWithCashAndDebtReductionWithoutDoubleDeduction() {
        // Step 1: Create a credit sale of 6 units @ 50 = 300.0 (unpaid)
        db.beginTransaction();
        long saleId;
        try {
            ContentValues saleCv = new ContentValues();
            saleCv.put("invoice_number", "INV-FOR-RETURN");
            saleCv.put("customer_id", testCustomerId);
            saleCv.put("total", 300.0);
            saleCv.put("discount", 0.0);
            saleCv.put("paid", 0.0);
            saleCv.put("remaining", 300.0);
            saleCv.put("payment_status", "آجلة");
            saleCv.put("created_by_user_id", testUserId);
            saleId = db.insert("sales", null, saleCv);

            ContentValues itemCv = new ContentValues();
            itemCv.put("sale_id", saleId);
            itemCv.put("product_id", testProductId);
            itemCv.put("quantity", 6.0);
            itemCv.put("unit_price", 50.0);
            itemCv.put("purchase_price", 40.0);
            itemCv.put("total", 300.0);
            db.insert("sale_items", null, itemCv);

            dbHelper.adjustWarehouseStock(db, testWarehouseId, testProductId, -6.0,
                    StockMovement.SALE, (int) saleId, testUserId, "بيع آجل");
            dbHelper.recordCustomerTransaction(db, testCustomerId, DatabaseHelper.TX_SALE, saleId, 300.0, "فاتورة INV-FOR-RETURN");
            dbHelper.syncCustomerBalance(db, testCustomerId);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        assertEquals(94.0, dbHelper.getTotalProductQuantity(db, testProductId), 0.001);
        assertEquals(300.0, getCustomerBalance(testCustomerId), 0.001);

        // Step 2: Return 2 units with DEBT REDUCTION (2 * 50 = 100.0)
        double returnQty = 2.0;
        double refundAmount = 100.0;
        double debtReduction = 100.0;

        db.beginTransaction();
        try {
            ContentValues retCv = new ContentValues();
            retCv.put("sale_id", saleId);
            retCv.put("product_id", testProductId);
            retCv.put("quantity", returnQty);
            retCv.put("amount", refundAmount);
            retCv.put("purchase_price", 40.0);
            retCv.put("return_method", "خصم من الدين");
            retCv.put("debt_reduction", debtReduction);
            retCv.put("created_by_user_id", testUserId);
            db.insert("returns", null, retCv);

            // Re-add stock
            dbHelper.adjustWarehouseStock(db, testWarehouseId, testProductId, returnQty,
                    StockMovement.SALE_RETURN, (int) saleId, testUserId, "مرتجع من الفاتورة");

            // Update sale remaining
            db.execSQL("UPDATE sales SET remaining = remaining - ? WHERE id = ?", new Object[]{debtReduction, saleId});

            // Single ledger transaction (NO DOUBLE DEDUCTION)
            dbHelper.recordCustomerTransaction(db, testCustomerId, DatabaseHelper.TX_RETURN, saleId, -debtReduction, "مرتجع من الفاتورة INV-FOR-RETURN");
            dbHelper.syncCustomerBalance(db, testCustomerId);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        // Verify stock is restored from 94 to 96
        assertEquals(96.0, dbHelper.getTotalProductQuantity(db, testProductId), 0.001);
        assertEquals(96.0, dbHelper.getWarehouseProductQuantity(db, testWarehouseId, testProductId), 0.001);

        // Verify customer balance decreased from 300 to 200 (exact single deduction)
        assertEquals(200.0, getCustomerBalance(testCustomerId), 0.001);

        // Verify sale remaining is 200.0
        Cursor sCur = db.rawQuery("SELECT remaining FROM sales WHERE id = ?", new String[]{String.valueOf(saleId)});
        assertTrue(sCur.moveToFirst());
        assertEquals(200.0, sCur.getDouble(0), 0.001);
        sCur.close();
    }
}
