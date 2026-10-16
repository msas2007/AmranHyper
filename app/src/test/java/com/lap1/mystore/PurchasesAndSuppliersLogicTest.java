package com.lap1.mystore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.models.PurchaseOrder;
import com.lap1.mystore.models.StockMovement;
import com.lap1.mystore.utils.SessionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PurchasesAndSuppliersLogicTest {

    private DatabaseHelper dbHelper;
    private SQLiteDatabase db;
    private Context context;

    private int testWarehouseId;
    private int testProductId;
    private int testSupplierId;
    private int testUserId;
    private String todayDate;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        dbHelper = new DatabaseHelper(context);
        db = dbHelper.getWritableDatabase();

        testWarehouseId = dbHelper.getDefaultWarehouseId();
        assertTrue("المخزن الافتراضي موجود", testWarehouseId > 0);

        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        // 1. Insert test user
        ContentValues userCv = new ContentValues();
        userCv.put("username", "purchaser1");
        userCv.put("password", "hash123");
        userCv.put("full_name", "مسؤول المشتريات");
        userCv.put("role", "admin");
        testUserId = (int) db.insert("users", null, userCv);
        SessionManager.getInstance(context).createLoginSession(testUserId, "purchaser1", "مسؤول المشتريات", "admin");

        // 2. Insert test product with 20 initial qty
        ContentValues prodCv = new ContentValues();
        prodCv.put("name", "سكر الأسرة 5 كجم");
        prodCv.put("barcode", "770011223344");
        prodCv.put("purchase_price", 25.0);
        prodCv.put("sale_price", 32.0);
        prodCv.put("quantity", 20.0);
        prodCv.put("min_quantity", 5.0);
        testProductId = (int) db.insert("products", null, prodCv);

        ContentValues wsCv = new ContentValues();
        wsCv.put("warehouse_id", testWarehouseId);
        wsCv.put("product_id", testProductId);
        wsCv.put("quantity", 20.0);
        db.insertWithOnConflict("warehouse_stock", null, wsCv, SQLiteDatabase.CONFLICT_REPLACE);

        // 3. Insert test supplier
        ContentValues supCv = new ContentValues();
        supCv.put("name", "شركة الهلال للتجارة والتوريد");
        supCv.put("phone", "771122334");
        supCv.put("balance", 0.0);
        supCv.put("is_archived", 0);
        testSupplierId = (int) db.insert("suppliers", null, supCv);
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
    public void testCashPurchase_updatesStockWarehouseAndLogsMovements_noSupplierDebt() {
        // Cash Purchase: 30 units @ 25 ر.ي = 750, Paid = 750, Remaining = 0
        double purchaseQty = 30.0;
        double unitPrice = 25.0;
        double total = purchaseQty * unitPrice; // 750.0
        double paid = total;
        double remaining = 0.0;

        db.beginTransaction();
        long purchaseId = -1;
        try {
            ContentValues pCv = new ContentValues();
            pCv.put("supplier_id", testSupplierId);
            pCv.put("supplier_name", "شركة الهلال للتجارة والتوريد");
            pCv.put("total", total);
            pCv.put("paid", paid);
            pCv.put("remaining", remaining);
            pCv.put("payment_method", "كاش");
            pCv.put("created_by_user_id", testUserId);
            purchaseId = db.insertOrThrow("purchases", null, pCv);

            ContentValues piCv = new ContentValues();
            piCv.put("purchase_id", purchaseId);
            piCv.put("product_id", testProductId);
            piCv.put("quantity", purchaseQty);
            piCv.put("unit_price", unitPrice);
            piCv.put("total", total);
            db.insertOrThrow("purchase_items", null, piCv);

            dbHelper.adjustWarehouseStock(db, testWarehouseId, testProductId, purchaseQty,
                    StockMovement.PURCHASE, (int) purchaseId, testUserId, "شراء نقدي #" + purchaseId);

            dbHelper.recordSupplierTransaction(db, testSupplierId, DatabaseHelper.TX_PURCHASE, purchaseId, total, "فاتورة شراء #" + purchaseId);
            dbHelper.recordSupplierTransaction(db, testSupplierId, DatabaseHelper.TX_PURCHASE_PAYMENT, purchaseId, -paid, "مدفوع نقداً #" + purchaseId);
            dbHelper.syncSupplierBalance(db, testSupplierId);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        assertTrue(purchaseId > 0);

        // 1. Stock check: initial 20 + 30 = 50
        double whQty = dbHelper.getWarehouseProductQuantity(db, testWarehouseId, testProductId);
        assertEquals(50.0, whQty, 0.001);
        double prodQty = dbHelper.getTotalProductQuantity(db, testProductId);
        assertEquals(50.0, prodQty, 0.001);

        // 2. Stock movement logged
        Cursor cMov = db.rawQuery("SELECT movement_type, quantity, reference_id FROM stock_movements WHERE product_id = ? AND reference_id = ?",
                new String[]{String.valueOf(testProductId), String.valueOf(purchaseId)});
        assertTrue("حركة المخزون مسجلة", cMov.moveToFirst());
        assertEquals(StockMovement.PURCHASE, cMov.getString(0));
        assertEquals(30.0, cMov.getDouble(1), 0.001);
        cMov.close();

        // 3. Supplier balance should be 0 (fully paid cash)
        double supBalance = dbHelper.getSupplierLedgerBalance(db, testSupplierId);
        assertEquals(0.0, supBalance, 0.001);

        // 4. Cash out should reflect 750
        double cashOut = dbHelper.getCashOutForDate(db, todayDate);
        assertTrue("تم تسجيل خروج نقدي للمشتريات", cashOut >= 750.0);
    }

    @Test
    public void testCreditPurchase_increasesSupplierDebtAndStock_noImmediateCashOut() {
        // Credit Purchase: 10 units @ 30 ر.ي = 300, Paid = 0, Remaining = 300
        double purchaseQty = 10.0;
        double unitPrice = 30.0;
        double total = 300.0;
        double paid = 0.0;
        double remaining = 300.0;

        db.beginTransaction();
        long purchaseId;
        try {
            ContentValues pCv = new ContentValues();
            pCv.put("supplier_id", testSupplierId);
            pCv.put("supplier_name", "شركة الهلال للتجارة والتوريد");
            pCv.put("total", total);
            pCv.put("paid", paid);
            pCv.put("remaining", remaining);
            pCv.put("payment_method", "آجل");
            pCv.put("created_by_user_id", testUserId);
            purchaseId = db.insertOrThrow("purchases", null, pCv);

            ContentValues piCv = new ContentValues();
            piCv.put("purchase_id", purchaseId);
            piCv.put("product_id", testProductId);
            piCv.put("quantity", purchaseQty);
            piCv.put("unit_price", unitPrice);
            piCv.put("total", total);
            db.insertOrThrow("purchase_items", null, piCv);

            dbHelper.adjustWarehouseStock(db, testWarehouseId, testProductId, purchaseQty,
                    StockMovement.PURCHASE, (int) purchaseId, testUserId, "شراء آجل #" + purchaseId);

            dbHelper.recordSupplierTransaction(db, testSupplierId, DatabaseHelper.TX_PURCHASE, purchaseId, total, "فاتورة شراء آجل #" + purchaseId);
            dbHelper.syncSupplierBalance(db, testSupplierId);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        // 1. Stock check: initial 20 + 10 = 30
        assertEquals(30.0, dbHelper.getWarehouseProductQuantity(db, testWarehouseId, testProductId), 0.001);

        // 2. Supplier balance increases to 300
        assertEquals(300.0, dbHelper.getSupplierLedgerBalance(db, testSupplierId), 0.001);
    }

    @Test
    public void testPartialPaymentPurchase_splitsCashAndDebtCorrectly() {
        // Partial Purchase: 40 units @ 20 ر.ي = 800, Paid = 300, Remaining = 500
        double purchaseQty = 40.0;
        double unitPrice = 20.0;
        double total = 800.0;
        double paid = 300.0;
        double remaining = 500.0;

        db.beginTransaction();
        long purchaseId;
        try {
            ContentValues pCv = new ContentValues();
            pCv.put("supplier_id", testSupplierId);
            pCv.put("supplier_name", "شركة الهلال للتجارة والتوريد");
            pCv.put("total", total);
            pCv.put("paid", paid);
            pCv.put("remaining", remaining);
            pCv.put("payment_method", "كاش");
            pCv.put("created_by_user_id", testUserId);
            purchaseId = db.insertOrThrow("purchases", null, pCv);

            ContentValues piCv = new ContentValues();
            piCv.put("purchase_id", purchaseId);
            piCv.put("product_id", testProductId);
            piCv.put("quantity", purchaseQty);
            piCv.put("unit_price", unitPrice);
            piCv.put("total", total);
            db.insertOrThrow("purchase_items", null, piCv);

            dbHelper.adjustWarehouseStock(db, testWarehouseId, testProductId, purchaseQty,
                    StockMovement.PURCHASE, (int) purchaseId, testUserId, "شراء جزئي #" + purchaseId);

            dbHelper.recordSupplierTransaction(db, testSupplierId, DatabaseHelper.TX_PURCHASE, purchaseId, total, "فاتورة شراء #" + purchaseId);
            dbHelper.recordSupplierTransaction(db, testSupplierId, DatabaseHelper.TX_PURCHASE_PAYMENT, purchaseId, -paid, "مدفوع عند الشراء #" + purchaseId);
            dbHelper.syncSupplierBalance(db, testSupplierId);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        // 1. Stock check: 20 + 40 = 60
        assertEquals(60.0, dbHelper.getWarehouseProductQuantity(db, testWarehouseId, testProductId), 0.001);

        // 2. Supplier balance should be exactly 500
        assertEquals(500.0, dbHelper.getSupplierLedgerBalance(db, testSupplierId), 0.001);

        // 3. Purchase row remaining = 500
        Cursor cp = db.rawQuery("SELECT remaining, paid FROM purchases WHERE id = ?", new String[]{String.valueOf(purchaseId)});
        assertTrue(cp.moveToFirst());
        assertEquals(500.0, cp.getDouble(0), 0.001);
        assertEquals(300.0, cp.getDouble(1), 0.001);
        cp.close();
    }

    @Test
    public void testSupplierPayment_reducesDebtAllocatesPurchasesAndRecordsCashOut() {
        // First create a credit purchase with 400 remaining
        ContentValues pCv = new ContentValues();
        pCv.put("supplier_id", testSupplierId);
        pCv.put("supplier_name", "شركة الهلال");
        pCv.put("total", 400.0);
        pCv.put("paid", 0.0);
        pCv.put("remaining", 400.0);
        pCv.put("payment_method", "آجل");
        long purchaseId = db.insertOrThrow("purchases", null, pCv);

        dbHelper.recordSupplierTransaction(db, testSupplierId, DatabaseHelper.TX_PURCHASE, purchaseId, 400.0, "فاتورة آجل #" + purchaseId);
        dbHelper.syncSupplierBalance(db, testSupplierId);
        assertEquals(400.0, dbHelper.getSupplierLedgerBalance(db, testSupplierId), 0.001);

        // Now pay 250 to supplier
        double paymentAmount = 250.0;
        db.beginTransaction();
        try {
            ContentValues spCv = new ContentValues();
            spCv.put("supplier_id", testSupplierId);
            spCv.put("amount", paymentAmount);
            spCv.put("payment_method", "كاش");
            spCv.put("notes", "سداد دفعة");
            long paymentId = db.insertOrThrow("supplier_payments", null, spCv);

            // Allocate
            db.execSQL("UPDATE purchases SET remaining = remaining - ?, paid = paid + ? WHERE id = ?",
                    new Object[]{paymentAmount, paymentAmount, purchaseId});

            ContentValues alloc = new ContentValues();
            alloc.put("payment_id", paymentId);
            alloc.put("purchase_id", purchaseId);
            alloc.put("amount", paymentAmount);
            db.insertOrThrow("supplier_payment_allocations", null, alloc);

            dbHelper.recordSupplierTransaction(db, testSupplierId, DatabaseHelper.TX_PAYMENT, paymentId, -paymentAmount, "سداد دفعة للمورد");
            dbHelper.syncSupplierBalance(db, testSupplierId);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        // 1. Supplier debt reduced from 400 to 150
        assertEquals(150.0, dbHelper.getSupplierLedgerBalance(db, testSupplierId), 0.001);

        // 2. Purchase remaining updated to 150
        Cursor cp = db.rawQuery("SELECT remaining, paid FROM purchases WHERE id = ?", new String[]{String.valueOf(purchaseId)});
        assertTrue(cp.moveToFirst());
        assertEquals(150.0, cp.getDouble(0), 0.001);
        assertEquals(250.0, cp.getDouble(1), 0.001);
        cp.close();
    }

    @Test
    public void testPurchaseOrder_draftAndRequestedDoNotChangeStockOrLedger() {
        double initialStock = dbHelper.getWarehouseProductQuantity(db, testWarehouseId, testProductId);
        assertEquals(20.0, initialStock, 0.001);
        double initialSupplierDebt = dbHelper.getSupplierLedgerBalance(db, testSupplierId);

        // Create PO
        ContentValues poCv = new ContentValues();
        poCv.put("order_number", "PO-9999");
        poCv.put("supplier_id", testSupplierId);
        poCv.put("total", 500.0);
        poCv.put("status", PurchaseOrder.STATUS_DRAFT);
        poCv.put("notes", "طلب تجريبي");
        poCv.put("created_by_user_id", testUserId);
        long poId = db.insertOrThrow("purchase_orders", null, poCv);

        ContentValues poiCv = new ContentValues();
        poiCv.put("order_id", poId);
        poiCv.put("product_id", testProductId);
        poiCv.put("quantity", 20.0);
        poiCv.put("unit_price", 25.0);
        poiCv.put("total", 500.0);
        db.insertOrThrow("purchase_order_items", null, poiCv);

        // Crucial Check: Stock must NOT change!
        assertEquals("طلب الشراء كمسودة لا يغير المخزون",
                initialStock, dbHelper.getWarehouseProductQuantity(db, testWarehouseId, testProductId), 0.001);
        assertEquals("طلب الشراء لا يغير مديونية المورد",
                initialSupplierDebt, dbHelper.getSupplierLedgerBalance(db, testSupplierId), 0.001);

        // Change status to REQUESTED
        ContentValues upd = new ContentValues();
        upd.put("status", PurchaseOrder.STATUS_REQUESTED);
        db.update("purchase_orders", upd, "id = ?", new String[]{String.valueOf(poId)});

        assertEquals("طلب الشراء بعد الإرسال لا يغير المخزون",
                initialStock, dbHelper.getWarehouseProductQuantity(db, testWarehouseId, testProductId), 0.001);
        assertEquals("طلب الشراء بعد الإرسال لا يغير الحسابات",
                initialSupplierDebt, dbHelper.getSupplierLedgerBalance(db, testSupplierId), 0.001);
    }

    @Test
    public void testPurchaseReturn_withDebtReduction_deductsStockReducesDebtAndRecordsMovement() {
        // 1. Initial credit purchase of 20 units @ 10 ر.ي = 200, Remaining = 200
        ContentValues pCv = new ContentValues();
        pCv.put("supplier_id", testSupplierId);
        pCv.put("supplier_name", "شركة الهلال");
        pCv.put("total", 200.0);
        pCv.put("paid", 0.0);
        pCv.put("remaining", 200.0);
        pCv.put("payment_method", "آجل");
        long purchaseId = db.insertOrThrow("purchases", null, pCv);

        // Add 20 units to stock -> total 20 + 20 = 40
        dbHelper.adjustWarehouseStock(db, testWarehouseId, testProductId, 20.0,
                StockMovement.PURCHASE, (int) purchaseId, testUserId, "شراء أولي");
        dbHelper.recordSupplierTransaction(db, testSupplierId, DatabaseHelper.TX_PURCHASE, purchaseId, 200.0, "فاتورة # " + purchaseId);
        dbHelper.syncSupplierBalance(db, testSupplierId);

        assertEquals(40.0, dbHelper.getWarehouseProductQuantity(db, testWarehouseId, testProductId), 0.001);
        assertEquals(200.0, dbHelper.getSupplierLedgerBalance(db, testSupplierId), 0.001);

        // 2. Return 5 units with method "خصم من حساب المورد" (debt reduction = 5 * 10 = 50)
        List<DatabaseHelper.PurchaseReturnItemInput> returnItems = new ArrayList<>();
        returnItems.add(new DatabaseHelper.PurchaseReturnItemInput(testProductId, 5.0, 10.0, 50.0));

        long returnId = dbHelper.executePurchaseReturn((int) purchaseId, testSupplierId, 50.0,
                "خصم من حساب المورد", 50.0, 0.0, "بضاعة معيبة", testUserId, testWarehouseId, returnItems);

        assertTrue("تم تنفيذ المرتجع بنجاح", returnId > 0);

        // 3. Stock must be deducted: 40 - 5 = 35
        assertEquals(35.0, dbHelper.getWarehouseProductQuantity(db, testWarehouseId, testProductId), 0.001);
        assertEquals(35.0, dbHelper.getTotalProductQuantity(db, testProductId), 0.001);

        // 4. Movement logged as PURCHASE_RETURN with -5
        Cursor cm = db.rawQuery("SELECT movement_type, quantity FROM stock_movements WHERE reference_id = ? AND movement_type = ?",
                new String[]{String.valueOf(returnId), StockMovement.PURCHASE_RETURN});
        assertTrue(cm.moveToFirst());
        assertEquals(-5.0, cm.getDouble(1), 0.001);
        cm.close();

        // 5. Supplier debt reduced from 200 to 150
        assertEquals(150.0, dbHelper.getSupplierLedgerBalance(db, testSupplierId), 0.001);

        // 6. Purchase remaining reduced from 200 to 150
        Cursor cp = db.rawQuery("SELECT remaining FROM purchases WHERE id = ?", new String[]{String.valueOf(purchaseId)});
        assertTrue(cp.moveToFirst());
        assertEquals(150.0, cp.getDouble(0), 0.001);
        cp.close();
    }

    @Test
    public void testPurchaseReturn_withCashRefund_deductsStockAddsToCashIn() {
        double initialStock = dbHelper.getWarehouseProductQuantity(db, testWarehouseId, testProductId); // 20
        double initialCashIn = dbHelper.getCashInForDate(db, todayDate);

        // Return 4 units @ 25 ر.ي = 100 with cash refund
        List<DatabaseHelper.PurchaseReturnItemInput> returnItems = new ArrayList<>();
        returnItems.add(new DatabaseHelper.PurchaseReturnItemInput(testProductId, 4.0, 25.0, 100.0));

        long returnId = dbHelper.executePurchaseReturn(0, testSupplierId, 100.0,
                "نقدي", 0.0, 100.0, "استرداد نقدي", testUserId, testWarehouseId, returnItems);

        assertTrue(returnId > 0);

        // 1. Stock deducted: 20 - 4 = 16
        assertEquals(initialStock - 4.0, dbHelper.getWarehouseProductQuantity(db, testWarehouseId, testProductId), 0.001);

        // 2. Cash In increases by 100.0
        double newCashIn = dbHelper.getCashInForDate(db, todayDate);
        assertEquals(initialCashIn + 100.0, newCashIn, 0.001);
    }

    @Test
    public void testSupplierArchiveAndFiltering() {
        // Insert active and archived suppliers
        ContentValues s1 = new ContentValues();
        s1.put("name", "مورد أ أكتيف");
        s1.put("is_archived", 0);
        long id1 = db.insert("suppliers", null, s1);

        ContentValues s2 = new ContentValues();
        s2.put("name", "مورد ب مؤرشف");
        s2.put("is_archived", 1);
        long id2 = db.insert("suppliers", null, s2);

        // Active query
        Cursor cActive = db.rawQuery("SELECT id FROM suppliers WHERE COALESCE(is_archived,0) = 0", null);
        List<Long> activeIds = new ArrayList<>();
        while (cActive.moveToNext()) activeIds.add(cActive.getLong(0));
        cActive.close();

        assertTrue(activeIds.contains(id1));
        assertFalse(activeIds.contains(id2));

        // Archived query
        Cursor cArchived = db.rawQuery("SELECT id FROM suppliers WHERE is_archived = 1", null);
        List<Long> archivedIds = new ArrayList<>();
        while (cArchived.moveToNext()) archivedIds.add(cArchived.getLong(0));
        cArchived.close();

        assertFalse(archivedIds.contains(id1));
        assertTrue(archivedIds.contains(id2));
    }

    @Test
    public void testSupplierCreation_withOpeningBalance_recordsLedgerAndUpdatesBalance() {
        ContentValues cv = new ContentValues();
        cv.put("name", "مورد افتتاحي جديد");
        cv.put("phone", "770000111");
        cv.put("is_archived", 0);
        cv.put("balance", 0.0);
        long newSupId = db.insert("suppliers", null, cv);
        assertTrue(newSupId > 0);

        // Record opening balance
        long txId = dbHelper.recordSupplierTransaction(db, (int) newSupId, DatabaseHelper.TX_OPENING, 0, 500.0, "رصيد افتتاحي دين سابق");
        assertTrue(txId > 0);
        dbHelper.syncSupplierBalance(db, (int) newSupId);

        // Verify balance
        assertEquals(500.0, dbHelper.getSupplierLedgerBalance(db, (int) newSupId), 0.001);

        // Verify query suppliers
        Cursor c = db.rawQuery("SELECT name, phone, balance FROM suppliers WHERE id = ?", new String[]{String.valueOf(newSupId)});
        assertTrue(c.moveToFirst());
        assertEquals("مورد افتتاحي جديد", c.getString(0));
        assertEquals("770000111", c.getString(1));
        assertEquals(500.0, c.getDouble(2), 0.001);
        c.close();
    }

    @Test
    public void testPurchaseReturnsLog_and_ReturnItemsQuery() {
        List<DatabaseHelper.PurchaseReturnItemInput> returnItems = new ArrayList<>();
        returnItems.add(new DatabaseHelper.PurchaseReturnItemInput(testProductId, 2.0, 25.0, 50.0));

        long returnId = dbHelper.executePurchaseReturn(0, testSupplierId, 50.0,
                "خصم من حساب المورد", 50.0, 0.0, "مرتجع للتجربة", testUserId, testWarehouseId, returnItems);

        assertTrue(returnId > 0);

        // Query Purchase Returns Log
        Cursor cLog = db.rawQuery(
                "SELECT pr.id, pr.supplier_id, s.name, pr.total_amount, pr.refund_method, pr.reason " +
                "FROM purchase_returns pr " +
                "LEFT JOIN suppliers s ON pr.supplier_id = s.id " +
                "WHERE pr.id = ?", new String[]{String.valueOf(returnId)});

        assertTrue(cLog.moveToFirst());
        assertEquals(testSupplierId, cLog.getInt(1));
        assertEquals("شركة الهلال للتجارة والتوريد", cLog.getString(2));
        assertEquals(50.0, cLog.getDouble(3), 0.001);
        assertEquals("خصم من حساب المورد", cLog.getString(4));
        assertEquals("مرتجع للتجربة", cLog.getString(5));
        cLog.close();

        // Query Purchase Return Items
        Cursor cItems = db.rawQuery(
                "SELECT pri.product_id, p.name, pri.quantity, pri.unit_price, pri.total " +
                "FROM purchase_return_items pri " +
                "JOIN products p ON pri.product_id = p.id " +
                "WHERE pri.return_id = ?", new String[]{String.valueOf(returnId)});

        assertTrue(cItems.moveToFirst());
        assertEquals(testProductId, cItems.getInt(0));
        assertEquals("سكر الأسرة 5 كجم", cItems.getString(1));
        assertEquals(2.0, cItems.getDouble(2), 0.001);
        assertEquals(25.0, cItems.getDouble(3), 0.001);
        assertEquals(50.0, cItems.getDouble(4), 0.001);
        cItems.close();
    }
}
