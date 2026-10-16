package com.lap1.mystore.activities;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.utils.BottomNavHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InventoryCountActivity extends AppCompatActivity {

    // Views
    private RecyclerView rvInventoryCount;
    private Button btnCommitInventory, btnHistory;
    private Button btnFilterAll, btnFilterDiff, btnFilterLow;
    private EditText etSearch;
    private ImageButton btnScanBarcode;
    private TextView tvStatsSummary;

    // Data
    private DatabaseHelper dbHelper;

    private final List<CountItem> allMasterList = new ArrayList<>();
    private final List<CountItem> displayedList = new ArrayList<>();

    private CountAdapter adapter;
    private String searchKeyword = "";
    private int filterMode = 0; // 0=All, 1=Diff only, 2=Low stock only

    public static class CountItem {
        int productId;
        String productName;
        String barcode;
        String unitName;
        double systemQty;
        double actualQty;
        double minQty;
        String reason;

        CountItem(int productId, String productName, String barcode, String unitName, double systemQty, double minQty) {
            this.productId = productId;
            this.productName = productName;
            this.barcode = barcode != null ? barcode : "";
            this.unitName = unitName != null && !unitName.isEmpty() ? unitName : "حبة";
            this.systemQty = systemQty;
            this.actualQty = systemQty;
            this.minQty = minQty;
            this.reason = "";
        }

        public double getDiff() {
            return actualQty - systemQty;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_count);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        dbHelper = new DatabaseHelper(this);

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        rvInventoryCount = findViewById(R.id.rvInventoryCount);
        btnCommitInventory = findViewById(R.id.btnCommitInventory);
        btnHistory = findViewById(R.id.btnViewAdjustmentHistory);
        btnFilterAll = findViewById(R.id.btnCountFilterAll);
        btnFilterDiff = findViewById(R.id.btnCountFilterDiff);
        btnFilterLow = findViewById(R.id.btnCountFilterLow);
        etSearch = findViewById(R.id.etSearchCount);
        btnScanBarcode = findViewById(R.id.btnScanCountBarcode);
        tvStatsSummary = findViewById(R.id.tvCountStatsSummary);

        rvInventoryCount.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CountAdapter();
        rvInventoryCount.setAdapter(adapter);

        if (btnCommitInventory != null) btnCommitInventory.setOnClickListener(v -> confirmAndCommit());
        if (btnHistory != null) btnHistory.setOnClickListener(v -> showAdjustmentHistoryDialog());

        if (btnFilterAll != null) {
            btnFilterAll.setOnClickListener(v -> {
                filterMode = 0;
                applyFilterAndSearch();
            });
        }

        if (btnFilterDiff != null) {
            btnFilterDiff.setOnClickListener(v -> {
                filterMode = 1;
                applyFilterAndSearch();
            });
        }

        if (btnFilterLow != null) {
            btnFilterLow.setOnClickListener(v -> {
                filterMode = 2;
                applyFilterAndSearch();
            });
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchKeyword = s.toString().trim();
                    applyFilterAndSearch();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (btnScanBarcode != null) {
            btnScanBarcode.setOnClickListener(v -> {
                GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder().enableAutoZoom().build();
                GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);
                scanner.startScan()
                        .addOnSuccessListener(barcode -> {
                            String code = barcode.getRawValue();
                            if (code != null && !code.isEmpty()) {
                                if (etSearch != null) etSearch.setText(code);
                                Toast.makeText(this, "تم مسح الباركود: " + code, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "تعذر مسح الباركود", Toast.LENGTH_SHORT).show());
            });
        }

        loadInventory();

        int targetProductId = getIntent().getIntExtra("PRODUCT_ID", -1);
        if (targetProductId > 0) {
            for (CountItem item : allMasterList) {
                if (item.productId == targetProductId) {
                    if (etSearch != null) etSearch.setText(item.productName);
                    break;
                }
            }
        }

        // Bottom Navigation
        BottomNavHelper.setupBottomNav(this, "more");
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInventory();
    }

    private void loadInventory() {
        allMasterList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor c = db.rawQuery(
                "SELECT p.id, p.name, p.barcode, p.unit_name, p.quantity, p.min_quantity " +
                "FROM products p WHERE (p.is_archived IS NULL OR p.is_archived = 0) " +
                "ORDER BY p.name ASC", null);

        try {
            while (c.moveToNext()) {
                allMasterList.add(new CountItem(
                        c.getInt(0),
                        c.getString(1),
                        c.getString(2),
                        c.getString(3),
                        c.getDouble(4),
                        c.getDouble(5)
                ));
            }
        } finally {
            c.close();
        }

        applyFilterAndSearch();
    }

    private void applyFilterAndSearch() {
        displayedList.clear();
        int diffCount = 0;

        for (CountItem item : allMasterList) {
            boolean hasDiff = Math.abs(item.getDiff()) > 0.0001;
            if (hasDiff) diffCount++;

            boolean matchesSearch = searchKeyword.isEmpty() ||
                    item.productName.toLowerCase().contains(searchKeyword.toLowerCase()) ||
                    item.barcode.contains(searchKeyword);

            if (!matchesSearch) continue;

            if (filterMode == 1) {
                // Differences only
                if (hasDiff) displayedList.add(item);
            } else if (filterMode == 2) {
                // Low stock / out of stock
                if (item.systemQty <= item.minQty || item.systemQty <= 0) displayedList.add(item);
            } else {
                displayedList.add(item);
            }
        }

        if (tvStatsSummary != null) {
            tvStatsSummary.setText(String.format(Locale.getDefault(), "الأصناف: %d  |  أصناف بها فروقات: %d", allMasterList.size(), diffCount));
        }

        adapter.notifyDataSetChanged();
    }

    private void confirmAndCommit() {
        List<CountItem> changedItems = new ArrayList<>();
        for (CountItem item : allMasterList) {
            if (Math.abs(item.getDiff()) > 0.0001) {
                changedItems.add(item);
            }
        }

        if (changedItems.isEmpty()) {
            Toast.makeText(this, "جميع أصناف الجرد مطابقة للرصيد النظامي! لا توجد فروقات للتعديل.", Toast.LENGTH_LONG).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("💾 اعتماد وتطبيق تسوية الجرد")
                .setMessage("سيتم تسجيل ورصد (" + changedItems.size() + ") تسوية مخزون وتحديث أرصدة المنتجات بالمركز الرئيسي الموحد.\nهل تريد التأكيد؟")
                .setPositiveButton("تأكيد واعتماد", (dialog, which) -> commitInventory(changedItems))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void commitInventory(List<CountItem> changedItems) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        int successCount = 0;

        try {
            for (CountItem item : changedItems) {
                double diff = item.getDiff();
                String noteText = item.reason.trim().isEmpty() ? "تعديل جرد موحد" : item.reason.trim();

                // 1. Record in stock_adjustments table
                ContentValues adj = new ContentValues();
                adj.put("product_id", item.productId);
                adj.put("product_name", item.productName);
                adj.put("system_quantity", item.systemQty);
                adj.put("actual_quantity", item.actualQty);
                adj.put("difference", diff);
                adj.put("notes", noteText);
                db.insert("stock_adjustments", null, adj);

                // 2. Sync product stock with central warehouse
                dbHelper.setProductStock(db, item.productId, item.actualQty, "جرد موحد: " + noteText);
                successCount++;
            }

            db.setTransactionSuccessful();
            Toast.makeText(this, "✅ تم اعتماد وتنفيذ (" + successCount + ") تسوية جرد بنجاح وتحديث أرصدة المخزون", Toast.LENGTH_LONG).show();
            loadInventory();
        } catch (Exception e) {
            Toast.makeText(this, "خطأ أثناء اعتماد الجرد: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            db.endTransaction();
        }
    }

    private void showAdjustmentHistoryDialog() {
        StringBuilder sb = new StringBuilder();
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery(
                    "SELECT product_name, system_quantity, actual_quantity, difference, notes, created_at " +
                    "FROM stock_adjustments ORDER BY id DESC LIMIT 20", null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        String pName = cursor.getString(0);
                        double sys = cursor.getDouble(1);
                        double act = cursor.getDouble(2);
                        double diff = cursor.getDouble(3);
                        String notes = cursor.getString(4);
                        String date = cursor.getString(5);

                        String diffLabel = diff > 0 ? ("+" + String.format(Locale.getDefault(), "%.2f", diff) + " (زيادة)") : (String.format(Locale.getDefault(), "%.2f", diff) + " (عجز)");
                        sb.append("• ").append(pName != null ? pName : "منتج غير معروف").append("\n")
                          .append("  الفارق: ").append(diffLabel)
                          .append("  |  فعلي: ").append(act).append(" (نظامي: ").append(sys).append(")\n")
                          .append("  السبب: ").append(notes != null && !notes.isEmpty() ? notes : "بدون ملاحظات").append("  [").append(date != null ? date : "السابق").append("]\n\n");
                    } while (cursor.moveToNext());
                } else {
                    sb.append("لا توجد تسويات جرد مسجلة سابقاً.");
                }
                cursor.close();
            } else {
                sb.append("تعذر قراءة سجل التسويات.");
            }
        } catch (Exception e) {
            sb.append("حدث خطأ أثناء تحميل سجل التسويات: ").append(e.getMessage());
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("📋 سجل تسويات الجرد السابقة")
                .setMessage(sb.toString().trim())
                .setPositiveButton("إغلاق", null)
                .show();
    }

    // Adapter
    private class CountAdapter extends RecyclerView.Adapter<CountAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_stock_count_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            CountItem item = displayedList.get(position);

            h.tvName.setText(item.productName);
            h.tvSystem.setText(String.format(Locale.getDefault(), "نظامي: %.2f %s", item.systemQty, item.unitName));

            // Actual Qty EditText
            h.etActual.removeTextChangedListener(h.qtyWatcher);
            h.etActual.setText(String.format(Locale.US, "%.2f", item.actualQty));
            h.qtyWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    try {
                        double newActual = Double.parseDouble(s.toString());
                        item.actualQty = Math.max(0.0, newActual);
                        updateDiffDisplay(h, item);
                    } catch (NumberFormatException ignored) {}
                }
            };
            h.etActual.addTextChangedListener(h.qtyWatcher);

            // Minus & Plus Buttons
            h.btnMinus.setOnClickListener(v -> {
                item.actualQty = Math.max(0.0, item.actualQty - 1.0);
                h.etActual.setText(String.format(Locale.US, "%.2f", item.actualQty));
                updateDiffDisplay(h, item);
            });

            h.btnPlus.setOnClickListener(v -> {
                item.actualQty += 1.0;
                h.etActual.setText(String.format(Locale.US, "%.2f", item.actualQty));
                updateDiffDisplay(h, item);
            });

            // Reason EditText
            h.etReason.removeTextChangedListener(h.reasonWatcher);
            h.etReason.setText(item.reason);
            h.reasonWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    item.reason = s.toString();
                }
            };
            h.etReason.addTextChangedListener(h.reasonWatcher);

            updateDiffDisplay(h, item);
        }

        private void updateDiffDisplay(VH h, CountItem item) {
            double diff = item.getDiff();
            if (Math.abs(diff) < 0.0001) {
                h.tvDiff.setText("مطابق (الفرق: 0)");
                h.tvDiff.setTextColor(getResources().getColor(R.color.text_secondary));
                h.tvDiff.setBackgroundResource(R.drawable.bg_badge_info);
            } else if (diff < 0) {
                h.tvDiff.setText(String.format(Locale.getDefault(), "عجز: %.2f", diff));
                h.tvDiff.setTextColor(getResources().getColor(R.color.danger));
                h.tvDiff.setBackgroundResource(R.drawable.bg_badge_out_of_stock);
            } else {
                h.tvDiff.setText(String.format(Locale.getDefault(), "زيادة: +%.2f", diff));
                h.tvDiff.setTextColor(getResources().getColor(R.color.success));
                h.tvDiff.setBackgroundResource(R.drawable.bg_badge_good_stock);
            }
        }

        @Override
        public int getItemCount() {
            return displayedList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvSystem, tvDiff;
            EditText etActual, etReason;
            ImageButton btnMinus, btnPlus;
            TextWatcher qtyWatcher, reasonWatcher;

            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvCountProductName);
                tvSystem = v.findViewById(R.id.tvSystemQty);
                etActual = v.findViewById(R.id.etActualQty);
                tvDiff = v.findViewById(R.id.tvDifference);
                etReason = v.findViewById(R.id.etCountReason);
                btnMinus = v.findViewById(R.id.btnMinusQty);
                btnPlus = v.findViewById(R.id.btnPlusQty);
            }
        }
    }
}