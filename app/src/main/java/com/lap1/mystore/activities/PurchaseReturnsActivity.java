package com.lap1.mystore.activities;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.utils.SessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PurchaseReturnsActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;

    // Navigation Tabs
    private Button btnTabLog, btnTabNew;
    private LinearLayout layoutLogContainer, layoutNewContainer;

    // Log Tab Elements
    private EditText etSearchLog;
    private ListView lvLog;
    private TextView tvEmptyLog;
    private ArrayList<PurchaseReturnLogModel> logList;
    private PurchaseReturnLogAdapter logAdapter;

    // New Return Tab Elements
    private Spinner spInvoices, spRefundMethod;
    private LinearLayout layoutInvoiceInfo, layoutItemsContainer;
    private TextView tvSupplierInfo, tvInvoiceFinancials, tvEmptyItems, tvTotalAmount;
    private EditText etReason;
    private Button btnCommit;

    private final List<Integer> invoiceIds = new ArrayList<>();
    private final List<String> invoiceLabels = new ArrayList<>();
    private final List<Integer> invoiceSupplierIds = new ArrayList<>();
    private final List<String> invoiceSupplierNames = new ArrayList<>();
    private final List<Double> invoiceTotals = new ArrayList<>();
    private final List<Double> invoiceRemainings = new ArrayList<>();

    private int selectedInvoiceId = 0;
    private int selectedSupplierId = 0;
    private String selectedSupplierName = "";
    private double selectedInvoiceTotal = 0.0;
    private double selectedInvoiceRemaining = 0.0;

    // Items list for current invoice
    private static class PurchaseReturnRowItem {
        int productId;
        String productName;
        double purchasedQty;
        double alreadyReturnedQty;
        double returnableQty;
        double unitPrice;
        String unitName;
        double returnQty = 0.0;
    }

    public static class PurchaseReturnLogModel {
        long id;
        long purchaseId;
        int supplierId;
        String supplierName;
        double totalAmount;
        String refundMethod;
        String reason;
        String returnDate;

        PurchaseReturnLogModel(long id, long purchaseId, int supplierId, String supplierName,
                               double totalAmount, String refundMethod, String reason, String returnDate) {
            this.id = id;
            this.purchaseId = purchaseId;
            this.supplierId = supplierId;
            this.supplierName = supplierName;
            this.totalAmount = totalAmount;
            this.refundMethod = refundMethod;
            this.reason = reason;
            this.returnDate = returnDate;
        }
    }

    private final List<PurchaseReturnRowItem> currentInvoiceItems = new ArrayList<>();
    private final List<View> rowViews = new ArrayList<>();
    private double totalReturnAmount = 0.0;

    private final String[] refundMethods = {"خصم من حساب المورد (تخفيض المديونية)", "استرداد نقدي (دخول للخزينة)"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_returns);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        dbHelper = new DatabaseHelper(this);

        ImageButton btnBack = findViewById(R.id.btnPurchaseReturnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Bind Tabs & Containers
        btnTabLog = findViewById(R.id.btnTabPurchaseReturnsLog);
        btnTabNew = findViewById(R.id.btnTabNewPurchaseReturn);
        layoutLogContainer = findViewById(R.id.layoutPurchaseReturnsLogContainer);
        layoutNewContainer = findViewById(R.id.layoutNewPurchaseReturnContainer);

        // Bind Log Tab views
        etSearchLog = findViewById(R.id.etSearchPurchaseReturnsLog);
        lvLog = findViewById(R.id.lvPurchaseReturnsLog);
        tvEmptyLog = findViewById(R.id.tvEmptyPurchaseReturnsLog);
        logList = new ArrayList<>();

        // Bind New Return Tab views
        spInvoices = findViewById(R.id.spPurchaseInvoiceToReturn);
        spRefundMethod = findViewById(R.id.spRefundMethod);
        layoutInvoiceInfo = findViewById(R.id.layoutSelectedInvoiceInfo);
        layoutItemsContainer = findViewById(R.id.layoutReturnItemsContainer);
        tvSupplierInfo = findViewById(R.id.tvReturnSupplierInfo);
        tvInvoiceFinancials = findViewById(R.id.tvReturnInvoiceFinancials);
        tvEmptyItems = findViewById(R.id.tvEmptyReturnItems);
        tvTotalAmount = findViewById(R.id.tvTotalReturnAmount);
        etReason = findViewById(R.id.etReturnReason);
        btnCommit = findViewById(R.id.btnCommitPurchaseReturn);

        ArrayAdapter<String> refundAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, refundMethods);
        spRefundMethod.setAdapter(refundAdapter);

        // Setup Tab Actions
        btnTabLog.setOnClickListener(v -> switchToLogTab());
        btnTabNew.setOnClickListener(v -> switchToNewReturnTab());

        loadReturnsLog("");
        loadInvoices();

        etSearchLog.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadReturnsLog(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        lvLog.setOnItemClickListener((parent, view, position, id) -> {
            PurchaseReturnLogModel model = logList.get(position);
            showReturnDetailsDialog(model);
        });

        spInvoices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < invoiceIds.size()) {
                    selectInvoice(pos);
                }
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });

        btnCommit.setOnClickListener(v -> confirmAndCommitReturn());

        // Check if preselected invoice was passed from PurchaseInvoiceDetailActivity
        int preselected = getIntent().getIntExtra("PRESELECTED_PURCHASE_ID", -1);
        if (preselected > 0) {
            switchToNewReturnTab();
            int foundIdx = invoiceIds.indexOf(preselected);
            if (foundIdx >= 0) {
                spInvoices.setSelection(foundIdx);
            }
        } else {
            switchToLogTab();
        }
    }

    private void switchToLogTab() {
        btnTabLog.setBackgroundTintList(getColorStateList(R.color.primary));
        btnTabLog.setTextColor(getResources().getColor(R.color.white));
        btnTabNew.setBackgroundTintList(getColorStateList(R.color.bg_card_secondary));
        btnTabNew.setTextColor(getResources().getColor(R.color.text_primary));

        layoutLogContainer.setVisibility(View.VISIBLE);
        layoutNewContainer.setVisibility(View.GONE);
        loadReturnsLog(etSearchLog != null ? etSearchLog.getText().toString().trim() : "");
    }

    private void switchToNewReturnTab() {
        btnTabNew.setBackgroundTintList(getColorStateList(R.color.primary));
        btnTabNew.setTextColor(getResources().getColor(R.color.white));
        btnTabLog.setBackgroundTintList(getColorStateList(R.color.bg_card_secondary));
        btnTabLog.setTextColor(getResources().getColor(R.color.text_primary));

        layoutNewContainer.setVisibility(View.VISIBLE);
        layoutLogContainer.setVisibility(View.GONE);
    }

    private void loadReturnsLog(String keyword) {
        logList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql = "SELECT pr.id, pr.purchase_id, pr.supplier_id, COALESCE(s.name, 'مورد نقدي') as supplier_name, " +
                "pr.total_amount, pr.refund_method, pr.reason, pr.return_date " +
                "FROM purchase_returns pr " +
                "LEFT JOIN suppliers s ON pr.supplier_id = s.id ";

        Cursor c;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String q = "%" + keyword.trim() + "%";
            sql += "WHERE (s.name LIKE ? OR CAST(pr.id AS TEXT) LIKE ? OR CAST(pr.purchase_id AS TEXT) LIKE ?) ORDER BY pr.id DESC";
            c = db.rawQuery(sql, new String[]{q, q, q});
        } else {
            sql += "ORDER BY pr.id DESC";
            c = db.rawQuery(sql, null);
        }

        try {
            while (c.moveToNext()) {
                logList.add(new PurchaseReturnLogModel(
                        c.getLong(0),
                        c.isNull(1) ? 0 : c.getLong(1),
                        c.isNull(2) ? 0 : c.getInt(2),
                        c.getString(3),
                        c.getDouble(4),
                        c.isNull(5) ? "نقدي" : c.getString(5),
                        c.getString(6),
                        c.getString(7)
                ));
            }
        } finally {
            c.close();
        }

        if (logList.isEmpty()) {
            tvEmptyLog.setVisibility(View.VISIBLE);
            lvLog.setVisibility(View.GONE);
        } else {
            tvEmptyLog.setVisibility(View.GONE);
            lvLog.setVisibility(View.VISIBLE);
        }

        logAdapter = new PurchaseReturnLogAdapter(this, logList);
        lvLog.setAdapter(logAdapter);
    }

    private void showReturnDetailsDialog(PurchaseReturnLogModel model) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cItems = db.rawQuery(
                "SELECT p.name, pri.quantity, pri.unit_price, pri.total, p.unit_name " +
                        "FROM purchase_return_items pri " +
                        "JOIN products p ON pri.product_id = p.id " +
                        "WHERE pri.return_id = ? ORDER BY pri.id ASC",
                new String[]{String.valueOf(model.id)});

        StringBuilder itemsSb = new StringBuilder();
        int count = 1;
        while (cItems.moveToNext()) {
            String pName = cItems.getString(0);
            double qty = cItems.getDouble(1);
            double price = cItems.getDouble(2);
            double total = cItems.getDouble(3);
            String unit = cItems.isNull(4) ? "حبة" : cItems.getString(4);

            itemsSb.append(String.format(Locale.US, "%d. %s\n   الكمية: %.2f %s | السعر: %.2f ر.ي | الإجمالي: %.2f ر.ي\n",
                    count++, pName, qty, unit, price, total));
        }
        cItems.close();

        if (itemsSb.length() == 0) itemsSb.append("لا توجد تفاصيل أصناف محفوظة لهذا المرتجع.");

        String message = String.format(Locale.US,
                "مرتجع مشتريات رقم: #%d\n" +
                        "التاريخ: %s\n" +
                        "المورد: %s\n" +
                        "فاتورة الشراء الأصلية: %s\n" +
                        "طريقة التسوية: %s\n" +
                        "إجمالي المرتجع: %.2f ر.ي\n" +
                        (model.reason != null && !model.reason.isEmpty() ? "السبب: " + model.reason + "\n" : "") +
                        "----------------------------------------\n" +
                        "الأصناف المرجعة:\n%s",
                model.id,
                model.returnDate != null ? model.returnDate : "",
                model.supplierName,
                model.purchaseId > 0 ? "#" + model.purchaseId : "غير محددة",
                model.refundMethod,
                model.totalAmount,
                itemsSb.toString());

        new MaterialAlertDialogBuilder(this)
                .setTitle("📄 تفاصيل مرتجع المشتريات #" + model.id)
                .setMessage(message)
                .setPositiveButton("إغلاق", null)
                .show();
    }

    private void loadInvoices() {
        invoiceIds.clear();
        invoiceLabels.clear();
        invoiceSupplierIds.clear();
        invoiceSupplierNames.clear();
        invoiceTotals.clear();
        invoiceRemainings.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id, supplier_id, supplier_name, total, remaining, purchase_date " +
                        "FROM purchases ORDER BY id DESC", null);
        try {
            while (c.moveToNext()) {
                int id = c.getInt(0);
                int supId = c.isNull(1) ? 0 : c.getInt(1);
                String supName = c.isNull(2) ? "مورد نقدي" : c.getString(2);
                double tot = c.getDouble(3);
                double rem = c.getDouble(4);

                invoiceIds.add(id);
                invoiceSupplierIds.add(supId);
                invoiceSupplierNames.add(supName);
                invoiceTotals.add(tot);
                invoiceRemainings.add(rem);
                invoiceLabels.add("فاتورة #" + id + " - " + supName + " (" + String.format(Locale.US, "%.2f ر.ي", tot) + ")");
            }
        } finally {
            c.close();
        }

        if (invoiceLabels.isEmpty()) {
            invoiceLabels.add("لا توجد فواتير شراء مسجلة");
            invoiceIds.add(0);
            invoiceSupplierIds.add(0);
            invoiceSupplierNames.add("");
            invoiceTotals.add(0.0);
            invoiceRemainings.add(0.0);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, invoiceLabels);
        spInvoices.setAdapter(adapter);
    }

    private void selectInvoice(int pos) {
        selectedInvoiceId = invoiceIds.get(pos);
        if (selectedInvoiceId <= 0) {
            layoutInvoiceInfo.setVisibility(View.GONE);
            layoutItemsContainer.removeAllViews();
            tvEmptyItems.setVisibility(View.VISIBLE);
            return;
        }

        selectedSupplierId = invoiceSupplierIds.get(pos);
        selectedSupplierName = invoiceSupplierNames.get(pos);
        selectedInvoiceTotal = invoiceTotals.get(pos);
        selectedInvoiceRemaining = invoiceRemainings.get(pos);

        layoutInvoiceInfo.setVisibility(View.VISIBLE);
        tvSupplierInfo.setText("المورد: " + selectedSupplierName);
        tvInvoiceFinancials.setText(String.format(Locale.US, "إجمالي الفاتورة: %.2f ر.ي  |  المتبقي بذمتها: %.2f ر.ي", selectedInvoiceTotal, selectedInvoiceRemaining));

        loadInvoiceItems(selectedInvoiceId);
    }

    private void loadInvoiceItems(int purchaseId) {
        currentInvoiceItems.clear();
        layoutItemsContainer.removeAllViews();
        rowViews.clear();
        totalReturnAmount = 0.0;
        updateTotalReturnDisplay();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // Already returned amounts map
        Map<Integer, Double> returnedMap = new HashMap<>();
        Cursor cRet = db.rawQuery(
                "SELECT pri.product_id, SUM(pri.quantity) " +
                        "FROM purchase_return_items pri " +
                        "JOIN purchase_returns pr ON pri.return_id = pr.id " +
                        "WHERE pr.purchase_id = ? " +
                        "GROUP BY pri.product_id", new String[]{String.valueOf(purchaseId)});
        while (cRet.moveToNext()) {
            returnedMap.put(cRet.getInt(0), cRet.getDouble(1));
        }
        cRet.close();

        // Items from purchase
        Cursor cItems = db.rawQuery(
                "SELECT pi.product_id, p.name, pi.quantity, pi.unit_price, p.unit_name " +
                        "FROM purchase_items pi " +
                        "JOIN products p ON pi.product_id = p.id " +
                        "WHERE pi.purchase_id = ? " +
                        "ORDER BY pi.id ASC", new String[]{String.valueOf(purchaseId)});

        while (cItems.moveToNext()) {
            PurchaseReturnRowItem item = new PurchaseReturnRowItem();
            item.productId = cItems.getInt(0);
            item.productName = cItems.getString(1);
            item.purchasedQty = cItems.getDouble(2);
            item.unitPrice = cItems.getDouble(3);
            item.unitName = cItems.isNull(4) ? "حبة" : cItems.getString(4);

            Double alreadyRet = returnedMap.get(item.productId);
            item.alreadyReturnedQty = alreadyRet != null ? alreadyRet : 0.0;
            item.returnableQty = Math.max(0.0, item.purchasedQty - item.alreadyReturnedQty);

            if (item.returnableQty > 0.001) {
                currentInvoiceItems.add(item);
            }
        }
        cItems.close();

        if (currentInvoiceItems.isEmpty()) {
            tvEmptyItems.setText("تم إرجاع جميع أصناف هذه الفاتورة بالكامل مسبقاً");
            tvEmptyItems.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyItems.setVisibility(View.GONE);

        for (int i = 0; i < currentInvoiceItems.size(); i++) {
            final PurchaseReturnRowItem item = currentInvoiceItems.get(i);

            View row = LayoutInflater.from(this).inflate(R.layout.item_purchase_return_row, layoutItemsContainer, false);
            TextView tvName = row.findViewById(R.id.tvPrItemName);
            TextView tvPurchased = row.findViewById(R.id.tvPrItemPurchasedQty);
            TextView tvPrice = row.findViewById(R.id.tvPrItemPrice);
            EditText etQty = row.findViewById(R.id.etPrReturnQty);
            TextView tvLineTotal = row.findViewById(R.id.tvPrLineReturnTotal);

            tvName.setText(item.productName);
            tvPurchased.setText(String.format(Locale.US, "المتاح للإرجاع: %.2f %s", item.returnableQty, item.unitName));
            tvPrice.setText(String.format(Locale.US, "السعر: %.2f ر.ي", item.unitPrice));
            tvLineTotal.setText("0.00 ر.ي");

            etQty.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    double inputQty = 0.0;
                    String qStr = s.toString().trim();
                    if (!qStr.isEmpty()) {
                        try { inputQty = Double.parseDouble(qStr); } catch (Exception ignored) {}
                    }

                    if (inputQty > item.returnableQty + 0.0001) {
                        etQty.setError("الكمية أكبر من المتاح (" + item.returnableQty + ")");
                        inputQty = 0.0;
                    } else if (inputQty < 0) {
                        inputQty = 0.0;
                    }

                    item.returnQty = inputQty;
                    double lineTotal = inputQty * item.unitPrice;
                    tvLineTotal.setText(String.format(Locale.US, "%.2f ر.ي", lineTotal));
                    recalculateTotal();
                }
                @Override public void afterTextChanged(Editable s) {}
            });

            layoutItemsContainer.addView(row);
            rowViews.add(row);
        }
    }

    private void recalculateTotal() {
        totalReturnAmount = 0.0;
        for (PurchaseReturnRowItem item : currentInvoiceItems) {
            totalReturnAmount += (item.returnQty * item.unitPrice);
        }
        updateTotalReturnDisplay();
    }

    private void updateTotalReturnDisplay() {
        if (tvTotalAmount != null) {
            tvTotalAmount.setText(String.format(Locale.US, "%.2f ر.ي", totalReturnAmount));
        }
    }

    private void confirmAndCommitReturn() {
        if (selectedInvoiceId <= 0) {
            Toast.makeText(this, "يرجى اختيار فاتورة شراء صالحة", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<DatabaseHelper.PurchaseReturnItemInput> returnItems = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        int warehouseId = dbHelper.getDefaultWarehouseId();

        for (PurchaseReturnRowItem item : currentInvoiceItems) {
            if (item.returnQty > 0.0001) {
                double currentStock = dbHelper.getWarehouseProductQuantity(db, warehouseId, item.productId);
                if (item.returnQty > currentStock + 0.0001) {
                    Toast.makeText(this, "الكمية المرتجعة من " + item.productName + " (" + item.returnQty + ") أكبر من رصيد المخزن الحالي (" + currentStock + ")", Toast.LENGTH_LONG).show();
                    return;
                }
                returnItems.add(new DatabaseHelper.PurchaseReturnItemInput(
                        item.productId, item.returnQty, item.unitPrice, item.returnQty * item.unitPrice
                ));
            }
        }

        if (returnItems.isEmpty()) {
            Toast.makeText(this, "أدخل كمية إرجاع لصنف واحد على الأقل", Toast.LENGTH_SHORT).show();
            return;
        }

        final String methodSelection = spRefundMethod.getSelectedItemPosition() == 0 ? "خصم من حساب المورد" : "نقدي";
        final double debtReduction = "خصم من حساب المورد".equals(methodSelection) ? totalReturnAmount : 0.0;
        final double cashRefund = "نقدي".equals(methodSelection) ? totalReturnAmount : 0.0;
        final String reason = etReason.getText().toString().trim();

        new MaterialAlertDialogBuilder(this)
                .setTitle("تأكيد اعتماد مرتجع المشتريات")
                .setMessage(String.format(Locale.US,
                        "فاتورة الشراء: #%d\nالمورد: %s\nعدد الأصناف: %d\nإجمالي المرتجع: %.2f ر.ي\nطريقة التسوية: %s\n\nهل أنت متأكد من تنفيذ العملية؟",
                        selectedInvoiceId, selectedSupplierName, returnItems.size(), totalReturnAmount, methodSelection))
                .setPositiveButton("اعتماد نهائي", (d, which) -> {
                    executeReturn(returnItems, methodSelection, debtReduction, cashRefund, reason, warehouseId);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void executeReturn(List<DatabaseHelper.PurchaseReturnItemInput> items, String refundMethod,
                               double debtReduction, double cashRefund, String reason, int warehouseId) {
        int userId = SessionManager.getInstance(this).getUserId();

        long returnId = dbHelper.executePurchaseReturn(
                selectedInvoiceId, selectedSupplierId, totalReturnAmount,
                refundMethod, debtReduction, cashRefund, reason, userId, warehouseId, items);

        if (returnId > 0) {
            Toast.makeText(this, "✅ تم اعتماد مرتجع المشتريات #" + returnId + " وتحديث المخزون والمالية", Toast.LENGTH_LONG).show();
            // Switch back to log tab to view the recorded return
            switchToLogTab();
        } else {
            Toast.makeText(this, "❌ فشل اعتماد المرتجع. تحقق من صحة البيانات.", Toast.LENGTH_LONG).show();
        }
    }

    // Adapter for Log List View
    private static class PurchaseReturnLogAdapter extends BaseAdapter {
        private final Context context;
        private final List<PurchaseReturnLogModel> list;

        PurchaseReturnLogAdapter(Context context, List<PurchaseReturnLogModel> list) {
            this.context = context;
            this.list = list;
        }

        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int position) { return list.get(position); }
        @Override public long getItemId(int position) { return list.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_purchase_return, parent, false);
            }

            TextView tvNumber = convertView.findViewById(R.id.tvLogReturnNumber);
            TextView tvBadge = convertView.findViewById(R.id.tvLogReturnMethodBadge);
            TextView tvSupplier = convertView.findViewById(R.id.tvLogSupplierName);
            TextView tvInvoice = convertView.findViewById(R.id.tvLogPurchaseInvoice);
            TextView tvDate = convertView.findViewById(R.id.tvLogReturnDate);
            TextView tvTotal = convertView.findViewById(R.id.tvLogTotalAmount);
            TextView tvReason = convertView.findViewById(R.id.tvLogReturnReason);

            PurchaseReturnLogModel item = list.get(position);

            tvNumber.setText("مرتجع #" + item.id);
            tvSupplier.setText("🚚 المورد: " + item.supplierName);
            tvInvoice.setText(item.purchaseId > 0 ? "فاتورة رقم #" + item.purchaseId : "غير مرابطة بفاتورة");
            tvDate.setText("📅 " + (item.returnDate != null ? item.returnDate : ""));
            tvTotal.setText(String.format(Locale.US, "%.2f ر.ي", item.totalAmount));

            tvBadge.setText(item.refundMethod != null ? item.refundMethod : "نقدي");
            if ("خصم من حساب المورد".equals(item.refundMethod) || (item.refundMethod != null && item.refundMethod.contains("خصم"))) {
                tvBadge.setTextColor(context.getResources().getColor(R.color.danger));
                tvBadge.setBackgroundResource(R.drawable.bg_badge_out_of_stock);
            } else {
                tvBadge.setTextColor(context.getResources().getColor(R.color.success));
                tvBadge.setBackgroundResource(R.drawable.bg_badge_good_stock);
            }

            if (item.reason != null && !item.reason.trim().isEmpty()) {
                tvReason.setVisibility(View.VISIBLE);
                tvReason.setText("📝 السبب: " + item.reason);
            } else {
                tvReason.setVisibility(View.GONE);
            }

            return convertView;
        }
    }
}
