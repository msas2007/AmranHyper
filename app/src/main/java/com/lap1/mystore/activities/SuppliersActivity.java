package com.lap1.mystore.activities;

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
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

import java.util.ArrayList;

public class SuppliersActivity extends AppCompatActivity {

    private EditText etSearch;
    private ListView lvSuppliers;
    private TextView tvSuppliersCount;
    private DatabaseHelper dbHelper;
    private ArrayList<SupplierModel> supplierList;
    private SupplierAdapter adapter;

    public static class SupplierModel {
        int id;
        String name;
        String phone;
        double balance;

        SupplierModel(int id, String name, String phone, double balance) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.balance = balance;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_suppliers);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        etSearch = findViewById(R.id.etSearchSupplier);
        lvSuppliers = findViewById(R.id.lvSuppliers);
        tvSuppliersCount = findViewById(R.id.tvSuppliersCount);
        Button btnAdd = findViewById(R.id.btnAddSupplier);
        View btnBack = findViewById(R.id.btnSuppliersBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        dbHelper = new DatabaseHelper(this);
        supplierList = new ArrayList<>();

        loadSuppliers("");

        btnAdd.setOnClickListener(v -> showAddSupplierDialog());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadSuppliers(s.toString().trim());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        lvSuppliers.setOnItemClickListener((parent, view, position, id) -> {
            SupplierModel supplier = supplierList.get(position);
            Intent intent = new Intent(SuppliersActivity.this, SupplierDetailActivity.class);
            intent.putExtra("SUPPLIER_ID", supplier.id);
            startActivity(intent);
        });

        // الشريط السفلي الثابت
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "");
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSuppliers(etSearch.getText().toString().trim());
    }

    private void loadSuppliers(String keyword) {
        supplierList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor;
        if (keyword.isEmpty()) {
            cursor = db.rawQuery("SELECT * FROM suppliers ORDER BY name", null);
        } else {
            cursor = db.rawQuery("SELECT * FROM suppliers WHERE name LIKE ? OR phone LIKE ? ORDER BY name", new String[]{"%" + keyword + "%", "%" + keyword + "%"});
        }

        if (cursor.moveToFirst()) {
            do {
                supplierList.add(new SupplierModel(
                        cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("phone")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("balance"))
                ));
            } while (cursor.moveToNext());
        }
        cursor.close();

        if (tvSuppliersCount != null) {
            tvSuppliersCount.setText("إجمالي الموردين: " + supplierList.size());
        }

        adapter = new SupplierAdapter(this, supplierList);
        lvSuppliers.setAdapter(adapter);
    }

    private void showAddSupplierDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_simple, null);
        EditText etName = view.findViewById(R.id.etSimpleName);
        EditText etPhone = view.findViewById(R.id.etSimplePhone);
        etName.setHint("اسم المورد");

        new MaterialAlertDialogBuilder(this)
                .setTitle("إضافة مورد جديد")
                .setView(view)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        SQLiteDatabase db = dbHelper.getWritableDatabase();
                        ContentValues cv = new ContentValues();
                        cv.put("name", name);
                        cv.put("phone", etPhone.getText().toString().trim());
                        db.insert("suppliers", null, cv);
                        loadSuppliers("");
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private static class SupplierAdapter extends BaseAdapter {
        private Context context;
        private ArrayList<SupplierModel> list;

        SupplierAdapter(Context context, ArrayList<SupplierModel> list) {
            this.context = context;
            this.list = list;
        }

        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int position) { return list.get(position); }
        @Override public long getItemId(int position) { return list.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = LayoutInflater.from(context).inflate(R.layout.item_supplier, parent, false);
            TextView tvName = convertView.findViewById(R.id.tvSupplierName);
            TextView tvPhone = convertView.findViewById(R.id.tvSupplierPhone);
            TextView tvBalance = convertView.findViewById(R.id.tvSupplierBalance);

            SupplierModel supplier = list.get(position);
            tvName.setText(supplier.name);
            tvPhone.setText("📞 الهاتف: " + (supplier.phone != null && !supplier.phone.isEmpty() ? supplier.phone : "غير مسجل"));
            tvBalance.setText("مستحقات: " + String.format(java.util.Locale.US, "%.2f", supplier.balance) + " ر.س");

            if (supplier.balance > 0) {
                tvBalance.setTextColor(context.getResources().getColor(R.color.danger));
                tvBalance.setBackgroundResource(R.drawable.bg_badge_out_of_stock);
            } else {
                tvBalance.setTextColor(context.getResources().getColor(R.color.success));
                tvBalance.setBackgroundResource(R.drawable.bg_badge_good_stock);
            }

            return convertView;
        }
    }
}
