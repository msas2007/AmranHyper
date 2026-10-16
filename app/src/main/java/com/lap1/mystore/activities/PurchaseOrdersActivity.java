package com.lap1.mystore.activities;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.models.PurchaseOrder;
import com.lap1.mystore.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PurchaseOrdersActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private RecyclerView rvOrders;
    private Spinner spStatusFilter;
    private TextView tvCount, tvEmpty;
    private final List<PurchaseOrder> allOrders = new ArrayList<>();
    private final List<PurchaseOrder> filteredOrders = new ArrayList<>();
    private PurchaseOrderAdapter adapter;

    // Cache products & suppliers for dialog
    private final List<Integer> supplierIds = new ArrayList<>();
    private final List<String> supplierNames = new ArrayList<>();
    private final List<Integer> productIds = new ArrayList<>();
    private final List<String> productNames = new ArrayList<>();
    private final List<Double> productCosts = new ArrayList<>();

    private final String[] filterStatuses = {"الكل", PurchaseOrder.STATUS_DRAFT, PurchaseOrder.STATUS_REQUESTED, PurchaseOrder.STATUS_RECEIVED, PurchaseOrder.STATUS_CANCELLED};
    private final String[] filterDisplay = {"جميع الحالات", "مسودة", "تم الطلب", "مستلم", "ملغي"};
    private String selectedStatus = "الكل";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_orders);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        dbHelper = new DatabaseHelper(this);

        rvOrders = findViewById(R.id.rvPurchaseOrders);
        spStatusFilter = findViewById(R.id.spPoStatusFilter);
        tvCount = findViewById(R.id.tvPoCount);
        tvEmpty = findViewById(R.id.tvEmptyPo);
        FloatingActionButton fabAdd = findViewById(R.id.fabCreatePo);
        ImageButton btnBack = findViewById(R.id.btnPurchaseOrdersBack);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (fabAdd != null) fabAdd.setOnClickListener(v -> showCreateOrderDialog());

        rvOrders.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PurchaseOrderAdapter();
        rvOrders.setAdapter(adapter);

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, filterDisplay);
        spStatusFilter.setAdapter(statusAdapter);
        spStatusFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedStatus = filterStatuses[pos];
                applyFilter();
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });

        loadCacheData();
        loadOrders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCacheData();
        loadOrders();
    }

    private void loadCacheData() {
        supplierIds.clear(); supplierNames.clear();
        productIds.clear(); productNames.clear(); productCosts.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cs = db.rawQuery("SELECT id, name FROM suppliers WHERE COALESCE(is_archived,0)=0 ORDER BY name", null);
        while (cs.moveToNext()) {
            supplierIds.add(cs.getInt(0));
            supplierNames.add(cs.getString(1));
        }
        cs.close();

        Cursor cp = db.rawQuery("SELECT id, name, COALESCE(purchase_price,0) FROM products WHERE COALESCE(is_archived,0)=0 ORDER BY name", null);
        while (cp.moveToNext()) {
            productIds.add(cp.getInt(0));
            productNames.add(cp.getString(1));
            productCosts.add(cp.getDouble(2));
        }
        cp.close();
    }

    private void loadOrders() {
        allOrders.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT po.id, po.order_number, po.supplier_id, s.name, po.total, po.status, po.notes, " +
                "po.created_by_user_id, po.created_at, " +
                "(SELECT COUNT(*) FROM purchase_order_items poi WHERE poi.order_id = po.id) as item_count " +
                "FROM purchase_orders po " +
                "LEFT JOIN suppliers s ON po.supplier_id = s.id " +
                "ORDER BY po.id DESC", null);
        try {
            while (c.moveToNext()) {
                PurchaseOrder po = new PurchaseOrder(
                        c.getInt(0),
                        c.getString(1),
                        c.isNull(2) ? 0 : c.getInt(2),
                        c.isNull(3) ? "مورد نقدي" : c.getString(3),
                        c.getDouble(4),
                        c.getString(5),
                        c.getString(6),
                        c.isNull(7) ? 0 : c.getInt(7),
                        c.getString(8)
                );
                po.setItemsCount(c.getInt(9));
                allOrders.add(po);
            }
        } finally {
            c.close();
        }
        applyFilter();
    }

    private void applyFilter() {
        filteredOrders.clear();
        for (PurchaseOrder po : allOrders) {
            if ("الكل".equals(selectedStatus) || selectedStatus.equalsIgnoreCase(po.getStatus())) {
                filteredOrders.add(po);
            }
        }

        if (tvCount != null) tvCount.setText(filteredOrders.size() + " طلب شراء");
        if (tvEmpty != null) tvEmpty.setVisibility(filteredOrders.isEmpty() ? View.VISIBLE : View.GONE);
        if (rvOrders != null) rvOrders.setVisibility(filteredOrders.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void showCreateOrderDialog() {
        if (productNames.isEmpty()) {
            Toast.makeText(this, "يرجى إضافة منتجات في النظام أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_purchase_order, null);
        Spinner spSupplier = dialogView.findViewById(R.id.spDialogPoSupplier);
        LinearLayout layoutItems = dialogView.findViewById(R.id.layoutDialogPoItems);
        android.widget.Button btnAddItem = dialogView.findViewById(R.id.btnDialogPoAddItem);
        EditText etNotes = dialogView.findViewById(R.id.etDialogPoNotes);

        if (supplierNames.isEmpty()) {
            supplierNames.add("مورد عام نقدي");
            supplierIds.add(0);
        }
        ArrayAdapter<String> sAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, supplierNames);
        spSupplier.setAdapter(sAdapter);

        final List<View> itemRows = new ArrayList<>();
        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_po_create_row, layoutItems, false);
            Spinner spProd = row.findViewById(R.id.spPoRowProduct);
            EditText etQty = row.findViewById(R.id.etPoRowQty);
            EditText etPrice = row.findViewById(R.id.etPoRowPrice);
            ImageButton btnRemove = row.findViewById(R.id.btnPoRowRemove);

            spProd.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, productNames));
            spProd.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    if (pos < productCosts.size()) {
                        etPrice.setText(String.format(Locale.US, "%.2f", productCosts.get(pos)));
                    }
                }
                public void onNothingSelected(AdapterView<?> p) {}
            });

            btnRemove.setOnClickListener(v -> {
                layoutItems.removeView(row);
                itemRows.remove(row);
            });

            layoutItems.addView(row);
            itemRows.add(row);
        };

        btnAddItem.setOnClickListener(v -> addRow.run());
        addRow.run(); // add initial row

        new MaterialAlertDialogBuilder(this)
                .setTitle("إنشاء طلب شراء جديد")
                .setView(dialogView)
                .setPositiveButton("حفظ كمسودة", (d, which) -> savePurchaseOrder(spSupplier, itemRows, etNotes, PurchaseOrder.STATUS_DRAFT))
                .setNeutralButton("إرسال الطلب", (d, which) -> savePurchaseOrder(spSupplier, itemRows, etNotes, PurchaseOrder.STATUS_REQUESTED))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void savePurchaseOrder(Spinner spSupplier, List<View> itemRows, EditText etNotes, String status) {
        if (itemRows.isEmpty()) {
            Toast.makeText(this, "أضف صنفاً واحداً على الأقل", Toast.LENGTH_SHORT).show();
            return;
        }

        int supIdx = spSupplier.getSelectedItemPosition();
        int supplierId = (supIdx >= 0 && supIdx < supplierIds.size()) ? supplierIds.get(supIdx) : 0;

        List<double[]> parsedItems = new ArrayList<>(); // [productId, qty, unitPrice, total]
        double orderTotal = 0.0;

        for (View row : itemRows) {
            Spinner sp = row.findViewById(R.id.spPoRowProduct);
            EditText etQ = row.findViewById(R.id.etPoRowQty);
            EditText etP = row.findViewById(R.id.etPoRowPrice);

            int pIdx = sp.getSelectedItemPosition();
            if (pIdx < 0 || pIdx >= productIds.size()) continue;
            int pid = productIds.get(pIdx);

            String qStr = etQ.getText().toString().trim();
            String pStr = etP.getText().toString().trim();
            if (qStr.isEmpty() || pStr.isEmpty()) continue;

            try {
                double qty = Double.parseDouble(qStr);
                double price = Double.parseDouble(pStr);
                if (qty <= 0 || price < 0) continue;
                double lineTotal = qty * price;
                orderTotal += lineTotal;
                parsedItems.add(new double[]{pid, qty, price, lineTotal});
            } catch (NumberFormatException ignored) {}
        }

        if (parsedItems.isEmpty()) {
            Toast.makeText(this, "يرجى إدخال كميات وأسعار صحيحة للأصناف", Toast.LENGTH_SHORT).show();
            return;
        }

        String notes = etNotes != null ? etNotes.getText().toString().trim() : "";
        int userId = SessionManager.getInstance(this).getUserId();

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            long nextId = 1;
            Cursor cid = db.rawQuery("SELECT COALESCE(MAX(id),0) + 1 FROM purchase_orders", null);
            if (cid.moveToFirst()) nextId = cid.getLong(0);
            cid.close();

            String orderNumber = "PO-" + String.format(Locale.US, "%04d", nextId);

            ContentValues cv = new ContentValues();
            cv.put("order_number", orderNumber);
            if (supplierId > 0) cv.put("supplier_id", supplierId); else cv.putNull("supplier_id");
            cv.put("total", orderTotal);
            cv.put("status", status);
            cv.put("notes", notes);
            if (userId > 0) cv.put("created_by_user_id", userId);
            long orderId = db.insertOrThrow("purchase_orders", null, cv);

            for (double[] item : parsedItems) {
                ContentValues cvi = new ContentValues();
                cvi.put("order_id", orderId);
                cvi.put("product_id", (int) item[0]);
                cvi.put("quantity", item[1]);
                cvi.put("unit_price", item[2]);
                cvi.put("total", item[3]);
                db.insertOrThrow("purchase_order_items", null, cvi);
            }

            db.setTransactionSuccessful();
            Toast.makeText(this, "✅ تم حفظ طلب الشراء " + orderNumber, Toast.LENGTH_LONG).show();
            loadOrders();
        } catch (Exception e) {
            Toast.makeText(this, "خطأ أثناء حفظ طلب الشراء: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            db.endTransaction();
        }
    }

    private class PurchaseOrderAdapter extends RecyclerView.Adapter<PurchaseOrderAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_purchase_order, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            PurchaseOrder po = filteredOrders.get(position);
            h.tvNumber.setText("📝 " + po.getOrderNumber());
            h.tvSupplier.setText(po.getSupplierName());
            h.tvDate.setText(po.getCreatedAt() != null ? po.getCreatedAt() : "");
            h.tvItems.setText(po.getItemsCount() + " أصناف");
            h.tvTotal.setText(String.format(Locale.US, "%.2f ر.ي", po.getTotal()));

            h.tvBadge.setText(po.getStatusArabic());
            switch (po.getStatus()) {
                case PurchaseOrder.STATUS_RECEIVED:
                    h.tvBadge.setTextColor(getResources().getColor(R.color.success, null));
                    h.tvBadge.setBackgroundResource(R.drawable.bg_badge_good_stock);
                    break;
                case PurchaseOrder.STATUS_CANCELLED:
                    h.tvBadge.setTextColor(getResources().getColor(R.color.danger, null));
                    h.tvBadge.setBackgroundResource(R.drawable.bg_badge_out_of_stock);
                    break;
                case PurchaseOrder.STATUS_REQUESTED:
                    h.tvBadge.setTextColor(getResources().getColor(R.color.accent, null));
                    h.tvBadge.setBackgroundResource(R.drawable.bg_badge_low_stock);
                    break;
                default: // DRAFT
                    h.tvBadge.setTextColor(getResources().getColor(R.color.warning, null));
                    h.tvBadge.setBackgroundResource(R.drawable.bg_badge_low_stock);
                    break;
            }

            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(PurchaseOrdersActivity.this, PurchaseOrderDetailActivity.class);
                intent.putExtra("PO_ID", po.getId());
                startActivity(intent);
            });
        }

        @Override public int getItemCount() { return filteredOrders.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvNumber, tvSupplier, tvDate, tvItems, tvTotal, tvBadge;
            VH(View v) {
                super(v);
                tvNumber = v.findViewById(R.id.tvPoNumber);
                tvSupplier = v.findViewById(R.id.tvPoSupplier);
                tvDate = v.findViewById(R.id.tvPoDate);
                tvItems = v.findViewById(R.id.tvPoItemsCount);
                tvTotal = v.findViewById(R.id.tvPoTotal);
                tvBadge = v.findViewById(R.id.tvPoStatusBadge);
            }
        }
    }
}
