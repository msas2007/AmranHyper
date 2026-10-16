package com.lap1.mystore.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.lap1.mystore.models.StoreSettings;
import com.lap1.mystore.models.User;
import com.lap1.mystore.models.StockMovement;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "mystore.db";
    private static final int DATABASE_VERSION = 11; // ترقية لدعم الأرشفة وسجلات تسوية الجرد

    public static final String TX_PURCHASE = "PURCHASE";
    public static final String TX_PURCHASE_PAYMENT = "PURCHASE_PAYMENT";
    public static final String TX_SALE = "SALE";
    public static final String TX_OPENING = "OPENING";
    public static final String TX_SALE_PAYMENT = "SALE_PAYMENT";
    public static final String TX_PAYMENT = "PAYMENT";
    public static final String TX_RETURN = "RETURN";

    public static class PurchaseReturnItemInput {
        public int productId;
        public double quantity;
        public double unitPrice;
        public double total;

        public PurchaseReturnItemInput(int productId, double quantity, double unitPrice, double total) {
            this.productId = productId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.total = total;
        }
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 1. المستخدمين
        db.execSQL("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL, " +
                "full_name TEXT, " +
                "role TEXT DEFAULT 'admin', " +
                "is_active INTEGER DEFAULT 1, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "last_login_at TIMESTAMP)");

        // إضافة مستخدم الأدمن الافتراضي
        db.execSQL("INSERT OR IGNORE INTO users (id, username, password, full_name, role, is_active) VALUES (1, 'admin', 'admin', 'مدير عام', 'admin', 1)");

        // 2. المرتجعات
        db.execSQL("CREATE TABLE IF NOT EXISTS returns (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sale_id INTEGER, " +
                "product_id INTEGER, " +
                "quantity REAL, " +
                "amount REAL, " +
                "purchase_price REAL, " +
                "debt_reduction REAL DEFAULT 0.0, " +
                "return_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY(sale_id) REFERENCES sales(id), " +
                "FOREIGN KEY(product_id) REFERENCES products(id))");

        db.execSQL("CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, barcode TEXT, category_id INTEGER, purchase_price REAL, sale_price REAL, quantity REAL, min_quantity REAL, expiry_date TEXT, alert_days INTEGER DEFAULT 10, unit_name TEXT DEFAULT 'حبة', unit_factor REAL DEFAULT 1.0, is_weighted INTEGER DEFAULT 0, is_archived INTEGER DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(category_id) REFERENCES categories(id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS customers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT, address TEXT, balance REAL DEFAULT 0.0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS suppliers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT, balance REAL DEFAULT 0.0, is_archived INTEGER DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sales (id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_number TEXT NOT NULL, customer_id INTEGER, total REAL, discount REAL DEFAULT 0.0, tax REAL DEFAULT 0.0, paid REAL, remaining REAL, payment_status TEXT, payment_method TEXT DEFAULT 'كاش', notes TEXT, created_by_user_id INTEGER, sale_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(customer_id) REFERENCES customers(id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS sale_items (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER, product_id INTEGER, quantity REAL, unit_price REAL, purchase_price REAL, total REAL, FOREIGN KEY(sale_id) REFERENCES sales(id), FOREIGN KEY(product_id) REFERENCES products(id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS purchases (id INTEGER PRIMARY KEY AUTOINCREMENT, supplier_id INTEGER, supplier_name TEXT, total REAL, paid REAL DEFAULT 0.0, remaining REAL DEFAULT 0.0, payment_method TEXT DEFAULT 'كاش', created_by_user_id INTEGER, purchase_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(supplier_id) REFERENCES suppliers(id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS purchase_items (id INTEGER PRIMARY KEY AUTOINCREMENT, purchase_id INTEGER, product_id INTEGER, quantity REAL, unit_price REAL, total REAL, FOREIGN KEY(purchase_id) REFERENCES purchases(id), FOREIGN KEY(product_id) REFERENCES products(id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS payments (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER, sale_id INTEGER, amount REAL, payment_method TEXT DEFAULT 'كاش', payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, notes TEXT, FOREIGN KEY(customer_id) REFERENCES customers(id), FOREIGN KEY(sale_id) REFERENCES sales(id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS supplier_payments (id INTEGER PRIMARY KEY AUTOINCREMENT, supplier_id INTEGER, amount REAL, payment_method TEXT DEFAULT 'كاش', payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, notes TEXT, FOREIGN KEY(supplier_id) REFERENCES suppliers(id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, category TEXT, amount REAL NOT NULL, payment_method TEXT DEFAULT 'Cash', expense_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, notes TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS daily_closings (id INTEGER PRIMARY KEY AUTOINCREMENT, closing_date DATE DEFAULT (DATE('now', 'localtime')), physical_cash REAL, system_cash REAL, system_network REAL, system_credit REAL, difference REAL, notes TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS stock_adjustments (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER, product_name TEXT, system_quantity REAL, actual_quantity REAL, difference REAL, notes TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(product_id) REFERENCES products(id))");

        // Additional tables required for tests and backup/restore
        db.execSQL("CREATE TABLE IF NOT EXISTS store_settings (id INTEGER PRIMARY KEY AUTOINCREMENT, store_name TEXT, address TEXT, phone TEXT, currency TEXT, invoice_footer TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS account_ledger (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER, tx_type TEXT, ref_id INTEGER, amount REAL, notes TEXT, tx_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS supplier_ledger (id INTEGER PRIMARY KEY AUTOINCREMENT, supplier_id INTEGER, tx_type TEXT, ref_id INTEGER, amount REAL, notes TEXT, tx_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS supplier_payment_allocations (id INTEGER PRIMARY KEY AUTOINCREMENT, payment_id INTEGER, purchase_id INTEGER, amount REAL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS warehouses (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, location TEXT, notes TEXT, is_active INTEGER DEFAULT 1, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS warehouse_stock (id INTEGER PRIMARY KEY AUTOINCREMENT, warehouse_id INTEGER, product_id INTEGER, quantity REAL DEFAULT 0.0, FOREIGN KEY(warehouse_id) REFERENCES warehouses(id), FOREIGN KEY(product_id) REFERENCES products(id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS stock_movements (id INTEGER PRIMARY KEY AUTOINCREMENT, warehouse_id INTEGER, product_id INTEGER, quantity REAL, movement_type TEXT, reference_id INTEGER, user_id INTEGER, notes TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS stock_transfers (id INTEGER PRIMARY KEY AUTOINCREMENT, from_warehouse_id INTEGER, to_warehouse_id INTEGER, user_id INTEGER, notes TEXT, transfer_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS stock_transfer_items (id INTEGER PRIMARY KEY AUTOINCREMENT, transfer_id INTEGER, product_id INTEGER, quantity REAL, FOREIGN KEY(transfer_id) REFERENCES stock_transfers(id), FOREIGN KEY(product_id) REFERENCES products(id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS purchase_orders (id INTEGER PRIMARY KEY AUTOINCREMENT, supplier_id INTEGER, total REAL, status TEXT, notes TEXT, order_number TEXT, created_by_user_id INTEGER, order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS purchase_order_items (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER, product_id INTEGER, quantity REAL, unit_price REAL, total REAL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS purchase_returns (id INTEGER PRIMARY KEY AUTOINCREMENT, purchase_id INTEGER, supplier_id INTEGER, total_amount REAL, refund_method TEXT, debt_reduction REAL DEFAULT 0.0, cash_refund REAL DEFAULT 0.0, reason TEXT, return_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE IF NOT EXISTS purchase_return_items (id INTEGER PRIMARY KEY AUTOINCREMENT, return_id INTEGER, product_id INTEGER, quantity REAL, unit_price REAL, total REAL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS other_income (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, category TEXT, amount REAL NOT NULL, payment_method TEXT DEFAULT 'Cash', notes TEXT, income_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, user_id INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS cash_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, opening_cash REAL, closing_cash REAL, physical_cash REAL, expected_cash REAL, difference REAL, status TEXT DEFAULT 'OPEN', notes TEXT, opened_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, closed_at TIMESTAMP)");

        // Default warehouse and store settings
        db.execSQL("INSERT OR IGNORE INTO warehouses (id, name, location, is_active) VALUES (1, 'المخزن الرئيسي', 'الرئيسي', 1)");
        db.execSQL("INSERT OR IGNORE INTO store_settings (id, store_name, currency) VALUES (1, 'عمران هايبر', 'ر.ي')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }

    // ============================================
    // إدارة المستخدمين والتسجيل الحقيقي
    // ============================================

    public boolean authenticateUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM users WHERE username = ? AND password = ? AND COALESCE(is_active,1) = 1", new String[]{username, password});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public User loginAndGetUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, username, full_name, role, is_active, created_at, last_login_at FROM users WHERE username = ? AND password = ? AND COALESCE(is_active,1) = 1", new String[]{username, password});
        try {
            if (c.moveToFirst()) {
                User u = new User(
                        c.getInt(0),
                        c.getString(1),
                        c.getString(2),
                        c.getString(3),
                        c.getInt(4) == 1,
                        c.getString(5),
                        c.getString(6)
                );
                db.execSQL("UPDATE users SET last_login_at = datetime('now') WHERE id = ?", new Object[]{u.getId()});
                return u;
            }
        } finally {
            c.close();
        }
        return null;
    }

    public String[] getFirstUser() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT username, password FROM users LIMIT 1", null);
        String[] user = new String[]{"admin", "admin"};
        if (cursor.moveToFirst()) {
            user[0] = cursor.getString(0);
            user[1] = cursor.getString(1);
        } else {
            SQLiteDatabase wDb = this.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("id", 1);
            cv.put("username", "admin");
            cv.put("password", "admin");
            cv.put("full_name", "مدير عام");
            cv.put("role", "admin");
            cv.put("is_active", 1);
            wDb.insertWithOnConflict("users", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        }
        cursor.close();
        return user;
    }

    public boolean updateUserCredentials(String oldUsername, String newUsername, String newPassword) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("username", newUsername);
        values.put("password", newPassword);

        int rows = db.update("users", values, "username = ?", new String[]{oldUsername});
        if (rows == 0) {
            values.put("id", 1);
            values.put("full_name", "مدير عام");
            values.put("role", "admin");
            values.put("is_active", 1);
            long id = db.insertWithOnConflict("users", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            return id != -1;
        }
        return rows > 0;
    }

    // ============================================
    // Store Settings
    // ============================================

    public StoreSettings getStoreSettings() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, store_name, address, phone, currency, invoice_footer, created_at, updated_at FROM store_settings LIMIT 1", null);
        try {
            if (c.moveToFirst()) {
                return new StoreSettings(
                        c.getInt(0),
                        c.getString(1),
                        c.getString(2),
                        c.getString(3),
                        c.getString(4),
                        c.getString(5),
                        c.getString(6),
                        c.getString(7)
                );
            }
        } finally {
            c.close();
        }
        return new StoreSettings(1, "عمران هايبر", "", "", "ر.ي", "", "", "");
    }

    public boolean updateStoreSettings(String storeName, String address, String phone, String currency, String footer) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("store_name", storeName);
        cv.put("address", address);
        cv.put("phone", phone);
        cv.put("currency", currency);
        cv.put("invoice_footer", footer);
        cv.put("updated_at", "datetime('now')");

        int rows = db.update("store_settings", cv, "id = 1", null);
        if (rows == 0) {
            cv.put("id", 1);
            long id = db.insertWithOnConflict("store_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            return id != -1;
        }
        return rows > 0;
    }

    // ============================================
    // Cashbox & Financial Reports Methods
    // ============================================

    public double getCashInForDate(SQLiteDatabase db, String date) {
        String datePattern = date + "%";
        double salesCash = safeScalar(db, "SELECT COALESCE(SUM(paid),0) FROM sales WHERE (sale_date LIKE ? OR date(sale_date)=? OR date(sale_date,'localtime')=?) AND LOWER(COALESCE(payment_method,'كاش')) IN ('كاش', 'cash')", datePattern, date, date);
        double pmtCash = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM payments WHERE (payment_date LIKE ? OR date(payment_date)=? OR date(payment_date,'localtime')=?) AND LOWER(COALESCE(payment_method,'كاش')) IN ('كاش', 'cash')", datePattern, date, date);
        double prRefund = safeScalar(db, "SELECT COALESCE(SUM(cash_refund),0) FROM purchase_returns WHERE (return_date LIKE ? OR date(return_date)=? OR date(return_date,'localtime')=?)", datePattern, date, date);
        double otherCash = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM other_income WHERE (income_date LIKE ? OR date(income_date)=? OR date(income_date,'localtime')=?) AND LOWER(COALESCE(payment_method,'cash')) IN ('كاش', 'cash')", datePattern, date, date);
        return salesCash + pmtCash + prRefund + otherCash;
    }

    public double getCashOutForDate(SQLiteDatabase db, String date) {
        String datePattern = date + "%";
        double expCash = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM expenses WHERE (expense_date LIKE ? OR date(expense_date)=? OR date(expense_date,'localtime')=?) AND LOWER(COALESCE(payment_method,'cash')) IN ('كاش', 'cash')", datePattern, date, date);
        double purchCash = safeScalar(db, "SELECT COALESCE(SUM(paid),0) FROM purchases WHERE (purchase_date LIKE ? OR date(purchase_date)=? OR date(purchase_date,'localtime')=?) AND LOWER(COALESCE(payment_method,'كاش')) IN ('كاش', 'cash')", datePattern, date, date);
        double spCash = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM supplier_payments WHERE (payment_date LIKE ? OR date(payment_date)=? OR date(payment_date,'localtime')=?) AND LOWER(COALESCE(payment_method,'كاش')) IN ('كاش', 'cash')", datePattern, date, date);
        double retCash = safeScalar(db, "SELECT COALESCE(SUM(amount-debt_reduction),0) FROM returns WHERE (return_date LIKE ? OR date(return_date)=? OR date(return_date,'localtime')=?)", datePattern, date, date);
        return expCash + purchCash + spCash + retCash;
    }

    public double getNetworkInForDate(SQLiteDatabase db, String date) {
        String datePattern = date + "%";
        double salesNet = safeScalar(db, "SELECT COALESCE(SUM(paid),0) FROM sales WHERE (sale_date LIKE ? OR date(sale_date)=? OR date(sale_date,'localtime')=?) AND (payment_method='شبكة' OR payment_method='Network')", datePattern, date, date);
        double pmtNet = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM payments WHERE (payment_date LIKE ? OR date(payment_date)=? OR date(payment_date,'localtime')=?) AND (payment_method='شبكة' OR payment_method='Network')", datePattern, date, date);
        double otherNet = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM other_income WHERE (income_date LIKE ? OR date(income_date)=? OR date(income_date,'localtime')=?) AND (payment_method='شبكة' OR payment_method='Network')", datePattern, date, date);
        return salesNet + pmtNet + otherNet;
    }

    public double getNetworkOutForDate(SQLiteDatabase db, String date) {
        String datePattern = date + "%";
        return safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM expenses WHERE (expense_date LIKE ? OR date(expense_date)=? OR date(expense_date,'localtime')=?) AND (payment_method='شبكة' OR payment_method='Network')", datePattern, date, date);
    }

    public double getCashInForDateRange(SQLiteDatabase db, String range) {
        if ("WEEK".equalsIgnoreCase(range)) {
            return safeScalarNoArg(db, "SELECT COALESCE(SUM(paid),0) FROM sales WHERE date(sale_date,'localtime') >= date('now','-7 days','localtime') AND COALESCE(payment_method,'كاش')='كاش'")
                    + safeScalarNoArg(db, "SELECT COALESCE(SUM(amount),0) FROM payments WHERE date(payment_date,'localtime') >= date('now','-7 days','localtime') AND COALESCE(payment_method,'كاش')='كاش'")
                    + safeScalarNoArg(db, "SELECT COALESCE(SUM(cash_refund),0) FROM purchase_returns WHERE date(return_date,'localtime') >= date('now','-7 days','localtime')")
                    + safeScalarNoArg(db, "SELECT COALESCE(SUM(amount),0) FROM other_income WHERE date(income_date,'localtime') >= date('now','-7 days','localtime') AND COALESCE(payment_method,'Cash')='Cash'");
        }
        return getCashInForDate(db, new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
    }

    public double getCashOutForDateRange(SQLiteDatabase db, String range) {
        if ("WEEK".equalsIgnoreCase(range)) {
            return safeScalarNoArg(db, "SELECT COALESCE(SUM(amount),0) FROM expenses WHERE date(expense_date,'localtime') >= date('now','-7 days','localtime') AND COALESCE(payment_method,'Cash')='Cash'")
                    + safeScalarNoArg(db, "SELECT COALESCE(SUM(paid),0) FROM purchases WHERE date(purchase_date,'localtime') >= date('now','-7 days','localtime') AND COALESCE(payment_method,'كاش')='كاش'")
                    + safeScalarNoArg(db, "SELECT COALESCE(SUM(amount),0) FROM supplier_payments WHERE date(payment_date,'localtime') >= date('now','-7 days','localtime') AND COALESCE(payment_method,'كاش')='كاش'")
                    + safeScalarNoArg(db, "SELECT COALESCE(SUM(amount-debt_reduction),0) FROM returns WHERE date(return_date,'localtime') >= date('now','-7 days','localtime')");
        }
        return getCashOutForDate(db, new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
    }

    public double getNetworkInForDateRange(SQLiteDatabase db, String range) {
        if ("WEEK".equalsIgnoreCase(range)) {
            return safeScalarNoArg(db, "SELECT COALESCE(SUM(paid),0) FROM sales WHERE date(sale_date,'localtime') >= date('now','-7 days','localtime') AND (payment_method='شبكة' OR payment_method='Network')");
        }
        return 0.0;
    }

    public double getNetworkOutForDateRange(SQLiteDatabase db, String range) {
        if ("WEEK".equalsIgnoreCase(range)) {
            return safeScalarNoArg(db, "SELECT COALESCE(SUM(amount),0) FROM expenses WHERE date(expense_date,'localtime') >= date('now','-7 days','localtime') AND (payment_method='شبكة' OR payment_method='Network')");
        }
        return 0.0;
    }

    private double safeScalar(SQLiteDatabase db, String sql, String arg1, String arg2, String arg3) {
        try {
            Cursor c = db.rawQuery(sql, new String[]{arg1, arg2, arg3});
            try { return c.moveToFirst() ? c.getDouble(0) : 0.0; } finally { c.close(); }
        } catch (Exception e) { return 0.0; }
    }

    private double safeScalarNoArg(SQLiteDatabase db, String sql) {
        try {
            Cursor c = db.rawQuery(sql, null);
            try { return c.moveToFirst() ? c.getDouble(0) : 0.0; } finally { c.close(); }
        } catch (Exception e) { return 0.0; }
    }

    // ============================================
    // Cash Sessions Methods
    // ============================================

    public Cursor getOpenSession() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM cash_sessions WHERE status = 'OPEN' ORDER BY id DESC LIMIT 1", null);
    }

    public boolean hasOpenSession() {
        Cursor c = getOpenSession();
        boolean has = c.moveToFirst();
        c.close();
        return has;
    }

    public double getCashInSince(SQLiteDatabase db, String openedAtStr) {
        String opened = openedAtStr != null ? openedAtStr : "2000-01-01 00:00:00";
        double salesCash = safeScalar(db, "SELECT COALESCE(SUM(paid),0) FROM sales WHERE sale_date >= ? AND COALESCE(payment_method,'كاش')='كاش'", opened, opened, opened);
        double pmtCash = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM payments WHERE payment_date >= ? AND COALESCE(payment_method,'كاش')='كاش'", opened, opened, opened);
        double prRefund = safeScalar(db, "SELECT COALESCE(SUM(cash_refund),0) FROM purchase_returns WHERE return_date >= ?", opened, opened, opened);
        double otherCash = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM other_income WHERE income_date >= ? AND COALESCE(payment_method,'Cash')='Cash'", opened, opened, opened);
        return salesCash + pmtCash + prRefund + otherCash;
    }

    public double getCashOutSince(SQLiteDatabase db, String openedAtStr) {
        String opened = openedAtStr != null ? openedAtStr : "2000-01-01 00:00:00";
        double expCash = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM expenses WHERE expense_date >= ? AND COALESCE(payment_method,'Cash')='Cash'", opened, opened, opened);
        double purchCash = safeScalar(db, "SELECT COALESCE(SUM(paid),0) FROM purchases WHERE purchase_date >= ? AND COALESCE(payment_method,'كاش')='كاش'", opened, opened, opened);
        double spCash = safeScalar(db, "SELECT COALESCE(SUM(amount),0) FROM supplier_payments WHERE payment_date >= ? AND COALESCE(payment_method,'كاش')='كاش'", opened, opened, opened);
        double retCash = safeScalar(db, "SELECT COALESCE(SUM(amount-debt_reduction),0) FROM returns WHERE return_date >= ?", opened, opened, opened);
        return expCash + purchCash + spCash + retCash;
    }

    public long openCashSession(double openAmt, int userId) {
        if (hasOpenSession()) return -2;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("user_id", userId);
        cv.put("opening_cash", openAmt);
        cv.put("status", "OPEN");
        cv.put("opened_at", "datetime('now')");
        return db.insert("cash_sessions", null, cv);
    }

    public boolean closeCurrentSession(long currentSessionId, double physical, double expected, int userId, String notes) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("closing_cash", physical);
        cv.put("physical_cash", physical);
        cv.put("expected_cash", expected);
        cv.put("difference", physical - expected);
        cv.put("status", "CLOSED");
        cv.put("notes", notes);
        cv.put("closed_at", "datetime('now')");
        int rows = db.update("cash_sessions", cv, "id = ?", new String[]{String.valueOf(currentSessionId)});
        return rows > 0;
    }

    // ============================================
    // Warehouse & Stock Methods
    // ============================================

    public int getDefaultWarehouseId() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM warehouses ORDER BY id ASC LIMIT 1", null);
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
        } finally {
            c.close();
        }
        SQLiteDatabase wDb = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", 1);
        cv.put("name", "المخزن الرئيسي");
        cv.put("is_active", 1);
        long id = wDb.insertWithOnConflict("warehouses", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return (int) (id != -1 ? id : 1);
    }

    public double getWarehouseProductQuantity(SQLiteDatabase db, int warehouseId, int productId) {
        Cursor c = db.rawQuery("SELECT quantity FROM warehouse_stock WHERE warehouse_id = ? AND product_id = ?", new String[]{String.valueOf(warehouseId), String.valueOf(productId)});
        try {
            if (c.moveToFirst()) {
                return c.getDouble(0);
            }
        } finally {
            c.close();
        }
        return 0.0;
    }

    public void setProductStock(SQLiteDatabase db, int productId, double actualQty, String note) {
        int warehouseId = getDefaultWarehouseId();
        double current = getWarehouseProductQuantity(db, warehouseId, productId);
        double diff = actualQty - current;
        if (Math.abs(diff) > 0.0001) {
            adjustWarehouseStock(db, warehouseId, productId, diff, "ADJUSTMENT", 0, 1, note);
        }
    }

    public void adjustWarehouseStock(SQLiteDatabase db, int warehouseId, int productId, double quantity, String type, int refId, int userId, String notes) {
        Cursor c = db.rawQuery("SELECT quantity FROM warehouse_stock WHERE warehouse_id = ? AND product_id = ?", new String[]{String.valueOf(warehouseId), String.valueOf(productId)});
        double existingVal = 0.0;
        boolean found = false;
        if (c.moveToFirst()) {
            existingVal = c.getDouble(0);
            found = true;
        }
        c.close();

        double newQty = existingVal + quantity;
        if (found) {
            db.execSQL("UPDATE warehouse_stock SET quantity = ? WHERE warehouse_id = ? AND product_id = ?", new Object[]{newQty, warehouseId, productId});
        } else {
            ContentValues cv = new ContentValues();
            cv.put("warehouse_id", warehouseId);
            cv.put("product_id", productId);
            cv.put("quantity", newQty);
            db.insert("warehouse_stock", null, cv);
        }

        db.execSQL("UPDATE products SET quantity = (SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id = ?) WHERE id = ?", new Object[]{productId, productId});

        ContentValues mCv = new ContentValues();
        mCv.put("warehouse_id", warehouseId);
        mCv.put("product_id", productId);
        mCv.put("quantity", quantity);
        mCv.put("movement_type", type);
        mCv.put("reference_id", refId);
        mCv.put("user_id", userId);
        mCv.put("notes", notes);
        db.insert("stock_movements", null, mCv);
    }

    public double getTotalProductQuantity(int productId) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id = ?", new String[]{String.valueOf(productId)})) {
            return c.moveToFirst() ? c.getDouble(0) : 0.0;
        }
    }

    public double getTotalProductQuantity(SQLiteDatabase db, int productId) {
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id = ?", new String[]{String.valueOf(productId)})) {
            return c.moveToFirst() ? c.getDouble(0) : 0.0;
        }
    }

    public boolean executeStockTransfer(int fromId, int toId, int userId, String notes, List<long[]> items) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues tCv = new ContentValues();
            tCv.put("from_warehouse_id", fromId);
            tCv.put("to_warehouse_id", toId);
            tCv.put("user_id", userId);
            tCv.put("notes", notes);
            long transferId = db.insertOrThrow("stock_transfers", null, tCv);

            if (items != null) {
                for (long[] item : items) {
                    int productId = (int) item[0];
                    double qty = Double.longBitsToDouble(item[1]);

                    ContentValues icv = new ContentValues();
                    icv.put("transfer_id", transferId);
                    icv.put("product_id", productId);
                    icv.put("quantity", qty);
                    db.insertOrThrow("stock_transfer_items", null, icv);

                    adjustWarehouseStock(db, fromId, productId, -qty, "TRANSFER_OUT", (int) transferId, userId, notes);
                    adjustWarehouseStock(db, toId, productId, qty, "TRANSFER_IN", (int) transferId, userId, notes);
                }
            }

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    // ============================================
    // Purchase Returns & Supplier / Customer Transactions
    // ============================================

    public long executePurchaseReturn(int purchaseId, int supplierId, double totalAmount, String refundMethod, double debtReduction, double cashRefund, String notes, int userId, int warehouseId, List<PurchaseReturnItemInput> items) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put("purchase_id", purchaseId);
            cv.put("supplier_id", supplierId);
            cv.put("total_amount", totalAmount);
            cv.put("refund_method", refundMethod);
            cv.put("debt_reduction", debtReduction);
            cv.put("cash_refund", cashRefund);
            cv.put("reason", notes);
            long returnId = db.insertOrThrow("purchase_returns", null, cv);

            if (items != null) {
                for (PurchaseReturnItemInput item : items) {
                    ContentValues icv = new ContentValues();
                    icv.put("return_id", returnId);
                    icv.put("product_id", item.productId);
                    icv.put("quantity", item.quantity);
                    icv.put("unit_price", item.unitPrice);
                    icv.put("total", item.total);
                    db.insertOrThrow("purchase_return_items", null, icv);

                    adjustWarehouseStock(db, warehouseId, item.productId, -item.quantity, StockMovement.PURCHASE_RETURN, (int) returnId, userId, notes);
                }
            }

            if (debtReduction > 0) {
                if (purchaseId > 0) {
                    db.execSQL("UPDATE purchases SET remaining = MAX(0, remaining - ?), paid = paid + ? WHERE id = ?",
                            new Object[]{debtReduction, debtReduction, purchaseId});
                }
                recordSupplierTransaction(db, supplierId, TX_RETURN, returnId, -debtReduction, notes);
                syncSupplierBalance(db, supplierId);
            }

            db.setTransactionSuccessful();
            return returnId;
        } catch (Exception e) {
            return -1;
        } finally {
            db.endTransaction();
        }
    }

    public long addOtherIncome(String title, String category, double amount, String paymentMethod, String notes, int userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("category", category);
        cv.put("amount", amount);
        cv.put("payment_method", paymentMethod != null ? paymentMethod : "Cash");
        cv.put("notes", notes);
        cv.put("user_id", userId);
        return db.insert("other_income", null, cv);
    }

    public long recordSupplierTransaction(SQLiteDatabase db, int supplierId, String txType, long refId, double amount, String notes) {
        ContentValues cv = new ContentValues();
        cv.put("supplier_id", supplierId);
        cv.put("tx_type", txType);
        cv.put("ref_id", refId);
        cv.put("amount", amount);
        cv.put("notes", notes);
        return db.insert("supplier_ledger", null, cv);
    }

    public void syncSupplierBalance(SQLiteDatabase db, int supplierId) {
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM supplier_ledger WHERE supplier_id = ?", new String[]{String.valueOf(supplierId)});
        double balance = 0.0;
        if (c.moveToFirst()) {
            balance = c.getDouble(0);
        }
        c.close();
        db.execSQL("UPDATE suppliers SET balance = ? WHERE id = ?", new Object[]{balance, supplierId});
    }

    public double getSupplierLedgerBalance(SQLiteDatabase db, int supplierId) {
        Cursor c = db.rawQuery("SELECT balance FROM suppliers WHERE id = ?", new String[]{String.valueOf(supplierId)});
        try {
            if (c.moveToFirst()) {
                return c.getDouble(0);
            }
        } finally {
            c.close();
        }
        return 0.0;
    }

    public long recordCustomerTransaction(SQLiteDatabase db, int customerId, String txType, long refId, double amount, String notes) {
        ContentValues cv = new ContentValues();
        cv.put("customer_id", customerId);
        cv.put("tx_type", txType);
        cv.put("ref_id", refId);
        cv.put("amount", amount);
        cv.put("notes", notes);
        return db.insert("account_ledger", null, cv);
    }

    public void syncCustomerBalance(SQLiteDatabase db, int customerId) {
        Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM account_ledger WHERE customer_id = ?", new String[]{String.valueOf(customerId)});
        double balance = 0.0;
        if (c.moveToFirst()) {
            balance = c.getDouble(0);
        }
        c.close();
        db.execSQL("UPDATE customers SET balance = ? WHERE id = ?", new Object[]{balance, customerId});
    }

    // ============================================
    // User Management Methods
    // ============================================

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, username, full_name, role, is_active, created_at, last_login_at FROM users", null);
        try {
            while (c.moveToNext()) {
                list.add(new User(
                        c.getInt(0),
                        c.getString(1),
                        c.getString(2),
                        c.getString(3),
                        c.getInt(4) == 1,
                        c.getString(5),
                        c.getString(6)
                ));
            }
        } finally {
            c.close();
        }
        return list;
    }

    public boolean toggleUserActive(int userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE users SET is_active = CASE WHEN COALESCE(is_active,1) = 1 THEN 0 ELSE 1 END WHERE id = ?", new Object[]{userId});
        return true;
    }

    public long addUser(String username, String password, String fullName, String role) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("username", username);
        cv.put("password", password);
        cv.put("full_name", fullName);
        cv.put("role", role);
        cv.put("is_active", 1);
        return db.insertWithOnConflict("users", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void updateUser(int userId, String fullName, String role, boolean isActive) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("full_name", fullName);
        cv.put("role", role);
        cv.put("is_active", isActive ? 1 : 0);
        db.update("users", cv, "id = ?", new String[]{String.valueOf(userId)});
    }

    public void changeUserPassword(int userId, String newPassword) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("password", newPassword);
        db.update("users", cv, "id = ?", new String[]{String.valueOf(userId)});
    }
}
