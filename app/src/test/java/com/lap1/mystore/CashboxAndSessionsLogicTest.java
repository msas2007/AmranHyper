package com.lap1.mystore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.models.User;
import com.lap1.mystore.utils.SessionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CashboxAndSessionsLogicTest {

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
    public void testOtherIncomeAndCashboxFlow() {
        // 1. Initial cash in should be 0
        double initialCashIn = dbHelper.getCashInForDate(db, todayDate);
        double initialNetIn = dbHelper.getNetworkInForDate(db, todayDate);

        // 2. Add cash income
        long inc1 = dbHelper.addOtherIncome("عمولة خدمات", "عمولات", 1500.0, "Cash", "ملاحظة 1", 1);
        assertTrue("Cash income should be inserted", inc1 > 0);

        // 3. Add network income
        long inc2 = dbHelper.addOtherIncome("إيجار منصة", "إيجارات", 5000.0, "Network", "ملاحظة 2", 1);
        assertTrue("Network income should be inserted", inc2 > 0);

        // 4. Verify cash in reflects 1500 and network in reflects 5000
        double updatedCashIn = dbHelper.getCashInForDate(db, todayDate);
        double updatedNetIn = dbHelper.getNetworkInForDate(db, todayDate);

        assertEquals("Cash-in should increase by exactly 1500", initialCashIn + 1500.0, updatedCashIn, 0.001);
        assertEquals("Network-in should increase by exactly 5000", initialNetIn + 5000.0, updatedNetIn, 0.001);
    }

    @Test
    public void testExpensesPaymentMethodFiltering() {
        // 1. Initial cash out
        double initialCashOut = dbHelper.getCashOutForDate(db, todayDate);

        // 2. Add Cash expense
        ContentValues expCash = new ContentValues();
        expCash.put("title", "فاتورة مياه");
        expCash.put("amount", 800.0);
        expCash.put("category", "كهرباء ومياه");
        expCash.put("payment_method", "Cash");
        db.insert("expenses", null, expCash);

        // 3. Add Network expense
        ContentValues expNet = new ContentValues();
        expNet.put("title", "اشتراك إنترنت");
        expNet.put("amount", 2500.0);
        expNet.put("category", "أخرى");
        expNet.put("payment_method", "Network");
        db.insert("expenses", null, expNet);

        // 4. Verify cash out only includes Cash expense (800)
        double updatedCashOut = dbHelper.getCashOutForDate(db, todayDate);
        assertEquals("Cash-out should increase by exactly 800 (cash expense only)", initialCashOut + 800.0, updatedCashOut, 0.001);
    }

    @Test
    public void testCashSessionOpenAndDuplicatePrevention() {
        // 1. Verify no open session initially
        assertFalse("Should not have open session initially", dbHelper.hasOpenSession());

        // 2. Open a session with opening cash 10,000
        long sessionId = dbHelper.openCashSession(10000.0, 1);
        assertTrue("Session ID should be valid", sessionId > 0);
        assertTrue("Should have open session now", dbHelper.hasOpenSession());

        // 3. Attempting to open another session should be rejected (-2)
        long duplicateId = dbHelper.openCashSession(5000.0, 1);
        assertEquals("Duplicate open session should return -2", -2, duplicateId);

        // 4. Retrieve open session cursor and verify
        Cursor c = dbHelper.getOpenSession();
        assertNotNull(c);
        assertTrue(c.moveToFirst());
        assertEquals("Status should be OPEN", "OPEN", c.getString(c.getColumnIndexOrThrow("status")));
        assertEquals(10000.0, c.getDouble(c.getColumnIndexOrThrow("opening_cash")), 0.001);
        c.close();
    }

    @Test
    public void testCashSessionCloseAndExpectedCashCalculation() {
        // 1. Open session
        long sessionId = dbHelper.openCashSession(2000.0, 1);
        assertTrue(sessionId > 0);

        // 2. Close session: expected = 2000, physical = 2050 (surplus of +50)
        boolean closed = dbHelper.closeCurrentSession(sessionId, 2050.0, 2000.0, 1, "وردية الصباح");
        assertTrue("Session should be closed successfully", closed);
        assertFalse("Should not have open session after close", dbHelper.hasOpenSession());

        // 3. Verify closed session record
        Cursor c = db.rawQuery("SELECT status, opening_cash, physical_cash, expected_cash, difference, closed_at FROM cash_sessions WHERE id = ?",
                new String[]{String.valueOf(sessionId)});
        assertTrue(c.moveToFirst());
        assertEquals("Status should be CLOSED", "CLOSED", c.getString(0));
        assertEquals(2000.0, c.getDouble(1), 0.001);
        assertEquals(2050.0, c.getDouble(2), 0.001);
        assertEquals(2000.0, c.getDouble(3), 0.001);
        assertEquals(50.0, c.getDouble(4), 0.001);
        assertNotNull("Closed at timestamp should be populated", c.getString(5));
        c.close();
    }

    @Test
    public void testUserManagementAndInactiveLogin() {
        // 1. Add users with different roles
        long u1 = dbHelper.addUser("cashier1", "123456", "كاشير الصباح", "CASHIER");
        long u2 = dbHelper.addUser("manager1", "123456", "مدير الفرع", "MANAGER");
        long u3 = dbHelper.addUser("storekeeper1", "123456", "أمين المخزن", "STOREKEEPER");

        assertTrue(u1 > 0);
        assertTrue(u2 > 0);
        assertTrue(u3 > 0);

        // 2. Verify all users retrieval
        List<User> users = dbHelper.getAllUsers();
        assertTrue("Should have at least 4 users (admin + 3 new)", users.size() >= 4);

        // 3. Successful login for active user
        User loggedUser = dbHelper.loginAndGetUser("cashier1", "123456");
        assertNotNull("Active user should be able to login", loggedUser);
        assertEquals("cashier1", loggedUser.getUsername());
        assertEquals("CASHIER", loggedUser.getRole());
        assertTrue(loggedUser.isActive());

        // 4. Deactivate user and verify login is blocked
        boolean toggled = dbHelper.toggleUserActive((int) u1);
        assertTrue(toggled);

        User blockedUser = dbHelper.loginAndGetUser("cashier1", "123456");
        assertNull("Inactive user should NOT be able to login", blockedUser);

        // 5. Reactivate user and verify login works again
        dbHelper.toggleUserActive((int) u1);
        User reactivatedUser = dbHelper.loginAndGetUser("cashier1", "123456");
        assertNotNull("Reactivated user should be able to login", reactivatedUser);
    }

    @Test
    public void testNoDoubleCountingInCashbox() {
        // 1. Record a cash sale
        ContentValues sale = new ContentValues();
        sale.put("invoice_number", "INV-TEST-001");
        sale.put("total", 3000.0);
        sale.put("discount", 0.0);
        sale.put("paid", 3000.0);
        sale.put("payment_method", "كاش");
        sale.put("notes", "عميل نقدي");
        long saleId = db.insert("sales", null, sale);
        assertTrue(saleId > 0);

        // 2. Record other income
        long incId = dbHelper.addOtherIncome("إيراد خدمات", "خدمات", 1000.0, "Cash", null, 1);
        assertTrue(incId > 0);

        // 3. Record expense
        ContentValues exp = new ContentValues();
        exp.put("title", "شراء أدوات");
        exp.put("amount", 500.0);
        exp.put("category", "أخرى");
        exp.put("payment_method", "Cash");
        long expId = db.insert("expenses", null, exp);
        assertTrue(expId > 0);

        // 4. Verify exact aggregation without duplication
        double totalCashIn = dbHelper.getCashInForDate(db, todayDate);
        double totalCashOut = dbHelper.getCashOutForDate(db, todayDate);

        assertEquals("Total cash in must equal sale (3000) + other_income (1000)", 4000.0, totalCashIn, 0.001);
        assertEquals("Total cash out must equal expense (500)", 500.0, totalCashOut, 0.001);
    }
}
