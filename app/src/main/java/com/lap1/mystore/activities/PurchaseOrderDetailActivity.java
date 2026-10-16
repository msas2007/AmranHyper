package com.lap1.mystore.activities;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.models.PurchaseOrder;
import com.lap1.mystore.models.StockMovement;
import com.lap1.mystore.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PurchaseOrderDetailActivity extends AppCompatActivity {

    private int orderId;
    private DatabaseHelper dbHelper;

    private TextView tvTitle, tvDate, tvStatusBadge, tvSupplier, tvNotes, tvTotal, tvItemsHeader;
    private LinearLayout layoutItems, layoutActions;
    private Button btnMarkRequested, btnReceive, btnCancel;

    private String currentStatus = PurchaseOrder.STATUS_DRAFT;
    private int supplierId = 0;
    private String supplierName = "";
    private double orderTotal = 0.0;
    private final List<OrderItemData> orderItems = new ArrayList<>();

    private static class OrderItemData {
        int productId;
        String productName;
        double quantity;
        double unitPrice;
        double total;
        String unitName;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_order_detail);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        orderId = getIntent().getIntExtra("PO_ID", -1);
        if (orderId <= 0) {
            Toast.makeText(this, "طلب الشراء غير صالح", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        dbHelper = new DatabaseHelper(this);

        tvTitle = findViewById(R.id.tvPoDetailTitle);
        tvDate = findViewById(R.id.tvPoDetailDate);
        tvStatusBadge = findViewById(R.id.tvPoDetailStatusBadge);
        tvSupplier = findViewById(R.id.tvPoDetailSupplier);
        tvNotes = findViewById(R.id.tvPoDetailNotes);
        tvTotal = findViewById(R.id.tvPoDetailTotal);
        tvItemsHeader = findViewById(R.id.tvPoDetailItemsHeader);
        layoutItems = findViewById(R.id.layoutPoDetailItems);
        layoutActions = findViewById(R.id.layoutPoActions);

        btnMarkRequested = findViewById(R.id.btnPoMarkRequested);
        btnReceive = findViewById(R.id.btnPoReceive);
        btnCancel = findViewById(R.id.btnPoCancel);

        ImageButton btnBack = findViewById(R.id.btnPoDetailBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        if (btnMarkRequested != null) btnMarkRequested.setOnClickListener(v -> updateOrderStatus(PurchaseOrder.STATUS_REQUESTED));
        if (btnCancel != null) btnCancel.setOnClickListener(v -> updateOrderStatus(PurchaseOrder.STATUS_CANCELLED));
        if (btnReceive != null) btnReceive.setOnClickListener(v -> showReceiveOrderDialog());

        loadOrderData();
    }

    private void loadOrderData() {
        orderItems.clear();
        layoutItems.removeAllViews();

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cPo = db.rawQuery(
                "SELECT po.order_number, po.supplier_id, s.name, po.total, po.status, po.notes, po.created_at " +
                "FROM purchase_orders po " +
                "LEFT JOIN suppliers s ON po.supplier_id = s.id " +
                "WHERE po.id = ?", new String[]{String.valueOf(orderId)});

        if (!cPo.moveToFirst()) {
            cPo.close();
            Toast.makeText(this, "تعذر العثور على طلب الشراء", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String orderNum = cPo.getString(0);
        supplierId = cPo.isNull(1) ? 0 : cPo.getInt(1);
        supplierName = cPo.isNull(2) ? "مورد نقدي" : cPo.getString(2);
        orderTotal = cPo.getDouble(3);
        currentStatus = cPo.getString(4);
        String notes = cPo.getString(5);
        String date = cPo.getString(6);
        cPo.close();

        tvTitle.setText("📝 " + orderNum);
        tvDate.setText(date != null ? date : "");
        tvSupplier.setText(supplierName);
        tvNotes.setText("الملاحظات: " + (notes != null && !notes.isEmpty() ? notes : "لا توجد ملاحظات"));
        tvTotal.setText(String.format(Locale.US, "%.2f ر.ي", orderTotal));

        tvStatusBadge.setText(getStatusArabic(currentStatus));
        updateStatusBadgeColor(currentStatus);

        // Load Items
        Cursor cItems = db.rawQuery(
                "SELECT poi.product_id, p.name, poi.quantity, poi.unit_price, poi.total, p.unit_name " +
                "FROM purchase_order_items poi " +
                "JOIN products p ON poi.product_id = p.id " +
                "WHERE poi.order_id = ? " +
                "ORDER BY poi.id ASC", new String[]{String.valueOf(orderId)});

        while (cItems.moveToNext()) {
            OrderItemData item = new OrderItemData();
            item.productId = cItems.getInt(0);
            item.productName = cItems.getString(1);
            item.quantity = cItems.getDouble(2);
            item.unitPrice = cItems.getDouble(3);
            item.total = cItems.getDouble(4);
            item.unitName = cItems.isNull(5) ? "حبة" : cItems.getString(5);
            orderItems.add(item);

            View row = LayoutInflater.from(this).inflate(R.layout.item_purchase_detail_row, layoutItems, false);
            TextView tvName = row.findViewById(R.id.tvDetailProductName);
            TextView tvSub = row.findViewById(R.id.tvDetailProductSub);
            TextView tvLineTotal = row.findViewById(R.id.tvDetailItemTotal);

            tvName.setText(item.productName);
            tvSub.setText(String.format(Locale.US, "الكمية المطلوبة: %.2f %s × %.2f ر.ي", item.quantity, item.unitName, item.unitPrice));
            tvLineTotal.setText(String.format(Locale.US, "%.2f ر.ي", item.total));

            layoutItems.addView(row);
        }
        cItems.close();

        tvItemsHeader.setText("أصناف الطلب (" + orderItems.size() + ")");

        // Action Buttons Visibility
        if (PurchaseOrder.STATUS_RECEIVED.equalsIgnoreCase(currentStatus) ||
            PurchaseOrder.STATUS_CANCELLED.equalsIgnoreCase(currentStatus)) {
            layoutActions.setVisibility(View.GONE);
        } else if (PurchaseOrder.STATUS_REQUESTED.equalsIgnoreCase(currentStatus)) {
            layoutActions.setVisibility(View.VISIBLE);
            btnMarkRequested.setVisibility(View.GONE);
            btnReceive.setVisibility(View.VISIBLE);
            btnCancel.setVisibility(View.VISIBLE);
        } else { // DRAFT
            layoutActions.setVisibility(View.VISIBLE);
            btnMarkRequested.setVisibility(View.VISIBLE);
            btnReceive.setVisibility(View.VISIBLE);
            btnCancel.setVisibility(View.VISIBLE);
        }
    }

    private void updateOrderStatus(String newStatus) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("status", newStatus);
        int rows = db.update("purchase_orders", cv, "id = ?", new String[]{String.valueOf(orderId)});
        if (rows > 0) {
            Toast.makeText(this, "تم تغيير حالة الطلب إلى: " + getStatusArabic(newStatus), Toast.LENGTH_SHORT).show();
            loadOrderData();
        }
    }

    private void showReceiveOrderDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_simple, null);
        EditText etPaid = dialogView.findViewById(R.id.etSimpleName);
        EditText etNotes = dialogView.findViewById(R.id.etSimplePhone);

        etPaid.setHint("المبلغ المدفوع (الافتراضي: " + String.format(Locale.US, "%.2f", orderTotal) + ")");
        etPaid.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etPaid.setText(String.format(Locale.US, "%.2f", orderTotal));

        etNotes.setHint("طريقة الدفع (كاش أو شبكة)");
        etNotes.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        etNotes.setText("كاش");

        new MaterialAlertDialogBuilder(this)
                .setTitle("استلام الطلب وتحويله إلى فاتورة شراء")
                .setMessage("سيتم إضافة أصناف الطلب إلى المخزن وتحديث مستحقات المورد فوراً.")
                .setView(dialogView)
                .setPositiveButton("اعتماد والاستلام", (d, which) -> {
                    double paid = orderTotal;
                    try {
                        String pStr = etPaid.getText().toString().trim();
                        if (!pStr.isEmpty()) paid = Double.parseDouble(pStr);
                    } catch (Exception ignored) {}
                    String method = etNotes.getText().toString().trim();
                    if (method.isEmpty()) method = "كاش";

                    convertPoToPurchaseInvoice(paid, method);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void convertPoToPurchaseInvoice(double paid, String paymentMethod) {
        if (orderItems.isEmpty()) {
            Toast.makeText(this, "لا توجد أصناف في الطلب للاستلام", Toast.LENGTH_SHORT).show();
            return;
        }

        paid = Math.max(0.0, Math.min(paid, orderTotal));
        double remaining = Math.max(0.0, orderTotal - paid);

        int userId = SessionManager.getInstance(this).getUserId();
        int defaultWarehouseId = dbHelper.getDefaultWarehouseId();

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // 1. Create purchases invoice record
            ContentValues cvP = new ContentValues();
            if (supplierId > 0) cvP.put("supplier_id", supplierId); else cvP.putNull("supplier_id");
            cvP.put("supplier_name", supplierName);
            cvP.put("total", orderTotal);
            cvP.put("paid", paid);
            cvP.put("remaining", remaining);
            cvP.put("payment_method", paymentMethod);
            if (userId > 0) cvP.put("created_by_user_id", userId);
            long purchaseId = db.insertOrThrow("purchases", null, cvP);

            // 2. Add purchase items and update warehouse stock
            for (OrderItemData item : orderItems) {
                ContentValues cvItem = new ContentValues();
                cvItem.put("purchase_id", purchaseId);
                cvItem.put("product_id", item.productId);
                cvItem.put("quantity", item.quantity);
                cvItem.put("unit_price", item.unitPrice);
                cvItem.put("total", item.total);
                db.insertOrThrow("purchase_items", null, cvItem);

                // Update warehouse stock & log stock movements
                dbHelper.adjustWarehouseStock(db, defaultWarehouseId, item.productId, item.quantity,
                        StockMovement.PURCHASE, (int) purchaseId, userId, "استلام أمر شراء #" + orderId);

                // Update purchase price on product
                ContentValues pu = new ContentValues();
                pu.put("purchase_price", item.unitPrice);
                db.update("products", pu, "id = ?", new String[]{String.valueOf(item.productId)});
            }

            // 3. Update supplier balance & ledger
            if (supplierId > 0) {
                dbHelper.recordSupplierTransaction(db, supplierId, DatabaseHelper.TX_PURCHASE, purchaseId,
                        orderTotal, "فاتورة شراء #" + purchaseId + " (أمر شراء #" + orderId + ")");
                if (paid > 0.001) {
                    dbHelper.recordSupplierTransaction(db, supplierId, DatabaseHelper.TX_PURCHASE_PAYMENT, purchaseId,
                            -paid, "مدفوع عند استلام أمر شراء #" + orderId);
                }
                dbHelper.syncSupplierBalance(db, supplierId);
            }

            // 4. Mark Purchase Order as RECEIVED
            ContentValues cvPo = new ContentValues();
            cvPo.put("status", PurchaseOrder.STATUS_RECEIVED);
            db.update("purchase_orders", cvPo, "id = ?", new String[]{String.valueOf(orderId)});

            db.setTransactionSuccessful();
            Toast.makeText(this, "✅ تم استلام أمر الشراء بنجاح وإنشاء فاتورة شراء #" + purchaseId, Toast.LENGTH_LONG).show();
            loadOrderData();
        } catch (Exception e) {
            Toast.makeText(this, "خطأ أثناء تحويل أمر الشراء: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            db.endTransaction();
        }
    }

    private String getStatusArabic(String status) {
        if (status == null) return "مسودة";
        switch (status) {
            case PurchaseOrder.STATUS_DRAFT: return "مسودة";
            case PurchaseOrder.STATUS_REQUESTED: return "تم الطلب";
            case PurchaseOrder.STATUS_RECEIVED: return "مستلم (تم التحويل لفاتورة)";
            case PurchaseOrder.STATUS_CANCELLED: return "ملغي";
            default: return status;
        }
    }

    private void updateStatusBadgeColor(String status) {
        if (status == null) status = PurchaseOrder.STATUS_DRAFT;
        switch (status) {
            case PurchaseOrder.STATUS_RECEIVED:
                tvStatusBadge.setTextColor(getResources().getColor(R.color.success, null));
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_good_stock);
                break;
            case PurchaseOrder.STATUS_CANCELLED:
                tvStatusBadge.setTextColor(getResources().getColor(R.color.danger, null));
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_out_of_stock);
                break;
            case PurchaseOrder.STATUS_REQUESTED:
                tvStatusBadge.setTextColor(getResources().getColor(R.color.accent, null));
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_low_stock);
                break;
            default:
                tvStatusBadge.setTextColor(getResources().getColor(R.color.warning, null));
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_low_stock);
                break;
        }
    }
}
