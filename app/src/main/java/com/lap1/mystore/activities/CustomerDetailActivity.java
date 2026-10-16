package com.lap1.mystore.activities;

import android.content.ContentValues;
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
import android.widget.EditText;
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

public class CustomerDetailActivity extends AppCompatActivity {

    private TextView tvName, tvPhone, tvSummaryDebt, tvSummaryPaid, tvSummaryBalance;
    private EditText etSettlementAmount;
    private ListView lvHistory;
    private DatabaseHelper dbHelper;

    private int customerId;
    private String customerName = "";
    private String customerPhone = "";
    private double currentBalance = 0.0;
    private double totalDebt = 0.0;
    private double totalPaid = 0.0;

    private ArrayList<String> historyList;
    private ArrayAdapter<String> historyAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_detail);

        setTitle("سجل حساب العميل وتفاصيل المنتجات");

        customerId = getIntent().getIntExtra("CUSTOMER_ID", -1);
        if (customerId == -1) {
            Toast.makeText(this, "خطأ في تحديد العميل", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvName = findViewById(R.id.tvDetailCustomerName);
        tvPhone = findViewById(R.id.tvDetailCustomerPhone);
        tvSummaryDebt = findViewById(R.id.tvSummaryDebt);
        tvSummaryPaid = findViewById(R.id.tvSummaryPaid);
        tvSummaryBalance = findViewById(R.id.tvSummaryBalance);
        etSettlementAmount = findViewById(R.id.etSettlementAmount);
        Button btnPayDebt = findViewById(R.id.btnPayDebt);
        Button btnSharePdf = findViewById(R.id.btnShareCustomerPdf);
        View btnBack = findViewById(R.id.btnCustomerDetailBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
        lvHistory = findViewById(R.id.lvCustomerHistory);

        dbHelper = new DatabaseHelper(this);
        historyList = new ArrayList<>();
        historyAdapter = new ArrayAdapter<>(this, R.layout.item_history_entry, R.id.tvHistoryText, historyList);
        lvHistory.setAdapter(historyAdapter);

        loadCustomerData();

        btnPayDebt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                processSettlement();
            }
        });

        btnSharePdf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateAndSharePdf();
            }
        });

        // الشريط السفلي الثابت
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "");
    }

    private void loadCustomerData() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // 1. جلب بيانات العميل
        Cursor cCust = db.rawQuery("SELECT name, phone, balance FROM customers WHERE id = ?", new String[]{String.valueOf(customerId)});
        if (cCust.moveToFirst()) {
            customerName = cCust.getString(cCust.getColumnIndexOrThrow("name"));
            customerPhone = cCust.getString(cCust.getColumnIndexOrThrow("phone"));
            currentBalance = cCust.getDouble(cCust.getColumnIndexOrThrow("balance"));

            tvName.setText(customerName);
            tvPhone.setText("الهاتف: " + (customerPhone != null && !customerPhone.isEmpty() ? customerPhone : "غير متوفر"));
        }
        cCust.close();

        // 2. حساب إجمالي المشتريات الآجلة والمدفوع عند إنشاء الفواتير
        Cursor cSales = db.rawQuery("SELECT SUM(total), SUM(paid) FROM sales WHERE customer_id = ?", new String[]{String.valueOf(customerId)});
        if (cSales.moveToFirst()) {
            totalDebt = cSales.getDouble(0);
            totalPaid = cSales.getDouble(1);
        }
        cSales.close();

        // [FIX] إضافة مبالغ السداد اللاحقة من جدول payments إلى إجمالي المدفوع
        Cursor cPaySum = db.rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE customer_id = ?",
                new String[]{String.valueOf(customerId)}
        );
        if (cPaySum.moveToFirst()) {
            totalPaid += cPaySum.getDouble(0);
        }
        cPaySum.close();

        tvSummaryDebt.setText(String.format(Locale.US, "إجمالي المشتريات الآجلة: %.2f", totalDebt));
        tvSummaryPaid.setText(String.format(Locale.US, "إجمالي المدفوع: %.2f", totalPaid));
        tvSummaryBalance.setText(String.format(Locale.US, "الرصيد المتبقي (الدين): %.2f", currentBalance));

        // 3. جلب كشف حساب العميل التحليلي بالترتيب الزمني مع الرصيد التراكمي
        historyList.clear();

        class CustomerTx {
            int id;
            String type; // SALE, PAYMENT, RETURN
            String date;
            double amountChange; // زيادة أو نقص في الدين
            String displayText;

            CustomerTx(int id, String type, String date, double amountChange, String displayText) {
                this.id = id;
                this.type = type;
                this.date = date != null ? date : "";
                this.amountChange = amountChange;
                this.displayText = displayText;
            }
        }

        ArrayList<CustomerTx> txList = new ArrayList<>();

        // أ. الفواتير
        Cursor cInvoices = db.rawQuery(
                "SELECT id, invoice_number, total, paid, remaining, payment_status, sale_date FROM sales WHERE customer_id = ? ORDER BY id ASC",
                new String[]{String.valueOf(customerId)}
        );
        while (cInvoices.moveToNext()) {
            int saleId = cInvoices.getInt(0);
            String inv = cInvoices.getString(1);
            double tot = cInvoices.getDouble(2);
            double paidVal = cInvoices.getDouble(3);
            double rem = cInvoices.getDouble(4);
            String status = cInvoices.getString(5);
            String date = cInvoices.getString(6);

            StringBuilder productsStr = new StringBuilder();
            Cursor cItems = db.rawQuery(
                    "SELECT p.name, si.quantity, si.unit_price FROM sale_items si JOIN products p ON si.product_id = p.id WHERE si.sale_id = ?",
                    new String[]{String.valueOf(saleId)}
            );
            while (cItems.moveToNext()) {
                productsStr.append("\n  • ").append(cItems.getString(0))
                        .append(" (الكمية: ").append(cItems.getInt(1))
                        .append(" × ").append(cItems.getDouble(2)).append(")");
            }
            cItems.close();

            String text = "📄 فاتورة مبيعات #" + inv + " (" + date + ")" +
                    productsStr.toString() +
                    "\nالإجمالي: " + String.format(Locale.US, "%.2f", tot) +
                    " | المدفوع: " + String.format(Locale.US, "%.2f", paidVal) +
                    " | الآجل الجديد: " + String.format(Locale.US, "%.2f", rem) +
                    " [" + status + "]";

            txList.add(new CustomerTx(saleId, "SALE", date, rem, text));
        }
        cInvoices.close();

        // ب. الدفعات المسددة لاحقاً
        Cursor cPayments = db.rawQuery(
                "SELECT id, amount, payment_date, notes FROM payments WHERE customer_id = ? ORDER BY id ASC",
                new String[]{String.valueOf(customerId)}
        );
        while (cPayments.moveToNext()) {
            int pId = cPayments.getInt(0);
            double amt = cPayments.getDouble(1);
            String pDate = cPayments.getString(2);
            String notes = cPayments.getString(3);

            String text = "💵 دفعة سداد دين: " + String.format(Locale.US, "%.2f ر.س", amt) +
                    "\nالتاريخ: " + pDate +
                    "\nملاحظات: " + (notes != null && !notes.isEmpty() ? notes : "تسديد حساب");

            txList.add(new CustomerTx(pId, "PAYMENT", pDate, -amt, text));
        }
        cPayments.close();

        // ج. المرتجعات المخصومة من الدين
        Cursor cReturns = db.rawQuery(
                "SELECT r.id, r.amount, r.debt_reduction, r.return_date, s.invoice_number " +
                        "FROM returns r JOIN sales s ON r.sale_id = s.id WHERE s.customer_id = ? AND r.debt_reduction > 0 ORDER BY r.id ASC",
                new String[]{String.valueOf(customerId)}
        );
        while (cReturns.moveToNext()) {
            int rId = cReturns.getInt(0);
            double retAmount = cReturns.getDouble(1);
            double debtRed = cReturns.getDouble(2);
            String rDate = cReturns.getString(3);
            String invNum = cReturns.getString(4);

            String text = "↩️ مرتجع مبيعات للفاتورة #" + invNum + " (" + rDate + ")" +
                    "\nقيمة المرتجع: " + String.format(Locale.US, "%.2f ر.س", retAmount) +
                    " | خصم من الدين: " + String.format(Locale.US, "%.2f ر.س", debtRed);

            txList.add(new CustomerTx(rId, "RETURN", rDate, -debtRed, text));
        }
        cReturns.close();

        // ترتيب الحركات زمنيًا حسب التاريخ والـ ID
        txList.sort((o1, o2) -> {
            int dateCmp = o1.date.compareTo(o2.date);
            if (dateCmp != 0) return dateCmp;
            return Integer.compare(o1.id, o2.id);
        });

        // حساب الرصيد المتراكم خطوة بخطوة
        double runningBal = 0.0;
        for (CustomerTx tx : txList) {
            runningBal += tx.amountChange;
            if (runningBal < 0) runningBal = 0;
            String entry = tx.displayText + "\n📊 الرصيد المتبقي بعد الحركة: " + String.format(Locale.US, "%.2f ر.س", runningBal);
            historyList.add(entry);
        }

        if (historyList.isEmpty()) {
            historyList.add("لا توجد عمليات مسجلة لهذا العميل حتى الآن.");
        }

        historyAdapter.notifyDataSetChanged();
    }

    private void processSettlement() {
        String amountStr = etSettlementAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال مبلغ السداد", Toast.LENGTH_SHORT).show();
            return;
        }

        double paymentAmount;
        try {
            paymentAmount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "مبلغ السداد غير صالح", Toast.LENGTH_SHORT).show();
            return;
        }

        if (paymentAmount <= 0) {
            Toast.makeText(this, "مبلغ السداد غير صالح", Toast.LENGTH_SHORT).show();
            return;
        }

        if (paymentAmount > currentBalance + 0.01) {
            Toast.makeText(this, "مبلغ السداد أكبر من إجمالي الدين الحالي للعميل!", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // 1. تسجيل الدفعة في جدول payments
            ContentValues cv = new ContentValues();
            cv.put("customer_id", customerId);
            cv.put("amount", paymentAmount);
            cv.put("notes", "تصفية وسداد جزء/كل الدين");
            db.insert("payments", null, cv);

            // 2. تخفيض رصيد العميل الإجمالي
            db.execSQL("UPDATE customers SET balance = balance - ? WHERE id = ?", new Object[]{paymentAmount, customerId});

            // [FIX] 3. توزيع مبلغ السداد على الفواتير المستحقة (من الأقدم للأحدث)
            double remainingPayment = paymentAmount;
            Cursor cOutstanding = db.rawQuery(
                    "SELECT id, remaining FROM sales WHERE customer_id = ? AND remaining > 0.001 ORDER BY id ASC",
                    new String[]{String.valueOf(customerId)}
            );
            while (cOutstanding.moveToNext() && remainingPayment > 0.001) {
                int invId = cOutstanding.getInt(0);
                double invRemaining = cOutstanding.getDouble(1);
                double toApply = Math.min(remainingPayment, invRemaining);
                double newRemaining = invRemaining - toApply;
                String newStatus = (newRemaining <= 0.001) ? "مدفوعة بالكامل" : "دفع جزئي";
                db.execSQL(
                        "UPDATE sales SET remaining = ?, paid = paid + ?, payment_status = ? WHERE id = ?",
                        new Object[]{newRemaining, toApply, newStatus, invId}
                );
                remainingPayment -= toApply;
            }
            cOutstanding.close();

            db.setTransactionSuccessful();
            Toast.makeText(this, "تم تسجيل السداد بنجاح!", Toast.LENGTH_SHORT).show();
            etSettlementAmount.setText("");

            if (currentBalance - paymentAmount <= 0.01) {
                Toast.makeText(this, "🎉 مبروك! تمت تصفية الحساب بالكامل وبدء دورة جديدة.", Toast.LENGTH_LONG).show();
            }

            loadCustomerData();

        } catch (Exception e) {
            Toast.makeText(this, "خطأ: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            db.endTransaction();
        }
    }

    private void generateAndSharePdf() {
        PdfDocument pdfDocument = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(14);

        int startX = 545; // للغة العربية من اليمين
        int y = 50;

        paint.setFakeBoldText(true);
        paint.setTextSize(18);
        canvas.drawText("تقرير كشف حساب العميل - MyStore", startX - 280, y, paint);
        y += 35;

        paint.setFakeBoldText(false);
        paint.setTextSize(14);
        canvas.drawText("اسم العميل: " + customerName, startX - paint.measureText("اسم العميل: " + customerName), y, paint);
        y += 22;
        canvas.drawText("الهاتف: " + (customerPhone != null ? customerPhone : "غير متوفر"), startX - paint.measureText("الهاتف: " + (customerPhone != null ? customerPhone : "غير متوفر")), y, paint);
        y += 30;

        canvas.drawText("------------------------------------------------------------------------------------------------", 40, y, paint);
        y += 25;
        canvas.drawText(String.format("إجمالي المشتريات الآجلة: %.2f", totalDebt), startX - paint.measureText(String.format("إجمالي المشتريات الآجلة: %.2f", totalDebt)), y, paint);
        y += 22;
        canvas.drawText(String.format("إجمالي المدفوع: %.2f", totalPaid), startX - paint.measureText(String.format("إجمالي المدفوع: %.2f", totalPaid)), y, paint);
        y += 22;
        paint.setFakeBoldText(true);
        canvas.drawText(String.format("الرصيد المتبقي (الدين): %.2f", currentBalance), startX - paint.measureText(String.format("الرصيد المتبقي (الدين): %.2f", currentBalance)), y, paint);
        y += 35;
        paint.setFakeBoldText(false);

        canvas.drawText("سجل الحركات وتفاصيل المنتجات:", startX - paint.measureText("سجل الحركات وتفاصيل المنتجات:"), y, paint);
        y += 22;
        canvas.drawText("------------------------------------------------------------------------------------------------", 40, y, paint);
        y += 25;

        for (String item : historyList) {
            if (y > 780) break;
            String[] lines = item.split("\n");
            for (String line : lines) {
                canvas.drawText(line, startX - paint.measureText(line), y, paint);
                y += 18;
            }
            y += 10;
        }

        pdfDocument.finishPage(page);

        File file = new File(getExternalFilesDir(null), "Customer_Statement_" + customerId + ".pdf");
        try {
            pdfDocument.writeTo(new FileOutputStream(file));
            pdfDocument.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "مشاركة كشف الحساب PDF عبر"));

        } catch (IOException e) {
            Toast.makeText(this, "خطأ في إنشاء ملف PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            pdfDocument.close();
        }
    }
}
