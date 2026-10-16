package com.lap1.mystore.activities;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.utils.BottomNavHelper;

import java.util.ArrayList;
import java.util.Locale;

public class InvoicesActivity extends AppCompatActivity {

    private ListView lvInvoices;
    private EditText etSearchInvoice;
    private TextView tvInvoicesCount;
    private DatabaseHelper dbHelper;
    private ArrayList<InvoiceModel> fullInvoiceList;
    private ArrayList<InvoiceModel> filteredInvoiceList;
    private InvoiceAdapter adapter;

    public static class InvoiceModel {
        int id;
        String invoiceNumber;
        double total;
        String paymentStatus;
        String saleDate;
        String customerName;
        double discount;
        double net;

        InvoiceModel(int id, String invoiceNumber, double total, String paymentStatus, String saleDate, String customerName, double discount) {
            this.id = id;
            this.invoiceNumber = invoiceNumber;
            this.total = total;
            this.paymentStatus = paymentStatus;
            this.saleDate = saleDate;
            this.customerName = (customerName != null && !customerName.isEmpty()) ? customerName : "عميل نقدي";
            this.discount = discount;
            this.net = total - discount;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoices);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        lvInvoices = findViewById(R.id.lvInvoices);
        etSearchInvoice = findViewById(R.id.etSearchInvoice);
        tvInvoicesCount = findViewById(R.id.tvInvoicesCount);
        ImageButton btnInvoicesBack = findViewById(R.id.btnInvoicesBack);

        if (btnInvoicesBack != null) {
            btnInvoicesBack.setOnClickListener(v -> finish());
        }

        dbHelper = new DatabaseHelper(this);
        fullInvoiceList = new ArrayList<>();
        filteredInvoiceList = new ArrayList<>();

        adapter = new InvoiceAdapter(this, filteredInvoiceList);
        lvInvoices.setAdapter(adapter);

        BottomNavHelper.setupBottomNav(this, "invoices");

        loadInvoices();

        if (etSearchInvoice != null) {
            etSearchInvoice.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterInvoices(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        lvInvoices.setOnItemClickListener((parent, view, position, id) -> {
            InvoiceModel model = filteredInvoiceList.get(position);
            Intent intent = new Intent(InvoicesActivity.this, InvoiceDetailActivity.class);
            intent.putExtra("SALE_ID", model.id);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInvoices();
    }

    private void loadInvoices() {
        fullInvoiceList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT s.id, s.invoice_number, s.total, s.payment_status, s.sale_date, c.name, s.discount " +
                "FROM sales s LEFT JOIN customers c ON s.customer_id = c.id ORDER BY s.id DESC";

        Cursor cursor = db.rawQuery(query, null);
        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(0);
                String invNo = cursor.getString(1);
                double total = cursor.getDouble(2);
                String status = cursor.getString(3);
                String date = cursor.getString(4);
                String custName = cursor.getString(5);
                double discount = cursor.getDouble(6);

                fullInvoiceList.add(new InvoiceModel(id, invNo, total, status, date, custName, discount));
            } while (cursor.moveToNext());
        }
        cursor.close();

        String currentFilter = (etSearchInvoice != null) ? etSearchInvoice.getText().toString() : "";
        filterInvoices(currentFilter);
    }

    private void filterInvoices(String query) {
        filteredInvoiceList.clear();
        String q = (query != null) ? query.trim().toLowerCase() : "";

        if (q.isEmpty()) {
            filteredInvoiceList.addAll(fullInvoiceList);
        } else {
            for (InvoiceModel item : fullInvoiceList) {
                if (item.invoiceNumber.toLowerCase().contains(q) || item.customerName.toLowerCase().contains(q) || (item.paymentStatus != null && item.paymentStatus.toLowerCase().contains(q))) {
                    filteredInvoiceList.add(item);
                }
            }
        }

        if (tvInvoicesCount != null) {
            tvInvoicesCount.setText("إجمالي الفواتير: " + filteredInvoiceList.size());
        }

        adapter.notifyDataSetChanged();
    }

    private static class InvoiceAdapter extends BaseAdapter {
        private final Context context;
        private final ArrayList<InvoiceModel> items;
        private final LayoutInflater inflater;

        InvoiceAdapter(Context context, ArrayList<InvoiceModel> items) {
            this.context = context;
            this.items = items;
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_invoice, parent, false);
                holder = new ViewHolder();
                holder.tvInvoiceNum = convertView.findViewById(R.id.tvInvoiceNum);
                holder.tvInvoiceStatusBadge = convertView.findViewById(R.id.tvInvoiceStatusBadge);
                holder.tvInvoiceCustomer = convertView.findViewById(R.id.tvInvoiceCustomer);
                holder.tvInvoiceNet = convertView.findViewById(R.id.tvInvoiceNet);
                holder.tvInvoiceDate = convertView.findViewById(R.id.tvInvoiceDate);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            InvoiceModel item = items.get(position);
            holder.tvInvoiceNum.setText("📄 فاتورة #" + item.invoiceNumber);
            holder.tvInvoiceCustomer.setText(item.customerName);
            holder.tvInvoiceNet.setText(String.format(Locale.getDefault(), "%.2f ر.س", item.net));
            holder.tvInvoiceDate.setText("🕒 " + (item.saleDate != null ? item.saleDate : "--"));

            String status = (item.paymentStatus != null) ? item.paymentStatus : "مدفوعة";
            holder.tvInvoiceStatusBadge.setText(status);

            if (status.contains("آجل") || status.contains("دين")) {
                holder.tvInvoiceStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.danger));
                holder.tvInvoiceStatusBadge.setBackgroundResource(R.drawable.bg_badge_out_of_stock);
            } else if (status.contains("جزئي")) {
                holder.tvInvoiceStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.warning));
                holder.tvInvoiceStatusBadge.setBackgroundResource(R.drawable.bg_badge_low_stock);
            } else {
                holder.tvInvoiceStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.success));
                holder.tvInvoiceStatusBadge.setBackgroundResource(R.drawable.bg_badge_good_stock);
            }

            return convertView;
        }

        static class ViewHolder {
            TextView tvInvoiceNum;
            TextView tvInvoiceStatusBadge;
            TextView tvInvoiceCustomer;
            TextView tvInvoiceNet;
            TextView tvInvoiceDate;
        }
    }
}
