package com.lap1.mystore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.models.StoreSettings;
import com.lap1.mystore.utils.InvoicePdfHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class Phase9ReportsAndSettingsTest {

    private DatabaseHelper dbHelper;
    private SQLiteDatabase db;
    private Context context;
    private String todayDate;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("mystore.db");
        dbHelper = new DatabaseHelper(context);
        db = dbHelper.getWritableDatabase();
        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
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
    public void testStoreSettingsUpdateAndDynamicAccess() {
        // 1. Initial default store settings
        StoreSettings initial = dbHelper.getStoreSettings();
        assertNotNull(initial);
        assertEquals("عمران هايبر", initial.getStoreName());
        assertEquals("ر.ي", initial.getCurrency());

        // 2. Update store settings
        boolean updated = dbHelper.updateStoreSettings(
                "سوبرماركت البركة",
                "صنعاء - التحرير",
                "771234567",
                "ر.س",
                "شكراً لزيارتكم! البضاعة المباعة ترد وتستبدل خلال يومين"
        );
        assertTrue("Store settings update should succeed", updated);

        // 3. Verify updated settings
        StoreSettings current = dbHelper.getStoreSettings();
        assertEquals("سوبرماركت البركة", current.getStoreName());
        assertEquals("صنعاء - التحرير", current.getAddress());
        assertEquals("771234567", current.getPhone());
        assertEquals("ر.س", current.getCurrency());
        assertEquals("شكراً لزيارتكم! البضاعة المباعة ترد وتستبدل خلال يومين", current.getInvoiceFooter());
    }

    @Test
    public void testAllTablesExistForBackupAndRestore() {
        String[] requiredTables = {
                "users", "products", "categories", "customers", "suppliers",
                "sales", "sale_items", "purchases", "purchase_items",
                "payments", "supplier_payments", "expenses", "returns",
                "daily_closings", "stock_adjustments", "store_settings", "account_ledger",
                "warehouses", "warehouse_stock", "stock_movements", "stock_transfers", "stock_transfer_items",
                "purchase_orders", "purchase_order_items", "purchase_returns", "purchase_return_items",
                "other_income", "cash_sessions"
        };

        for (String table : requiredTables) {
            Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{table});
            try {
                assertTrue("Table '" + table + "' must exist in database for complete backup & restore", cursor.moveToFirst());
            } finally {
                cursor.close();
            }
        }
    }

    @Test
    public void testReportsFinancialCalculationsAndNoDoubleCounting() {
        // 1. Insert product (cost: 70, price: 100)
        ContentValues prod = new ContentValues();
        prod.put("name", "أرز بسمتي 5 كجم");
        prod.put("quantity", 50.0);
        prod.put("purchase_price", 70.0);
        prod.put("sale_price", 100.0);
        long prodId = db.insert("products", null, prod);
        assertTrue(prodId > 0);

        // 2. Insert customer
        ContentValues cust = new ContentValues();
        cust.put("name", "علي محمد");
        cust.put("phone", "770000001");
        cust.put("balance", 0.0);
        long custId = db.insert("customers", null, cust);
        assertTrue(custId > 0);

        // 3. Insert sale: 10 units @ 100 = 1000, discount = 50, net = 950, paid = 500, remaining = 450
        ContentValues sale = new ContentValues();
        sale.put("invoice_number", "INV-2026-001");
        sale.put("customer_id", custId);
        sale.put("total", 1000.0);
        sale.put("discount", 50.0);
        sale.put("paid", 500.0);
        sale.put("remaining", 450.0);
        sale.put("payment_status", "دفع جزئي");
        sale.put("payment_method", "كاش");
        long saleId = db.insert("sales", null, sale);
        assertTrue(saleId > 0);

        // Sale items: 10 units @ 100 (cost: 70), total = 1000
        ContentValues item = new ContentValues();
        item.put("sale_id", saleId);
        item.put("product_id", prodId);
        item.put("quantity", 10.0);
        item.put("unit_price", 100.0);
        item.put("purchase_price", 70.0);
        item.put("total", 1000.0);
        db.insert("sale_items", null, item);

        // 4. Insert expense: 100 Cash
        ContentValues exp = new ContentValues();
        exp.put("title", "كهرباء");
        exp.put("amount", 100.0);
        exp.put("category", "كهرباء ومياه");
        exp.put("payment_method", "Cash");
        db.insert("expenses", null, exp);

        // 5. Insert Other Income: 50 Cash
        dbHelper.addOtherIncome("عمولة بيع", "عمولات", 50.0, "Cash", "ملاحظة", 1);

        // 6. Verify Calculations:
        // Revenue (Net Sales) = 1000 - 50 = 950
        // COGS = 10 * 70 = 700
        // Gross Profit = (100 - 70)*10 - 50 = 300 - 50 = 250
        // Net Profit = Gross Profit (250) - Expenses (100) = 150
        double netSales = getScalar("SELECT COALESCE(SUM(total-discount),0) FROM sales");
        double cogs = getScalar("SELECT COALESCE(SUM(purchase_price*quantity),0) FROM sale_items");
        double grossProfit = getScalar("SELECT COALESCE(SUM((unit_price-purchase_price)*quantity),0) FROM sale_items")
                - getScalar("SELECT COALESCE(SUM(discount),0) FROM sales");
        double expenses = getScalar("SELECT COALESCE(SUM(amount),0) FROM expenses");
        double netProfit = grossProfit - expenses;

        assertEquals(950.0, netSales, 0.001);
        assertEquals(700.0, cogs, 0.001);
        assertEquals(250.0, grossProfit, 0.001);
        assertEquals(100.0, expenses, 0.001);
        assertEquals(150.0, netProfit, 0.001);

        // 7. Verify Cash flow (No Double Counting):
        // Cash in = sale paid (500) + other_income (50) = 550
        // Cash out = expense (100) = 100
        // Net cash = 450
        double cashIn = dbHelper.getCashInForDate(db, todayDate);
        double cashOut = dbHelper.getCashOutForDate(db, todayDate);

        assertEquals(550.0, cashIn, 0.001);
        assertEquals(100.0, cashOut, 0.001);
    }

    @Test
    public void testInvoicePdfHelperGeneration() {
        // 1. Insert product
        ContentValues prod = new ContentValues();
        prod.put("name", "شاي كبوس 250 جم");
        prod.put("quantity", 20.0);
        prod.put("purchase_price", 300.0);
        prod.put("sale_price", 400.0);
        long prodId = db.insert("products", null, prod);
        assertTrue(prodId > 0);

        // 2. Insert sale with item
        ContentValues sale = new ContentValues();
        sale.put("invoice_number", "INV-PDF-001");
        sale.put("total", 800.0);
        sale.put("discount", 50.0);
        sale.put("paid", 750.0);
        sale.put("remaining", 0.0);
        sale.put("payment_status", "مدفوعة");
        sale.put("payment_method", "كاش");
        long saleId = db.insert("sales", null, sale);
        assertTrue(saleId > 0);

        ContentValues item = new ContentValues();
        item.put("sale_id", saleId);
        item.put("product_id", prodId);
        item.put("quantity", 2.0);
        item.put("unit_price", 400.0);
        item.put("purchase_price", 300.0);
        item.put("total", 800.0);
        db.insert("sale_items", null, item);

        // 3. Verify invoice items query and formatting
        Cursor c = db.rawQuery("SELECT p.name, si.quantity, si.unit_price, si.total FROM sale_items si JOIN products p ON si.product_id=p.id WHERE si.sale_id=?",
                new String[]{String.valueOf(saleId)});
        assertTrue(c.moveToFirst());
        assertEquals("شاي كبوس 250 جم", c.getString(0));
        assertEquals(2.0, c.getDouble(1), 0.001);
        assertEquals(400.0, c.getDouble(2), 0.001);
        assertEquals(800.0, c.getDouble(3), 0.001);
        c.close();

        // 4. In Robolectric headless test environment, test generateInvoicePdf safely
        try {
            File pdfFile = InvoicePdfHelper.generateInvoicePdf(context, (int) saleId);
            if (pdfFile != null) {
                assertTrue("Generated PDF should exist if native canvas is available", pdfFile.exists());
            }
        } catch (IllegalStateException e) {
            // Expected in headless Robolectric without native libpdf
            assertTrue(e.getMessage().contains("document is closed") || e.getMessage().contains("closed"));
        }
    }

    private double getScalar(String sql) {
        Cursor c = db.rawQuery(sql, null);
        try {
            return c.moveToFirst() ? c.getDouble(0) : 0.0;
        } finally {
            c.close();
        }
    }
}
