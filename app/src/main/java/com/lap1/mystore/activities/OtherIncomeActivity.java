package com.lap1.mystore.activities;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;
import com.lap1.mystore.utils.SessionManager;

import java.util.ArrayList;
import java.util.Locale;

public class OtherIncomeActivity extends AppCompatActivity {

    private EditText etTitle, etAmount, etNotes;
    private Spinner spCategory;
    private RadioGroup rgPaymentMethod;
    private RadioButton rbCash, rbNetwork;
    private ListView lvIncomes;
    private TextView tvTotal, tvCount;
    private DatabaseHelper dbHelper;
    private ArrayList<IncomeModel> incomeList;
    private IncomeAdapter adapter;

    private final String[] categories = {"مبيعات متفرقة", "إيجارات", "عمولات", "هبات", "استرداد ضرائب", "أخرى"};

    public static class IncomeModel {
        int id;
        String title, category, paymentMethod, date, notes;
        double amount;

        IncomeModel(int id, String title, String category, double amount, String paymentMethod, String date, String notes) {
            this.id = id; this.title = title; this.category = category;
            this.amount = amount; this.paymentMethod = paymentMethod;
            this.date = date; this.notes = notes;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_other_income);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        etTitle = findViewById(R.id.etIncomeTitle);
        etAmount = findViewById(R.id.etIncomeAmount);
        etNotes = findViewById(R.id.etIncomeNotes);
        spCategory = findViewById(R.id.spIncomeCategory);
        rgPaymentMethod = findViewById(R.id.rgIncomePaymentMethod);
        rbCash = findViewById(R.id.rbIncomeCash);
        rbNetwork = findViewById(R.id.rbIncomeNetwork);
        lvIncomes = findViewById(R.id.lvIncomes);
        tvTotal = findViewById(R.id.tvIncomesTotal);
        tvCount = findViewById(R.id.tvIncomesCount);
        Button btnAdd = findViewById(R.id.btnAddIncome);
        View btnBack = findViewById(R.id.btnIncomeBack);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        dbHelper = new DatabaseHelper(this);
        incomeList = new ArrayList<>();

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(catAdapter);

        loadIncomes();
        btnAdd.setOnClickListener(v -> addIncome());
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "more");
    }

    private void addIncome() {
        String title = etTitle.getText().toString().trim();
        String amountStr = etAmount.getText().toString().trim();
        String category = spCategory.getSelectedItem() != null ? spCategory.getSelectedItem().toString() : "أخرى";
        String notes = etNotes != null ? etNotes.getText().toString().trim() : "";
        String paymentMethod = (rbNetwork != null && rbNetwork.isChecked()) ? "Network" : "Cash";

        if (title.isEmpty() || amountStr.isEmpty()) {
            Toast.makeText(this, "يرجى إدخال العنوان والمبلغ", Toast.LENGTH_SHORT).show();
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) { Toast.makeText(this, "المبلغ يجب أن يكون أكبر من الصفر", Toast.LENGTH_SHORT).show(); return; }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "مبلغ غير صالح", Toast.LENGTH_SHORT).show();
            return;
        }

        int userId = SessionManager.getInstance(this).getUserId();
        long id = dbHelper.addOtherIncome(title, category, amount, paymentMethod, notes.isEmpty() ? null : notes, userId);
        if (id > 0) {
            Toast.makeText(this, "✅ تم تسجيل الإيراد بنجاح", Toast.LENGTH_SHORT).show();
            etTitle.setText(""); etAmount.setText("");
            if (etNotes != null) etNotes.setText("");
            if (rbCash != null) rbCash.setChecked(true);
            loadIncomes();
        } else {
            Toast.makeText(this, "❌ فشل في تسجيل الإيراد", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadIncomes() {
        incomeList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, title, category, amount, payment_method, income_date, notes FROM other_income ORDER BY id DESC", null);
        double total = 0;
        try {
            while (c.moveToNext()) {
                int id = c.getInt(0);
                String title = c.getString(1);
                String cat = c.getString(2);
                double amount = c.getDouble(3);
                String pm = c.getString(4);
                String date = c.getString(5);
                String notes = c.getString(6);
                total += amount;
                incomeList.add(new IncomeModel(id, title, cat, amount, pm, date, notes));
            }
        } finally { c.close(); }
        if (tvTotal != null) tvTotal.setText(String.format(Locale.US, "%.2f ر.ي", total));
        if (tvCount != null) tvCount.setText(incomeList.size() + " إيراد");
        adapter = new IncomeAdapter(this, incomeList);
        lvIncomes.setAdapter(adapter);
    }

    private void confirmDelete(IncomeModel item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("حذف الإيراد")
                .setMessage("هل تريد حذف إيراد \"" + item.title + "\"؟")
                .setPositiveButton("نعم، حذف", (d, w) -> {
                    dbHelper.getWritableDatabase().delete("other_income", "id = ?", new String[]{String.valueOf(item.id)});
                    loadIncomes();
                })
                .setNegativeButton("إلغاء", null).show();
    }

    private class IncomeAdapter extends BaseAdapter {
        private final Context ctx;
        private final ArrayList<IncomeModel> list;

        IncomeAdapter(Context ctx, ArrayList<IncomeModel> list) { this.ctx = ctx; this.list = list; }

        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int p) { return list.get(p); }
        @Override public long getItemId(int p) { return list.get(p).id; }

        @Override
        public View getView(int position, View cv, ViewGroup parent) {
            if (cv == null) cv = LayoutInflater.from(ctx).inflate(R.layout.item_other_income, parent, false);
            IncomeModel item = list.get(position);
            TextView tvTitle = cv.findViewById(R.id.tvIncomeName);
            TextView tvCat = cv.findViewById(R.id.tvIncomeCategory);
            TextView tvAmt = cv.findViewById(R.id.tvIncomeAmount);
            TextView tvDate = cv.findViewById(R.id.tvIncomeDate);
            TextView tvPm = cv.findViewById(R.id.tvIncomePaymentMethod);
            View btnDel = cv.findViewById(R.id.btnDeleteIncome);

            if (tvTitle != null) tvTitle.setText(item.title);
            if (tvCat != null) tvCat.setText(item.category != null ? item.category : "");
            if (tvAmt != null) tvAmt.setText(String.format(Locale.US, "+ %.2f ر.ي", item.amount));
            if (tvDate != null) tvDate.setText(item.date != null ? item.date.substring(0, Math.min(10, item.date.length())) : "");
            if (tvPm != null) tvPm.setText("Network".equals(item.paymentMethod) ? "🏧 شبكة" : "💵 نقدي");
            if (btnDel != null) btnDel.setOnClickListener(v -> confirmDelete(item));
            return cv;
        }
    }
}
