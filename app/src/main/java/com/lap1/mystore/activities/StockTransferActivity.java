package com.lap1.mystore.activities;

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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class StockTransferActivity extends AppCompatActivity {

    private Spinner spinnerFromWarehouse, spinnerToWarehouse;
    private LinearLayout layoutTransferItems;
    private EditText etTransferNotes;
    private Button btnConfirmTransfer, btnAddTransferItem;
    private DatabaseHelper dbHelper;

    private final List<Integer> warehouseIds = new ArrayList<>();
    private final List<String> warehouseNames = new ArrayList<>();
    private final List<Integer> productIds = new ArrayList<>();
    private final List<String> productNames = new ArrayList<>();
    private final List<View> transferItemViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_transfer);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        dbHelper = new DatabaseHelper(this);

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        spinnerFromWarehouse = findViewById(R.id.spinnerFromWarehouse);
        spinnerToWarehouse   = findViewById(R.id.spinnerToWarehouse);
        layoutTransferItems  = findViewById(R.id.layoutTransferItems);
        etTransferNotes      = findViewById(R.id.etTransferNotes);
        btnConfirmTransfer   = findViewById(R.id.btnConfirmTransfer);
        btnAddTransferItem   = findViewById(R.id.btnAddTransferItem);

        loadWarehouses();
        loadProducts();

        if (btnAddTransferItem != null) btnAddTransferItem.setOnClickListener(v -> addTransferItemRow());
        addTransferItemRow(); // ابدأ بصف واحد افتراضي

        if (btnConfirmTransfer != null) btnConfirmTransfer.setOnClickListener(v -> validateAndTransfer());
    }

    private void loadWarehouses() {
        warehouseIds.clear();
        warehouseNames.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, name FROM warehouses WHERE is_active = 1 ORDER BY id", null);
        try {
            while (c.moveToNext()) {
                warehouseIds.add(c.getInt(0));
                warehouseNames.add(c.getString(1));
            }
        } finally { c.close(); }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, warehouseNames);
        if (spinnerFromWarehouse != null) spinnerFromWarehouse.setAdapter(adapter);
        if (spinnerToWarehouse != null)   spinnerToWarehouse.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new ArrayList<>(warehouseNames)));
    }

    private void loadProducts() {
        productIds.clear();
        productNames.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id, name FROM products WHERE COALESCE(is_archived,0)=0 ORDER BY name", null);
        try {
            while (c.moveToNext()) {
                productIds.add(c.getInt(0));
                productNames.add(c.getString(1));
            }
        } finally { c.close(); }
    }

    private void addTransferItemRow() {
        if (layoutTransferItems == null || productNames.isEmpty()) return;
        View row = LayoutInflater.from(this).inflate(R.layout.item_transfer_item_row, layoutTransferItems, false);

        Spinner sp = row.findViewById(R.id.spinnerTransferProduct);
        if (sp != null) {
            sp.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, productNames));
        }
        ImageButton btnRemove = row.findViewById(R.id.btnRemoveTransferItem);
        if (btnRemove != null) {
            btnRemove.setOnClickListener(v -> {
                layoutTransferItems.removeView(row);
                transferItemViews.remove(row);
            });
        }
        layoutTransferItems.addView(row);
        transferItemViews.add(row);
    }

    private void validateAndTransfer() {
        if (warehouseIds.size() < 2) {
            Toast.makeText(this, "يجب توفر مخزنَين على الأقل", Toast.LENGTH_SHORT).show();
            return;
        }
        int fromIdx = spinnerFromWarehouse != null ? spinnerFromWarehouse.getSelectedItemPosition() : 0;
        int toIdx   = spinnerToWarehouse   != null ? spinnerToWarehouse.getSelectedItemPosition()   : 0;

        if (fromIdx < 0 || fromIdx >= warehouseIds.size() ||
            toIdx   < 0 || toIdx   >= warehouseIds.size()) return;

        int fromId = warehouseIds.get(fromIdx);
        int toId   = warehouseIds.get(toIdx);

        if (fromId == toId) {
            Toast.makeText(this, "لا يمكن التحويل للمخزن نفسه", Toast.LENGTH_SHORT).show();
            return;
        }
        if (transferItemViews.isEmpty()) {
            Toast.makeText(this, "أضف صنفاً واحداً على الأقل", Toast.LENGTH_SHORT).show();
            return;
        }

        // بناء قائمة الأصناف مع التحقق من الكميات
        List<long[]> items = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        for (View row : transferItemViews) {
            Spinner sp    = row.findViewById(R.id.spinnerTransferProduct);
            EditText etQty = row.findViewById(R.id.etTransferQty);
            if (sp == null || etQty == null) continue;

            int pid = productIds.get(sp.getSelectedItemPosition());
            String qtyStr = etQty.getText().toString().trim();
            if (qtyStr.isEmpty()) continue;

            double qty;
            try { qty = Double.parseDouble(qtyStr); }
            catch (NumberFormatException e) {
                Toast.makeText(this, "كمية غير صالحة", Toast.LENGTH_SHORT).show();
                return;
            }
            if (qty <= 0) {
                Toast.makeText(this, "الكمية يجب أن تكون أكبر من صفر", Toast.LENGTH_SHORT).show();
                return;
            }
            double avail = dbHelper.getWarehouseProductQuantity(db, fromId, pid);
            if (qty > avail + 0.0001) {
                String productName = productNames.get(sp.getSelectedItemPosition());
                Toast.makeText(this,
                        "❌ " + productName + ": المطلوب " + qty + " — المتاح " + avail,
                        Toast.LENGTH_LONG).show();
                return;
            }
            items.add(new long[]{pid, Double.doubleToLongBits(qty)});
        }

        if (items.isEmpty()) {
            Toast.makeText(this, "أضف صنفاً واحداً على الأقل بكمية صحيحة", Toast.LENGTH_SHORT).show();
            return;
        }

        // تأكيد التنفيذ
        String fromName = warehouseNames.get(fromIdx);
        String toName   = warehouseNames.get(toIdx);
        new MaterialAlertDialogBuilder(this)
                .setTitle("تأكيد التحويل")
                .setMessage("تحويل " + items.size() + " صنف\nمن: " + fromName + "\nإلى: " + toName)
                .setPositiveButton("تنفيذ", (d, w) -> executeTransfer(fromId, toId, items))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void executeTransfer(int fromId, int toId, List<long[]> items) {
        String notes  = etTransferNotes != null ? etTransferNotes.getText().toString().trim() : null;
        int    userId = SessionManager.getInstance(this).getUserId();

        boolean ok = dbHelper.executeStockTransfer(fromId, toId, userId, notes, items);
        if (ok) {
            Toast.makeText(this, "✅ تم تنفيذ التحويل بنجاح", Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(this, "❌ فشل التحويل. تحقق من الكميات المتاحة", Toast.LENGTH_LONG).show();
        }
    }
}
