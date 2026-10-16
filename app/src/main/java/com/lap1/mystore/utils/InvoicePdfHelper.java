package com.lap1.mystore.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.models.StoreSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InvoicePdfHelper {

    public static class InvoiceItemRow {
        public String name;
        public double quantity;
        public double unitPrice;
        public double total;

        public InvoiceItemRow(String name, double quantity, double unitPrice, double total) {
            this.name = name;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.total = total;
        }
    }

    public static File generateInvoicePdf(Context context, int saleId) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        StoreSettings settings = dbHelper.getStoreSettings();

        String invoiceNo = "";
        double total = 0.0, paid = 0.0, remaining = 0.0, discount = 0.0;
        String paymentStatus = "", saleDate = "", customerName = "", paymentMethod = "", cashierName = "";

        // 1. Fetch sale master
        Cursor c = db.rawQuery(
                "SELECT s.invoice_number, s.total, s.paid, s.remaining, s.payment_status, s.sale_date, " +
                        "c.name, s.discount, s.payment_method, u.full_name " +
                        "FROM sales s " +
                        "LEFT JOIN customers c ON s.customer_id = c.id " +
                        "LEFT JOIN users u ON s.created_by_user_id = u.id " +
                        "WHERE s.id = ?",
                new String[]{String.valueOf(saleId)});
        if (c.moveToFirst()) {
            invoiceNo = c.getString(0);
            total = c.getDouble(1);
            paid = c.getDouble(2);
            remaining = c.getDouble(3);
            paymentStatus = c.getString(4);
            saleDate = c.getString(5);
            customerName = c.getString(6);
            discount = c.getDouble(7);
            paymentMethod = c.getString(8);
            cashierName = c.getString(9);
        }
        c.close();

        if (customerName == null || customerName.isEmpty()) customerName = "عميل نقدي";
        if (cashierName == null || cashierName.isEmpty()) cashierName = "كاشير";
        if (paymentMethod == null || paymentMethod.isEmpty()) paymentMethod = "كاش";
        if (paymentStatus == null || paymentStatus.isEmpty()) paymentStatus = (remaining <= 0.001 ? "مدفوعة" : "آجل");

        // 2. Fetch sale items
        List<InvoiceItemRow> items = new ArrayList<>();
        Cursor cItems = db.rawQuery(
                "SELECT p.name, si.quantity, si.unit_price, si.total " +
                        "FROM sale_items si JOIN products p ON si.product_id = p.id WHERE si.sale_id = ?",
                new String[]{String.valueOf(saleId)});
        while (cItems.moveToNext()) {
            items.add(new InvoiceItemRow(cItems.getString(0), cItems.getDouble(1), cItems.getDouble(2), cItems.getDouble(3)));
        }
        cItems.close();

        // 3. Render PDF document (multi-page support)
        final int PAGE_WIDTH = 595;
        final int PAGE_HEIGHT = 842;
        final int MARGIN = 30;
        final int USABLE_WIDTH = PAGE_WIDTH - (MARGIN * 2);

        PdfDocument pdfDocument = new PdfDocument();

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.LTGRAY);
        strokePaint.setStrokeWidth(1);

        int itemIndex = 0;
        int pageNumber = 1;
        final int itemsPerPageFirst = 14;
        final int itemsPerPageSubsequent = 22;

        while (itemIndex < items.size() || pageNumber == 1) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int y = 40;

            // Header Banner
            fillPaint.setColor(Color.rgb(24, 43, 73)); // Dark Navy
            canvas.drawRoundRect(new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 65), 8, 8, fillPaint);

            paint.setColor(Color.WHITE);
            paint.setFakeBoldText(true);
            paint.setTextSize(18);
            String storeTitle = settings.getStoreName();
            canvas.drawText(storeTitle, (PAGE_WIDTH - paint.measureText(storeTitle)) / 2f, y + 28, paint);

            paint.setTextSize(10);
            paint.setFakeBoldText(false);
            String subHeader = "فاتورة مبيعات رسمية" + (settings.getPhone().isEmpty() ? "" : " | هاتف: " + settings.getPhone()) +
                    (settings.getAddress().isEmpty() ? "" : " | " + settings.getAddress());
            canvas.drawText(subHeader, (PAGE_WIDTH - paint.measureText(subHeader)) / 2f, y + 48, paint);

            y += 80;

            // Invoice Info Box
            fillPaint.setColor(Color.rgb(245, 247, 250));
            canvas.drawRoundRect(new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 65), 6, 6, fillPaint);
            canvas.drawRoundRect(new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 65), 6, 6, strokePaint);

            paint.setColor(Color.rgb(33, 37, 41));
            paint.setTextSize(10);

            // Row 1
            drawRtl(canvas, "رقم الفاتورة: " + invoiceNo, PAGE_WIDTH - MARGIN - 12, y + 20, paint);
            drawRtl(canvas, "التاريخ: " + (saleDate != null ? saleDate : "--"), PAGE_WIDTH / 2f + 20, y + 20, paint);

            // Row 2
            drawRtl(canvas, "العميل: " + customerName, PAGE_WIDTH - MARGIN - 12, y + 38, paint);
            drawRtl(canvas, "الكاشير: " + cashierName, PAGE_WIDTH / 2f + 20, y + 38, paint);

            // Row 3
            drawRtl(canvas, "طريقة الدفع: " + paymentMethod + " (" + paymentStatus + ")", PAGE_WIDTH - MARGIN - 12, y + 54, paint);

            y += 78;

            // Table Header
            fillPaint.setColor(Color.rgb(230, 235, 242));
            canvas.drawRect(new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 24), fillPaint);
            canvas.drawRect(new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 24), strokePaint);

            paint.setColor(Color.rgb(20, 35, 60));
            paint.setFakeBoldText(true);
            paint.setTextSize(10);

            float colNo = PAGE_WIDTH - MARGIN - 25;
            float colName = PAGE_WIDTH - MARGIN - 60;
            float colQty = MARGIN + 160;
            float colPrice = MARGIN + 95;
            float colTotal = MARGIN + 20;

            drawRtl(canvas, "#", colNo, y + 16, paint);
            drawRtl(canvas, "الصنف / البيان", colName, y + 16, paint);
            drawRtl(canvas, "الكمية", colQty, y + 16, paint);
            drawRtl(canvas, "السعر", colPrice, y + 16, paint);
            drawRtl(canvas, "الإجمالي", colTotal, y + 16, paint);

            y += 24;
            paint.setFakeBoldText(false);

            int itemsLimit = (pageNumber == 1) ? itemsPerPageFirst : itemsPerPageSubsequent;
            int countThisPage = 0;

            while (itemIndex < items.size() && countThisPage < itemsLimit) {
                InvoiceItemRow row = items.get(itemIndex);
                fillPaint.setColor((itemIndex % 2 == 0) ? Color.WHITE : Color.rgb(250, 251, 253));
                canvas.drawRect(new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 20), fillPaint);
                canvas.drawRect(new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 20), strokePaint);

                paint.setColor(Color.DKGRAY);
                paint.setTextSize(9);

                drawRtl(canvas, String.valueOf(itemIndex + 1), colNo, y + 14, paint);

                // Truncate long item names if needed
                String pName = row.name != null ? row.name : "";
                if (paint.measureText(pName) > 230) {
                    while (pName.length() > 3 && paint.measureText(pName + "...") > 230) {
                        pName = pName.substring(0, pName.length() - 1);
                    }
                    pName += "...";
                }
                drawRtl(canvas, pName, colName, y + 14, paint);

                drawRtl(canvas, String.format(Locale.US, "%.2f", row.quantity), colQty, y + 14, paint);
                drawRtl(canvas, String.format(Locale.US, "%.2f", row.unitPrice), colPrice, y + 14, paint);
                drawRtl(canvas, String.format(Locale.US, "%.2f", row.total), colTotal, y + 14, paint);

                y += 20;
                itemIndex++;
                countThisPage++;
            }

            // If last page, print summary totals box
            boolean isLastPage = (itemIndex >= items.size());
            if (isLastPage) {
                y += 10;
                fillPaint.setColor(Color.rgb(245, 248, 252));
                canvas.drawRoundRect(new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 90), 6, 6, fillPaint);
                canvas.drawRoundRect(new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 90), 6, 6, strokePaint);

                paint.setColor(Color.rgb(30, 40, 55));
                paint.setTextSize(10);

                double netTotal = total - discount;
                String curr = settings.getCurrency();

                drawRtl(canvas, "الإجمالي: " + String.format(Locale.US, "%.2f %s", total, curr), PAGE_WIDTH - MARGIN - 16, y + 22, paint);
                drawRtl(canvas, "الخصم: " + String.format(Locale.US, "%.2f %s", discount, curr), PAGE_WIDTH - MARGIN - 16, y + 42, paint);

                paint.setFakeBoldText(true);
                paint.setColor(Color.rgb(0, 100, 50)); // Dark green
                drawRtl(canvas, "الصافي: " + String.format(Locale.US, "%.2f %s", netTotal, curr), PAGE_WIDTH - MARGIN - 16, y + 66, paint);

                paint.setFakeBoldText(false);
                paint.setColor(Color.DKGRAY);
                drawRtl(canvas, "المدفوع: " + String.format(Locale.US, "%.2f %s", paid, curr), PAGE_WIDTH / 2f + 10, y + 22, paint);
                drawRtl(canvas, "المتبقي: " + String.format(Locale.US, "%.2f %s", remaining, curr), PAGE_WIDTH / 2f + 10, y + 42, paint);

                // Footer Note
                String footer = settings.getInvoiceFooter();
                if (footer.isEmpty()) footer = "شكراً لتعاملكم معنا!";
                y += 105;
                paint.setTextSize(9);
                paint.setColor(Color.GRAY);
                canvas.drawText(footer, (PAGE_WIDTH - paint.measureText(footer)) / 2f, y, paint);
            }

            // Page Number
            paint.setTextSize(8);
            paint.setColor(Color.GRAY);
            String pageStr = "صفحة " + pageNumber;
            canvas.drawText(pageStr, MARGIN, PAGE_HEIGHT - 20, paint);

            pdfDocument.finishPage(page);
            pageNumber++;
            if (isLastPage) break;
        }

        File outFile = new File(context.getExternalFilesDir(null), "Invoice_" + invoiceNo + ".pdf");
        try {
            pdfDocument.writeTo(new FileOutputStream(outFile));
            pdfDocument.close();
            return outFile;
        } catch (Exception e) {
            try { pdfDocument.close(); } catch (Exception ignored) {}
            return null;
        }
    }

    public static void printOrShareInvoice(Activity activity, int saleId, boolean isPrint) {
        File file = generateInvoicePdf(activity, saleId);
        if (file == null || !file.exists()) {
            Toast.makeText(activity, "تعذر إنشاء ملف الفاتورة PDF", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isPrint) {
            PrintManager printManager = (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
            if (printManager != null) {
                PrintDocumentAdapter printAdapter = new PrintDocumentAdapter() {
                    @Override
                    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes, CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
                        if (cancellationSignal.isCanceled()) {
                            callback.onLayoutCancelled();
                            return;
                        }
                        PrintDocumentInfo pdi = new PrintDocumentInfo.Builder(file.getName())
                                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                .build();
                        callback.onLayoutFinished(pdi, true);
                    }

                    @Override
                    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination, CancellationSignal cancellationSignal, WriteResultCallback callback) {
                        try (InputStream in = new FileInputStream(file);
                             OutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
                            byte[] buf = new byte[1024];
                            int size;
                            while ((size = in.read(buf)) > 0) {
                                out.write(buf, 0, size);
                            }
                            callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                        } catch (Exception e) {
                            callback.onWriteFailed(e.getMessage());
                        }
                    }
                };
                printManager.print("فاتورة مبيعات", printAdapter, new PrintAttributes.Builder().build());
            }
        } else {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "مشاركة الفاتورة PDF عبر"));
        }
    }

    private static void drawRtl(Canvas canvas, String text, float rightX, float baseline, Paint paint) {
        if (text == null) text = "";
        canvas.drawText(text, rightX - paint.measureText(text), baseline, paint);
    }
}
