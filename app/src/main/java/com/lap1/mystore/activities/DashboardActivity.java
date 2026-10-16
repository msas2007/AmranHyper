package com.lap1.mystore.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.utils.BottomNavHelper;

import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvTotalSales;
    private TextView tvInvoiceCount;
    private TextView tvCustomerDebtsTotal;
    private TextView tvLowStockCount;
    private EditText etSearchFilter;
    private LinearLayout layoutRecentOperations;
    private LinearLayout layoutAlertsSection;
    private TextView tvAlertsBody;

    private DatabaseHelper dbHelper;
    private static final String PREFS_NAME = "MyStorePrefs";
    private static final String KEY_DARK_MODE = "is_dark_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        dbHelper = new DatabaseHelper(this);

        tvTotalSales = findViewById(R.id.tvTotalSales);
        tvInvoiceCount = findViewById(R.id.tvInvoiceCount);
        tvCustomerDebtsTotal = findViewById(R.id.tvCustomerDebtsTotal);
        tvLowStockCount = findViewById(R.id.tvLowStockCount);
        etSearchFilter = findViewById(R.id.etSearchFilter);
        layoutRecentOperations = findViewById(R.id.layoutRecentOperations);
        layoutAlertsSection = findViewById(R.id.layoutAlertsSection);
        tvAlertsBody = findViewById(R.id.tvAlertsBody);

        // Header Action: Theme Toggle
        ImageButton btnHeaderThemeToggle = findViewById(R.id.btnHeaderThemeToggle);
        if (btnHeaderThemeToggle != null) {
            btnHeaderThemeToggle.setOnClickListener(v -> {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                boolean currentDark = prefs.getBoolean(KEY_DARK_MODE, false);
                boolean newDark = !currentDark;
                prefs.edit().putBoolean(KEY_DARK_MODE, newDark).apply();

                if (newDark) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    Toast.makeText(this, "تم تفعيل الوضع الداكن", Toast.LENGTH_SHORT).show();
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    Toast.makeText(this, "تم تفعيل الوضع الفاتح", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Header Action: Notifications
        ImageButton btnHeaderNotifications = findViewById(R.id.btnHeaderNotifications);
        if (btnHeaderNotifications != null) {
            btnHeaderNotifications.setOnClickListener(v -> showNotificationsDialog());
        }

        // 1. Quick Access Action Buttons (أفعال)
        Button btnQuickNewSale = findViewById(R.id.btnQuickNewSale);
        Button btnQuickAddProduct = findViewById(R.id.btnQuickAddProduct);
        Button btnQuickPurchase = findViewById(R.id.btnQuickPurchase);
        Button btnQuickCollectPayment = findViewById(R.id.btnQuickCollectPayment);

        if (btnQuickNewSale != null) {
            btnQuickNewSale.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, SalesActivity.class)));
        }
        if (btnQuickAddProduct != null) {
            btnQuickAddProduct.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, ProductsActivity.class);
                intent.putExtra("OPEN_ADD_DIALOG", true);
                startActivity(intent);
            });
        }
        if (btnQuickPurchase != null) {
            btnQuickPurchase.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, PurchasesActivity.class)));
        }
        if (btnQuickCollectPayment != null) {
            btnQuickCollectPayment.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, CustomersActivity.class)));
        }

        // 2. Clickable Today's Summary KPI Cards
        View cardTodaySalesMetric = findViewById(R.id.cardTodaySalesMetric);
        View cardTodayInvoicesMetric = findViewById(R.id.cardTodayInvoicesMetric);
        View cardCustomerDebtsMetric = findViewById(R.id.cardCustomerDebtsMetric);
        View cardLowStockMetric = findViewById(R.id.cardLowStockMetric);
        TextView tvViewReportsLink = findViewById(R.id.tvViewReportsLink);
        TextView tvAllInvoicesLink = findViewById(R.id.tvAllInvoicesLink);

        if (cardTodaySalesMetric != null) cardTodaySalesMetric.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, ReportsActivity.class)));
        if (tvViewReportsLink != null) tvViewReportsLink.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, ReportsActivity.class)));
        if (cardTodayInvoicesMetric != null) cardTodayInvoicesMetric.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, InvoicesActivity.class)));
        if (tvAllInvoicesLink != null) tvAllInvoicesLink.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, InvoicesActivity.class)));
        if (cardCustomerDebtsMetric != null) cardCustomerDebtsMetric.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, CustomersActivity.class)));
        if (cardLowStockMetric != null) cardLowStockMetric.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, ExpiryActivity.class)));

        // Global Search button action
        Button btnGlobalSearch = findViewById(R.id.btnGlobalSearch);
        if (btnGlobalSearch != null) {
            btnGlobalSearch.setOnClickListener(v -> {
                String query = etSearchFilter.getText().toString().trim();
                if (!query.isEmpty()) {
                    Intent intent = new Intent(DashboardActivity.this, ProductsActivity.class);
                    intent.putExtra("SEARCH_QUERY", query);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "أدخل كلمة للبحث عنها", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Setup Fixed Bottom Navigation Bar
        BottomNavHelper.setupBottomNav(this, "home");

        loadDashboardSummary();
        loadRecentOperations();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboardSummary();
        loadRecentOperations();
    }

    private void showNotificationsDialog() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // 1. Low stock count
        Cursor lowCursor = db.rawQuery("SELECT COUNT(id) FROM products WHERE quantity <= min_quantity", null);
        int lowStock = lowCursor.moveToFirst() ? lowCursor.getInt(0) : 0;
        lowCursor.close();

        // 2. Debt Customers
        Cursor custCursor = db.rawQuery("SELECT COUNT(id) FROM customers WHERE balance > 0", null);
        int debtCustomers = custCursor.moveToFirst() ? custCursor.getInt(0) : 0;
        custCursor.close();

        StringBuilder sb = new StringBuilder();
        if (lowStock > 0) {
            sb.append("🔴 تنبيه مخزون: يوجد ").append(lowStock).append(" منتجات وصلت للحد الأدنى من المخزون أو قاربت على الانتهاء.\n\n");
        }
        if (debtCustomers > 0) {
            sb.append("🟠 تنبيه ديون: يوجد ").append(debtCustomers).append(" عملاء لديهم حسابات أو فواتير آجلة مستحقة.\n\n");
        }
        if (lowStock == 0 && debtCustomers == 0) {
            sb.append("✅ حالة النظام ممتازة: لا توجد نواقص حرجة أو تنبيهات معلقة حالياً.\n\n");
        }
        sb.append("🔐 يمكنك إدارة الإعدادات والصلاحيات من قائمة المزيد.");

        final int finalLowStock = lowStock;
        final int finalDebtCustomers = debtCustomers;

        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(this)
                        .setTitle("🔔 مركز الإشعارات والتنبيهات")
                        .setMessage(sb.toString().trim())
                        .setNegativeButton("إغلاق", null)
                        .setNeutralButton("🔐 الصلاحيات", (dialog, which) -> {
                            Intent intent = new Intent(DashboardActivity.this, BackupActivity.class);
                            intent.putExtra("FOCUS_CREDENTIALS", true);
                            startActivity(intent);
                        });

        if (finalLowStock > 0) {
            builder.setPositiveButton("📦 فحص النواقص", (dialog, which) -> startActivity(new Intent(DashboardActivity.this, ExpiryActivity.class)));
        } else if (finalDebtCustomers > 0) {
            builder.setPositiveButton("👥 كشف الديون", (dialog, which) -> startActivity(new Intent(DashboardActivity.this, CustomersActivity.class)));
        }

        builder.show();
    }

    private void loadDashboardSummary() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // 1. Today's Sales
        Cursor sCursor = db.rawQuery(
                "SELECT SUM(total - discount) FROM sales WHERE DATE(sale_date, 'localtime') = DATE('now', 'localtime')",
                null
        );
        double todaySales = 0;
        if (sCursor.moveToFirst() && !sCursor.isNull(0)) {
            todaySales = sCursor.getDouble(0);
        }
        sCursor.close();

        // 2. Today's Invoices
        Cursor invCursor = db.rawQuery(
                "SELECT COUNT(id) FROM sales WHERE DATE(sale_date, 'localtime') = DATE('now', 'localtime')",
                null
        );
        int todayInvoiceCount = 0;
        if (invCursor.moveToFirst()) {
            todayInvoiceCount = invCursor.getInt(0);
        }
        invCursor.close();

        // 3. Customer Debts Balance
        Cursor debtSumCursor = db.rawQuery("SELECT SUM(balance) FROM customers WHERE balance > 0", null);
        double totalCustomerDebts = 0;
        if (debtSumCursor.moveToFirst() && !debtSumCursor.isNull(0)) {
            totalCustomerDebts = debtSumCursor.getDouble(0);
        }
        debtSumCursor.close();

        // 4. Low Stock Count
        Cursor lowCursor = db.rawQuery("SELECT COUNT(id) FROM products WHERE quantity <= min_quantity", null);
        final int lowStockCount = lowCursor.moveToFirst() ? lowCursor.getInt(0) : 0;
        lowCursor.close();

        // 5. Debt Customers Count
        Cursor debtCountCursor = db.rawQuery("SELECT COUNT(id) FROM customers WHERE balance > 0", null);
        final int debtCustomersCount = debtCountCursor.moveToFirst() ? debtCountCursor.getInt(0) : 0;
        debtCountCursor.close();

        // Display Metrics
        tvTotalSales.setText(String.format(Locale.getDefault(), "%.2f ر.س", todaySales));
        tvInvoiceCount.setText(String.valueOf(todayInvoiceCount));
        tvCustomerDebtsTotal.setText(String.format(Locale.getDefault(), "%.2f ر.س", totalCustomerDebts));
        tvLowStockCount.setText(lowStockCount + " صنف");

        // "يحتاج انتباهك" Smart Section
        if (layoutAlertsSection != null && tvAlertsBody != null) {
            if (lowStockCount > 0 || debtCustomersCount > 0) {
                layoutAlertsSection.setVisibility(View.VISIBLE);
                StringBuilder alertsText = new StringBuilder();
                if (lowStockCount > 0) {
                    alertsText.append("• ").append(lowStockCount).append(" منتجات وصلت للحد الأدنى من المخزون وتحتاج للطلب.\n");
                }
                if (debtCustomersCount > 0) {
                    alertsText.append("• ").append(debtCustomersCount).append(" عملاء لديهم مديونيات وفواتير آجلة مستحقة.");
                }
                tvAlertsBody.setText(alertsText.toString().trim());

                layoutAlertsSection.setOnClickListener(v -> {
                    if (lowStockCount > 0) {
                        startActivity(new Intent(DashboardActivity.this, ExpiryActivity.class));
                    } else {
                        startActivity(new Intent(DashboardActivity.this, CustomersActivity.class));
                    }
                });
            } else {
                layoutAlertsSection.setVisibility(View.GONE);
            }
        }
    }

    private void loadRecentOperations() {
        if (layoutRecentOperations == null) return;
        layoutRecentOperations.removeAllViews();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT id, invoice_number, total, sale_date, 'SALE' as type FROM sales UNION ALL " +
                "SELECT id, COALESCE(supplier_name, 'مشتريات') as invoice_number, total, purchase_date as sale_date, 'PURCHASE' as type FROM purchases UNION ALL " +
                "SELECT id, 'مرتجع' as invoice_number, amount as total, return_date as sale_date, 'RETURN' as type FROM returns UNION ALL " +
                "SELECT id, title as invoice_number, amount as total, expense_date as sale_date, 'EXPENSE' as type FROM expenses " +
                "ORDER BY sale_date DESC LIMIT 8",
                null
        );

        if (cursor.getCount() == 0) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("لم يتم تسجيل أي نشاطات أو فواتير حتى الآن");
            tvEmpty.setTextSize(12f);
            tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvEmpty.setPadding(0, 16, 0, 16);
            layoutRecentOperations.addView(tvEmpty);
        } else {
            while (cursor.moveToNext()) {
                final int itemId = cursor.getInt(0);
                String invNum = cursor.getString(1);
                double total = cursor.getDouble(2);
                String date = cursor.getString(3);
                String type = cursor.getString(4);

                LinearLayout itemLayout = new LinearLayout(this);
                itemLayout.setOrientation(LinearLayout.VERTICAL);
                itemLayout.setPadding(0, 12, 0, 12);
                itemLayout.setClickable(true);
                itemLayout.setFocusable(true);

                String iconTag;
                String typeTitle;

                if ("SALE".equals(type)) {
                    iconTag = "🧾";
                    typeTitle = "فاتورة بيع #" + invNum;
                    itemLayout.setOnClickListener(v -> {
                        Intent intent = new Intent(DashboardActivity.this, InvoiceDetailActivity.class);
                        intent.putExtra("SALE_ID", itemId);
                        startActivity(intent);
                    });
                } else if ("PURCHASE".equals(type)) {
                    iconTag = "📦";
                    typeTitle = "عملية شراء: " + invNum;
                    itemLayout.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, PurchasesActivity.class)));
                } else if ("RETURN".equals(type)) {
                    iconTag = "↩️";
                    typeTitle = "عملية مرتجع";
                    itemLayout.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, ReturnsActivity.class)));
                } else {
                    iconTag = "💰";
                    typeTitle = "مصروف: " + invNum;
                    itemLayout.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, ExpensesActivity.class)));
                }

                TextView tvTitle = new TextView(this);
                tvTitle.setText(iconTag + " " + typeTitle + "  ›");
                tvTitle.setTextSize(13f);
                tvTitle.setTypeface(null, Typeface.BOLD);
                tvTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

                TextView tvSub = new TextView(this);
                tvSub.setText("المبلغ: " + String.format(Locale.getDefault(), "%.2f ر.س", total) + "  •  " + (date != null ? date : "اليوم"));
                tvSub.setTextSize(11f);
                tvSub.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

                itemLayout.addView(tvTitle);
                itemLayout.addView(tvSub);

                View divider = new View(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
                params.setMargins(0, 6, 0, 6);
                divider.setLayoutParams(params);
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.stroke_color));

                layoutRecentOperations.addView(itemLayout);
                layoutRecentOperations.addView(divider);
            }
        }
        cursor.close();
    }
}
