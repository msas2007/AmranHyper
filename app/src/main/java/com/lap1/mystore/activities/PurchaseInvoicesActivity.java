package com.lap1.mystore.activities;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.utils.BottomNavHelper;

import java.util.ArrayList;
import java.util.Locale;

public class PurchaseInvoicesActivity extends AppCompatActivity {

    private ListView lvInvoices;
    private EditText etSearch;
    private TextView tvCount, tvTotalAmount, tvEmpty;
    private DatabaseHelper dbHelper;
    private ArrayList<PurchaseInvoiceModel> fullList = new ArrayList<>();
    private ArrayList<PurchaseInvoiceModel> filteredList = new ArrayList<>();
    private PurchaseInvoiceAdapter adapter;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    public static class PurchaseInvoiceModel {
        int id;
        int supplierId;
        String supplierName;
        double total;
        double paid;
        double remaining;
        String paymentMethod;
        String purchaseDate;

        public PurchaseInvoiceModel(int id, int supplierId, String supplierName, double total,
                                    double paid, double remaining, String paymentMethod, String purchaseDate) {
            this.id = id;
            this.supplierId = supplierId;
            this.supplierName = (supplierName != null && !supplierName.isEmpty()) ? supplierName : "مورد نقدي";
            this.total = total;
            this.paid = paid;
            this.remaining = remaining;
            this.paymentMethod = paymentMethod;
            this.purchaseDate = purchaseDate;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_invoices);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        dbHelper = new DatabaseHelper(this);
        lvInvoices = findViewById(R.id.lvPurchaseInvoices);
        etSearch = findViewById(R.id.etSearchPurchaseInvoice);
        tvCount = findViewById(R.id.tvPurchaseInvoicesCount);
        tvTotalAmount = findViewById(R.id.tvPurchaseInvoicesTotalAmount);
        tvEmpty = findViewById(R.id.tvEmptyPurchaseInvoices);

        ImageButton btnBack = findViewById(R.id.btnPurchaseInvoicesBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        Button btnNew = findViewById(R.id.btnNewPurchase);
        if (btnNew != null) {
            btnNew.setOnClickListener(v -> {
                startActivity(new Intent(PurchaseInvoicesActivity.this, PurchasesActivity.class));
            });
        }

        adapter = new PurchaseInvoiceAdapter(this, filteredList);
        lvInvoices.setAdapter(adapter);

        lvInvoices.setOnItemClickListener((parent, view, position, id) -> {
            PurchaseInvoiceModel item = filteredList.get(position);
            Intent intent = new Intent(PurchaseInvoicesActivity.this, PurchaseInvoiceDetailActivity.class);
            intent.putExtra("PURCHASE_ID", item.id);
            startActivity(intent);
        });

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String query = s.toString().trim();
                    if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                    searchRunnable = () -> applyFilter(query);
                    searchHandler.postDelayed(searchRunnable, 200);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        BottomNavHelper.setupBottomNav(this, "");
        loadInvoices();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInvoices();
    }

    private void loadInvoices() {
        fullList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT id, supplier_id, supplier_name, total, paid, remaining, payment_method, purchase_date " +
                "FROM purchases ORDER BY id DESC", null);
        try {
            while (cursor.moveToNext()) {
                fullList.add(new PurchaseInvoiceModel(
                        cursor.getInt(0),
                        cursor.isNull(1) ? 0 : cursor.getInt(1),
                        cursor.getString(2),
                        cursor.getDouble(3),
                        cursor.getDouble(4),
                        cursor.getDouble(5),
                        cursor.getString(6),
                        cursor.getString(7)
                ));
            }
        } finally {
            cursor.close();
        }
        applyFilter(etSearch != null ? etSearch.getText().toString().trim() : "");
    }

    private void applyFilter(String query) {
        filteredList.clear();
        double sumTotal = 0.0;
        if (query.isEmpty()) {
            filteredList.addAll(fullList);
        } else {
            String lq = query.toLowerCase();
            for (PurchaseInvoiceModel model : fullList) {
                if (String.valueOf(model.id).contains(lq) ||
                    model.supplierName.toLowerCase().contains(lq) ||
                    (model.purchaseDate != null && model.purchaseDate.contains(lq))) {
                    filteredList.add(model);
                }
            }
        }

        for (PurchaseInvoiceModel item : filteredList) {
            sumTotal += item.total;
        }

        if (tvCount != null) tvCount.setText(filteredList.size() + " فاتورة شراء");
        if (tvTotalAmount != null) tvTotalAmount.setText(String.format(Locale.US, "الإجمالي: %.2f ر.ي", sumTotal));
        if (tvEmpty != null) tvEmpty.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
        if (lvInvoices != null) lvInvoices.setVisibility(filteredList.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private static class PurchaseInvoiceAdapter extends BaseAdapter {
        private final Context context;
        private final ArrayList<PurchaseInvoiceModel> list;

        PurchaseInvoiceAdapter(Context context, ArrayList<PurchaseInvoiceModel> list) {
            this.context = context;
            this.list = list;
        }

        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int position) { return list.get(position); }
        @Override public long getItemId(int position) { return list.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_purchase_invoice, parent, false);
            }

            PurchaseInvoiceModel m = list.get(position);

            TextView tvNum = convertView.findViewById(R.id.tvPurchaseInvoiceNum);
            TextView tvBadge = convertView.findViewById(R.id.tvPurchaseStatusBadge);
            TextView tvSupplier = convertView.findViewById(R.id.tvPurchaseSupplier);
            TextView tvDate = convertView.findViewById(R.id.tvPurchaseDate);
            TextView tvTotal = convertView.findViewById(R.id.tvPurchaseTotal);
            TextView tvPaid = convertView.findViewById(R.id.tvPurchasePaid);
            TextView tvRemaining = convertView.findViewById(R.id.tvPurchaseRemaining);

            tvNum.setText("🚚 فاتورة شراء #" + m.id);
            tvSupplier.setText(m.supplierName);
            tvDate.setText(m.purchaseDate != null ? m.purchaseDate : "");

            tvTotal.setText(String.format(Locale.US, "%.2f ر.ي", m.total));
            tvPaid.setText(String.format(Locale.US, "%.2f ر.ي", m.paid));
            tvRemaining.setText(String.format(Locale.US, "%.2f ر.ي", m.remaining));

            if (m.remaining <= 0.001) {
                tvBadge.setText("مدفوعة");
                tvBadge.setTextColor(context.getResources().getColor(R.color.success));
                tvBadge.setBackgroundResource(R.drawable.bg_badge_good_stock);
            } else if (m.paid <= 0.001) {
                tvBadge.setText("آجل");
                tvBadge.setTextColor(context.getResources().getColor(R.color.danger));
                tvBadge.setBackgroundResource(R.drawable.bg_badge_out_of_stock);
            } else {
                tvBadge.setText("دفع جزئي");
                tvBadge.setTextColor(context.getResources().getColor(R.color.warning));
                tvBadge.setBackgroundResource(R.drawable.bg_badge_low_stock);
            }

            return convertView;
        }
    }
}
