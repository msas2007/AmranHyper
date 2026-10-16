package com.lap1.mystore.utils;

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
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.lap1.mystore.activities.CustomerDetailActivity;
import com.lap1.mystore.database.DatabaseHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CustomerStatementPdfHelper {

    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int LEFT = 40;
    private static final int RIGHT = 555;

    public static class PdfHelper {
        public Paint paint(int color, float textSize, boolean bold) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(color);
            p.setTextSize(textSize);
            p.setFakeBoldText(bold);
            return p;
        }

        public void drawCenter(Canvas canvas, String text, float cx, float cy, Paint paint) {
            if (text == null) text = "";
            float w = paint.measureText(text);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float baseline = cy - (fm.descent + fm.ascent) / 2f;
            canvas.drawText(text, cx - w / 2f, baseline, paint);
        }

        public void drawRight(Canvas canvas, String text, float rightX, float y, Paint paint) {
            if (text == null) text = "";
            canvas.drawText(text, rightX - paint.measureText(text), y, paint);
        }

        public void drawLeft(Canvas canvas, String text, float leftX, float y, Paint paint) {
            if (text == null) text = "";
            canvas.drawText(text, leftX, y, paint);
        }

        public void line(Canvas canvas, float x1, float y1, float x2, float y2, int color, float strokeWidth) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(color);
            p.setStrokeWidth(strokeWidth);
            canvas.drawLine(x1, y1, x2, y2, p);
        }

        public void roundRect(Canvas canvas, float l, float t, float r, float b, float radius, int fillColor) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(fillColor);
            p.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
        }
    }

    public static class SaleItemDetail {
        public String productName;
        public double quantity;
        public double unitPrice;
        public double total;

        public SaleItemDetail(String productName, double quantity, double unitPrice, double total) {
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.total = total;
        }
    }

    public static class SaleInvoiceDetail {
        public String invoiceNo;
        public double total;
        public double discount;
        public double paid;
        public double remaining;
        public String paymentMethod;
        public String saleDate;
        public String notes;
        public List<SaleItemDetail> items = new ArrayList<>();
    }

    public static void generateAndShareStatementPdf(Context context, int customerId, String customerName, String customerPhone,
                                                    double sumSales, double sumPayments, double sumReturns, double currentBalance,
                                                    List<CustomerDetailActivity.CustomerLedgerModel> ledgerList) {
        PdfHelper h = new PdfHelper();
        PdfDocument pdfDocument = new PdfDocument();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        try {
            int rowIndex = 0;
            int pageNumber = 1;

            while (rowIndex < ledgerList.size() || (ledgerList.isEmpty() && pageNumber == 1)) {
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
                PdfDocument.Page page = pdfDocument.startPage(pageInfo);
                Canvas canvas = page.getCanvas();

                // 1. Header Banner
                Paint headerPaint = h.paint(Color.rgb(20, 55, 105), 18, true);
                canvas.drawRect(0, 0, PAGE_WIDTH, 88, headerPaint);

                Paint whitePaint = h.paint(Color.WHITE, 13, true);
                canvas.drawCircle(58, 44, 26, whitePaint);
                Paint logoPaint = h.paint(Color.rgb(20, 55, 105), 13, true);
                h.drawCenter(canvas, "AH", 58, 49, logoPaint);

                Paint headerText = h.paint(Color.WHITE, 18, true);
                h.drawRight(canvas, "عمران هايبر للتموين والتجارة", RIGHT - 18, 31, headerText);

                Paint headerSub = h.paint(Color.rgb(220, 230, 245), 9, false);
                h.drawRight(canvas, "MyStore • كشف حساب معتمد (ماله وما عليه)", RIGHT - 18, 52, headerSub);
                h.drawRight(canvas, "جميع المبالغ بالريال اليمني", RIGHT - 18, 69, headerSub);

                int y = 106;

                Paint titlePaint = h.paint(Color.rgb(25, 45, 70), 16, true);
                h.drawRight(canvas, pageNumber == 1 ? "كشف حساب العميل (الذمم المالية)" : "تكملة كشف الحساب", RIGHT, y, titlePaint);
                y += 18;

                Paint subtitlePaint = h.paint(Color.rgb(105, 112, 122), 9, false);
                h.drawRight(canvas, pageNumber == 1 ? "تفاصيل دقيقة لجميع المبيعات، المدفوعات، والأرصدة" : customerName, RIGHT, y, subtitlePaint);
                y += 16;

                if (pageNumber == 1) {
                    String issueDate = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(new Date());
                    h.drawRight(canvas, "تاريخ الإصدار: " + issueDate, RIGHT, y, subtitlePaint);
                    y += 12;

                    h.line(canvas, LEFT, y, RIGHT, y, Color.rgb(210, 216, 224), 1);
                    y += 10;

                    // Customer Info Box
                    h.roundRect(canvas, LEFT, y, RIGHT, y + 42, 8, Color.rgb(247, 249, 252));
                    Paint custText = h.paint(Color.rgb(30, 41, 59), 11, true);
                    h.drawRight(canvas, "العميل الكريم: " + customerName, RIGHT - 12, y + 17, custText);
                    h.drawRight(canvas, "رقم الهاتف: " + (customerPhone == null || customerPhone.isEmpty() ? "غير متوفر" : customerPhone), RIGHT - 12, y + 33, custText);
                    y += 50;

                    // Financial Summary Box
                    h.roundRect(canvas, LEFT, y, RIGHT, y + 46, 8, Color.rgb(235, 242, 250));
                    Paint sumLabel = h.paint(Color.rgb(70, 85, 110), 9, false);
                    Paint sumVal = h.paint(Color.rgb(20, 55, 105), 11, true);

                    h.drawCenter(canvas, "إجمالي المبيعات", (LEFT + RIGHT) / 3f, y + 15, sumLabel);
                    h.drawCenter(canvas, String.format(Locale.US, "%.2f ر.ي", sumSales), (LEFT + RIGHT) / 3f, y + 34, sumVal);

                    h.drawCenter(canvas, "إجمالي التحصيل", (LEFT + RIGHT) / 2f, y + 15, sumLabel);
                    h.drawCenter(canvas, String.format(Locale.US, "%.2f ر.ي", sumPayments), (LEFT + RIGHT) / 2f, y + 34, sumVal);

                    h.drawCenter(canvas, "صافي المديونية (عليك)", (LEFT + RIGHT) * 0.82f, y + 15, sumLabel);
                    h.drawCenter(canvas, String.format(Locale.US, "%.2f ر.ي", currentBalance), (LEFT + RIGHT) * 0.82f, y + 34, h.paint(Color.rgb(217, 91, 91), 11, true));

                    y += 58;
                }

                Paint rowPaint = h.paint(Color.rgb(30, 41, 59), 9f, false);
                Paint rowPaintBold = h.paint(Color.rgb(30, 41, 59), 9.5f, true);

                while (rowIndex < ledgerList.size()) {
                    if (y > 730) {
                        break; // Go to next page
                    }

                    CustomerDetailActivity.CustomerLedgerModel item = ledgerList.get(rowIndex);

                    if (DatabaseHelper.TX_SALE.equals(item.type) && item.refId > 0) {
                        SaleInvoiceDetail inv = fetchSaleDetail(db, item.refId);

                        int boxHeight = 38 + (inv.items.size() * 16) + 18;
                        if (y + boxHeight > 760) {
                            break;
                        }

                        // Invoice Header box
                        h.roundRect(canvas, LEFT, y, RIGHT, y + 22, 4, Color.rgb(224, 231, 242));
                        Paint invHeaderPaint = h.paint(Color.rgb(20, 55, 105), 9.5f, true);
                        h.drawLeft(canvas, "🛒 فاتورة مبيعات #" + (inv.invoiceNo != null ? inv.invoiceNo : item.refId), LEFT + 8, y + 15, invHeaderPaint);
                        h.drawRight(canvas, "التاريخ: " + (item.date != null ? item.date : ""), RIGHT - 8, y + 15, invHeaderPaint);
                        y += 24;

                        // Items table header
                        Paint thP = h.paint(Color.rgb(100, 115, 140), 8.5f, true);
                        h.drawLeft(canvas, "اسم الصنف", LEFT + 10, y, thP);
                        h.drawCenter(canvas, "الكمية", LEFT + 310, y, thP);
                        h.drawRight(canvas, "السعر", RIGHT - 90, y, thP);
                        h.drawRight(canvas, "الإجمالي", RIGHT - 8, y, thP);
                        y += 12;
                        h.line(canvas, LEFT, y, RIGHT, y, Color.rgb(210, 216, 224), 0.5f);
                        y += 4;

                        // Items
                        for (SaleItemDetail si : inv.items) {
                            h.drawLeft(canvas, si.productName, LEFT + 10, y, rowPaint);
                            h.drawCenter(canvas, String.format(Locale.US, "%.1f", si.quantity), LEFT + 310, y, rowPaint);
                            h.drawRight(canvas, String.format(Locale.US, "%.2f", si.unitPrice), RIGHT - 90, y, rowPaint);
                            h.drawRight(canvas, String.format(Locale.US, "%.2f", si.total), RIGHT - 8, y, rowPaintBold);
                            y += 16;
                        }

                        // Invoice footer totals & running balance
                        h.line(canvas, LEFT, y, RIGHT, y, Color.rgb(210, 216, 224), 0.5f);
                        y += 12;
                        Paint invFooterP = h.paint(Color.rgb(30, 41, 59), 9f, true);
                        h.drawLeft(canvas, String.format(Locale.US, "الإجمالي: %.2f | المدفوع: %.2f | المتبقي: %.2f", inv.total, inv.paid, inv.remaining), LEFT + 10, y, invFooterP);
                        h.drawRight(canvas, String.format(Locale.US, "الرصيد بعدها: %.2f", item.runningBalance), RIGHT - 8, y, h.paint(Color.rgb(20, 55, 105), 9.5f, true));
                        y += 20;

                    } else {
                        // Render standard ledger transaction row
                        h.roundRect(canvas, LEFT, y, RIGHT, y + 24, 4, Color.rgb(247, 249, 252));

                        String typeStr;
                        if (DatabaseHelper.TX_OPENING.equals(item.type)) typeStr = "📌 رصيد افتتاحي مديونية";
                        else if (DatabaseHelper.TX_SALE_PAYMENT.equals(item.type)) typeStr = "💵 مدفوع بيع #" + item.refId;
                        else if (DatabaseHelper.TX_PAYMENT.equals(item.type)) typeStr = "💵 سند تحصيل دين";
                        else if (DatabaseHelper.TX_RETURN.equals(item.type)) typeStr = "↩️ مرتجع مبيعات #" + item.refId;
                        else typeStr = "🔹 حركة حساب";

                        if (item.notes != null && !item.notes.trim().isEmpty()) {
                            typeStr += " (" + item.notes + ")";
                        }

                        String dateStr = item.date != null ? item.date : "";
                        if (dateStr.length() > 16) dateStr = dateStr.substring(0, 16);

                        h.drawLeft(canvas, dateStr, LEFT + 8, y + 15, rowPaint);
                        h.drawLeft(canvas, typeStr, LEFT + 130, y + 15, rowPaint);

                        String amtStr = String.format(Locale.US, "%.2f", Math.abs(item.amount)) + (item.amount > 0 ? " +" : " -");
                        Paint amtPaint = h.paint(item.amount > 0 ? Color.rgb(217, 91, 91) : Color.rgb(32, 165, 107), 9.5f, true);
                        h.drawRight(canvas, amtStr, RIGHT - 100, y + 15, amtPaint);

                        String balStr = String.format(Locale.US, "%.2f", item.runningBalance);
                        h.drawRight(canvas, balStr, RIGHT - 8, y + 15, rowPaintBold);

                        y += 28;
                    }

                    rowIndex++;
                }

                // Page Number Footer
                Paint footerPaint = h.paint(Color.rgb(120, 130, 145), 9, false);
                h.drawCenter(canvas, "الصفحة " + pageNumber, PAGE_WIDTH / 2f, 825, footerPaint);

                pdfDocument.finishPage(page);
                pageNumber++;
            }

            File outFile = new File(context.getExternalFilesDir(null), "Customer_Statement_" + customerId + ".pdf");
            pdfDocument.writeTo(new FileOutputStream(outFile));
            pdfDocument.close();

            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", outFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "مشاركة كشف حساب العميل PDF"));

        } catch (Exception e) {
            Toast.makeText(context, "تعذر إنشاء ملف PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            try { pdfDocument.close(); } catch (Exception ignored) {}
        } finally {
            db.close();
        }
    }

    private static SaleInvoiceDetail fetchSaleDetail(SQLiteDatabase db, long saleId) {
        SaleInvoiceDetail detail = new SaleInvoiceDetail();
        Cursor c = db.rawQuery(
                "SELECT invoice_number, total, discount, paid, remaining, payment_method, sale_date, notes " +
                        "FROM sales WHERE id = ?", new String[]{String.valueOf(saleId)});
        if (c.moveToFirst()) {
            detail.invoiceNo = c.getString(0);
            detail.total = c.getDouble(1);
            detail.discount = c.getDouble(2);
            detail.paid = c.getDouble(3);
            detail.remaining = c.getDouble(4);
            detail.paymentMethod = c.getString(5);
            detail.saleDate = c.getString(6);
            detail.notes = c.getString(7);
        }
        c.close();

        Cursor ci = db.rawQuery(
                "SELECT p.name, si.quantity, si.unit_price, si.total " +
                        "FROM sale_items si JOIN products p ON si.product_id = p.id WHERE si.sale_id = ?",
                new String[]{String.valueOf(saleId)});
        while (ci.moveToNext()) {
            detail.items.add(new SaleItemDetail(ci.getString(0), ci.getDouble(1), ci.getDouble(2), ci.getDouble(3)));
        }
        ci.close();

        return detail;
    }
}
