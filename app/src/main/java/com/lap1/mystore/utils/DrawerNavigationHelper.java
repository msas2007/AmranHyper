package com.lap1.mystore.utils;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lap1.mystore.MyStoreApp;
import com.lap1.mystore.R;
import com.lap1.mystore.activities.BackupActivity;
import com.lap1.mystore.activities.CashboxActivity;
import com.lap1.mystore.activities.CategoriesActivity;
import com.lap1.mystore.activities.ClosingActivity;
import com.lap1.mystore.activities.CustomersActivity;
import com.lap1.mystore.activities.DashboardActivity;
import com.lap1.mystore.activities.ExpensesActivity;
import com.lap1.mystore.activities.ExpiryActivity;
import com.lap1.mystore.activities.InventoryCountActivity;
import com.lap1.mystore.activities.InvoicesActivity;
import com.lap1.mystore.activities.LoginActivity;
import com.lap1.mystore.activities.OtherIncomeActivity;
import com.lap1.mystore.activities.ProductsActivity;
import com.lap1.mystore.activities.PurchaseInvoicesActivity;
import com.lap1.mystore.activities.PurchaseOrdersActivity;
import com.lap1.mystore.activities.PurchaseReturnsActivity;
import com.lap1.mystore.activities.PurchasesActivity;
import com.lap1.mystore.activities.ReportsActivity;
import com.lap1.mystore.activities.ReturnsActivity;
import com.lap1.mystore.activities.SalesActivity;
import com.lap1.mystore.activities.SettingsActivity;
import com.lap1.mystore.activities.StockTransferActivity;
import com.lap1.mystore.activities.SuppliersActivity;
import com.lap1.mystore.activities.UsersActivity;
import com.lap1.mystore.activities.WarehousesActivity;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.models.StoreSettings;

/**
 * فئة مساعدة شاملة لإدارة التنقل الموحد عبر Drawer والقائمة الجانبية الأكوردين (RTL Navigation Drawer Helper)
 */
public class DrawerNavigationHelper {

