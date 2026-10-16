package com.lap1.mystore.activities;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

import java.util.Locale;

public class PurchaseInvoiceDetailActivity extends AppCompatActivity {

    private int purchaseId;
    private DatabaseHelper dbHelper;

    private TextView tvTitle, tvDate, tvSupplier, tvStatus, tvMethod, tvTotal, tvPaid, tvRemaining, tvItemsHeader;
    private LinearLayout layoutItemsContainer;
    private Button btnReturn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_invoice_detail);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        purchaseId = getIntent().getIntExtra("PURCHASE_ID", -1);
        if (purchaseId <= 0) {
            Toast.makeText(this, "رقم الفاتورة غير صالح", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        dbHelper = new DatabaseHelper(this);

        tvTitle = findViewById(R.id.tvPurchaseDetailTitle);
        tvDate = findViewById(R.id.tvPurchaseDetailDate);
        tvSupplier = findViewById(R.id.tvPurchaseDetailSupplier);
        tvStatus = findViewById(R.id.tvPurchaseDetailStatus);
        tvMethod = findViewById(R.id.tvPurchaseDetailMethod);
        tvTotal = findViewById(R.id.tvPurchaseDetailTotal);
        tvPaid = findViewById(R.id.tvPurchaseDetailPaid);
        tvRemaining = findViewById(R.id.tvPurchaseDetailRemaining);
        tvItemsHeader = findViewById(R.id.tvPurchaseDetailItemsHeader);
        layoutItemsContainer = findViewById(R.id.layoutPurchaseDetailItemsContainer);
        btnReturn = findViewById(R.id.btnReturnFromInvoice);

        ImageButton btnBack = findViewById(R.id.btnPurchaseDetailBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        if (btnReturn != null) {
            btnReturn.setOnClickListener(v -> {
                Intent intent = new Intent(PurchaseInvoiceDetailActivity.this, PurchaseReturnsActivity.class);
                intent.putExtra("PRESELECTED_PURCHASE_ID", purchaseId);
                startActivity(intent);
            });
        }

        loadInvoiceDetails();
    }

    private void loadInvoiceDetails() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // 1. Invoice Header
        Cursor cHeader = db.rawQuery(
                "SELECT id, supplier_id, supplier_name, total, paid, remaining, payment_method, purchase_date " +
                "FROM purchases WHERE id = ?", new String[]{String.valueOf(purchaseId)});

        if (!cHeader.moveToFirst()) {
            cHeader.close();
            Toast.makeText(this, "تعذر العثور على الفاتورة", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String supplierName = cHeader.getString(2);
        double total = cHeader.getDouble(3);
        double paid = cHeader.getDouble(4);
        double remaining = cHeader.getDouble(5);
        String method = cHeader.getString(6);
        String date = cHeader.getString(7);
        cHeader.close();

        tvTitle.setText("🚚 فاتورة شراء #" + purchaseId);
        tvDate.setText(date != null ? date : "");
        tvSupplier.setText(supplierName != null && !supplierName.isEmpty() ? supplierName : "مورد نقدي");
        tvMethod.setText(method != null ? method : "كاش");
        tvTotal.setText(String.format(Locale.US, "%.2f ر.ي", total));
        tvPaid.setText(String.format(Locale.US, "%.2f ر.ي", paid));
        tvRemaining.setText(String.format(Locale.US, "%.2f ر.ي", remaining));

        if (remaining <= 0.001) {
            tvStatus.setText("مدفوعة بالكامل");
            tvStatus.setTextColor(getResources().getColor(R.color.success, null));
            tvStatus.setBackgroundResource(R.drawable.bg_badge_good_stock);
        } else if (paid <= 0.001) {
            tvStatus.setText("آجل (غير مدفوعة)");
            tvStatus.setTextColor(getResources().getColor(R.color.danger, null));
            tvStatus.setBackgroundResource(R.drawable.bg_badge_out_of_stock);
        } else {
            tvStatus.setText("دفع جزئي");
            tvStatus.setTextColor(getResources().getColor(R.color.warning, null));
            tvStatus.setBackgroundResource(R.drawable.bg_badge_low_stock);
        }

        // 2. Invoice Items
        layoutItemsContainer.removeAllViews();
        Cursor cItems = db.rawQuery(
                "SELECT pi.product_id, p.name, pi.quantity, pi.unit_price, pi.total, p.unit_name " +
                "FROM purchase_items pi " +
                "JOIN products p ON pi.product_id = p.id " +
                "WHERE pi.purchase_id = ? " +
                "ORDER BY pi.id ASC",
                new String[]{String.valueOf(purchaseId)});

        int itemCount = 0;
        try {
            while (cItems.moveToNext()) {
                itemCount++;
                String pName = cItems.getString(1);
                double qty = cItems.getDouble(2);
                double price = cItems.getDouble(3);
                double lineTotal = cItems.getDouble(4);
                String unit = cItems.isNull(5) ? "حبة" : cItems.getString(5);

                View row = LayoutInflater.from(this).inflate(R.layout.item_purchase_detail_row, layoutItemsContainer, false);
                TextView tvName = row.findViewById(R.id.tvDetailProductName);
                TextView tvSub = row.findViewById(R.id.tvDetailProductSub);
                TextView tvLineTotal = row.findViewById(R.id.tvDetailItemTotal);

                tvName.setText(pName);
                tvSub.setText(String.format(Locale.US, "الكمية: %.2f %s × %.2f ر.ي", qty, unit, price));
                tvLineTotal.setText(String.format(Locale.US, "%.2f ر.ي", lineTotal));

                layoutItemsContainer.addView(row);
            }
        } finally {
            cItems.close();
        }

        tvItemsHeader.setText("الأصناف المشتراة (" + itemCount + ")");
    }
}
