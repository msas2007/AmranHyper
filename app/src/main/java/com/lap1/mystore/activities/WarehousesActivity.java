package com.lap1.mystore.activities;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.lap1.mystore.R;

import java.util.ArrayList;
import java.util.List;

public class WarehousesActivity extends BaseDrawerActivity {

    private RecyclerView rvWarehouses;
    private final List<int[]> warehouseData = new ArrayList<>();    // [id, stockCount]
    private final List<String[]> warehouseStrings = new ArrayList<>(); // [name, location]
    private WarehouseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warehouses);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        setupDrawer("inventory", "المخازن والمستودعات");

        rvWarehouses = findViewById(R.id.rvWarehouses);
        rvWarehouses.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WarehouseAdapter();
        rvWarehouses.setAdapter(adapter);

        FloatingActionButton fabAdd = findViewById(R.id.fabAddWarehouse);
        if (fabAdd != null) fabAdd.setOnClickListener(v -> showAddEditWarehouseDialog(-1, null, null));

        loadWarehouses();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWarehouses();
    }

    private void loadWarehouses() {
        warehouseData.clear();
        warehouseStrings.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT w.id, w.name, w.location, " +
            "(SELECT COUNT(DISTINCT product_id) FROM warehouse_stock ws WHERE ws.warehouse_id = w.id AND ws.quantity > 0) AS stock_count " +
            "FROM warehouses w WHERE w.is_active = 1 ORDER BY w.id", null);
        try {
            while (c.moveToNext()) {
                warehouseData.add(new int[]{c.getInt(0), c.getInt(3)});
                warehouseStrings.add(new String[]{c.getString(1), c.isNull(2) ? "" : c.getString(2)});
            }
        } finally {
            c.close();
        }
        adapter.notifyDataSetChanged();
    }

    private void showAddEditWarehouseDialog(int whId, String existingName, String existingLocation) {
        boolean isEdit = whId > 0;

        EditText etName = new EditText(this);
        etName.setHint("اسم المخزن");
        if (existingName != null) etName.setText(existingName);

        EditText etLocation = new EditText(this);
        etLocation.setHint("الموقع (اختياري)");
        if (existingLocation != null) etLocation.setText(existingLocation);

        android.widget.LinearLayout ll = new android.widget.LinearLayout(this);
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(48, 16, 48, 0);
        ll.addView(etName);
        ll.addView(etLocation);

        new MaterialAlertDialogBuilder(this)
            .setTitle(isEdit ? "تعديل المخزن" : "إضافة مخزن جديد")
            .setView(ll)
            .setPositiveButton("حفظ", (d, w) -> {
                String name = etName.getText().toString().trim();
                if (TextUtils.isEmpty(name)) {
                    Toast.makeText(this, "أدخل اسم المخزن", Toast.LENGTH_SHORT).show();
                    return;
                }
                String location = etLocation.getText().toString().trim();
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put("name", name);
                cv.put("location", location);
                if (isEdit) {
                    db.update("warehouses", cv, "id = ?", new String[]{String.valueOf(whId)});
                    Toast.makeText(this, "تم تحديث المخزن", Toast.LENGTH_SHORT).show();
                } else {
                    cv.put("is_active", 1);
                    db.insert("warehouses", null, cv);
                    Toast.makeText(this, "تم إضافة المخزن بنجاح", Toast.LENGTH_SHORT).show();
                }
                loadWarehouses();
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    // ─────────────────────────────────────────────────────
    // Adapter
    // ─────────────────────────────────────────────────────
    private class WarehouseAdapter extends RecyclerView.Adapter<WarehouseAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_warehouse_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            int whId       = warehouseData.get(position)[0];
            int stockCount = warehouseData.get(position)[1];
            String name    = warehouseStrings.get(position)[0];
            String location = warehouseStrings.get(position)[1];

            String displayName = name;
            if (whId == 1 && !displayName.contains("المركزي")) {
                displayName += " ⭐ (المخزن المركزي الرئيسي)";
            }

            holder.tvName.setText(displayName);
            holder.tvLocation.setText(TextUtils.isEmpty(location) ? (whId == 1 ? "المركز الرئيسي للمتجر" : "بدون موقع محدد") : location);
            holder.tvStockCount.setText(stockCount + " صنف متوفر بالمخزن");

            View.OnClickListener openStock = v -> {
                Intent i = new Intent(WarehousesActivity.this, WarehouseStockActivity.class);
                i.putExtra("WAREHOUSE_ID", whId);
                i.putExtra("WAREHOUSE_NAME", name);
                i.putExtra("WAREHOUSE_LOCATION", location);
                startActivity(i);
            };

            holder.itemView.setOnClickListener(openStock);
            if (holder.btnView != null) holder.btnView.setOnClickListener(openStock);

            holder.tvName.setOnLongClickListener(v -> {
                showAddEditWarehouseDialog(whId, name, location);
                return true;
            });
        }

        @Override
        public int getItemCount() { return warehouseData.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvLocation, tvStockCount;
            View btnView;

            VH(View v) {
                super(v);
                tvName       = v.findViewById(R.id.tvWarehouseItemName);
                tvLocation   = v.findViewById(R.id.tvWarehouseItemLocation);
                tvStockCount = v.findViewById(R.id.tvWarehouseItemStockCount);
                btnView      = v.findViewById(R.id.btnViewWarehouseStock);
            }
        }
    }
}
