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

public class InvoiceDetailActivity extends AppCompatActivity {

    private TextView tvInvNo, tvInvDate, tvInvCustomer, tvInvTotal, tvInvPaid, tvInvRemaining, tvInvStatus, tvInvDiscount;
    private ListView lvInvItems;
    private DatabaseHelper dbHelper;

    private int saleId;
    private String invoiceNo = "";
    private String saleDate = "";
    private String customerName = "";
    private double total = 0.0;
    private double discount = 0.0;
    private double paid = 0.0;
    private double remaining = 0.0;
    private String paymentStatus = "";
    private String paymentMethod = "";

    private ArrayList<String> itemsList;
    private ArrayAdapter<String> itemsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice_detail);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        saleId = getIntent().getIntExtra("SALE_ID", -1);
        if (saleId == -1) {
            Toast.makeText(this, "خطأ في تحديد الفاتورة", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvInvNo = findViewById(R.id.tvInvoiceNo);
        tvInvDate = findViewById(R.id.tvInvoiceDate);
        tvInvCustomer = findViewById(R.id.tvInvoiceCustomer);
        tvInvTotal = findViewById(R.id.tvInvoiceTotal);
        tvInvDiscount = findViewById(R.id.tvInvoiceDiscount);
        tvInvPaid = findViewById(R.id.tvInvoicePaid);
        tvInvRemaining = findViewById(R.id.tvInvoiceRemaining);
        tvInvStatus = findViewById(R.id.tvInvoiceStatus);
        lvInvItems = findViewById(R.id.lvInvoiceItems);
        TextView tvInvoiceCashier = findViewById(R.id.tvInvoiceCashier);
        TextView tvInvoiceNetTotal = findViewById(R.id.tvInvoiceNetTotal);
        Button btnSharePdf = findViewById(R.id.btnShareInvoicePdf);
        Button btnPrint = findViewById(R.id.btnPrintInvoice);
        View btnBack = findViewById(R.id.btnInvoiceDetailBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        dbHelper = new DatabaseHelper(this);

        // تحميل اسم المستخدم الحالي ككاشير
        String[] userCreds = dbHelper.getFirstUser();
        if (tvInvoiceCashier != null) {
            tvInvoiceCashier.setText(userCreds != null && userCreds[0] != null ? userCreds[0] + " (كاشير)" : "أحمد (المسؤول)");
        }

        itemsList = new ArrayList<>();
        itemsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, itemsList);
        lvInvItems.setAdapter(itemsAdapter);

        loadInvoiceDetails();

        if (tvInvoiceNetTotal != null) {
            tvInvoiceNetTotal.setText(String.format(java.util.Locale.getDefault(), "الإجمالي النهائي: %.2f ر.س", (total - discount)));
        }

        btnSharePdf.setOnClickListener(v -> generateAndShareInvoicePdf(false));
        if (btnPrint != null) {
            btnPrint.setOnClickListener(v -> generateAndShareInvoicePdf(true));
        }

        // الشريط السفلي الثابت
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "invoices");
    }

    private void loadInvoiceDetails() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // 1. جلب بيانات الفاتورة الرئيسية والعميل (مع الخصم ووسيلة الدفع)
        String query = "SELECT s.invoice_number, s.total, s.paid, s.remaining, s.payment_status, s.sale_date, c.name, s.discount, s.payment_method " +
                "FROM sales s LEFT JOIN customers c ON s.customer_id = c.id WHERE s.id = ?";
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(saleId)});
        if (cursor.moveToFirst()) {
            invoiceNo = cursor.getString(0);
            total = cursor.getDouble(1);
            paid = cursor.getDouble(2);
            remaining = cursor.getDouble(3);
            paymentStatus = cursor.getString(4);
            saleDate = cursor.getString(5);
            customerName = cursor.getString(6);
            discount = cursor.getDouble(7);
            paymentMethod = cursor.getString(8);

            tvInvNo.setText("رقم الفاتورة: " + invoiceNo);
            tvInvDate.setText("التاريخ والوقت: " + saleDate);
            tvInvCustomer.setText("العميل: " + (customerName != null && !customerName.isEmpty() ? customerName : "عميل نقدي"));
            tvInvTotal.setText("الإجمالي قبل الخصم: " + total);
            tvInvDiscount.setText("الخصم الممنوح: " + discount);
            tvInvPaid.setText("المدفوع: " + paid);
            tvInvRemaining.setText("المتبقي: " + remaining);
            tvInvStatus.setText("الحالة: " + paymentStatus + " (" + (paymentMethod != null ? paymentMethod : "كاش") + ")");
        }
        cursor.close();

        // 2. جلب المنتجات والكميات وسعر الوحدة من sale_items
        itemsList.clear();
        String itemsQuery = "SELECT p.name, si.quantity, si.unit_price, si.total " +
                "FROM sale_items si JOIN products p ON si.product_id = p.id WHERE si.sale_id = ?";
        Cursor itemCursor = db.rawQuery(itemsQuery, new String[]{String.valueOf(saleId)});
        if (itemCursor.moveToFirst()) {
            do {
                String pName = itemCursor.getString(0);
                int pQty = itemCursor.getInt(1);
                double pPrice = itemCursor.getDouble(2);
                double pTotal = itemCursor.getDouble(3);

                itemsList.add(pName + "\nالكمية: " + pQty + " | سعر الوحدة: " + pPrice + " | الإجمالي: " + pTotal);
            } while (itemCursor.moveToNext());
        }
        itemCursor.close();

        itemsAdapter.notifyDataSetChanged();
    }

    private void generateAndShareInvoicePdf(boolean isPrint) {
        PdfDocument pdfDocument = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(14);

        int startX = 545;
        int y = 50;

        paint.setFakeBoldText(true);
        paint.setTextSize(20);
        canvas.drawText("فاتورة مبيعات رسمية - MyStore", startX - 300, y, paint);
        y += 35;

        paint.setFakeBoldText(false);
        paint.setTextSize(14);
        canvas.drawText("رقم الفاتورة: " + invoiceNo, startX - paint.measureText("رقم الفاتورة: " + invoiceNo), y, paint);
        y += 22;
        canvas.drawText("التاريخ والوقت: " + saleDate, startX - paint.measureText("التاريخ والوقت: " + saleDate), y, paint);
        y += 22;
        canvas.drawText("العميل: " + (customerName != null && !customerName.isEmpty() ? customerName : "عميل نقدي"), startX - paint.measureText("العميل: " + (customerName != null && !customerName.isEmpty() ? customerName : "عميل نقدي")), y, paint);
        y += 30;

        canvas.drawText("------------------------------------------------------------------------------------------------", 40, y, paint);
        y += 25;
        canvas.drawText("المنتجات والكميات:", startX - paint.measureText("المنتجات والكميات:"), y, paint);
        y += 20;

        for (String item : itemsList) {
            if (y > 750) break;
            String[] lines = item.split("\n");
            for (String line : lines) {
                canvas.drawText(line, startX - paint.measureText(line), y, paint);
                y += 18;
            }
            y += 8;
        }

        canvas.drawText("------------------------------------------------------------------------------------------------", 40, y, paint);
        y += 25;
        canvas.drawText("الإجمالي قبل الخصم: " + total, startX - paint.measureText("الإجمالي قبل الخصم: " + total), y, paint);
        y += 22;
        canvas.drawText("الخصم الممنوح: " + discount, startX - paint.measureText("الخصم الممنوح: " + discount), y, paint);
        y += 22;
        canvas.drawText("صافي الفاتورة: " + (total - discount), startX - paint.measureText("صافي الفاتورة: " + (total - discount)), y, paint);
        y += 22;
        canvas.drawText("المبلغ المدفوع: " + paid, startX - paint.measureText("المبلغ المدفوع: " + paid), y, paint);
        y += 22;
        canvas.drawText("المبلغ المتبقي: " + remaining, startX - paint.measureText("المبلغ المتبقي: " + remaining), y, paint);
        y += 22;
        paint.setFakeBoldText(true);
        canvas.drawText("حالة الدفع: " + paymentStatus + " (" + paymentMethod + ")", startX - paint.measureText("حالة الدفع: " + paymentStatus + " (" + paymentMethod + ")"), y, paint);

        pdfDocument.finishPage(page);

        File file = new File(getExternalFilesDir(null), "Invoice_" + invoiceNo + ".pdf");
        try {
            pdfDocument.writeTo(new FileOutputStream(file));
            pdfDocument.close();

            if (isPrint) {
                android.print.PrintManager printManager = (android.print.PrintManager) getSystemService(android.content.Context.PRINT_SERVICE);
                if (printManager != null) {
                    android.print.PrintDocumentAdapter printAdapter = new android.print.PrintDocumentAdapter() {
                        @Override
                        public void onLayout(android.print.PrintAttributes oldAttributes, android.print.PrintAttributes newAttributes, android.os.CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
                            if (cancellationSignal.isCanceled()) {
                                callback.onLayoutCancelled();
                                return;
                            }
                            android.print.PrintDocumentInfo pdi = new android.print.PrintDocumentInfo.Builder("Invoice_" + invoiceNo + ".pdf")
                                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                    .build();
                            callback.onLayoutFinished(pdi, true);
                        }

                        @Override
                        public void onWrite(android.print.PageRange[] pages, android.os.ParcelFileDescriptor destination, android.os.CancellationSignal cancellationSignal, WriteResultCallback callback) {
                            java.io.InputStream in = null;
                            java.io.OutputStream out = null;
                            try {
                                in = new java.io.FileInputStream(file);
                                out = new java.io.FileOutputStream(destination.getFileDescriptor());
                                byte[] buf = new byte[1024];
                                int size;
                                while ((size = in.read(buf)) > 0) {
                                    out.write(buf, 0, size);
                                }
                                callback.onWriteFinished(new android.print.PageRange[]{android.print.PageRange.ALL_PAGES});
                            } catch (Exception e) {
                                callback.onWriteFailed(e.getMessage());
                            } finally {
                                try { if (in != null) in.close(); if (out != null) out.close(); } catch (Exception ignored) {}
                            }
                        }
                    };
                    printManager.print("فاتورة #" + invoiceNo, printAdapter, new android.print.PrintAttributes.Builder().build());
                }
            } else {
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/pdf");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, "مشاركة الفاتورة PDF عبر"));
            }

        } catch (IOException e) {
            Toast.makeText(this, "خطأ في معالجة ملف PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            pdfDocument.close();
        }
    }
}
