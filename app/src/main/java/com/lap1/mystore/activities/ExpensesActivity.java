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
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lap1.mystore.R;
import com.lap1.mystore.database.DatabaseHelper;

import java.util.ArrayList;
import java.util.Locale;

public class ExpensesActivity extends AppCompatActivity {

    private EditText etTitle, etAmount;
    private Spinner spCategory;
    private ListView lvExpenses;
    private TextView tvTotalExpenses, tvExpensesCount;
    private DatabaseHelper dbHelper;
    private ArrayList<ExpenseModel> expenseList;
    private ExpenseAdapter adapter;

    private final String[] categories = {"إيجار", "رواتب", "كهرباء ومياه", "نظافة", "صيانة", "أدوات مكتبية", "أخرى"};

    public static class ExpenseModel {
        int id;
        String title;
        double amount;
        String category;
        String date;

        ExpenseModel(int id, String title, double amount, String category, String date) {
            this.id = id;
            this.title = title;
            this.amount = amount;
            this.category = category;
            this.date = date;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expenses);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        etTitle = findViewById(R.id.etExpenseTitle);
        etAmount = findViewById(R.id.etExpenseAmount);
        spCategory = findViewById(R.id.spExpenseCategory);
        lvExpenses = findViewById(R.id.lvExpenses);
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses);
        tvExpensesCount = findViewById(R.id.tvExpensesCount);
        Button btnAdd = findViewById(R.id.btnAddExpense);
        View btnBack = findViewById(R.id.btnExpensesBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        dbHelper = new DatabaseHelper(this);
        expenseList = new ArrayList<>();

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(catAdapter);

        loadExpenses();

        btnAdd.setOnClickListener(v -> addExpense());

        // الشريط السفلي الثابت
        com.lap1.mystore.utils.BottomNavHelper.setupBottomNav(this, "more");
    }

    private void addExpense() {
        String title = etTitle.getText().toString().trim();
        String amountStr = etAmount.getText().toString().trim();
        String category = spCategory.getSelectedItem().toString();

        if (title.isEmpty() || amountStr.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال عنوان المصروف والمبلغ", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                Toast.makeText(this, "المبلغ يجب أن يكون أكبر من صفر", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "مبلغ غير صالح", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("amount", amount);
        values.put("category", category);

        long id = db.insert("expenses", null, values);

        if (id != -1) {
            Toast.makeText(this, "تم تسجيل المصروف بنجاح", Toast.LENGTH_SHORT).show();
            etTitle.setText("");
            etAmount.setText("");
            loadExpenses();
        } else {
            Toast.makeText(this, "فشل في تسجيل المصروف", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadExpenses() {
        expenseList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id, title, amount, category, expense_date FROM expenses ORDER BY id DESC", null);

        double total = 0.0;
        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(0);
                String title = cursor.getString(1);
                double amount = cursor.getDouble(2);
                String cat = cursor.getString(3);
                String date = cursor.getString(4);

                total += amount;
                expenseList.add(new ExpenseModel(id, title, amount, cat, date));
            } while (cursor.moveToNext());
        }
        cursor.close();

        if (tvTotalExpenses != null) {
            tvTotalExpenses.setText(String.format(Locale.US, "%.2f ر.س", total));
        }
        if (tvExpensesCount != null) {
            tvExpensesCount.setText(expenseList.size() + " عملية");
        }

        adapter = new ExpenseAdapter(this, expenseList);
        lvExpenses.setAdapter(adapter);
    }

    private void confirmDeleteExpense(ExpenseModel expense) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("حذف المصروف")
                .setMessage("هل أنت متأكد من حذف مصروف \"" + expense.title + "\" بمبلغ " + String.format(Locale.US, "%.2f", expense.amount) + " ر.س؟")
                .setPositiveButton("نعم، حذف", (dialog, which) -> {
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    db.delete("expenses", "id = ?", new String[]{String.valueOf(expense.id)});
                    Toast.makeText(this, "تم الحذف بنجاح", Toast.LENGTH_SHORT).show();
                    loadExpenses();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private class ExpenseAdapter extends BaseAdapter {
        private final Context context;
        private final ArrayList<ExpenseModel> list;

        ExpenseAdapter(Context context, ArrayList<ExpenseModel> list) {
            this.context = context;
            this.list = list;
        }

        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int position) { return list.get(position); }
        @Override public long getItemId(int position) { return list.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_expense, parent, false);
            }

            TextView tvCategory = convertView.findViewById(R.id.tvExpenseCategoryBadge);
            TextView tvTitle = convertView.findViewById(R.id.tvExpenseTitle);
            TextView tvAmount = convertView.findViewById(R.id.tvExpenseAmount);
            TextView tvDate = convertView.findViewById(R.id.tvExpenseDate);
            ImageButton btnDelete = convertView.findViewById(R.id.btnDeleteExpense);

            ExpenseModel item = list.get(position);
            tvTitle.setText(item.title);
            tvCategory.setText(item.category != null ? item.category : "عام");
            tvAmount.setText(String.format(Locale.US, "- %.2f ر.س", item.amount));
            tvDate.setText("📅 " + (item.date != null ? item.date : "اليوم"));

            btnDelete.setOnClickListener(v -> confirmDeleteExpense(item));

            return convertView;
        }
    }
}