    public static void setupDrawer(final Activity activity, final String activeCategory, final String activePageTitle) {
        if (activity == null) return;

        final DrawerLayout drawerLayout = activity.findViewById(R.id.drawerLayout);
        ImageButton btnDrawerMenu = activity.findViewById(R.id.btnDrawerMenu);

        // 1. ربط زر الهيدر لفتح Drawer الجانبي (RTL)
        if (drawerLayout != null && btnDrawerMenu != null) {
            btnDrawerMenu.setOnClickListener(v -> {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        // 2. تحديث بيانات المستخدم اسم المتجر والدور في هيدر القائمة الجانبية
        bindHeaderData(activity);

        // 3. إعداد الأزرار المساعدة بالهيدر (Theme Toggle & Notifications)
        setupHeaderActions(activity);

        // 4. إعداد الأكوردين للأقسام الرئيسية الـ 5
        setupAccordionCategories(activity, drawerLayout, activeCategory);

        // 5. ربط العناصر وتنفيذ الإجراءات
        bindMenuClickListeners(activity, drawerLayout);

        // 6. تمييز الصفحة الحالية
        if (activePageTitle != null) {
            TextView tvHeaderTitle = activity.findViewById(R.id.tvHeaderTitle);
            if (tvHeaderTitle != null) {
                tvHeaderTitle.setText(activePageTitle);
            }
        }
    }

    private static void bindHeaderData(Activity activity) {
        TextView tvDrawerStoreName = activity.findViewById(R.id.tvDrawerStoreName);
        TextView tvDrawerUserFullName = activity.findViewById(R.id.tvDrawerUserFullName);
        TextView tvDrawerUserRole = activity.findViewById(R.id.tvDrawerUserRole);
        TextView tvHeaderSubtitle = activity.findViewById(R.id.tvHeaderSubtitle);

        DatabaseHelper dbHelper = new DatabaseHelper(activity);
        StoreSettings settings = dbHelper.getStoreSettings();
        SessionManager session = SessionManager.getInstance(activity);

        String storeName = settings != null ? settings.getStoreName() : "عمران هايبر";
        String userFullName = session.isLoggedIn() ? session.getFullName() : "زائر";
        String userRole = session.isLoggedIn() ? session.getRole() : "admin";

        if (tvDrawerStoreName != null) tvDrawerStoreName.setText(storeName);
        if (tvHeaderSubtitle != null) tvHeaderSubtitle.setText(storeName);
        if (tvDrawerUserFullName != null) tvDrawerUserFullName.setText(userFullName);
        if (tvDrawerUserRole != null) {
            String roleText = "admin".equalsIgnoreCase(userRole) ? "المدير العام" : userRole;
            tvDrawerUserRole.setText(roleText);
        }
    }

    private static void setupHeaderActions(final Activity activity) {
        ImageButton btnHeaderThemeToggle = activity.findViewById(R.id.btnHeaderThemeToggle);
        if (btnHeaderThemeToggle != null) {
            btnHeaderThemeToggle.setOnClickListener(v -> {
                SharedPreferences prefs = activity.getSharedPreferences(MyStoreApp.PREFS_NAME, Activity.MODE_PRIVATE);
                String current = prefs.getString(MyStoreApp.KEY_THEME_MODE, "system");
                String next = "system".equals(current) ? "dark" : ("dark".equals(current) ? "light" : "system");
                prefs.edit().putString(MyStoreApp.KEY_THEME_MODE, next).apply();
                if (activity.getApplication() instanceof MyStoreApp) {
                    ((MyStoreApp) activity.getApplication()).applyAppTheme();
                }
                String label = "system".equals(next) ? "وضع الهاتف" : ("dark".equals(next) ? "الوضع الداكن" : "الوضع الفاتح");
                Toast.makeText(activity, "تم اختيار: " + label, Toast.LENGTH_SHORT).show();
            });
        }

        ImageButton btnHeaderNotifications = activity.findViewById(R.id.btnHeaderNotifications);
        if (btnHeaderNotifications != null) {
            btnHeaderNotifications.setOnClickListener(v -> showNotificationsDialog(activity));
        }
    }

    private static void setupAccordionCategories(Activity activity, DrawerLayout drawerLayout, String activeCategory) {
        // Categories Header & Container Pairs
        setupCategoryToggle(activity, R.id.cat_sales_header, R.id.cat_sales_container, R.id.arrow_sales, "sales".equalsIgnoreCase(activeCategory));
        setupCategoryToggle(activity, R.id.cat_inventory_header, R.id.cat_inventory_container, R.id.arrow_inventory, "inventory".equalsIgnoreCase(activeCategory));
        setupCategoryToggle(activity, R.id.cat_purchases_header, R.id.cat_purchases_container, R.id.arrow_purchases, "purchases".equalsIgnoreCase(activeCategory));
        setupCategoryToggle(activity, R.id.cat_finance_header, R.id.cat_finance_container, R.id.arrow_finance, "finance".equalsIgnoreCase(activeCategory));
        setupCategoryToggle(activity, R.id.cat_reports_header, R.id.cat_reports_container, R.id.arrow_reports, "reports".equalsIgnoreCase(activeCategory));
    }

    private static void setupCategoryToggle(final Activity activity, int headerId, int containerId, int arrowId, boolean defaultExpanded) {
        final View header = activity.findViewById(headerId);
        final View container = activity.findViewById(containerId);
        final ImageView arrow = activity.findViewById(arrowId);

        if (header == null || container == null) return;

        if (defaultExpanded) {
            container.setVisibility(View.VISIBLE);
            if (arrow != null) arrow.setImageResource(R.drawable.ic_expand_less);
        } else {
            container.setVisibility(View.GONE);
            if (arrow != null) arrow.setImageResource(R.drawable.ic_expand_more);
        }

        header.setOnClickListener(v -> {
            boolean isExpanded = container.getVisibility() == View.VISIBLE;
            if (isExpanded) {
                container.setVisibility(View.GONE);
                if (arrow != null) arrow.setImageResource(R.drawable.ic_expand_more);
            } else {
                container.setVisibility(View.VISIBLE);
                if (arrow != null) arrow.setImageResource(R.drawable.ic_expand_less);
            }
        });
    }

    private static void bindMenuClickListeners(final Activity activity, final DrawerLayout drawerLayout) {
        // 🏠 الرئيسية
        setNavigationAction(activity, drawerLayout, R.id.nav_item_dashboard, DashboardActivity.class, null);

        // 🛒 المبيعات
        setNavigationAction(activity, drawerLayout, R.id.nav_sales_pos, SalesActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_sales_invoices, InvoicesActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_sales_returns, ReturnsActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_sales_closing, ClosingActivity.class, null);

        // 📦 المخزون
        setNavigationAction(activity, drawerLayout, R.id.nav_inventory_products, ProductsActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_inventory_categories, CategoriesActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_inventory_warehouses, InventoryCountActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_inventory_audit, InventoryCountActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_inventory_transfers, InventoryCountActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_inventory_adjustments, InventoryCountActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_inventory_alerts, ExpiryActivity.class, null);

        // 🚚 المشتريات
        setNavigationAction(activity, drawerLayout, R.id.nav_purchases_new, PurchasesActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_purchases_invoices, PurchaseInvoicesActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_purchases_orders, PurchaseOrdersActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_purchases_suppliers, SuppliersActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_purchases_returns, PurchaseReturnsActivity.class, null);

        // 💰 المالية
        setNavigationAction(activity, drawerLayout, R.id.nav_finance_customers, CustomersActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_finance_expenses, ExpensesActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_finance_income, OtherIncomeActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_finance_treasury, CashboxActivity.class, null);

        // 📊 الإدارة والتقارير
        setNavigationAction(activity, drawerLayout, R.id.nav_reports_all, ReportsActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_reports_users, UsersActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_reports_settings, SettingsActivity.class, null);
        setNavigationAction(activity, drawerLayout, R.id.nav_reports_backup, BackupActivity.class, null);

        // 🚪 تسجيل الخروج
        View navLogout = activity.findViewById(R.id.nav_item_logout);
        if (navLogout != null) {
            navLogout.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                SessionManager.getInstance(activity).logoutUser();
                Toast.makeText(activity, "تم تسجيل الخروج بنجاح", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(activity, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                activity.startActivity(intent);
                activity.finish();
            });
        }
    }

    private static void setNavigationAction(final Activity activity, final DrawerLayout drawerLayout, int viewId, final Class<?> targetClass, final String toastMsg) {
        View view = activity.findViewById(viewId);
        if (view == null) return;
        view.setOnClickListener(v -> {
            if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
            if (toastMsg != null) {
                Toast.makeText(activity, toastMsg, Toast.LENGTH_SHORT).show();
            }
            if (!activity.getClass().equals(targetClass)) {
                Intent intent = new Intent(activity, targetClass);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
                if (!(activity instanceof DashboardActivity)) {
                    activity.finish();
                }
            }
        });
    }

    public static void showNotificationsDialog(Activity activity) {
        DatabaseHelper dbHelper = new DatabaseHelper(activity);
        var db = dbHelper.getReadableDatabase();

        var lowCursor = db.rawQuery("SELECT COUNT(id) FROM products WHERE (is_archived IS NULL OR is_archived = 0) AND quantity <= min_quantity", null);
        int lowStock = lowCursor.moveToFirst() ? lowCursor.getInt(0) : 0;
        lowCursor.close();

        var custCursor = db.rawQuery("SELECT COUNT(id) FROM customers WHERE balance > 0", null);
        int debtCustomers = custCursor.moveToFirst() ? custCursor.getInt(0) : 0;
        custCursor.close();

        StringBuilder sb = new StringBuilder();
        if (lowStock > 0) {
            sb.append("🔴 تنبيه مخزون: يوجد ").append(lowStock).append(" منتجات قاربت على النفاد.\n\n");
        }
        if (debtCustomers > 0) {
            sb.append("🟠 تنبيه ديون: يوجد ").append(debtCustomers).append(" عملاء لديهم مديونيات مستحقة.\n\n");
        }
        if (lowStock == 0 && debtCustomers == 0) {
            sb.append("✅ حالة النظام ممتازة: لا توجد نواقص حرجة أو تنبيهات حالياً.\n\n");
        }
        sb.append("🔐 القائمة الجانبية أصبحت الآن القائمة الرئيسية الموحدة للنظام.");

        new MaterialAlertDialogBuilder(activity)
                .setTitle("🔔 مركز الإشعارات والتنبيهات")
                .setMessage(sb.toString().trim())
                .setNegativeButton("إغلاق", null)
                .setPositiveButton(lowStock > 0 ? "📦 النواقص" : "👥 الديون", (dialog, which) -> {
                    Intent intent = new Intent(activity, lowStock > 0 ? ExpiryActivity.class : CustomersActivity.class);
                    activity.startActivity(intent);
                })
                .show();
    }

    public static boolean onBackPressed(Activity activity) {
        if (activity == null) return false;
        DrawerLayout drawerLayout = activity.findViewById(R.id.drawerLayout);
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        return false;
    }
}
