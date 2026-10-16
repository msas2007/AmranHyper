package com.lap1.mystore.activities;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class ReportsActivity extends AppCompatActivity {

    private TextView tvReportPeriodTitle, tvTotalSales, tvTotalDebt, tvTotalProfit, tvTotalInvoices, tvTotalDiscounts;
    private ListView lvTopProducts;
    private LinearLayout layoutSalesChart;
    private Button btnFilterAll, btnFilterToday, btnFilterWeek, btnExportExcel, btnExportPdf;
    private DatabaseHelper dbHelper;

    private ArrayList<String> topProductsList;
    private ArrayAdapter<String> topProductsAdapter;

    private String currentFilter = "ALL"; // ALL, TODAY, WEEK

    // قيم مجمعة للتقرير
    private double totalSalesVal = 0, totalDebtVal = 0, netProfitVal = 0, totalDiscountsVal = 0;
    private int totalInvoicesVal = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        tvReportPeriodTitle = findViewById(R.id.tvReportPeriodTitle);
        tvTotalSales = findViewById(R.id.tvTotalSales);
        tvTotalDebt = findViewById(R.id.tvTotalDebt);
        tvTotalProfit = findViewById(R.id.tvTotalProfit);
        tvTotalDiscounts = findViewById(R.id.tvTotalDiscounts);
        tvTotalInvoices = findViewById(R.id.tvTotalInvoices);
        lvTopProducts = findViewById(R.id.lvTopProducts);
        layoutSalesChart = findViewById(R.id.layoutSalesChart);

        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterToday = findViewById(R.id.btnFilterToday);
        btnFilterWeek = findViewById(R.id.btnFilterWeek);
        btnExportExcel = findViewById(R.id.btnExportExcel);
        btnExportPdf = findViewById(R.id.btnExportPdf);
        View btnBack = findViewById(R.id.btnReportsBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        dbHelper = new DatabaseHelper(this);
        topProductsList = new ArrayList<>();
        topProductsAdapter = new ArrayAdapter<>(this, R.layout.item_history_entry, R.id.tvHistoryText, topProductsList);
        lvTopProducts.setAdapter(topProductsAdapter);

        loadReportsData(currentFilter);
        loadSalesTrend();

        btnFilterAll.setOnClickListener(v -> { currentFilter = "ALL"; updateFilterButtonsUi(); loadReportsData(currentFilter); });
        btnFilterToday.setOnClickListener(v -> { currentFilter = "TODAY"; updateFilterButtonsUi(); loadReportsData(currentFilter); });
        btnFilterWeek.setOnClickListener(v -> { currentFilter = "WEEK"; updateFilterButtonsUi(); loadReportsData(currentFilter); });

        btnExportExcel.setOnClickListener(v -> exportToCSV());
        btnExportPdf.setOnClickListener(v -> exportToPDF());

        // الشريط السفلي الثابت
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "more");
    }

    private void updateFilterButtonsUi() {
        int activeBg = androidx.core.content.ContextCompat.getColor(this, R.color.primary);
        int inactiveBg = androidx.core.content.ContextCompat.getColor(this, R.color.stroke_color);
        int activeText = android.graphics.Color.WHITE;
        int inactiveText = androidx.core.content.ContextCompat.getColor(this, R.color.text_primary);

        btnFilterAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactiveBg));
        btnFilterAll.setTextColor(inactiveText);
        btnFilterToday.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactiveBg));
        btnFilterToday.setTextColor(inactiveText);
        btnFilterWeek.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactiveBg));
        btnFilterWeek.setTextColor(inactiveText);

        if (currentFilter.equals("ALL")) {
            btnFilterAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeBg));
            btnFilterAll.setTextColor(activeText);
            tvReportPeriodTitle.setText("📅 الفترة: إجمالي التاريخ (كل العمليات)");
        } else if (currentFilter.equals("TODAY")) {
            btnFilterToday.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeBg));
            btnFilterToday.setTextColor(activeText);
            tvReportPeriodTitle.setText("📅 الفترة: مبيعات اليوم الحالي");
        } else if (currentFilter.equals("WEEK")) {
            btnFilterWeek.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeBg));
            btnFilterWeek.setTextColor(activeText);
            tvReportPeriodTitle.setText("📅 الفترة: خلال هذا الأسبوع (آخر 7 أيام)");
        }
    }

    private void loadReportsData(String filter) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String dateCondition = "";
        String returnCondition = "";
        String expenseCondition = "";

        if (filter.equals("TODAY")) {
            dateCondition = " WHERE date(sale_date, 'localtime') = date('now', 'localtime')";
            returnCondition = " WHERE date(return_date, 'localtime') = date('now', 'localtime')";
            expenseCondition = " WHERE date(expense_date, 'localtime') = date('now', 'localtime')";
        } else if (filter.equals("WEEK")) {
            dateCondition = " WHERE date(sale_date, 'localtime') >= date('now', 'localtime', '-7 days')";
            returnCondition = " WHERE date(return_date, 'localtime') >= date('now', 'localtime', '-7 days')";
            expenseCondition = " WHERE date(expense_date, 'localtime') >= date('now', 'localtime', '-7 days')";
        }

        // 1. إجمالي المبيعات وعدد الفواتير والخصومات
        String salesQuery = "SELECT SUM(total), COUNT(id), SUM(discount) FROM sales" + dateCondition;
        Cursor cSales = db.rawQuery(salesQuery, null);
        totalSalesVal = 0;
        totalInvoicesVal = 0;
        totalDiscountsVal = 0;
        if (cSales.moveToFirst()) {
            totalSalesVal = cSales.getDouble(0);
            totalInvoicesVal = cSales.getInt(1);
            totalDiscountsVal = cSales.getDouble(2);
        }
        cSales.close();

        // 2. إجمالي الديون المستحقة من جميع العملاء (دائماً الإجمالي الحالي)
        Cursor cDebt = db.rawQuery("SELECT SUM(balance) FROM customers", null);
        totalDebtVal = 0;
        if (cDebt.moveToFirst()) {
            totalDebtVal = cDebt.getDouble(0);
        }
        cDebt.close();

        // 3. المرتجعات والمصروفات
        double totalExpenses = 0;
        double lostProfitOnReturns = 0;
        double totalReturnAmount = 0;

        // حساب المرتجعات للفترة
        Cursor cRet = db.rawQuery("SELECT SUM(amount), SUM(amount - (purchase_price * quantity)) FROM returns" + returnCondition, null);
        if (cRet.moveToFirst()) {
            totalReturnAmount = cRet.getDouble(0);
            lostProfitOnReturns = cRet.getDouble(1);
        }
        cRet.close();

        // حساب المصروفات للفترة
        Cursor cExp = db.rawQuery("SELECT SUM(amount) FROM expenses" + expenseCondition, null);
        if (cExp.moveToFirst()) totalExpenses = cExp.getDouble(0);
        cExp.close();

        // حساب صافي المبيعات (المبيعات - الخصومات - المرتجعات)
        double netSales = totalSalesVal - totalDiscountsVal - totalReturnAmount;

        // حساب الأرباح الإجمالية من الفارق بين سعر البيع وسعر الشراء
        double totalGrossProfit = 0;
        String profitQuery;
        if (filter.equals("ALL")) {
            profitQuery = "SELECT SUM((si.unit_price - si.purchase_price) * si.quantity) FROM sale_items si";
            Cursor cProf = db.rawQuery(profitQuery, null);
            if (cProf.moveToFirst()) totalGrossProfit = cProf.getDouble(0);
            cProf.close();
        } else {
            // [FIX] إضافة مسافة قبل JOIN لتجنب خطأ SQL
            profitQuery = "SELECT SUM((si.unit_price - si.purchase_price) * si.quantity) " +
                    "FROM sale_items si JOIN sales s ON si.sale_id = s.id" + dateCondition;
            Cursor cProf = db.rawQuery(profitQuery, null);
            if (cProf.moveToFirst()) totalGrossProfit = cProf.getDouble(0);
            cProf.close();
        }

        // [FIX] خصم الخصومات الممنوحة من صافي الأرباح (كانت تُضاف كأرباح وهمية)
        netProfitVal = totalGrossProfit - totalDiscountsVal - totalExpenses - lostProfitOnReturns;

        // 4. المنتجات الأكثر مبيعاً
        topProductsList.clear();
        // [FIX] إضافة مسافة قبل JOIN في حالة الفلاتر الزمنية
        String topQuery = "SELECT p.name, SUM(si.quantity) as total_qty, SUM(si.total) as total_amt " +
                "FROM sale_items si JOIN products p ON si.product_id = p.id " +
                (filter.equals("ALL") ? "" : " JOIN sales s ON si.sale_id = s.id" + dateCondition) +
                " GROUP BY si.product_id ORDER BY total_qty DESC LIMIT 5";

        Cursor cTop = db.rawQuery(topQuery, null);
        if (cTop.moveToFirst()) {
            int rank = 1;
            do {
                String pName = cTop.getString(0);
                double qtySold = cTop.getDouble(1);
                double amtSold = cTop.getDouble(2);
                topProductsList.add("#" + rank + " - " + pName + "\nالكمية المباعة: " + qtySold + " | الإجمالي: " + amtSold);
                rank++;
            } while (cTop.moveToNext());
        }
        cTop.close();

        if (topProductsList.isEmpty()) {
            topProductsList.add("لا توجد بيانات مبيعات مسجلة في هذه الفترة.");
        }

        // عرض القيم في البطاقات المالية
        tvTotalSales.setText("💵 صافي المبيعات: " + String.format(Locale.US, "%.2f", netSales));
        tvTotalDebt.setText("⏳ إجمالي الديون المستحقة: " + totalDebtVal);
        tvTotalProfit.setText("📈 صافي الأرباح (بعد المصاريف والخصومات): " + String.format(Locale.US, "%.2f", netProfitVal));
        tvTotalDiscounts.setText("🏷️ إجمالي الخصومات: " + totalDiscountsVal);
        tvTotalInvoices.setText("🧾 عدد الفواتير: " + totalInvoicesVal);

        topProductsAdapter.notifyDataSetChanged();
    }

    /**
     * رسم بياني مبسط جداً للأعمدة (آخر 7 أيام)
     */
    private void loadSalesTrend() {
        layoutSalesChart.removeAllViews();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        double maxSales = 100; // قيمة افتراضية للقياس
        ArrayList<Double> dailySales = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            // استخدام دوال SQLite للتعامل مع التاريخ المحلي بدقة
            String query = "SELECT SUM(total - discount) FROM sales WHERE date(sale_date, 'localtime') = date('now', 'localtime', '-" + i + " days')";
            Cursor cursor = db.rawQuery(query, null);
            double sales = 0;
            if (cursor.moveToFirst()) sales = cursor.getDouble(0);
            cursor.close();

            dailySales.add(sales);
            if (sales > maxSales) maxSales = sales;
        }

        for (int i = 0; i < dailySales.size(); i++) {
            double val = dailySales.get(i);

            // إنشاء عمود (View)
            View bar = new View(this);
            int height = (int) ((val / maxSales) * 100 * 3); // تناسبي
            if (height < 5) height = 5;

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height);
            params.weight = 1;
            params.setMargins(4, 0, 4, 0);
            bar.setLayoutParams(params);
            bar.setBackgroundColor(i == 6 ?
                    androidx.core.content.ContextCompat.getColor(this, R.color.primary) :
                    androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark));

            layoutSalesChart.addView(bar);
        }
    }

    private void exportToCSV() {
        StringBuilder csvData = new StringBuilder();
        csvData.append("ID,Name,Barcode,Price,Quantity,Expiry\n");

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM products", null);
        while (cursor.moveToNext()) {
            csvData.append(cursor.getInt(0)).append(",")
                    .append(cursor.getString(1)).append(",")
                    .append(cursor.getString(2)).append(",")
                    .append(cursor.getDouble(5)).append(",")
                    .append(cursor.getDouble(7)).append(",")
                    .append(cursor.getString(8)).append("\n");
        }
        cursor.close();

        try {
            File file = new File(getExternalFilesDir(null), "Inventory_Report.csv");
            FileOutputStream out = new FileOutputStream(file);
            out.write(csvData.toString().getBytes());
            out.close();
            shareFile(file, "text/csv", "مشاركة تقرير الجرد Excel");
        } catch (IOException e) {
            Toast.makeText(this, "خطأ في التصدير: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportToPDF() {
        PdfDocument pdf = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = pdf.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        paint.setTextSize(18);
        paint.setFakeBoldText(true);
        canvas.drawText("تقرير الأداء المالي - MyStore", 180, 50, paint);

        paint.setTextSize(14);
        paint.setFakeBoldText(false);
        canvas.drawText("الفترة: " + tvReportPeriodTitle.getText().toString(), 50, 100, paint);
        canvas.drawText(tvTotalSales.getText().toString(), 50, 130, paint);
        canvas.drawText(tvTotalProfit.getText().toString(), 50, 160, paint);
        canvas.drawText(tvTotalDebt.getText().toString(), 50, 190, paint);
        canvas.drawText(tvTotalDiscounts.getText().toString(), 50, 220, paint);

        canvas.drawText("--------------------------------------------------", 50, 250, paint);
        canvas.drawText("أهم 5 منتجات مبيعاً:", 50, 280, paint);

        int y = 310;
        for (String item : topProductsList) {
            canvas.drawText(item.replace("\n", " - "), 50, y, paint);
            y += 25;
        }

        pdf.finishPage(page);
        File file = new File(getExternalFilesDir(null), "Financial_Report.pdf");
        try {
            pdf.writeTo(new FileOutputStream(file));
            pdf.close();
            shareFile(file, "application/pdf", "مشاركة تقرير PDF");
        } catch (IOException e) {
            Toast.makeText(this, "خطأ في التصدير: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(File file, String mimeType, String title) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, title));
    }
}
