package com.lap1.mystore.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "mystore.db";
    private static final int DATABASE_VERSION = 11; // ترقية لدعم الأرشفة وسجلات تسوية الجرد

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 1. المستخدمين
        db.execSQL("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL)");

        // إضافة مستخدم الأدمن الافتراضي
        db.execSQL("INSERT OR IGNORE INTO users (id, username, password) VALUES (1, 'admin', 'admin')");

        // 2. المرتجعات
        db.execSQL("CREATE TABLE returns (" +
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

        db.execSQL("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)");
        db.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, barcode TEXT, category_id INTEGER, purchase_price REAL, sale_price REAL, quantity REAL, min_quantity REAL, expiry_date TEXT, alert_days INTEGER DEFAULT 10, unit_name TEXT DEFAULT 'حبة', unit_factor REAL DEFAULT 1.0, is_weighted INTEGER DEFAULT 0, is_archived INTEGER DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(category_id) REFERENCES categories(id))");
        db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT, address TEXT, balance REAL DEFAULT 0.0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE suppliers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT, balance REAL DEFAULT 0.0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_number TEXT NOT NULL, customer_id INTEGER, total REAL, discount REAL DEFAULT 0.0, tax REAL DEFAULT 0.0, paid REAL, remaining REAL, payment_status TEXT, payment_method TEXT DEFAULT 'كاش', sale_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(customer_id) REFERENCES customers(id))");
        db.execSQL("CREATE TABLE sale_items (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER, product_id INTEGER, quantity REAL, unit_price REAL, purchase_price REAL, total REAL, FOREIGN KEY(sale_id) REFERENCES sales(id), FOREIGN KEY(product_id) REFERENCES products(id))");
        db.execSQL("CREATE TABLE purchases (id INTEGER PRIMARY KEY AUTOINCREMENT, supplier_id INTEGER, supplier_name TEXT, total REAL, paid REAL DEFAULT 0.0, remaining REAL DEFAULT 0.0, purchase_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(supplier_id) REFERENCES suppliers(id))");
        db.execSQL("CREATE TABLE purchase_items (id INTEGER PRIMARY KEY AUTOINCREMENT, purchase_id INTEGER, product_id INTEGER, quantity REAL, unit_price REAL, total REAL, FOREIGN KEY(purchase_id) REFERENCES purchases(id), FOREIGN KEY(product_id) REFERENCES products(id))");
        db.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER, sale_id INTEGER, amount REAL, payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, notes TEXT, FOREIGN KEY(customer_id) REFERENCES customers(id), FOREIGN KEY(sale_id) REFERENCES sales(id))");
        db.execSQL("CREATE TABLE supplier_payments (id INTEGER PRIMARY KEY AUTOINCREMENT, supplier_id INTEGER, amount REAL, payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, notes TEXT, FOREIGN KEY(supplier_id) REFERENCES suppliers(id))");
        db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, category TEXT, amount REAL NOT NULL, expense_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, notes TEXT)");
        db.execSQL("CREATE TABLE daily_closings (id INTEGER PRIMARY KEY AUTOINCREMENT, closing_date DATE DEFAULT (DATE('now', 'localtime')), physical_cash REAL, system_cash REAL, system_network REAL, system_credit REAL, difference REAL, notes TEXT)");
        db.execSQL("CREATE TABLE stock_adjustments (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER, product_name TEXT, system_quantity REAL, actual_quantity REAL, difference REAL, notes TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(product_id) REFERENCES products(id))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) { try { db.execSQL("ALTER TABLE products ADD COLUMN alert_days INTEGER DEFAULT 10;"); } catch (Exception ignored) {} }
        if (oldVersion < 3) {
            try { db.execSQL("ALTER TABLE products ADD COLUMN unit_name TEXT DEFAULT 'حبة';"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE products ADD COLUMN unit_factor REAL DEFAULT 1.0;"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE products ADD COLUMN is_weighted INTEGER DEFAULT 0;"); } catch (Exception ignored) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS suppliers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT, balance REAL DEFAULT 0.0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"); } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE purchases ADD COLUMN supplier_id INTEGER;");
                db.execSQL("ALTER TABLE purchases ADD COLUMN paid REAL DEFAULT 0.0;");
                db.execSQL("ALTER TABLE purchases ADD COLUMN remaining REAL DEFAULT 0.0;");
            } catch (Exception ignored) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS supplier_payments (id INTEGER PRIMARY KEY AUTOINCREMENT, supplier_id INTEGER, amount REAL, payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, notes TEXT, FOREIGN KEY(supplier_id) REFERENCES suppliers(id))"); } catch (Exception ignored) {}
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, category TEXT, amount REAL NOT NULL, expense_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, notes TEXT)");
                db.execSQL("CREATE TABLE IF NOT EXISTS daily_closings (id INTEGER PRIMARY KEY AUTOINCREMENT, closing_date DATE DEFAULT (DATE('now', 'localtime')), physical_cash REAL, system_cash REAL, system_network REAL, system_credit REAL, difference REAL, notes TEXT)");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 5) { try { db.execSQL("ALTER TABLE sales ADD COLUMN payment_method TEXT DEFAULT 'كاش';"); } catch (Exception ignored) {} }
        if (oldVersion < 6) { try { db.execSQL("ALTER TABLE returns ADD COLUMN purchase_price REAL;"); } catch (Exception ignored) {} }
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE sales ADD COLUMN discount REAL DEFAULT 0.0;");
                db.execSQL("ALTER TABLE sales ADD COLUMN tax REAL DEFAULT 0.0;");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 8) { try { db.execSQL("ALTER TABLE returns ADD COLUMN return_method TEXT DEFAULT 'نقدي';"); } catch (Exception ignored) {} }
        if (oldVersion < 9) {
            try { db.execSQL("ALTER TABLE returns ADD COLUMN debt_reduction REAL DEFAULT 0.0;"); } catch (Exception ignored) {}
        }
        if (oldVersion < 10) {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, password TEXT NOT NULL)");
                db.execSQL("INSERT OR IGNORE INTO users (id, username, password) VALUES (1, 'admin', 'admin')");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 11) {
            try { db.execSQL("ALTER TABLE products ADD COLUMN is_archived INTEGER DEFAULT 0;"); } catch (Exception ignored) {}
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS stock_adjustments (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "product_id INTEGER, " +
                        "product_name TEXT, " +
                        "system_quantity REAL, " +
                        "actual_quantity REAL, " +
                        "difference REAL, " +
                        "notes TEXT, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "FOREIGN KEY(product_id) REFERENCES products(id))");
            } catch (Exception ignored) {}
        }
    }

    // ============================================
    // إدارة المستخدمين والتسجيل الحقيقي
    // ============================================

    public boolean authenticateUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM users WHERE username = ? AND password = ?", new String[]{username, password});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public String[] getFirstUser() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT username, password FROM users LIMIT 1", null);
        String[] user = new String[]{"admin", "admin"};
        if (cursor.moveToFirst()) {
            user[0] = cursor.getString(0);
            user[1] = cursor.getString(1);
        } else {
            // إذا لم توجد بيانات قم بإنشاء أدمن
            SQLiteDatabase wDb = this.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("id", 1);
            cv.put("username", "admin");
            cv.put("password", "admin");
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
            // إذا كان الجدول فارغاً نقوم بإدراجه
            values.put("id", 1);
            long id = db.insertWithOnConflict("users", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            return id != -1;
        }
        return rows > 0;
    }
}
