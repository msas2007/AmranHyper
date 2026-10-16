package com.lap1.mystore.activities;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Displays the stock of a single warehouse.
 * Reuses item_warehouse_card.xml to show product name, quantity, price,
 * and highlights low-stock items in red.
 */
public class WarehouseStockActivity extends AppCompatActivity {

    private int warehouseId;
    private DatabaseHelper dbHelper;
    private RecyclerView rvStock;
    private TextView tvWarehouseName, tvWarehouseLocation, tvStockItemCount, tvEmptyStock;
    private EditText etSearch;
    // Columns: [name, qty, unit, barcode, min_qty, price]
    private final List<String[]> allItems = new ArrayList<>();
    private final List<String[]> filtered = new ArrayList<>();
    private StockAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warehouse_stock);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        warehouseId = getIntent().getIntExtra("WAREHOUSE_ID", 1);
        String wName     = getIntent().getStringExtra("WAREHOUSE_NAME");
        String wLocation = getIntent().getStringExtra("WAREHOUSE_LOCATION");

        dbHelper = new DatabaseHelper(this);

        rvStock           = findViewById(R.id.rvWarehouseStock);
        tvWarehouseName   = findViewById(R.id.tvWarehouseName);
        tvWarehouseLocation = findViewById(R.id.tvWarehouseLocation);
        tvStockItemCount  = findViewById(R.id.tvStockItemCount);
        tvEmptyStock      = findViewById(R.id.tvEmptyStock);
        etSearch          = findViewById(R.id.etSearchStock);

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        if (tvWarehouseName != null)
            tvWarehouseName.setText(wName != null ? wName : "مخزون المخزن");
        if (tvWarehouseLocation != null)
            tvWarehouseLocation.setText((wLocation != null && !wLocation.isEmpty()) ? wLocation : "—");

        rvStock.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StockAdapter();
        rvStock.setAdapter(adapter);

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                public void afterTextChanged(Editable s) { applyFilter(s.toString()); }
                public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                public void onTextChanged(CharSequence s, int st, int b, int c) {}
            });
        }

        loadStock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStock();
    }

    private void loadStock() {
        allItems.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT p.name, ws.quantity, p.unit_name, p.barcode, p.min_quantity, p.sale_price " +
            "FROM warehouse_stock ws " +
            "JOIN products p ON ws.product_id = p.id " +
            "WHERE ws.warehouse_id = ? AND COALESCE(p.is_archived,0) = 0 " +
            "ORDER BY p.name",
            new String[]{String.valueOf(warehouseId)});
        try {
            while (c.moveToNext()) {
                allItems.add(new String[]{
                    c.getString(0),                                                          // name
                    String.format(Locale.getDefault(), "%.2f", c.getDouble(1)),             // qty
                    c.isNull(2) ? "حبة" : c.getString(2),                                   // unit
                    c.isNull(3) ? "" : c.getString(3),                                      // barcode
                    String.format(Locale.getDefault(), "%.2f", c.getDouble(4)),             // min_qty
                    String.format(Locale.getDefault(), "%.2f ر.ي", c.getDouble(5))         // price
                });
            }
        } finally {
            c.close();
        }
        applyFilter(etSearch != null ? etSearch.getText().toString() : "");
    }

    private void applyFilter(String q) {
        filtered.clear();
        if (q == null || q.trim().isEmpty()) {
            filtered.addAll(allItems);
        } else {
            String lq = q.toLowerCase(Locale.getDefault());
            for (String[] item : allItems) {
                if (item[0].toLowerCase(Locale.getDefault()).contains(lq) || item[3].contains(lq))
                    filtered.add(item);
            }
        }
        if (tvStockItemCount != null) tvStockItemCount.setText(filtered.size() + " صنف");
        boolean empty = filtered.isEmpty();
        if (tvEmptyStock != null) tvEmptyStock.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (rvStock != null)      rvStock.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    // ─────────────────────────────────────────────────────
    // Adapter — reuses item_warehouse_card.xml
    // ─────────────────────────────────────────────────────
    private class StockAdapter extends RecyclerView.Adapter<StockAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_warehouse_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            String[] item  = filtered.get(pos);
            double qty     = Double.parseDouble(item[1]);
            double minQty  = Double.parseDouble(item[4]);

            h.tvName.setText(item[0]);
            h.tvLocation.setText("الكمية: " + item[1] + " " + item[2] + "  •  حد أدنى: " + item[4]);
            h.tvStock.setText("السعر: " + item[5]);
            h.tvStock.setTextColor(qty <= minQty
                ? getResources().getColor(R.color.danger, null)
                : getResources().getColor(R.color.success, null));

            if (h.btnView != null) h.btnView.setVisibility(View.GONE);
        }

        @Override
        public int getItemCount() { return filtered.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvLocation, tvStock;
            View btnView;

            VH(View v) {
                super(v);
                tvName    = v.findViewById(R.id.tvWarehouseItemName);
                tvLocation = v.findViewById(R.id.tvWarehouseItemLocation);
                tvStock   = v.findViewById(R.id.tvWarehouseItemStockCount);
                btnView   = v.findViewById(R.id.btnViewWarehouseStock);
            }
        }
    }
}
