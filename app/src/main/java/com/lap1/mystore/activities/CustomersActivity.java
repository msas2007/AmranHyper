package com.lap1.mystore.activities;

import android.app.Dialog;
import android.content.ContentValues;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

import java.util.ArrayList;
import java.util.Locale;

public class CustomersActivity extends AppCompatActivity {

    private ListView lvCustomers;
    private EditText etSearchCustomer;
    private TextView tvCustomersCount;
    private DatabaseHelper dbHelper;
    private ArrayList<CustomerModel> customerList;
    private CustomerAdapter adapter;

    public static class CustomerModel {
        int id;
        String name;
        String phone;
        double balance;

        CustomerModel(int id, String name, String phone, double balance) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.balance = balance;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customers);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        lvCustomers = findViewById(R.id.lvCustomers);
        etSearchCustomer = findViewById(R.id.etSearchCustomer);
        tvCustomersCount = findViewById(R.id.tvCustomersCount);
        ImageButton btnBack = findViewById(R.id.btnCustomersBack);
        Button btnOpenAddCustomer = findViewById(R.id.btnOpenAddCustomer);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnOpenAddCustomer != null) {
            btnOpenAddCustomer.setOnClickListener(v -> showAddCustomerDialog());
        }

        dbHelper = new DatabaseHelper(this);
        customerList = new ArrayList<>();

        adapter = new CustomerAdapter(this, customerList);
        lvCustomers.setAdapter(adapter);

        loadCustomers("");

        // البحث الحي
        if (etSearchCustomer != null) {
            etSearchCustomer.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    loadCustomers(s.toString().trim());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        // الانتقال لتفاصيل العميل
        lvCustomers.setOnItemClickListener((parent, view, position, id) -> {
            CustomerModel customer = customerList.get(position);
            Intent intent = new Intent(CustomersActivity.this, CustomerDetailActivity.class);
            intent.putExtra("CUSTOMER_ID", customer.id);
            startActivity(intent);
        });

        // الشريط السفلي الثابت
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "");
    }

    @Override
    protected void onResume() {
        super.onResume();
        String q = (etSearchCustomer != null) ? etSearchCustomer.getText().toString().trim() : "";
        loadCustomers(q);
    }

    private void showAddCustomerDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_simple, null);
        EditText etName = view.findViewById(R.id.etSimpleName);
        EditText etPhone = view.findViewById(R.id.etSimplePhone);
        etName.setHint("اسم العميل *");
        etPhone.setHint("رقم الهاتف (اختياري)");

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("إضافة عميل جديد")
                .setView(view)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    String name = etName != null ? etName.getText().toString().trim() : "";
                    String phone = etPhone != null ? etPhone.getText().toString().trim() : "";

                    if (name.isEmpty()) {
                        Toast.makeText(this, "الرجاء إدخال اسم العميل", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    ContentValues cv = new ContentValues();
                    cv.put("name", name);
                    cv.put("phone", phone);
                    cv.put("balance", 0.0);

                    long newId = db.insert("customers", null, cv);
                    if (newId != -1) {
                        Toast.makeText(this, "تمت إضافة العميل بنجاح", Toast.LENGTH_SHORT).show();
                        loadCustomers("");
                    } else {
                        Toast.makeText(this, "فشل حفظ العميل", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void loadCustomers(String keyword) {
        customerList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT id, name, phone, balance FROM customers ";
        Cursor cursor;

        if (keyword.isEmpty()) {
            query += "ORDER BY balance DESC, name ASC";
            cursor = db.rawQuery(query, null);
        } else {
            query += "WHERE name LIKE ? OR phone LIKE ? ORDER BY balance DESC, name ASC";
            cursor = db.rawQuery(query, new String[]{"%" + keyword + "%", "%" + keyword + "%"});
        }

        if (cursor.moveToFirst()) {
            do {
                customerList.add(new CustomerModel(
                        cursor.getInt(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getDouble(3)
                ));
            } while (cursor.moveToNext());
        }
        cursor.close();

        if (tvCustomersCount != null) {
            tvCustomersCount.setText("إجمالي العملاء: " + customerList.size());
        }

        adapter.notifyDataSetChanged();
    }

    // محول مخصص لتصميم البطاقات الأنيق
    private static class CustomerAdapter extends BaseAdapter {
        private final Context context;
        private final ArrayList<CustomerModel> list;
        private final LayoutInflater inflater;

        CustomerAdapter(Context context, ArrayList<CustomerModel> list) {
            this.context = context;
            this.list = list;
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() { return list.size(); }

        @Override
        public Object getItem(int position) { return list.get(position); }

        @Override
        public long getItemId(int position) { return list.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_customer, parent, false);
            }

            TextView tvName = convertView.findViewById(R.id.tvItemCustomerName);
            TextView tvPhone = convertView.findViewById(R.id.tvItemCustomerPhone);
            TextView tvBalance = convertView.findViewById(R.id.tvItemCustomerBalance);

            CustomerModel customer = list.get(position);
            tvName.setText(customer.name);
            tvPhone.setText("📞 الهاتف: " + (customer.phone != null && !customer.phone.isEmpty() ? customer.phone : "غير مسجل"));

            if (customer.balance > 0) {
                tvBalance.setText("المديونية: " + String.format(Locale.getDefault(), "%.2f ر.س", customer.balance));
                tvBalance.setTextColor(ContextCompat.getColor(context, R.color.danger));
                tvBalance.setBackgroundResource(R.drawable.bg_badge_out_of_stock);
            } else {
                tvBalance.setText("مسدد بالكامل");
                tvBalance.setTextColor(ContextCompat.getColor(context, R.color.success));
                tvBalance.setBackgroundResource(R.drawable.bg_badge_good_stock);
            }

            return convertView;
        }
    }
}
